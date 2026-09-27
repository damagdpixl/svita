package com.damagdpixl.svita.ui.outfits

import com.damagdpixl.svita.core.data.ItemAggregate
import com.damagdpixl.svita.core.model.AttributeDefinition
import com.damagdpixl.svita.core.model.AttributeEntry
import com.damagdpixl.svita.core.model.AttributeType
import com.damagdpixl.svita.core.model.Category
import com.damagdpixl.svita.core.model.Item
import com.damagdpixl.svita.core.model.PaletteColor
import com.damagdpixl.svita.core.model.Section
import com.damagdpixl.svita.core.model.Season
import com.damagdpixl.svita.core.model.Subtype
import com.damagdpixl.svita.core.model.Tag
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wardrobe -> engine mapping (P2 T6): colors resolve to PALETTE KEYS (key or
 * hex stored forms), styles come from user tags that coincide with the seeded
 * style-tag keys, and everything unparseable is dropped (engine-neutral).
 */
class DressMeMapperTest {

    private val palette = listOf(
        PaletteColor(1, "SkyBlue", "Блакитний", "#3399DB"),
        PaletteColor(2, "Mint", "М'ятний", "#61E0C7"),
    )

    private val colorDef = definition(7)
    private val otherDef = definition(8, type = AttributeType.TEXT)

    private val mapper = EngineGarmentMapper(palette)

    private val styleKeys = setOf("casual", "sport", "classic")

    @Test
    fun `color stored as palette key resolves case-insensitively to the canonical key`() {
        val aggregate = item(colors = listOf(7L to "skyblue"))
        val garment = mapper.garment(aggregate, setOf(7), styleKeys)
        assertEquals("body.sweater", garment.subtype)
        assertEquals(listOf("SkyBlue"), garment.colors)
    }

    @Test
    fun `color stored as hex maps to the matching palette key`() {
        val aggregate = item(colors = listOf(7L to "#3399db"))
        val garment = mapper.garment(aggregate, setOf(7), styleKeys)
        assertEquals(listOf("SkyBlue"), garment.colors)
    }

    @Test
    fun `unknown color values are dropped and the garment stays engine-neutral`() {
        val aggregate = item(colors = listOf(7L to "#FFFFFF", 7L to "неон"))
        val garment = mapper.garment(aggregate, setOf(7), styleKeys)
        assertTrue(garment.colors.isEmpty())
    }

    @Test
    fun `only color-typed attributes count`() {
        val aggregate = item(colors = listOf(8L to "SkyBlue"))
        val garment = mapper.garment(aggregate, setOf(7), styleKeys)
        assertTrue(garment.colors.isEmpty())
    }

    @Test
    fun `user tags feed styles only when they match a seeded style key`() {
        val aggregate = item(
            colors = emptyList(),
            tags = listOf(Tag(1, "Casual"), Tag(2, "punk"), Tag(3, "sport")),
        )
        val garment = mapper.garment(aggregate, setOf(7), styleKeys)
        assertEquals(listOf("casual", "sport"), garment.styles)
    }

    @Test
    fun `empty style vocabulary yields no styles`() {
        val aggregate = item(colors = emptyList(), tags = listOf(Tag(1, "Casual")))
        val garment = mapper.garment(aggregate, setOf(7), emptySet())
        assertTrue(garment.styles.isEmpty())
    }

    // -- fixtures -------------------------------------------------------------

    private fun definition(id: Long, type: AttributeType = AttributeType.COLOR) = AttributeDefinition(
        id = id,
        categoryId = null,
        key = "attr-$id",
        type = type,
        config = null,
        sortOrder = 0,
    )

    private fun item(
        colors: List<Pair<Long, String>>,
        tags: List<Tag> = emptyList(),
    ): ItemAggregate {
        val item = Item(
            id = 42,
            subtypeId = 2005,
            name = "Светр",
            notes = null,
            price = null,
            purchaseDate = null,
            seasons = Season.entries.toSet(),
            sex = null,
            rating = null,
            archived = false,
            createdAt = Instant.fromEpochSeconds(0),
            updatedAt = Instant.fromEpochSeconds(0),
        )
        val subtype = Subtype(2005, 1003, "body.sweater", "Sweater", "Светр", "C5")
        val category = Category(1003, null, "Sweaters", "Светри", null, 30, true, Section.BODY)
        return ItemAggregate(
            item = item,
            subtype = subtype,
            category = category,
            photos = emptyList(),
            tags = tags,
            attributes = colors.map { (definitionId, value) ->
                AttributeEntry(itemId = 42, definitionId = definitionId, value = value)
            },
        )
    }
}
