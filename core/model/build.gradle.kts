// Pure Kotlin, zero Android dependencies — KMP-ready: converting to KGP later is a plugin swap.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // Domain dates (LocalDate) and timestamps (Instant) are part of the model API.
    api(libs.kotlinx.datetime)
    testImplementation(kotlin("test"))
}
