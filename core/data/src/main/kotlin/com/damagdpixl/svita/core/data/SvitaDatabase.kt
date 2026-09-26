package com.damagdpixl.svita.core.data

import app.cash.sqldelight.db.SqlDriver
import app.svita.core.data.db.AppDatabase

/**
 * Opens [AppDatabase] on a caller-supplied [SqlDriver].
 *
 * The driver choice (Android SQLite in production, JVM JDBC in tests, native
 * later on other platforms) stays entirely with the caller: this module never
 * references concrete driver classes.
 *
 * Creates the schema on first use with [AppDatabase.Schema.create].
 */
public fun createSvitaDatabase(driver: SqlDriver, createSchema: Boolean = false): AppDatabase {
    if (createSchema) {
        AppDatabase.Schema.create(driver)
    }
    // SQLite enforces foreign keys per connection, off by default.
    driver.execute(null, "PRAGMA foreign_keys = ON", 0)
    return AppDatabase(driver)
}
