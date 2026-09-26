package com.damagdpixl.svita.core.data

/**
 * Persistence contracts. Schema v1 lives in src/main/sqldelight, the generated
 * database class is app.svita.core.data.db.AppDatabase, opened via createSvitaDatabase()
 * with an injected SqlDriver.
 */
object DataContracts {
    const val PERSISTENCE_VERSION: Int = 1
}
