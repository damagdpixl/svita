package com.damagdpixl.svita.ui.wardrobe

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Розмірно-обізнаний декод фото для показу: довга сторона декодованої карти
 * не перевищує подвійну ціль (степінь двійки семплінгу), біті файли дають null.
 * Без цього кожна картка декодувала повний 2048px файл на кожну композицію.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StoredPhotoDecodeTest {

    private fun writeJpeg(width: Int, height: Int): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xFF336699.toInt())
        val target = File(
            RuntimeEnvironment.getApplication().cacheDir,
            "decode_${width}x${height}_${System.nanoTime()}.jpg",
        )
        target.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        bitmap.recycle()
        return target
    }

    @Test
    fun `довга сторона декодованої карти в межах подвійної цілі`() = runBlocking {
        val source = writeJpeg(2048, 2048)
        try {
            // Сітка (512): семпл до 512 — не повні 2048.
            val grid = decodeStoredPhoto(source.absolutePath, PHOTO_DECODE_GRID)
            assertNotNull(grid)
            assertEquals(512, maxOf(grid!!.width, grid.height))

            // Мініатюра (256).
            val thumb = decodeStoredPhoto(source.absolutePath, PHOTO_DECODE_THUMB)
            assertNotNull(thumb)
            assertEquals(256, maxOf(thumb!!.width, thumb.height))

            // Пейджер (1280): джерело 2048 лишається при sample=1, ліміт x2 тримається.
            val pager = decodeStoredPhoto(source.absolutePath, PHOTO_DECODE_LARGE)
            assertNotNull(pager)
            val longSide = maxOf(pager!!.width, pager.height)
            assertTrue("маємо $longSide ≤ ${PHOTO_DECODE_LARGE * 2}", longSide <= PHOTO_DECODE_LARGE * 2)
        } finally {
            source.delete()
        }
    }

    @Test
    fun `битий файл дає null а не виняток`() = runBlocking {
        val broken = File(
            RuntimeEnvironment.getApplication().cacheDir,
            "broken_decode_${System.nanoTime()}.jpg",
        )
        broken.writeText("це не зображення")
        assertNull(decodeStoredPhoto(broken.absolutePath, PHOTO_DECODE_GRID))
        assertNull(decodeStoredPhoto(null, PHOTO_DECODE_GRID))
        broken.delete()
        Unit
    }
}
