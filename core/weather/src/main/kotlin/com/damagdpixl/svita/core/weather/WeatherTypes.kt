package com.damagdpixl.svita.core.weather

import kotlinx.coroutines.CancellationException
import kotlinx.datetime.LocalDate
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.round

/**
 * Daily weather sample the dress-me flow consumes. Temperatures are Celsius;
 * [condition] is one of the coarse repo-owned category labels produced by
 * [wmoCondition] (`clear`, `partly_cloudy`, `overcast`, `fog`, `rain`, `snow`,
 * `storm`, `unknown`) so callers never see provider-specific codes.
 */
public data class DayWeather(
    public val date: LocalDate,
    public val tempMinC: Double,
    public val tempMaxC: Double,
    public val condition: String,
)

/**
 * One monthly climate normal: the typical daily min/max temperatures for a
 * calendar month plus a coarse [condition] label. Twelve rows (months 1..12)
 * form the offline fallback table (see [ClimateNormsAsset] for provenance).
 */
public data class MonthlyNorm(
    public val month: Int,
    public val tempMinC: Double,
    public val tempMaxC: Double,
    public val condition: String,
)

/** Label for conditions the WMO mapping (or the norms table) cannot classify. */
public const val UNKNOWN_CONDITION: String = "unknown"

/**
 * Documented last-resort sample used only when even the climate-norms floor is
 * unavailable: a mild spring day — the least-wrong guess for clothing.
 */
public const val NEUTRAL_TEMP_MIN_C: Double = 10.0
public const val NEUTRAL_TEMP_MAX_C: Double = 18.0

/**
 * A weather source. [forecast] reports every failure as [Result.failure]
 * instead of throwing, so the facade can degrade uniformly. The default
 * window is 7 days.
 */
public interface WeatherProvider {
    public suspend fun forecast(lat: Double, lon: Double, days: Int = 7): Result<List<DayWeather>>
}

/**
 * Minimal injectable HTTP seam — the only network-touching code in this module
 * is an implementation of this interface; unit tests always use fakes.
 * Implementations throw on transport-level failures (DNS, timeout, reset) and
 * return [HttpTransportResponse] for any completed HTTP exchange, so status
 * codes stay inspectable.
 */
public interface HttpTransport {
    public suspend fun get(url: String): HttpTransportResponse
}

public data class HttpTransportResponse(
    public val statusCode: Int,
    public val body: String,
)

/**
 * Last-good forecast store, keyed by lat/lon rounded to 2 decimals (~1 km
 * bucket) plus the exact date. Interface only here by design: the DB-backed
 * implementation belongs to `:core:data`, which owns SQLDelight; until it
 * lands, [InMemoryWeatherCache] is the reference implementation — process
 * death simply means a cold start on climate norms, which is acceptable for
 * an offline-first, failure-tolerant flow.
 */
public interface WeatherCache {
    public suspend fun get(lat: Double, lon: Double, date: LocalDate): DayWeather?
    public suspend fun put(lat: Double, lon: Double, forecast: List<DayWeather>)
}

/** Thread-safe in-process [WeatherCache] with defensive coordinate rounding. */
public class InMemoryWeatherCache : WeatherCache {
    private val store = ConcurrentHashMap<String, DayWeather>()

    override suspend fun get(lat: Double, lon: Double, date: LocalDate): DayWeather? =
        store[cacheKey(lat, lon, date)]

    override suspend fun put(lat: Double, lon: Double, forecast: List<DayWeather>) {
        for (day in forecast) {
            store[cacheKey(lat, lon, day.date)] = day
        }
    }

    private fun cacheKey(lat: Double, lon: Double, date: LocalDate): String =
        "${roundCoord(lat)}|${roundCoord(lon)}|$date"
}

/** Coordinates are bucketed to 2 decimals (~1.1 km) for cache keys. */
internal fun roundCoord(value: Double): Double = round(value * 100) / 100

/** Coordinates are sent to the API with 4 decimals (~11 m), enough for weather. */
internal fun roundCoord4(value: Double): Double = round(value * 10_000) / 10_000

/** System-today resolution in the device timezone (injectable in tests).
 *  java.time is used because kotlinx-datetime 0.7 moved Clock/Instant to
 *  kotlin.time behind an opt-in; this is the only such spot in the module and
 *  the natural expect/actual seam if the module ever goes multiplatform. */
internal fun systemToday(): LocalDate {
    val now = java.time.LocalDate.now()
    return LocalDate(now.year, now.monthValue, now.dayOfMonth)
}

/**
 * [kotlin.runCatching] equivalent that preserves cancellation: even inside the
 * "never throws" facade, a cancelled coroutine must stay cancelled instead of
 * being converted into a Result.
 */
internal inline fun <T> suspendCatching(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Throwable) {
        Result.failure(failure)
    }
