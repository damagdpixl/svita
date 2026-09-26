package com.damagdpixl.svita.core.weather

import kotlinx.datetime.LocalDate

/** Dress-me comfort slider bounds (owner-fixed semantics). */
public const val COMFORT_MIN: Double = -1.0
public const val COMFORT_MAX: Double = 1.0

/** Owner-fixed swing: the comfort slider spans ±15 °C around the daily minimum. */
public const val COMFORT_SWING_C: Double = 15.0

/**
 * Dress-me selection semantics (owner-fixed): the engine target temperature is
 * `weather.tempMinC + comfort * 15` with comfort ∈ [−1, 1]. −1 = the coldest
 * target (engine picks the warmest outfits), +1 = the warmest target. Values
 * outside the range are clamped — the slider is the only producer, and
 * clamping keeps hostile input harmless instead of throwing.
 */
public fun targetTempC(weather: DayWeather, comfortSlider: Double): Double =
    weather.tempMinC + comfortSlider.coerceIn(COMFORT_MIN, COMFORT_MAX) * COMFORT_SWING_C

/**
 * Failure-tolerant weather entry point for dress-me. Resolution order per the
 * contract — cache → network → climate norms — with [forDate] never throwing:
 *
 *  1. last-good cached sample for the rounded-coordinate bucket + exact date;
 *  2. a network fetch whose window is sized so it can actually contain the
 *     requested date (Open-Meteo forecasts start "today"; 16 days is its max
 *     length). Past dates coerce to the minimal 1-day window and effectively
 *     always degrade to norms or cache, by design;
 *  3. the climate norms floor, which itself degrades to the documented
 *     neutral sample if the norms map is broken — the chain's last resort.
 *
 * Cache failures are never fatal: a broken get is a miss, a broken put costs
 * nothing. Cancellation is preserved (see [suspendCatching]).
 */
public class WeatherFacade(
    private val openMeteo: WeatherProvider,
    private val norms: ClimateNormsProvider,
    private val cache: WeatherCache,
    private val today: () -> LocalDate = ::systemToday,
) {

    public suspend fun forDate(lat: Double, lon: Double, date: LocalDate): DayWeather {
        val cacheLat = roundCoord(lat)
        val cacheLon = roundCoord(lon)

        // 1) cache: the last-good sample for this bucket + date.
        suspendCatching { cache.get(cacheLat, cacheLon, date) }.getOrNull()?.let { return it }

        // 2) network: fetch a window that can contain the requested date,
        //    then cache the whole forecast so neighbour days are warm.
        val windowDays = (date.toEpochDays() - today().toEpochDays() + 1L)
            .coerceIn(1L, OPEN_METEO_MAX_DAYS.toLong())
            .toInt()
        // Outer suspendCatching survives a provider that throws despite the
        // Result contract; inner getOrNull maps a reported failure to null.
        val fetched = suspendCatching { openMeteo.forecast(lat, lon, windowDays) }.getOrNull()
        val forecast: List<DayWeather>? = fetched?.getOrNull()
        if (forecast != null) {
            suspendCatching { cache.put(cacheLat, cacheLon, forecast) }
            forecast.firstOrNull { it.date == date }?.let { return it }
        }

        // 3) climate norms: the guaranteed floor, never throwing.
        return suspendCatching { norms.forDate(date) }.getOrElse {
            DayWeather(date, NEUTRAL_TEMP_MIN_C, NEUTRAL_TEMP_MAX_C, UNKNOWN_CONDITION)
        }
    }
}
