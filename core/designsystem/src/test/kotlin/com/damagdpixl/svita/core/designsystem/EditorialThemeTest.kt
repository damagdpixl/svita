package com.damagdpixl.svita.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EditorialThemeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun lightThemeUsesEditorialTokens() {
        var scheme: androidx.compose.material3.ColorScheme? = null
        composeRule.setContent {
            EditorialTheme(darkTheme = false) {
                scheme = MaterialTheme.colorScheme
            }
        }
        composeRule.runOnIdle {
            val s = scheme!!
            assertEquals(Marigold, s.primary)
            assertEquals(Orange, s.secondary)
            assertEquals(ActionGreen, s.tertiary)
            assertEquals(Cream, s.surface)
            assertEquals(Charcoal, s.onSurface)
            assertEquals(White, s.background)
        }
    }

    @Test
    fun darkThemeUsesEditorialTokens() {
        var scheme: androidx.compose.material3.ColorScheme? = null
        composeRule.setContent {
            EditorialTheme(darkTheme = true) {
                scheme = MaterialTheme.colorScheme
            }
        }
        composeRule.runOnIdle {
            val s = scheme!!
            assertEquals(Marigold, s.primary)
            assertEquals(Charcoal, s.background)
            assertEquals(CharcoalPanel, s.surface)
            assertEquals(Cream, s.onSurface)
        }
    }

    @Test
    fun typographyUsesEditorialFamilies() {
        var typography: androidx.compose.material3.Typography? = null
        composeRule.setContent {
            EditorialTheme(darkTheme = false) {
                typography = MaterialTheme.typography
            }
        }
        composeRule.runOnIdle {
            val t = typography!!
            assertEquals(PlayfairDisplayFamily, t.headlineLarge.fontFamily)
            assertEquals(JetBrainsMonoFamily, t.labelLarge.fontFamily)
        }
    }

    @Test
    fun shapesArePanelRounded() {
        var shapes: androidx.compose.material3.Shapes? = null
        composeRule.setContent {
            EditorialTheme(darkTheme = false) {
                shapes = MaterialTheme.shapes
            }
        }
        composeRule.runOnIdle {
            val s = shapes!!
            org.junit.Assert.assertTrue(
                s.medium is androidx.compose.foundation.shape.RoundedCornerShape,
            )
            org.junit.Assert.assertTrue(
                s.extraLarge is androidx.compose.foundation.shape.RoundedCornerShape,
            )
        }
    }
}
