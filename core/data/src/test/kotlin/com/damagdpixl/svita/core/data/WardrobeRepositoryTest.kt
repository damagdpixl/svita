package com.damagdpixl.svita.core.data

import com.damagdpixl.svita.core.model.OutfitEntry
import com.damagdpixl.svita.core.model.Section
import com.damagdpixl.svita.core.model.Season
import com.damagdpixl.svita.core.model.Sex
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Wardrobe repository: aggregate round-trip, every observeItems filter
 * combination, transaction integrity (a failed creation must leave no orphan
 * photos/tags), wear-log JSON scrubbing on delete.
 */
class WardrobeRepositoryTest {
    private lateinit var f: RepositoriesFixture

    @Before
    fun setUp() {
        f = RepositoriesFixture()
    }

    @Test
    fun `річ — повний aggregate round-trip`() = runBlocking {
        val tag = f.wardrobe.createTag("літнє")
        val colorDef = f.attributes.createDefinition(null, "color", com.damagdpixl.svita.core.model.AttributeType.COLOR, null, 0)
        val id = f.wardrobe.createItem(
            draft = ItemDraft(
                subtypeId = f.subtypeIdOf(f.subTShirt),
                name = "Біла футболка",
                notes = "улюблена",
                price = 299.0,
                purchaseDate = LocalDate(2025, 5, 1),
                seasons = setOf(Season.SPRING, Season.SUMMER),
                sex = Sex.FEMALE,
                rating = 5,
            ),
            photos = listOf("content://p1.jpg", "content://p2.jpg"),
            tagIds = setOf(tag),
            attributeValues = mapOf(colorDef to "#FFFFFF"),
        )

        val agg = f.wardrobe.getItem(id)!!
        assertEquals("Біла футболка", agg.item.name)
        assertEquals("улюблена", agg.item.notes)
        assertEquals(299.0, agg.item.price!!, 0.0)
        assertEquals(LocalDate(2025, 5, 1), agg.item.purchaseDate)
        assertEquals(setOf(Season.SPRING, Season.SUMMER), agg.item.seasons)
        assertEquals(Sex.FEMALE, agg.item.sex)
        assertEquals(5, agg.item.rating)
        assertEquals(false, agg.item.archived)
        assertEquals(f.subtypeIdOf(f.subTShirt), agg.subtype.id)
        assertEquals(f.catTops, agg.category?.id)
        assertEquals(listOf("content://p1.jpg", "content://p2.jpg"), agg.photos.map { it.path })
        assertEquals(listOf(0, 1), agg.photos.map { it.position })
        assertEquals(listOf("літнє"), agg.tags.map { it.name })
        assertEquals(listOf(colorDef), agg.attributes.map { it.definitionId })
        assertEquals(listOf("#FFFFFF"), agg.attributes.map { it.value })
        assertNull(f.wardrobe.getItem(999L))
    }

    @Test
    fun `річ — оновлення полів та заміна дітей`() = runBlocking {
        val id = f.createItem("Футболка", f.subTShirt, photos = listOf("content://old.jpg"))
        val before = f.wardrobe.getItem(id)!!

        // photos = null: untouched.
        f.wardrobe.updateItem(id, ItemDraft(f.subtypeIdOf(f.subTShirt), "Футболка оновлена", price = 199.0))
        val afterFields = f.wardrobe.getItem(id)!!
        assertEquals("Футболка оновлена", afterFields.item.name)
        assertEquals(199.0, afterFields.item.price!!, 0.0)
        assertEquals(listOf("content://old.jpg"), afterFields.photos.map { it.path })
        assertTrue(afterFields.item.updatedAt > before.item.updatedAt)

        // photos = non-null: full replacement.
        f.wardrobe.updateItem(id, ItemDraft(f.subtypeIdOf(f.subTShirt), "Футболка оновлена"), photos = listOf("content://a.jpg", "content://b.jpg"))
        assertEquals(listOf("content://a.jpg", "content://b.jpg"), f.wardrobe.getItem(id)!!.photos.map { it.path })

        // Missing id fails fast.
        val missing = runCatching {
            f.wardrobe.updateItem(999L, ItemDraft(f.subtypeIdOf(f.subTShirt), "привид"))
        }.exceptionOrNull()
        assertTrue("expected NoSuchElementException, got $missing", missing is NoSuchElementException)
    }

    @Test
    fun `спостереження wardrobe — кожен фільтр observeItems`() = runBlocking {
        val summerTag = f.tagIdOf("літнє")
        val baseTag = f.tagIdOf("базове")
        val winterTag = f.tagIdOf("зимове")
        val colorDef = f.colorDefinitionId()

        val tshirt = f.createItem(
            "Біла футболка", f.subTShirt,
            seasons = setOf(Season.SPRING, Season.SUMMER), sex = Sex.FEMALE,
            tagIds = setOf(summerTag), attributeValues = mapOf(colorDef to "#FFFFFF"),
        )
        val jeans = f.createItem(
            "Сині джинси", f.subJeans,
            seasons = setOf(Season.SPRING, Season.AUTUMN), sex = Sex.UNISEX,
            tagIds = setOf(summerTag, baseTag),
        )
        val jacket = f.createItem(
            "Зимова куртка", f.subJacket,
            seasons = setOf(Season.WINTER), sex = Sex.MALE,
            tagIds = setOf(winterTag),
        )
        val hat = f.createItem("В'язана шапка", f.subHat, seasons = setOf(Season.WINTER), archived = true)

        suspend fun ids(filter: ItemFilter) = f.wardrobe.observeItems(filter).first().map { it.id }

        // Every field off: all non-archived items.
        assertEquals(listOf(tshirt, jeans, jacket), ids(ItemFilter()))
        // Archived toggle.
        assertEquals(listOf(tshirt, jeans, jacket, hat), ids(ItemFilter(includeArchived = true)))
        // Subtype.
        assertEquals(listOf(jacket), ids(ItemFilter(subtypeId = f.subtypeIdOf(f.subJacket))))
        // Category (Trousers -> jeans only).
        assertEquals(listOf(jeans), ids(ItemFilter(categoryId = f.catTrousers)))
        // Section (jackets seed as OUTER, not BODY).
        assertEquals(listOf(tshirt), ids(ItemFilter(section = Section.BODY)))
        assertEquals(listOf(jacket), ids(ItemFilter(section = Section.OUTER)))
        assertEquals(emptyList<Long>(), ids(ItemFilter(section = Section.FEET)))
        // Seasons: intersect semantics.
        assertEquals(listOf(tshirt), ids(ItemFilter(seasons = setOf(Season.SUMMER))))
        assertEquals(listOf(jeans), ids(ItemFilter(seasons = setOf(Season.AUTUMN))))
        assertEquals(setOf(tshirt, jeans, jacket), ids(ItemFilter(seasons = setOf(Season.SPRING, Season.WINTER))).toSet())
        // Sex: strict equality.
        assertEquals(listOf(jacket), ids(ItemFilter(sex = Sex.MALE)))
        assertEquals(listOf(tshirt), ids(ItemFilter(sex = Sex.FEMALE)))
        assertEquals(listOf(jeans), ids(ItemFilter(sex = Sex.UNISEX)))
        // Tags: conjunctive (ALL); both tshirt and jeans carry summerTag.
        assertEquals(listOf(tshirt, jeans), ids(ItemFilter(tagIds = setOf(summerTag))))
        assertEquals(listOf(jeans), ids(ItemFilter(tagIds = setOf(summerTag, baseTag))))
        assertEquals(emptyList<Long>(), ids(ItemFilter(tagIds = setOf(summerTag, winterTag))))
        // Color via attribute definition, hex normalized on the filter side.
        assertEquals(listOf(tshirt), ids(ItemFilter(colorDefinitionId = colorDef, colorHex = "#ffffff")))
        assertEquals(emptyList<Long>(), ids(ItemFilter(colorDefinitionId = colorDef, colorHex = "#000000")))
        // Name search: Unicode-aware case-insensitive substring.
        assertEquals(listOf(tshirt), ids(ItemFilter(nameQuery = "ФУТБОЛ")))
        assertEquals(listOf(jacket), ids(ItemFilter(nameQuery = "куртка")))
        assertEquals(emptyList<Long>(), ids(ItemFilter(nameQuery = "сорочка")))
        // Combinations.
        assertEquals(listOf(jacket), ids(ItemFilter(section = Section.OUTER, seasons = setOf(Season.WINTER))))
        assertEquals(listOf(hat), ids(ItemFilter(includeArchived = true, nameQuery = "шапка")))
        assertEquals(
            listOf(jeans),
            ids(ItemFilter(sex = Sex.UNISEX, seasons = setOf(Season.SPRING), tagIds = setOf(baseTag))),
        )
    }

    @Test
    fun `спостереження wardrobe — емісія після зміни`() = runBlocking {
        val id = f.createItem("Футболка", f.subTShirt)
        val flow = f.wardrobe.observeItems()

        val next = flow.nextAfter { f.wardrobe.setArchived(id, true) }
        assertTrue(next.isEmpty())
    }

    @Test
    fun `транзакція — невдале створення речі не лишає сиріт`() = runBlocking {
        val thrown = runCatching {
            f.wardrobe.createItem(
                draft = ItemDraft(f.subtypeIdOf(f.subTShirt), "зламана"),
                photos = listOf("content://ghost.jpg"),
                tagIds = setOf(424_242L), // неіснуючий тег — FK падає посеред транзакції
                attributeValues = mapOf(f.colorDefinitionId() to "#FFFFFF"),
            )
        }.exceptionOrNull()
        assertTrue("expected FK failure, got $thrown", thrown != null)

        assertEquals(0, f.db.itemsQueries.selectAllItems().executeAsList().size)
        assertEquals(0, f.db.photosQueries.selectAllPhotos().executeAsList().size)
        assertEquals(0, f.db.tagsQueries.selectAllItemTags().executeAsList().size)
        assertEquals(0, f.db.attribute_valuesQueries.selectAllAttributeValues().executeAsList().size)
    }

    @Test
    fun `видалення речі — каскад дочірніх таблиць і чистка wear_log`() = runBlocking {
        val a = f.createItem("Футболка", f.subTShirt, photos = listOf("content://a.jpg"))
        val b = f.createItem("Джинси", f.subJeans)
        val tag = f.tagIdOf("літнє")
        f.createItem("Куртка", f.subJacket, tagIds = setOf(tag))

        val outfit = f.outfits.createOutfit(
            OutfitDraft("Прогулянка"),
            entries = listOf(
                OutfitEntry(0L, a, Section.BODY),
                OutfitEntry(0L, b, Section.LEGS),
            ),
        )
        val packing = f.packing.createPackingList("Виїзд", itemIds = listOf(a, b))
        f.wearLog.addEntry(LocalDate(2025, 6, 1), null, listOf(a, b))
        f.wearLog.addEntry(LocalDate(2025, 6, 2), null, listOf(a))
        f.wearLog.addEntry(LocalDate(2025, 6, 3), null, listOf(b))

        f.wardrobe.deleteItem(a)

        // Direct children cascaded.
        assertNull(f.wardrobe.getItem(a))
        assertEquals(0, f.db.photosQueries.selectAllPhotos().executeAsList().size)
        // Outfit and packing lists lost the item but survive.
        assertEquals(listOf(b), f.outfits.getOutfit(outfit)!!.entries.map { it.itemId })
        assertEquals(listOf(b), f.packing.getPackingList(packing)!!.entries.map { it.itemId })
        // wear_log JSON scrubbed of the deleted id only.
        val entries = f.wearLog.observeByRange(LocalDate(2025, 6, 1), LocalDate(2025, 6, 3)).first()
        assertEquals(listOf(listOf(b), emptyList(), listOf(b)), entries.map { it.itemIds })
        // Other items untouched, tags still exist.
        assertEquals(listOf("Куртка", "Джинси").sorted(), f.wardrobe.observeItems().first().map { it.name }.sorted())
        assertEquals(1, f.wardrobe.observeTags().first().size)
    }

    @Test
    fun `теги — створення, пошук за іменем, видалення`() = runBlocking {
        val id = f.wardrobe.createTag("літнє")
        assertEquals(id, f.tagIdOf("літнє")) // повторне — повертає наявний
        val item = f.createItem("Футболка", f.subTShirt, tagIds = setOf(id))
        f.wardrobe.deleteTag(id)
        assertEquals(0, f.wardrobe.getItem(item)!!.tags.size)
        assertNull(f.wardrobe.tagByName("літнє"))
    }

    @Test
    fun `перейменування тегу — на місці, зв'язки з речами живуть`() = runBlocking {
        val id = f.wardrobe.createTag("літнє")
        val item = f.createItem("Футболка", f.subTShirt, tagIds = setOf(id))

        f.wardrobe.renameTag(id, "спекотне")

        // Той самий рядок під новою назвою: старої немає, нова за тим самим id.
        assertNull(f.wardrobe.tagByName("літнє"))
        val renamed = f.wardrobe.tagByName("спекотне")!!
        assertEquals(id, renamed.id)
        assertEquals(listOf("спекотне"), f.wardrobe.getItem(item)!!.tags.map { it.name })
        // Невідомий id — швидка відмова.
        try {
            f.wardrobe.renameTag(99999L, "привид")
            error("renameTag must fail on an unknown id")
        } catch (expected: NoSuchElementException) {
            // очікувано
        }
    }

    @Test
    fun `перейменування тегу на наявну назву падає з UNIQUE`() = runBlocking {
        val a = f.wardrobe.createTag("літнє")
        f.wardrobe.createTag("зимове")
        var constraintHit = false
        try {
            f.wardrobe.renameTag(a, "зимове")
        } catch (expected: Exception) {
            constraintHit = expected.message?.contains("UNIQUE", ignoreCase = true) == true
        }
        assertTrue(constraintHit)
        // Назви лишилися недоторканими.
        assertEquals("літнє", f.wardrobe.tagByName("літнє")!!.name)
        assertEquals("зимове", f.wardrobe.tagByName("зимове")!!.name)
    }

    @Test
    fun `архівація — перемикається і ховається з базового списку`() = runBlocking {
        val id = f.createItem("Футболка", f.subTShirt)
        f.wardrobe.setArchived(id, true)
        assertEquals(0, f.wardrobe.observeItems().first().size)
        assertEquals(1, f.wardrobe.observeItems(ItemFilter(includeArchived = true)).first().size)
        assertTrue(f.wardrobe.getItem(id)!!.item.archived)
        f.wardrobe.setArchived(id, false)
        assertEquals(1, f.wardrobe.observeItems().first().size)
    }
}
