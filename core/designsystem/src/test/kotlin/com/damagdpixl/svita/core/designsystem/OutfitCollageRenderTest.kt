package com.damagdpixl.svita.core.designsystem

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Robolectric render gate for the flat-lay collage: photo tiles and tinted
 * silhouette placeholders must compose without crashing in both grid sizes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OutfitCollageRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `four photo tiles render as a 2x2 composition`() {
        composeRule.setContent {
            EditorialTheme(darkTheme = false) {
                OutfitCollage(
                    tiles = List(4) { index ->
                        CollageTile.Photo(
                            painter = ColorPainter(if (index % 2 == 0) Color.Gray else Color.DarkGray),
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                    testTag = "outfit_collage",
                )
            }
        }
        composeRule.onNodeWithTag("outfit_collage").assertExists()
        composeRule.waitForIdle()
    }

    @Test
    fun `nine mixed tiles with silhouette placeholders render`() {
        composeRule.setContent {
            EditorialTheme(darkTheme = false) {
                OutfitCollage(
                    tiles = listOf(
                        CollageTile.Photo(ColorPainter(Color.Gray)),
                        CollageTile.Silhouette(AvatarGarmentShape.TEE, "#D71C0A"),
                        CollageTile.Photo(ColorPainter(Color.DarkGray)),
                        CollageTile.Silhouette(AvatarGarmentShape.SKIRT, "#3449BE"),
                        CollageTile.Silhouette(AvatarGarmentShape.SHOES),
                        CollageTile.Photo(ColorPainter(Color.LightGray)),
                        CollageTile.Silhouette(AvatarGarmentShape.DRESS, "#B91F81"),
                        CollageTile.Silhouette(AvatarGarmentShape.BOOTS, "#5E4433"),
                        CollageTile.Silhouette(AvatarGarmentShape.GLASSES),
                    ),
                    modifier = Modifier.fillMaxSize(),
                    testTag = "outfit_collage",
                )
            }
        }
        composeRule.onNodeWithTag("outfit_collage").assertExists()
        composeRule.waitForIdle()
    }

    @Test
    fun `empty tile list renders nothing`() {
        composeRule.setContent {
            EditorialTheme(darkTheme = false) {
                OutfitCollage(tiles = emptyList(), testTag = "outfit_collage")
            }
        }
        composeRule.onNodeWithTag("outfit_collage").assertDoesNotExist()
    }
}
