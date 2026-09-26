package com.damagdpixl.svita.core.data

import com.damagdpixl.svita.core.model.Section
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Taxonomy repository over the seeded v1 data (24 categories, 133 subtypes, 24 colors, 6 style tags). */
class TaxonomyRepositoryTest {
    private lateinit var f: RepositoriesFixture

    @Before
    fun setUp() {
        f = RepositoriesFixture()
    }

    @Test
    fun `сід таксономії — повні набори`() = runBlocking {
        assertEquals(24, f.taxonomy.categories().size)
        assertEquals(133, f.taxonomy.subtypesBySection(Section.BODY).size +
            f.taxonomy.subtypesBySection(Section.LEGS).size +
            f.taxonomy.subtypesBySection(Section.FEET).size +
            f.taxonomy.subtypesBySection(Section.DRESS).size +
            f.taxonomy.subtypesBySection(Section.OUTER).size +
            f.taxonomy.subtypesBySection(Section.HAT).size +
            f.taxonomy.subtypesBySection(Section.SCARF).size +
            f.taxonomy.subtypesBySection(Section.ACCESSORY).size +
            f.taxonomy.subtypesBySection(Section.BAG).size)
        assertEquals(24, f.taxonomy.observeColors().first().size)
        assertEquals(6, f.taxonomy.observeStyleTags().first().size)
    }

    @Test
    fun `категорії — за id, дерево, спостереження`() = runBlocking {
        val tops = f.taxonomy.category(f.catTops)!!
        assertEquals("Топи", tops.nameUk)
        assertEquals("Tops", tops.nameEn)
        assertEquals(Section.BODY, tops.section)
        assertNull(f.taxonomy.category(424242L))

        val tree = f.taxonomy.categoryTree()
        // Сід має рівний список коренів (parent_id = NULL скрізь).
        assertEquals(24, tree.size)
        assertTrue(tree.none { it.children.isNotEmpty() })
        assertEquals(f.catTops, tree.first().category.id)

        assertEquals(24, f.taxonomy.observeCategories().first().size)
    }

    @Test
    fun `підтипи — за категорією, секцією та ключем`() = runBlocking {
        val topsSubtypes = f.taxonomy.subtypesByCategory(f.catTops)
        assertTrue(topsSubtypes.map { it.key }.contains(f.subTShirt))
        assertTrue(topsSubtypes.all { it.categoryId == f.catTops })

        val feet = f.taxonomy.subtypesBySection(Section.FEET)
        assertTrue(feet.isNotEmpty())
        assertTrue(feet.any { it.key == f.subBoots })

        val tshirt = f.taxonomy.subtypeByKey(f.subTShirt)!!
        assertEquals("Футболка", tshirt.nameUk)
        assertEquals("T-shirt", tshirt.nameEn)
        assertEquals("C2", tshirt.thermalClass)
        assertEquals(tshirt.id, f.taxonomy.subtype(tshirt.id)!!.id)
        assertNull(f.taxonomy.subtypeByKey("no.such.key"))
        assertNull(f.taxonomy.subtype(424242L))
    }

    @Test
    fun `кольори та стильові теги — пошук за ключем`() = runBlocking {
        val white = f.taxonomy.colorByKey("White")!!
        assertEquals("Білий", white.nameUk)
        assertEquals("#FFFFFF", white.hex)
        assertNull(f.taxonomy.colorByKey("NoColor"))

        val casual = f.taxonomy.styleTagByKey("casual")!!
        assertEquals("Повсякденний", casual.nameUk)
        assertEquals("Casual", casual.nameEn)
        assertNull(f.taxonomy.styleTagByKey("no-such-tag"))
    }
}
