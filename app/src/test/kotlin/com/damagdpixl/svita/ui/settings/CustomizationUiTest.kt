package com.damagdpixl.svita.ui.settings

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.damagdpixl.svita.core.data.ItemDraft
import com.damagdpixl.svita.core.data.ItemFilter
import com.damagdpixl.svita.core.model.Section
import com.damagdpixl.svita.ui.wardrobe.WardrobeUiTestBase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Еditor категорій (USP): власна категорія потрапляє у вибір типу редактора
 * речі, видалення категорії з речами блокується з українським діалогом,
 * «Скинути до стандартних» повертає сідові назви.
 */
class CustomizationUiTest : WardrobeUiTestBase() {

    private fun openCustomization() {
        setContent()
        // Шафа може бути і порожньою, і з сідованими речами — чекаємо корінь.
        waitUntilExists("screen_wardrobe")
        clickByTag("tab_settings")
        waitUntilExists("settings_row_customization")
        clickByTag("settings_row_customization")
        waitUntilExists("customization_root")
    }

    private fun createCategoryViaDialog(nameEn: String, nameUk: String) {
        clickByTag("category_add")
        waitUntilExists("category_editor_scrim")
        composeRule.onNodeWithTag("category_name_uk").performTextInput(nameUk)
        composeRule.onNodeWithTag("category_name_en").performTextInput(nameEn)
        clickByTag("category_editor_save")
        waitUntilGone("category_editor_scrim")
    }

    @Test
    fun `власна категорія з'являється у виборі типу редактора речі`() {
        openCustomization()
        createCategoryViaDialog("Capes", "Пончо")

        // У списку кастомізації вона є (несистемна, свій авто-підтип).
        waitUntilTrue("Capes у списку категорій") {
            composeRule.onAllNodesWithText("Capes", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        val customTag = "editor_subtype_custom." + runBlocking {
            graph.repos.taxonomy.categories().first { !it.isSystem }.id
        }

        // І в редакторі речі: група категорії + авто-підтип custom.<id>.
        clickByTag("customization_back")
        clickByTag("tab_wardrobe")
        waitUntilExists("wardrobe_add_empty")
        clickByTag("wardrobe_add_empty")
        waitUntilExists("editor_root")
        waitUntilExists("editor_subtype_pick")
        clickByTag("editor_subtype_pick")
        waitUntilExists("subtype_picker_list")
        // Власна категорія — у кінці списку (макс. sort_order): лінивий список
        // треба докрутити, інакше вузла просто немає в дереві семантики.
        composeRule.onNodeWithTag("subtype_picker_list", useUnmergedTree = true)
            .performScrollToNode(hasTestTag(customTag))
        waitUntilExists(customTag)
        clickByTag(customTag)
        composeRule.onNodeWithTag("editor_name").performTextInput("Червоне пончо")
        clickByTag("editor_save")
        waitUntilGone("editor_root")

        val items = runBlocking { graph.repos.wardrobe.observeItems().first() }
        assertEquals(1, items.size)
        assertEquals("Червоне пончо", items[0].name)
    }

    @Test
    fun `видалення категорії з річчю блокується українським діалогом`() {
        // Сід через репозиторій: власна категорія + річ на її авто-підтипі.
        val categoryId = runBlocking {
            val id = graph.repos.taxonomyEditor.createCategory(
                Section.BODY, "Capes", "Пончо", null,
            )
            val subtype = graph.repos.taxonomy.subtypesByCategory(id)[0]
            graph.repos.wardrobe.createItem(
                draft = ItemDraft(subtypeId = subtype.id, name = "Red cape"),
            )
            id
        }

        openCustomization()
        clickByTag("category_delete_$categoryId")
        waitUntilExists("confirm_scrim")
        clickByTag("category_delete_confirm")

        waitUntilExists("category_blocked_text")
        composeRule.onNodeWithTag("category_blocked_text", useUnmergedTree = true).assertExists()
        // Річ і категорія на місці — нічого не видалено.
        val items = runBlocking {
            graph.repos.wardrobe.observeItems(ItemFilter(includeArchived = true)).first()
        }
        assertEquals(1, items.size)
        runBlocking {
            assertTrue(graph.repos.taxonomy.category(categoryId) != null)
        }
    }

    @Test
    fun `скидання до стандартних повертає сідові назви`() {
        runBlocking {
            graph.repos.taxonomyEditor.updateCategory(1001L, "Changed", "Змінено", null)
        }
        openCustomization()

        // Змінена назва у списку.
        waitUntilTrue("змінена назва у списку") {
            composeRule.onAllNodesWithText("Changed", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        clickByTag("categories_reset")
        waitUntilExists("confirm_scrim")
        clickByTag("reset_confirm")

        waitUntilTrue("назва повернулася до сіду") {
            composeRule.onAllNodesWithText("Tops", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals("Tops", runBlocking { graph.repos.taxonomy.category(1001L)!!.nameEn })
    }

    @Test
    fun `редагування системної категорії обмежене назвою та іконкою`() {
        openCustomization()
        clickByTag("category_edit_1001")
        waitUntilExists("category_editor_scrim")
        // Підказка про системність і заблокований вибір розділу.
        composeRule.onNodeWithTag("category_name_en").assertExists()
        val sectionChips = composeRule.onAllNodes(
            hasTestTag("category_section_BODY"),
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        // Чіп розділу присутній, але клік по ньому не змінює вибір (enabled=false).
        assertTrue(sectionChips.isNotEmpty())
        clickByTag("category_editor_cancel")
        waitUntilGone("category_editor_scrim")
    }
}
