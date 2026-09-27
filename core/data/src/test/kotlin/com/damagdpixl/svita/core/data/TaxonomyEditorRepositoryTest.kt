package com.damagdpixl.svita.core.data

import com.damagdpixl.svita.core.model.AttributeType
import com.damagdpixl.svita.core.model.Section
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Редактор таксономії (USP P2 T4): створення власних категорій з авто-підтипом,
 * обмежене редагування системних, упорядкування, видалення із захистом речей
 * і «скинути до стандартних».
 */
class TaxonomyEditorRepositoryTest {

    private lateinit var f: RepositoriesFixture

    @Before
    fun setUp() {
        f = RepositoriesFixture()
    }

    @Test
    fun `створена категорія отримує авто-підтип і з'являється у списках`() = runBlocking {
        val id = f.taxonomyEditor.createCategory(
            section = Section.BODY,
            nameEn = "Capes",
            nameUk = "Пончо",
            icon = "coat",
        )
        val created = f.taxonomy.categories().first { it.id == id }
        assertEquals(false, created.isSystem)
        assertEquals("Пончо", created.nameUk)
        assertEquals(Section.BODY, created.section)

        // Авто-підтип: ключ custom.<id>, нейтральний клас, назви як у категорії.
        val subtypes = f.taxonomy.subtypesByCategory(id)
        assertEquals(1, subtypes.size)
        assertEquals("custom.$id", subtypes[0].key)
        assertEquals("Capes", subtypes[0].nameEn)
        assertEquals("Пончо", subtypes[0].nameUk)
        assertEquals("N", subtypes[0].thermalClass)
    }

    @Test
    fun `повне редагування власної категорії тягне назву авто-підтипа`() = runBlocking {
        val id = f.taxonomyEditor.createCategory(Section.BODY, "Capes", "Пончо", null)
        f.taxonomyEditor.updateCustomCategory(
            id = id,
            section = Section.OUTER,
            nameEn = "Capes & ponchos",
            nameUk = "Пончо й накидки",
            icon = null,
            sortOrder = 5,
        )
        val category = f.taxonomy.category(id)!!
        assertEquals("Пончо й накидки", category.nameUk)
        assertEquals(Section.OUTER, category.section)
        assertEquals(5, category.sortOrder)
        assertEquals("Пончо й накидки", f.taxonomy.subtypesByCategory(id)[0].nameUk)
    }

    @Test
    fun `системну категорію можна редагувати лише у назвах та іконці`() = runBlocking {
        val before = f.taxonomy.category(f.catTops)!!
        f.taxonomyEditor.updateCategory(
            id = f.catTops,
            nameEn = "Tops & tanks",
            nameUk = "Топи й майки",
            icon = "vest",
        )
        val after = f.taxonomy.category(f.catTops)!!
        assertEquals("Tops & tanks", after.nameEn)
        assertEquals("Топи й майки", after.nameUk)
        assertEquals("vest", after.icon)
        // Розділ і порядок — недоторкані (дані сиду), сідові підтипи не чіплені.
        assertEquals(before.section, after.section)
        assertEquals(before.sortOrder, after.sortOrder)
        assertEquals("Футболка", f.taxonomy.subtypeByKey("body.t-shirt")!!.nameUk)
        // Повне редагування на системній категорії відхиляється.
        try {
            f.taxonomyEditor.updateCustomCategory(f.catTops, Section.BODY, "X", "X", null, 0)
            error("updateCustomCategory must refuse system categories")
        } catch (expected: IllegalArgumentException) {
            // очікувано
        }
    }

    @Test
    fun `видалення категорії з речами заблоковане а порожньої проходить`() = runBlocking {
        val id = f.taxonomyEditor.createCategory(Section.BODY, "Capes", "Пончо", null)
        val subtype = f.taxonomy.subtypesByCategory(id)[0]
        f.wardrobe.createItem(draft = ItemDraft(subtypeId = subtype.id, name = "Red cape"))

        val blocked = f.taxonomyEditor.deleteCategory(id)
        assertEquals(CategoryDeleteBlock.HAS_ITEMS, (blocked as CategoryDeleteResult.Blocked).reason)
        assertNotNull(f.taxonomy.category(id))

        f.wardrobe.deleteItem(f.wardrobe.observeItems().first()[0].id)
        assertEquals(CategoryDeleteResult.Deleted, f.taxonomyEditor.deleteCategory(id))
        assertNull(f.taxonomy.category(id))
        assertTrue(f.taxonomy.subtypesByCategory(id).isEmpty())
    }

    @Test
    fun `поле категорії каскадно видаляється разом з нею`() = runBlocking {
        val id = f.taxonomyEditor.createCategory(Section.BODY, "Capes", "Пончо", null)
        val definitionId = f.attributes.createDefinition(id, "hood", AttributeType.TEXT, null, 0)
        assertEquals(CategoryDeleteResult.Deleted, f.taxonomyEditor.deleteCategory(id))
        assertNull(f.attributes.definition(definitionId))
    }

    @Test
    fun `системну категорію з сідовими підтипами видалити не можна`() = runBlocking {
        val result = f.taxonomyEditor.deleteCategory(f.catTops)
        assertEquals(CategoryDeleteBlock.HAS_SUBTYPES, (result as CategoryDeleteResult.Blocked).reason)
    }

    @Test
    fun `упорядкування міняє місцями власних сусідів`() = runBlocking {
        val first = f.taxonomyEditor.createCategory(Section.BODY, "Capes", "Пончо", null)
        val second = f.taxonomyEditor.createCategory(Section.BODY, "Wraps", "Палантини", null)
        assertTrue(
            f.taxonomy.categories().first { it.id == first }.sortOrder <
                f.taxonomy.categories().first { it.id == second }.sortOrder,
        )

        f.taxonomyEditor.moveCustomCategory(second, SortMove.UP)
        val tail = f.taxonomy.categories().filter { it.id == first || it.id == second }
        assertEquals(second, tail.first().id)
        assertEquals(first, tail.last().id)
    }

    @Test
    fun `скидання повертає сід і прибирає власні категорії з полями`() = runBlocking {
        // Знімок чистого стану — після скидання має збігтися рів за рівнем.
        val pristine = f.taxonomy.categories()

        f.taxonomyEditor.updateCategory(f.catTops, "Changed", "Змінено", null)
        f.taxonomyEditor.updateCategory(f.catTrousers, "Changed too", "Теж змінено", "bag")
        val customId = f.taxonomyEditor.createCategory(Section.BODY, "Capes", "Пончо", null)
        val customDefinition = f.attributes.createDefinition(
            customId, "hood", AttributeType.ENUM, """{"options":["yes","no"]}""", 0,
        )
        val globalDefinition = f.attributes.createDefinition(null, "mood", AttributeType.TEXT, null, 0)

        assertEquals(ResetTaxonomyResult.Reset, f.taxonomyEditor.resetToDefaults())

        assertEquals(pristine, f.taxonomy.categories())
        assertNull(f.taxonomy.category(customId))
        assertNull(f.attributes.definition(customDefinition))
        assertNotNull(f.attributes.definition(globalDefinition))
        assertTrue(f.taxonomy.subtypesByCategory(customId).isEmpty())
    }

    @Test
    fun `скидання блоковане поки у власній категорії є речі`() = runBlocking {
        val customId = f.taxonomyEditor.createCategory(Section.BODY, "Capes", "Пончо", null)
        val subtype = f.taxonomy.subtypesByCategory(customId)[0]
        f.wardrobe.createItem(draft = ItemDraft(subtypeId = subtype.id, name = "Red cape"))

        assertEquals(ResetTaxonomyResult.BlockedByItems, f.taxonomyEditor.resetToDefaults())
        assertNotNull(f.taxonomy.category(customId))
    }

    @Test
    fun `лічильник речей рахує через підтипи`() = runBlocking {
        val customId = f.taxonomyEditor.createCategory(Section.BODY, "Capes", "Пончо", null)
        val subtype = f.taxonomy.subtypesByCategory(customId)[0]
        f.createItem("Shirt one", "body.t-shirt")
        f.wardrobe.createItem(draft = ItemDraft(subtypeId = subtype.id, name = "Red cape"))
        f.wardrobe.createItem(draft = ItemDraft(subtypeId = subtype.id, name = "Blue cape"))

        val counts = f.taxonomyEditor.itemCountsByCategory()
        assertEquals(2L, counts[customId])
        assertEquals(1L, counts[f.catTops])
    }

    @Test
    fun `неіснуюча категорія падає з NoSuchElementException`() = runBlocking {
        try {
            f.taxonomyEditor.updateCategory(99999L, "X", "X", null)
            error("updateCategory must fail on an unknown id")
        } catch (expected: NoSuchElementException) {
            // очікувано
        }
        try {
            f.taxonomyEditor.deleteCategory(99999L)
            error("deleteCategory must fail on an unknown id")
        } catch (expected: NoSuchElementException) {
            // очікувано
        }
    }
}
