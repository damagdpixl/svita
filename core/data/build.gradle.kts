// Android library module with the SQLDelight persistence core: schema v1 lives in
// src/main/sqldelight/app/svita/core/data, generated database class is
// app.svita.core.data.db.AppDatabase. The SqlDriver is always injected
// (see createSvitaDatabase) — no driver classes are hardcoded here.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.sqldelight)
}

sqldelight {
    databases {
        create("AppDatabase") {
            packageName.set("app.svita.core.data.db")
            // Schema snapshots are committed so migrations can be verified
            // against the v1 baseline.
            schemaOutputDirectory.set(file("sqldelight/schemas"))
        }
    }
}

android {
    namespace = "com.damagdpixl.svita.core.data"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.kotlinx.datetime)
    implementation(libs.sqldelight.android.driver)

    testImplementation(libs.junit)
    // Plain-JVM JDBC driver backed by sqlite-jdbc for in-memory smoke tests.
    testImplementation(libs.sqldelight.sqlite.driver)
}
