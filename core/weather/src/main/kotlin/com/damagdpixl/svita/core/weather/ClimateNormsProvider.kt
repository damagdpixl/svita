package com.damagdpixl.svita.core.weather

import kotlinx.datetime.LocalDate

/**
 * Offline fallback provider: resolves any calendar date to its month's climate
 * normal. This is the brief's "by day-of-year" resolution — the date's month
 * selects the row, so leap years need no special casing.
 *
 * Never throws at query time: a missing month falls back to the average of the
 * provided norms, and an empty map to the documented neutral mild-spring-day
 * sample. The bundled production table comes from [ClimateNormsAsset].
 */
public class ClimateNormsProvider(
    private val norms: Map<Int, MonthlyNorm>,
    private val today: () -> LocalDate = ::systemToday,
) : WeatherProvider {

    /**
     * The guaranteed floor of the facade chain: any date (past or future)
     * always resolves to a [DayWeather].
     */
    public fun forDate(date: LocalDate): DayWeather {
        // monthNumber is deprecated in kotlinx-datetime 0.7; ordinal + 1 is
        // the sanctioned month index (JANUARY..DECEMBER = 0..11).
        val month = date.month.ordinal + 1
        val norm = norms[month] ?: averageNorm()
        return DayWeather(date, norm.tempMinC, norm.tempMaxC, norm.condition)
    }

    /**
     * [WeatherProvider] conformance: norm-based pseudo-forecast for the next
     * [days] calendar days starting from the injected [today]. The facade
     * never calls this (it uses [forDate] directly, timezone-safe); the method
     * exists so both providers are interchangeable at the seam.
     */
    public override suspend fun forecast(lat: Double, lon: Double, days: Int): Result<List<DayWeather>> = suspendCatching {
        require(days >= 0) { "days must be >= 0, got $days" }
        val start = today()
        List(days) { offset ->
            forDate(LocalDate.fromEpochDays(start.toEpochDays() + offset.toLong()))
        }
    }

    private fun averageNorm(): MonthlyNorm {
        val present = norms.values.toList()
        if (present.isEmpty()) {
            return MonthlyNorm(
                month = 0,
                tempMinC = NEUTRAL_TEMP_MIN_C,
                tempMaxC = NEUTRAL_TEMP_MAX_C,
                condition = UNKNOWN_CONDITION,
            )
        }
        return MonthlyNorm(
            month = 0,
            tempMinC = present.map { it.tempMinC }.average(),
            tempMaxC = present.map { it.tempMaxC }.average(),
            condition = UNKNOWN_CONDITION,
        )
    }
}
