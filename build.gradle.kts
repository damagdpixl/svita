// Svita root build. Module configuration lives next to each module's sources.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.sqldelight) apply false
}

buildscript {
    // Gradle's embedded-Kotlin pinning (kotlin-stdlib/annotations on the plugin
    // classpath) conflicts with the versions AGP/KGP/SQLDelight actually need;
    // force the versions the real plugins request.
    configurations.classpath {
        resolutionStrategy {
            force("org.jetbrains.kotlin:kotlin-stdlib:2.2.21")
            force("org.jetbrains:annotations:23.0.0")
        }
    }
}
