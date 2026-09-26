package com.damagdpixl.svita.core.weather

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClimateNormsProviderTest {

    private val fullNorms: Map<Int, MonthlyNorm> = (1..12).associateWith { month ->
        MonthlyNorm(month, tempMinC = month * 1.0, tempMaxC = month * 1.0 + 10.0, condition = "norm_$month")
    }

    @Test
    fun dateResolvesThroughItsMonth() {
        val provider = ClimateNormsProvider(fullNorms)
        val jan = provider.forDate(LocalDate.parse("2026-01-15"))
        assertEquals(1.0, jan.tempMinC)
        assertEquals(11.0, jan.tempMaxC)
        assertEquals("norm_1", jan.condition)
        // Leap-year Feb 29 resolves through the month too.
        val feb = provider.forDate(LocalDate.parse("2024-02-29"))
        assertEquals("norm_2", feb.condition)
        val jul = provider.forDate(LocalDate.parse("2026-07-04"))
        assertEquals(7.0, jul.tempMinC)
    }

    @Test
    fun missingMonthFallsBackToAverageOfProvidedNorms() {
        val partial = mapOf(
            1 to MonthlyNorm(1, 0.0, 10.0, "overcast"),
            2 to MonthlyNorm(2, 4.0, 14.0, "overcast"),
        )
        val march = ClimateNormsProvider(partial).forDate(LocalDate.parse("2026-03-10"))
        assertEquals(2.0, march.tempMinC)
        assertEquals(12.0, march.tempMaxC)
        assertEquals(UNKNOWN_CONDITION, march.condition)
    }

    @Test
    fun emptyMapNeverThrowsAndDegradesToNeutral() {
        val provider = ClimateNormsProvider(emptyMap())
        val day = provider.forDate(LocalDate.parse("2026-07-04"))
        assertEquals(NEUTRAL_TEMP_MIN_C, day.tempMinC)
        assertEquals(NEUTRAL_TEMP_MAX_C, day.tempMaxC)
        assertEquals(UNKNOWN_CONDITION, day.condition)
    }

    @Test
    fun forecastGeneratesConsecutiveDaysFromInjectedToday() = runTest {
        val provider = ClimateNormsProvider(
            fullNorms,
            today = { LocalDate.parse("2026-09-26") },
        )
        val result = provider.forecast(50.0, 30.0, days = 3)
        assertTrue(result.isSuccess)
        assertEquals(
            listOf("2026-09-26", "2026-09-27", "2026-09-28"),
            result.getOrThrow().map { it.date.toString() },
        )
    }

    @Test
    fun forecastWithZeroDaysIsEmptySuccess() = runTest {
        val result = ClimateNormsProvider(fullNorms).forecast(50.0, 30.0, days = 0)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }
}
