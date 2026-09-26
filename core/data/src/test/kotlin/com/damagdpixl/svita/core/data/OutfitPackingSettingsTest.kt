package com.damagdpixl.svita.core.data

import com.damagdpixl.svita.core.model.OutfitEntry
import com.damagdpixl.svita.core.model.Section
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Outfits (aggregate round-trip, entry replacement), packing lists
 * (round-trip, packed toggle, cascades) and typed settings.
 */
class OutfitPackingSettingsTest {
    private lateinit var f: RepositoriesFixture
    private var tshirt: Long = 0
    private var jeans: Long = 0

    @Before
    fun setUp() = runBlocking {
        f = RepositoriesFixture()
        tshirt = f.createItem("Футболка", f.subTShirt)
        jeans = f.createItem("Джинси", f.subJeans)
    }

    // ---------- Outfits ----------

    @Test
    fun `образ — створення, читання, заміна вхідних даних`() = runBlocking {
        val id = f.outfits.createOutfit(
            OutfitDraft("Прогулянка", rating = 4, note = "літній варіант"),
            entries = listOf(OutfitEntry(0L, tshirt, Section.BODY)),
        )
        val agg = f.outfits.getOutfit(id)!!
        assertEquals("Прогулянка", agg.outfit.name)
        assertEquals(4, agg.outfit.rating)
        assertEquals("літній варіант", agg.outfit.note)
        assertEquals(listOf(tshirt), agg.entries.map { it.itemId })
        assertEquals(Section.BODY, agg.entries.single().slot)

        f.outfits.updateOutfit(
            id,
            OutfitDraft("Прогулянка 2", rating = 5, note = null),
            entries = listOf(
                OutfitEntry(0L, jeans, Section.LEGS),
                OutfitEntry(0L, tshirt, Section.BODY),
            ),
        )
        val updated = f.outfits.getOutfit(id)!!
        assertEquals("Прогулянка 2", updated.outfit.name)
        assertNull(updated.outfit.note)
        // Entries read back in item_id order (no position column in schema v1).
        assertEquals(listOf(tshirt, jeans).sorted(), updated.entries.map { it.itemId }.sorted())
        assertEquals(
            listOf(Section.BODY, Section.LEGS).sorted(),
            updated.entries.map { it.slot }.sorted(),
        )

        assertEquals(1, f.outfits.observeOutfits().first().size)
        assertNull(f.outfits.getOutfit(999L))

        val missing = runCatching { f.outfits.updateOutfit(999L, OutfitDraft("x"), entries = emptyList()) }
            .exceptionOrNull()
        assertTrue("expected NoSuchElementException, got $missing", missing is NoSuchElementException)
    }

    @Test
    fun `видалення речі прибирає її з образів`() = runBlocking {
        val outfit = f.outfits.createOutfit(
            OutfitDraft("Прогулянка"),
            entries = listOf(
                OutfitEntry(0L, tshirt, Section.BODY),
                OutfitEntry(0L, jeans, Section.LEGS),
            ),
        )
        f.wardrobe.deleteItem(tshirt)
        assertEquals(listOf(jeans), f.outfits.getOutfit(outfit)!!.entries.map { it.itemId })
    }

    // ---------- Packing ----------

    @Test
    fun `список пакування — round-trip`() = runBlocking {
        val id = f.packing.createPackingList(
            "Виїзд на море",
            dateFrom = LocalDate(2025, 7, 1),
            dateTo = LocalDate(2025, 7, 10),
            itemIds = listOf(tshirt, jeans),
        )
        val agg = f.packing.getPackingList(id)!!
        assertEquals("Виїзд на море", agg.list.title)
        assertEquals(LocalDate(2025, 7, 1), agg.list.dateFrom)
        assertEquals(LocalDate(2025, 7, 10), agg.list.dateTo)
        assertEquals(listOf(tshirt, jeans), agg.entries.map { it.itemId })
        assertFalse(agg.entries.any { it.packed })

        // Toggle packed + idempotent add (не скидає прапорець і не дублює).
        f.packing.setPacked(id, tshirt, true)
        f.packing.addPackingItem(id, tshirt)
        val afterToggle = f.packing.getPackingList(id)!!
        assertEquals(listOf(tshirt, jeans), afterToggle.entries.map { it.itemId })
        assertEquals(listOf(true, false), afterToggle.entries.map { it.packed })

        // Remove + update fields.
        f.packing.removePackingItem(id, jeans)
        f.packing.updatePackingList(id, "Виїзд у гори", LocalDate(2025, 8, 1), null)
        val updated = f.packing.getPackingList(id)!!
        assertEquals("Виїзд у гори", updated.list.title)
        assertEquals(LocalDate(2025, 8, 1), updated.list.dateFrom)
        assertNull(updated.list.dateTo)
        assertEquals(listOf(tshirt), updated.entries.map { it.itemId })

        assertEquals(1, f.packing.observePackingLists().first().size)
        assertNull(f.packing.getPackingList(999L))
    }

    @Test
    fun `видалення списку пакування каскадно прибирає записи`() = runBlocking {
        val id = f.packing.createPackingList("Виїзд", itemIds = listOf(tshirt))
        f.packing.deletePackingList(id)
        assertNull(f.packing.getPackingList(id))
        assertEquals(0, f.db.packingQueries.selectAllPackingItems().executeAsList().size)
        // Річ, звісно, жива.
        assertTrue(f.wardrobe.getItem(tshirt) != null)
    }

    @Test
    fun `додавання речі в список з неіснуючим id падає`() = runBlocking {
        val id = f.packing.createPackingList("Виїзд")
        val thrown = runCatching { f.packing.addPackingItem(id, 999L) }.exceptionOrNull()
        assertTrue("expected FK failure, got $thrown", thrown != null)
    }

    // ---------- Settings ----------

    @Test
    fun `налаштування — типізований доступ і видалення`() = runBlocking {
        val s = f.settings
        assertNull(s.getString("units"))

        s.putString("units", "metric")
        assertEquals("metric", s.getString("units"))
        s.putString("units", "imperial") // upsert
        assertEquals("imperial", s.getString("units"))

        s.putBoolean("dark", true)
        assertTrue(s.getBoolean("dark", false))
        assertEquals(false, s.getBoolean("missing", false))

        s.putInt("rail.page", 3)
        assertEquals(3, s.getInt("rail.page", 0))
        assertEquals(7, s.getInt("missing", 7))

        s.putLong("installed.at", 1_760_000_000_000L)
        assertEquals(1_760_000_000_000L, s.getLong("installed.at", 0L))

        s.putDouble("ratio", 0.75)
        assertEquals(0.75, s.getDouble("ratio", 1.0), 0.0)

        // Брудне значення -> default, не крах.
        s.putString("ratio", "abc")
        assertEquals(1.0, s.getDouble("ratio", 1.0), 0.0)

        s.remove("units")
        assertNull(s.getString("units"))
        assertEquals("default", s.getString("units") ?: "default")
    }

    @Test
    fun `налаштування — спостереження реагує на запис`() = runBlocking {
        val flow = f.settings.observe("units")
        assertNull(flow.first())

        val next = flow.nextAfter { f.settings.putString("units", "metric") }
        assertEquals("metric", next)
    }
}
