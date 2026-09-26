package com.damagdpixl.svita.core.weather

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class TargetTempTest {

    private fun weather(min: Double) = DayWeather(
        date = LocalDate.parse("2026-09-26"),
        tempMinC = min,
        tempMaxC = 20.0,
        condition = "clear",
    )

    @Test
    fun zeroComfortTargetsTheDailyMinimum() {
        assertEquals(5.0, targetTempC(weather(5.0), 0.0))
    }

    @Test
    fun minusOneComfortIsFifteenBelowTheMinimum() {
        assertEquals(-10.0, targetTempC(weather(5.0), -1.0))
    }

    @Test
    fun plusOneComfortIsFifteenAboveTheMinimum() {
        assertEquals(20.0, targetTempC(weather(5.0), 1.0))
    }

    @Test
    fun usesTheDailyMinimumNotTheMaximum() {
        assertEquals(2.0, targetTempC(weather(2.0), 0.0))
    }

    @Test
    fun sliderOutOfRangeIsClamped() {
        assertEquals(-10.0, targetTempC(weather(5.0), -2.5))
        assertEquals(20.0, targetTempC(weather(5.0), 3.0))
    }

    @Test
    fun negativeTemperaturesAndFractionalComfort() {
        assertEquals(-2.5, targetTempC(weather(-10.0), 0.5))
        assertEquals(-17.5, targetTempC(weather(-10.0), -0.5))
    }
}
