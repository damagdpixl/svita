package com.damagdpixl.svita.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * pHash-lite for the bulk import: the classic DCT-free "average hash" (aHash).
 *
 * How it works: the image is squeezed onto an [GRID]×[GRID] grayscale grid,
 * the Rec.601 luma of every cell is compared with the integer mean, and each
 * comparison becomes one bit of a [HASH_BITS]-bit word. Two images whose words
 * differ in at most [MAX_DISTANCE_BITS] bits are reported as likely duplicates.
 *
 * Chosen over a real DCT pHash on purpose (brief: "honest and simple"):
 * it is ~15 lines, allocation-free after one tiny decode, and good enough for
 * the warn-only UX — the user always gets the final say («все одно додати»).
 * Known tolerance: flat (single-color) images all collapse to the same word,
 * so they warn as duplicates; that is a false positive the dialog absorbs.
 *
 * Decoding is bounds-probed first and sampled ([DECODE_TARGET_PX] long side),
 * so hashing a picked image never decodes the full-size file.
 */
object PhotoHash {

    /** Hash grid side; the word is [HASH_BITS] bits wide. */
    const val GRID: Int = 8

    /** Total bits in one hash word ([GRID]²). */
    const val HASH_BITS: Int = GRID * GRID

    /**
     * Max differing bits that still counts as "likely duplicate" — ~10% of the
     * 64-bit word (exact: 6/64 ≈ 9.4% distance, ≥ 90.6% similarity). JPEG
     * re-encodes and resizes of the same photo typically differ by 0-3 bits.
     */
    const val MAX_DISTANCE_BITS: Int = 6

    /** Long-side decode target before the 8×8 squeeze (bounds-probe + sample). */
    private const val DECODE_TARGET_PX: Int = 64

    /** Number of differing bits between two hash words. */
    fun hammingDistance(a: Long, b: Long): Int = (a xor b).countOneBits()

    /** True when two hashes are close enough to warn about a duplicate. */
    fun isLikelyDuplicate(a: Long, b: Long): Boolean =
        hammingDistance(a, b) <= MAX_DISTANCE_BITS

    /**
     * Average hash of a bitmap. The source is left untouched; the internal
     * scaled copy (when one is made) is recycled. Strict `>` against the
     * integer mean keeps the hash deterministic for a given pixel set.
     */
    fun averageHash(source: Bitmap): Long {
        val small = if (source.width == GRID && source.height == GRID) {
            source
        } else {
            Bitmap.createScaledBitmap(source, GRID, GRID, true)
        }
        val pixels = IntArray(HASH_BITS)
        small.getPixels(pixels, 0, GRID, 0, 0, GRID, GRID)
        if (small !== source) small.recycle()

        val luma = IntArray(HASH_BITS)
        var sum = 0L
        for (i in 0 until HASH_BITS) {
            val pixel = pixels[i]
            val value = (Color.red(pixel) * 299 + Color.green(pixel) * 587 + Color.blue(pixel) * 114) / 1000
            luma[i] = value
            sum += value
        }
        val mean = (sum / HASH_BITS).toInt()
        var hash = 0L
        for (i in 0 until HASH_BITS) {
            if (luma[i] > mean) hash = hash or (1L shl i)
        }
        return hash
    }

    /**
     * Average hash of a picked image (any content/file URI the resolver can
     * open); null when the source is unreadable. Runs off the main thread.
     */
    suspend fun ofPickedImage(resolver: ContentResolver, uri: Uri): Long? =
        withContext(Dispatchers.IO) {
            runCatching {
                val tiny = decodeTiny { options ->
                    resolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream, null, options)
                    }
                } ?: return@runCatching null
                val hash = averageHash(tiny)
                tiny.recycle()
                hash
            }.getOrNull()
        }

    /**
     * Average hash of an already-stored wardrobe photo file; null when the
     * file is missing or corrupt. Runs off the main thread.
     */
    suspend fun ofStoredPhoto(path: String): Long? = withContext(Dispatchers.IO) {
        runCatching {
            val tiny = decodeTiny { options -> BitmapFactory.decodeFile(path, options) }
                ?: return@runCatching null
            val hash = averageHash(tiny)
            tiny.recycle()
            hash
        }.getOrNull()
    }

    /** Bounds probe, then one power-of-two sampled decode toward [DECODE_TARGET_PX]. */
    private fun decodeTiny(decode: (BitmapFactory.Options) -> Bitmap?): Bitmap? {
        // The bounds probe never returns a bitmap (inJustDecodeBounds = true);
        // its outcome is read from the options, as in PhotoStore.importOne.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        decode(bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= DECODE_TARGET_PX) {
            sample *= 2
        }
        return decode(BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
