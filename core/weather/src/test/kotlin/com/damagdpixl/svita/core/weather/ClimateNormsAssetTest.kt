package com.damagdpixl.svita.core.weather

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ClimateNormsAssetTest {

    @Test
    fun bundledAssetParsesAndIsSane() {
        val norms = ClimateNormsAsset.loadBundled()
        assertEquals((1..12).toSet(), norms.keys)
        for ((month, norm) in norms) {
            assertEquals(month, norm.month)
            assertTrue(norm.tempMinC.isFinite() && norm.tempMaxC.isFinite(), "month $month non-finite")
            assertTrue(norm.tempMinC <= norm.tempMaxC, "month $month inverted temperatures")
            assertTrue(norm.condition.isNotBlank(), "month $month blank condition")
        }
        // Temperate sanity: deep-winter minimum far below high-summer minimum.
        assertTrue(norms.getValue(1).tempMinC < norms.getValue(7).tempMinC)
        assertTrue(norms.getValue(7).tempMaxC > 20.0)
        assertTrue(norms.getValue(1).tempMinC < 0.0)
    }

    @Test
    fun parseAcceptsValidTwelveMonthTable() {
        val raw = """
            {"months": [
              {"month": 1, "tempMinC": -5.8, "tempMaxC": -0.9, "condition": "overcast"},
              {"month": 2, "tempMinC": -4.9, "tempMaxC": 0.9, "condition": "overcast"},
              {"month": 3, "tempMinC": -0.3, "tempMaxC": 6.9, "condition": "overcast"},
              {"month": 4, "tempMinC": 5.0, "tempMaxC": 14.8, "condition": "partly_cloudy"},
              {"month": 5, "tempMinC": 10.0, "tempMaxC": 20.8, "condition": "partly_cloudy"},
              {"month": 6, "tempMinC": 13.6, "tempMaxC": 24.1, "condition": "partly_cloudy"},
              {"month": 7, "tempMinC": 15.9, "tempMaxC": 25.9, "condition": "clear"},
              {"month": 8, "tempMinC": 14.9, "tempMaxC": 25.3, "condition": "clear"},
              {"month": 9, "tempMinC": 10.4, "tempMaxC": 19.8, "condition": "partly_cloudy"},
              {"month": 10, "tempMinC": 5.4, "tempMaxC": 13.2, "condition": "overcast"},
              {"month": 11, "tempMinC": 0.9, "tempMaxC": 6.4, "condition": "overcast"},
              {"month": 12, "tempMinC": -3.6, "tempMaxC": 0.9, "condition": "overcast"}
            ]}
        """.trimIndent()
        assertEquals(12, ClimateNormsAsset.parse(raw).size)
    }

    @Test
    fun parseRejectsGarbage() {
        assertFailsWith<IllegalArgumentException> { ClimateNormsAsset.parse("<html>oops</html>") }
    }

    @Test
    fun parseRejectsIncompleteTable() {
        val elevenMonths = """{"months": [""" +
            (1..11).joinToString(",") {
                """{"month": $it, "tempMinC": 0.0, "tempMaxC": 10.0, "condition": "overcast"}"""
            } + "]}"
        assertFailsWith<IllegalArgumentException> { ClimateNormsAsset.parse(elevenMonths) }
    }

    @Test
    fun parseRejectsDuplicateMonth() {
        val duplicated = """{"months": [""" +
            (1..12).joinToString(",") {
                """{"month": $it, "tempMinC": 0.0, "tempMaxC": 10.0, "condition": "overcast"}"""
            } +
                """, {"month": 7, "tempMinC": 1.0, "tempMaxC": 2.0, "condition": "clear"}]}"""
        assertFailsWith<IllegalArgumentException> { ClimateNormsAsset.parse(duplicated) }
    }

    @Test
    fun parseRejectsInvertedOrMissingTemperatures() {
        val inverted = """{"months": [""" +
            (1..12).joinToString(",") { month ->
                if (month == 6) {
                    """{"month": $month, "tempMinC": 30.0, "tempMaxC": 10.0, "condition": "clear"}"""
                } else {
                    """{"month": $month, "tempMinC": 0.0, "tempMaxC": 10.0, "condition": "overcast"}"""
                }
            } + "]}"
        assertFailsWith<IllegalArgumentException> { ClimateNormsAsset.parse(inverted) }

        val missing = """{"months": [""" +
            (1..12).joinToString(",") { month ->
                if (month == 6) {
                    """{"month": $month, "condition": "clear"}"""
                } else {
                    """{"month": $month, "tempMinC": 0.0, "tempMaxC": 10.0, "condition": "overcast"}"""
                }
            } + "]}"
        assertFailsWith<IllegalArgumentException> { ClimateNormsAsset.parse(missing) }
    }
}
