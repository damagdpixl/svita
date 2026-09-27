package com.damagdpixl.svita.ui.calendar

import androidx.compose.ui.test.onAllNodesWithTag
import com.damagdpixl.svita.core.data.ItemDraft
import com.damagdpixl.svita.core.designsystem.EditorialTheme
import com.damagdpixl.svita.core.model.Season
import com.damagdpixl.svita.data.OutfitPrefs
import com.damagdpixl.svita.ui.wardrobe.WardrobeUiTestBase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Calendar end-to-end (Robolectric, P2 T6): the month grid renders with day
 * tags, a past-day sheet logs wear manually, a future-day sheet plans via
 * dress-me (wear_log entry with `note='plan'`), and entries are deletable.
 * The date is pinned to 2026-09-15 => deterministic September norms.
 */
class CalendarUiTest : WardrobeUiTestBase() {

    private val today = LocalDate(2026, Month.SEPTEMBER, 15)
    private val pastDay = LocalDate(2026, Month.SEPTEMBER, 8)
    private val futureDay = LocalDate(2026, Month.SEPTEMBER, 20)

    private fun seedItem(name: String = "Вовняний светр"): Long = runBlocking {
        val subtype = graph.repos.taxonomy.subtypeByKey("body.sweater")
            ?: error("seed subtype body.sweater must exist")
        graph.repos.wardrobe.createItem(
            draft = ItemDraft(subtypeId = subtype.id, name = name, seasons = Season.entries.toSet()),
        )
    }

    /** Seeds enough pieces for dress-me to assemble a look on the fixed date. */
    private fun seedLookWardrobe(): List<Long> = runBlocking {
        listOf(
            "body.sweater",
            "legs.jeans",
            "feet.classic shoes.brogues",
            "body.jacket.classic",
        ).map { subtypeKey ->
            val subtype = graph.repos.taxonomy.subtypeByKey(subtypeKey)
                ?: error("seed subtype $subtypeKey must exist")
            graph.repos.wardrobe.createItem(
                draft = ItemDraft(
                    subtypeId = subtype.id,
                    name = subtypeKey,
                    seasons = Season.entries.toSet(),
                ),
            )
        }
    }

    private fun openCalendar(): CalendarViewModel {
        val vm = CalendarViewModel(graph, today = { today })
        composeRule.setContent {
            EditorialTheme {
                CalendarScreen(viewModel = vm)
            }
        }
        waitUntilExists("calendar_month_title")
        return vm
    }

    @Test
    fun `місяць рендерить клітинки з мітками й легендою`() {
        openCalendar()
        waitUntilExists("calendar_day_$today")
        waitUntilExists("calendar_day_$pastDay")
        waitUntilExists("calendar_day_$futureDay")
        waitUntilExists("calendar_legend_logged")
        waitUntilExists("calendar_legend_planned")
    }

    @Test
    fun `минулий день — додати носіння вручну`() {
        val itemId = seedItem()
        openCalendar()
        clickByTag("calendar_day_$pastDay")
        waitUntilExists("day_sheet")
        waitUntilExists("sheet_add_wear")

        clickByTag("sheet_add_wear")
        waitUntilExists("item_picker")
        clickByTag("pick_item_$itemId")
        clickByTag("picker_apply")

        waitUntilTrue("запис за $pastDay у базі") {
            runBlocking { graph.repos.wearLog.observeByDate(pastDay).first().isNotEmpty() }
        }
        val entry = runBlocking { graph.repos.wearLog.observeByDate(pastDay).first().single() }
        assertEquals(listOf(itemId), entry.itemIds)
        waitUntilTrue("запис видно у day sheet") {
            composeRule.onAllNodesWithTag("sheet_entry_${entry.id}", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `запис у day sheet можна видалити`() {
        val itemId = seedItem()
        runBlocking {
            graph.repos.wearLog.addEntry(date = pastDay, outfitId = null, itemIds = listOf(itemId))
        }
        openCalendar()
        clickByTag("calendar_day_$pastDay")
        val entry = runBlocking { graph.repos.wearLog.observeByDate(pastDay).first().single() }
        waitUntilExists("sheet_entry_${entry.id}")

        clickByTag("sheet_delete_${entry.id}")
        waitUntilTrue("запис видалено з бази") {
            runBlocking { graph.repos.wearLog.observeByDate(pastDay).first().isEmpty() }
        }
        waitUntilGone("sheet_entry_${entry.id}")
    }

    @Test
    fun `майбутній день — запланувати через dress-me з note plan`() {
        seedLookWardrobe()
        openCalendar()
        clickByTag("calendar_day_$futureDay")
        waitUntilExists("day_sheet")
        waitUntilExists("plan_dressme")

        clickByTag("plan_dressme")
        waitUntilTrue("запланований запис за $futureDay") {
            runBlocking {
                val entries = graph.repos.wearLog.observeByDate(futureDay).first()
                entries.any { it.note == OutfitPrefs.PLAN_NOTE && it.itemIds.isNotEmpty() }
            }
        }
        val plan = runBlocking {
            graph.repos.wearLog.observeByDate(futureDay).first().first { it.note == OutfitPrefs.PLAN_NOTE }
        }
        // The planned look carries the September-norms target temp (10.4 °C).
        val planTemp = plan.tempC
        waitUntilTrue("температура цілі у записі") {
            planTemp != null && kotlin.math.abs(planTemp - 10.4) < 1e-6
        }
        waitUntilTrue("плановий запис показано в sheet") {
            composeRule.onAllNodesWithTag("sheet_entry_${plan.id}", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `редагування запису замінює набір речей, зберігаючи дату`() {
        val first = seedItem("Светр")
        val second = seedItem("Джинси")
        runBlocking {
            graph.repos.wearLog.addEntry(date = pastDay, outfitId = null, itemIds = listOf(first))
        }
        openCalendar()
        clickByTag("calendar_day_$pastDay")
        val entry = runBlocking { graph.repos.wearLog.observeByDate(pastDay).first().single() }
        waitUntilExists("sheet_entry_${entry.id}")

        clickByTag("sheet_edit_${entry.id}")
        waitUntilExists("item_picker")
        // Зняти першу річ, додати другу -> набір = [second].
        clickByTag("pick_item_$first")
        clickByTag("pick_item_$second")
        clickByTag("picker_apply")

        waitUntilTrue("запис переписано на новий набір") {
            runBlocking {
                val entries = graph.repos.wearLog.observeByDate(pastDay).first()
                entries.size == 1 && entries.single().itemIds == listOf(second)
            }
        }
    }
}
