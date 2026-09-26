package com.damagdpixl.svita.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EditorialComponentsRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun allComponentsRenderWithoutCrashing() {
        var ctaClicks = 0
        composeRule.setContent {
            EditorialTheme(darkTheme = false) {
                Column {
                    CreamMenuBar(
                        label = "menu",
                        leading = { Box(Modifier.size(24.dp)) },
                        trailing = { StickerBadge(text = "New") },
                    )
                    CharcoalPanel {
                        Box(Modifier.size(40.dp))
                    }
                    PillButton(text = "press", onClick = {})
                    GreenCta(text = "go", onClick = { ctaClicks++ })
                    StickerBadge(text = "tag")
                    StickerBadge(text = "New", variant = StickerVariant.Bubble)
                    PolaroidCard(caption = "polaroid") {
                        Box(Modifier.size(48.dp))
                    }
                    ManifestoTiles(text = "NO ACCOUNTS · NO CLOUD · NO ADS")
                }
            }
        }

        // Labels are uppercased by the components.
        composeRule.onNodeWithText("MENU").assertExists()
        composeRule.onNodeWithText("PRESS").assertExists()
        composeRule.onNodeWithText("GO").assertExists()
        composeRule.onNodeWithText("TAG").assertExists()
        composeRule.onAllNodesWithText("NEW")[0].assertExists()
        composeRule.onNodeWithText("POLAROID").assertExists()

        // The CTA is interactive.
        composeRule.onNodeWithText("GO").performClick()
        composeRule.runOnIdle { org.junit.Assert.assertEquals(1, ctaClicks) }
    }

    @Test
    fun componentsRenderInDarkTheme() {
        composeRule.setContent {
            EditorialTheme(darkTheme = true) {
                Column {
                    CreamMenuBar(label = "menu")
                    CharcoalPanel { Box(Modifier.size(40.dp)) }
                    PillButton(text = "press", onClick = {})
                    GreenCta(text = "go", onClick = {})
                    PolaroidCard { Box(Modifier.size(48.dp)) }
                }
            }
        }
        composeRule.onNodeWithText("MENU").assertExists()
        composeRule.onNodeWithText("PRESS").assertExists()
    }
}
