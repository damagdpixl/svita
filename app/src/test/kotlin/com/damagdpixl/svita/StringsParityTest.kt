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
}
