package com.damagdpixl.svita.ui.outfits

import androidx.compose.ui.test.onAllNodesWithTag
import com.damagdpixl.svita.core.data.ItemDraft
import com.damagdpixl.svita.core.designsystem.EditorialTheme
import com.damagdpixl.svita.core.model.Season
import com.damagdpixl.svita.ui.wardrobe.WardrobeUiTestBase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import org.junit.Test

/**
 * Outfits tab end-to-end (Robolectric, P2 T6): the «Сьогодні» panel renders the
 * engine look (paper doll + flat-lay) from the seeded mini-wardrobe with the
 * «офлайн» badge (offline graph mode = climate norms), «Носити» writes
 * today's wear_log entry, and the «не носилось останні 60 днів» section lists
 * idle items. The date is pinned to 2026-09-15 => deterministic September norms.
 */
class OutfitsTodayUiTest : WardrobeUiTestBase() {

    private val today = LocalDate(2026, Month.SEPTEMBER, 15)

    private fun seedWardrobe(): List<Long> = runBlocking {
        listOf(
            "body.sweater" to "Вовняний светр",
            "legs.jeans" to "Сині джинси",
            "feet.classic shoes.brogues" to "Брогі",
            "body.jacket.classic" to "Класична куртка",
        ).map { (subtypeKey, name) ->
            val subtype = graph.repos.taxonomy.subtypeByKey(subtypeKey)
                ?: error("seed subtype $subtypeKey must exist")
            graph.repos.wardrobe.createItem(
                draft = ItemDraft(subtypeId = subtype.id, name = name, seasons = Season.entries.toSet()),
            )
        }
    }

    private fun openOutfits(): List<Long> {
        val ids = seedWardrobe()
        val vm = OutfitsViewModel(graph, today = { today })
        composeRule.setContent {
            EditorialTheme {
                OutfitsScreen(viewModel = vm)
            }
        }
        waitUntilExists("outfit_look_avatar")
        return ids
    }

    @Test
    fun `сьогодні рендерить образ з офлайн-бейджем і колажем`() {
        openOutfits()
        waitUntilExists("outfit_offline_badge")
        waitUntilExists("outfit_look_collage")
        waitUntilExists("outfit_weather")
        // Усі чотири речі образу перелічені.
        runBlocking {
            val lookItems = graph.repos.wardrobe
                .observeItems()
                .first()
                .map { it.id }
            lookItems.forEach { id ->
                waitUntilTrue("річ $id у списку образу") {
                    composeRule.onAllNodesWithTag("look_item_$id", useUnmergedTree = true)
                        .fetchSemanticsNodes().isNotEmpty()
                }
            }
        }
    }

    @Test
    fun `носити пише запис у wear_log за сьогодні`() {
        openOutfits()
        clickByTag("outfit_wear_today")
        waitUntilExists("outfit_worn_confirm")
        waitUntilTrue("запис у wear_log за $today") {
            runBlocking {
                graph.repos.wearLog.observeByDate(today).first().isNotEmpty()
            }
        }
        val entry = runBlocking { graph.repos.wearLog.observeByDate(today).first().single() }
        waitUntilTrue("усі речі образу у записі") {
            entry.itemIds.size == 4
        }
    }

    @Test
    fun `секція не носилось показує речі без носіння у вікні 60 днів`() {
        val ids = openOutfits()
        waitUntilExists("notworn_title")
        waitUntilExists("notworn_badge")
        ids.forEach { id ->
            waitUntilTrue("річ $id у секції «не носилось»") {
                composeRule.onAllNodesWithTag("notworn_item_$id", useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
        }
        // Після «Носити» всі чотири речі виходять із вікна простою.
        clickByTag("outfit_wear_today")
        waitUntilTrue("секція спорожніла") {
            composeRule.onAllNodesWithTag("notworn_empty", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `порожня шафа показує підказку`() {
        val vm = OutfitsViewModel(graph, today = { today })
        composeRule.setContent {
            EditorialTheme {
                OutfitsScreen(viewModel = vm)
            }
        }
        waitUntilExists("outfit_empty_wardrobe")
    }
}
