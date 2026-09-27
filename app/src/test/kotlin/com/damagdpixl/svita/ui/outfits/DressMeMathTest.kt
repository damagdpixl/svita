package com.damagdpixl.svita.ui.outfits

import com.damagdpixl.svita.core.weather.ClimateNormsAsset
import com.damagdpixl.svita.core.weather.ClimateNormsProvider
import com.damagdpixl.svita.core.weather.DayWeather
import com.damagdpixl.svita.core.weather.HttpTransport
import com.damagdpixl.svita.core.weather.InMemoryWeatherCache
import com.damagdpixl.svita.core.weather.OpenMeteoProvider
import com.damagdpixl.svita.core.weather.WeatherFacade
import com.damagdpixl.svita.core.weather.targetTempC
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * Dress-me math (P2 T6): the owner-fixed target temperature
 * `weather.tempMinC + comfort * 15`, the offline norms floor behind the
 * «офлайн» badge, and the timezone-safe «today» seam.
 */
class DressMeMathTest {

    private val sample = DayWeather(LocalDate(2026, Month.SEPTEMBER, 15), 2.0, 9.0, "clear")

    @Test
    fun `target temperature uses the daily minimum plus comfort swing`() {
        assertEquals(-13.0, targetTempC(sample, -1.0), 1e-9)
        assertEquals(2.0, targetTempC(sample, 0.0), 1e-9)
        assertEquals(17.0, targetTempC(sample, 1.0), 1e-9)
    }

    @Test
    fun `hostile comfort values clamp into the documented range`() {
        assertEquals(17.0, targetTempC(sample, 5.0), 1e-9)
        assertEquals(-13.0, targetTempC(sample, -7.0), 1e-9)
    }

    @Test
    fun `comfort zero on a fractional minimum keeps the fraction`() {
        val fractional = DayWeather(LocalDate(2026, Month.SEPTEMBER, 15), 10.4, 19.8, "partly_cloudy")
        assertEquals(10.4, targetTempC(fractional, 0.0), 1e-9)
        assertEquals(25.4, targetTempC(fractional, 1.0), 1e-9)
    }

    /**
     * Weather absent: a provider that always fails degrades to the bundled
     * Kyiv climate norms — the sample the «офлайн» badge stands for. (The
     * network-only transport seam is exercised by the graph; here the failure
     * is explicit.)
     */
    @Test
    fun `weather absent falls back to climate norms and the target math holds`() = runBlocking {
        val facade = WeatherFacade(
            openMeteo = OpenMeteoProvider(AlwaysFailingTransport),
            norms = ClimateNormsProvider(ClimateNormsAsset.loadBundled()),
            cache = InMemoryWeatherCache(),
        )
        val date = LocalDate(2026, Month.SEPTEMBER, 15)
        val weather = facade.forDate(50.45, 30.52, date)
        assertEquals(date, weather.date)
        assertEquals(10.4, weather.tempMinC, 1e-9)
        assertEquals(19.8, weather.tempMaxC, 1e-9)
        assertEquals("partly_cloudy", weather.condition)

        assertEquals(10.4, targetTempC(weather, 0.0), 1e-9)
        assertEquals(-4.6, targetTempC(weather, -1.0), 1e-9)
    }

    /** Timezone-safe «today»: same instant, different zones, different dates. */
    @Test
    fun `today respects the explicit zone at a fixed instant`() {
        // 23:30 UTC on Mar 8: Kiribati (+14) is already on Mar 9 13:30,
        // Midway (-11) is still on Mar 8 12:30.
        val clock = Clock.fixed(Instant.parse("2026-03-08T23:30:00Z"), ZoneId.of("UTC"))
        assertEquals(LocalDate(2026, Month.MARCH, 8), todayIn(ZoneId.of("UTC"), clock))
        assertEquals(LocalDate(2026, Month.MARCH, 9), todayIn(ZoneId.of("Pacific/Kiritimati"), clock))
        assertEquals(LocalDate(2026, Month.MARCH, 8), todayIn(ZoneId.of("Pacific/Midway"), clock))
    }

    /** The offline stand-in: every request throws, the facade degrades to norms. */
    private object AlwaysFailingTransport : HttpTransport {
        override suspend fun get(url: String): com.damagdpixl.svita.core.weather.HttpTransportResponse {
            throw java.io.IOException("offline test transport")
        }
    }
}
