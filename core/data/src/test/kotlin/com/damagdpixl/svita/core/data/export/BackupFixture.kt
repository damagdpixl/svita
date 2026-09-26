package com.damagdpixl.svita.core.data.export

import app.svita.core.data.db.AppDatabase
import com.damagdpixl.svita.core.data.ItemDraft
import com.damagdpixl.svita.core.data.OutfitDraft
import com.damagdpixl.svita.core.data.RepositoriesFixture
import com.damagdpixl.svita.core.data.toDomain
import com.damagdpixl.svita.core.data.toPair
import com.damagdpixl.svita.core.model.AttributeDefinition
import com.damagdpixl.svita.core.model.AttributeEntry
import com.damagdpixl.svita.core.model.AttributeType
import com.damagdpixl.svita.core.model.Category
import com.damagdpixl.svita.core.model.Item
import com.damagdpixl.svita.core.model.Outfit
import com.damagdpixl.svita.core.model.OutfitEntry
import com.damagdpixl.svita.core.model.PackingEntry
import com.damagdpixl.svita.core.model.PackingList
import com.damagdpixl.svita.core.model.PaletteColor
import com.damagdpixl.svita.core.model.Photo
import com.damagdpixl.svita.core.model.Section
import com.damagdpixl.svita.core.model.Season
import com.damagdpixl.svita.core.model.Sex
import com.damagdpixl.svita.core.model.StyleTag
import com.damagdpixl.svita.core.model.Subtype
import com.damagdpixl.svita.core.model.Tag
import com.damagdpixl.svita.core.model.WearLogEntry
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json

/** Every table as domain rows — the deep-equality snapshot for round trips. */
internal data class FullSnapshot(
    val categories: List<Category>,
    val subtypes: List<Subtype>,
    val colors: List<PaletteColor>,
    val styleTags: List<StyleTag>,
    val items: List<Item>,
    val photos: List<Photo>,
    val definitions: List<AttributeDefinition>,
    val values: List<AttributeEntry>,
    val tags: List<Tag>,
    val itemTags: List<Pair<Long, Long>>,
    val outfits: List<Outfit>,
    val outfitEntries: List<OutfitEntry>,
    val wearLog: List<WearLogEntry>,
    val packingLists: List<PackingList>,
    val packingEntries: List<PackingEntry>,
    val settings: List<Pair<String, String>>,
)

/** The reminder clock is per-device by contract — excluded from comparisons. */
internal fun FullSnapshot.withoutReminder(): FullSnapshot = copy(
    settings = settings.filterNot { it.first == BackupManager.LAST_EXPORT_SETTING_KEY },
)

internal fun fullSnapshot(db: AppDatabase): FullSnapshot = FullSnapshot(
    categories = db.categoriesQueries.selectAllCategories().executeAsList().map { it.toDomain() },
    subtypes = db.subtypesQueries.selectAllSubtypes().executeAsList().map { it.toDomain() },
    colors = db.colorsQueries.selectAllColors().executeAsList().map { it.toDomain() },
    styleTags = db.style_tagsQueries.selectAllStyleTags().executeAsList().map { it.toDomain() },
    items = db.itemsQueries.selectAllItems().executeAsList().map { it.toDomain() },
    photos = db.photosQueries.selectAllPhotos().executeAsList().map { it.toDomain() },
    definitions = db.attribute_definitionsQueries.selectAllAttributeDefinitions()
        .executeAsList().map { it.toDomain() },
    values = db.attribute_valuesQueries.selectAllAttributeValues().executeAsList().map { it.toDomain() },
    tags = db.tagsQueries.selectAllTags().executeAsList().map { it.toDomain() },
    itemTags = db.tagsQueries.selectAllItemTags().executeAsList().map { it.item_id to it.tag_id },
    outfits = db.outfitsQueries.selectAllOutfits().executeAsList().map { it.toDomain() },
    outfitEntries = db.outfitsQueries.selectAllOutfitItems().executeAsList().map { it.toDomain() },
    wearLog = db.wear_logQueries.selectAllWearLog().executeAsList().map { it.toDomain() },
    packingLists = db.packingQueries.selectAllPackingLists().executeAsList().map { it.toDomain() },
    packingEntries = db.packingQueries.selectAllPackingItems().executeAsList().map { it.toDomain() },
    settings = db.app_settingsQueries.selectAllSettings().executeAsList().map { it.toPair() },
)

/** Ids produced by [BackupFixture.seedRichData]. */
internal class SeedData(
    val item1: Long,
    val item2: Long,
    val outfit: Long,
    val wearWithOutfit: Long,
    val wearWithoutOutfit: Long,
    val packingList: Long,
    val tagSummer: Long,
    val tagFavorite: Long,
    val colorDef: Long,
    val brandDef: Long,
    val sizeDef: Long,
)

/**
 * Two independent in-memory databases, each with its own [DirectoryFileStore]
 * and [BackupManager], plus a shared backup file. Rich seed data covers every
 * user table so round trips are exercised end to end.
 */
internal class BackupFixture {
    val a = RepositoriesFixture()
    val b = RepositoriesFixture()

    private val tmpRoot: Path = Files.createTempDirectory("svita-backup")
    val rootA: File = tmpRoot.resolve("photos-a").toFile()
    val rootB: File = tmpRoot.resolve("photos-b").toFile()
    val storeA = DirectoryFileStore(rootA)
    val storeB = DirectoryFileStore(rootB)

    val managerA = BackupManager(a.db, storeA, appVersion = "test-1.0")
    val managerB = BackupManager(b.db, storeB, appVersion = "test-1.0")

    val backupFile: File = tmpRoot.resolve("backup.zip").toFile()

    fun cleanup() {
        tmpRoot.toFile().deleteRecursively()
    }

    fun sink(): FileSink {
        val stream = FileOutputStream(backupFile)
        return object : FileSink {
            override fun outputStream(): OutputStream = stream
            override fun close() = stream.close()
        }
    }

    fun source(): FileSource {
        val stream = FileInputStream(backupFile)
        return object : FileSource {
            override fun inputStream() = stream
            override fun close() = stream.close()
        }
    }

    fun sourceOf(bytes: ByteArray): FileSource {
        val stream = ByteArrayInputStream(bytes)
        return object : FileSource {
            override fun inputStream() = stream
            override fun close() = stream.close()
        }
    }

    fun textSink(file: File): TextSink {
        val writer = OutputStreamWriter(FileOutputStream(file), Charsets.UTF_8)
        return object : TextSink {
            override fun writer() = writer
            override fun close() = writer.close()
        }
    }

    fun photoBytes(root: File, path: String): ByteArray = root.resolve(path).readBytes()

    /**
     * Rich wardrobe on [a]: two items (one archived) with photos, tags,
     * attribute values, an outfit with placements, two wear-log entries (with
     * and without an outfit), a packing list, settings and one user-created
     * category (system_flag = 0) that must survive REPLACE as an override.
     */
    fun seedRichData(): SeedData = runBlocking {
        val tagSummer = a.wardrobe.createTag("літнє")
        val tagFavorite = a.wardrobe.createTag("улюблене")
        val colorDef = a.attributes.createDefinition(null, "color", AttributeType.COLOR, null, 0)
        val brandDef = a.attributes.createDefinition(null, "brand", AttributeType.TEXT, null, 1)
        val sizeDef = a.attributes.createDefinition(a.catTops, "top_size", AttributeType.TEXT, null, 2)

        val item1 = a.wardrobe.createItem(
            draft = ItemDraft(
                subtypeId = a.subtypeIdOf(a.subTShirt),
                name = "Біла футболка",
                notes = "любима, з \"лапками\",\nдругий рядок",
                price = 299.99,
                purchaseDate = LocalDate.parse("2025-06-01"),
                seasons = setOf(Season.SPRING, Season.SUMMER),
                sex = Sex.FEMALE,
                rating = 5,
            ),
            photos = listOf("img/one.jpg", "img/two.jpg"),
            tagIds = setOf(tagSummer, tagFavorite),
            attributeValues = mapOf(colorDef to "#FFFFFF", brandDef to "Nike", sizeDef to "M"),
        )
        val item2 = a.wardrobe.createItem(
            draft = ItemDraft(
                subtypeId = a.subtypeIdOf(a.subJeans),
                name = "Сині джинси",
                seasons = setOf(Season.AUTUMN, Season.WINTER),
                sex = Sex.UNISEX,
                archived = true,
            ),
            photos = listOf("img/three.jpg"),
        )

        val outfit = a.outfits.createOutfit(
            draft = OutfitDraft("Прогулянка", rating = 4, note = "літній день"),
            entries = listOf(
                OutfitEntry(0L, item1, Section.BODY),
                OutfitEntry(0L, item2, Section.LEGS),
            ),
        )
        val wearWithOutfit = a.wearLog.addEntry(
            date = LocalDate.parse("2025-06-02"),
            outfitId = outfit,
            itemIds = listOf(item1, item2),
            tempC = 22.5,
            note = "спекотно",
        )
        val wearWithoutOutfit = a.wearLog.addEntry(
            date = LocalDate.parse("2025-06-05"),
            outfitId = null,
            itemIds = listOf(item1),
            tempC = 18.0,
            note = null,
        )

        val packingList = a.packing.createPackingList(
            title = "Виїзд",
            dateFrom = LocalDate.parse("2025-07-01"),
            dateTo = LocalDate.parse("2025-07-10"),
            itemIds = listOf(item1),
        )
        a.packing.setPacked(packingList, item1, true)

        a.settings.putString("units", "metric")
        a.settings.putInt("reminder_days", 7)

        // A user-created taxonomy override (system_flag = 0) — no repository
        // API creates these in v1, so it is written at the query level.
        a.db.categoriesQueries.insertCategory(
            9001L, a.catTops, "Капюшони", "Капюшони", "custom_hood", 500L, 0L, "body",
        )

        rootA.resolve("img").mkdirs()
        File(rootA, "img/one.jpg").writeBytes(byteArrayOf(1, 2, 3, 4))
        File(rootA, "img/two.jpg").writeBytes(byteArrayOf(9, 8, 7))
        File(rootA, "img/three.jpg").writeBytes("jeans-photo".encodeToByteArray())

        SeedData(
            item1 = item1,
            item2 = item2,
            outfit = outfit,
            wearWithOutfit = wearWithOutfit,
            wearWithoutOutfit = wearWithoutOutfit,
            packingList = packingList,
            tagSummer = tagSummer,
            tagFavorite = tagFavorite,
            colorDef = colorDef,
            brandDef = brandDef,
            sizeDef = sizeDef,
        )
    }
}

// ---- zip helpers for crafted-manifest tests -----------------------------

private val testJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

/** Reads the manifest of a backup file produced by the exporter. */
internal fun readManifest(file: File): ExportManifest {
    ZipFile(file).use { zip ->
        val entry = zip.getEntry(BackupManager.MANIFEST_ENTRY)
            ?: throw AssertionError("no manifest in archive")
        return testJson.decodeFromString(
            ExportManifest.serializer(),
            zip.getInputStream(entry).readBytes().decodeToString(),
        )
    }
}

/** Reads all photo entries of a backup file keyed by photo row id. */
internal fun readPhotoEntries(file: File): Map<Long, ByteArray> {
    val photos = LinkedHashMap<Long, ByteArray>()
    ZipInputStream(file.inputStream().buffered()).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            val id = Regex("^photos/(\\d+)").find(entry.name)?.groupValues?.get(1)?.toLongOrNull()
            if (id != null) photos[id] = zip.readBytes()
            zip.closeEntry()
        }
    }
    return photos
}

/** Serializes a (possibly mutated) manifest plus photo bytes into a fresh archive. */
internal fun zipOf(manifest: ExportManifest, photos: Map<Long, ByteArray> = emptyMap()): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        zip.putNextEntry(ZipEntry(BackupManager.MANIFEST_ENTRY))
        zip.write(testJson.encodeToString(ExportManifest.serializer(), manifest).encodeToByteArray())
        zip.closeEntry()
        photos.forEach { (id, bytes) ->
            zip.putNextEntry(ZipEntry(BackupManager.photoEntryName(id)))
            zip.write(bytes)
            zip.closeEntry()
        }
    }
    return out.toByteArray()
}

/** A zip archive that is valid but carries no manifest. */
internal fun zipWithoutManifest(): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        zip.putNextEntry(ZipEntry("photos/9"))
        zip.write(byteArrayOf(1))
        zip.closeEntry()
    }
    return out.toByteArray()
}

internal inline fun <reified T : Throwable> assertThrows(block: () -> Unit): T {
    try {
        block()
    } catch (e: Throwable) {
        if (e is T) return e
        throw e
    }
    throw AssertionError("expected ${T::class.java.name} to be thrown")
}

// ---- RFC 4180 parser (test side of the CSV contract) --------------------

internal fun parseCsv(text: String): List<List<String>> {
    val rows = ArrayList<List<String>>()
    val field = StringBuilder()
    var row = ArrayList<String>()
    var inQuotes = false
    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            inQuotes -> when {
                c == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                    field.append('"')
                    i++
                }

                c == '"' -> inQuotes = false
                else -> field.append(c)
            }

            c == '"' -> inQuotes = true
            c == ',' -> {
                row.add(field.toString())
                field.setLength(0)
            }

            c == '\r' -> {
                if (i + 1 < text.length && text[i + 1] == '\n') i++
                row.add(field.toString())
                rows.add(row)
                row = ArrayList()
                field.setLength(0)
            }

            c == '\n' -> {
                row.add(field.toString())
                rows.add(row)
                row = ArrayList()
                field.setLength(0)
            }

            else -> field.append(c)
        }
        i++
    }
    if (field.isNotEmpty() || row.isNotEmpty()) {
        row.add(field.toString())
        rows.add(row)
    }
    return rows
}
