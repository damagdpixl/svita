package com.damagdpixl.svita.ui.settings

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import com.damagdpixl.svita.core.data.ItemDraft
import com.damagdpixl.svita.core.model.AttributeType
import com.damagdpixl.svita.ui.wardrobe.WardrobeUiTestBase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Власні поля (attribute definitions): поле, створене з екрана Кастомізації,
 * з'являється в редакторі речі з варіантами; конфігурація, що «протухнула»
 * (видалений варіант), дає українську помилку валідації й блокує запис.
 */
class FieldsUiTest : WardrobeUiTestBase() {

    private fun openCustomization() {
        setContent()
        waitUntilExists("wardrobe_add_empty")
        clickByTag("tab_settings")
        waitUntilExists("settings_row_customization")
        clickByTag("settings_row_customization")
        waitUntilExists("customization_root")
    }

    @Test
    fun `створене enum-поле з'являється в редакторі речі з варіантами`() {
        openCustomization()

        // Поле «розмір» (enum: S, M) — глобальний скоуп.
        clickByTag("field_add")
        waitUntilExists("field_editor_scrim")
        composeRule.onNodeWithTag("field_name").performTextInput("розмір")
        clickByTag("field_type_ENUM")
        composeRule.onNodeWithTag("field_option_input").performTextInput("S")
        clickByTag("field_option_commit")
        composeRule.onNodeWithTag("field_option_input").performTextInput("M")
        clickByTag("field_option_commit")
        clickByTag("field_editor_save")
        waitUntilGone("field_editor_scrim")

        // Поле у списку кастомізації (рядок малює ключ у верхньому регістрі).
        waitUntilTrue("поле РОЗМІР у списку") {
            composeRule.onAllNodesWithText("РОЗМІР")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Редактор речі показує варіанти поля: вибір M, збереження.
        clickByTag("customization_back")
        clickByTag("tab_wardrobe")
        waitUntilExists("wardrobe_add_empty")
        clickByTag("wardrobe_add_empty")
        waitUntilExists("editor_root")
        waitUntilExists("editor_subtype_pick")
        composeRule.onNodeWithTag("editor_name").performTextInput("Сорочка")
        clickByTag("editor_subtype_pick")
        waitUntilExists("subtype_picker_list")
        clickByTag("editor_subtype_body.t-shirt")

        waitUntilTrue("варіанти enum-поля у редакторі") {
            composeRule.onAllNodesWithText("M", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        clickByText("M")
        clickByTag("editor_save")
        waitUntilGone("editor_root")

        val items = runBlocking {
            graph.repos.wardrobe.observeItems().first().also {
                assertEquals(1, it.size)
            }
        }
        val stored = runBlocking { graph.repos.attributes.valuesForItem(items[0].id) }
        assertEquals(1, stored.size)
        assertEquals("M", stored[0].value)
    }

    @Test
    @org.robolectric.annotation.Config(qualifiers = "uk-w420dp-h1000dp")
    fun `видалений варіант конфіга дає українську помилку при збереженні`() {
        // Сід: enum-поле (S, M) + річ зі значенням S — напряму через репозиторій.
        val definitionId = runBlocking {
            graph.repos.attributes.createDefinition(
                categoryId = null,
                key = "розмір",
                type = AttributeType.ENUM,
                config = """{"options":["S","M"]}""",
                sortOrder = 0,
            )
        }
        runBlocking {
            val subtype = graph.repos.taxonomy.subtypeByKey("body.t-shirt")!!
            graph.repos.wardrobe.createItem(
                draft = ItemDraft(subtypeId = subtype.id, name = "Сорочка"),
                attributeValues = mapOf(definitionId to "S"),
            )
        }
        // Поле «протухло»: варіант S прибрали, у речі лишилося S.
        runBlocking {
            graph.repos.attributes.updateDefinition(
                id = definitionId,
                categoryId = null,
                key = "розмір",
                type = AttributeType.ENUM,
                config = """{"options":["M"]}""",
                sortOrder = 0,
            )
        }

        setContent()
        waitUntilExists("wardrobe_grid")
        // Перша річ (id=1) на сітці → деталь → редагування.
        waitUntilExists("item_card_1")
        clickByTag("item_card_1")

        waitUntilExists("detail_root")
        clickByTag("detail_edit")

        waitUntilExists("editor_root")
        waitUntilExists("editor_save")
        clickByTag("editor_save")

        waitUntilTrue("показано українську помилку варіанта") {
            composeRule.onAllNodesWithText("Оберіть один із запропонованих варіантів", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        // Запис не пройшов: значення S лишилося в базі.
        val stored = runBlocking { graph.repos.attributes.valuesForItem(1L) }
        assertEquals("S", stored[0].value)
    }
}
