package com.damagdpixl.svita.ui.wardrobe

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.damagdpixl.svita.core.designsystem.EditorialTheme
import com.damagdpixl.svita.data.WardrobePrefs
import com.damagdpixl.svita.ui.SvitaApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Щасливий шлях майстра першого запуску: фото (пропущено) -> тип -> назва
 * (автозаповнення з типу) -> властивості -> готово. Річ записується через
 * репозиторії, прапорець онбордингу виставляється, сітка показує річ.
 */
class OnboardingWizardTest : WardrobeUiTestBase() {

    private fun setContentFresh() {
        composeRule.setContent {
            EditorialTheme {
                SvitaApp()
            }
        }
    }


    @Test
    fun `майстер першого запуску створює першу річ через репозиторії`() {
        // Свіжа установка: прибираємо прапорець, інакше майстер не відкриється.
        runBlocking { graph.repos.settings.remove(WardrobePrefs.ONBOARDING_DONE) }
        setContentFresh()
        advanceLooperTime()
        waitUntilExists("wizard_root")

        // Крок 1 (фото): без фото.
        clickByTag("wizard_next")

        // Крок 2 (тип): сідана футболка.
        waitUntilExists("wizard_subtype_body.t-shirt")
        clickByTag("wizard_subtype_body.t-shirt")
        clickByTag("wizard_next")

        // Крок 3 (назва): поле попередньо заповнене назвою типу.
        waitUntilExists("wizard_name")
        waitUntilTrue("назву автозаповнено з типу") {
            composeRule
                .onAllNodes(hasText("T-shirt"), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        clickByTag("wizard_next")

        // Крок 4 (властивості): завершуємо.
        waitUntilExists("wizard_finish")
        clickByTag("wizard_finish")

        // Повернення на сітку: річ записано і показано.
        waitUntilExists("screen_wardrobe")
        waitUntilExists("wardrobe_grid")
        // Підпис картки рендериться в UPPER CASE (monoUpper) — порівнюємо
        // без урахування регістру.
        composeRule.onNodeWithText("T-shirt", substring = true, ignoreCase = true).assertExists()

        val items = runBlocking { graph.repos.wardrobe.observeItems().first() }
        assertEquals(1, items.size)
        assertEquals("T-shirt", items[0].name)
        val onboardingDone = runBlocking {
            graph.repos.settings.getBoolean(WardrobePrefs.ONBOARDING_DONE, false)
        }
        assertTrue(onboardingDone)
    }

    @Test
    fun `пропуск майстра не створює речей`() {
        runBlocking { graph.repos.settings.remove(WardrobePrefs.ONBOARDING_DONE) }
        setContentFresh()
        waitUntilExists("wizard_root")

        clickByTag("wizard_skip")

        waitUntilExists("screen_wardrobe")
        assertEquals(0, runBlocking { graph.repos.wardrobe.observeItems().first().size })
        val done = runBlocking {
            graph.repos.settings.getBoolean(WardrobePrefs.ONBOARDING_DONE, false)
        }
        assertTrue(done)
    }
}
