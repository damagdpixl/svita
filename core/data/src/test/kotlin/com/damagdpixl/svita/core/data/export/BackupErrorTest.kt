package com.damagdpixl.svita.core.data.export

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Robustness: garbage archives, missing or malformed manifests and newer
 * versions surface as typed [ImportError]s; a failed import — including a
 * REPLACE that fails validation — leaves the database byte-identical.
 */
class BackupErrorTest {
    private lateinit var fixture: BackupFixture

    @Before
    fun setUp() {
        fixture = BackupFixture()
    }

    @After
    fun tearDown() {
        fixture.cleanup()
    }

    private fun expectTypedError(
        bytes: ByteArray,
        mode: ImportMode,
        onError: (ImportError) -> Unit,
    ) = runBlocking {
        fixture.seedRichDataOnB()
        val before = fullSnapshot(fixture.b.db)
        val error = assertThrows<ImportError> {
            fixture.managerB.importAll(fixture.sourceOf(bytes), mode)
        }
        onError(error)
        assertEquals(before, fullSnapshot(fixture.b.db))
        Unit
    }

    @Test
    fun `сміття замість архіву - NotAnArchive, база не змінена`() {
        expectTypedError("це точно не zip-архів, клянуся".encodeToByteArray(), ImportMode.REPLACE) { error ->
            assertTrue(error is ImportError.NotAnArchive)
        }
    }

    /**
     * A truncated archive ends without a manifest: the stream reports EOF as a
     * quiet end of data, so this surfaces as [ImportError.MissingManifest] —
     * still a typed error, and the database stays untouched.
     */
    @Test
    fun `обрізаний zip - типова помилка, база не змінена`() {
        val valid = zipWithoutManifest()
        expectTypedError(valid.copyOfRange(0, valid.size / 2), ImportMode.MERGE) { error ->
            assertTrue(
                "got: $error",
                error is ImportError.NotAnArchive || error is ImportError.MissingManifest,
            )
        }
    }

    @Test
    fun `архів без маніфесту - MissingManifest`() {
        expectTypedError(zipWithoutManifest(), ImportMode.REPLACE) { error ->
            assertTrue(error is ImportError.MissingManifest)
        }
    }

    @Test
    fun `битий JSON маніфесту - MalformedManifest`() {
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry(BackupManager.MANIFEST_ENTRY))
            zip.write("{це не json".encodeToByteArray())
            zip.closeEntry()
        }
        expectTypedError(out.toByteArray(), ImportMode.REPLACE) { error ->
            assertTrue(error is ImportError.MalformedManifest)
        }
    }

    @Test
    fun `новіший format_version - UnsupportedFormatVersion, і для MERGE теж`() = runBlocking {
        fixture.seedRichData()
        fixture.managerA.exportAll(fixture.sink())
        val manifest = readManifest(fixture.backupFile)
        val photos = readPhotoEntries(fixture.backupFile)
        val newer = zipOf(manifest.copy(formatVersion = BackupManager.BACKUP_FORMAT_VERSION + 1), photos)

        val error = assertThrows<ImportError> {
            fixture.managerB.importAll(fixture.sourceOf(newer), ImportMode.REPLACE)
        }
        assertTrue(error is ImportError.UnsupportedFormatVersion)
        assertEquals(BackupManager.BACKUP_FORMAT_VERSION + 1, (error as ImportError.UnsupportedFormatVersion).found)

        val mergeError = assertThrows<ImportError> {
            fixture.managerB.importAll(fixture.sourceOf(newer), ImportMode.MERGE)
        }
        assertTrue(mergeError is ImportError.UnsupportedFormatVersion)
    }

    @Test
    fun `новіша schema_version - UnsupportedSchemaVersion`() = runBlocking {
        fixture.seedRichData()
        fixture.managerA.exportAll(fixture.sink())
        val manifest = readManifest(fixture.backupFile)
        val photos = readPhotoEntries(fixture.backupFile)
        val bytes = zipOf(manifest.copy(schemaVersion = 2), photos)

        val error = assertThrows<ImportError> {
            fixture.managerB.importAll(fixture.sourceOf(bytes), ImportMode.REPLACE)
        }
        assertTrue(error is ImportError.UnsupportedSchemaVersion)
        assertEquals(2, (error as ImportError.UnsupportedSchemaVersion).found)
    }

    @Test
    fun `REPLACE з невідомим підтипом - ReferentialIntegrity і повний відкат`() = runBlocking {
        fixture.seedRichDataOnB()
        val before = fullSnapshot(fixture.b.db)
        val crafted = ExportManifest(
            formatVersion = 1,
            schemaVersion = 1,
            exportedAt = "2025-06-02T10:00:00Z",
            appVersion = "test",
            taxonomy = TaxonomyMeta(seedVersion = 1),
            items = listOf(
                ItemRow(
                    id = 50L,
                    subtypeId = 4242L, // no such subtype in the seed
                    name = "Річ-сирота",
                    notes = null,
                    price = null,
                    purchaseDate = null,
                    seasonFlags = 15,
                    sex = null,
                    rating = null,
                    archived = false,
                    createdAt = "2025-06-02T10:00:00Z",
                    updatedAt = "2025-06-02T10:00:00Z",
                ),
            ),
        )

        val error = assertThrows<ImportError> {
            fixture.managerB.importAll(fixture.sourceOf(zipOf(crafted)), ImportMode.REPLACE)
        }

        assertTrue(error is ImportError.ReferentialIntegrity)
        assertTrue(error.message!!.contains("subtype"))
        // Full rollback: seed taxonomy, existing data, settings — all intact.
        assertEquals(before, fullSnapshot(fixture.b.db))
        assertNull(fixture.managerB.lastExportAt()) // untouched by the failed run
    }

    @Test
    fun `REPLACE з даними без зовнішньо-ключових зв'язків проходить після відхиленого`() = runBlocking {
        fixture.seedRichData()
        fixture.managerA.exportAll(fixture.sink())
        val manifest = readManifest(fixture.backupFile)
        val photos = readPhotoEntries(fixture.backupFile)
        val broken = zipOf(
            manifest.copy(items = manifest.items + manifest.items.first().copy(id = 500, subtypeId = 4242)),
            photos,
        )
        assertThrows<ImportError> { fixture.managerB.importAll(fixture.sourceOf(broken), ImportMode.REPLACE) }

        // The next, valid import succeeds — nothing was half-applied.
        val report = fixture.managerB.importAll(fixture.source(), ImportMode.REPLACE)
        assertEquals(2, report.tables.getValue("items").inserted)
        assertEquals(
            fullSnapshot(fixture.a.db).withoutReminder(),
            fullSnapshot(fixture.b.db).withoutReminder(),
        )
    }
}

/** Seeds a small non-empty wardrobe on device B (rollback witnesses). */
private suspend fun BackupFixture.seedRichDataOnB() {
    b.createItem("Річ Б1", b.subTShirt)
    b.wardrobe.createTag("тег Б")
    b.settings.putString("units", "imperial")
}
