import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Release signing: put the keystore details in medipote/keystore.properties (never committed),
 * or in environment variables for CI. Without them, the release build is produced unsigned.
 */
val signing = Properties().apply {
    val file = project.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
    System.getenv("MEDIPOTE_STORE_FILE")?.let { setProperty("storeFile", it) }
    System.getenv("MEDIPOTE_STORE_PASSWORD")?.let { setProperty("storePassword", it) }
    System.getenv("MEDIPOTE_KEY_ALIAS")?.let { setProperty("keyAlias", it) }
    System.getenv("MEDIPOTE_KEY_PASSWORD")?.let { setProperty("keyPassword", it) }
}
val hasSigning = listOf("storeFile", "storePassword", "keyAlias", "keyPassword").all { signing.getProperty(it) != null }

android {
    namespace = "com.medipote.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.medipote.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "1.2.0"
        manifestPlaceholders["appLabel"] = "@string/app_name"
    }

    // The app is in French: drop the other languages bundled by libraries (smaller download).
    androidResources {
        localeFilters += listOf("fr")
    }

    signingConfigs {
        if (hasSigning) {
            create("release") {
                storeFile = project.file(signing.getProperty("storeFile"))
                storePassword = signing.getProperty("storePassword")
                keyAlias = signing.getProperty("keyAlias")
                keyPassword = signing.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Installs next to the release version, with its own name.
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

    // The dependency report is only useful to the Play Store, which reads it from the bundle.
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.profileinstaller)
    // Glance pulls WorkManager 2.7.1, whose Room 2.2.5 keep rules predate R8 full mode: the release
    // build then lost WorkDatabase_Impl's constructor and crashed at launch. Use a current WorkManager.
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.glance.appwidget.testing)
}
