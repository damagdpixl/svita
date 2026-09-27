package com.damagdpixl.svita.ui.stats

import com.damagdpixl.svita.core.data.ItemWearCount
import com.damagdpixl.svita.core.model.Item
import com.damagdpixl.svita.core.model.Section
import kotlin.math.floor

/**
 * Pure statistics math for the «Статистика» screen (P2 T7).
 *
 * Contract:
 * - cost-per-wear = price / wearCount, rounded to 2 decimals; only items with
 *   a price AND at least one wear qualify — zero-wear and no-price items are
 *   excluded, never rendered as 0 or ∞;
 * - «best value first» = lowest cost-per-wear first; ties resolve by item id
 *   ascending, so the order is stable and deterministic across reloads;
 * - top-5 lists slice the SAME single `wearCounts()` result (P2 contract: one
 *   call, no per-item queries); ties resolve by item id ascending in both
 *   directions;
 * - wardrobe composition counts ACTIVE items per section; sections without
 *   items are omitted (the screen shows what exists).
 */

/** One cost-per-wear row: the item, its wear count and the rounded value. */
data class CostPerWearRow(
    val item: Item,
    val wearCount: Long,
    val costPerWear: Double,
)

/** One most/least-worn row: the item plus its wear count. */
data class WornRow(
    val item: Item,
    val wearCount: Long,
)

/** One wardrobe-composition row: section and its item count (>= 1). */
data class SectionCount(
    val section: Section,
    val count: Int,
)

/**
 * Rounds to 2 decimals, half-up (0.125 -> 0.13). `kotlin.math.round` is
 * ties-to-even (0.125 -> 0.12), which reads as a wrong price — the classic
 * `floor(x * 100 + 0.5)` keeps the money convention. Non-negative inputs
 * only (prices and counts are never negative).
 */
fun round2(value: Double): Double = floor(value * 100.0 + 0.5) / 100.0

/**
 * Cost-per-wear rows, best value first. [wearCounts] is the item id -> count
 * map of a single `wearCounts()` call; items missing from it are treated as
 * never worn and excluded.
 */
fun costPerWearRows(items: List<Item>, wearCounts: Map<Long, Long>): List<CostPerWearRow> =
    items.mapNotNull { item ->
        val price = item.price ?: return@mapNotNull null
        val wears = wearCounts[item.id] ?: return@mapNotNull null
        if (wears < 1) return@mapNotNull null
        CostPerWearRow(item = item, wearCount = wears, costPerWear = round2(price / wears))
    }.sortedWith(compareBy<CostPerWearRow> { it.costPerWear }.thenBy { it.item.id })

/** Most-worn top-[limit], descending; ties by item id ascending. */
fun topWornRows(items: List<Item>, wearCounts: List<ItemWearCount>, limit: Int = 5): List<WornRow> =
    wearCounts.sortedWith(compareByDescending<ItemWearCount> { it.wearCount }.thenBy { it.itemId })
        .take(limit)
        .mapNotNull { count -> items.byId(count.itemId)?.let { WornRow(it, count.wearCount) } }

/** Least-worn top-[limit], ascending; ties by item id ascending. */
fun leastWornRows(items: List<Item>, wearCounts: List<ItemWearCount>, limit: Int = 5): List<WornRow> =
    wearCounts.sortedWith(compareBy<ItemWearCount> { it.wearCount }.thenBy { it.itemId })
        .take(limit)
        .mapNotNull { count -> items.byId(count.itemId)?.let { WornRow(it, count.wearCount) } }

/**
 * Composition of the wardrobe by section, canonical section order, zero-count
 * sections omitted. [sectionOf] resolves an item's subtype id to its section
 * (null = unresolvable, excluded).
 */
fun sectionComposition(items: List<Item>, sectionOf: (Long) -> Section?): List<SectionCount> =
    Section.entries.mapNotNull { section ->
        val count = items.count { sectionOf(it.subtypeId) == section }
        if (count == 0) null else SectionCount(section, count)
    }

private fun List<Item>.byId(id: Long): Item? = firstOrNull { it.id == id }
