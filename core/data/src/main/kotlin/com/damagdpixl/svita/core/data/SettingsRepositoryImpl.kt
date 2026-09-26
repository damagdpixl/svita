package com.damagdpixl.svita.core.data

import app.svita.core.data.db.AppDatabase
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

internal class SettingsRepositoryImpl(private val db: AppDatabase) : SettingsRepository {

    override fun observe(key: String): Flow<String?> =
        db.app_settingsQueries.selectSetting(key).asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .map { row -> row?.value_ }

    override suspend fun getString(key: String): String? = withContext(Dispatchers.IO) {
        db.app_settingsQueries.selectSetting(key).executeAsOneOrNull()?.value_
    }

    override suspend fun putString(key: String, value: String): Unit = withContext(Dispatchers.IO) {
        db.app_settingsQueries.insertSetting(key, value)
    }

    override suspend fun remove(key: String): Unit = withContext(Dispatchers.IO) {
        db.app_settingsQueries.deleteSetting(key)
    }

    override suspend fun getBoolean(key: String, default: Boolean): Boolean =
        getString(key)?.toBooleanStrictOrNull() ?: default

    override suspend fun putBoolean(key: String, value: Boolean) = putString(key, value.toString())

    override suspend fun getInt(key: String, default: Int): Int =
        getString(key)?.toIntOrNull() ?: default

    override suspend fun putInt(key: String, value: Int) = putString(key, value.toString())

    override suspend fun getLong(key: String, default: Long): Long =
        getString(key)?.toLongOrNull() ?: default

    override suspend fun putLong(key: String, value: Long) = putString(key, value.toString())

    override suspend fun getDouble(key: String, default: Double): Double =
        getString(key)?.toDoubleOrNull() ?: default

    override suspend fun putDouble(key: String, value: Double) = putString(key, value.toString())
}
