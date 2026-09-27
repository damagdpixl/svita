package com.damagdpixl.svita.ui.wardrobe

import androidx.compose.ui.test.onNodeWithTag
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Архів: за замовчуванням сховані, перемикач «Архів» показує їх; архівування
 * з екрана деталей ховає річ із активної сітки.
 */
class WardrobeArchiveTest : WardrobeUiTestBase() {



    @Test
    fun `архівні речі сховані доки не увімкнено перемикач`() {
        val activeId = seedItem(name = "Активна сорочка", subtypeKey = "body.t-shirt")
        val archivedId = seedItem(name = "Стара сорочка", subtypeKey = "body.t-shirt", archived = true)
        assertTrue(activeId > 0 && archivedId > 0)

        setContent()
        waitUntilExists("wardrobe_grid")
        scrollToGridItem("item_card_$activeId")
        waitUntilExists("item_card_$activeId")

        clickByTag("wardrobe_archive_toggle")
        scrollToGridItem("item_card_$archivedId")
        waitUntilExists("item_card_$archivedId")
        scrollToGridItem("item_card_$activeId")
        waitUntilExists("item_card_$activeId")
    }

    @Test
    fun `архівування з деталей ховає річ із сітки`() {
        val id = seedItem(name = "Куртка на осінь", subtypeKey = "body.jacket.denim")
        setContent()
        waitUntilExists("wardrobe_grid")
        scrollToGridItem("item_card_$id")
        waitUntilExists("item_card_$id")

        clickByTag("item_card_$id")
        waitUntilExists("detail_root")
        waitUntilExists("detail_archive")
        clickByTag("detail_archive")

        // Після перезавантаження агрегата кнопка показує «З архіву».
        waitUntilExists("detail_back")
        clickByTag("detail_back")

        waitUntilExists("screen_wardrobe")
        waitUntilGone("item_card_$id")
        val item = runBlocking {
            graph.repos.wardrobe
                .observeItems(com.damagdpixl.svita.core.data.ItemFilter(includeArchived = true))
                .first()
                .first { it.id == id }
        }
        assertTrue(item.archived)
        assertFalse(
            runBlocking {
                graph.repos.wardrobe.observeItems().first().any { it.id == id }
            },
        )
    }
}
