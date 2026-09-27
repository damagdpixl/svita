package com.damagdpixl.svita.ui.wardrobe

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.damagdpixl.svita.core.model.Season
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Сітка шафи: дебаунс-пошук за назвою, панель фільтрів (сезон, тег) звужує
 * видачу відповідно до семантики ItemFilter.
 */
class WardrobeFiltersTest : WardrobeUiTestBase() {



    @Test
    fun `пошук за назвою фільтрує сітку`() {
        seedItem(name = "Сині джинси", subtypeKey = "legs.jeans", seasons = setOf(Season.SPRING, Season.SUMMER))
        seedItem(name = "Вечірня сукня", subtypeKey = "body.dress.mini", seasons = setOf(Season.WINTER))

        setContent()
        waitUntilExists("wardrobe_grid")
        scrollToGridItem("item_card_1")
        waitUntilExists("item_card_1")
        waitUntilExists("item_card_2")

        composeRule.onNodeWithTag("wardrobe_search").performTextInput("джин")
        advanceLooperTime()
        waitUntilGone("item_card_2")
        waitUntilExists("item_card_1")

        composeRule.onNodeWithTag("wardrobe_search").performTextClearance()
        advanceLooperTime()
        waitUntilExists("item_card_2")
    }

    @Test
    fun `фільтр сезону показує лише зимові речі`() {
        seedItem(name = "Сині джинси", subtypeKey = "legs.jeans", seasons = setOf(Season.SPRING, Season.SUMMER))
        seedItem(name = "Вечірня сукня", subtypeKey = "body.dress.mini", seasons = setOf(Season.WINTER))

        setContent()
        waitUntilExists("wardrobe_grid")
        scrollToGridItem("item_card_1")
        waitUntilExists("item_card_1")

        clickByTag("wardrobe_filters_button")
        waitUntilExists("filter_scrim")
        clickByTag("filter_season_WINTER")
        clickByTag("filter_apply")

        waitUntilGone("item_card_1")
        waitUntilExists("item_card_2")
    }

    @Test
    fun `фільтр тегів вимагає всі теги одразу`() {
        val denim = runBlocking { tagId("denim") }
        seedItem(name = "Сині джинси", subtypeKey = "legs.jeans", tags = setOf(denim))
        seedItem(name = "Вечірня сукня", subtypeKey = "body.dress.mini")

        setContent()
        waitUntilExists("wardrobe_grid")
        scrollToGridItem("item_card_1")
        waitUntilExists("item_card_1")

        clickByTag("wardrobe_filters_button")
        waitUntilExists("filter_scrim")
        clickByText("denim")
        clickByTag("filter_apply")

        waitUntilGone("item_card_2")
        waitUntilExists("item_card_1")

        // База відповідає тим самим семантикам (conjunctive ALL).
        val filtered = runBlocking {
            graph.repos.wardrobe
                .observeItems(
                    com.damagdpixl.svita.core.data.ItemFilter(tagIds = setOf(denim)),
                )
                .first()
        }
        assertEquals(listOf("Сині джинси"), filtered.map { it.name })
    }
}
