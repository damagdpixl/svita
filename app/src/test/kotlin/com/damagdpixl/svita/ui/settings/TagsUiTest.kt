package com.damagdpixl.svita.ui.settings

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.damagdpixl.svita.core.data.ItemDraft
import com.damagdpixl.svita.ui.wardrobe.WardrobeUiTestBase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Менеджер тегів: перейменування на місці (зв'язки з речами живуть),
 * дублікат назви дає українську помилку в діалозі, видалення знімає тег.
 */
class TagsUiTest : WardrobeUiTestBase() {

    private fun openTags() {
        setContent()
        // Шафа може бути і порожньою, і з сідованими речами — чекаємо корінь.
        waitUntilExists("screen_wardrobe")
        clickByTag("tab_settings")
        waitUntilExists("settings_row_tags")
        clickByTag("settings_row_tags")
        waitUntilExists("tags_root")
        waitUntilExists("tags_list")
    }

    @Test
    fun `перейменування тегу оновлює список і речі`() {
        val itemId = runBlocking {
            val tag = graph.repos.wardrobe.createTag("літнє")
            val subtype = graph.repos.taxonomy.subtypeByKey("body.t-shirt")!!
            graph.repos.wardrobe.createItem(
                draft = ItemDraft(subtypeId = subtype.id, name = "Сорочка"),
                tagIds = setOf(tag),
            )
        }
        openTags()

        clickByTag("tag_rename_1")
        waitUntilExists("tag_rename_scrim")
        composeRule.onNodeWithTag("tag_rename_input").performTextClearance()
        composeRule.onNodeWithTag("tag_rename_input").performTextInput("спекотне")
        clickByTag("tag_rename_save")

        // Діалог закрився після успіху, у списку нова назва.
        waitUntilGone("tag_rename_scrim")
        waitUntilTrue("нова назва у списку") {
            composeRule.onAllNodesWithText("спекотне")
                .fetchSemanticsNodes().isNotEmpty()
        }
        val tags = runBlocking { graph.repos.wardrobe.getItem(itemId.toLong())!!.tags }
        assertEquals(listOf("спекотне"), tags.map { it.name })
    }

    @Test
    fun `дублікат назви показує українську помилку і лишає діалог`() {
        runBlocking {
            graph.repos.wardrobe.createTag("літнє")
            graph.repos.wardrobe.createTag("зимове")
        }
        openTags()

        // Перейменовуємо «літнє» (id 1) у «зимове» — UNIQUE.
        clickByTag("tag_rename_1")
        waitUntilExists("tag_rename_scrim")
        composeRule.onNodeWithTag("tag_rename_input").performTextClearance()
        composeRule.onNodeWithTag("tag_rename_input").performTextInput("зимове")
        clickByTag("tag_rename_save")

        waitUntilTrue("помилка дубліката показана") {
            composeRule.onAllNodesWithText("A tag with this name already exists", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        // Діалог лишився відкритим — можна виправити назву.
        waitUntilExists("tag_rename_scrim")
        composeRule.onNodeWithTag("tag_rename_input").performTextClearance()
        composeRule.onNodeWithTag("tag_rename_input").performTextInput("дощове")
        clickByTag("tag_rename_save")
        waitUntilGone("tag_rename_scrim")

        assertEquals("дощове", runBlocking { graph.repos.wardrobe.tagByName("дощове") }!!.name)
    }

    @Test
    fun `видалення тегу знімає його з речей`() {
        runBlocking {
            val tag = graph.repos.wardrobe.createTag("літнє")
            val subtype = graph.repos.taxonomy.subtypeByKey("body.t-shirt")!!
            graph.repos.wardrobe.createItem(
                draft = ItemDraft(subtypeId = subtype.id, name = "Сорочка"),
                tagIds = setOf(tag),
            )
        }
        openTags()
        clickByTag("tag_delete_1")
        waitUntilExists("tag_delete_scrim")
        clickByTag("tag_delete_confirm")

        waitUntilTrue("тег зник зі списку") {
            composeRule.onAllNodesWithText("літнє")
                .fetchSemanticsNodes().isEmpty()
        }
        val tags = runBlocking { graph.repos.wardrobe.getItem(1L)!!.tags }
        assertEquals(0, tags.size)
    }
}
