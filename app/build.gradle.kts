import com.android.build.api.variant.FilterConfiguration.FilterType
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.aboutlibraries)
}
android {
    namespace = "okoge.house.throttling_app"

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
        }
    }
    splits {
        abi {
            // fdroid フレーバーで有効化、gradle タスク名で判定
            isEnable = gradle.startParameter.taskRequests.any { task ->
                task.args.any { it.contains("Fdroid", ignoreCase = true) }
            }
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            isUniversalApk = false
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

val abiCodes = mapOf(
    "x86" to 1,
    "armeabi-v7a" to 2,
    "x86_64" to 3,
    "arm64-v8a" to 4,
)

androidComponents {
    onVariants { variant ->
        // fdroid フレーバーのみ ABI split の versionCode を付与
        if (variant.flavorName == "fdroid") {
            variant.outputs.forEach { output ->
                val abi = output.filters.find { it.filterType == FilterType.ABI }?.identifier
                if (abi != null) {
                    val abiCode = abiCodes[abi] ?: 0
                    output.versionCode.set((output.versionCode.get() ?: 0) * 10 + abiCode)
                }
            }
        }
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