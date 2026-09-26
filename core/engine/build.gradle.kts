// Pure Kotlin, zero Android dependencies — KMP-ready: converting to KGP later is a plugin swap.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core:model"))
    testImplementation(kotlin("test"))
    // JSON fixture parsing for the parity suite (JsonElement API only, no codegen plugin needed).
    testImplementation(libs.kotlinx.serialization.json)
}
