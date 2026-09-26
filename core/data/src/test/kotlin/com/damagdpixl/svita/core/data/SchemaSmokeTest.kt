package com.damagdpixl.svita.core.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.svita.core.data.db.AppDatabase
import app.svita.core.data.db.Attribute_values
import app.svita.core.data.db.Item_tags
import app.svita.core.data.db.Photos
import app.svita.core.data.db.Packing_items
import com.damagdpixl.svita.core.model.AttributeDefinition
import com.damagdpixl.svita.core.model.AttributeEntry
import com.damagdpixl.svita.core.model.AttributeType
import com.damagdpixl.svita.core.model.Category
import com.damagdpixl.svita.core.model.Item
import com.damagdpixl.svita.core.model.Outfit
import com.damagdpixl.svita.core.model.OutfitEntry
import com.damagdpixl.svita.core.model.PackingList
import com.damagdpixl.svita.core.model.Season
import com.damagdpixl.svita.core.model.Section
import com.damagdpixl.svita.core.model.Sex
import com.damagdpixl.svita.core.model.Subtype
import com.damagdpixl.svita.core.model.Tag
import com.damagdpixl.svita.core.model.WearLogEntry
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.sql.SQLException

/**
 * Smoke test of schema v1: every table is created, one row round-trips
 * through insert + select + mapper, and FK enforcement is on.
 */
class SchemaSmokeTest {
    private lateinit var driver: SqlDriver
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        db = createSvitaDatabase(driver)
    }

    private fun seedCategory() {
        db.categoriesQueries.insertCategory(1L, null, "Tops", "Верх", "top", 10L, 1L, "body")
    }

    private fun seedSubtype() {
        seedCategory()
        db.subtypesQueries.insertSubtype(1L, 1L, "body.t-shirt", "T-shirt", "Футболка", "C1")
    }

    private fun seedItem() {
        seedSubtype()
        db.itemsQueries.insertItem(
            1L, "Біла футболка", "любима", 299.0, "2025-06-01",
            15L, "f", 5L, 0L, "2025-06-01T12:00:00Z", "2025-06-02T08:30:00Z",
        )
    }

    @Test
    fun `категорія — вставка і читання`() {
        seedCategory()
        val expected = Category(1L, null, "Tops", "Верх", "top", 10, true, Section.BODY)
        assertEquals(expected, db.categoriesQueries.selectAllCategories().executeAsOne().toDomain())
    }

    @Test
    fun `підтип — вставка і читання`() {
        seedSubtype()
        val expected = Subtype(1L, 1L, "body.t-shirt", "T-shirt", "Футболка", "C1")
        assertEquals(expected, db.subtypesQueries.selectAllSubtypes().executeAsOne().toDomain())
    }

    @Test
    fun `річ — вставка і читання`() {
        seedItem()
        val expected = Item(
            id = 1L,
            subtypeId = 1L,
            name = "Біла футболка",
            notes = "любима",
            price = 299.0,
            purchaseDate = LocalDate.parse("2025-06-01"),
            seasons = Season.fromBitmask(15),
            sex = Sex.FEMALE,
            rating = 5,
            archived = false,
            createdAt = Instant.parse("2025-06-01T12:00:00Z"),
            updatedAt = Instant.parse("2025-06-02T08:30:00Z"),
        )
        assertEquals(expected, db.itemsQueries.selectAllItems().executeAsOne().toDomain())
    }

    @Test
    fun `фото — вставка і читання`() {
        seedItem()
        db.photosQueries.insertPhoto(1L, "content://photo/1.jpg", 0L)
        assertEquals(
            Photos(1L, 1L, "content://photo/1.jpg", 0L),
            db.photosQueries.selectAllPhotos().executeAsOne(),
        )
    }

    @Test
    fun `визначення атрибута — вставка і читання`() {
        seedCategory()
        db.attribute_definitionsQueries.insertAttributeDefinition(1L, "brand", "text", null, 0L)
        val expected = AttributeDefinition(1L, 1L, "brand", AttributeType.TEXT, null, 0)
        assertEquals(
            expected,
            db.attribute_definitionsQueries.selectAllAttributeDefinitions().executeAsOne().toDomain(),
        )
    }

    @Test
    fun `значення атрибута — вставка і читання`() {
        seedItem()
        db.attribute_definitionsQueries.insertAttributeDefinition(null, "color", "color", null, 0L)
        db.attribute_valuesQueries.insertAttributeValue(1L, 1L, "#ffffff")
        val expected = AttributeEntry(1L, 1L, "#ffffff")
        assertEquals(
            expected,
            db.attribute_valuesQueries.selectAllAttributeValues().executeAsOne().toDomain(),
        )
    }

    @Test
    fun `тег — вставка і читання`() {
        db.tagsQueries.insertTag("літнє")
        assertEquals(Tag(1L, "літнє"), db.tagsQueries.selectAllTags().executeAsOne().toDomain())
    }

    @Test
    fun `тег на речі — вставка і читання`() {
        seedItem()
        db.tagsQueries.insertTag("літнє")
        db.tagsQueries.insertItemTag(1L, 1L)
        assertEquals(
            Item_tags(1L, 1L),
            db.tagsQueries.selectAllItemTags().executeAsOne(),
        )
    }

    @Test
    fun `образ — вставка і читання`() {
        seedItem()
        db.outfitsQueries.insertOutfit("Прогулянка", 4L, "2025-06-01T09:00:00Z", null)
        db.outfitsQueries.insertOutfitItem(1L, 1L, "body")
        val expectedOutfit = Outfit(1L, "Прогулянка", 4, Instant.parse("2025-06-01T09:00:00Z"), null)
        assertEquals(expectedOutfit, db.outfitsQueries.selectAllOutfits().executeAsOne().toDomain())
        val expectedEntry = OutfitEntry(1L, 1L, Section.BODY)
        assertEquals(expectedEntry, db.outfitsQueries.selectAllOutfitItems().executeAsOne().toDomain())
    }

    @Test
    fun `журнал носіння — вставка і читання`() {
        seedItem()
        db.wear_logQueries.insertWearLogEntry(
            "2025-06-01", null, encodeItemIds(listOf(1L, 2L, 3L)), 22.5, "спекотно",
        )
        val expected = WearLogEntry(
            id = 1L,
            date = LocalDate.parse("2025-06-01"),
            outfitId = null,
            itemIds = listOf(1L, 2L, 3L),
            tempC = 22.5,
            note = "спекотно",
        )
        assertEquals(expected, db.wear_logQueries.selectAllWearLog().executeAsOne().toDomain())
    }

    @Test
    fun `список пакування — вставка і читання`() {
        seedItem()
        db.packingQueries.insertPackingList("Виїзд", "2025-07-01", "2025-07-10", "2025-06-15T10:00:00Z")
        db.packingQueries.insertPackingItem(1L, 1L, 1L)
        val expectedList = PackingList(
            1L, "Виїзд",
            LocalDate.parse("2025-07-01"), LocalDate.parse("2025-07-10"),
            Instant.parse("2025-06-15T10:00:00Z"),
        )
        assertEquals(expectedList, db.packingQueries.selectAllPackingLists().executeAsOne().toDomain())
        val packedRow = db.packingQueries.selectAllPackingItems().executeAsOne()
        assertEquals(Packing_items(1L, 1L, 1L), packedRow)
        assertTrue(packedRow.isPacked())
    }

    @Test
    fun `налаштування — вставка і читання`() {
        db.app_settingsQueries.insertSetting("units", "metric")
        assertEquals("units" to "metric", db.app_settingsQueries.selectAllSettings().executeAsOne().toPair())
    }

    @Test
    fun `зовнішні ключі — річ без підтипу падає`() {
        val thrown = runCatching {
            db.itemsQueries.insertItem(
                999L, "сирота", null, null, null,
                15L, null, null, 0L, "2025-06-01T12:00:00Z", "2025-06-01T12:00:00Z",
            )
        }.exceptionOrNull()
        assertTrue("expected FK violation, got $thrown", thrown is SQLException)
    }

    @Test
    fun `ідентифікатори речей — round trip JSON`() {
        assertEquals("[1,2,3]", encodeItemIds(listOf(1L, 2L, 3L)))
        assertEquals(listOf(1L, 2L, 3L), decodeItemIds("[1,2,3]"))
        assertEquals(emptyList<Long>(), decodeItemIds("[]"))
    }
}
