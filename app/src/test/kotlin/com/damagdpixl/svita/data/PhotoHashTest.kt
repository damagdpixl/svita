package com.damagdpixl.svita.data

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * pHash-lite: середнє-хеш 8×8 (aHash), гемінгова відстань і поріг ~10% бітів.
 * Хеш має бути детермінованим для заданого піксельного набору, а одне й те саме
 * зображення — читатися однаково з URI і зі збереженого файлу.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoHashTest {

    /** 8×8 split pattern: left half [left] color, right half [right]. */
    private fun splitBitmap(left: Int, right: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(PhotoHash.GRID, PhotoHash.GRID, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(PhotoHash.HASH_BITS) { i ->
            if (i % PhotoHash.GRID < PhotoHash.GRID / 2) left else right
        }
        bitmap.setPixels(pixels, 0, PhotoHash.GRID, 0, 0, PhotoHash.GRID, PhotoHash.GRID)
        return bitmap
    }

    private fun withBits(count: Int, seed: Long = 0L): Long {
        var value = seed
        repeat(count) { i -> value = value or (1L shl i) }
        return value
    }

    @Test
    fun `гемінгова відстань рахує різні біти`() {
        assertEquals(0, PhotoHash.hammingDistance(0L, 0L))
        assertEquals(0, PhotoHash.hammingDistance(123456789123456789L, 123456789123456789L))
        assertEquals(2, PhotoHash.hammingDistance(0b1010L, 0b0110L))
        assertEquals(PhotoHash.HASH_BITS, PhotoHash.hammingDistance(0L, -1L))
        assertEquals(1, PhotoHash.hammingDistance(0L, Long.MIN_VALUE))
    }

    @Test
    fun `поріг дубліката — приблизно десять відсотків бітів`() {
        assertEquals(6, PhotoHash.MAX_DISTANCE_BITS)
        val base = 0L
        assertTrue(PhotoHash.isLikelyDuplicate(base, withBits(0)))
        assertTrue(PhotoHash.hammingDistance(base, withBits(6)) <= PhotoHash.MAX_DISTANCE_BITS)
        assertFalse(PhotoHash.isLikelyDuplicate(base, withBits(7)))
        assertFalse(PhotoHash.isLikelyDuplicate(0L, -1L))
    }

    @Test
    fun `однаковий патерн дає той самий хеш а дзеркальний інверсію`() {
        val dark = Color.rgb(10, 10, 10)
        val light = Color.rgb(245, 245, 245)

        val original = splitBitmap(dark, light)
        val same = splitBitmap(dark, light)
        val inverse = splitBitmap(light, dark)

        val h1 = PhotoHash.averageHash(original)
        val h2 = PhotoHash.averageHash(same)
        val h3 = PhotoHash.averageHash(inverse)

        assertEquals(h1, h2)
        assertEquals(0, PhotoHash.hammingDistance(h1, h2))
        assertEquals(PhotoHash.HASH_BITS, PhotoHash.hammingDistance(h1, h3))
        assertNotEquals(h1, h3)
        original.recycle()
        same.recycle()
        inverse.recycle()
    }

    @Test
    fun `однотонні зображення колапсують в один хеш це задокументований допуск`() {
        val red = splitBitmap(Color.RED, Color.RED)
        val blue = splitBitmap(Color.BLUE, Color.BLUE)
        val hRed = PhotoHash.averageHash(red)
        val hBlue = PhotoHash.averageHash(blue)
        assertEquals(hRed, hBlue)
        assertTrue(PhotoHash.isLikelyDuplicate(hRed, hBlue))
        red.recycle()
        blue.recycle()
    }

    @Test
    fun `хеш з URI і з збереженого файлу збігається для одного файла`() = runBlocking {
        val bitmap = splitBitmap(Color.rgb(5, 5, 5), Color.rgb(250, 250, 250))
        val file = File(
            RuntimeEnvironment.getApplication().cacheDir,
            "hash_source_${System.nanoTime()}.jpg",
        )
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        bitmap.recycle()

        try {
            val fromUri = PhotoHash.ofPickedImage(
                RuntimeEnvironment.getApplication().contentResolver,
                Uri.fromFile(file),
            )
            val fromFile = PhotoHash.ofStoredPhoto(file.absolutePath)
            assertEquals(fromUri, fromFile)
            assertTrue("хеш не порожній", fromUri != null && fromUri != 0L)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `битий файл дає null а не виняток`() = runBlocking {
        val broken = File(
            RuntimeEnvironment.getApplication().cacheDir,
            "hash_broken_${System.nanoTime()}.jpg",
        )
        broken.writeText("це не зображення")
        try {
            assertNull(PhotoHash.ofStoredPhoto(broken.absolutePath))
            assertNull(
                PhotoHash.ofPickedImage(
                    RuntimeEnvironment.getApplication().contentResolver,
                    Uri.fromFile(broken),
                ),
            )
        } finally {
            broken.delete()
        }
    }
}
