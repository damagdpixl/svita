package com.damagdpixl.svita.ui.wardrobe

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Редактор речі: порожня назва блокує збереження (жодного рядка в базі),
 * після введення назви річ створюється через репозиторій.
 */
class ItemEditorValidationTest : WardrobeUiTestBase() {



    @Test
    fun `порожня назва блокує збереження а заповнена створює річ`() {
        setContent()
        waitUntilExists("wardrobe_add_empty")
        clickByTag("wardrobe_add_empty")

        waitUntilExists("editor_root")
        waitUntilExists("editor_save")
        clickByTag("editor_save")

        // Помилка валідації показана, запису в базі немає.
        composeRule.onNodeWithText("Name is required").assertExists()
        assertEquals(0, runBlocking { graph.repos.wardrobe.observeItems().first().size })

        // Назва + тип: друга спроба проходить.
        composeRule.onNodeWithTag("editor_name").performTextInput("Синя сорочка")
        clickByTag("editor_subtype_pick")
        waitUntilExists("subtype_picker_list")
        clickByTag("editor_subtype_body.t-shirt")
        clickByTag("editor_save")

        waitUntilExists("screen_wardrobe")
        waitUntilGone("editor_root")
        waitUntilExists("wardrobe_grid")
        val items = runBlocking { graph.repos.wardrobe.observeItems().first() }
        assertEquals(1, items.size)
        assertEquals("Синя сорочка", items[0].name)
        assertTrue(items[0].archived.not())
    }
}
