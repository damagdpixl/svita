package com.damagdpixl.svita.core.weather

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import java.net.SocketTimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Fakes only — the module never performs real I/O under test. */
private class ScriptedTransport(
    private val respond: (url: String) -> HttpTransportResponse,
) : HttpTransport {
    var calls: Int = 0
        private set
    var lastUrl: String? = null
        private set

    override suspend fun get(url: String): HttpTransportResponse {
        calls++
        lastUrl = url
        return respond(url)
    }
}

private class ThrowingTransport(private val error: Throwable) : HttpTransport {
    override suspend fun get(url: String): HttpTransportResponse = throw error
}

private fun transport(statusCode: Int, body: String) =
    ScriptedTransport { HttpTransportResponse(statusCode, body) }

class OpenMeteoProviderTest {

    private val okPayload = """
        {
          "latitude": 50.45, "longitude": 30.523, "timezone": "Europe/Kyiv",
          "daily": {
            "time": ["2026-09-26", "2026-09-27", "2026-09-28"],
            "temperature_2m_max": [21.4, 19.8, 17.2],
            "temperature_2m_min": [11.2, 10.5, null],
            "weather_code": [0, 61, 71]
          }
        }
    """.trimIndent()

    @Test
    fun parsesSuccessfulForecast() = runTest {
        val transport = transport(200, okPayload)
        val result = OpenMeteoProvider(transport).forecast(50.45, 30.523)

        assertTrue(result.isSuccess)
        val days = result.getOrThrow()
        // The third row (null min temperature) is skipped individually.
        assertEquals(2, days.size)
        assertEquals(LocalDate.parse("2026-09-26"), days[0].date)
        assertEquals(11.2, days[0].tempMinC)
        assertEquals(21.4, days[0].tempMaxC)
        assertEquals("clear", days[0].condition)
        assertEquals("rain", days[1].condition)
    }

    @Test
    fun buildsKeylessRequestUrl() = runTest {
        val transport = transport(200, okPayload)
        OpenMeteoProvider(transport).forecast(50.450012, 30.523987, days = 16)

        val url = transport.lastUrl.orEmpty()
        assertTrue(url.startsWith("$OPEN_METEO_BASE_URL/forecast?"), url)
        assertTrue(url.contains("latitude=50.45"), url)
        assertTrue(url.contains("longitude=30.524"), url)
        assertTrue(url.contains("daily=temperature_2m_max,temperature_2m_min,weather_code"), url)
        assertTrue(url.contains("timezone=auto"), url)
        assertTrue(url.contains("forecast_days=16"), url)
        assertTrue(!url.contains("key=") && !url.contains("apikey"), "no key material in URL: $url")
    }

    @Test
    fun httpErrorStatusIsAFailure() = runTest {
        val result404 = OpenMeteoProvider(transport(404, "not found")).forecast(50.0, 30.0)
        val result500 = OpenMeteoProvider(transport(500, "boom")).forecast(50.0, 30.0)
        assertTrue(result404.isFailure)
        assertTrue(result500.isFailure)
    }

    @Test
    fun transportTimeoutIsAFailure() = runTest {
        val provider = OpenMeteoProvider(ThrowingTransport(SocketTimeoutException("read timed out")))
        val result = provider.forecast(50.0, 30.0)
        assertTrue(result.isFailure)
    }

    @Test
    fun malformedPayloadIsAFailure() = runTest {
        val html = OpenMeteoProvider(transport(200, "<html>gateway error</html>")).forecast(50.0, 30.0)
        val missingDaily = OpenMeteoProvider(transport(200, """{"latitude": 50.0}""")).forecast(50.0, 30.0)
        val emptyDaily = OpenMeteoProvider(
            transport(200, """{"daily": {"time": [], "temperature_2m_max": [], "temperature_2m_min": [], "weather_code": []}}"""),
        ).forecast(50.0, 30.0)
        assertTrue(html.isFailure)
        assertTrue(missingDaily.isFailure)
        assertTrue(emptyDaily.isFailure)
    }

    @Test
    fun invalidArgumentsAreFailures() = runTest {
        val transport = transport(200, okPayload)
        val provider = OpenMeteoProvider(transport)
        assertTrue(provider.forecast(91.0, 30.0).isFailure)
        assertTrue(provider.forecast(50.0, -181.0).isFailure)
        assertTrue(provider.forecast(Double.NaN, 30.0).isFailure)
        assertTrue(provider.forecast(50.0, 30.0, days = 0).isFailure)
        assertTrue(provider.forecast(50.0, 30.0, days = 17).isFailure)
        // Rejected arguments must never reach the transport.
        assertEquals(0, transport.calls)
    }

    @Test
    fun unknownWeatherCodeDegradesToUnknownCondition() = runTest {
        val payload = """
            {"daily": {"time": ["2026-09-26"], "temperature_2m_max": [20.0],
             "temperature_2m_min": [10.0], "weather_code": [1234]}}
        """.trimIndent()
        val days = OpenMeteoProvider(transport(200, payload)).forecast(50.0, 30.0).getOrThrow()
        assertEquals(UNKNOWN_CONDITION, days.single().condition)
    }

    @Test
    fun wmoCodesMapToCoarseCategories() {
        assertEquals("clear", wmoCondition(0))
        assertEquals("partly_cloudy", wmoCondition(2))
        assertEquals("overcast", wmoCondition(3))
        assertEquals("fog", wmoCondition(45))
        assertEquals("rain", wmoCondition(63))
        assertEquals("rain", wmoCondition(81))
        assertEquals("snow", wmoCondition(73))
        assertEquals("snow", wmoCondition(86))
        assertEquals("storm", wmoCondition(95))
        assertEquals(UNKNOWN_CONDITION, wmoCondition(null))
        assertEquals(UNKNOWN_CONDITION, wmoCondition(42))
    }
}
