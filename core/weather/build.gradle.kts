// Pure Kotlin, zero Android dependencies — same KMP-ready pattern as :core:engine.
// Network access exists ONLY behind the injected HttpTransport seam: unit tests
// always use fakes, so this module never performs real I/O under test.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // LocalDate for the public API + system-today resolution.
    implementation(libs.kotlinx.datetime)
    // suspend seams + Dispatchers.IO in the java.net transport.
    implementation(libs.kotlinx.coroutines.core)
    // Open-Meteo payload parsing + the bundled climate-norms asset.
    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}
