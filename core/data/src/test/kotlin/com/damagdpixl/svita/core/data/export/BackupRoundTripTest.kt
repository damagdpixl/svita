package com.damagdpixl.svita.core.data.export

import java.util.zip.ZipFile
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The exit-gate round trip: rich seed data -> export -> wipe -> REPLACE import
 * into a fresh database -> deep equality of full domain snapshots, plus the
 * archive structure and the backup-reminder clock contract.
 */
class BackupRoundTripTest {
    private lateinit var fixture: BackupFixture

    @Before
    fun setUp() {
        fixture = BackupFixture()
    }

    @After
    fun tearDown() {
        fixture.cleanup()
    }

    @Test
    fun `експорт - REPLACE імпорт - глибока рівність знімків`() = runBlocking {
        val seed = fixture.seedRichData()

        // Wipe check: the target device already holds junk that must vanish
        // (its id is 1, so without the wipe the restore below would collide).
        fixture.b.createItem("Зайва річ", fixture.b.subBoots)

        val manifest = fixture.managerA.exportAll(fixture.sink())

        assertEquals(BackupManager.BACKUP_FORMAT_VERSION, manifest.formatVersion)
        assertEquals(1, manifest.schemaVersion)
        assertEquals("test-1.0", manifest.appVersion)
        assertEquals(1, manifest.taxonomy.seedVersion)
        Instant.parse(manifest.exportedAt) // must not throw
        // The seeded taxonomy is NOT exported as data; overrides are.
        assertEquals(listOf(9001L), manifest.categories.map { it.id })
        assertEquals(listOf(seed.item1, seed.item2), manifest.items.map { it.id })
        assertEquals(3, manifest.photos.size)
        assertEquals(2, manifest.settings.size)

        val report = fixture.managerB.importAll(fixture.source(), ImportMode.REPLACE)

        assertEquals(ImportMode.REPLACE, report.mode)
        assertEquals(manifest.exportedAt, report.exportedAt)
        assertEquals(2, report.tables.getValue("items").inserted)
        assertEquals(3, report.tables.getValue("photos").inserted)
        assertEquals(1, report.tables.getValue("categories").inserted)
        assertEquals(2, report.tables.getValue("settings").inserted)
        assertEquals(3, report.photosRestored)
        assertEquals(0, report.photosMissingInArchive)

        // Deep equality of every table (reminder clock excluded: per device).
        val snapshotA = fullSnapshot(fixture.a.db).withoutReminder()
        val snapshotB = fullSnapshot(fixture.b.db).withoutReminder()
        assertEquals(snapshotA, snapshotB)
        // Exactly the two exported items: the junk row is wiped, and the
        // restored rows reuse the manifest ids (the junk id collides by design,
        // so the count equality above is what proves the wipe).
        assertEquals(setOf(1L, 2L), snapshotB.items.map { it.id }.toSet())

        // Photo binaries travelled through the archive byte for byte.
        listOf("img/one.jpg", "img/two.jpg", "img/three.jpg").forEach { path ->
            assertTrue(
                path,
                fixture.photoBytes(fixture.rootA, path).contentEquals(fixture.photoBytes(fixture.rootB, path)),
            )
        }

        // Reminder clock: reset on export and on import.
        assertNotNull(fixture.managerA.lastExportAt())
        assertNotNull(fixture.managerB.lastExportAt())
    }

    @Test
    fun `REPLACE повторно з тим самим архівом - ідемпотентний`() = runBlocking {
        fixture.seedRichData()
        fixture.managerA.exportAll(fixture.sink())
        fixture.managerB.importAll(fixture.source(), ImportMode.REPLACE)
        val first = fullSnapshot(fixture.b.db).withoutReminder()

        fixture.managerB.importAll(fixture.source(), ImportMode.REPLACE)

        assertEquals(first, fullSnapshot(fixture.b.db).withoutReminder())
    }

    @Test
    fun `експорт без бінарки - рядок зберігається, файлу немає, рахунок у звіті`() = runBlocking {
        fixture.seedRichData()
        // The third photo file is missing on disk at export time.
        fixture.rootA.resolve("img/three.jpg").delete()

        fixture.managerA.exportAll(fixture.sink())
        val report = fixture.managerB.importAll(fixture.source(), ImportMode.REPLACE)

        assertEquals(2, report.photosRestored)
        assertEquals(1, report.photosMissingInArchive)
        assertEquals(
            fullSnapshot(fixture.a.db).withoutReminder(),
            fullSnapshot(fixture.b.db).withoutReminder(),
        )
        // The row is restored even though its binary never entered the archive.
        assertEquals(3, fullSnapshot(fixture.b.db).photos.size)
        assertFalse(fixture.rootB.resolve("img/three.jpg").exists())
        assertTrue(fixture.rootB.resolve("img/one.jpg").isFile)
    }

    @Test
    fun `структура архіву - маніфест перший, фото в photos`() = runBlocking {
        fixture.seedRichData()
        fixture.managerA.exportAll(fixture.sink())

        ZipFile(fixture.backupFile).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toList()
            assertEquals(BackupManager.MANIFEST_ENTRY, names.first())
            // One entry per photo row id, nothing else, no directories.
            assertEquals(listOf("photos/1", "photos/2", "photos/3"), names.drop(1))
            val manifestEntry = zip.getEntry(BackupManager.MANIFEST_ENTRY)!!
            val text = zip.getInputStream(manifestEntry).readBytes().decodeToString()
            assertTrue(text.contains("\"format_version\": 1"))
            assertTrue(text.contains("\"seed_version\": 1"))
        }
    }

    @Test
    fun `останній експорт - нуль до першого, парситься, битe значення читається як null`() = runBlocking {
        assertNull(fixture.managerA.lastExportAt())

        fixture.managerA.exportAll(fixture.sink())
        val instant = fixture.managerA.lastExportAt()
        assertNotNull(instant)
        assertTrue(instant!!.toEpochMilliseconds() > 0)

        // An unparseable stored value must read as null, never throw.
        fixture.a.settings.putString(BackupManager.LAST_EXPORT_SETTING_KEY, "не-дата")
        assertNull(fixture.managerA.lastExportAt())
    }

    @Test
    fun `спостереження репозиторію бачить імпортовані дані`() = runBlocking {
        fixture.seedRichData()
        fixture.managerA.exportAll(fixture.sink())
        fixture.managerB.importAll(fixture.source(), ImportMode.REPLACE)

        // Flow observation works over the restored data (P2 UI contract).
        val items = fixture.b.wardrobe.observeItems().first()
        assertEquals(1, items.size) // one active item; the jeans are archived
        assertEquals("Біла футболка", items.single().name)
        val aggregate = fixture.b.wardrobe.getItem(items.single().id)!!
        assertEquals(listOf("img/one.jpg", "img/two.jpg"), aggregate.photos.map { it.path })
        assertEquals(setOf("літнє", "улюблене"), aggregate.tags.map { it.name }.toSet())
        assertEquals(3, aggregate.attributes.size)
    }
}
