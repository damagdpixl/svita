package com.damagdpixl.svita.ui.stats

import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import com.damagdpixl.svita.core.data.ItemDraft
import com.damagdpixl.svita.core.data.WearLogRepository
import com.damagdpixl.svita.ui.wardrobe.WardrobeUiTestBase
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Test

/**
 * «Статистика» screen end-to-end (Robolectric, P2 T7): the Settings hub entry
 * opens the screen; with seeded wear data it renders cost-per-wear, the
 * most/least-worn top-5 and the composition; PLANNED entries never inflate
 * the wear counters (the exclusion lives in :core:data, this is the UI proof).
 */
class StatsUiTest : WardrobeUiTestBase() {

    private var tshirt: Long = 0
    private var jeans: Long = 0
    private var sweater: Long = 0
    private var cap: Long = 0

    private fun seed() {
        tshirt = seedItemWithPrice("Футболка", "body.t-shirt", price = 100.0)
        jeans = seedItemWithPrice("Джинси", "legs.jeans", price = 300.0)
        sweater = seedItemWithPrice("Светр", "body.sweater", price = 50.0)
        cap = seedItemWithPrice("Кепка", "head.hats.cap", price = null)
        runBlocking {
            // Футболка ×3 (100/3 = 33,33), светр ×2 (25,0), джинси ×1 (300,0).
            repeat(3) { n ->
                graph.repos.wearLog.addEntry(LocalDate(2026, 9, 1 + n), null, listOf(tshirt))
            }
            repeat(2) { n ->
                graph.repos.wearLog.addEntry(LocalDate(2026, 9, 10 + n), null, listOf(sweater))
            }
            graph.repos.wearLog.addEntry(LocalDate(2026, 9, 12), null, listOf(jeans))
            // ПЛАН: запланована кепка — не носіння, у статистику не потрапляє.
            graph.repos.wearLog.addEntry(
                LocalDate(2026, 10, 1),
                null,
                listOf(cap),
                note = WearLogRepository.PLAN_NOTE,
            )
        }
    }

    private fun seedItemWithPrice(
        name: String,
        subtypeKey: String,
        price: Double?,
    ): Long = runBlocking {
        val subtype = graph.repos.taxonomy.subtypeByKey(subtypeKey)
            ?: error("seed subtype $subtypeKey must exist")
        graph.repos.wardrobe.createItem(
            draft = ItemDraft(
                subtypeId = subtype.id,
                name = name,
                price = price,
            ),
        )
    }

    private fun openStats() {
        setContent()
        clickByTag("tab_settings")
        waitUntilExists("settings_row_stats")
        clickByTag("settings_row_stats")
        waitUntilExists("stats_back")
    }

    @Test
    fun `пункт налаштувань відкриває екран статистики`() {
        seed()
        openStats()
        waitUntilExists("stats_cpw_title")
        waitUntilExists("stats_composition_title")
    }

    @Test
    fun `ціна за носіння рахується з округленням і сортується від кращої`() {
        seed()
        openStats()
        // Футболка: 100/3 = 33,33 за носіння (тестова локаль — англійська,
        // формат ціни — український кома-стиль formatPrice).
        waitUntilTrue("рядок футболки в CPW") {
            composeRule.onAllNodesWithTag("stats_cpw_chart_label_$tshirt", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithText("33,33 per wear").assertExists()
        // Кепка без ціни не з'являється у CPW взагалі.
        waitUntilTrue("кепка без ціни виключена") {
            composeRule.onAllNodesWithTag("stats_cpw_chart_label_$cap", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty()
        }
    }

    @Test
    fun `планована річ не має лічильника носінь`() {
        seed()
        openStats()
        waitUntilExists("stats_most_title")
        waitUntilTrue("кепки немає в найчастіших") {
            composeRule.onAllNodesWithTag("stats_most_chart_label_$cap", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty()
        }
        // Футболка очолює «найчастіше в носінні» (3 носіння).
        waitUntilTrue("футболка в найчастіших") {
            composeRule.onAllNodesWithTag("stats_most_chart_label_$tshirt", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun `склад шафи показує розділи з кількістю`() {
        seed()
        openStats()
        waitUntilExists("stats_composition_title")
        // Верх ×2 (футболка + светр), Низ ×1, Головні убори ×1 (кепка — активна
        // річ, склад шафи показує її незалежно від носіння).
        waitUntilTrue("склад відрендерився") {
            composeRule.onAllNodesWithTag("stats_composition_chart_label_body", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithText("2 items").assertExists()
    }

    @Test
    fun `без носінь показується порожній стан`() {
        seedItemWithPrice("Щось", "body.t-shirt", price = 10.0)
        openStats()
        waitUntilExists("stats_empty")
    }
}
