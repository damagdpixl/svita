plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // Attribute definitions carry config JSON (enum/multi options, number bounds).
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.damagdpixl.svita"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.damagdpixl.svita"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { test ->
                test.maxHeapSize = "2g"
            }
        }
    }
}

// Unit tests gate the debug variant; release unit tests only duplicate them.
androidComponents {
    beforeVariants { variantBuilder ->
        if (variantBuilder.name == "release") {
            variantBuilder.unitTestEnabled = false
        }
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
    implementation(project(":core:engine"))
    implementation(project(":core:weather"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)

    // ViewModels: debounce/flatMapLatest over repository flows.
    implementation(libs.kotlinx.coroutines.core)
    // Domain dates surface in editor/detail state.
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)
    // The app owns the database lifecycle: AndroidSqliteDriver + reactive cover index.
    implementation(libs.sqldelight.android.driver)
    implementation(libs.sqldelight.coroutines)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.compose.ui.test.manifest)
}
