# Svita backup export format — schema v1

This document specifies the backup archive produced by `BackupManager.exportAll`
(module `:core:data`, package `com.damagdpixl.svita.core.data.export`) and
consumed by `BackupManager.importAll`. The format version in this document is
**1** (`BackupManager.BACKUP_FORMAT_VERSION`).

Design goals: full data ownership (one file contains everything the user
created), exact round trip (a REPLACE import reproduces the exported state),
typed and safe import (no raw exceptions, whole import transactional), and
privacy (the archive contains nothing beyond the database content and the
user's own photo files — no identifiers, no telemetry, no timestamps beyond the
data itself plus the export moment).

## 1. Archive layout

A backup is a ZIP archive (deflate or stored entries) with exactly one
convention:

- `manifest.json` — the versioned manifest, **always the first zip entry**.
  The importer reads the manifest wherever it occurs, but photo entries are
  streamed in one pass, so an archive whose manifest is not first can only be
  imported if its photo entries fit the importer's small pre-manifest buffer;
  only Svita's own exporter (manifest first) is a supported producer.
- `photos/<photoRowId>` — one entry per photo row whose binary was readable at
  export time. `<photoRowId>` is the numeric `photos.id` of the row (the
  importer matches entries by numeric-id prefix, tolerating an extension).
  Photo rows whose file was missing on disk are exported **without** a binary
  entry; import restores the row as-is and reports it in
  `photosMissingInArchive`.

## 2. Manifest (`manifest.json`)

UTF-8 JSON, pretty-printed, generated with kotlinx.serialization. Unknown
fields are ignored on import (forward compatibility within one format version).

| Field | Type | Meaning |
|---|---|---|
| `format_version` | int | Archive format version. Import refuses files with a version greater than the app's supported one (`UnsupportedFormatVersion`). |
| `schema_version` | int | Database schema version at export time. Import refuses files with a version greater than the installed schema (`UnsupportedSchemaVersion`). |
| `exported_at` | string | ISO-8601 instant of the export. |
| `app_version` | string | App version that produced the file; diagnostics only, never validated. |
| `taxonomy` | object | `{ "seed_version": <int> }` — the taxonomy seed version the export was produced against. **Metadata reference only.** |
| `categories` | array | User-created categories (`system_flag = 0`) **only**. |
| `items` | array | All item rows. |
| `photos` | array | All photo rows (including rows whose binary is absent from the archive). |
| `attribute_definitions` | array | All attribute definitions. |
| `attribute_values` | array | All attribute values (raw stored form). |
| `tags` | array | All user tags. |
| `item_tags` | array | Item↔tag link rows. |
| `outfits` | array | All outfits. |
| `outfit_items` | array | Outfit placement rows. |
| `wear_log` | array | All wear-log entries. |
| `packing_lists` | array | All packing lists. |
| `packing_items` | array | Packing list entry rows. |
| `settings` | array | All `app_settings` key/value rows. |

### 2.1 Taxonomy is metadata, not data

The seeded taxonomy (categories with `system_flag = 1`, subtypes, colors,
style tags) ships inside the app and is created by the schema seed. It is
**not exported as data**: the manifest carries only the `taxonomy.seed_version`
reference, and the importing app keeps (REPLACE) or keeps-or-merges (MERGE) its
own installed seed. Row ids of the seed are stable (1000+), so item/subtype
references survive across devices of the same schema version.

User-created categories (`system_flag = 0`) ARE exported in `categories` and
restored; their `system` field is always `false`.

### 2.2 Row encodings

Row types mirror the raw database columns — the manifest stores what SQLite
stores, so the round trip is exact:

- dates/timestamps: ISO strings exactly as stored (`purchase_date`
  `yyyy-MM-dd`; `created_at`/`updated_at` ISO instants);
- `sex`: storage encoding `m` / `f` / `u` or null;
- `season_flags`: 4-bit mask (1 spring, 2 summer, 4 autumn, 8 winter);
- booleans (`archived`, `packed`, `system`): JSON booleans (converted from the
  0/1 column at export);
- `wear_log.item_ids`: JSON array of item ids (decoded from the stored JSON
  column; re-encoded canonically on import);
- attribute `config` / `value`: raw stored strings (the exporter does not
  re-validate; values were validated by `AttributesRepository` at write time).

## 3. Import

`importAll(source, mode)` runs **one database transaction**. Any failure —
typed `ImportError` below, or a storage error — rolls the transaction back and
leaves the database untouched. The importer performs no schema migration: a
manifest whose `schema_version` is newer than the installed schema is refused.

Import never lets a raw JSON/zip/SQL exception escape. Typed errors
(`ImportError` subclasses):

| Error | When |
|---|---|
| `NotAnArchive` | The stream is not a ZIP at all (no ZIP signature, garbage) or becomes unreadable mid-scan (stream error). |
| `MissingManifest` | The archive yielded no `manifest.json` entry — not a Svita backup, an empty archive, or truncated (ZIP streams report EOF inside an entry as a quiet end of data, so truncation surfaces here). |
| `InvalidPhotoPath` | A manifest photo row's `path` is one no `FileStore` could legally store: empty, absolute (leading `/`), or escaping its root with a `..` segment. Checked before anything is written, so a hostile backup can neither touch files nor crash a store mid-scan; the store's own refusal (defense in depth) maps to the same typed error. |
| `MalformedManifest` | `manifest.json` is not a parseable v1 manifest (bad JSON, negative/zero version numbers, wrong shape). |
| `UnsupportedFormatVersion` | `format_version` newer than supported. |
| `UnsupportedSchemaVersion` | `schema_version` newer than the installed schema. |
| `ReferentialIntegrity` | REPLACE only: a manifest row violates the schema's foreign keys against the post-restore world. The import refuses instead of silently dropping data. |
| `WriteFailed` | The write transaction failed (e.g. SQL constraint); rolled back, cause attached. |

### 3.1 MERGE — insert-new, keep-existing

Semantics are **per table**: a manifest row is written only when its identity
is free locally. Otherwise the local row survives unchanged (skipped). Rows
referencing rows that exist neither locally nor in the manifest are orphans and
are dropped (never inserted; counted as `dropped`).

| Table | Identity (collision ⇒ skipped) | Orphan (⇒ dropped) |
|---|---|---|
| `categories` | `id` | `parent_id` resolves to neither the local DB nor an imported category (cycles count as orphans) |
| `items` | `id` | `subtype_id` not in the local (seeded) taxonomy |
| `photos` | `id` | `item_id` missing after the items step |
| `attribute_definitions` | `id` | `category_id` missing after the categories step |
| `attribute_values` | pair `(item_id, definition_id)` | either side missing |
| `tags` | `id`, **or** `name` (UNIQUE) held by a local tag with a different id | — |
| `item_tags` | pair `(item_id, tag_id)` | either side missing |
| `outfits` | `id` | — |
| `outfit_items` | pair `(outfit_id, item_id)` | either side missing |
| `wear_log` | `id` | — (see below) |
| `packing_lists` | `id` | — |
| `packing_items` | pair `(packing_list_id, item_id)` | either side missing |
| `settings` | `key` | — |

`wear_log` special cases (matching the column's own semantics and the
scrubbing `WardrobeRepository.deleteItem` performs):

- `outfit_id` referencing an outfit that did not survive the merge imports as
  `null` (the column is `ON DELETE SET NULL`), counted as inserted;
- `item_ids` are scrubbed to ids that exist after the merge (the JSON column is
  invisible to foreign keys); an entry left with an empty list is kept.

Because identity is per table, child rows of a kept (skipped) parent can still
merge into it when their own ids are free locally — e.g. a new photo of an
existing item is attached to that item.

### 3.2 REPLACE — wipe, re-seed, restore

The manifest becomes the whole truth:

1. All user tables **and** the taxonomy tables are emptied (children before
   parents; the import transaction keeps foreign keys satisfied throughout).
2. The **currently installed taxonomy seed is re-inserted verbatim** — the rows
   captured in step 1 (identical to a fresh seed for the same schema version).
   This includes categories, subtypes, colors and style tags. Manifest
   categories (user-created overrides) are then inserted parents-first.
3. The manifest is restored in foreign-key order (items → photos →
   definitions → values → tags → links → outfits → placements → wear log →
   packing → settings).
4. Referential violations are **refused** (`ReferentialIntegrity`), not
   dropped: unknown `subtype_id`, dangling photo/definition/link references,
   duplicate ids/names/pairs/keys inside the manifest, a user category id that
   collides with a seeded id. Two exceptions match column semantics:
   `wear_log.outfit_id` referencing a missing outfit becomes `null`, and
   `wear_log.item_ids` are scrubbed to the restored item ids.
5. Restoring explicit ids into `AUTOINCREMENT` tables bumps their sequence past
   the restored ids, so ids generated after an import never collide.

After the database transaction commits, the photo binaries found in the archive
are written into the `FileStore` under each restored photo row's `path`
(restoring a missing binary is reported, not fatal). A failure between the two
steps can leave orphan photo files — harmless; the database references only
restored rows.

### 3.3 Backup-reminder clock

`app_settings` key `backup.last_export_at` (ISO-8601 instant):

- written by a successful `exportAll` and by a successful `importAll` (fresh
  data starts a fresh reminder cycle; the manifest's own value for the key
  describes another device and is never adopted);
- read by `lastExportAt()` (`null` when never); an unparseable stored value
  reads as `null`.

## 4. CSV export (`exportItemsCsv`)

RFC 4180: comma-separated, CRLF line endings, fields containing comma, quote,
CR or LF wrapped in double quotes with inner quotes doubled. UTF-8. Headers in
English; rows ordered by item id. Columns:

```
id,name,subtype_key,notes,price,purchase_date,seasons,sex,rating,archived,created_at,updated_at,attr.<key>[,...]
```

- **Formula guard:** every data field whose first character is `=`, `+`, `-`,
  `@`, TAB or CR gets a `'` prefix — the standard defense against CSV formula
  injection (Excel/LibreOffice would otherwise execute such values). This
  includes legitimate negative numbers, which load as text in spreadsheet
  applications; all other values are emitted as stored, without reformatting.
- `subtype_key`: functional subtype id (e.g. `body.t-shirt`), stable across
  devices; category is derivable from it.
- `seasons`: season names joined with `;` (e.g. `spring;summer`), empty cell
  for an empty mask.
- `sex`: raw encoding (`m`/`f`/`u`, empty cell for null). `archived`: `1`/`0`.
- One `attr.<key>` column per attribute definition, ordered by the
  definitions' `sort_order, id`; an item without a value for a definition gets
  an empty cell. Duplicate definition keys are disambiguated as
  `attr.<key>#<definitionId>`.

The CSV is a reporting view; it is not an import format.

## 5. Limits

- The importer streams photo entries; only photo entries occurring before the
  manifest (non-standard archives) are buffered in memory.
- The archive is not encrypted; it contains the user's data verbatim and must
  be stored by the caller (UI layer) in a location the user controls.
