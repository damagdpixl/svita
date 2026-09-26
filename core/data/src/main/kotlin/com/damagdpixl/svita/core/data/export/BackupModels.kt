package com.damagdpixl.svita.core.data.export

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The versioned manifest (`manifest.json`) inside a backup archive. Row types
 * mirror the raw database columns (dates as ISO strings, enums in their storage
 * encoding) so an export/import round trip is exact. The full wire contract is
 * documented in `docs/export_format.md`.
 */
@Serializable
public data class ExportManifest(
    /** Backup format version; refuse files with a newer one. */
    @SerialName("format_version") public val formatVersion: Int,
    /** Database schema version ([com.damagdpixl.svita.core.data.DataContracts.PERSISTENCE_VERSION]) at export time. */
    @SerialName("schema_version") public val schemaVersion: Int,
    /** ISO-8601 instant of the export. */
    @SerialName("exported_at") public val exportedAt: String,
    /** Application version that produced the file (diagnostics only). */
    @SerialName("app_version") public val appVersion: String,
    /** Taxonomy metadata. The seeded taxonomy itself is NOT exported as data — the importing app re-seeds it from its own resources. */
    public val taxonomy: TaxonomyMeta,
    /**
     * User-created categories only (`system_flag = 0`). Seeded categories,
     * subtypes, colors and style tags are re-seeded by the importing app.
     */
    public val categories: List<CategoryRow> = emptyList(),
    public val items: List<ItemRow> = emptyList(),
    public val photos: List<PhotoRow> = emptyList(),
    @SerialName("attribute_definitions") public val attributeDefinitions: List<AttributeDefinitionRow> = emptyList(),
    @SerialName("attribute_values") public val attributeValues: List<AttributeValueRow> = emptyList(),
    public val tags: List<TagRow> = emptyList(),
    @SerialName("item_tags") public val itemTags: List<ItemTagRow> = emptyList(),
    public val outfits: List<OutfitRow> = emptyList(),
    @SerialName("outfit_items") public val outfitItems: List<OutfitItemRow> = emptyList(),
    @SerialName("wear_log") public val wearLog: List<WearLogRow> = emptyList(),
    @SerialName("packing_lists") public val packingLists: List<PackingListRow> = emptyList(),
    @SerialName("packing_items") public val packingItems: List<PackingItemRow> = emptyList(),
    public val settings: List<SettingRow> = emptyList(),
)

/** Taxonomy metadata reference (see `docs/export_format.md`). */
@Serializable
public data class TaxonomyMeta(
    /** Version of the taxonomy seed the export was produced against. */
    @SerialName("seed_version") public val seedVersion: Int,
)

@Serializable
public data class CategoryRow(
    public val id: Long,
    @SerialName("parent_id") public val parentId: Long?,
    @SerialName("name_en") public val nameEn: String,
    @SerialName("name_uk") public val nameUk: String,
    public val icon: String?,
    @SerialName("sort_order") public val sortOrder: Int,
    /** Always `false`: seeded (system) categories are never exported. */
    public val system: Boolean,
    public val section: String,
)

@Serializable
public data class ItemRow(
    public val id: Long,
    @SerialName("subtype_id") public val subtypeId: Long,
    public val name: String,
    public val notes: String?,
    public val price: Double?,
    /** ISO date (`yyyy-MM-dd`) or null. */
    @SerialName("purchase_date") public val purchaseDate: String?,
    /** 4-bit season mask: 1 spring, 2 summer, 4 autumn, 8 winter. */
    @SerialName("season_flags") public val seasonFlags: Int,
    /** Storage encoding: `m`, `f`, `u` or null. */
    public val sex: String?,
    public val rating: Int?,
    public val archived: Boolean,
    @SerialName("created_at") public val createdAt: String,
    @SerialName("updated_at") public val updatedAt: String,
)

@Serializable
public data class PhotoRow(
    public val id: Long,
    @SerialName("item_id") public val itemId: Long,
    /** Opaque `photos.path` value; the bytes travel in the zip entry `photos/<id>`. */
    public val path: String,
    public val position: Int,
)

@Serializable
public data class AttributeDefinitionRow(
    public val id: Long,
    @SerialName("category_id") public val categoryId: Long?,
    public val key: String,
    /** Storage encoding: `text`, `number`, `enum`, `multi`, `color`. */
    public val type: String,
    /** Attribute config JSON as stored (enum/multi options, number bounds). */
    public val config: String?,
    @SerialName("sort_order") public val sortOrder: Int,
)

@Serializable
public data class AttributeValueRow(
    @SerialName("item_id") public val itemId: Long,
    @SerialName("definition_id") public val definitionId: Long,
    /** Raw stored value, validated at write time on the exporting device. */
    public val value: String,
)

@Serializable
public data class TagRow(
    public val id: Long,
    public val name: String,
)

@Serializable
public data class ItemTagRow(
    @SerialName("item_id") public val itemId: Long,
    @SerialName("tag_id") public val tagId: Long,
)

@Serializable
public data class OutfitRow(
    public val id: Long,
    public val name: String,
    public val rating: Int?,
    @SerialName("created_at") public val createdAt: String,
    public val note: String?,
)

@Serializable
public data class OutfitItemRow(
    @SerialName("outfit_id") public val outfitId: Long,
    @SerialName("item_id") public val itemId: Long,
    /** Section storage encoding, e.g. `body`, `legs`, `feet`. */
    public val slot: String,
)

@Serializable
public data class WearLogRow(
    public val id: Long,
    /** ISO date (`yyyy-MM-dd`). */
    public val date: String,
    @SerialName("outfit_id") public val outfitId: Long?,
    /** Item ids referenced by the entry (JSON array column, no foreign key). */
    @SerialName("item_ids") public val itemIds: List<Long>,
    @SerialName("temp_c") public val tempC: Double?,
    public val note: String?,
)

@Serializable
public data class PackingListRow(
    public val id: Long,
    public val title: String,
    @SerialName("date_from") public val dateFrom: String?,
    @SerialName("date_to") public val dateTo: String?,
    @SerialName("created_at") public val createdAt: String,
)

@Serializable
public data class PackingItemRow(
    @SerialName("packing_list_id") public val packingListId: Long,
    @SerialName("item_id") public val itemId: Long,
    public val packed: Boolean,
)

@Serializable
public data class SettingRow(
    public val key: String,
    public val value: String,
)

/** Import strategy: keep local rows on id collisions, or replace everything. */
public enum class ImportMode {
    /**
     * Insert-new, keep-existing: a manifest row is inserted only when its id
     * (or key/pair, per table) is not already present locally; otherwise the
     * local row survives and the manifest row is counted as skipped. Manifest
     * rows referencing rows that exist neither locally nor in the manifest are
     * dropped (orphans are never inserted). See `docs/export_format.md`.
     */
    MERGE,

    /**
     * Wipe-then-insert in one transaction: every user table AND the taxonomy
     * tables are emptied, the installed taxonomy seed is re-inserted verbatim,
     * then the manifest is restored. The manifest becomes the whole truth;
     * referential violations fail the import (typed
     * [ImportError.ReferentialIntegrity]) instead of dropping rows.
     */
    REPLACE,
}

/** Per-table outcome counters of an import. */
public data class TableReport(
    /** Rows written into the database. */
    public val inserted: Int = 0,
    /** Rows not written because the id/key/pair already existed locally (MERGE keeps the local row). */
    public val skipped: Int = 0,
    /** Rows not written because a referenced row does not exist anywhere (orphans; MERGE only). */
    public val dropped: Int = 0,
)

/** Result of a successful [BackupManager.importAll]. */
public data class ImportReport(
    public val mode: ImportMode,
    /** `exported_at` of the manifest that was imported. */
    public val exportedAt: String,
    /** Outcome per table (key = table name, see `docs/export_format.md`). */
    public val tables: Map<String, TableReport>,
    /** Photo files restored from the archive into the [FileStore]. */
    public val photosRestored: Int,
    /** Photo rows whose bytes are absent from the archive (row restored as-is). */
    public val photosMissingInArchive: Int,
)

/**
 * Typed import failure. Expected failure modes surface as subclasses of
 * [ImportError] — raw JSON/zip/SQL exceptions never escape
 * [BackupManager.importAll]; the database is left untouched (the write
 * transaction rolls back before the error is thrown).
 */
public sealed class ImportError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** The stream is not a ZIP at all (no ZIP signature) or becomes unreadable mid-scan. */
    public data object NotAnArchive : ImportError("backup is not a readable zip archive")

    /** The archive yielded no `manifest.json` — not a Svita backup, empty, or truncated. */
    public data object MissingManifest : ImportError("backup archive has no manifest.json")

    /**
     * A manifest photo row carries a path no [FileStore] could legally store
     * (empty, absolute, or escaping its root with a `..` segment). Checked
     * before anything is written, so a hostile backup cannot touch files.
     */
    public class InvalidPhotoPath(detail: String, cause: Throwable? = null) :
        ImportError("backup photo path is invalid: $detail", cause)

    /** `manifest.json` exists but is not a parseable v1 manifest. */
    public class MalformedManifest(detail: String, cause: Throwable? = null) :
        ImportError("backup manifest is malformed: $detail", cause)

    /** The file was written by a newer app: manifest format version too new. */
    public class UnsupportedFormatVersion(public val found: Int, public val supported: Int) :
        ImportError(
            "backup format version $found is newer than supported $supported; update the app to import this backup",
        )

    /** The file was exported from a newer database schema. */
    public class UnsupportedSchemaVersion(public val found: Int, public val supported: Int) :
        ImportError(
            "backup schema version $found is newer than supported $supported; update the app to import this backup",
        )

    /**
     * REPLACE import: a manifest row violates the schema's foreign keys against
     * the post-restore world (unknown subtype, dangling photo reference, ...)
     * and is refused instead of silently dropping data. MERGE never raises
     * this — it drops such rows and reports them.
     */
    public class ReferentialIntegrity(detail: String) :
        ImportError("backup violates referential integrity: $detail")

    /** The write transaction failed and was rolled back; the database is unchanged. */
    public class WriteFailed(detail: String, cause: Throwable) :
        ImportError("import failed and was rolled back: $detail", cause)
}
