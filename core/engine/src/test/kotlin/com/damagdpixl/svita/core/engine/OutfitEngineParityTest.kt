package com.damagdpixl.svita.core.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Parity suite against the reference oracle: every fixture case re-runs the
 * Kotlin engine and the response must match the recorded oracle output DEEPly
 * (look count, look ids, canonical garment-id order, style lists, exact score
 * doubles — 0.0 tolerance, both sides round half-even to 4 decimals).
 *
 * A failing case is always an engine bug: the oracle is ground truth and the
 * fixtures are immutable inputs, never adjusted to hide a failure.
 */
class OutfitEngineParityTest {

    private val engine = OutfitEngine(standardPalette(), ThermalMapping.standard())
    private val json = Json

    private fun loadResource(name: String): JsonElement {
        val stream = javaClass.classLoader.getResourceAsStream("parity/$name")
            ?: fail("missing parity fixture resource: parity/$name")
        return stream.use { json.parseToJsonElement(it.readBytes().decodeToString()) }
    }

    private fun JsonObject.strings(field: String): List<String> =
        get(field)!!.jsonArray.map { it.jsonPrimitive.content }

    private fun JsonObject.longs(field: String): List<Long> =
        get(field)!!.jsonArray.map { it.jsonPrimitive.content.toLong() }

    private fun JsonElement.asGarment(): EngineGarment {
        val o = jsonObject
        return EngineGarment(
            id = o["id"]!!.jsonPrimitive.content.toLong(),
            subtype = o["subtype"]!!.jsonPrimitive.content,
            colors = o.strings("colors"),
            styles = o.strings("style"),
        )
    }

    private data class ParityCase(
        val name: String,
        val garments: List<EngineGarment>,
        val targetTempC: Double,
        val styleTags: List<String>,
        val excludeRecent: List<Long>,
        val expected: List<EngineLook>,
    )

    private fun JsonElement.asCase(): ParityCase {
        val o = jsonObject
        val input = o["input"]!!.jsonObject
        val expectedLooks = o["expected"]!!.jsonObject["looks"]!!.jsonArray.map { look ->
            val l = look.jsonObject
            EngineLook(
                id = l["id"]!!.jsonPrimitive.content.toLong(),
                garmentIds = l.longs("garments"),
                styles = l.strings("style"),
                score = l["score"]!!.jsonPrimitive.content.toDouble(),
            )
        }
        return ParityCase(
            name = o["name"]!!.jsonPrimitive.content,
            garments = input["garments"]!!.jsonArray.map { it.asGarment() },
            targetTempC = input["targetTempC"]!!.jsonPrimitive.content.toDouble(),
            styleTags = input.strings("styleTags"),
            excludeRecent = input.longs("excludeRecent"),
            expected = expectedLooks,
        )
    }

    private fun allCaseFiles(): List<String> {
        val manifest = loadResource("manifest.json").jsonObject
        return manifest["files"]!!.jsonArray.map { it.jsonObject["file"]!!.jsonPrimitive.content }
    }

    private fun allCases(): List<ParityCase> = allCaseFiles().flatMap { file ->
        loadResource(file).jsonObject["cases"]!!.jsonArray.map { it.asCase() }
    }

    private fun run(case: ParityCase): LooksResult = engine.looks(
        garments = case.garments,
        targetTempC = case.targetTempC,
        styleTags = case.styleTags,
        excludeRecent = case.excludeRecent.toSet(),
    )

    @Test
    fun manifestDeclaresTheFullSuite() {
        val manifest = loadResource("manifest.json").jsonObject
        assertEquals(18, manifest["totalFiles"]!!.jsonPrimitive.content.toInt())
        assertEquals(240, manifest["totalCases"]!!.jsonPrimitive.content.toInt())
        assertEquals(18, allCaseFiles().size)
        assertEquals(240, allCases().size)
    }

    @Test
    fun parityWithReferenceFixtures() {
        var mismatches = 0
        for (case in allCases()) {
            val actual = run(case)
            val expected = case.expected
            val problems = buildList {
                if (actual.looks.size != expected.size) {
                    add("look count ${actual.looks.size} != ${expected.size}")
                }
                for (i in 0 until minOf(actual.looks.size, expected.size)) {
                    val a = actual.looks[i]
                    val e = expected[i]
                    if (a.id != e.id) add("look[$i] id ${a.id} != ${e.id}")
                    if (a.garmentIds != e.garmentIds) add("look[$i] garments ${a.garmentIds} != ${e.garmentIds}")
                    if (a.styles != e.styles) add("look[$i] styles ${a.styles} != ${e.styles}")
                    if (a.score != e.score) add("look[$i] score ${a.score} != ${e.score}")
                }
            }
            if (problems.isNotEmpty()) {
                mismatches++
                if (mismatches <= 10) {
                    println("PARITY MISMATCH [${case.name}]: ${problems.joinToString("; ")}")
                }
            }
        }
        assertEquals(0, mismatches, "cases deviating from the reference output")
    }

    /**
     * Structural self-checks on every look the engine produces across the whole
     * fixture corpus: coverage rule (body+legs+feet or dress+feet), outerwear
     * mode by temperature, thermal windows (D1), role quotas, canonical order,
     * deterministic id, style discipline. Mirrors the spirit of the oracle's
     * own matrix test without weakening anything.
     */
    @Test
    fun everyProducedLookSatisfiesStructureInvariants() {
        val violations = ArrayList<String>()
        var lookCount = 0
        for (case in allCases()) {
            val result = run(case)
            val byId = case.garments.associateBy { it.id }
            val t = case.targetTempC.coerceIn(
                OutfitEngine.T_MIN_ENGINE,
                OutfitEngine.T_MAX_ENGINE,
            )
            val ids = result.looks.map { it.id }
            if (ids.size != ids.toSet().size) violations += "${case.name}: duplicate look ids"
            for (look in result.looks) {
                lookCount++
                val prefix = "${case.name}/${look.id}"
                val garments = look.garmentIds.map { g ->
                    byId[g] ?: run {
                        violations += "$prefix: unknown garment id $g"
                        return@map null
                    }
                }
                if (garments.any { it == null }) continue
                val roles = garments.filterNotNull().map { engine.roleOf(it.subtype)!! }
                val dup = look.garmentIds.size != look.garmentIds.toSet().size
                if (dup) violations += "$prefix: duplicate garments"
                if ("feet" !in roles) violations += "$prefix: coverage feet missing"
                val hasBody = "body" in roles
                val hasDress = "dress" in roles
                val hasLegs = "legs" in roles
                if (!hasBody && !hasDress) violations += "$prefix: coverage body/dress missing"
                if (hasDress == hasLegs) violations += "$prefix: dress xor legs violated"
                if (roles.count { it == "hat" } > 1) violations += "$prefix: hat quota"
                if (roles.count { it == "scarf" } > 1) violations += "$prefix: scarf quota"
                if (roles.count { it == "accessory" } > 2) violations += "$prefix: accessory quota"
                if (roles.count { it == "bag" } > 1) violations += "$prefix: bag quota"
                val outerCount = roles.count { it == "outer" }
                when (engine.outerMode(t)) {
                    OuterMode.FORBIDDEN -> if (outerCount != 0) violations += "$prefix: outer forbidden"
                    OuterMode.REQUIRED -> if (outerCount != 1) violations += "$prefix: outer required"
                    OuterMode.OPTIONAL -> if (outerCount > 1) violations += "$prefix: outer optional max1"
                }
                for (g in garments.filterNotNull()) {
                    if (!engine.inWindow(g.subtype, t)) violations += "$prefix: ${g.subtype} outside window at T=$t"
                }
                val canonical = engine.canonicalIds(garments.filterNotNull())
                if (canonical != look.garmentIds) violations += "$prefix: non-canonical order"
                if (look.id != engine.lookId(look.garmentIds)) violations += "$prefix: id hash mismatch"
                if (look.score > 1.0 + 1e-9) violations += "$prefix: score above 1: ${look.score}"
                if (case.styleTags.isNotEmpty()) {
                    if (look.styles.isEmpty() || !look.styles.all { it in case.styleTags }) {
                        violations += "$prefix: styles ${look.styles} outside request ${case.styleTags}"
                    }
                }
            }
        }
        assertTrue(lookCount > 300, "expected a rich corpus, got $lookCount looks")
        assertTrue(violations.isEmpty(), "${violations.size} violations, first 10:\n" +
            violations.take(10).joinToString("\n"))
    }
}
