plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.navigation.safeargs)
}

android {
    namespace = "com.resq.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.resq.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    androidResources {
        // .onnx files must remain uncompressed for sherpa-onnx to read via openFd
        noCompress += listOf("onnx", "txt", "pmtiles")
    }
}

configurations.all {
    // sherpa-onnx publishes both an AAR and a JVM JAR; only the AAR is
    // needed on Android. Excluding the JVM artifact prevents duplicate-class errors.
    exclude(group = "com.github.k2-fsa.sherpa-onnx", module = "sherpa-onnx-jvm")
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.constraintlayout)

    // Material Design 3
    implementation(libs.material)

    // Lifecycle
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.common)

    // Navigation
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)

    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Recycler View
    implementation(libs.androidx.recyclerview)

    // Splash Screen
    implementation(libs.androidx.splashscreen)

    // Glide — image loading (IMAGE, GIF)
    implementation(libs.glide.core)
    ksp(libs.glide.compiler)

    // Media3 ExoPlayer — video playback
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)

    // Sherpa-ONNX — offline TTS, ASR, and VAD
    implementation(libs.sherpa.onnx)

    // MapLibre — offline vector maps (PMTiles)
    implementation(libs.maplibre.android)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}