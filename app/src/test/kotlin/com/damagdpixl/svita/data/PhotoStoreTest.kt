package com.damagdpixl.svita.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * Фото-сховище: копія в приватну теку, даунскейл до ≤2048px по довгій стороні,
 * JPEG 85; видалення лише власних файлів (захист від довільних шляхів).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoStoreTest {

    private fun newStore(): PhotoStore =
        PhotoStore(RuntimeEnvironment.getApplication(), dirName = "wardrobe_test")

    private fun writeTempJpeg(width: Int, height: Int): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xFF336699.toInt())
        val target = File(
            RuntimeEnvironment.getApplication().cacheDir,
            "source_${width}x${height}_${System.nanoTime()}.jpg",
        )
        target.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        bitmap.recycle()
        return target
    }

    @Test
    fun `імпорт копіює файл у приватну теку зі зменшенням до 2048`() = runBlocking {
        val store = newStore()
        val source = writeTempJpeg(2600, 1000)
        try {
            val stored = store.import(Uri.fromFile(source))
            assertNotNull(stored)
            val path = stored!!
            assertTrue("шлях у приватній теці", path.startsWith(store.dir.absolutePath))
            assertTrue("розширення jpg", path.endsWith(".jpg"))
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            assertTrue("довга сторона ≤ 2048 (маємо $longest)", longest <= 2048)
            assertEquals("ширина зменшена до ліміту", 2048, bounds.outWidth)
            assertTrue(
                "пропорції збережено (маємо ${bounds.outHeight})",
                bounds.outHeight in 780..795,
            )
        } finally {
            source.delete()
        }
    }

    @Test
    fun `маленьке фото не масштабується і читається назад`() = runBlocking {
        val store = newStore()
        val source = writeTempJpeg(400, 300)
        val stored = store.import(Uri.fromFile(source))
        assertNotNull(stored)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(stored, bounds)
        assertEquals(400, bounds.outWidth)
        assertEquals(300, bounds.outHeight)
    }

    @Test
    fun `битий файл дає null а не виняток`() = runBlocking {
        val store = newStore()
        val broken = File(
            RuntimeEnvironment.getApplication().cacheDir,
            "broken_${System.nanoTime()}.jpg",
        )
        broken.writeText("це не зображення")
        val stored = store.import(Uri.fromFile(broken))
        assertNull(stored)
    }

    @Test
    fun `видалення дозволене лише для власних файлів`() = runBlocking {
        val store = newStore()
        val source = writeTempJpeg(200, 200)
        val owned = store.import(Uri.fromFile(source))
        assertNotNull(owned)

        val outside = File(
            RuntimeEnvironment.getApplication().cacheDir,
            "outside_${System.nanoTime()}.txt",
        )
        outside.writeText("не чіпати")

        store.deleteIfOwned(outside.absolutePath)
        assertTrue("чужий файл живий", outside.exists())

        store.deleteIfOwned(owned)
        assertFalse("власний файл видалено", File(owned).exists())

        outside.delete()
        Unit
    }
}
