package com.damagdpixl.svita.core.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.svita.core.data.db.AppDatabase
import com.damagdpixl.svita.core.model.Category
import com.damagdpixl.svita.core.model.Section
import com.damagdpixl.svita.core.model.Subtype
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Seed parity: the taxonomy shipped in taxonomy_seed.sq (executed at schema
 * creation) must cover the outfit engine's functional data — the full
 * subtype -> thermal-class mapping, the class vocabulary, the role/section
 * set, the 24-color palette and the style tags — and every engine
 * test-wardrobe key must resolve, since the T4 parity fixtures are built on it.
 */
class TaxonomySeedTest {
    private lateinit var driver: SqlDriver
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        db = createSvitaDatabase(driver)
    }

    private fun categories(): List<Category> =
        db.categoriesQueries.selectAllCategories().executeAsList().map { it.toDomain() }

    private fun subtypes(): List<Subtype> =
        db.subtypesQueries.selectAllSubtypes().executeAsList().map { it.toDomain() }

    // Mirror of the engine's role_of(): role derived from the subtype key prefix.
    private fun roleOf(key: String): String? {
        val bodyOuterPrefixes = listOf("body.blazer", "body.gilet", "body.jacket", "body.coat")
        return when {
            key.startsWith("body.") -> when {
                key.startsWith("body.dress.") -> "dress"
                key == "body.cardigan" || key == "body.fur" ||
                    bodyOuterPrefixes.any(key::startsWith) -> "outer"
                else -> "body"
            }
            key.startsWith("legs.") -> "legs"
            key.startsWith("feet.") -> "feet"
            key.startsWith("head.hats.") -> "hat"
            key.startsWith("head.scarfs.") -> "scarf"
            key.startsWith("accessory.") -> "accessory"
            key.startsWith("bag.") -> "bag"
            else -> null
        }
    }

    private fun isCyrillic(s: String) = s.any { it.code in 0x0400..0x04FF }

    private fun isLatin(s: String) = s.isNotEmpty() && s.none { it.code in 0x0400..0x04FF }

    @Test
    fun `сід таксономії — 24 категорії та 133 підтипи`() {
        assertEquals(24, categories().size)
        assertEquals(133, subtypes().size)
        assertTrue(categories().all { it.isSystem })
        assertEquals(TaxonomySeedExpected.SUBTYPE_CLASS.keys, subtypes().map { it.key }.toSet())
    }

    @Test
    fun `сід підтипів — кожен має авторські en та uk назви`() {
        val byKey = subtypes().associateBy { it.key }
        for ((key, subtype) in byKey) {
            assertTrue("$key: порожня en назва", isLatin(subtype.nameEn))
            assertTrue("$key: порожня uk назва", isCyrillic(subtype.nameUk))
            assertTrue("$key: uk == en", subtype.nameUk != subtype.nameEn)
        }
    }

    @Test
    fun `сід підтипів — теплові класи в межах таблиці класів рушія`() {
        val unknown = subtypes().filter { it.thermalClass !in TaxonomySeedExpected.ENGINE_CLASSES }
        assertTrue("невідомі класи: ${unknown.map { it.key to it.thermalClass }}", unknown.isEmpty())
    }

    @Test
    fun `сід підтипів — мапа ключ-клас тотожна мапі рушія`() {
        val seedMap = subtypes().associate { it.key to it.thermalClass }
        assertEquals(TaxonomySeedExpected.SUBTYPE_CLASS, seedMap)
    }

    @Test
    fun `сід категорій — секції покривають повний набір ролей рушія`() {
        val sections = categories().map { it.section.db }.toSet()
        assertEquals(TaxonomySeedExpected.ENGINE_ROLES, sections)
    }

    @Test
    fun `сід — секція категорії дорівнює ролі підтипу за рушієм`() {
        val sectionOf = categories().associate { it.id to it.section }
        val mismatches = subtypes().mapNotNull { subtype ->
            val section = sectionOf.getValue(subtype.categoryId)
            val expectedRole = roleOf(subtype.key)
            if (section.db != expectedRole) subtype.key to (section.db to expectedRole) else null
        }
        assertTrue("невідповідності ролей: $mismatches", mismatches.isEmpty())
    }

    @Test
    fun `сід підтипів — ключі тестового гардероба рушія знаходяться`() {
        val keys = subtypes().map { it.key }.toSet()
        val missing = TaxonomySeedExpected.TEST_WARDROBE_KEYS.filterNot(keys::contains)
        assertTrue("відсутні ключі гардероба: $missing", missing.isEmpty())
    }

    @Test
    fun `сід — ідентифікатори системних рядків починаються з 1000`() {
        val ids = categories().map { it.id } +
            subtypes().map { it.id } +
            db.colorsQueries.selectAllColors().executeAsList().map { it.id } +
            db.style_tagsQueries.selectAllStyleTags().executeAsList().map { it.id }
        assertTrue("сід-id у діапазоні користувацьких: ${ids.filter { it < 1000L }}", ids.all { it >= 1000L })
    }

    @Test
    fun `сід кольорів — 24 кольори, ключі та hex тотожні палітрі рушія`() {
        val rows = db.colorsQueries.selectAllColors().executeAsList()
        assertEquals(24, rows.size)
        assertEquals(TaxonomySeedExpected.PALETTE_HEX, rows.associate { it.key to it.hex })
        for (row in rows) {
            assertTrue("${row.key}: uk назва кирилицею", isCyrillic(row.name_uk))
            assertTrue("${row.key}: hex ${row.hex}", row.hex.matches(Regex("#[0-9A-Fa-f]{6}")))
        }
    }

    @Test
    fun `сід стилів — шість стилів з en та uk назвами`() {
        val rows = db.style_tagsQueries.selectAllStyleTags().executeAsList()
        assertEquals(TaxonomySeedExpected.STYLE_TAG_KEYS, rows.map { it.key }.toSet())
        for (row in rows) {
            assertTrue("${row.key}: порожня en назва", isLatin(row.name_en))
            assertTrue("${row.key}: порожня uk назва", isCyrillic(row.name_uk))
        }
    }

    @Test
    fun `Section — розширений набір покриває ролі рушія`() {
        for (role in TaxonomySeedExpected.ENGINE_ROLES) {
            assertEquals("роль $role не мапиться", role, Section.fromDb(role)?.db)
        }
    }
}
