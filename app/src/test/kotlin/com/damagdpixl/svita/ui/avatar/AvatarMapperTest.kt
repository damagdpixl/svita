package com.damagdpixl.svita.ui.avatar

import com.damagdpixl.svita.core.data.ItemAggregate
import com.damagdpixl.svita.core.designsystem.AvatarBodyType
import com.damagdpixl.svita.core.designsystem.AvatarGarmentShape
import com.damagdpixl.svita.core.designsystem.AvatarLayer
import com.damagdpixl.svita.core.designsystem.AvatarSkinTone
import com.damagdpixl.svita.core.designsystem.AvatarSlot
import com.damagdpixl.svita.core.designsystem.resolvedLayers
import com.damagdpixl.svita.core.model.AttributeDefinition
import com.damagdpixl.svita.core.model.AttributeEntry
import com.damagdpixl.svita.core.model.AttributeType
import com.damagdpixl.svita.core.model.Category
import com.damagdpixl.svita.core.model.Item
import com.damagdpixl.svita.core.model.Section
import com.damagdpixl.svita.core.model.Subtype
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Гардероб -> лялька: мапінг речей у шари аватара — розділ категорії є
 * авторитетним (власні категорії теж), ключ підтипу лише резервує, колір
 * береться з першого COLOR-поля (ключ палітри або hex), варіанти силуетів
 * розгалужуються за ключом підтипу.
 */
class AvatarMapperTest {

    private fun subtype(key: String) = Subtype(
        id = 20L,
        categoryId = 10L,
        key = key,
        nameEn = "Fixture",
        nameUk = "Фікстура",
        thermalClass = "N",
    )

    private fun category(section: Section) = Category(
        id = 10L,
        parentId = null,
        nameEn = "Fixture",
        nameUk = "Фікстура",
        icon = null,
        sortOrder = 0,
        isSystem = true,
        section = section,
    )

    private fun aggregate(
        subtypeKey: String,
        section: Section? = null,
        colorValues: List<String> = emptyList(),
    ): ItemAggregate {
        val sub = subtype(subtypeKey)
        return ItemAggregate(
            item = Item(
                id = 77L,
                subtypeId = sub.id,
                name = "fixture",
                notes = null,
                price = null,
                purchaseDate = null,
                seasons = emptySet(),
                sex = null,
                rating = null,
                archived = false,
                createdAt = Instant.fromEpochMilliseconds(0),
                updatedAt = Instant.fromEpochMilliseconds(0),
            ),
            subtype = sub,
            category = section?.let { category(it) },
            photos = emptyList(),
            tags = emptyList(),
            attributes = colorValues.mapIndexed { index, value ->
                AttributeEntry(itemId = 77L, definitionId = (index + 1).toLong(), value = value)
            },
        )
    }

    private val colorDefinition = AttributeDefinition(
        id = 1L,
        categoryId = null,
        key = "color",
        type = AttributeType.COLOR,
        config = null,
        sortOrder = 0,
    )

    private val colorIds = setOf(1L)

    // -- slot mapping --------------------------------------------------------

    @Test
    fun `seeded category section decides the slot`() {
        val cases = mapOf(
            Section.BODY to AvatarSlot.BODY,
            Section.LEGS to AvatarSlot.LEGS,
            Section.FEET to AvatarSlot.FEET,
            Section.DRESS to AvatarSlot.DRESS,
            Section.OUTER to AvatarSlot.OUTER,
            Section.ACCESSORY to AvatarSlot.ACCESSORY,
            Section.HAT to AvatarSlot.HAT,
            Section.SCARF to AvatarSlot.SCARF,
            Section.BAG to AvatarSlot.BAG,
        )
        cases.forEach { (section, slot) ->
            assertEquals(
                "розділ $section має мапитися на $slot",
                slot,
                AvatarMapper.garmentFor(aggregate("custom.42", section), colorIds).slot,
            )
        }
    }

    @Test
    fun `custom category with bag section maps even with body-prefixed key`() {
        // Custom categories get key custom.<id>; the section is authoritative.
        val garment = AvatarMapper.garmentFor(aggregate("custom.42", Section.BAG), colorIds)
        assertEquals(AvatarSlot.BAG, garment.slot)
    }

    @Test
    fun `missing category falls back to subtype key prefixes`() {
        assertEquals(
            AvatarSlot.DRESS,
            AvatarMapper.garmentFor(aggregate("body.dress.mini"), colorIds).slot,
        )
        assertEquals(
            AvatarSlot.OUTER,
            AvatarMapper.garmentFor(aggregate("body.jacket.moto"), colorIds).slot,
        )
        assertEquals(
            AvatarSlot.BODY,
            AvatarMapper.garmentFor(aggregate("body.t-shirt"), colorIds).slot,
        )
        assertEquals(
            AvatarSlot.FEET,
            AvatarMapper.garmentFor(aggregate("feet.boots.chelsea"), colorIds).slot,
        )
        assertEquals(
            AvatarSlot.HAT,
            AvatarMapper.garmentFor(aggregate("head.hats.beret"), colorIds).slot,
        )
        assertEquals(
            AvatarSlot.SCARF,
            AvatarMapper.garmentFor(aggregate("head.scarfs.snood"), colorIds).slot,
        )
    }

    // -- tint resolution -----------------------------------------------------

    @Test
    fun `first color attribute value becomes the primary tint`() {
        val aggregate = aggregate(
            "body.t-shirt",
            Section.BODY,
            colorValues = listOf("#3399db", "#FFCD02"),
        )
        val garment = AvatarMapper.garmentFor(aggregate, colorIds)
        assertEquals("#3399DB", garment.tintHex)
    }

    @Test
    fun `palette key value resolves through the palette map`() {
        val aggregate = aggregate("body.t-shirt", Section.BODY, colorValues = listOf("skyblue"))
        val garment = AvatarMapper.garmentFor(
            aggregate,
            colorIds,
            paletteHexByKey = mapOf("skyblue" to "#3399DB"),
        )
        assertEquals("#3399DB", garment.tintHex)
    }

    @Test
    fun `invalid color value yields null tint`() {
        val garment = AvatarMapper.garmentFor(
            aggregate("body.t-shirt", Section.BODY, colorValues = listOf("maroon-ish")),
            colorIds,
        )
        assertNull(garment.tintHex)
    }

    @Test
    fun `item without color attributes yields null tint`() {
        val garment = AvatarMapper.garmentFor(aggregate("body.t-shirt", Section.BODY), colorIds)
        assertNull(garment.tintHex)
    }

    @Test
    fun `non color definitions are ignored for tinting`() {
        val textDefinition = colorDefinition.copy(id = 2L, type = AttributeType.TEXT)
        val aggregate = aggregate("body.t-shirt", Section.BODY, colorValues = listOf("#3399DB"))
        val garment = AvatarMapper.garmentFor(
            aggregate,
            colorDefinitionIds = setOf(textDefinition.id),
        )
        assertNull(garment.tintHex)
    }

    // -- shape variants ------------------------------------------------------

    @Test
    fun `shape branches follow the seeded subtype keys`() {
        fun shape(key: String, section: Section) =
            AvatarMapper.garmentFor(aggregate(key, section), colorIds).shape

        assertEquals(AvatarGarmentShape.LONG_SLEEVE, shape("body.sweater", Section.BODY))
        assertEquals(AvatarGarmentShape.LONG_SLEEVE, shape("body.sweatshirt.hoody", Section.BODY))
        assertEquals(AvatarGarmentShape.TEE, shape("body.tank", Section.BODY))

        assertEquals(AvatarGarmentShape.SKIRT, shape("legs.skirt.pencil", Section.LEGS))
        assertEquals(AvatarGarmentShape.SHORTS, shape("legs.shorts", Section.LEGS))
        assertEquals(AvatarGarmentShape.TROUSERS, shape("legs.jeans", Section.LEGS))

        assertEquals(AvatarGarmentShape.BOOTS, shape("feet.boots.chelsea", Section.FEET))
        assertEquals(AvatarGarmentShape.BOOTS, shape("feet.hight boots.knee-highboots", Section.FEET))
        assertEquals(AvatarGarmentShape.HEELS, shape("feet.heels.classic", Section.FEET))
        assertEquals(AvatarGarmentShape.SHOES, shape("feet.sport.keds", Section.FEET))

        assertEquals(AvatarGarmentShape.DRESS, shape("body.dress.gored", Section.DRESS))
        assertEquals(AvatarGarmentShape.COAT, shape("body.coat.pea", Section.OUTER))
        assertEquals(AvatarGarmentShape.HAT, shape("head.hats.cap", Section.HAT))
        assertEquals(AvatarGarmentShape.SCARF, shape("head.scarfs.classic", Section.SCARF))
        assertEquals(AvatarGarmentShape.BAG, shape("bag.backpack", Section.BAG))
        assertEquals(AvatarGarmentShape.GLASSES, shape("accessory.glasses", Section.ACCESSORY))
        assertEquals(AvatarGarmentShape.BROOCH, shape("accessory.watch", Section.ACCESSORY))
    }

    // -- manifest ------------------------------------------------------------

    @Test
    fun `manifestFor wires body tone and garments together`() {
        val aggregates = listOf(
            aggregate("body.dress.gored", Section.DRESS, colorValues = listOf("#B91F81")),
            aggregate("feet.heels.classic", Section.FEET, colorValues = listOf("#000000")),
        )
        val manifest = AvatarMapper.manifestFor(
            aggregates = aggregates,
            definitions = listOf(colorDefinition),
            bodyType = AvatarBodyType.CURVY,
            skinTone = AvatarSkinTone.DEEP,
        )
        assertEquals(2, manifest.garments.size)
        assertEquals(AvatarBodyType.CURVY, manifest.bodyType)
        assertEquals(AvatarSkinTone.DEEP, manifest.skinTone)
        // Dress present -> the garment stack is dress + feet only.
        val slots = manifest.resolvedLayers()
            .filterIsInstance<AvatarLayer.Garment>()
            .map { it.slot }
        assertEquals(listOf(AvatarSlot.DRESS, AvatarSlot.FEET), slots)
    }
}
