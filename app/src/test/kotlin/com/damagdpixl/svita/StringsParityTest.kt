package com.damagdpixl.svita

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * i18n gate: English keys are canonical (`values/strings.xml`); the Ukrainian
 * file must carry exactly the same key set, with non-blank values on both sides.
 */
class StringsParityTest {

    private val enFile = File("src/main/res/values/strings.xml")
    private val ukFile = File("src/main/res/values-uk/strings.xml")

    private fun parse(file: File): Map<String, String> {
        assertTrue("Missing strings file: $file", file.exists())
        val doc = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(file)
        val strings = doc.getElementsByTagName("string")
        val result = mutableMapOf<String, String>()
        for (i in 0 until strings.length) {
            val node = strings.item(i) as Element
            val key = node.getAttribute("name")
            val value = node.textContent
            result[key] = value
        }
        return result
    }

    /** Plural name -> the set of quantities defined for it in this file. */
    private fun parsePlurals(file: File): Map<String, Set<String>> {
        val doc = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(file)
        val plurals = doc.getElementsByTagName("plurals")
        val result = mutableMapOf<String, MutableSet<String>>()
        for (i in 0 until plurals.length) {
            val node = plurals.item(i) as Element
            val name = node.getAttribute("name")
            val items = node.getElementsByTagName("item")
            val quantities = result.getOrPut(name) { mutableSetOf() }
            for (j in 0 until items.length) {
                quantities += (items.item(j) as Element).getAttribute("quantity")
            }
        }
        return result
    }

    @Test
    fun `english and ukrainian key sets are identical`() {
        val en = parse(enFile)
        val uk = parse(ukFile)

        assertTrue("English strings.xml must not be empty", en.isNotEmpty())
        assertTrue("Ukrainian strings.xml must not be empty", uk.isNotEmpty())

        val missingInUk = en.keys - uk.keys
        val missingInEn = uk.keys - en.keys
        assertTrue(
            "Keys missing in values-uk/strings.xml: $missingInUk",
            missingInUk.isEmpty(),
        )
        assertTrue(
            "Keys missing in values/strings.xml: $missingInEn",
            missingInEn.isEmpty(),
        )
        assertEquals(en.keys, uk.keys)
    }

    @Test
    fun `no string is blank in either language`() {
        val en = parse(enFile)
        val uk = parse(ukFile)
        val blankEn = en.filterValues { it.isBlank() }.keys
        val blankUk = uk.filterValues { it.isBlank() }.keys
        assertTrue("Blank values in en: $blankEn", blankEn.isEmpty())
        assertTrue("Blank values in uk: $blankUk", blankUk.isEmpty())
    }

    /**
     * Plurals carry their own key namespace — checked for name parity only:
     * the required QUANTITY sets legitimately differ per language
     * (English one/other; Ukrainian one/few/many/other).
     */
    @Test
    fun `plural key sets are identical and every plural is non-empty`() {
        val en = parsePlurals(enFile)
        val uk = parsePlurals(ukFile)

        val missingInUk = en.keys - uk.keys
        val missingInEn = uk.keys - en.keys
        assertTrue("Plurals missing in values-uk/strings.xml: $missingInUk", missingInUk.isEmpty())
        assertTrue("Plurals missing in values/strings.xml: $missingInEn", missingInEn.isEmpty())
        assertEquals(en.keys, uk.keys)

        val emptyUk = uk.filterValues { it.isEmpty() }.keys
        val emptyEn = en.filterValues { it.isEmpty() }.keys
        assertTrue("Empty plurals in en: $emptyEn", emptyEn.isEmpty())
        assertTrue("Empty plurals in uk: $emptyUk", emptyUk.isEmpty())
    }
}
