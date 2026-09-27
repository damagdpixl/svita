package com.damagdpixl.svita.ui.wardrobe

import android.os.Looper
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import com.damagdpixl.svita.core.designsystem.EditorialTheme
import com.damagdpixl.svita.core.model.Season
import com.damagdpixl.svita.core.model.Sex
import com.damagdpixl.svita.data.SvitaGraph
import com.damagdpixl.svita.data.WardrobePrefs
import com.damagdpixl.svita.ui.SvitaApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Спільна основа UI-тестів шафи: справжня in-memory база за ручним DI-графом,
 * маячок «онбординг пройдено», щоб сітка не перемикалася на майстра першого
 * запуску (окремий тест вмикає його сам).
 *
 * Особливості середовища (документовано ще в P1):
 * - кліки виконуються через семантичну дію OnClick, бо координатні
 *   performClick ненадійні під Robolectric;
 * - вікно за замовчуванням крихітне, тому сітка часто потребує прокрутки
 *   до потрібної картки (лінива віртуалізація);
 * - віртуальний час looper завмирає: debounce пошуку спрацьовує лише після
 *   [advanceLooperTime].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w420dp-h1000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
abstract class WardrobeUiTestBase {

    @get:Rule
    val composeRule = createComposeRule()

    protected lateinit var graph: SvitaGraph.Graph

    @Before
    fun setUpGraph() {
        graph = SvitaGraph.init(RuntimeEnvironment.getApplication(), databaseName = null)
        runBlocking {
            graph.repos.settings.putBoolean(WardrobePrefs.ONBOARDING_DONE, true)
        }
    }

    @After
    fun tearDownGraph() {
        SvitaGraph.resetForTests()
    }

    protected fun setContent() {
        composeRule.setContent {
            EditorialTheme {
                SvitaApp()
            }
        }
    }

    /** Semantics-level click: reliable under Robolectric's degenerate layout. */
    protected fun clickByTag(tag: String) {
        composeRule.onNodeWithTag(tag, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)
    }

    protected fun clickByText(text: String) {
        composeRule.onNodeWithText(text).performSemanticsAction(SemanticsActions.OnClick)
    }

    protected fun waitUntilExists(tag: String) {
        waitUntilTrue("тестовий тег «$tag»") { countTag(tag) > 0 }
    }

    protected fun waitUntilGone(tag: String) {
        waitUntilTrue("відсутність тестового тега «$tag»") { countTag(tag) == 0 }
    }

    /**
     * Полінг із сон+waitForIdle: composeRule.waitUntil під Robolectric
     * прокачує looper інакше й пропускає продовження корутин бази
     * (withContext(IO) -> Main), а ручний ShadowPausedLooper.idle() ламає
     * планування кадрів Compose (NATIVE graphics). Сон дає IO-потокам час,
     * waitForIdle смикає recomposition.
     */
    protected fun waitUntilTrue(description: String, timeoutMillis: Long = 20_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) {
                error("Перевищено таймаут очікування: $description")
            }
            Thread.sleep(50)
            composeRule.waitForIdle()
        }
    }

    private fun countTag(tag: String) =
        composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .size

    /** Прокрутка сітки шафи до елемента із заданим тегом. */
    protected fun scrollToGridItem(tag: String) {
        composeRule.onNodeWithTag("wardrobe_grid")
            .performScrollToNode(hasTestTag(tag))
    }

    /**
     * Прокачує головний looper до кінця: у Robolectric віртуальний час
     * завмирає, тож debounce-пошук (delay у майбутньому) спрацьовує лише
     * після цього виклику.
     */
    protected fun advanceLooperTime() {
        shadowOf(Looper.getMainLooper()).runToEndOfTasks()
        composeRule.waitForIdle()
    }

    protected suspend fun itemNames(): List<String> =
        graph.repos.wardrobe
            .observeItems(com.damagdpixl.svita.core.data.ItemFilter(includeArchived = true))
            .first()
            .map { it.name }

    /** Seeds one item straight through the repositories (bypassing the UI). */
    protected fun seedItem(
        name: String,
        subtypeKey: String,
        seasons: Set<Season> = Season.entries.toSet(),
        sex: Sex? = null,
        archived: Boolean = false,
        tags: Set<Long> = emptySet(),
    ): Long = runBlocking {
        val subtype = graph.repos.taxonomy.subtypeByKey(subtypeKey)
            ?: error("seed subtype $subtypeKey must exist")
        graph.repos.wardrobe.createItem(
            draft = com.damagdpixl.svita.core.data.ItemDraft(
                subtypeId = subtype.id,
                name = name,
                seasons = seasons,
                sex = sex,
                archived = archived,
            ),
            tagIds = tags,
        )
    }

    protected suspend fun tagId(name: String): Long =
        graph.repos.wardrobe.createTag(name)
}
