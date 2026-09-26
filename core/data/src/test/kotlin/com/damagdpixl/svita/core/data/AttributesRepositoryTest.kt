package com.damagdpixl.svita.core.data

import com.damagdpixl.svita.core.model.AttributeType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Attribute definitions CRUD and per-type validation of values:
 * text / number / enum / multi / color, valid and invalid cases for each,
 * plus the setValue contract (invalid never overwrites, null clears).
 */
class AttributesRepositoryTest {
    private lateinit var f: RepositoriesFixture
    private var item: Long = 0

    @Before
    fun setUp() = runBlocking {
        f = RepositoriesFixture()
        item = f.createItem("Футболка", f.subTShirt)
    }

    private fun valid(s: String) = ValueValidation.Valid(s)
    private fun invalid(kind: ValueErrorKind) = ValueValidation.Invalid(kind)

    // ---------- TEXT ----------

    @Test
    fun `валідація text — валідні та невалідні значення`() = runBlocking {
        val def = f.attributes.createDefinition(null, "brand", AttributeType.TEXT, null, 0)
        assertEquals(valid("Nike"), f.attributes.validate(def, " Nike "))
        assertEquals(valid("а".repeat(500)), f.attributes.validate(def, "а".repeat(500)))
        assertEquals(invalid(ValueErrorKind.EMPTY_TEXT), f.attributes.validate(def, ""))
        assertEquals(invalid(ValueErrorKind.EMPTY_TEXT), f.attributes.validate(def, "   "))
        assertEquals(invalid(ValueErrorKind.TEXT_TOO_LONG), f.attributes.validate(def, "а".repeat(501)))
    }

    // ---------- NUMBER ----------

    @Test
    fun `валідація number — межі з конфігу, кома-роздільник, сміття`() = runBlocking {
        val bounded = f.attributes.createDefinition(null, "warmth", AttributeType.NUMBER, """{"min":0,"max":10}""", 1)
        assertEquals(valid("5.0"), f.attributes.validate(bounded, "5"))
        assertEquals(valid("5.5"), f.attributes.validate(bounded, "5,5"))
        assertEquals(valid("0.0"), f.attributes.validate(bounded, "0"))
        assertEquals(valid("10.0"), f.attributes.validate(bounded, "10"))
        assertEquals(invalid(ValueErrorKind.OUT_OF_RANGE), f.attributes.validate(bounded, "-0.1"))
        assertEquals(invalid(ValueErrorKind.OUT_OF_RANGE), f.attributes.validate(bounded, "10.01"))
        assertEquals(invalid(ValueErrorKind.NOT_A_NUMBER), f.attributes.validate(bounded, "абв"))
        assertEquals(invalid(ValueErrorKind.NOT_A_NUMBER), f.attributes.validate(bounded, "NaN"))
    }

    @Test
    fun `валідація number — без конфігу меж немає, битий конфіг помітний`() = runBlocking {
        val free = f.attributes.createDefinition(null, "price-attr", AttributeType.NUMBER, null, 2)
        assertEquals(valid("-99.5"), f.attributes.validate(free, "-99,5"))

        val broken = f.attributes.createDefinition(null, "broken-num", AttributeType.NUMBER, "{min: не-jason}", 3)
        assertEquals(invalid(ValueErrorKind.MALFORMED_CONFIG), f.attributes.validate(broken, "5"))

        val inverted = f.attributes.createDefinition(null, "inverted", AttributeType.NUMBER, """{"min":10,"max":0}""", 4)
        assertEquals(invalid(ValueErrorKind.MALFORMED_CONFIG), f.attributes.validate(inverted, "5"))
    }

    // ---------- ENUM ----------

    @Test
    fun `валідація enum — значення з опцій, інакше відмова`() = runBlocking {
        val def = f.attributes.createDefinition(
            null, "material", AttributeType.ENUM,
            """{"options":["бавовна","вовна","синтетика"]}""", 5,
        )
        assertEquals(valid("бавовна"), f.attributes.validate(def, " бавовна "))
        assertEquals(invalid(ValueErrorKind.NOT_AN_OPTION), f.attributes.validate(def, "шовк"))
        assertEquals(invalid(ValueErrorKind.NOT_AN_OPTION), f.attributes.validate(def, "Бавовна")) // чутливо до регістру

        val noConfig = f.attributes.createDefinition(null, "material-broken", AttributeType.ENUM, null, 6)
        assertEquals(invalid(ValueErrorKind.MISSING_CONFIG), f.attributes.validate(noConfig, "бавовна"))
    }

    // ---------- MULTI ----------

    @Test
    fun `валідація multi — JSON-масив підмножина опцій, дедуплікація`() = runBlocking {
        val def = f.attributes.createDefinition(
            null, "care", AttributeType.MULTI,
            """{"options":["прання","хімчистка","відпарювання"]}""", 7,
        )
        assertEquals(valid("""["прання","хімчистка"]"""), f.attributes.validate(def, """["прання","хімчистка"]"""))
        assertEquals(valid("""["прання"]"""), f.attributes.validate(def, """["прання","прання"]"""))
        assertEquals(valid("[]"), f.attributes.validate(def, "[]"))
        assertEquals(invalid(ValueErrorKind.NOT_AN_OPTION), f.attributes.validate(def, """["прання","гладити"]"""))
        assertEquals(invalid(ValueErrorKind.MALFORMED_MULTI), f.attributes.validate(def, "прання,хімчистка"))
        assertEquals(invalid(ValueErrorKind.MALFORMED_MULTI), f.attributes.validate(def, """[1,2]"""))
        assertEquals(invalid(ValueErrorKind.MISSING_CONFIG), f.attributes.validate(
            f.attributes.createDefinition(null, "care-broken", AttributeType.MULTI, null, 8), "[]",
        ))
    }

    // ---------- COLOR ----------

    @Test
    fun `валідація color — hex нормалізується до верхнього регістру`() = runBlocking {
        val def = f.colorDefinitionId()
        assertEquals(valid("#ABCDEF"), f.attributes.validate(def, "#abcdef"))
        assertEquals(valid("#123456"), f.attributes.validate(def, "#123456"))
        assertEquals(invalid(ValueErrorKind.MALFORMED_COLOR), f.attributes.validate(def, "FFFFFF"))
        assertEquals(invalid(ValueErrorKind.MALFORMED_COLOR), f.attributes.validate(def, "#FFF"))
        assertEquals(invalid(ValueErrorKind.MALFORMED_COLOR), f.attributes.validate(def, "#GGGGGG"))
        assertEquals(invalid(ValueErrorKind.MALFORMED_COLOR), f.attributes.validate(def, "#12345"))
    }

    // ---------- setValue ----------

    @Test
    fun `setValue — валідне значення зберігається, апсерт замінює`() = runBlocking {
        val def = f.colorDefinitionId()
        assertEquals(ValueWriteResult.Saved, f.attributes.setValue(item, def, "#ffffff"))
        assertEquals(listOf("#FFFFFF"), f.attributes.valuesForItem(item).map { it.value })
        assertEquals(ValueWriteResult.Saved, f.attributes.setValue(item, def, "#000000"))
        assertEquals(listOf("#000000"), f.attributes.valuesForItem(item).map { it.value })
    }

    @Test
    fun `setValue — невалідне значення не перезаписує попереднє`() = runBlocking {
        val def = f.colorDefinitionId()
        f.attributes.setValue(item, def, "#FFFFFF")
        assertEquals(
            ValueWriteResult.Rejected(ValueErrorKind.MALFORMED_COLOR),
            f.attributes.setValue(item, def, "білий"),
        )
        assertEquals(listOf("#FFFFFF"), f.attributes.valuesForItem(item).map { it.value })
    }

    @Test
    fun `setValue — null чистить значення, невідоме визначення розпізнається`() = runBlocking {
        val def = f.colorDefinitionId()
        f.attributes.setValue(item, def, "#FFFFFF")
        assertEquals(ValueWriteResult.Saved, f.attributes.setValue(item, def, null))
        assertEquals(emptyList<Any>(), f.attributes.valuesForItem(item))
        assertEquals(ValueWriteResult.UnknownDefinition, f.attributes.setValue(item, 999L, "#FFFFFF"))

        // Неіснуюча річ — FK-помилка назовні.
        val thrown = runCatching { f.attributes.setValue(999L, def, "#FFFFFF") }.exceptionOrNull()
        assertTrue("expected FK failure, got $thrown", thrown != null)
    }

    // ---------- definitions CRUD + scoping ----------

    @Test
    fun `визначення — глобальні та категорійні, scope спостереження`() = runBlocking {
        val cat = f.catTops
        val global = f.attributes.createDefinition(null, "color", AttributeType.COLOR, null, 0)
        val scoped = f.attributes.createDefinition(cat, "сукня-довжина", AttributeType.TEXT, null, 10)
        f.attributes.createDefinition(f.catTrousers, "посадка", AttributeType.TEXT, null, 20)

        assertEquals(setOf(global, scoped), f.attributes.observeDefinitions(cat).first().map { it.id }.toSet())
        assertEquals(3, f.attributes.observeDefinitions(null).first().size)
        assertEquals(3, f.attributes.definitions().size)

        f.attributes.updateDefinition(scoped, cat, "довжина", AttributeType.TEXT, null, 5)
        assertEquals("довжина", f.attributes.definition(scoped)!!.key)

        // Значення під визначенням каскадно видаляються разом з ним.
        f.attributes.setValue(item, scoped, "міді")
        f.attributes.deleteDefinition(scoped)
        assertNull(f.attributes.definition(scoped))
        assertEquals(emptyList<Any>(), f.attributes.valuesForItem(item))
        assertTrue(f.attributes.observeDefinitions(cat).first().all { it.key != "довжина" })
    }
}
