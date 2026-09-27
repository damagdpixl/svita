package com.damagdpixl.svita.data

import android.content.Context
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.svita.core.data.db.AppDatabase
import com.damagdpixl.svita.core.data.SvitaRepositories
import com.damagdpixl.svita.core.data.createSvitaDatabase
import com.damagdpixl.svita.core.engine.OutfitEngine
import com.damagdpixl.svita.core.engine.ThermalMapping
import com.damagdpixl.svita.core.engine.standardPalette
import com.damagdpixl.svita.core.weather.ClimateNormsAsset
import com.damagdpixl.svita.core.weather.ClimateNormsProvider
import com.damagdpixl.svita.core.weather.HttpTransport
import com.damagdpixl.svita.core.weather.HttpTransportResponse
import com.damagdpixl.svita.core.weather.InMemoryWeatherCache
import com.damagdpixl.svita.core.weather.JavaNetHttpTransport
import com.damagdpixl.svita.core.weather.OpenMeteoProvider
import com.damagdpixl.svita.core.weather.WeatherCache
import com.damagdpixl.svita.core.weather.WeatherFacade
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
 * Settings and constants of the Outfits/Calendar dress-me flow (P2 T6).
 */
object OutfitPrefs {
    /** Comfort slider (−1..+1), persisted; 0 = target equals the daily minimum. */
    const val COMFORT: String = "outfit.comfort"

    /**
     * The `wear_log.note` marker that turns an entry into a PLAN (P2 T6
     * contract): an entry with this note and `date >= today` is a planned
     * outfit; the same note on a past date reads as history. Entries without
     * the note are always logged wear.
     */
    const val PLAN_NOTE: String = "plan"

    /**
     * Dress-me location: Kyiv, matching the provenance of the bundled climate
     * norms table. Deliberately fixed — the app has no location permission and
     * must not gain one (privacy contract: no tracking); the forecast window
     * comes from Open-Meteo for these coordinates or degrades to the norms.
     */
    const val DRESS_ME_LAT: Double = 50.45
    const val DRESS_ME_LON: Double = 30.52
}

/**
 * Transport that never leaves the process: every request throws immediately.
 *
 * Used when the graph runs WITHOUT a database file (Robolectric tests): the
 * weather facade degrades to climate norms deterministically and the test
 * suite never touches a real network. Production builds get
 * [JavaNetHttpTransport] instead.
 */
private object NoNetworkTransport : HttpTransport {
    override suspend fun get(url: String): HttpTransportResponse {
        throw java.io.IOException("network disabled in this graph mode")
    }
}

/**
 * The manual dependency graph of the app (no DI framework, per task contract).
 *
 * Owns the database lifecycle: the Android SQLite driver is created here, the
 * SQLDelight schema is applied by the driver callback, and the seven
 * repositories are wired through the landed [SvitaRepositories] facade.
 *
 * P2 T6 additions, built once:
 * - [engine]: the dress-me engine over the STANDARD palette + thermal tables
 *   (mirrored seed data — identical constants to `taxonomy_seed.sq`, pinned by
 *   the `:core:engine` parity tests);
 * - [weather]/[weatherCache]: the never-throwing weather facade
 *   (cache → Open-Meteo → climate norms) and its cache, which the Outfits
 *   model also PROBES for provenance (a sample in the cache came from the
 *   network; nothing in the cache after `forDate` means the norms fallback
 *   answered and the UI shows the «офлайн» badge).
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
        /** Dress-me engine: pure, stateless, safe to share across screens. */
        val engine: OutfitEngine,
        /** Failure-tolerant dress-me weather (never throws, offline norms floor). */
        val weather: WeatherFacade,
        /** The cache behind [weather]; also the offline-badge provenance probe. */
        val weatherCache: WeatherCache,
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
     * builds an in-memory database — the Robolectric test mode, which also
     * disables the real network transport (see [NoNetworkTransport]).
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
        val transport: HttpTransport =
            if (databaseName == null) NoNetworkTransport else JavaNetHttpTransport()
        val weatherCache = InMemoryWeatherCache()
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
            engine = OutfitEngine(standardPalette(), ThermalMapping.standard()),
            weather = WeatherFacade(
                openMeteo = OpenMeteoProvider(transport),
                norms = ClimateNormsProvider(ClimateNormsAsset.loadBundled()),
                cache = weatherCache,
            ),
            weatherCache = weatherCache,
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
