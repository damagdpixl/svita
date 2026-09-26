package com.damagdpixl.svita.core.data.export

import app.svita.core.data.db.AppDatabase
import app.svita.core.data.db.App_settings
import app.svita.core.data.db.Attribute_definitions
import app.svita.core.data.db.Attribute_values
import app.svita.core.data.db.Categories
import app.svita.core.data.db.Colors
import app.svita.core.data.db.Item_tags
import app.svita.core.data.db.Items
import app.svita.core.data.db.Outfit_items
import app.svita.core.data.db.Outfits
import app.svita.core.data.db.Packing_items
import app.svita.core.data.db.Packing_lists
import app.svita.core.data.db.Photos
import app.svita.core.data.db.Style_tags
import app.svita.core.data.db.Subtypes
import app.svita.core.data.db.Tags
import app.svita.core.data.db.Wear_log
import com.damagdpixl.svita.core.data.DataContracts
import com.damagdpixl.svita.core.data.decodeItemIds
import com.damagdpixl.svita.core.data.encodeItemIds
import com.damagdpixl.svita.core.data.nowIso
import com.damagdpixl.svita.core.data.read
import com.damagdpixl.svita.core.data.write
import com.damagdpixl.svita.core.model.Season
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

private val backupJson = Json {
    // Forward compatibility: a manifest written by a newer patch release may
    // carry extra fields; they are ignored instead of failing the import.
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
}

/**
 * Full data ownership: export everything into one versioned archive (JSON
 * manifest + photo binaries), restore it (MERGE or REPLACE), and export the
 * wardrobe as CSV. The wire format is specified in `docs/export_format.md`.
 *
 * Concurrency: every database access runs through the shared transaction
 * helpers (`db.read`/`db.write`) off the caller thread; the whole import is one
 * transaction — a failure rolls it back and surfaces as a typed
 * [ImportError], leaving the database untouched.
 */
public class BackupManager(
    private val db: AppDatabase,
    private val files: FileStore,
    private val appVersion: String = "unknown",
) {
    /**
     * Exports the complete database plus all photo binaries into [output] as a
     * ZIP archive (`manifest.json` first, then one `photos/<id>` entry per
     * photo row with readable bytes) and returns the written manifest. Photo
     * rows whose file is missing on disk keep their row and skip the binary.
     * On success the backup-reminder clock is reset. Takes ownership of and
     * closes [output].
     */
    public suspend fun exportAll(output: FileSink): ExportManifest = withContext(Dispatchers.IO) {
        val manifest = readManifest()
        output.use { sink ->
            ZipOutputStream(sink.outputStream().buffered()).use { zip ->
                zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
                zip.write(
                    backupJson.encodeToString(ExportManifest.serializer(), manifest).encodeToByteArray(),
                )
                zip.closeEntry()
                manifest.photos.forEach { photo ->
                    val source = files.openPhoto(photo.path) ?: return@forEach
                    source.use {
                        zip.putNextEntry(ZipEntry(photoEntryName(photo.id)))
                        it.inputStream().use { input -> input.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
        }
        resetReminderClock()
        manifest
    }

    /**
     * Imports [source] (a backup produced by [exportAll]) in [mode] and returns
     * an [ImportReport]. Expected failures (corrupt archive, wrong version,
     * malformed manifest, REPLACE referential violations, storage errors) throw
     * a typed [ImportError] and leave the database untouched. Photo binaries
     * found in the archive are restored into [files] for the photo rows that
     * get inserted. Takes ownership of and closes [source]. On success the
     * backup-reminder clock is reset (fresh data = fresh reminder cycle, and
     * the manifest's own reminder value describes another device).
     */
    public suspend fun importAll(source: FileSource, mode: ImportMode): ImportReport =
        withContext(Dispatchers.IO) {
            source.use { src ->
                // A ZIP signature check up front separates "not an archive at
                // all" (garbage never reads as an error inside ZipInputStream —
                // it just yields no entries) from a valid-but-empty archive.
                val pushed = java.io.PushbackInputStream(src.inputStream().buffered(), 4)
                val signature = ByteArray(4)
                // Manual fill (readNBytes is API 33; min is 26).
                var read = 0
                while (read < 4) {
                    val n = pushed.read(signature, read, 4 - read)
                    if (n < 0) break
                    read += n
                }
                if (read < 4) throw ImportError.NotAnArchive
                val isZipSignature = signature[0] == 'P'.code.toByte() && signature[1] == 'K'.code.toByte() &&
                    (
                        (signature[2] == 3.toByte() && signature[3] == 4.toByte()) ||
                            (signature[2] == 5.toByte() && signature[3] == 6.toByte()) ||
                            (signature[2] == 7.toByte() && signature[3] == 8.toByte())
                        )
                if (!isZipSignature) throw ImportError.NotAnArchive
                pushed.unread(signature, 0, read)
                val zip = ZipInputStream(pushed)
                var manifest: ExportManifest? = null
                var plan: ImportPlan? = null
                // Photo entries that appear before the manifest (only possible
                // in non-standard archives) are buffered; ours always put the
                // manifest first.
                val earlyPhotos = LinkedHashMap<Long, ByteArray>()
                val restoredIds = HashSet<Long>()
                try {
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val name = entry.name
                        val photoId = photoEntryId(name)
                        when {
                            name == MANIFEST_ENTRY && manifest == null -> {
                                val parsed = parseManifest(zip.readBytes())
                                validateVersions(parsed)
                                plan = buildPlan(parsed, mode)
                                manifest = parsed
                            }

                            photoId != null && manifest == null -> earlyPhotos[photoId] = zip.readBytes()

                            photoId != null && plan != null -> {
                                val target = plan.photoTargets[photoId]
                                if (target != null) {
                                    restorePhotoBytes(target) { out -> zip.copyTo(out) }
                                    restoredIds += photoId
                                }
                            }
                        }
                        zip.closeEntry()
                    }
                } catch (e: ZipException) {
                    throw ImportError.NotAnArchive
                } catch (e: java.io.IOException) {
                    // Unreadable archive (stream error mid-scan): the database
                    // was not touched yet, whatever the manifest state is.
                    throw ImportError.NotAnArchive
                }
                val parsed = manifest ?: throw ImportError.MissingManifest
                val finalPlan = plan ?: error("plan missing for a parsed manifest")
                // Restore any photos that were buffered before the manifest.
                earlyPhotos.forEach { (photoId, bytes) ->
                    val target = finalPlan.photoTargets[photoId]
                    if (target != null) {
                        restorePhotoBytes(target) { it.write(bytes) }
                        restoredIds += photoId
                    }
                }
                try {
                    applyPlan(finalPlan)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    throw ImportError.WriteFailed(e.message ?: e::class.simpleName ?: "database error", e)
                }
                resetReminderClock()
                ImportReport(
                    mode = mode,
                    exportedAt = parsed.exportedAt,
                    tables = finalPlan.reports,
                    photosRestored = restoredIds.size,
                    photosMissingInArchive = (finalPlan.photoTargets.keys - restoredIds).size,
                )
            }
        }

    /**
     * One row per item: core columns plus one flattened column per attribute
     * definition (`attr.<key>`). Headers are English; values are emitted as
     * stored, RFC 4180-quoted, CRLF-terminated (see `docs/export_format.md`).
     * Takes ownership of and closes [output].
     */
    public suspend fun exportItemsCsv(output: TextSink): Unit = withContext(Dispatchers.IO) {
        output.use { sink ->
            sink.writer().use { writer ->
                db.read {
                    val definitions = db.attribute_definitionsQueries.selectAllAttributeDefinitions().executeAsList()
                    val columns = csvColumns(definitions)
                    val subtypeKeys = db.subtypesQueries.selectAllSubtypes().executeAsList()
                        .associate { it.id to it.key }
                    val values = db.attribute_valuesQueries.selectAllAttributeValues().executeAsList()
                        .groupBy({ it.item_id }) { it.definition_id to it.value_ }
                    writer.write(
                        Csv.encodeRow(
                            listOf(
                                "id", "name", "subtype_key", "notes", "price", "purchase_date",
                                "seasons", "sex", "rating", "archived", "created_at", "updated_at",
                            ) + columns.map { it.second },
                        ),
                    )
                    db.itemsQueries.selectAllItems().executeAsList().forEach { item ->
                        val itemValues = values[item.id].orEmpty().toMap()
                        writer.write(
                            Csv.encodeRow(
                                buildList {
                                    add(item.id.toString())
                                    add(item.name)
                                    add(subtypeKeys[item.subtype_id] ?: "")
                                    add(item.notes ?: "")
                                    add(item.price?.toString() ?: "")
                                    add(item.purchase_date ?: "")
                                    add(
                                        Season.fromBitmask(item.season_flags.toInt())
                                            .joinToString(";") { it.name.lowercase() },
                                    )
                                    add(item.sex ?: "")
                                    add(item.rating?.toString() ?: "")
                                    add(if (item.archived != 0L) "1" else "0")
                                    add(item.created_at)
                                    add(item.updated_at)
                                    columns.forEach { (definitionId, _) -> add(itemValues[definitionId] ?: "") }
                                },
                            ),
                        )
                    }
                }
            }
        }
    }

    /**
     * When the last successful backup ([exportAll]) or restore ([importAll])
     * happened, for the Settings reminder; `null` when never.
     */
    public suspend fun lastExportAt(): Instant? = withContext(Dispatchers.IO) {
        db.app_settingsQueries.selectSetting(LAST_EXPORT_SETTING_KEY).executeAsOneOrNull()?.value_
            ?.let { raw -> runCatching { Instant.parse(raw) }.getOrNull() }
    }

    // ---- export ----------------------------------------------------------

    private suspend fun readManifest(): ExportManifest = db.read {
        ExportManifest(
            formatVersion = BACKUP_FORMAT_VERSION,
            schemaVersion = DataContracts.PERSISTENCE_VERSION,
            exportedAt = nowIso(),
            appVersion = appVersion,
            taxonomy = TaxonomyMeta(seedVersion = DataContracts.PERSISTENCE_VERSION),
            categories = db.categoriesQueries.selectAllCategories().executeAsList()
                .map { it.toRow() }.filterNot { it.system },
            items = db.itemsQueries.selectAllItems().executeAsList().map { it.toRow() },
            photos = db.photosQueries.selectAllPhotos().executeAsList().map { it.toRow() },
            attributeDefinitions = db.attribute_definitionsQueries.selectAllAttributeDefinitions()
                .executeAsList().map { it.toRow() },
            attributeValues = db.attribute_valuesQueries.selectAllAttributeValues().executeAsList()
                .map { it.toRow() },
            tags = db.tagsQueries.selectAllTags().executeAsList().map { it.toRow() },
            itemTags = db.tagsQueries.selectAllItemTags().executeAsList().map { it.toRow() },
            outfits = db.outfitsQueries.selectAllOutfits().executeAsList().map { it.toRow() },
            outfitItems = db.outfitsQueries.selectAllOutfitItems().executeAsList().map { it.toRow() },
            wearLog = db.wear_logQueries.selectAllWearLog().executeAsList().map { it.toRow() },
            packingLists = db.packingQueries.selectAllPackingLists().executeAsList().map { it.toRow() },
            packingItems = db.packingQueries.selectAllPackingItems().executeAsList().map { it.toRow() },
            settings = db.app_settingsQueries.selectAllSettings().executeAsList().map { it.toRow() },
        )
    }

    private suspend fun resetReminderClock() {
        db.write { db.app_settingsQueries.insertSetting(LAST_EXPORT_SETTING_KEY, nowIso()) }
    }

    // ---- import planning -------------------------------------------------

    private fun parseManifest(bytes: ByteArray): ExportManifest =
        try {
            backupJson.decodeFromString(ExportManifest.serializer(), bytes.decodeToString())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ImportError.MalformedManifest("manifest.json is not a valid v1 manifest", e)
        }

    private fun validateVersions(manifest: ExportManifest) {
        if (manifest.formatVersion < 1) {
            throw ImportError.MalformedManifest("format_version ${manifest.formatVersion} is not a positive version")
        }
        if (manifest.schemaVersion < 1) {
            throw ImportError.MalformedManifest("schema_version ${manifest.schemaVersion} is not a positive version")
        }
        if (manifest.formatVersion > BACKUP_FORMAT_VERSION) {
            throw ImportError.UnsupportedFormatVersion(manifest.formatVersion, BACKUP_FORMAT_VERSION)
        }
        if (manifest.schemaVersion > DataContracts.PERSISTENCE_VERSION) {
            throw ImportError.UnsupportedSchemaVersion(manifest.schemaVersion, DataContracts.PERSISTENCE_VERSION)
        }
    }

    private suspend fun buildPlan(manifest: ExportManifest, mode: ImportMode): ImportPlan = when (mode) {
        ImportMode.MERGE -> buildMergePlan(manifest)
        ImportMode.REPLACE -> buildReplacePlan(manifest)
    }

    /**
     * MERGE: insert-new, keep-existing on id collision (per table — the pair or
     * key tables compare their own key). Orphans — manifest rows referencing
     * rows that exist neither locally nor in the manifest — are dropped and
     * counted, never inserted. wear_log entries keep only item ids that exist
     * after the merge; an entry whose outfit did not survive imports with
     * `outfit_id = null` (the semantics of that column's ON DELETE SET NULL).
     */
    private suspend fun buildMergePlan(manifest: ExportManifest): ImportPlan = db.read {
        val counts = Counts()

        val dbCategoryIds = db.categoriesQueries.selectAllCategories().executeAsList().mapTo(HashSet()) { it.id }
        val dbSubtypeIds = db.subtypesQueries.selectAllSubtypes().executeAsList().mapTo(HashSet()) { it.id }
        val dbItemIds = db.itemsQueries.selectAllItems().executeAsList().mapTo(HashSet()) { it.id }
        val dbPhotoIds = db.photosQueries.selectAllPhotos().executeAsList().mapTo(HashSet()) { it.id }
        val dbDefinitionIds = db.attribute_definitionsQueries.selectAllAttributeDefinitions()
            .executeAsList().mapTo(HashSet()) { it.id }
        val dbOutfitIds = db.outfitsQueries.selectAllOutfits().executeAsList().mapTo(HashSet()) { it.id }
        val dbWearIds = db.wear_logQueries.selectAllWearLog().executeAsList().mapTo(HashSet()) { it.id }
        val dbPackingListIds = db.packingQueries.selectAllPackingLists().executeAsList().mapTo(HashSet()) { it.id }
        val dbTags = db.tagsQueries.selectAllTags().executeAsList()
        val dbTagIds = dbTags.mapTo(HashSet()) { it.id }
        val dbTagNames = dbTags.mapTo(HashSet()) { it.name }
        val dbSettingKeys = db.app_settingsQueries.selectAllSettings().executeAsList().mapTo(HashSet()) { it.key }
        val dbValuePairs = db.attribute_valuesQueries.selectAllAttributeValues().executeAsList()
            .mapTo(HashSet()) { it.item_id to it.definition_id }
        val dbItemTagPairs = db.tagsQueries.selectAllItemTags().executeAsList()
            .mapTo(HashSet()) { it.item_id to it.tag_id }
        val dbOutfitItemPairs = db.outfitsQueries.selectAllOutfitItems().executeAsList()
            .mapTo(HashSet()) { it.outfit_id to it.item_id }
        val dbPackingPairs = db.packingQueries.selectAllPackingItems().executeAsList()
            .mapTo(HashSet()) { it.packing_list_id to it.item_id }

        // Categories (user-created overrides only): resolve parents iteratively
        // so children of imported parents survive; unresolved leftovers are a
        // cycle or a missing parent — dropped.
        val keptCategories = ArrayList<CategoryRow>()
        val keptCategoryIds = HashSet<Long>()
        var pending = manifest.categories
        while (pending.isNotEmpty()) {
            val unresolved = ArrayList<CategoryRow>()
            var progress = false
            for (row in pending) {
                when {
                    row.id in dbCategoryIds || row.id in keptCategoryIds ->
                        counts.add("categories", skipped = 1)

                    row.parentId == null || row.parentId in dbCategoryIds || row.parentId in keptCategoryIds -> {
                        keptCategories += row
                        keptCategoryIds += row.id
                        counts.add("categories", inserted = 1)
                        progress = true
                    }

                    else -> unresolved += row
                }
            }
            if (!progress) {
                counts.add("categories", dropped = unresolved.size)
                break
            }
            pending = unresolved
        }
        val finalCategoryIds = dbCategoryIds + keptCategoryIds

        val keptItems = ArrayList<ItemRow>()
        val keptItemIds = HashSet<Long>()
        for (row in manifest.items) {
            when {
                row.subtypeId !in dbSubtypeIds -> counts.add("items", dropped = 1)
                row.id in dbItemIds || row.id in keptItemIds -> counts.add("items", skipped = 1)
                else -> {
                    keptItems += row
                    keptItemIds += row.id
                    counts.add("items", inserted = 1)
                }
            }
        }
        val finalItemIds = dbItemIds + keptItemIds

        val keptPhotos = ArrayList<PhotoRow>()
        val photoTargets = HashMap<Long, String>()
        for (row in manifest.photos) {
            when {
                row.itemId !in finalItemIds -> counts.add("photos", dropped = 1)
                row.id in dbPhotoIds || row.id in photoTargets -> counts.add("photos", skipped = 1)
                else -> {
                    keptPhotos += row
                    photoTargets[row.id] = row.path
                    counts.add("photos", inserted = 1)
                }
            }
        }

        val keptDefinitions = ArrayList<AttributeDefinitionRow>()
        val keptDefinitionIds = HashSet<Long>()
        for (row in manifest.attributeDefinitions) {
            when {
                row.categoryId != null && row.categoryId !in finalCategoryIds ->
                    counts.add("attribute_definitions", dropped = 1)

                row.id in dbDefinitionIds || row.id in keptDefinitionIds ->
                    counts.add("attribute_definitions", skipped = 1)

                else -> {
                    keptDefinitions += row
                    keptDefinitionIds += row.id
                    counts.add("attribute_definitions", inserted = 1)
                }
            }
        }
        val finalDefinitionIds = dbDefinitionIds + keptDefinitionIds

        val keptValues = ArrayList<AttributeValueRow>()
        val keptValuePairs = HashSet<Pair<Long, Long>>()
        for (row in manifest.attributeValues) {
            when {
                row.itemId !in finalItemIds || row.definitionId !in finalDefinitionIds ->
                    counts.add("attribute_values", dropped = 1)

                (row.itemId to row.definitionId) in dbValuePairs ||
                    (row.itemId to row.definitionId) in keptValuePairs ->
                    counts.add("attribute_values", skipped = 1)

                else -> {
                    keptValues += row
                    keptValuePairs += row.itemId to row.definitionId
                    counts.add("attribute_values", inserted = 1)
                }
            }
        }

        val keptTags = ArrayList<TagRow>()
        val keptTagIds = HashSet<Long>()
        val keptTagNames = HashSet<String>()
        for (row in manifest.tags) {
            when {
                row.id in dbTagIds || row.id in keptTagIds -> counts.add("tags", skipped = 1)
                // name is UNIQUE: a local tag with a different id still wins.
                row.name in dbTagNames || row.name in keptTagNames -> counts.add("tags", skipped = 1)
                else -> {
                    keptTags += row
                    keptTagIds += row.id
                    keptTagNames += row.name
                    counts.add("tags", inserted = 1)
                }
            }
        }
        val finalTagIds = dbTagIds + keptTagIds

        val keptItemTags = ArrayList<ItemTagRow>()
        val keptItemTagPairs = HashSet<Pair<Long, Long>>()
        for (row in manifest.itemTags) {
            when {
                row.itemId !in finalItemIds || row.tagId !in finalTagIds ->
                    counts.add("item_tags", dropped = 1)

                (row.itemId to row.tagId) in dbItemTagPairs ||
                    (row.itemId to row.tagId) in keptItemTagPairs ->
                    counts.add("item_tags", skipped = 1)

                else -> {
                    keptItemTags += row
                    keptItemTagPairs += row.itemId to row.tagId
                    counts.add("item_tags", inserted = 1)
                }
            }
        }

        val keptOutfits = ArrayList<OutfitRow>()
        val keptOutfitIds = HashSet<Long>()
        for (row in manifest.outfits) {
            if (row.id in dbOutfitIds || row.id in keptOutfitIds) {
                counts.add("outfits", skipped = 1)
            } else {
                keptOutfits += row
                keptOutfitIds += row.id
                counts.add("outfits", inserted = 1)
            }
        }
        val finalOutfitIds = dbOutfitIds + keptOutfitIds

        val keptOutfitItems = ArrayList<OutfitItemRow>()
        val keptOutfitItemPairs = HashSet<Pair<Long, Long>>()
        for (row in manifest.outfitItems) {
            when {
                row.outfitId !in finalOutfitIds || row.itemId !in finalItemIds ->
                    counts.add("outfit_items", dropped = 1)

                (row.outfitId to row.itemId) in dbOutfitItemPairs ||
                    (row.outfitId to row.itemId) in keptOutfitItemPairs ->
                    counts.add("outfit_items", skipped = 1)

                else -> {
                    keptOutfitItems += row
                    keptOutfitItemPairs += row.outfitId to row.itemId
                    counts.add("outfit_items", inserted = 1)
                }
            }
        }

        val keptWearLog = ArrayList<WearLogRow>()
        val keptWearIds = HashSet<Long>()
        for (row in manifest.wearLog) {
            if (row.id in dbWearIds || row.id in keptWearIds) {
                counts.add("wear_log", skipped = 1)
                continue
            }
            // Match the column semantics: SET NULL for a lost outfit, and the
            // same scrubbing WardrobeRepository.deleteItem applies to item ids.
            keptWearLog += row.copy(
                outfitId = row.outfitId?.takeIf { it in finalOutfitIds },
                itemIds = row.itemIds.filter { it in finalItemIds },
            )
            keptWearIds += row.id
            counts.add("wear_log", inserted = 1)
        }

        val keptPackingLists = ArrayList<PackingListRow>()
        val keptPackingListIds = HashSet<Long>()
        for (row in manifest.packingLists) {
            if (row.id in dbPackingListIds || row.id in keptPackingListIds) {
                counts.add("packing_lists", skipped = 1)
            } else {
                keptPackingLists += row
                keptPackingListIds += row.id
                counts.add("packing_lists", inserted = 1)
            }
        }

        val keptPackingItems = ArrayList<PackingItemRow>()
        val keptPackingPairs = HashSet<Pair<Long, Long>>()
        for (row in manifest.packingItems) {
            when {
                row.packingListId !in dbPackingListIds + keptPackingListIds || row.itemId !in finalItemIds ->
                    counts.add("packing_items", dropped = 1)

                (row.packingListId to row.itemId) in dbPackingPairs ||
                    (row.packingListId to row.itemId) in keptPackingPairs ->
                    counts.add("packing_items", skipped = 1)

                else -> {
                    keptPackingItems += row
                    keptPackingPairs += row.packingListId to row.itemId
                    counts.add("packing_items", inserted = 1)
                }
            }
        }

        val keptSettings = ArrayList<SettingRow>()
        val keptSettingKeys = HashSet<String>()
        for (row in manifest.settings) {
            if (row.key in dbSettingKeys || row.key in keptSettingKeys) {
                counts.add("settings", skipped = 1)
            } else {
                keptSettings += row
                keptSettingKeys += row.key
                counts.add("settings", inserted = 1)
            }
        }

        ImportPlan(
            mode = ImportMode.MERGE,
            categories = keptCategories,
            items = keptItems,
            photos = keptPhotos,
            definitions = keptDefinitions,
            values = keptValues,
            tags = keptTags,
            itemTags = keptItemTags,
            outfits = keptOutfits,
            outfitItems = keptOutfitItems,
            wearLog = keptWearLog,
            packingLists = keptPackingLists,
            packingItems = keptPackingItems,
            settings = keptSettings,
            photoTargets = photoTargets,
            reports = counts.snapshot(TABLES),
        )
    }

    /**
     * REPLACE: validate the manifest against the post-restore world (the
     * manifest becomes the whole truth, so referential violations refuse the
     * import instead of dropping data), and capture the currently installed
     * taxonomy seed — the wipe below empties the seed tables too, and the
     * apply step re-inserts the captured seed verbatim before the manifest.
     * The seed capture takes SYSTEM category rows only: user categories (from
     * this or a previous import) are not seed — they are wiped and restored
     * from the manifest.
     */
    private suspend fun buildReplacePlan(manifest: ExportManifest): ImportPlan = db.read {
        val seedCategories = db.categoriesQueries.selectAllCategories().executeAsList()
            .filter { it.system_flag != 0L }
        val seedSubtypes = db.subtypesQueries.selectAllSubtypes().executeAsList()
        val seedColors = db.colorsQueries.selectAllColors().executeAsList()
        val seedStyleTags = db.style_tagsQueries.selectAllStyleTags().executeAsList()
        val seedCategoryIds = seedCategories.mapTo(HashSet()) { it.id }
        val seedSubtypeIds = seedSubtypes.mapTo(HashSet()) { it.id }

        fun integrity(detail: String): Nothing = throw ImportError.ReferentialIntegrity(detail)

        fun <T> List<T>.uniqueIds(idOf: (T) -> Long, table: String) {
            val seen = HashSet<Long>()
            forEach { row ->
                if (!seen.add(idOf(row))) integrity("duplicate $table id ${idOf(row)} in manifest")
            }
        }

        manifest.categories.uniqueIds({ it.id }, "category")
        manifest.items.uniqueIds({ it.id }, "item")
        manifest.photos.uniqueIds({ it.id }, "photo")
        manifest.attributeDefinitions.uniqueIds({ it.id }, "attribute definition")
        manifest.outfits.uniqueIds({ it.id }, "outfit")
        manifest.wearLog.uniqueIds({ it.id }, "wear log entry")
        manifest.packingLists.uniqueIds({ it.id }, "packing list")

        val manifestCategoryIds = manifest.categories.mapTo(HashSet()) { it.id }
        val finalCategoryIds = seedCategoryIds + manifestCategoryIds
        // Parents first among the overrides; a parent cycle refuses the import.
        val orderedCategories = orderParentsFirst(manifest.categories) { it.id to it.parentId }
            ?: integrity("category parent_id forms a cycle")
        orderedCategories.forEach { row ->
            if (row.id in seedCategoryIds) {
                integrity("user category ${row.id} collides with a seeded category id")
            }
            if (row.parentId != null && row.parentId !in finalCategoryIds) {
                integrity("category ${row.id} references unknown parent ${row.parentId}")
            }
        }

        val manifestItemIds = manifest.items.mapTo(HashSet()) { it.id }
        manifest.items.forEach { row ->
            if (row.subtypeId !in seedSubtypeIds) {
                integrity("item ${row.id} references unknown subtype ${row.subtypeId}")
            }
        }
        manifest.photos.forEach { row ->
            if (row.itemId !in manifestItemIds) {
                integrity("photo ${row.id} references unknown item ${row.itemId}")
            }
        }
        val manifestDefinitionIds = manifest.attributeDefinitions.mapTo(HashSet()) { it.id }
        manifest.attributeDefinitions.forEach { row ->
            if (row.categoryId != null && row.categoryId !in finalCategoryIds) {
                integrity("attribute definition ${row.id} references unknown category ${row.categoryId}")
            }
        }
        val manifestValuePairs = HashSet<Pair<Long, Long>>()
        manifest.attributeValues.forEach { row ->
            if (row.itemId !in manifestItemIds || row.definitionId !in manifestDefinitionIds) {
                integrity("attribute value (${row.itemId}, ${row.definitionId}) references a missing row")
            }
            if (!manifestValuePairs.add(row.itemId to row.definitionId)) {
                integrity("duplicate attribute value pair (${row.itemId}, ${row.definitionId}) in manifest")
            }
        }
        val manifestTagIds = manifest.tags.mapTo(HashSet()) { it.id }
        val manifestTagNames = HashSet<String>()
        manifest.tags.forEach { row ->
            if (!manifestTagNames.add(row.name)) integrity("duplicate tag name '${row.name}' in manifest")
        }
        val manifestItemTagPairs = HashSet<Pair<Long, Long>>()
        manifest.itemTags.forEach { row ->
            if (row.itemId !in manifestItemIds || row.tagId !in manifestTagIds) {
                integrity("item tag (${row.itemId}, ${row.tagId}) references a missing row")
            }
            if (!manifestItemTagPairs.add(row.itemId to row.tagId)) {
                integrity("duplicate item tag pair (${row.itemId}, ${row.tagId}) in manifest")
            }
        }
        val manifestOutfitIds = manifest.outfits.mapTo(HashSet()) { it.id }
        val manifestOutfitItemPairs = HashSet<Pair<Long, Long>>()
        manifest.outfitItems.forEach { row ->
            if (row.outfitId !in manifestOutfitIds || row.itemId !in manifestItemIds) {
                integrity("outfit item (${row.outfitId}, ${row.itemId}) references a missing row")
            }
            if (!manifestOutfitItemPairs.add(row.outfitId to row.itemId)) {
                integrity("duplicate outfit item pair (${row.outfitId}, ${row.itemId}) in manifest")
            }
        }
        // wear_log: SET NULL semantics for a dangling outfit; item ids are
        // scrubbed to what the restore actually contains.
        val wearLog = manifest.wearLog.map { row ->
            row.copy(
                outfitId = row.outfitId?.takeIf { it in manifestOutfitIds },
                itemIds = row.itemIds.filter { it in manifestItemIds },
            )
        }
        val manifestPackingListIds = manifest.packingLists.mapTo(HashSet()) { it.id }
        val manifestPackingPairs = HashSet<Pair<Long, Long>>()
        manifest.packingItems.forEach { row ->
            if (row.packingListId !in manifestPackingListIds || row.itemId !in manifestItemIds) {
                integrity("packing item (${row.packingListId}, ${row.itemId}) references a missing row")
            }
            if (!manifestPackingPairs.add(row.packingListId to row.itemId)) {
                integrity("duplicate packing item pair (${row.packingListId}, ${row.itemId}) in manifest")
            }
        }
        val manifestSettingKeys = HashSet<String>()
        manifest.settings.forEach { row ->
            if (!manifestSettingKeys.add(row.key)) integrity("duplicate setting key '${row.key}' in manifest")
        }

        ImportPlan(
            mode = ImportMode.REPLACE,
            seedCategories = seedCategories,
            seedSubtypes = seedSubtypes,
            seedColors = seedColors,
            seedStyleTags = seedStyleTags,
            categories = orderedCategories,
            items = manifest.items,
            photos = manifest.photos,
            definitions = manifest.attributeDefinitions,
            values = manifest.attributeValues,
            tags = manifest.tags,
            itemTags = manifest.itemTags,
            outfits = manifest.outfits,
            outfitItems = manifest.outfitItems,
            wearLog = wearLog,
            packingLists = manifest.packingLists,
            packingItems = manifest.packingItems,
            settings = manifest.settings,
            photoTargets = manifest.photos.associate { it.id to it.path },
            reports = TABLES.associateWith { table ->
                TableReport(
                    inserted = when (table) {
                        "categories" -> orderedCategories.size
                        "items" -> manifest.items.size
                        "photos" -> manifest.photos.size
                        "attribute_definitions" -> manifest.attributeDefinitions.size
                        "attribute_values" -> manifest.attributeValues.size
                        "tags" -> manifest.tags.size
                        "item_tags" -> manifest.itemTags.size
                        "outfits" -> manifest.outfits.size
                        "outfit_items" -> manifest.outfitItems.size
                        "wear_log" -> wearLog.size
                        "packing_lists" -> manifest.packingLists.size
                        "packing_items" -> manifest.packingItems.size
                        else -> manifest.settings.size
                    },
                )
            },
        )
    }

    // ---- import apply ----------------------------------------------------

    private suspend fun applyPlan(plan: ImportPlan) {
        db.write {
            if (plan.mode == ImportMode.REPLACE) {
                wipeAll()
                // Re-seed verbatim: the rows captured before the wipe are the
                // seed as installed on THIS device (identical to a fresh seed
                // for the same schema version).
                plan.seedCategories.sortedBy { it.id }.forEach { row ->
                    db.insertCategory(row)
                }
                plan.seedSubtypes.forEach { row -> db.insertSubtype(row) }
                plan.seedColors.forEach { row -> db.insertColor(row) }
                plan.seedStyleTags.forEach { row -> db.insertStyleTag(row) }
            }
            // FK-safe insert order: parents before children.
            plan.categories.forEach { db.insert(it) }
            plan.items.forEach { db.insert(it) }
            plan.photos.forEach { db.insert(it) }
            plan.definitions.forEach { db.insert(it) }
            plan.values.forEach { db.insert(it) }
            plan.tags.forEach { db.insert(it) }
            plan.itemTags.forEach { db.insert(it) }
            plan.outfits.forEach { db.insert(it) }
            plan.outfitItems.forEach { db.insert(it) }
            plan.wearLog.forEach { db.insert(it) }
            plan.packingLists.forEach { db.insert(it) }
            plan.packingItems.forEach { db.insert(it) }
            plan.settings.forEach { db.insert(it) }
        }
    }

    /** Empties every user table and the taxonomy tables, children before parents. */
    private fun wipeAll() {
        db.wear_logQueries.deleteAllWearLog()
        db.packingQueries.deleteAllPackingItems()
        db.packingQueries.deleteAllPackingLists()
        db.outfitsQueries.deleteAllOutfitItems()
        db.outfitsQueries.deleteAllOutfits()
        db.attribute_valuesQueries.deleteAllAttributeValues()
        db.attribute_definitionsQueries.deleteAllAttributeDefinitions()
        db.tagsQueries.deleteAllItemTags()
        db.photosQueries.deleteAllPhotos()
        db.itemsQueries.deleteAllItems()
        db.tagsQueries.deleteAllTags()
        db.app_settingsQueries.deleteAllSettings()
        db.subtypesQueries.deleteAllSubtypes()
        db.categoriesQueries.deleteAllCategories()
        db.colorsQueries.deleteAllColors()
        db.style_tagsQueries.deleteAllStyleTags()
    }

    private fun restorePhotoBytes(path: String, write: (OutputStream) -> Unit) {
        files.sinkPhoto(path).use { sink ->
            sink.outputStream().use { out -> write(out) }
        }
    }

    // ---- CSV helpers -----------------------------------------------------

    /** `attr.<key>` per definition; duplicate keys get `#<definitionId>` appended. */
    private fun csvColumns(definitions: List<Attribute_definitions>): List<Pair<Long, String>> {
        val used = HashSet<String>()
        return definitions.map { definition ->
            val base = "attr.${definition.key}"
            val name = if (used.add(base)) base else "$base#${definition.id}".also { used.add(it) }
            definition.id to name
        }
    }

    public companion object {
        /** Current backup format version; refuse files written by a newer one. */
        public const val BACKUP_FORMAT_VERSION: Int = 1

        /** Zip entry name of the manifest; the exporter always writes it first. */
        public const val MANIFEST_ENTRY: String = "manifest.json"

        /** Zip entry prefix of photo binaries, followed by the photo row id. */
        public const val PHOTO_ENTRY_PREFIX: String = "photos/"

        /** Settings key of the backup-reminder clock ([lastExportAt]). */
        public const val LAST_EXPORT_SETTING_KEY: String = "backup.last_export_at"

        /** Photo zip entry name for a photo row id, e.g. `photos/5`. */
        public fun photoEntryName(photoId: Long): String = "$PHOTO_ENTRY_PREFIX$photoId"

        private val PHOTO_ENTRY = Regex("^photos/(\\d+)")

        private fun photoEntryId(name: String): Long? =
            PHOTO_ENTRY.find(name)?.groupValues?.get(1)?.toLongOrNull()

        private val TABLES = listOf(
            "categories", "items", "photos", "attribute_definitions", "attribute_values",
            "tags", "item_tags", "outfits", "outfit_items", "wear_log",
            "packing_lists", "packing_items", "settings",
        )
    }
}

// ---- plan --------------------------------------------------------------

/** Mutable per-table counters reduced into [TableReport]s at the end. */
private class Counts {
    private val map = LinkedHashMap<String, LongArray>()

    private fun of(table: String): LongArray = map.getOrPut(table) { LongArray(3) }

    fun add(table: String, inserted: Int = 0, skipped: Int = 0, dropped: Int = 0) {
        val slot = of(table)
        slot[0] += inserted
        slot[1] += skipped
        slot[2] += dropped
    }

    /** Every table gets an entry (zeroed when untouched) — stable report shape. */
    fun snapshot(allTables: List<String>): Map<String, TableReport> = allTables.associateWith { table ->
        map[table]?.let { slot ->
            TableReport(inserted = slot[0].toInt(), skipped = slot[1].toInt(), dropped = slot[2].toInt())
        } ?: TableReport()
    }
}

/** Everything the apply step writes, precomputed so validation precedes writes. */
private class ImportPlan(
    val mode: ImportMode,
    val seedCategories: List<Categories> = emptyList(),
    val seedSubtypes: List<Subtypes> = emptyList(),
    val seedColors: List<Colors> = emptyList(),
    val seedStyleTags: List<Style_tags> = emptyList(),
    val categories: List<CategoryRow>,
    val items: List<ItemRow>,
    val photos: List<PhotoRow>,
    val definitions: List<AttributeDefinitionRow>,
    val values: List<AttributeValueRow>,
    val tags: List<TagRow>,
    val itemTags: List<ItemTagRow>,
    val outfits: List<OutfitRow>,
    val outfitItems: List<OutfitItemRow>,
    val wearLog: List<WearLogRow>,
    val packingLists: List<PackingListRow>,
    val packingItems: List<PackingItemRow>,
    val settings: List<SettingRow>,
    val photoTargets: Map<Long, String>,
    val reports: Map<String, TableReport>,
)

/**
 * Orders rows so a parent (by [keyAndParent]) precedes its children; returns
 * null when the parents form a cycle.
 */
private fun <T> orderParentsFirst(rows: List<T>, keyAndParent: (T) -> Pair<Long, Long?>): List<T>? {
    val byId = rows.associateBy { keyAndParent(it).first }
    val ordered = ArrayList<T>(rows.size)
    val done = HashSet<Long>()
    var remaining = rows
    while (remaining.isNotEmpty()) {
        var progress = false
        val next = ArrayList<T>()
        for (row in remaining) {
            val (id, parent) = keyAndParent(row)
            val ready = parent == null || parent !in byId || parent in done
            if (ready) {
                ordered += row
                done += id
                progress = true
            } else {
                next += row
            }
        }
        if (!progress) return null
        remaining = next
    }
    return ordered
}

// ---- row conversion: generated rows <-> manifest rows ------------------

private fun Categories.toRow() = CategoryRow(
    id = id,
    parentId = parent_id,
    nameEn = name_en,
    nameUk = name_uk,
    icon = icon,
    sortOrder = sort_order.toInt(),
    system = system_flag != 0L,
    section = section,
)

private fun Items.toRow() = ItemRow(
    id = id,
    subtypeId = subtype_id,
    name = name,
    notes = notes,
    price = price,
    purchaseDate = purchase_date,
    seasonFlags = season_flags.toInt(),
    sex = sex,
    rating = rating?.toInt(),
    archived = archived != 0L,
    createdAt = created_at,
    updatedAt = updated_at,
)

private fun Photos.toRow() = PhotoRow(id = id, itemId = item_id, path = path, position = position.toInt())

private fun Attribute_definitions.toRow() = AttributeDefinitionRow(
    id = id,
    categoryId = category_id,
    key = key,
    type = type,
    config = config,
    sortOrder = sort_order.toInt(),
)

private fun Attribute_values.toRow() = AttributeValueRow(itemId = item_id, definitionId = definition_id, value = value_)

private fun Tags.toRow() = TagRow(id = id, name = name)

private fun Item_tags.toRow() = ItemTagRow(itemId = item_id, tagId = tag_id)

private fun Outfits.toRow() = OutfitRow(
    id = id,
    name = name,
    rating = rating?.toInt(),
    createdAt = created_at,
    note = note,
)

private fun Outfit_items.toRow() = OutfitItemRow(outfitId = outfit_id, itemId = item_id, slot = slot)

private fun Wear_log.toRow() = WearLogRow(
    id = id,
    date = date,
    outfitId = outfit_id,
    itemIds = decodeItemIds(item_ids),
    tempC = temp_c,
    note = note,
)

private fun Packing_lists.toRow() = PackingListRow(
    id = id,
    title = title,
    dateFrom = date_from,
    dateTo = date_to,
    createdAt = created_at,
)

private fun Packing_items.toRow() = PackingItemRow(
    packingListId = packing_list_id,
    itemId = item_id,
    packed = packed != 0L,
)

private fun App_settings.toRow() = SettingRow(key = key, value = value_)

// ---- manifest rows / seed rows -> inserts (column order, positional) ----

private fun AppDatabase.insert(row: CategoryRow) = categoriesQueries.insertCategory(
    row.id, row.parentId, row.nameEn, row.nameUk, row.icon,
    row.sortOrder.toLong(), if (row.system) 1L else 0L, row.section,
)

private fun AppDatabase.insert(row: ItemRow) = itemsQueries.restoreItem(
    row.id, row.subtypeId, row.name, row.notes, row.price, row.purchaseDate,
    row.seasonFlags.toLong(), row.sex, row.rating?.toLong(), if (row.archived) 1L else 0L,
    row.createdAt, row.updatedAt,
)

private fun AppDatabase.insert(row: PhotoRow) = photosQueries.restorePhoto(
    row.id, row.itemId, row.path, row.position.toLong(),
)

private fun AppDatabase.insert(row: AttributeDefinitionRow) = attribute_definitionsQueries.restoreAttributeDefinition(
    row.id, row.categoryId, row.key, row.type, row.config, row.sortOrder.toLong(),
)

private fun AppDatabase.insert(row: AttributeValueRow) = attribute_valuesQueries.insertAttributeValue(
    row.itemId, row.definitionId, row.value,
)

private fun AppDatabase.insert(row: TagRow) = tagsQueries.restoreTag(row.id, row.name)

private fun AppDatabase.insert(row: ItemTagRow) = tagsQueries.insertItemTag(row.itemId, row.tagId)

private fun AppDatabase.insert(row: OutfitRow) = outfitsQueries.restoreOutfit(
    row.id, row.name, row.rating?.toLong(), row.createdAt, row.note,
)

private fun AppDatabase.insert(row: OutfitItemRow) = outfitsQueries.insertOutfitItem(
    row.outfitId, row.itemId, row.slot,
)

private fun AppDatabase.insert(row: WearLogRow) = wear_logQueries.restoreWearLogEntry(
    row.id, row.date, row.outfitId, encodeItemIds(row.itemIds), row.tempC, row.note,
)

private fun AppDatabase.insert(row: PackingListRow) = packingQueries.restorePackingList(
    row.id, row.title, row.dateFrom, row.dateTo, row.createdAt,
)

private fun AppDatabase.insert(row: PackingItemRow) = packingQueries.insertPackingItem(
    row.packingListId, row.itemId, if (row.packed) 1L else 0L,
)

private fun AppDatabase.insert(row: SettingRow) = app_settingsQueries.insertSetting(row.key, row.value)

private fun AppDatabase.insertCategory(row: Categories) = categoriesQueries.insertCategory(
    row.id, row.parent_id, row.name_en, row.name_uk, row.icon,
    row.sort_order, row.system_flag, row.section,
)

private fun AppDatabase.insertSubtype(row: Subtypes) = subtypesQueries.insertSubtype(
    row.id, row.category_id, row.key, row.name_en, row.name_uk, row.thermal_class,
)

private fun AppDatabase.insertColor(row: Colors) = colorsQueries.insertColor(row.id, row.key, row.name_uk, row.hex)

private fun AppDatabase.insertStyleTag(row: Style_tags) = style_tagsQueries.insertStyleTag(
    row.id, row.key, row.name_en, row.name_uk, row.sort_order,
)
