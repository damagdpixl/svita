package com.damagdpixl.svita.ui.wardrobe

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import kotlinx.datetime.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Екран речі: лічильник носінь береться з пакетного wearCounts() (не з
 * поелемітного lastWorn), видалення вимагає підтвердження й справді стирає
 * річ із бази.
 */
class ItemDetailTest : WardrobeUiTestBase() {

    private fun openDetail(id: Long) {
        scrollToGridItem("item_card_$id")
        clickByTag("item_card_$id")
        waitUntilExists("detail_root")
        waitUntilExists("detail_wear_count")
    }

    @Test
    fun `деталі показують лічильник носінь з пакетного запиту`() {
        val id = seedItem(name = "Улюблена сорочка", subtypeKey = "body.t-shirt")
        runBlocking {
            graph.repos.wearLog.addEntry(LocalDate(2026, 9, 20), null, listOf(id))
            graph.repos.wearLog.addEntry(LocalDate(2026, 9, 25), null, listOf(id))
        }

        setContent()
        waitUntilExists("wardrobe_grid")
        openDetail(id)

        composeRule.onNodeWithTag("detail_wear_count").assertTextContains("2", substring = true)
    }

    @Test
    fun `видалення вимагає підтвердження і стирає річ`() {
        val id = seedItem(name = "Річ на видалення", subtypeKey = "body.t-shirt")

        setContent()
        waitUntilExists("wardrobe_grid")
        openDetail(id)

        // Скасування: річ залишається.
        clickByTag("detail_delete")
        waitUntilExists("delete_scrim")
        clickByTag("delete_cancel")
        waitUntilGone("delete_scrim")
        assertTrue(
            runBlocking {
                graph.repos.wardrobe
                    .observeItems(com.damagdpixl.svita.core.data.ItemFilter(includeArchived = true))
                    .first()
                    .any { it.id == id }
            },
        )

        // Підтвердження: річ зникає з бази, повертає на сітку.
        clickByTag("detail_delete")
        waitUntilExists("delete_scrim")
        composeRule.onNodeWithTag("delete_confirm").performClick()
        waitUntilGone("detail_root")
        waitUntilExists("screen_wardrobe")
        assertFalse(
            runBlocking {
                graph.repos.wardrobe
                    .observeItems(com.damagdpixl.svita.core.data.ItemFilter(includeArchived = true))
                    .first()
                    .any { it.id == id }
            },
        )
    }
}
