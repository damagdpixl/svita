package com.damagdpixl.svita.core.data.export

import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.Writer

/**
 * Byte-oriented write handle for one backup artifact (the zip archive of
 * [BackupManager.exportAll]). [BackupManager] takes ownership: it closes the
 * sink when the export finishes, successfully or not. Implementations must
 * tolerate `close()` being called twice (once for the stream handed out via
 * [outputStream], once for the sink itself).
 */
public interface FileSink : Closeable {
    /** The stream to write the artifact to; closed again through [close]. */
    public fun outputStream(): OutputStream

    override fun close()
}

/**
 * Byte-oriented read handle for one backup artifact (the input of
 * [BackupManager.importAll]). [BackupManager] takes ownership and closes the
 * source when the import finishes, successfully or not.
 */
public interface FileSource : Closeable {
    /** The stream to read the artifact from; closed again through [close]. */
    public fun inputStream(): InputStream

    override fun close()
}

/**
 * Character-oriented write handle for the CSV export
 * ([BackupManager.exportItemsCsv]). Implementations must encode in UTF-8 —
 * wardrobe names, notes and attribute values are user text in any script.
 * [BackupManager] takes ownership and closes the sink when done.
 */
public interface TextSink : Closeable {
    /** The writer to emit the CSV to; closed again through [close]. */
    public fun writer(): Writer

    override fun close()
}

/**
 * Access to the app's photo files. `photos.path` values stored in the database
 * are opaque keys for the export/import; only the app knows where the bytes
 * actually live, so [BackupManager] routes every photo read and write through
 * this interface and never touches the filesystem itself.
 */
public interface FileStore {
    /**
     * Opens the photo stored under the given `photos.path` value for reading,
     * or `null` when the file is missing on disk (the backup then contains the
     * photo row but no binary — restore keeps the row as-is).
     */
    public fun openPhoto(path: String): FileSource?

    /** Opens a destination the photo bytes are (re)stored under. */
    public fun sinkPhoto(path: String): FileSink
}

/**
 * [FileStore] rooted at one directory, for the JVM tests and as the reference
 * wiring for the Android app (root = a private files subdirectory).
 *
 * Photo paths are resolved relative to [root]; a leading `/` is ignored and a
 * path that would escape the root is rejected, so a crafted database or backup
 * cannot make an import write outside the store.
 */
public class DirectoryFileStore(private val root: File) : FileStore {
    init {
        if (!root.exists() && !root.mkdirs()) {
            error("cannot create file store root: $root")
        }
    }

    private fun resolve(path: String): File {
        val clean = path.trim().trimStart('/')
        require(clean.isNotEmpty()) { "empty photo path" }
        val file = File(root, clean).canonicalFile
        check(file.path.startsWith(root.canonicalPath + File.separator)) {
            "photo path escapes the file store root: $path"
        }
        return file
    }

    override fun openPhoto(path: String): FileSource? {
        val file = resolve(path)
        if (!file.isFile) return null
        val stream = FileInputStream(file)
        return object : FileSource {
            override fun inputStream(): InputStream = stream
            override fun close() = stream.close()
        }
    }

    override fun sinkPhoto(path: String): FileSink {
        val file = resolve(path)
        file.parentFile?.mkdirs()
        val stream = FileOutputStream(file)
        return object : FileSink {
            override fun outputStream(): OutputStream = stream
            override fun close() = stream.close()
        }
    }
}
