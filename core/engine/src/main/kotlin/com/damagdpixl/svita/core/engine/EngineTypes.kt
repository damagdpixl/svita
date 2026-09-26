package com.damagdpixl.svita.core.engine

import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Public data types of the dress-me outfit engine (v1).
 *
 * The engine is pure Kotlin with zero Android or module dependencies: the app
 * wires the color palette and the subtype-to-thermal-class mapping in through
 * the [OutfitEngine] constructor, so the algorithm stays independently testable.
 */

/** One garment as the engine sees it: a subtype tag plus color/style tags. */
data class EngineGarment(
    val id: Long,
    val subtype: String,
    val colors: List<String>,
    val styles: List<String>,
)

/** One produced outfit: canonical garment order, common style tags, score. */
data class EngineLook(
    val id: Long,
    val garmentIds: List<Long>,
    val styles: List<String>,
    val score: Double,
)

/** Response envelope: up to TOP_N looks, possibly empty. */
data class LooksResult(val looks: List<EngineLook>)

/**
 * A palette entry: sRGB hex plus its precomputed OKLCH coordinates.
 *
 * [l] lightness, [c] chroma, [h] hue in degrees. The color-harmony math uses
 * the stored chroma/hue values; they must come from the same precomputation
 * as the rest of the product (see [ColorEntry.fromHex] for the formula).
 */
data class ColorEntry(
    val hex: String,
    val l: Double,
    val c: Double,
    val h: Double,
) {
    companion object {
        // sRGB linearization threshold and matrices of the standard OKLab formula.
        private const val LINEAR_THRESHOLD = 0.04045
        private val M1 = arrayOf(
            doubleArrayOf(0.4122214708, 0.5363325363, 0.0514459929),
            doubleArrayOf(0.2119034982, 0.6806995451, 0.1073969566),
            doubleArrayOf(0.0883024619, 0.2817188376, 0.6299787005),
        )
        private val M2 = arrayOf(
            doubleArrayOf(0.2104542553, 0.7936177850, -0.0040720468),
            doubleArrayOf(1.9779984951, -2.4285922050, 0.4505937099),
            doubleArrayOf(0.0259040371, 0.7827717662, -0.8086757660),
        )

        private fun srgbToLinear(c: Double): Double =
            if (c <= LINEAR_THRESHOLD) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

        /**
         * '#RRGGBB' -> OKLCH entry with the same operation order as the
         * reference pipeline: linearize, XYZ (M1), cube root, Lab (M2),
         * chroma = sqrt(a^2+b^2), hue = atan2(b, a) mapped to [0, 360).
         */
        fun fromHex(hexcode: String): ColorEntry {
            val hx = hexcode.removePrefix("#")
            val r = srgbToLinear(hx.substring(0, 2).toInt(16) / 255.0)
            val g = srgbToLinear(hx.substring(2, 4).toInt(16) / 255.0)
            val b = srgbToLinear(hx.substring(4, 6).toInt(16) / 255.0)
            val lms = DoubleArray(3) { row -> M1[row][0] * r + M1[row][1] * g + M1[row][2] * b }
            val lmsRoot = DoubleArray(3) { row -> lms[row].pow(1.0 / 3.0) }
            val lab = DoubleArray(3) { row -> M2[row][0] * lmsRoot[0] + M2[row][1] * lmsRoot[1] + M2[row][2] * lmsRoot[2] }
            val chroma = sqrt(lab[1] * lab[1] + lab[2] * lab[2])
            var hue = Math.toDegrees(atan2(lab[2], lab[1]))
            hue %= 360.0
            return ColorEntry(hexcode, lab[0], chroma, hue)
        }
    }
}

/**
 * Functional data table: garment subtype -> thermal class (C1..C8, DA, WA,
 * DB, WB or the neutral fallback). Unknown subtypes resolve to "N", which has
 * the widest temperature window and zero insulation weight.
 *
 * The role of a garment and its insulation weight are derived from the class
 * table inside the engine; only this mapping is per-installation data, so the
 * app may load it from the taxonomy seed or take [standard].
 */
class ThermalMapping(private val classOfSubtype: Map<String, String>) {

    /** All subtypes explicitly covered by this mapping. */
    val subtypes: Set<String> get() = classOfSubtype.keys

    /** Thermal class of a subtype; unknown subtypes fall back to [NEUTRAL_CLASS]. */
    fun thermalClass(subtype: String): String = classOfSubtype[subtype] ?: NEUTRAL_CLASS

    companion object {
        const val NEUTRAL_CLASS: String = "N"

        /**
         * The canonical mapping shipped with the product: female 118 + male 89
         * taxonomy subtypes plus the shared set they agree on. Functional data,
         * mirrored from the taxonomy seed; kept here so the engine and its
         * parity tests are self-contained without a database dependency.
         */
        fun standard(): ThermalMapping = ThermalMapping(STANDARD_SUBTYPE_CLASS)
    }
}
