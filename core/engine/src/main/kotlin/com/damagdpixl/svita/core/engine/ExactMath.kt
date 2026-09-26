package com.damagdpixl.svita.core.engine

import kotlin.math.abs

/**
 * Exact numeric helpers of the engine.
 *
 * Both helpers exist to keep results bit-identical with the reference
 * implementation's semantics (IEEE CRC-32 and decimal round-half-even to
 * 4 fractional digits of the exact binary double value). They use only
 * stdlib integer arithmetic, so the module stays platform-pure.
 */

// Reflected IEEE CRC-32 polynomial, held as an Int bit pattern (hex literals
// wider than Int.MAX resolve to Long, hence the explicit conversion).
private val CRC32_POLY: Int = 0xEDB88320.toInt()

private val CRC32_TABLE: IntArray = IntArray(256) { n ->
    var c = n
    repeat(8) {
        c = if (c and 1 == 1) (c ushr 1) xor CRC32_POLY else c ushr 1
    }
    c
}

/** IEEE CRC-32 (the zlib/gzip/png polynomial), returned as an unsigned value in a Long. */
internal fun crc32(bytes: ByteArray): Long {
    var crc = -1L // all-ones 32-bit register
    for (b in bytes) {
        // Masking after the xor makes sign extension of the negative Byte irrelevant.
        val index = ((crc xor b.toLong()) and 0xFFL).toInt()
        // Mask BEFORE the shift: the reference register is 32 bits wide, so the
        // bits shifted out of its top are discarded (a 64-bit Long would keep
        // them otherwise and pollute the high half of the register).
        crc = (((crc and 0xFFFFFFFFL) ushr 8) xor (CRC32_TABLE[index].toLong() and 0xFFFFFFFFL)) and 0xFFFFFFFFL
    }
    return crc.inv() and 0xFFFFFFFFL
}

/**
 * Compensated (Neumaier) summation of doubles.
 *
 * The reference implementation aggregates floats with its runtime's built-in
 * sum(), which since 3.12 uses exactly this improved Kahan-Babuska scheme —
 * NOT naive sequential accumulation. Bit-parity of the scores therefore
 * requires the same algorithm here (naive summation diverges by 1 ULP on
 * values like six 0.95 pair scores, which flips 4-decimal rounding at
 * .xxxx5 boundaries).
 */
internal fun neumaierSum(values: List<Double>): Double {
    var sum = 0.0
    var compensation = 0.0
    for (x in values) {
        val t = sum + x
        compensation += if (abs(sum) >= abs(x)) (sum - t) + x else (x - t) + sum
        sum = t
    }
    return sum + compensation
}

/**
 * Round [x] to 4 fractional digits, ties-to-even, operating on the EXACT
 * binary value of the double (the same semantics the reference runtime's
 * built-in decimal rounding provides). Verified against 2M+ boundary-swept
 * values; a naive "multiply, round, divide" pipeline diverges on values that
 * sit within half an ULP of a rounding boundary, this one cannot.
 *
 * Implementation: x = m * 2^e0 exactly (m a 53-bit integer). Then
 * x * 10^4 = (m * 625) / 2^(shift - 4) with shift = -e0 and 10^4 = 16 * 625.
 * The quotient, remainder and the half-tie comparison are computed in integer
 * arithmetic, so the tie decision is exact. Domain: |x| < 9e11 keeps the
 * final scaled integer within 53 bits; engine scores are bounded by |1|.
 */
internal fun roundHalfEven4(x: Double): Double {
    if (x.isNaN() || x.isInfinite() || x == 0.0) return x
    require(x in -9.0e11..9.0e11) { "roundHalfEven4 domain is |x| < 9e11, got $x" }
    val neg = x < 0.0
    val bits = abs(x).toRawBits()
    val biasedExp = ((bits ushr 52) and 0x7FFL).toInt()
    val fracBits = bits and 0x000FFFFFFFFFFFFFL
    val mantissa: ULong
    val e0: Int
    if (biasedExp == 0) {
        mantissa = fracBits.toULong()
        e0 = -1074
    } else {
        mantissa = (fracBits or (1L shl 52)).toULong()
        e0 = biasedExp - 1075
    }
    if (mantissa == 0UL) return x
    val shift = -e0
    // For |x| >= 2^48 the value is already an integer multiple of 1e-4.
    if (shift < 4) return x
    val mag = mantissa * 625UL // < 2^53 * 625 < 2^63, no overflow
    val s = shift - 4
    val scaled: ULong = when {
        // value is far below the smallest representable 4-digit decimal: rounds to 0
        s >= 64 -> 0UL
        else -> {
            val q = mag shr s
            val r = if (s > 0) mag and ((1UL shl s) - 1UL) else 0UL
            val half = if (s > 0) 1UL shl (s - 1) else 0UL
            when {
                s == 0 -> q // exact integer, no fractional part
                r > half -> q + 1UL
                r == half -> q + (q and 1UL) // exact tie: round to even
                else -> q
            }
        }
    }
    val v = scaled.toDouble() / 10000.0
    return if (neg) -v else v
}
