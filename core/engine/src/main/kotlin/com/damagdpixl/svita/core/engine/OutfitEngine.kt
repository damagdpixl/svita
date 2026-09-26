package com.damagdpixl.svita.core.engine

import kotlin.math.abs
import kotlin.math.min

/**
 * Dress-me outfit engine v1.
 *
 * Deterministic pipeline per request:
 *  1. pool: drop recently-worn ids, then temperature-window pruning. The hot
 *     side (T above a class maximum) prunes every class; the cold side (T below
 *     a class minimum) prunes only the "summer" classes C1/C2/C3 — base layers
 *     survive under outerwear and the warmth model decides. This is the single
 *     documented interpretation the algorithm relies on: a literal two-sided
 *     window for every class would contradict the reference control example at
 *     T = -5 (no body layer could ever accompany winter coats), so the window
 *     is treated as an anti-absurdity filter, not as the warmth model.
 *  2. role buckets (body/dress/legs/feet/outer/hat/scarf/accessory/bag) with a
 *     deterministic per-role cap of [POOL_ROLE_LIMIT] items (sorted by id).
 *  3. core generation: {body, legs, feet[, outer]} or {dress, feet[, outer]};
 *     outerwear is forbidden above +22, required at or below +12, optional in
 *     between. Candidate score = warmth x color harmony x style match, rounded
 *     half-even to 4 decimals, cut off at [SCORE_CUTOFF].
 *  4. ranking: score descending, then sorted garment ids ascending.
 *  5. top [TOP_N] looks: greedy attachment of hat -> scarf -> bag -> accessory
 *     (max 1/1/1/2) while the score does not drop, then a [REPEAT_PENALTY] per
 *     garment already used by earlier looks of the same response.
 *
 * [comfortDelta] is informational in v1 and does not influence selection; the
 * caller folds it into targetTempC when desired. The parameter is kept for API
 * compatibility.
 */
class OutfitEngine(
    private val palette: Map<String, ColorEntry>,
    private val thermal: ThermalMapping,
) {

    fun looks(
        garments: List<EngineGarment>,
        targetTempC: Double,
        styleTags: List<String> = emptyList(),
        excludeRecent: Set<Long> = emptySet(),
        comfortDelta: Double = 0.0,
    ): LooksResult {
        // Informational in v1: never influences selection.
        @Suppress("UNUSED_EXPRESSION")
        comfortDelta

        val t = targetTempC.coerceIn(T_MIN_ENGINE, T_MAX_ENGINE)
        val req = cloReq(t)
        val styleSet = if (styleTags.isEmpty()) null else styleTags.toSet()

        // 1) pool: hard exclusions, then the D1 temperature-window pruning.
        val poolItems = garments.asSequence()
            .filter { it.id !in excludeRecent }
            .filter { inWindow(it.subtype, t) }
            .toList()

        // 2) role buckets + deterministic per-role cap.
        val pool = HashMap<String, MutableList<EngineGarment>>()
        for (g in poolItems) {
            val role = roleOf(g.subtype) ?: continue
            pool.getOrPut(role) { mutableListOf() }.add(g)
        }
        for (role in pool.keys.toList()) {
            val list = pool[role]!!
            if (list.size > POOL_ROLE_LIMIT) {
                pool[role] = list.sortedBy { it.id }.take(POOL_ROLE_LIMIT).toMutableList()
            }
        }

        // 3) core generation.
        val mode = outerMode(t)
        val outerPool = pool["outer"].orEmpty()
        val outerLists: List<EngineGarment?> = when (mode) {
            OuterMode.FORBIDDEN -> listOf(null)
            OuterMode.REQUIRED -> outerPool
            OuterMode.OPTIONAL -> listOf(null) + outerPool
        }

        val cands = ArrayList<Candidate>()
        for (feet in pool["feet"].orEmpty()) {
            val bases = ArrayList<List<EngineGarment>>()
            for (body in pool["body"].orEmpty()) {
                for (legs in pool["legs"].orEmpty()) {
                    bases.add(listOf(body, legs, feet))
                }
            }
            for (dress in pool["dress"].orEmpty()) {
                bases.add(listOf(dress, feet))
            }
            for (base in bases) {
                for (outer in outerLists) {
                    if (mode == OuterMode.REQUIRED && outer == null) continue
                    val core = if (outer != null) base + outer else base
                    // aggregated with the reference runtime's compensated sum()
                    val sumW = neumaierSum(core.map { cloWeight(it.subtype) })
                    val warm = warmFit(sumW, req)
                    val score = scoreFrom(warm, core, styleSet)
                    if (score >= SCORE_CUTOFF) {
                        cands.add(Candidate(core, warm, score, core.map { it.id }.sorted()))
                    }
                }
            }
        }

        // 4) deterministic ranking: score desc, then sorted ids asc.
        cands.sortWith(
            compareByDescending<Candidate> { it.score }
                .thenComparator { a, b -> compareElementwise(a.sortedIds, b.sortedIds) },
        )

        // 5) top-N with optional-layer attachment and repeat penalty.
        val picks = ArrayList<Pick>()
        val used = HashSet<Long>()
        for (cand in cands) {
            val attached = attachOptionals(cand.garments, cand.warm, cand.score, pool, styleSet)
            val repeatHits = attached.garments.map { it.id }.toSet().count { it in used }
            val final = roundHalfEven4(attached.score - REPEAT_PENALTY * repeatHits)
            picks.add(Pick(attached.garments, final))
            used.addAll(attached.garments.map { it.id })
            if (picks.size == TOP_N) break
        }

        val resultLooks = picks.map { pick ->
            val canonical = canonicalIds(pick.garments)
            EngineLook(
                id = lookId(canonical),
                garmentIds = canonical,
                styles = lookStyle(pick.garments, styleSet),
                score = pick.score,
            )
        }
        return LooksResult(resultLooks)
    }

    private data class Candidate(
        val garments: List<EngineGarment>,
        val warm: Double,
        val score: Double,
        val sortedIds: List<Long>,
    )

    private data class Pick(val garments: List<EngineGarment>, val score: Double)

    // ------------------------------------------------------------------
    // Thermal model
    // ------------------------------------------------------------------

    /** Class of a subtype, or of the argument itself when it names a class. */
    internal fun classOf(subtypeOrClass: String): String =
        if (CLASSES.containsKey(subtypeOrClass)) subtypeOrClass else thermal.thermalClass(subtypeOrClass)

    /** Insulation weight (CLO) of the garment's thermal class. */
    fun cloWeight(subtype: String): Double = CLASSES.getValue(classOf(subtype)).clo

    /**
     * Temperature window with the documented cold-side interpretation: above a
     * class maximum everything is pruned; below a class minimum only the summer
     * classes C1/C2/C3 are pruned. Accepts a subtype or a bare class name.
     */
    fun inWindow(subtypeOrClass: String, t: Double): Boolean {
        val cls = classOf(subtypeOrClass)
        val window = CLASSES.getValue(cls)
        if (t > window.tMax) return false
        if (t < window.tMin && cls in HOT_SIDE_PRUNE_CLASSES) return false
        return true
    }

    /** Required insulation at temperature T: clamp((26 - T) / 15, 0, 2). */
    fun cloReq(t: Double): Double = ((26.0 - t) / 15.0).coerceIn(0.0, 2.0)

    /** 1 - min(1, |sumW - req| / 0.80). */
    fun warmFit(sumW: Double, req: Double): Double =
        1.0 - min(1.0, abs(sumW - req) / 0.80)

    // ------------------------------------------------------------------
    // Roles
    // ------------------------------------------------------------------

    /**
     * Role of a garment subtype. Within the body section: dress subtypes are
     * "dress", the outer families (and cardigan/fur) are "outer", the rest is
     * "body". Unknown sections have no role and never enter the pool.
     */
    fun roleOf(subtype: String): String? {
        if (subtype.startsWith("body.")) {
            if (subtype.startsWith("body.dress.")) return "dress"
            if (subtype == "body.cardigan" || subtype == "body.fur" ||
                BODY_OUTER_PREFIXES.any { subtype.startsWith(it) }
            ) {
                return "outer"
            }
            return "body"
        }
        return when {
            subtype.startsWith("legs.") -> "legs"
            subtype.startsWith("feet.") -> "feet"
            subtype.startsWith("head.hats.") -> "hat"
            subtype.startsWith("head.scarfs.") -> "scarf"
            subtype.startsWith("accessory.") -> "accessory"
            subtype.startsWith("bag.") -> "bag"
            else -> null
        }
    }

    /** Outerwear requirement at T: forbidden above +22, required at/below +12. */
    fun outerMode(t: Double): OuterMode = when {
        t > 22.0 -> OuterMode.FORBIDDEN
        t <= 12.0 -> OuterMode.REQUIRED
        else -> OuterMode.OPTIONAL
    }

    /** Garment ids in canonical role order; within a role by ascending id. */
    fun canonicalIds(garments: List<EngineGarment>): List<Long> =
        garments.sortedWith(
            compareBy({ ROLE_RANK.getValue(roleOf(it.subtype)!!) }, { it.id }),
        ).map { it.id }

    // ------------------------------------------------------------------
    // Color harmony
    // ------------------------------------------------------------------

    /** 'neutral' | 'muted' | 'bright'; empty/unknown colors are neutral. */
    fun colorCategory(colorName: String?): String {
        val entry = colorName?.let { palette[it] } ?: return "neutral"
        val c = entry.c
        if (c <= NEUTRAL_C_MAX) return "neutral"
        return if (c <= MUTED_C_MAX) "muted" else "bright"
    }

    /** Harmony of a pair of dominant colors in [0, 1]. */
    fun pairScore(colorA: String?, colorB: String?): Double {
        val catA = colorCategory(colorA)
        val catB = colorCategory(colorB)
        if (catA == "neutral" || catB == "neutral") return 0.95
        val h1 = palette.getValue(colorA!!).h
        val h2 = palette.getValue(colorB!!).h
        var dh = abs(h1 - h2)
        dh = min(dh, 360.0 - dh)
        return when {
            dh <= 15.0 -> 0.85 // tone-on-tone
            dh <= 45.0 -> 1.00 // analogous harmony
            dh <= 90.0 -> 0.55
            dh <= 150.0 -> 0.25 // clash
            else -> 0.90 // complementary pair
        }
    }

    /** Color score of a look (core or core + attached layers) in [0, 1]. */
    fun colorScore(garments: List<EngineGarment>): Double {
        val dominant = garments.map { it.colors.firstOrNull() }
        val cats = dominant.map { colorCategory(it) }
        val nNeutral = cats.count { it == "neutral" }
        val nAccent = garments.size - nNeutral
        val baseBonus = when {
            nNeutral >= 2 -> 1.0
            nNeutral == 1 -> 0.9
            else -> 0.6
        }
        var pairMean = 1.0
        if (garments.size >= 2) {
            val pairs = ArrayList<Double>(garments.size * garments.size / 2)
            for (i in dominant.indices) {
                for (j in i + 1 until dominant.size) {
                    pairs.add(pairScore(dominant[i], dominant[j]))
                }
            }
            // aggregated with the reference runtime's compensated sum()
            pairMean = neumaierSum(pairs) / pairs.size
        }
        val accentF = when {
            nAccent <= 1 -> 1.0
            nAccent == 2 -> 0.95
            nAccent == 3 -> 0.85
            else -> 0.70
        }
        val score = baseBonus * pairMean * accentF
        return min(1.0, maxOf(0.0, score))
    }

    // ------------------------------------------------------------------
    // Style tags
    // ------------------------------------------------------------------

    /** Intersection of the garments' non-empty style tag sets. */
    fun styleCommon(garments: List<EngineGarment>): Set<String> {
        var common: Set<String>? = null
        for (g in garments) {
            val s = g.styles.toSet()
            if (s.isEmpty()) continue
            common = if (common == null) s else common.intersect(s)
        }
        return common ?: emptySet()
    }

    /** 1.0 on non-empty intersection with the request; without a request, 1.0 vs 0.6. */
    fun styleScore(garments: List<EngineGarment>, styleTags: Set<String>?): Double {
        val common = styleCommon(garments)
        return if (!styleTags.isNullOrEmpty()) {
            if (common.intersect(styleTags).isNotEmpty()) 1.0 else 0.0
        } else {
            if (common.isNotEmpty()) 1.0 else 0.6
        }
    }

    /** Look.style: sorted common tags (optionally intersected with the request); fallback ["casual"]. */
    fun lookStyle(garments: List<EngineGarment>, styleTags: Set<String>?): List<String> {
        val common = styleCommon(garments)
        val tags = if (!styleTags.isNullOrEmpty()) common.intersect(styleTags).sorted() else common.sorted()
        return tags.ifEmpty { listOf("casual") }
    }

    // ------------------------------------------------------------------
    // Scoring and assembly
    // ------------------------------------------------------------------

    /** score = warm x color x style, rounded half-even to 4 decimals. */
    internal fun scoreFrom(warm: Double, garments: List<EngineGarment>, styleTags: Set<String>?): Double =
        roundHalfEven4(warm * colorScore(garments) * styleScore(garments, styleTags))

    /**
     * Greedy attachment of hat -> scarf -> bag -> accessory layers. A layer is
     * added only when the recomputed score does not drop; among equal scores
     * the smallest id wins; the quota loop for a role stops at the first
     * attachment failure.
     */
    private fun attachOptionals(
        coreGarments: List<EngineGarment>,
        warm: Double,
        baseScore: Double,
        pool: Map<String, List<EngineGarment>>,
        styleTags: Set<String>?,
    ): Attached {
        var current = coreGarments
        var currentScore = baseScore
        val currentIds = coreGarments.map { it.id }.toHashSet()
        for (role in OPTIONAL_ATTACHMENT_ORDER) {
            val taken = current.count { roleOf(it.subtype) == role }
            val quota = OPTIONAL_QUOTAS.getValue(role)
            for (slot in taken until quota) {
                var bestScore = Double.NEGATIVE_INFINITY
                var best: EngineGarment? = null
                for (cand in pool[role].orEmpty().sortedBy { it.id }) {
                    if (cand.id in currentIds) continue
                    val s = scoreFrom(warm, current + cand, styleTags)
                    if (s >= currentScore && s > bestScore) {
                        bestScore = s
                        best = cand
                    }
                }
                if (best == null) break
                current = current + best
                currentIds.add(best.id)
                currentScore = bestScore
            }
        }
        return Attached(current, currentScore)
    }

    private data class Attached(val garments: List<EngineGarment>, val score: Double)

    // ------------------------------------------------------------------
    // Deterministic look id
    // ------------------------------------------------------------------

    /**
     * id = crc32(utf8(ids sorted as decimal strings, joined with ",")) masked
     * to 31 bits. Ids are sorted AS STRINGS (lexicographic), not numerically.
     */
    fun lookId(garmentIds: List<Long>): Long {
        val joined = garmentIds.map { it.toString() }.sorted().joinToString(",")
        return crc32(joined.toByteArray(Charsets.UTF_8)) and 0x7FFFFFFFL
    }

    private fun compareElementwise(a: List<Long>, b: List<Long>): Int {
        for (i in a.indices) {
            val c = a[i].compareTo(b[i])
            if (c != 0) return c
        }
        return a.size.compareTo(b.size)
    }

    companion object {
        /** Maximum looks per response. */
        const val TOP_N = 5

        /** Minimum candidate score (applied before the repeat penalty). */
        const val SCORE_CUTOFF = 0.25

        /** Deterministic per-role pool cap (sorted by id). */
        const val POOL_ROLE_LIMIT = 30

        /** Penalty per garment already used by earlier looks of the response. */
        const val REPEAT_PENALTY = 0.15

        /** Working temperature range of the engine; requests are clamped into it. */
        const val T_MIN_ENGINE = -30.0
        const val T_MAX_ENGINE = 45.0

        // Color categories by fixed chroma thresholds.
        const val NEUTRAL_C_MAX = 0.048
        const val MUTED_C_MAX = 0.105

        /** Thermal class: insulation weight (CLO) and comfortable window. */
        data class ThermalClass(val clo: Double, val tMin: Double, val tMax: Double)

        val CLASSES: Map<String, ThermalClass> = mapOf(
            "C1" to ThermalClass(0.05, 20.0, 45.0),
            "C2" to ThermalClass(0.09, 16.0, 30.0),
            "C3" to ThermalClass(0.15, 12.0, 29.0),
            "C4" to ThermalClass(0.25, 8.0, 28.0),
            "C5" to ThermalClass(0.30, 5.0, 27.0),
            "C6" to ThermalClass(0.40, 0.0, 25.0),
            "C7" to ThermalClass(0.55, -10.0, 23.0),
            "C8" to ThermalClass(0.75, -30.0, 20.0),
            "DA" to ThermalClass(0.04, 5.0, 25.0),
            "WA" to ThermalClass(0.04, -30.0, 12.0),
            "DB" to ThermalClass(0.10, 2.0, 18.0),
            "WB" to ThermalClass(0.15, -25.0, 8.0),
            "N" to ThermalClass(0.00, -30.0, 45.0),
        )

        /** Canonical role order inside a look; dress occupies the body slot. */
        val ROLE_RANK: Map<String, Int> = mapOf(
            "body" to 0, "dress" to 0, "legs" to 1, "feet" to 2, "outer" to 3,
            "hat" to 4, "scarf" to 5, "accessory" to 6, "bag" to 7,
        )

        private val BODY_OUTER_PREFIXES = listOf("body.blazer", "body.gilet", "body.jacket", "body.coat")

        private val OPTIONAL_ATTACHMENT_ORDER = listOf("hat", "scarf", "bag", "accessory")

        private val OPTIONAL_QUOTAS: Map<String, Int> = mapOf(
            "hat" to 1, "scarf" to 1, "bag" to 1, "accessory" to 2,
        )

        private val HOT_SIDE_PRUNE_CLASSES = setOf("C1", "C2", "C3")
    }
}

/** Outerwear requirement mode at a given temperature. */
enum class OuterMode { FORBIDDEN, REQUIRED, OPTIONAL }
