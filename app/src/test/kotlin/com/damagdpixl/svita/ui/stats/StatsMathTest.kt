package com.damagdpixl.svita.ui.stats

import com.damagdpixl.svita.core.data.ItemWearCount
import com.damagdpixl.svita.core.model.Item
import com.damagdpixl.svita.core.model.Section
import com.damagdpixl.svita.core.model.Season
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Statistics math gate (P2 T7): cost-per-wear qualification/rounding/order,
 * top-5 slicing with ties, wardrobe composition.
 */
class StatsMathTest {

    private fun item(id: Long, price: Double? = null): Item = Item(
        id = id,
        subtypeId = id,
        name = "Item $id",
        notes = null,
        price = price,
        purchaseDate = null,
        seasons = Season.entries.toSet(),
        sex = null,
        rating = null,
        archived = false,
        createdAt = Instant.fromEpochMilliseconds(0),
        updatedAt = Instant.fromEpochMilliseconds(0),
    )

    // ---------- cost per wear ----------

    @Test
    fun `нульове носіння та відсутня ціна виключаються`() {
        val items = listOf(
            item(1, price = 100.0), // жодного носіння -> геть
            item(2, price = null), // немає ціни -> геть
            item(3), // і те, й інше -> геть
            item(4, price = 50.0), // 2 носіння -> залишається
        )
        val counts = mapOf(1L to 0L, 4L to 2L)
        val rows = costPerWearRows(items, counts)
        assertEquals(listOf(4L), rows.map { it.item.id })
        assertEquals(25.0, rows.single().costPerWear, 1e-9)
    }

    @Test
    fun `округлення до двох знаків`() {
        val rows = costPerWearRows(listOf(item(7, price = 100.0)), mapOf(7L to 3L))
        // 100 / 3 = 33.333... -> 33.33
        assertEquals(33.33, rows.single().costPerWear, 1e-9)
    }

    @Test
    fun `краща цінність першою при однакових значеннях - стабільний порядок за id`() {
        val items = listOf(
            item(30, price = 30.0), // 10,0
            item(10, price = 30.0), // 10,0 — tie
            item(20, price = 10.0), // 5,0 — best
            item(40, price = 60.0), // 20,0 — worst
        )
        val counts = mapOf(10L to 3L, 20L to 2L, 30L to 3L, 40L to 3L)
        val order = costPerWearRows(items, counts).map { it.item.id }
        assertEquals(listOf(20L, 10L, 30L, 40L), order)
    }

    @Test
    fun `round2 тримає півця вгору на представленних значеннях`() {
        assertEquals(0.13, round2(0.125), 1e-9)
        assertEquals(33.33, round2(33.333333), 1e-9)
        assertEquals(1.0, round2(1.0), 1e-9)
    }

    // ---------- top-5 ----------

    @Test
    fun `найчастіше - топ-5 за спаданням нічиї за id`() {
        val items = listOf(1L, 2L, 3L, 4L, 5L, 6L, 7L).map { item(it) }
        val counts = listOf(
            ItemWearCount(1, 5),
            ItemWearCount(2, 9),
            ItemWearCount(3, 1),
            ItemWearCount(4, 9),
            ItemWearCount(5, 3),
            ItemWearCount(6, 9),
            ItemWearCount(7, 9),
        )
        // 9: items 2,4,6,7 (нічия -> за зростанням id); далі 5: item 1.
        assertEquals(
            listOf(2L, 4L, 6L, 7L, 1L),
            topWornRows(items, counts).map { it.item.id },
        )
    }

    @Test
    fun `найрідше - топ-5 за зростанням нічиї за id`() {
        val items = listOf(1L, 2L, 3L, 4L, 5L, 8L, 9L).map { item(it) }
        val counts = listOf(
            ItemWearCount(4, 2),
            ItemWearCount(2, 2),
            ItemWearCount(9, 7),
            ItemWearCount(1, 1),
            ItemWearCount(3, 2),
            ItemWearCount(5, 8),
        )
        // 1: item 1; 2: items 2,3,4 (нічия -> id); далі 7: item 9. item 5 і 8
        // немає в лічильниках (ніколи не носились) -> не потрапляють.
        assertEquals(
            listOf(1L, 2L, 3L, 4L, 9L),
            leastWornRows(items, counts).map { it.item.id },
        )
    }

    @Test
    fun `менше ніж п'ять речей - усі залишаються`() {
        val items = listOf(item(1), item(2))
        val counts = listOf(ItemWearCount(1, 2), ItemWearCount(2, 1))
        assertEquals(2, topWornRows(items, counts).size)
        assertEquals(2, leastWornRows(items, counts).size)
    }

    @Test
    fun `невідомі id у лічильниках відкидаються, а імена підтягуються`() {
        val items = listOf(item(2))
        val counts = listOf(ItemWearCount(1, 9), ItemWearCount(2, 5))
        val rows = topWornRows(items, counts)
        assertEquals(listOf(2L), rows.map { it.item.id })
        assertEquals(5L, rows.single().wearCount)
    }

    // ---------- composition ----------

    @Test
    fun `склад шафи рахує речі за розділами без нульових`() {
        val items = listOf(
            item(1), item(2), item(3), // BODY
            item(4), // LEGS
            item(5), // FEET
        )
        val sectionOf: (Long) -> Section? = { subtypeId ->
            when (subtypeId) {
                1L, 2L, 3L -> Section.BODY
                4L -> Section.LEGS
                5L -> Section.FEET
                else -> null
            }
        }
        val composition = sectionComposition(items, sectionOf)
        assertEquals(
            listOf(
                Section.BODY to 3,
                Section.LEGS to 1,
                Section.FEET to 1,
            ),
            composition.map { it.section to it.count },
        )
    }

    @Test
    fun `порожня шафа - порожній склад, нерозпізнані підтипи ігноруються`() {
        assertEquals(emptyList<SectionCount>(), sectionComposition(emptyList()) { Section.BODY })
        val composition = sectionComposition(listOf(item(1))) { null }
        assertEquals(emptyList<SectionCount>(), composition)
    }
}
