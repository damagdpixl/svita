package com.damagdpixl.svita.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EnumsTest {
    @Test
    fun `сезони — маска кодує всі чотири значення`() {
        assertEquals(setOf(Season.SPRING, Season.SUMMER, Season.AUTUMN, Season.WINTER), Season.fromBitmask(15))
        assertEquals(setOf(Season.SPRING, Season.AUTUMN), Season.fromBitmask(5))
        assertTrue(Season.fromBitmask(0).isEmpty())
    }

    @Test
    fun `сезони — round trip маски`() {
        for (mask in 0..15) {
            assertEquals(mask, Season.fromBitmask(mask).toBitmask())
        }
    }

    @Test
    fun `секції — значення збігаються зі сховищем`() {
        assertEquals("body", Section.BODY.db)
        assertEquals("accessory", Section.ACCESSORY.db)
        assertEquals(Section.LEGS, Section.fromDb("legs"))
        assertNull(Section.fromDb("wings"))
    }

    @Test
    fun `стать — значення збігаються зі сховищем`() {
        assertEquals(Sex.MALE, Sex.fromDb("m"))
        assertEquals(Sex.FEMALE, Sex.fromDb("f"))
        assertEquals(Sex.UNISEX, Sex.fromDb("u"))
        assertNull(Sex.fromDb("x"))
    }

    @Test
    fun `типи атрибутів — значення збігаються зі сховищем`() {
        assertEquals(AttributeType.ENUM, AttributeType.fromDb("enum"))
        assertEquals(AttributeType.COLOR, AttributeType.fromDb("color"))
        assertNull(AttributeType.fromDb("bool"))
    }
}
