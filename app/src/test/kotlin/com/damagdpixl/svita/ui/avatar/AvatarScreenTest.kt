package com.damagdpixl.svita.ui.avatar

import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.damagdpixl.svita.data.AvatarPrefs
import com.damagdpixl.svita.ui.wardrobe.WardrobeUiTestBase
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Налаштування -> Аватар: пункт у хабі відкриває екран, вибір типу тіла й тону
 * шкіри миттєво відображається на попередньому перегляді та зберігається в
 * налаштуваннях (ключі avatar.body / avatar.tone), і після повторного входу
 * вибір відновлюється.
 */
class AvatarScreenTest : WardrobeUiTestBase() {

    private fun openAvatar() {
        setContent()
        waitUntilExists("wardrobe_add_empty")
        clickByTag("tab_settings")
        waitUntilExists("settings_row_avatar")
        clickByTag("settings_row_avatar")
        waitUntilExists("avatar_preview")
    }

    @Test
    fun `хаб налаштувань містить пункт аватара і він відкриває екран`() {
        setContent()
        waitUntilExists("wardrobe_add_empty")
        clickByTag("tab_settings")
        waitUntilExists("settings_row_avatar")
        composeRule.onNodeWithText("Body and skin tone").assertExists()
        clickByTag("settings_row_avatar")
        waitUntilExists("avatar_preview")
        waitUntilExists("avatar_body_label")
        waitUntilExists("avatar_tone_label")
        composeRule.onNodeWithText("MY AVATAR").assertExists()
    }

    @Test
    fun `вибір типу тіла зберігається у налаштуваннях`() {
        openAvatar()
        clickByTag("avatar_body_curvy")
        waitUntilTrue("avatar.body = curvy у налаштуваннях") {
            runBlocking {
                graph.repos.settings.getString(AvatarPrefs.AVATAR_BODY) == "curvy"
            }
        }
        composeRule.onNodeWithContentDescription("Paper-doll avatar: curvy body, light skin")
            .assertExists()
    }

    @Test
    fun `вибір тону шкіри зберігається у налаштуваннях`() {
        openAvatar()
        clickByTag("avatar_tone_deep")
        waitUntilTrue("avatar.tone = deep у налаштуваннях") {
            runBlocking {
                graph.repos.settings.getString(AvatarPrefs.AVATAR_TONE) == "deep"
            }
        }
        composeRule.onNodeWithContentDescription("Paper-doll avatar: regular body, deep skin")
            .assertExists()
    }

    @Test
    fun `збережений вибір відновлюється після повторного входу`() {
        runBlocking {
            graph.repos.settings.putString(AvatarPrefs.AVATAR_BODY, "slim")
            graph.repos.settings.putString(AvatarPrefs.AVATAR_TONE, "medium")
        }
        openAvatar()
        // Завантаження збережених значень асинхронне: чекаємо саме на
        // відновлений стан, а не на перший кадр із дефолтами (гонка,
        // яку спіймав CI).
        waitUntilTrue("попередній перегляд відновив slim/medium") {
            composeRule.onAllNodesWithContentDescription(
                "Paper-doll avatar: slim body, medium skin",
            ).fetchSemanticsNodes().isNotEmpty()
        }
        waitUntilTrue("чип slim присутній") {
            composeRule.onAllNodesWithTag("avatar_body_slim", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }
}
