package com.damagdpixl.svita.core.weather

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Fakes only; every dependency can be made hostile to prove the never-throws contract. */
private class StubProvider(
    private val result: (lat: Double, lon: Double, days: Int) -> Result<List<DayWeather>>,
) : WeatherProvider {
    var calls: Int = 0
        private set
    var lastDays: Int? = null
        private set
    var lastLat: Double? = null
        private set

    override suspend fun forecast(lat: Double, lon: Double, days: Int): Result<List<DayWeather>> {
        calls++
        lastDays = days
        lastLat = lat
        return result(lat, lon, days)
    }
}

private class HostileProvider : WeatherProvider {
    override suspend fun forecast(lat: Double, lon: Double, days: Int): Result<List<DayWeather>> =
        throw IllegalStateException("network down")
}

private class FaultyCache : WeatherCache {
    var failGet: Boolean = false
    var failPut: Boolean = false
    val store = HashMap<String, DayWeather>()

    override suspend fun get(lat: Double, lon: Double, date: LocalDate): DayWeather? {
        if (failGet) throw IllegalStateException("cache get failed")
        return store["${roundCoord(lat)}|${roundCoord(lon)}|$date"]
    }

    override suspend fun put(lat: Double, lon: Double, forecast: List<DayWeather>) {
        if (failPut) throw IllegalStateException("cache put failed")
        for (day in forecast) store["${roundCoord(lat)}|${roundCoord(lon)}|${day.date}"] = day
    }
}

private class ExplodingCache : WeatherCache {
    override suspend fun get(lat: Double, lon: Double, date: LocalDate): DayWeather? =
        throw IllegalStateException("cache exploded")
    override suspend fun put(lat: Double, lon: Double, forecast: List<DayWeather>) =
        throw IllegalStateException("cache exploded")
}

class WeatherFacadeTest {

    private val today: () -> LocalDate = { LocalDate.parse("2026-09-26") }

    private val fullNorms: Map<Int, MonthlyNorm> = (1..12).associateWith { month ->
        MonthlyNorm(month, tempMinC = month * 1.0, tempMaxC = month * 1.0 + 10.0, condition = "norm_$month")
    }

    private fun normsProvider(norms: Map<Int, MonthlyNorm> = fullNorms) = ClimateNormsProvider(norms, today)

    private fun day(date: String, min: Double, max: Double, condition: String = "clear") =
        DayWeather(LocalDate.parse(date), min, max, condition)

    @Test
    fun cacheHitAvoidsTheNetwork() = runTest {
        val cache = FaultyCache()
        cache.store["50.45|30.52|2026-09-26"] = day("2026-09-26", 9.9, 19.9, "cached")
        val provider = StubProvider { _, _, _ -> Result.success(emptyList()) }
        val facade = WeatherFacade(provider, normsProvider(), cache, today)

        // Coordinates a few metres off the cached bucket still hit.
        val result = facade.forDate(50.45001, 30.52001, LocalDate.parse("2026-09-26"))

        assertEquals("cached", result.condition)
        assertEquals(0, provider.calls)
    }

    @Test
    fun cacheMissesOnADifferentBucket() = runTest {
        val cache = FaultyCache()
        cache.store["50.45|30.52|2026-09-26"] = day("2026-09-26", 9.9, 19.9, "cached")
        val provider = StubProvider { _, _, _ -> Result.success(emptyList()) }
        val facade = WeatherFacade(provider, normsProvider(), cache, today)

        facade.forDate(50.46001, 30.52001, LocalDate.parse("2026-09-26"))

        assertEquals(1, provider.calls)
    }

    @Test
    fun networkSuccessIsReturnedAndCached() = runTest {
        val cache = FaultyCache()
        val provider = StubProvider { _, _, _ ->
            Result.success(
                listOf(
                    day("2026-09-26", 11.2, 21.4),
                    day("2026-09-27", 10.5, 19.8, "rain"),
                ),
            )
        }
        val facade = WeatherFacade(provider, normsProvider(), cache, today)

        val result = facade.forDate(50.45, 30.52, LocalDate.parse("2026-09-27"))

        assertEquals(10.5, result.tempMinC)
        assertEquals("rain", result.condition)
        // Whole forecast was cached, so the neighbouring day is warm too.
        assertEquals(11.2, cache.store["50.45|30.52|2026-09-26"]?.tempMinC)
        assertEquals(10.5, cache.store["50.45|30.52|2026-09-27"]?.tempMinC)
    }

    @Test
    fun secondCallWithinWindowIsServedFromCache() = runTest {
        val cache = FaultyCache()
        val provider = StubProvider { _, _, _ ->
            Result.success(listOf(day("2026-09-26", 11.2, 21.4), day("2026-09-27", 10.5, 19.8)))
        }
        val facade = WeatherFacade(provider, normsProvider(), cache, today)

        val first = facade.forDate(50.45, 30.52, LocalDate.parse("2026-09-26"))
        val second = facade.forDate(50.45, 30.52, LocalDate.parse("2026-09-27"))

        assertEquals(1, provider.calls)
        assertEquals(11.2, first.tempMinC)
        assertEquals(10.5, second.tempMinC)
    }

    @Test
    fun networkWindowIsSizedToCoverTheRequestedDate() = runTest {
        val provider = StubProvider { _, _, _ -> Result.success(emptyList()) }
        val facade = WeatherFacade(provider, normsProvider(), FaultyCache(), today)

        facade.forDate(50.0, 30.0, LocalDate.parse("2026-09-30")) // today + 4
        assertEquals(5, provider.lastDays)

        facade.forDate(50.0, 30.0, LocalDate.parse("2026-12-01")) // 66 days out -> clamped
        assertEquals(OPEN_METEO_MAX_DAYS, provider.lastDays)

        facade.forDate(50.0, 30.0, LocalDate.parse("2026-09-25")) // past -> minimal window
        assertEquals(1, provider.lastDays)
    }

    @Test
    fun networkFailureFallsBackToNorms() = runTest {
        val date = LocalDate.parse("2027-01-15")
        val facade = WeatherFacade(HostileProvider(), normsProvider(), FaultyCache(), today)

        val result = facade.forDate(50.0, 30.0, date)

        assertEquals(day("2027-01-15", 1.0, 11.0, "norm_1"), result)
    }

    @Test
    fun dateOutsideTheFetchedWindowFallsBackToNorms() = runTest {
        val provider = StubProvider { _, _, _ ->
            Result.success(listOf(day("2026-09-26", 11.2, 21.4), day("2026-09-27", 10.5, 19.8)))
        }
        val facade = WeatherFacade(provider, normsProvider(), FaultyCache(), today)

        val result = facade.forDate(50.0, 30.0, LocalDate.parse("2026-09-28"))

        assertEquals(9.0, result.tempMinC)
        assertEquals("norm_9", result.condition)
    }

    @Test
    fun brokenCacheGetIsTreatedAsAMiss() = runTest {
        val cache = FaultyCache().apply { failGet = true }
        val provider = StubProvider { _, _, _ -> Result.success(listOf(day("2026-09-26", 11.2, 21.4))) }
        val facade = WeatherFacade(provider, normsProvider(), cache, today)

        val result = facade.forDate(50.45, 30.52, LocalDate.parse("2026-09-26"))

        assertEquals(11.2, result.tempMinC)
        assertEquals(1, provider.calls)
    }

    @Test
    fun brokenCachePutStillKeepsTheNetworkResult() = runTest {
        val cache = FaultyCache().apply { failPut = true }
        val provider = StubProvider { _, _, _ -> Result.success(listOf(day("2026-09-26", 11.2, 21.4))) }
        val facade = WeatherFacade(provider, normsProvider(), cache, today)

        val result = facade.forDate(50.45, 30.52, LocalDate.parse("2026-09-26"))

        assertEquals(11.2, result.tempMinC)
    }

    @Test
    fun neverThrowsEvenWhenEverythingIsHostile() = runTest {
        // Hostile provider + empty norms map + exploding cache: the chain's last
        // resort is the documented neutral sample.
        val facade = WeatherFacade(HostileProvider(), normsProvider(emptyMap()), ExplodingCache(), today)

        val result = facade.forDate(50.0, 30.0, LocalDate.parse("2026-09-26"))

        assertTrue(result.date == LocalDate.parse("2026-09-26"))
        assertEquals(NEUTRAL_TEMP_MIN_C, result.tempMinC)
        assertEquals(NEUTRAL_TEMP_MAX_C, result.tempMaxC)
        assertEquals(UNKNOWN_CONDITION, result.condition)
    }
}
