package com.damagdpixl.svita.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Owns the app-private photo storage (`filesDir/wardrobe`).
 *
 * Import pipeline per the P2 contract: copy the picker image into private
 * storage, downscale to at most [MAX_SIDE_PX] on the long side, re-encode as
 * JPEG at [JPEG_QUALITY]. No original file, content URI or permission is kept;
 * the database stores only the private absolute path.
 */
class PhotoStore(private val context: Context, private val dirName: String = "wardrobe") {

    val dir: File
        get() = File(context.filesDir, dirName)

    /** Imports several picker images; unreadable sources are skipped silently. */
    suspend fun import(uris: List<Uri>): List<String> = withContext(Dispatchers.IO) {
        uris.mapNotNull { importOne(it) }
    }

    /** Imports one picker image; returns the stored absolute path or null. */
    suspend fun import(uri: Uri): String? = withContext(Dispatchers.IO) { importOne(uri) }

    /** Deletes a photo file, but only if it lives inside [dir] (path-safety guard). */
    fun deleteIfOwned(path: String?) {
        if (path.isNullOrBlank()) return
        val file = File(path)
        val root = dir.absoluteFile
        val target = file.absoluteFile
        if (!target.path.startsWith(root.path + File.separator)) return
        target.delete()
    }

    private fun importOne(uri: Uri): String? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val probed = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        if (probed == null && (bounds.outWidth <= 0 || bounds.outHeight <= 0)) return null

        val sample = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(bounds) }
        val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, sample)
        } ?: return null

        val bitmap = downscaleToLimit(decoded)
        try {
            dir.mkdirs()
            val target = File(dir, "item_${UUID.randomUUID()}.jpg")
            target.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            target.absolutePath
        } finally {
            if (bitmap !== decoded) bitmap.recycle()
            decoded.recycle()
        }
    }.getOrNull()

    private fun sampleSizeFor(bounds: BitmapFactory.Options): Int {
        var sample = 1
        var longest = maxOf(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return 1
        // Largest power-of-two sample that still decodes at or above the limit;
        // the exact fit happens in [downscaleToLimit].
        while (longest / (sample * 2) >= MAX_SIDE_PX) {
            sample *= 2
            longest /= 2
        }
        return sample
    }

    private fun downscaleToLimit(bitmap: Bitmap): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= MAX_SIDE_PX) return bitmap
        val scale = MAX_SIDE_PX.toFloat() / longest
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    companion object {
        const val MAX_SIDE_PX: Int = 2048
        const val JPEG_QUALITY: Int = 85
    }
}
