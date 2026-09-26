package com.damagdpixl.svita.core.weather

import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Open-Meteo needs no API key; the base URL stays overridable for fakes/tests. */
public const val OPEN_METEO_BASE_URL: String = "https://api.open-meteo.com/v1"

/** Open-Meteo's `forecast_days` upper bound. */
internal const val OPEN_METEO_MAX_DAYS: Int = 16

/**
 * Real weather fetch against the Open-Meteo forecast API (no key required).
 * Every failure — bad arguments, transport errors, non-2xx status, malformed
 * or unusable payloads — comes back as [Result.failure], so the facade can
 * degrade to climate norms.
 *
 * Rows are skipped individually: an unparseable date or a missing/non-finite
 * temperature invalidates only that row. An exchange that leaves zero usable
 * rows is a failure. Arrays longer than `time` are truncated; shorter arrays
 * cap the parsed row count.
 */
public class OpenMeteoProvider(
    private val client: HttpTransport,
    private val baseUrl: String = OPEN_METEO_BASE_URL,
) : WeatherProvider {

    public override suspend fun forecast(lat: Double, lon: Double, days: Int): Result<List<DayWeather>> = suspendCatching {
        require(lat.isFinite() && lat in MIN_LAT..MAX_LAT) { "latitude out of range: $lat" }
        require(lon.isFinite() && lon in MIN_LON..MAX_LON) { "longitude out of range: $lon" }
        require(days in 1..OPEN_METEO_MAX_DAYS) { "days must be in 1..$OPEN_METEO_MAX_DAYS, got $days" }

        val url = "$baseUrl/forecast" +
            "?latitude=${roundCoord4(lat)}&longitude=${roundCoord4(lon)}" +
            "&daily=$DAILY_FIELDS&timezone=auto&forecast_days=$days"
        val response = client.get(url)
        if (response.statusCode !in HTTP_OK_MIN..HTTP_OK_MAX) {
            error("Open-Meteo responded with HTTP ${response.statusCode}")
        }
        parseDaily(response.body)
    }

    private fun parseDaily(payload: String): List<DayWeather> {
        val decoded = runCatching { payloadJson.decodeFromString<OpenMeteoResponseDto>(payload) }
            .getOrElse { failure ->
                throw IllegalArgumentException("Open-Meteo payload is not valid JSON: ${failure.message}", failure)
            }
        val daily = decoded.daily
            ?: throw IllegalArgumentException("Open-Meteo payload has no daily block")

        val rowCount = minOf(
            daily.time.size,
            daily.temperature2mMax.size,
            daily.temperature2mMin.size,
            daily.weatherCode.size,
        )
        val days = ArrayList<DayWeather>(rowCount)
        for (i in 0 until rowCount) {
            val date = runCatching { LocalDate.parse(daily.time[i]) }.getOrNull() ?: continue
            val tempMin = daily.temperature2mMin[i]?.takeIf(Double::isFinite) ?: continue
            val tempMax = daily.temperature2mMax[i]?.takeIf(Double::isFinite) ?: continue
            days.add(DayWeather(date, tempMin, tempMax, wmoCondition(daily.weatherCode[i])))
        }
        if (days.isEmpty()) {
            throw IllegalArgumentException("Open-Meteo daily block contained no usable rows")
        }
        return days
    }

    private companion object {
        const val MIN_LAT = -90.0
        const val MAX_LAT = 90.0
        const val MIN_LON = -180.0
        const val MAX_LON = 180.0
        const val HTTP_OK_MIN = 200
        const val HTTP_OK_MAX = 299
        const val DAILY_FIELDS = "temperature_2m_max,temperature_2m_min,weather_code"
        val payloadJson: Json = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
internal data class OpenMeteoResponseDto(val daily: OpenMeteoDailyDto? = null)

@Serializable
internal data class OpenMeteoDailyDto(
    val time: List<String> = emptyList(),
    @SerialName("temperature_2m_max") val temperature2mMax: List<Double?> = emptyList(),
    @SerialName("temperature_2m_min") val temperature2mMin: List<Double?> = emptyList(),
    @SerialName("weather_code") val weatherCode: List<Int?> = emptyList(),
)

/**
 * WMO weather-interpretation codes (Open-Meteo daily `weather_code`) folded to
 * the repo's coarse condition labels. Unknown/unmapped codes degrade to
 * [UNKNOWN_CONDITION].
 */
internal fun wmoCondition(code: Int?): String = when (code) {
    null -> UNKNOWN_CONDITION
    0 -> "clear"
    1, 2 -> "partly_cloudy"
    3 -> "overcast"
    45, 48 -> "fog"
    in 51..67, in 80..82 -> "rain"
    in 71..77, 85, 86 -> "snow"
    in 95..99 -> "storm"
    else -> UNKNOWN_CONDITION
}
