package com.damagdpixl.svita.data

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Ручний експорт лога налагодження: фейкові джерело рядків і share-мапер
 * (справжній logcat у тестах недоступний, а статичний кеш FileProvider
 * Robolectric не скидає між тестами) — шапка з пристроєм/ОС/версією, ліміт
 * 500 рядків (новіші виграють), непорожній файл.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DebugLogExporterTest {

    private lateinit var context: Context

    /** Fake share mapper: no FileProvider static state involved. */
    private val fakeShareUri: (File) -> Uri = { file ->
        Uri.parse("content://fake.fileprovider/log/" + file.name)
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `шапка і рядки джерела потрапляють у файл`() {
        val exporter = DebugLogExporter(
            context,
            logLines = { listOf("D/First line", "D/Second line") },
            shareUriOf = fakeShareUri,
        )

        val file = exporter.export().getOrThrow()
        val text = file.readText()

        assertTrue(text.contains("Svita debug log"))
        assertTrue(text.contains("device:"))
        assertTrue(text.contains("android:"))
        assertTrue(text.contains("app: 0.1.0"))
        assertTrue(text.contains("D/First line"))
        assertTrue(text.contains("D/Second line"))
        // Share-URI веде до того самого файлу через заданий мапер.
        assertEquals(
            "content://fake.fileprovider/log/svita-debug-log.txt",
            exporter.shareUri(file).toString(),
        )
    }

    @Test
    fun `ліміт 500 рядків — новіші виграють`() {
        // 601 рядок: line 1 (найстаріший) .. line 601 (найновіший).
        val exporter = DebugLogExporter(
            context,
            logLines = { (1..DebugLogExporter.MAX_LINES + 101).map { n -> "D/line $n" } },
            shareUriOf = fakeShareUri,
        )

        val file = exporter.export().getOrThrow()
        val logLines = file.readText().lines().filter { it.startsWith("D/") }

        assertEquals(DebugLogExporter.MAX_LINES, logLines.size)
        assertEquals("D/line 601", logLines.last()) // найновіший лишився
        assertEquals("D/line 102", logLines.first()) // 101 найстаріших зрізано
    }

    @Test
    fun `порожнє джерело дає валідний файл лише з шапкою`() {
        val exporter = DebugLogExporter(
            context,
            logLines = { emptyList() },
            shareUriOf = fakeShareUri,
        )

        val file = exporter.export().getOrThrow()
        val text = file.readText()

        assertTrue(text.contains("Svita debug log"))
        assertFalse(text.contains("D/"))
        assertTrue(file.length() > 0)
    }
}
