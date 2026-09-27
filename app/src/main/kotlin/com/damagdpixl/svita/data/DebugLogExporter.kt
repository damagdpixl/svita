package com.damagdpixl.svita.data

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Process
import androidx.core.content.FileProvider
import java.io.File

/**
 * Manual debug-log export (owner decision: crash feedback ONLY, never automatic).
 *
 * Collects the last ~500 logcat lines of THIS process (Debug level and above)
 * plus a small device/app header into a text file under the app's private
 * cache; the caller shares it through the FileProvider share sheet. NOTHING is
 * collected, buffered or sent by itself — the file appears only when the user
 * taps «Експортувати лог налагодження».
 *
 * [logLines] is the injectable source: tests pass a fake instead of running
 * the real `logcat` binary.
 */
class DebugLogExporter(
    private val context: Context,
    private val logLines: () -> List<String> = Companion::recentLogcatLines,
) {

    /** The written log file plus its share-sheet content URI (FileProvider). */
    data class Exported(val file: File, val shareUri: Uri)

    fun export(): Result<Exported> = runCatching {
        val dir = File(context.cacheDir, "debug_logs").apply { mkdirs() }
        val file = File(dir, "svita-debug-log.txt")
        val appVersion = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
        file.writeText(buildString {
            appendLine("Svita debug log")
            appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("app: $appVersion")
            appendLine()
            logLines().forEach(::appendLine)
        })
        Exported(
            file = file,
            shareUri = FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                file,
            ),
        )
    }

    companion object {
        /** Logcat lines of this process, Debug level+, newest ~[limit]. Empty
         * when the logcat binary is unavailable (some test environments). */
        fun recentLogcatLines(limit: Int = 500): List<String> = runCatching {
            val process = ProcessBuilder(
                "logcat", "-d", "-t", limit.toString(), "-v", "time",
                "--pid=" + Process.myPid(), "*:D",
            ).start()
            val lines = process.inputStream.bufferedReader().use { it.readLines() }
            process.waitFor()
            lines
        }.getOrDefault(emptyList())
    }
}
