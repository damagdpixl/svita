package com.damagdpixl.svita.ui.wardrobe

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.damagdpixl.svita.core.model.Season
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * Імпорт у UI (Robolectric): маршрут Routes.IMPORT відкривається і з підказки
 * онбордингу, і з кнопки в шапці сітки; стікер-підсумок «N речей додано»
 * з'являється на сітці після імпорту і зникає після закриття.
 *
 * Сам мульти-вибір системного пікера в Robolectric не відтворюється — він
 * покритий ViewModel-тестами (ImportBatchTest).
 */
class ImportScreenTest : WardrobeUiTestBase() {

    @Test
    fun `підказка онбордингу відкриває екран імпорту`() {
        setContent()
        waitUntilExists("import_hint")
        clickByTag("import_hint")

        waitUntilExists("import_root")
        waitUntilExists("import_pick")
    }

    @Test
    fun `кнопка імпорту в шапці сітки відкриває екран`() {
        seedItem("Сорочка", "body.t-shirt")
        setContent()
        waitUntilExists("wardrobe_grid")
        waitUntilExists("wardrobe_import")
        clickByTag("wardrobe_import")

        waitUntilExists("import_root")
    }

    @Test
    fun `кнопка імпорту є й у порожній шафі`() {
        setContent()
        waitUntilExists("wardrobe_add_empty")
        waitUntilExists("wardrobe_import_empty")
    }

    @Test
    fun `підсумок імпорту показано на сітці і закриття його прибирає`() {
        setContent()
        waitUntilExists("screen_wardrobe")

        composeRule.runOnIdle { graph.importSummary.value = 2 }
        waitUntilExists("import_summary")
        // StickerBadge renders mono uppercase, hence ignoreCase.
        composeRule.onNodeWithText("2 items added", ignoreCase = true).assertExists()

        clickByTag("import_summary_dismiss")
        waitUntilGone("import_summary")
        composeRule.runOnIdle {
            assertNull("підсумок спожито після закриття", graph.importSummary.value)
        }
    }

    @Test
    @Config(qualifiers = "uk-w420dp-h1000dp")
    fun `підсумок в українській локалі має правильну форму множини`() {
        seedItem("Сорочка", "body.t-shirt")
        setContent()
        waitUntilExists("wardrobe_grid")

        composeRule.runOnIdle { graph.importSummary.value = 5 }
        waitUntilText("Додано 5 речей") // many

        composeRule.runOnIdle { graph.importSummary.value = 2 }
        waitUntilText("Додано 2 речі") // few

        composeRule.runOnIdle { graph.importSummary.value = 1 }
        waitUntilText("Додано 1 річ") // one

        composeRule.runOnIdle { graph.importSummary.value = null }
    }

    private fun waitUntilText(text: String) {
        waitUntilTrue("текст «$text»") {
            composeRule.onAllNodesWithText(text, useUnmergedTree = true, ignoreCase = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }
}
