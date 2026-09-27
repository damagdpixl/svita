package com.damagdpixl.svita.ui.wardrobe

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * Редактор речі: порожня назва блокує збереження (жодного рядка в базі),
 * після введення назви річ створюється через репозиторій; помилка валідації
 * властивості показує українське повідомлення і також блокує запис.
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

    /**
     * Шлях помилки валідації властивостей: enum-поле створюється через діалог
     * (його чіпи фізично не дають ввести недійсне значення — вибір обмежений
     * варіантами), тож недійсний ввод проверяємо на NUMBER-полі: очікуємо
     * українську помилку «Це не число», жодного рядка речі в базі, а після
     * виправлення — успішний запис із вибраним enum-варіантом.
     */
    @Test
    @Config(qualifiers = "uk-w420dp-h1000dp")
    fun `недійсне значення властивості показує українську помилку і не пише річ`() {
        setContent()
        waitUntilExists("wardrobe_add_empty")
        clickByTag("wardrobe_add_empty")
        waitUntilExists("editor_root")
        waitUntilExists("editor_save")

        composeRule.onNodeWithTag("editor_name").performTextInput("Сорочка з розміром")
        clickByTag("editor_subtype_pick")
        waitUntilExists("subtype_picker_list")
        clickByTag("editor_subtype_body.t-shirt")

        // Enum-поле: назва, тип, варіанти через кому.
        clickByTag("editor_add_attribute")
        waitUntilExists("attribute_scrim")
        composeRule.onNodeWithTag("attribute_name").performTextInput("розмір")
        clickByTag("attribute_type_ENUM")
        composeRule.onNodeWithTag("attribute_options").performTextInput("S,M,L")
        clickByTag("attribute_create")
        waitUntilTrue("варіанти enum-поля відрендерено") {
            composeRule.onAllNodesWithText("M", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Числове поле: сюди можна ввести недійсне значення.
        clickByTag("editor_add_attribute")
        waitUntilExists("attribute_scrim")
        composeRule.onNodeWithTag("attribute_name").performTextInput("вага")
        clickByTag("attribute_type_NUMBER")
        clickByTag("attribute_create")
        waitUntilExists("attribute_input_вага")
        composeRule.onNodeWithTag("attribute_input_вага").performTextInput("абракадабра")

        clickByTag("editor_save")

        waitUntilTrue("показано помилку «Це не число»") {
            composeRule.onAllNodesWithText("Це не число", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(0, runBlocking { graph.repos.wardrobe.observeItems().first().size })

        // Виправлення значення + вибір варіанту enum: запис проходить.
        composeRule.onNodeWithTag("attribute_input_вага").performTextClearance()
        composeRule.onNodeWithTag("attribute_input_вага").performTextInput("42")
        clickByText("M")
        clickByTag("editor_save")

        waitUntilGone("editor_root")
        val items = runBlocking { graph.repos.wardrobe.observeItems().first() }
        assertEquals(1, items.size)
        assertEquals("Сорочка з розміром", items[0].name)
    }
}
