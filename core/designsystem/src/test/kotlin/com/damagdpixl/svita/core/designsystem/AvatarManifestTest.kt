package com.damagdpixl.svita.core.designsystem

import com.damagdpixl.svita.core.engine.standardPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Manifest contracts of the paper doll (pure JVM — no graphics surface):
 * stack ordering (dress replaces body+legs, outer above body, accessories
 * topmost) and tint resolution (all 24 system palette colors with a
 * contrast-safe outline).
 */
class AvatarManifestTest {

    private fun garment(id: Long, slot: AvatarSlot, shape: AvatarGarmentShape, tint: String? = "#3399DB") =
        AvatarGarment(garmentId = id, slot = slot, shape = shape, tintHex = tint)

    // -- ordering ------------------------------------------------------------

    @Test
    fun `body layer is always first in the stack`() {
        val manifest = AvatarManifest(
            bodyType = AvatarBodyType.CURVY,
            skinTone = AvatarSkinTone.DEEP,
            garments = listOf(garment(1, AvatarSlot.BODY, AvatarGarmentShape.TEE)),
        )
        val body = manifest.resolvedLayers().first()
        assertTrue("першим шаром має бути тіло ляльки", body is AvatarLayer.Body)
        assertEquals(AvatarBodyType.CURVY, (body as AvatarLayer.Body).bodyType)
        assertEquals(AvatarSkinTone.DEEP, body.tone)
    }

    @Test
    fun `dress replaces body and legs garments`() {
        val manifest = AvatarManifest(
            garments = listOf(
                garment(1, AvatarSlot.BODY, AvatarGarmentShape.TEE),
                garment(2, AvatarSlot.LEGS, AvatarGarmentShape.TROUSERS),
                garment(3, AvatarSlot.DRESS, AvatarGarmentShape.DRESS),
                garment(4, AvatarSlot.FEET, AvatarGarmentShape.SHOES),
            ),
        )
        val slots = manifest.resolvedLayers().filterIsInstance<AvatarLayer.Garment>().map { it.slot }
        assertEquals(listOf(AvatarSlot.DRESS, AvatarSlot.FEET), slots)
    }

    @Test
    fun `without dress body and legs both render`() {
        val manifest = AvatarManifest(
            garments = listOf(
                garment(1, AvatarSlot.BODY, AvatarGarmentShape.TEE),
                garment(2, AvatarSlot.LEGS, AvatarGarmentShape.TROUSERS),
            ),
        )
        val slots = manifest.resolvedLayers().filterIsInstance<AvatarLayer.Garment>().map { it.slot }
        assertEquals(listOf(AvatarSlot.BODY, AvatarSlot.LEGS), slots)
    }

    @Test
    fun `outer renders above body`() {
        val manifest = AvatarManifest(
            garments = listOf(
                garment(1, AvatarSlot.OUTER, AvatarGarmentShape.COAT),
                garment(2, AvatarSlot.BODY, AvatarGarmentShape.TEE),
            ),
        )
        val slots = manifest.resolvedLayers().filterIsInstance<AvatarLayer.Garment>().map { it.slot }
        assertEquals(listOf(AvatarSlot.BODY, AvatarSlot.OUTER), slots)
    }

    @Test
    fun `accessory band renders topmost in engine attach order`() {
        val manifest = AvatarManifest(
            garments = listOf(
                garment(1, AvatarSlot.ACCESSORY, AvatarGarmentShape.BROOCH),
                garment(2, AvatarSlot.FEET, AvatarGarmentShape.SHOES),
                garment(3, AvatarSlot.SCARF, AvatarGarmentShape.SCARF),
                garment(4, AvatarSlot.HAT, AvatarGarmentShape.HAT),
                garment(5, AvatarSlot.BAG, AvatarGarmentShape.BAG),
            ),
        )
        val slots = manifest.resolvedLayers().filterIsInstance<AvatarLayer.Garment>().map { it.slot }
        assertEquals(
            listOf(AvatarSlot.FEET, AvatarSlot.HAT, AvatarSlot.SCARF, AvatarSlot.BAG, AvatarSlot.ACCESSORY),
            slots,
        )
    }

    @Test
    fun `garments sharing a slot keep ascending garment id order`() {
        val manifest = AvatarManifest(
            garments = listOf(
                garment(7, AvatarSlot.ACCESSORY, AvatarGarmentShape.BROOCH),
                garment(3, AvatarSlot.ACCESSORY, AvatarGarmentShape.GLASSES),
            ),
        )
        val ids = manifest.resolvedLayers().filterIsInstance<AvatarLayer.Garment>().map { it.garmentId }
        assertEquals(listOf(3L, 7L), ids)
    }

    @Test
    fun `full canonical order body legs dress outer feet hat scarf bag accessory`() {
        val manifest = AvatarManifest(
            garments = listOf(
                garment(1, AvatarSlot.SCARF, AvatarGarmentShape.SCARF),
                garment(2, AvatarSlot.OUTER, AvatarGarmentShape.COAT),
                garment(3, AvatarSlot.FEET, AvatarGarmentShape.BOOTS),
                garment(4, AvatarSlot.BAG, AvatarGarmentShape.BAG),
                garment(5, AvatarSlot.LEGS, AvatarGarmentShape.TROUSERS),
                garment(6, AvatarSlot.HAT, AvatarGarmentShape.HAT),
                garment(7, AvatarSlot.BODY, AvatarGarmentShape.TEE),
                garment(8, AvatarSlot.ACCESSORY, AvatarGarmentShape.GLASSES),
            ),
        )
        val slots = manifest.resolvedLayers().filterIsInstance<AvatarLayer.Garment>().map { it.slot }
        assertEquals(
            listOf(
                AvatarSlot.BODY,
                AvatarSlot.LEGS,
                AvatarSlot.OUTER,
                AvatarSlot.FEET,
                AvatarSlot.HAT,
                AvatarSlot.SCARF,
                AvatarSlot.BAG,
                AvatarSlot.ACCESSORY,
            ),
            slots,
        )
    }

    // -- tint resolution -----------------------------------------------------

    @Test
    fun `all 24 system palette colors resolve as tints`() {
        val palette = standardPalette()
        assertEquals(24, palette.size)
        val manifest = AvatarManifest(
            garments = palette.values.mapIndexed { index, entry ->
                garment(index.toLong() + 1, AvatarSlot.ACCESSORY, AvatarGarmentShape.BROOCH, entry.hex)
            },
        )
        val tints = manifest.resolvedLayers()
            .filterIsInstance<AvatarLayer.Garment>()
            .map { it.tintHex }
            .toSet()
        assertEquals("кожен колір палітри має дати окремий тінт", palette.values.map { it.hex.uppercase() }.toSet(), tints)
    }

    @Test
    fun `outline is contrast safe for every palette color`() {
        val palette = standardPalette()
        palette.values.forEach { entry ->
            val tint = AvatarTint.normalized(entry.hex)
            val outline = AvatarTint.outlineFor(tint)
            val delta = kotlin.math.abs(AvatarTint.relativeLuminance(tint) - AvatarTint.relativeLuminance(outline))
            assertTrue(
                "тінт ${entry.hex} і контур $outline мають бути контрастними (delta=$delta)",
                delta >= 0.25,
            )
        }
    }

    @Test
    fun `outline picks dark for light tints and light for dark tints`() {
        assertEquals(AvatarTint.OUTLINE_DARK, AvatarTint.outlineFor("#FFFFFF"))
        assertEquals(AvatarTint.OUTLINE_LIGHT, AvatarTint.outlineFor("#000000"))
    }

    @Test
    fun `malformed tint falls back to neutral gray`() {
        assertEquals(AvatarTint.FALLBACK_HEX, AvatarTint.normalized(null))
        assertEquals(AvatarTint.FALLBACK_HEX, AvatarTint.normalized(""))
        assertEquals(AvatarTint.FALLBACK_HEX, AvatarTint.normalized("SkyBlue"))
        assertEquals(AvatarTint.FALLBACK_HEX, AvatarTint.normalized("#12345"))
        assertEquals(AvatarTint.FALLBACK_HEX, AvatarTint.normalized("#GGGGGG"))
        assertEquals(AvatarTint.FALLBACK_HEX, AvatarTint.normalized("#3399DBXZ"))
    }

    @Test
    fun `tint normalization is uppercase with hash and stable`() {
        assertEquals("#3399DB", AvatarTint.normalized("3399db"))
        assertEquals("#3399DB", AvatarTint.normalized("#3399db"))
        assertEquals(AvatarTint.normalized("#3399db"), AvatarTint.normalized("#3399DB"))
        assertNotEquals(AvatarTint.FALLBACK_HEX, AvatarTint.normalized(" #3399db "))
    }

    @Test
    fun `resolved garment layers carry normalized tint and its outline`() {
        val manifest = AvatarManifest(
            garments = listOf(garment(1, AvatarSlot.BODY, AvatarGarmentShape.TEE, "3399db")),
        )
        val layer = manifest.resolvedLayers().filterIsInstance<AvatarLayer.Garment>().single()
        assertEquals("#3399DB", layer.tintHex)
        assertEquals(AvatarTint.outlineFor("#3399DB"), layer.outlineHex)
    }

    // -- ids round trip ------------------------------------------------------

    @Test
    fun `body and tone ids round trip with neutral fallbacks`() {
        assertEquals(AvatarBodyType.SLIM, AvatarBodyType.fromId("slim"))
        assertEquals(AvatarBodyType.REGULAR, AvatarBodyType.fromId("regular"))
        assertEquals(AvatarBodyType.CURVY, AvatarBodyType.fromId("curvy"))
        assertEquals(AvatarBodyType.REGULAR, AvatarBodyType.fromId("banana"))
        assertEquals(AvatarBodyType.REGULAR, AvatarBodyType.fromId(null))

        assertEquals(AvatarSkinTone.LIGHT, AvatarSkinTone.fromId("light"))
        assertEquals(AvatarSkinTone.MEDIUM, AvatarSkinTone.fromId("medium"))
        assertEquals(AvatarSkinTone.DEEP, AvatarSkinTone.fromId("deep"))
        assertEquals(AvatarSkinTone.LIGHT, AvatarSkinTone.fromId(null))
    }

    @Test
    fun `z order keeps accessories above everything and dress after legs`() {
        assertTrue(AvatarSlot.DRESS.zOrder > AvatarSlot.LEGS.zOrder)
        assertTrue(AvatarSlot.OUTER.zOrder > AvatarSlot.BODY.zOrder)
        assertTrue(AvatarSlot.ACCESSORY.zOrder > AvatarSlot.FEET.zOrder)
        assertTrue(AvatarSlot.ACCESSORY.zOrder > AvatarSlot.OUTER.zOrder)
    }

    @Test
    fun `collage composition is 2x2 up to four tiles and 3 columns beyond`() {
        assertEquals(2, collageColumnCount(1))
        assertEquals(2, collageColumnCount(4))
        assertEquals(3, collageColumnCount(5))
        assertEquals(3, collageColumnCount(9))
    }
}
