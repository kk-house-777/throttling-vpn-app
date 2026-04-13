import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.aboutlibraries)
}

val localProperties = Properties()
rootProject.file("local.properties").let { file ->
    if (file.exists()) localProperties.load(file.inputStream())
}

android {
    namespace = "okoge.house.throttling_app"

    signingConfigs {
        create("fdroidRelease") {
            storeFile = file(localProperties.getProperty("FDROID_STORE_FILE", ""))
            storePassword = localProperties.getProperty("FDROID_STORE_PASSWORD", "")
            keyAlias = localProperties.getProperty("FDROID_KEY_ALIAS", "")
            keyPassword = localProperties.getProperty("FDROID_KEY_PASSWORD", "")
        }
    }

    defaultConfig {
        applicationId = "okoge.house.throttling_app"
        minSdk = 26
        targetSdk = 36
        compileSdk { version = release(36) }
        versionCode = 2
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        debug {
            isDebuggable = true
        }
        release {
            isDebuggable = false
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    flavorDimensions += "store"
    productFlavors {
        create("play") {
            dimension = "store"
            applicationIdSuffix = ".play"
        }
        create("fdroid") {
            dimension = "store"
            applicationIdSuffix = ".fdroid"
            signingConfig = signingConfigs.getByName("fdroidRelease")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

aboutLibraries {
    collect {
        configPath = file("../config")
    }
}

dependencies {
    implementation(files("libs/tun2socks.aar"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.aboutlibraries.compose.m3)
    implementation(libs.aboutlibraries.core)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}