package com.damagdpixl.svita.data

import android.content.Context
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.svita.core.data.db.AppDatabase
import com.damagdpixl.svita.core.data.SvitaRepositories
import com.damagdpixl.svita.core.data.createSvitaDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Settings keys used by the wardrobe UI layer. */
object WardrobePrefs {
    /** Set once the first-run wizard is completed or skipped. */
    const val ONBOARDING_DONE: String = "wardrobe.onboarding_done"

    /** Set when the user dismisses the «import from gallery» hint. */
    const val IMPORT_HINT_DISMISSED: String = "wardrobe.import_hint_dismissed"
}

/** Settings keys of the paper-doll avatar (Settings -> Аватар). */
object AvatarPrefs {
    /** Body silhouette: slim | regular | curvy (AvatarBodyType.id). */
    const val AVATAR_BODY: String = "avatar.body"

    /** Skin tone: light | medium | deep (AvatarSkinTone.id). */
    const val AVATAR_TONE: String = "avatar.tone"
}

/**
 * The manual dependency graph of the app (no DI framework, per task contract).
 *
 * Owns the database lifecycle: the Android SQLite driver is created here, the
 * SQLDelight schema is applied by the driver callback, and the seven
 * repositories are wired through the landed [SvitaRepositories] facade.
 *
 * One deliberate app-layer extension beyond the facade: the gallery grid needs
 * a BATCH cover-photo index (one reactive query for all items), which the
 * repository contract does not expose — it reads the generated `photosQueries`
 * of the same database the graph already owns. Reads stay typed, local and
 * reactive; the repositories' write path is untouched.
 */
object SvitaGraph {

    class Graph internal constructor(
        val db: AppDatabase,
        val repos: SvitaRepositories,
        val photoStore: PhotoStore,
        /** itemId -> cover photo path (lowest position), re-emitted on any photo change. */
        val coverPhotos: Flow<Map<Long, String>>,
        /**
         * How many items the last bulk import created («N речей додано»), or
         * null. The import screen writes it right before navigating back; the
         * wardrobe grid shows the StickerBadge summary until dismissed. A tiny
         * cross-screen signal the graph owns — navigation arguments would be
         * overkill for one nullable Int.
         */
        val importSummary: MutableStateFlow<Int?> = MutableStateFlow(null),
    )

    @Volatile
    private var graph: Graph? = null

    fun get(): Graph = checkNotNull(graph) {
        "SvitaGraph.init(context) must run before the first screen is shown"
    }

    /**
     * Initializes (or returns) the process-wide graph. [databaseName] = null
     * builds an in-memory database — the Robolectric test mode.
     */
    @Synchronized
    fun init(context: Context, databaseName: String? = DATABASE_NAME): Graph {
        graph?.let { return it }
        val appContext = context.applicationContext
        val driver: SqlDriver = AndroidSqliteDriver(
            schema = AppDatabase.Schema,
            context = appContext,
            name = databaseName,
        )
        val db = createSvitaDatabase(driver, createSchema = false)
        val built = Graph(
            db = db,
            repos = SvitaRepositories(db),
            photoStore = PhotoStore(appContext),
            coverPhotos = db.photosQueries.selectAllPhotos()
                .asFlow()
                .mapToList(Dispatchers.IO)
                .map { rows ->
                    // Rows are ordered by (item_id, position, id): first row per
                    // item is the cover. Absorbed in a single pass.
                    val covers = LinkedHashMap<Long, String>()
                    for (row in rows) {
                        covers.putIfAbsent(row.item_id, row.path)
                    }
                    covers
                },
        )
        graph = built
        return built
    }

    /** Test seam: replaces the graph wholesale (call [resetForTests] after). */
    @Synchronized
    fun overrideForTests(replacement: Graph) {
        graph = replacement
    }

    @Synchronized
    fun resetForTests() {
        graph = null
    }

    private const val DATABASE_NAME: String = "svita.db"
}
