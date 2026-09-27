package com.damagdpixl.svita.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * CreamBarChart render gate (P2 T7): labels, value captions and per-row Canvas
 * tags exist for every row; the chart lives on a charcoal panel in both themes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CreamBarChartRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(darkTheme: Boolean) {
        composeRule.setContent {
            EditorialTheme(darkTheme = darkTheme) {
                CharcoalPanel {
                    Column {
                        CreamBarChart(
                            rows = listOf(
                                CreamBarRow(id = "a", label = "t-shirt", fraction = 1f, valueText = "12,5"),
                                CreamBarRow(id = "b", label = "jeans", fraction = 0.5f),
                            ),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `рядки діаграми рендеряться з підписами й значеннями`() {
        setContent(darkTheme = false)
        composeRule.onNodeWithTag("cream_bar_chart").assertExists()
        composeRule.onNodeWithText("T-SHIRT").assertExists()
        composeRule.onNodeWithText("JEANS").assertExists()
        composeRule.onNodeWithText("12,5").assertExists()
        composeRule.onNodeWithTag("cream_bar_chart_row_a").assertExists()
        composeRule.onNodeWithTag("cream_bar_chart_bar_a").assertExists()
        composeRule.onNodeWithTag("cream_bar_chart_bar_b").assertExists()
        // Рядок без valueText не має вузла значення.
        composeRule.onNodeWithTag("cream_bar_chart_value_b").assertDoesNotExist()
    }

    @Test
    fun `діаграма рендериться у темній темі`() {
        setContent(darkTheme = true)
        composeRule.onNodeWithTag("cream_bar_chart_label_a").assertExists()
    }
}
