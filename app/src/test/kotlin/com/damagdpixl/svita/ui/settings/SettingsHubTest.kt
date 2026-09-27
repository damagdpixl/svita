package com.damagdpixl.svita.ui.settings

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.damagdpixl.svita.ui.wardrobe.WardrobeUiTestBase
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Головна «Налаштувань»: меню рендериться, діалог «Про застосунок» показує
 * версію та ліцензію, а ручний експорт лога налагодження створює файл
 * («Нічого не надсилається автоматично» — поруч із кнопкою).
 */
class SettingsHubTest : WardrobeUiTestBase() {

    private fun openSettings() {
        setContent()
        waitUntilExists("wardrobe_add_empty")
        clickByTag("tab_settings")
        waitUntilExists("settings_row_customization")
    }

    @Test
    fun `меню налаштувань містить усі пункти і застереження`() {
        openSettings()
        waitUntilExists("settings_row_tags")
        waitUntilExists("settings_row_export")
        waitUntilExists("settings_row_debug_log")
        waitUntilExists("settings_row_about")
        waitUntilExists("settings_debug_log_note")
        composeRule.onNodeWithText("Nothing is sent automatically").assertExists()
        composeRule.onNodeWithText("Coming soon").assertExists()
    }

    @Test
    fun `діалог про застосунок показує версію та ліцензію`() {
        openSettings()
        clickByTag("settings_row_about")
        waitUntilExists("about_scrim")
        composeRule.onNodeWithText("About Svita").assertExists()
        composeRule.onNodeWithText("License: GPL-3.0").assertExists()
        composeRule.onNodeWithText("Version 0.1.0").assertExists()
        clickByTag("about_close")
        waitUntilGone("about_scrim")
    }

    @Test
    fun `екпорт лога створює файл у кеші й показує статус`() {
        openSettings()
        clickByTag("settings_row_debug_log")

        waitUntilExists("settings_debug_log_status")
        val context = androidx.test.core.app.ApplicationProvider
            .getApplicationContext<android.content.Context>()
        val dir = File(context.cacheDir, "debug_logs")
        val logFile = dir.listFiles()?.firstOrNull { it.name == "svita-debug-log.txt" }
        assertNotNull("файл лога має бути створений", logFile)
        assertTrue(logFile!!.exists())
        assertTrue(logFile.readText().contains("Svita debug log"))
    }

    @Test
    fun `пункт кастомізації відкриває екран категорій`() {
        openSettings()
        clickByTag("settings_row_customization")
        waitUntilExists("customization_root")
        waitUntilExists("category_add")
        waitUntilExists("categories_reset")
        // Сідові категорії у списку (тестова локаль — англійська).
        waitUntilTrue("категорія Tops у списку") {
            composeRule.onAllNodesWithText("Tops", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        clickByTag("customization_back")
        waitUntilGone("customization_root")
    }
}
