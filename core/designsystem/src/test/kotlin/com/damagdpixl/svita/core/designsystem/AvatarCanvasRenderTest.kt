package com.damagdpixl.svita.core.designsystem

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Robolectric render gate for the paper doll: the canvas must draw a FULL
 * outfit manifest (and every tricky single shape) without crashing, and a
 * body/tone change must be reflected in the accessibility description.
 *
 * Expected descriptions are resolved from the RESOURCE (Robolectric
 * application context), so the assertions stay honest if the wording is
 * re-translated — the hook under test is "manifest -> description", not an
 * English literal. Layer ABSENCE under a dress is pinned deterministically
 * by AvatarManifestTest (these render tests only gate no-crash execution:
 * the Robolectric draw phase is not reliably observable from the test).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AvatarCanvasRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** Resource-resolved expected description for a manifest configuration. */
    private fun expectedDescription(manifest: AvatarManifest): String =
        RuntimeEnvironment.getApplication().getString(
            R.string.avatar_canvas_description,
            manifest.bodyType.id,
            manifest.skinTone.id,
        )

    private fun fullOutfit() = AvatarManifest(
        bodyType = AvatarBodyType.REGULAR,
        skinTone = AvatarSkinTone.MEDIUM,
        garments = listOf(
            AvatarGarment(1, AvatarSlot.BODY, AvatarGarmentShape.TEE, "#F47CC3"),
            AvatarGarment(2, AvatarSlot.LEGS, AvatarGarmentShape.TROUSERS, "#3449BE"),
            AvatarGarment(3, AvatarSlot.OUTER, AvatarGarmentShape.COAT, "#000000"),
            AvatarGarment(4, AvatarSlot.FEET, AvatarGarmentShape.SHOES, "#FFFFFF"),
            AvatarGarment(5, AvatarSlot.HAT, AvatarGarmentShape.HAT, "#FFCD02"),
            AvatarGarment(6, AvatarSlot.SCARF, AvatarGarmentShape.SCARF, "#D71C0A"),
            AvatarGarment(7, AvatarSlot.BAG, AvatarGarmentShape.BAG, "#5E4433"),
            AvatarGarment(8, AvatarSlot.ACCESSORY, AvatarGarmentShape.GLASSES, "#745EC5"),
        ),
    )

    @Test
    fun `full outfit manifest renders without crashing`() {
        val manifest = fullOutfit()
        composeRule.setContent {
            EditorialTheme(darkTheme = false) {
                AvatarCanvas(manifest = manifest)
            }
        }
        composeRule.onNodeWithContentDescription(expectedDescription(manifest)).assertExists()
        composeRule.waitForIdle()
    }

    @Test
    fun `dress manifest renders without crashing`() {
        val manifest = AvatarManifest(
            bodyType = AvatarBodyType.CURVY,
            skinTone = AvatarSkinTone.DEEP,
            garments = listOf(
                AvatarGarment(1, AvatarSlot.BODY, AvatarGarmentShape.TEE, "#FFFFFF"),
                AvatarGarment(2, AvatarSlot.LEGS, AvatarGarmentShape.SHORTS, "#2ECC71"),
                AvatarGarment(3, AvatarSlot.DRESS, AvatarGarmentShape.DRESS, "#B91F81"),
                AvatarGarment(4, AvatarSlot.FEET, AvatarGarmentShape.HEELS, "#745EC5"),
            ),
        )
        composeRule.setContent {
            EditorialTheme(darkTheme = false) {
                AvatarCanvas(manifest = manifest)
            }
        }
        composeRule.onNodeWithContentDescription(expectedDescription(manifest)).assertExists()
        composeRule.waitForIdle()
    }

    @Test
    fun `body and tone change is reflected after recomposition`() {
        val manifest = androidx.compose.runtime.mutableStateOf(
            AvatarManifest(bodyType = AvatarBodyType.SLIM, skinTone = AvatarSkinTone.LIGHT),
        )
        composeRule.setContent {
            EditorialTheme(darkTheme = false) {
                AvatarCanvas(manifest = manifest.value)
            }
        }
        composeRule.onNodeWithContentDescription(expectedDescription(manifest.value)).assertExists()

        composeRule.runOnIdle {
            manifest.value = AvatarManifest(bodyType = AvatarBodyType.CURVY, skinTone = AvatarSkinTone.DEEP)
        }
        composeRule.onNodeWithContentDescription(expectedDescription(manifest.value)).assertExists()
        composeRule.waitForIdle()
    }

    @Test
    fun `every garment shape renders without crashing`() {
        composeRule.setContent {
            EditorialTheme(darkTheme = false) {
                AvatarCanvas(
                    manifest = AvatarManifest(
                        garments = AvatarGarmentShape.entries.mapIndexed { index, shape ->
                            AvatarGarment(
                                garmentId = index.toLong(),
                                slot = AvatarSlot.ACCESSORY,
                                shape = shape,
                                tintHex = "#61E0C7",
                            )
                        },
                    ),
                )
            }
        }
        composeRule.waitForIdle()
    }
}
