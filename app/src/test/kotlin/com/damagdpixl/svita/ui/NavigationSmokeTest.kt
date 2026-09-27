package com.damagdpixl.svita.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import com.damagdpixl.svita.core.designsystem.EditorialTheme
import com.damagdpixl.svita.data.SvitaGraph
import com.damagdpixl.svita.data.WardrobePrefs
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Robolectric compose smoke test of the navigation shell (real in-memory
 * database behind the manual DI graph; onboarding pre-done so the wardrobe
 * tab shows the grid, not the first-run wizard).
 *
 * Note: Robolectric lays the Scaffold bottom bar out with degenerate bounds,
 * so tab activation uses the OnClick semantics action directly instead of an
 * injected touch (which needs real coordinates).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
class NavigationSmokeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun setUpGraph() {
        SvitaGraph.init(RuntimeEnvironment.getApplication(), databaseName = null)
        runBlocking {
            SvitaGraph.get().repos.settings.putBoolean(WardrobePrefs.ONBOARDING_DONE, true)
        }
    }

    @After
    fun tearDownGraph() {
        SvitaGraph.resetForTests()
    }

    @Test
    fun bottomBarShowsFiveDestinations() {
        composeRule.setContent {
            EditorialTheme {
                SvitaApp()
            }
        }
        composeRule.onNodeWithTag("bottom_bar", useUnmergedTree = true).assertExists()
        listOf("wardrobe", "outfits", "calendar", "packing", "settings").forEach { route ->
            composeRule.onNodeWithTag("tab_$route").assertExists()
        }
    }

    private fun activateTab(route: String) {
        composeRule.onNodeWithTag("tab_$route")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntilAtLeastOneExists(
            androidx.compose.ui.test.hasTestTag("screen_$route"),
            timeoutMillis = 10_000,
        )
        composeRule.onNodeWithTag("screen_$route").assertExists()
    }

    @Test
    fun tappingTabsSwitchesScreens() {
        composeRule.setContent {
            EditorialTheme {
                SvitaApp()
            }
        }

        composeRule.onNodeWithTag("screen_wardrobe").assertExists()

        activateTab("settings")
        activateTab("calendar")
        activateTab("wardrobe")
    }

    @Test
    fun wardrobeShowsHeadlineAndCta() {
        composeRule.setContent {
            EditorialTheme {
                SvitaApp()
            }
        }
        composeRule.onNodeWithText("Your closet").assertExists()
        composeRule.onNodeWithText("ADD ITEM").assertExists()
    }
}
