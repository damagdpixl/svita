package com.damagdpixl.svita.core.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.svita.core.data.db.AppDatabase
import com.damagdpixl.svita.core.model.AttributeType
import com.damagdpixl.svita.core.model.Season
import com.damagdpixl.svita.core.model.Sex
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Fresh in-memory database + all seven repositories per test instance.
 * The taxonomy seed ships with the schema, so seed ids (1000+) are stable
 * constants; repository-generated ids (items, tags, definitions) start at 1.
 */
internal class RepositoriesFixture {
    val driver: SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    val db: AppDatabase = createSvitaDatabase(driver, createSchema = true)

    val wardrobe: WardrobeRepository = WardrobeRepositoryImpl(db)
    val taxonomy: TaxonomyRepository = TaxonomyRepositoryImpl(db)
    val attributes: AttributesRepository = AttributesRepositoryImpl(db)
    val outfits: OutfitRepository = OutfitRepositoryImpl(db)
    val wearLog: WearLogRepository = WearLogRepositoryImpl(db)
    val packing: PackingRepository = PackingRepositoryImpl(db)
    val settings: SettingsRepository = SettingsRepositoryImpl(db)

    // Seeded taxonomy anchors.
    val catTops: Long = 1001L
    val catTrousers: Long = 1006L
    val subTShirt: String = "body.t-shirt"
    val subJeans: String = "legs.jeans"
    val subJacket: String = "body.jacket.denim"
    val subBoots: String = "feet.boots.combat"
    val subHat: String = "head.hats.cap"

    fun subtypeIdOf(key: String): Long = runBlocking {
        taxonomy.subtypeByKey(key)?.id ?: error("seed subtype $key must exist")
    }

    suspend fun createItem(
        name: String,
        subtypeKey: String,
        seasons: Set<Season> = Season.entries.toSet(),
        sex: Sex? = null,
        archived: Boolean = false,
        photos: List<String> = emptyList(),
        tagIds: Set<Long> = emptySet(),
        attributeValues: Map<Long, String> = emptyMap(),
        rating: Int? = null,
    ): Long = wardrobe.createItem(
        draft = ItemDraft(
            subtypeId = subtypeIdOf(subtypeKey),
            name = name,
            seasons = seasons,
            sex = sex,
            archived = archived,
            rating = rating,
        ),
        photos = photos,
        tagIds = tagIds,
        attributeValues = attributeValues,
    )

    suspend fun tagIdOf(name: String): Long = wardrobe.tagByName(name)?.id ?: wardrobe.createTag(name)

    suspend fun colorDefinitionId(key: String = "color"): Long =
        attributes.definitions().firstOrNull { it.key == key }?.id
            ?: attributes.createDefinition(null, key, AttributeType.COLOR, null, 0)
}

/**
 * Reactive-flow assertion helper: subscribes, waits for the first emission,
 * performs [action], returns the next emission. (A fresh `flow.first()` after
 * a write would only prove freshness, not that the flow re-emits on change.)
 */
internal suspend fun <T> Flow<T>.nextAfter(action: suspend () -> Unit): T = coroutineScope {
    val firstSeen = CompletableDeferred<Unit>()
    val next = CompletableDeferred<T>()
    val job = launch {
        var first = true
        collect { value ->
            if (first) {
                first = false
                firstSeen.complete(Unit)
            } else if (!next.isCompleted) {
                next.complete(value)
            }
        }
    }
    firstSeen.await()
    action()
    val result = next.await()
    job.cancel()
    result
}
