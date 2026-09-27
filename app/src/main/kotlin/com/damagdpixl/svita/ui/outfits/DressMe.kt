package com.damagdpixl.svita.ui.outfits

import com.damagdpixl.svita.core.data.ItemAggregate
import com.damagdpixl.svita.core.engine.EngineGarment
import com.damagdpixl.svita.core.model.AttributeType
import com.damagdpixl.svita.core.model.PaletteColor
import com.damagdpixl.svita.core.model.Tag
import kotlinx.datetime.LocalDate
import java.time.ZoneId

/**
 * Today in an EXPLICIT zone (P2 T6 date contract): every screen and ViewModel
 * resolves «today» through this seam, never through an ambient `now()`, so the
 * dress-me pick, the calendar grid and the wear log always agree on the same
 * date regardless of device timezone. Production passes
 * [ZoneId.systemDefault]; tests pin a fixed lambda (or a fixed [java.time.Clock]
 * to prove the zone arithmetic).
 */
fun todayIn(
    zone: ZoneId = ZoneId.systemDefault(),
    clock: java.time.Clock = java.time.Clock.system(zone),
): LocalDate {
    val now = java.time.LocalDate.now(clock.withZone(zone))
    return LocalDate(now.year, now.monthValue, now.dayOfMonth)
}

/**
 * One dress-me result ready for the UI: the engine look plus the wardrobe
 * aggregates behind its garment ids, in the engine's canonical order, with the
 * target temperature and the styles that survived.
 */
data class DressMeLook(
    /** Deterministic engine look id (crc32 of the sorted garment ids). */
    val lookId: Long,
    val items: List<ItemAggregate>,
    val styles: List<String>,
    /** Engine target temperature the look was assembled for (°C). */
    val targetTempC: Double,
    /** Minimum temperature of the weather sample behind the target (°C). */
    val weatherMinC: Double,
    /** Maximum temperature of the weather sample behind the target (°C). */
    val weatherMaxC: Double,
    /** Coarse condition label of the weather sample (repo-owned category). */
    val condition: String,
    /** True when the climate-norms fallback (not live/cache weather) answered. */
    val offline: Boolean,
)

/**
 * Wardrobe -> engine bridge (P2 T6): turns item aggregates into
 * [EngineGarment]s for `OutfitEngine.looks`.
 *
 * Mapping contract:
 * - subtype: the seeded subtype KEY (e.g. `body.t-shirt`) — the same key the
 *   standard thermal table and the role rules are written against;
 * - colors: the item's COLOR-type attribute values, resolved to palette KEYS
 *   (the engine's harmony math is palette-keyed). A value stored as a palette
 *   key is matched case-insensitively; a value stored as `#RRGGBB` is matched
 *   against the palette hexes (first seed match wins); anything else is
 *   skipped and the garment stays engine-neutral (no colors);
 * - styles: the item's user tag names lowercased and intersected with the
 *   seeded style-tag keys (`casual`, `classic`, …) — tags are free-form, so
 *   only the ones that coincide with a system style key feed the engine.
 */
class EngineGarmentMapper(palette: List<PaletteColor>) {

    /** lowercase palette key -> canonical key. */
    private val keyIndex: Map<String, String> = palette.associate { it.key.lowercase() to it.key }

    /** normalized palette hex -> canonical key (first seed match wins). */
    private val hexIndex: Map<String, String> = palette
        .map { it.hex.trim().removePrefix("#").uppercase() to it.key }
        .toMap()

    fun garment(aggregate: ItemAggregate, colorDefinitionIds: Set<Long>, styleKeys: Set<String>): EngineGarment =
        EngineGarment(
            id = aggregate.item.id,
            subtype = aggregate.subtype.key,
            colors = colorsOf(aggregate, colorDefinitionIds),
            styles = stylesOf(aggregate, styleKeys),
        )

    private fun colorsOf(aggregate: ItemAggregate, colorDefinitionIds: Set<Long>): List<String> =
        aggregate.attributes
            .filter { it.definitionId in colorDefinitionIds }
            .mapNotNull { entry -> resolveColorKey(entry.value) }
            .distinct()

    private fun resolveColorKey(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        keyIndex[value.lowercase()]?.let { return it }
        return hexIndex[value.removePrefix("#").uppercase()]
    }

    private fun stylesOf(aggregate: ItemAggregate, styleKeys: Set<String>): List<String> =
        if (styleKeys.isEmpty()) {
            emptyList()
        } else {
            aggregate.tags.mapNotNull { styleKeyOf(it, styleKeys) }.distinct()
        }

    private fun styleKeyOf(tag: Tag, styleKeys: Set<String>): String? {
        val candidate = tag.name.trim().lowercase()
        return candidate.takeIf { it in styleKeys }
    }
}
