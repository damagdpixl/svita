package com.damagdpixl.svita.core.data.export

import com.damagdpixl.svita.core.data.ItemDraft
import com.damagdpixl.svita.core.data.OutfitDraft
import com.damagdpixl.svita.core.model.AttributeType
import com.damagdpixl.svita.core.model.OutfitEntry
import com.damagdpixl.svita.core.model.Section
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * MERGE semantics: insert-new, keep-existing on id collision (per table);
 * orphans — rows referencing rows that exist nowhere — are dropped and
 * counted, never inserted; wear_log scrubbing and SET NULL match the column's
 * own contract (see docs/export_format.md, section 3.1).
 */
class BackupMergeTest {
    private lateinit var fixture: BackupFixture

    @Before
    fun setUp() {
        fixture = BackupFixture()
    }

    @After
    fun tearDown() {
        fixture.cleanup()
    }

    /**
     * Device A exports two items; device B already has its own item with id 1
     * and its own tag with id 1. After MERGE, B keeps its rows where ids
     * collide and absorbs everything else. Note the per-table consequence the
     * format documents: A's item_tag link (item 1, tag 1) merges the LOCAL tag
     * 1 onto the LOCAL item 1 — identities are matched by id, not by lineage.
     */
    @Test
    fun `MERGE - конфлікти лишаються локальними, нові рядки вставляються`() = runBlocking {
        // ---- device A ----
        fixture.a.run {
            val tag = wardrobe.createTag("спільне") // id 1
            val colorDef = attributes.createDefinition(null, "color", AttributeType.COLOR, null, 0) // id 1
            val item1 = wardrobe.createItem(
                draft = ItemDraft(subtypeIdOf(subTShirt), "Футболка з A"),
                photos = listOf("img/a.jpg"),
                tagIds = setOf(tag),
                attributeValues = mapOf(colorDef to "#111111"),
            )
            val item2 = wardrobe.createItem(
                draft = ItemDraft(subtypeIdOf(subJeans), "Джинси з A", price = 1000.0),
            )
            val outfit = outfits.createOutfit(
                OutfitDraft("Образ з A"),
                entries = listOf(OutfitEntry(0L, item2, Section.LEGS)),
            )
            // References item 999, which exists nowhere — import must scrub it.
            wearLog.addEntry(LocalDate.parse("2025-06-02"), outfit, listOf(item2, 999L), 20.0, null)
        }
        File(fixture.rootA, "img").mkdirs()
        File(fixture.rootA, "img/a.jpg").writeBytes(byteArrayOf(7, 7, 7))
        fixture.managerA.exportAll(fixture.sink())
        val manifest = readManifest(fixture.backupFile)

        // ---- device B before merge ----
        val localItem = fixture.b.createItem("Локальна річ", fixture.b.subBoots)
        assertEquals(1L, localItem) // collides with A's item 1
        val localTag = fixture.b.wardrobe.createTag("локальне")
        assertEquals(1L, localTag) // collides with A's tag 1
        val localPhotoBytes = byteArrayOf(42)
        File(fixture.rootB, "img").mkdirs()
        File(fixture.rootB, "img/local.jpg").writeBytes(localPhotoBytes)

        // ---- merge ----
        val report = fixture.managerB.importAll(fixture.source(), ImportMode.MERGE)

        // ---- report ----
        assertEquals(ImportMode.MERGE, report.mode)
        assertEquals(1, report.tables.getValue("items").inserted)
        assertEquals(1, report.tables.getValue("items").skipped)
        assertEquals(1, report.tables.getValue("photos").inserted)
        assertEquals(1, report.tables.getValue("tags").skipped)
        assertEquals(1, report.tables.getValue("item_tags").inserted)
        // A's attribute definition is new on B, so its value merges onto the
        // kept item 1 (per-table identities).
        assertEquals(1, report.tables.getValue("attribute_definitions").inserted)
        assertEquals(1, report.tables.getValue("attribute_values").inserted)
        assertEquals(1, report.tables.getValue("outfits").inserted)
        assertEquals(1, report.tables.getValue("outfit_items").inserted)
        assertEquals(1, report.tables.getValue("wear_log").inserted)
        assertEquals(0, report.tables.getValue("settings").inserted)

        // ---- database state ----
        val snapshot = fullSnapshot(fixture.b.db)
        assertEquals(listOf(1L, 2L), snapshot.items.map { it.id })
        assertEquals("Локальна річ", snapshot.items[0].name) // kept, untouched
        assertEquals("Джинси з A", snapshot.items[1].name) // inserted from A

        // The new photo row arrived with its binary.
        assertEquals(listOf(1L), snapshot.photos.map { it.id })
        assertEquals("img/a.jpg", snapshot.photos.single().path)
        assertTrue(
            "restored bytes match",
            fixture.photoBytes(fixture.rootA, "img/a.jpg").contentEquals(fixture.photoBytes(fixture.rootB, "img/a.jpg")),
        )
        // The kept item's own photo file is untouched.
        assertTrue(localPhotoBytes.contentEquals(fixture.photoBytes(fixture.rootB, "img/local.jpg")))

        // wear_log: the lost item id 999 is scrubbed; outfit 1 survived.
        val wear = snapshot.wearLog.single()
        assertEquals(listOf(2L), wear.itemIds)
        assertEquals(1L, wear.outfitId!!)

        // The local item carries the local tag 1 (per-table merge rule).
        assertEquals(setOf(1L to 1L), snapshot.itemTags.toSet())

        // Reminder clock reset by the import.
        assertNotNull(fixture.managerB.lastExportAt())
    }

    /**
     * A crafted manifest full of orphan rows: every child row referencing a
     * row that exists neither locally nor in the manifest is dropped, counted,
     * and never reaches a foreign-key error.
     */
    @Test
    fun `MERGE з сирітськими рядками - сироти викидаються, wear_log чиститься`() = runBlocking {
        fixture.seedRichData()
        fixture.managerA.exportAll(fixture.sink())
        val manifest = readManifest(fixture.backupFile)
        val photos = readPhotoEntries(fixture.backupFile)

        val crafted = manifest.copy(
            photos = manifest.photos + PhotoRow(id = 90L, itemId = 999L, path = "img/orphan.jpg", position = 0),
            attributeValues = manifest.attributeValues +
                AttributeValueRow(itemId = 999L, definitionId = manifest.attributeDefinitions.first().id, value = "x"),
            itemTags = manifest.itemTags + ItemTagRow(itemId = 999L, tagId = manifest.tags.first().id),
            packingItems = manifest.packingItems +
                PackingItemRow(packingListId = 999L, itemId = manifest.items.first().id, packed = false),
            wearLog = manifest.wearLog + WearLogRow(
                id = 88L,
                date = "2025-06-06",
                outfitId = 777L, // outfit exists nowhere -> must import as null
                itemIds = listOf(999L, manifest.items.first().id),
                tempC = 1.0,
                note = "сирота",
            ),
        )

        val report = fixture.managerB.importAll(fixture.sourceOf(zipOf(crafted, photos)), ImportMode.MERGE)

        assertEquals(1, report.tables.getValue("photos").dropped)
        assertEquals(1, report.tables.getValue("attribute_values").dropped)
        assertEquals(1, report.tables.getValue("item_tags").dropped)
        assertEquals(1, report.tables.getValue("packing_items").dropped)
        // The wear-log orphan entry itself is fine — it just loses its outfit
        // and its unknown item id.
        assertEquals(0, report.tables.getValue("wear_log").dropped)

        val snapshot = fullSnapshot(fixture.b.db)
        assertFalse(snapshot.photos.any { it.id == 90L })
        assertFalse(snapshot.values.any { it.itemId == 999L })
        assertFalse(snapshot.itemTags.any { it.first == 999L })
        assertFalse(snapshot.packingEntries.any { it.listId == 999L })
        val orphanWear = snapshot.wearLog.single { it.note == "сирота" }
        assertNull(orphanWear.outfitId)
        assertEquals(listOf(manifest.items.first().id), orphanWear.itemIds)
        // No partial state: the rest of the manifest merged in full.
        assertEquals(manifest.items.size, snapshot.items.size)
    }

    @Test
    fun `MERGE у ту саму базу двічі - другий раз нічого не змінює`() = runBlocking {
        fixture.seedRichData()
        fixture.managerA.exportAll(fixture.sink())
        val manifest = readManifest(fixture.backupFile)
        val photos = readPhotoEntries(fixture.backupFile)
        val bytes = zipOf(manifest, photos)

        fixture.managerB.importAll(fixture.sourceOf(bytes), ImportMode.MERGE)
        val afterFirst = fullSnapshot(fixture.b.db).withoutReminder()
        val report = fixture.managerB.importAll(fixture.sourceOf(bytes), ImportMode.MERGE)

        assertEquals(afterFirst, fullSnapshot(fixture.b.db).withoutReminder())
        assertEquals(manifest.items.size, report.tables.getValue("items").skipped)
        assertEquals(0, report.tables.getValue("items").inserted)
    }
}
