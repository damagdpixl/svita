package com.damagdpixl.svita.core.data.export

import com.damagdpixl.svita.core.data.ItemDraft
import com.damagdpixl.svita.core.model.AttributeType
import com.damagdpixl.svita.core.model.Season
import com.damagdpixl.svita.core.model.Sex
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * CSV export contract: RFC 4180 quoting round-trips through a real parser,
 * headers are English, values are emitted as stored, attribute definitions are
 * flattened into `attr.<key>` columns (docs/export_format.md, section 4).
 */
class CsvExportTest {
    private lateinit var fixture: BackupFixture
    private lateinit var csvFile: File

    @Before
    fun setUp() {
        fixture = BackupFixture()
        csvFile = File(fixture.rootA, "items.csv")
    }

    @After
    fun tearDown() {
        fixture.cleanup()
    }

    @Test
    fun `CSV - заголовки англійською, значення як є, лапки обходяться`() = runBlocking {
        val colorDef = fixture.a.attributes.createDefinition(null, "color", AttributeType.COLOR, null, 0)
        val brandDef = fixture.a.attributes.createDefinition(null, "brand", AttributeType.TEXT, null, 1)
        fixture.a.wardrobe.createItem(
            draft = ItemDraft(
                subtypeId = fixture.a.subtypeIdOf(fixture.a.subTShirt),
                name = "Сорочка, \"біла\"",
                notes = "нотатки з комою, лапками \"та\nпереносом",
                price = 299.99,
                purchaseDate = LocalDate.parse("2025-06-01"),
                seasons = setOf(Season.SPRING, Season.WINTER),
                sex = Sex.FEMALE,
                rating = 5,
            ),
            attributeValues = mapOf(colorDef to "#FFFFFF", brandDef to "Nike, Inc"),
        )
        fixture.a.wardrobe.createItem(
            draft = ItemDraft(
                subtypeId = fixture.a.subtypeIdOf(fixture.a.subJeans),
                name = "Джинси",
                seasons = emptySet(),
                archived = true,
            ),
        )

        fixture.managerA.exportItemsCsv(fixture.textSink(csvFile))
        val rows = parseCsv(csvFile.readText())

        assertEquals(3, rows.size) // header + 2 items
        assertEquals(
            listOf(
                "id", "name", "subtype_key", "notes", "price", "purchase_date",
                "seasons", "sex", "rating", "archived", "created_at", "updated_at",
                "attr.color", "attr.brand",
            ),
            rows[0],
        )
        val shirt = rows[1]
        assertEquals("1", shirt[0])
        assertEquals("Сорочка, \"біла\"", shirt[1])
        assertEquals("body.t-shirt", shirt[2])
        assertEquals("нотатки з комою, лапками \"та\nпереносом", shirt[3])
        assertEquals("299.99", shirt[4])
        assertEquals("2025-06-01", shirt[5])
        assertEquals("spring;winter", shirt[6])
        assertEquals("f", shirt[7])
        assertEquals("5", shirt[8])
        assertEquals("0", shirt[9])
        // Attribute columns: raw stored values, the comma-quoted brand included.
        assertEquals("#FFFFFF", shirt[12])
        assertEquals("Nike, Inc", shirt[13])
        val jeans = rows[2]
        assertEquals("2", jeans[0])
        assertEquals("Джинси", jeans[1])
        assertEquals("legs.jeans", jeans[2])
        assertEquals("", jeans[3])
        assertEquals("", jeans[6]) // empty season mask -> empty cell
        assertEquals("1", jeans[9]) // archived
        assertEquals("", jeans[12]) // no attributes
        assertEquals("", jeans[13])
    }

    @Test
    fun `CSV - порожня шафа дає тільки заголовок, дублікати ключів отримують суфікс`() = runBlocking {
        // Two definitions sharing a key: columns are disambiguated by id.
        fixture.a.attributes.createDefinition(null, "color", AttributeType.COLOR, null, 0)
        fixture.a.attributes.createDefinition(fixture.a.catTrousers, "color", AttributeType.COLOR, null, 1)

        fixture.managerA.exportItemsCsv(fixture.textSink(csvFile))

        val rows = parseCsv(csvFile.readText())
        assertEquals(1, rows.size)
        assertEquals(
            listOf("id", "name", "subtype_key", "notes", "price", "purchase_date", "seasons",
                "sex", "rating", "archived", "created_at", "updated_at", "attr.color", "attr.color#2"),
            rows[0],
        )
        // Sanity: the sink is valid UTF-8 CSV, not blank.
        assertTrue(csvFile.length() > 0)
    }
}
