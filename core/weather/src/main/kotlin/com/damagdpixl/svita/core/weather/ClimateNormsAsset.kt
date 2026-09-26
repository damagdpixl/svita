package com.damagdpixl.svita.core.weather

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Loader for the bundled climate-norms JSON asset.
 *
 * Asset provenance: monthly temperature normals for Kyiv (temperate
 * continental climate), derived and rounded from public WMO climate normals
 * (1991-2020 reference period); the per-month `condition` labels are a coarse
 * repo-owned approximation, not an official statistic. The table is the
 * offline fallback for dress-me when neither cache nor network is available.
 *
 * The parse is strict on purpose: a broken asset must fail loudly at load
 * time (startup), never at dress-me time.
 */
public object ClimateNormsAsset {

    public const val BUNDLED_RESOURCE_PATH: String =
        "com/damagdpixl/svita/core/weather/climate_norms.json"

    private val normsJson: Json = Json { ignoreUnknownKeys = true }

    /** Strict parse: exactly months 1..12, each once, finite values, min <= max. */
    public fun parse(raw: String): Map<Int, MonthlyNorm> {
        val file = runCatching { normsJson.decodeFromString<ClimateNormsFileDto>(raw) }
            .getOrElse { failure ->
                throw IllegalArgumentException("climate norms asset is not valid JSON: ${failure.message}", failure)
            }
        val byMonth = file.months.associateBy { it.month }
        require(file.months.size == 12 && byMonth.size == 12 && byMonth.keys == (1..12).toSet()) {
            "climate norms asset must define exactly months 1..12 once each, found ${byMonth.keys.sorted()}"
        }
        val parsed = HashMap<Int, MonthlyNorm>(12)
        for ((month, row) in byMonth) {
            require(row.tempMinC.isFinite() && row.tempMaxC.isFinite() && row.tempMinC <= row.tempMaxC) {
                "climate norms row for month $month has invalid temperatures"
            }
            parsed[month] = MonthlyNorm(month, row.tempMinC, row.tempMaxC, row.condition)
        }
        return parsed
    }

    /** Loads the packaged resource; also the packaging guard used by the tests. */
    public fun loadBundled(
        classLoader: ClassLoader = ClimateNormsAsset::class.java.classLoader,
    ): Map<Int, MonthlyNorm> {
        val stream = checkNotNull(classLoader.getResourceAsStream(BUNDLED_RESOURCE_PATH)) {
            "bundled climate norms resource is missing: $BUNDLED_RESOURCE_PATH"
        }
        return stream.use { parse(it.readBytes().decodeToString()) }
    }
}

@Serializable
internal data class ClimateNormsFileDto(
    val source: String? = null,
    val months: List<MonthlyNormDto> = emptyList(),
)

@Serializable
internal data class MonthlyNormDto(
    val month: Int,
    // Defaults make an absent field fail the finite-value validation below
    // instead of crashing the decoder with a less actionable error.
    val tempMinC: Double = Double.NaN,
    val tempMaxC: Double = Double.NaN,
    val condition: String = UNKNOWN_CONDITION,
)
