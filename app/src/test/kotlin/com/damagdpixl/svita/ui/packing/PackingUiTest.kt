package com.damagdpixl.svita.ui.packing

import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import com.damagdpixl.svita.ui.wardrobe.WardrobeUiTestBase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Packing end-to-end (Robolectric, P2 T7): the wizard creates a trip persisted
 * with the suggestion union (real wears AND planned entries in the range),
 * the `packed` toggle persists, and the list deletes. All assertions check the
 * database through [com.damagdpixl.svita.core.data.PackingRepository], not just
 * the UI.
 */
class PackingUiTest : WardrobeUiTestBase() {

    private var tshirt: Long = 0
    private var jeans: Long = 0
    private var cap: Long = 0

    private fun seedWardrobe() {
        tshirt = seedItem("Футболка", "body.t-shirt")
        jeans = seedItem("Джинси", "legs.jeans")
        cap = seedItem("Кепка", "head.hats.cap")
    }

    /** Real wear on the 5th, PLAN on the 7th — both inside the trip window. */
    private fun seedWearAndPlan() = runBlocking {
        graph.repos.wearLog.addEntry(LocalDate(2026, 10, 5), null, listOf(tshirt, jeans))
        graph.repos.wearLog.addEntry(
            LocalDate(2026, 10, 7),
            null,
            listOf(cap),
            note = com.damagdpixl.svita.core.data.WearLogRepository.PLAN_NOTE,
        )
    }

    private fun openPackingTab() {
        setContent()
        clickByTag("tab_packing")
        // Таб рендерить один із двох станів: порожній (CTA) або список поїздок.
        waitUntilTrue("вкладка «Пакування» відрендерилась") {
            composeRule.onAllNodesWithTag("packing_new_empty", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty() ||
                composeRule.onAllNodesWithTag("packing_trip_1", useUnmergedTree = true)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
        }
    }

    @Test
    fun `майстер створює список із запропонованими речами разом із запланованими`() {
        seedWardrobe()
        seedWearAndPlan()
        openPackingTab()

        clickByTag("packing_new_empty")
        waitUntilExists("packing_wizard")
        composeRule.onNodeWithTag("packing_wizard_name").performTextInput("Каракум")
        composeRule.onNodeWithTag("packing_wizard_from").performTextInput("2026-10-04")
        composeRule.onNodeWithTag("packing_wizard_to").performTextInput("2026-10-08")

        // Union of the range: wear items + the PLANNED cap.
        waitUntilExists("packing_suggested_$cap")
        waitUntilExists("packing_suggested_$tshirt")
        waitUntilExists("packing_suggested_$jeans")

        clickByTag("packing_create")
        waitUntilExists("packing_trip_1")

        runBlocking {
            val aggregate = graph.repos.packing.getPackingList(1)
            assertNotNull("список має бути збережений", aggregate)
            assertEquals("Каракум", aggregate!!.list.title)
            assertEquals(LocalDate(2026, 10, 4), aggregate.list.dateFrom)
            assertEquals(LocalDate(2026, 10, 8), aggregate.list.dateTo)
            assertEquals(
                "валіза = носіння + плани, без дублікатів",
                setOf(tshirt, jeans, cap),
                aggregate.entries.map { it.itemId }.toSet(),
            )
            assertTrue("усі позиції починаються незібраними", aggregate.entries.none { it.packed })
        }
    }

    @Test
    fun `перемикач зібрано зберігається в базі`() {
        seedWardrobe()
        runBlocking {
            graph.repos.packing.createPackingList(
                "Листопад",
                LocalDate(2026, 11, 1),
                LocalDate(2026, 11, 5),
                listOf(tshirt, jeans),
            )
        }
        openPackingTab()
        waitUntilExists("packing_trip_1")
        clickByTag("packing_trip_1")
        waitUntilExists("trip_progress")

        clickByTag("trip_item_${tshirt}_toggle")
        waitUntilTrue("галочка збереглась") {
            runBlocking {
                graph.repos.packing.getPackingList(1)!!
                    .entries
                    .first { it.itemId == tshirt }
                    .packed
            }
        }

        // Progress counts the packed row.
        waitUntilTrue("прогрес 1 з 2") {
            composeRule.onAllNodesWithText("1 of 2 packed", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        runBlocking {
            val entries = graph.repos.packing.getPackingList(1)!!.entries
            assertEquals(1, entries.count { it.packed })
            assertFalse(entries.first { it.itemId == jeans }.packed)
        }
    }

    @Test
    fun `видалення списку прибирає його з бази й повертає порожній стан`() {
        seedWardrobe()
        runBlocking {
            graph.repos.packing.createPackingList("На вихідні", null, null, listOf(tshirt))
        }
        openPackingTab()
        waitUntilExists("packing_trip_1")
        clickByTag("packing_trip_1")
        waitUntilExists("trip_delete")

        clickByTag("trip_delete")
        waitUntilExists("trip_delete_scrim")
        clickByTag("trip_delete_confirm")

        waitUntilExists("packing_new_empty")
        runBlocking {
            assertTrue(graph.repos.packing.observePackingLists().first().isEmpty())
        }
    }

    @Test
    fun `пошук у пікері фільтрує речі за назвою`() {
        seedWardrobe()
        runBlocking {
            graph.repos.packing.createPackingList("Листопад", null, null, emptyList())
        }
        openPackingTab()
        waitUntilExists("packing_trip_1")
        clickByTag("packing_trip_1")
        waitUntilExists("trip_add_items")

        clickByTag("trip_add_items")
        waitUntilExists("packing_picker_search")
        composeRule.onNodeWithTag("packing_picker_search").performTextInput("джин")
        waitUntilExists("picker_item_$jeans")
        waitUntilTrue("футболка відфільтрована") {
            composeRule.onAllNodesWithTag("picker_item_$tshirt", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty()
        }
    }
}
