plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.vpegs.ms"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.vpegs.ms"
        minSdk = 29
        targetSdk = 35
        versionCode = 27
        versionName = "1.6.6"
        // Only real phone ABIs — cuts APK ~half (drop x86 emulator libs)
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        create("vpegs") {
            storeFile = file("../keystore/vpegs-debug.jks")
            storePassword = "vpegsdebug"
            keyAlias = "vpegs"
            keyPassword = "vpegsdebug"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("vpegs")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("vpegs")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
        jniLibs { useLegacyPackaging = true }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Xray core (AndroidLibXrayLite)
    implementation(files("libs/libv2ray.aar"))
    implementation(files("libs/hev-socks5-tunnel.aar"))
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
}

