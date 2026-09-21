import com.android.build.api.variant.FilterConfiguration
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Version bands (mpvRx-style): stable occupies the top of its band, preview
// uses the next band offset by commit count, so the upgrade path
// Stable -> Preview -> newer Preview -> next Stable always increases.
// Bump releaseVersionCode with every stable release.
val releaseVersionCode = 1
val versionCodeBandSize = 10_000
val stableVersionCode = releaseVersionCode * versionCodeBandSize + (versionCodeBandSize - 1)
val previewVersionCode =
    (releaseVersionCode + 1) * versionCodeBandSize +
        (getCommitCount().toIntOrNull() ?: 1).coerceIn(1, versionCodeBandSize - 2)

// Release signing is configured via a git-ignored keystore.properties at the repo
// root. When it's absent (e.g. fresh clone / CI without secrets), release builds
// are simply left unsigned and debug builds are unaffected.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        FileInputStream(keystorePropertiesFile).use { load(it) }
    }
}

android {
    namespace = "com.bunko.reader"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.bunko.reader"
        minSdk = 26
        targetSdk = 36
        versionCode = stableVersionCode
        versionName = "0.23"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GIT_SHA", "\"${getCommitSha()}\"")
        buildConfigField("int", "GIT_COUNT", getCommitCount())
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    buildTypes {
        release {
            buildConfigField("boolean", "IS_PREVIEW_BUILD", "false")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                signingConfig = signingConfigs.getByName("debug")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            buildConfigField("boolean", "IS_PREVIEW_BUILD", "false")
        }
        create("preview") {
            initWith(getByName("release"))
            signingConfig = null
            buildConfigField("boolean", "IS_PREVIEW_BUILD", "true")
            versionNameSuffix = "-beta.r${getCommitCount()}"
        }
    }


    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }

    // Per-ABI version codes (mpvRx-style): keeps every split output on a
    // distinct, ordered code within its channel band.
    androidComponents {
        val abiCodes = mapOf(
            "universal" to 0,
            "armeabi-v7a" to 1,
            "arm64-v8a" to 2,
            "x86" to 3,
            "x86_64" to 4
        )
        onVariants { variant ->
            val channelVersionCode =
                if (variant.buildType == "preview") previewVersionCode
                else (variant.outputs.mapNotNull { it.versionCode.getOrNull() }.firstOrNull() ?: stableVersionCode)
            variant.outputs.forEach { output ->
                val abi = output.filters
                    .find { it.filterType == FilterConfiguration.FilterType.ABI }
                    ?.identifier
                output.versionCode.set(channelVersionCode * 10 + (abiCodes[abi] ?: 0))
            }
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.junrar)
    implementation(libs.commons.compress)
    implementation(libs.tukaani.xz)
    implementation(libs.jsoup)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.leakcanary.android)
}

// ---------------- Git helpers ----------------

fun getCommitCount(): String = runCommand("git rev-list --count HEAD") ?: "0"

fun getCommitSha(): String = runCommand("git rev-parse --short HEAD") ?: "unknown"

fun runCommand(command: String): String? =
    try {
        val parts = command.split(' ')
        val process = ProcessBuilder(parts)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()
        output.ifBlank { null }
    } catch (_: Exception) {
        null
    }
