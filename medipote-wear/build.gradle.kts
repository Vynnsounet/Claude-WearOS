import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/**
 * The Wear OS companion of MédiPote. Same application id and same signing key as the phone app:
 * that is what lets the two talk to each other through the Wear data layer.
 * Signing details are read from medipote/keystore.properties (never committed) or the environment.
 */
val phoneDir = rootProject.file("medipote")
val signing = Properties().apply {
    val file = File(phoneDir, "keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
    System.getenv("MEDIPOTE_STORE_FILE")?.let { setProperty("storeFile", it) }
    System.getenv("MEDIPOTE_STORE_PASSWORD")?.let { setProperty("storePassword", it) }
    System.getenv("MEDIPOTE_KEY_ALIAS")?.let { setProperty("keyAlias", it) }
    System.getenv("MEDIPOTE_KEY_PASSWORD")?.let { setProperty("keyPassword", it) }
}
val hasSigning = listOf("storeFile", "storePassword", "keyAlias", "keyPassword").all { signing.getProperty(it) != null }

android {
    namespace = "com.medipote.wear"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.medipote.app"
        minSdk = 30 // Wear OS 3+
        targetSdk = 36
        // Same version name as the phone app; a distinct code, as the Play Store asks for watch builds.
        versionCode = 100028
        versionName = "1.15.0"
        manifestPlaceholders["appLabel"] = "MédiPote"
    }

    androidResources {
        localeFilters += listOf("fr")
    }

    signingConfigs {
        if (hasSigning) {
            create("release") {
                storeFile = File(phoneDir, signing.getProperty("storeFile"))
                storePassword = signing.getProperty("storePassword")
                keyAlias = signing.getProperty("keyAlias")
                keyPassword = signing.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Like the phone's debug build: the two must share their application id to talk.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            manifestPlaceholders["appLabel"] = "MédiPote (debug)"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasSigning) signingConfig = signingConfigs.getByName("release")
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/*.version",
                "META-INF/{AL2.0,LGPL2.1}",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json",
            )
        }
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":medipote-sync"))
    implementation(libs.play.services.wearable)
    // Play services depends on an old Fragment whose activities break today's permission prompts.
    implementation(libs.androidx.fragment)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.navigation)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
}
