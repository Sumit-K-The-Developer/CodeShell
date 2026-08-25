import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

android {
    namespace = "com.codeshell.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.codeshell.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField(
            "String",
            "CODESHELL_GROQ_API_KEY",
            "\"${localProperties.getProperty("CODESHELL_GROQ_API_KEY", "")}\"",
        )
        buildConfigField(
            "String",
            "GNEWS_API_KEY",
            "\"${localProperties.getProperty("GNEWS_API_KEY", "")}\"",
        )
        buildConfigField("Boolean", "ALLOW_DEMO_CAPTURE", "false")
    }

    flavorDimensions += "shell"
    productFlavors {
        create("coding") {
            dimension = "shell"
            buildConfigField("String", "SHELL_FLAVOR", "\"coding\"")
            buildConfigField("String", "LESSON_ASSET", "\"lessons/default.json\"")
            buildConfigField("String", "LESSON_REMOTE_DOC", "\"lessons_coding\"")
        }
        create("grammar") {
            dimension = "shell"
            buildConfigField("String", "SHELL_FLAVOR", "\"grammar\"")
            buildConfigField("String", "LESSON_ASSET", "\"lessons/default.json\"")
            buildConfigField("String", "LESSON_REMOTE_DOC", "\"lessons_grammar\"")
        }
        create("news") {
            dimension = "shell"
            buildConfigField("String", "SHELL_FLAVOR", "\"news\"")
            buildConfigField("String", "LESSON_ASSET", "\"lessons/default.json\"")
            buildConfigField("String", "LESSON_REMOTE_DOC", "\"lessons_news\"")
        }
    }

    buildTypes {
        debug {
            // Keep debug builds protected by FLAG_SECURE so the intentional
            // device-capture switch can be tested realistically.
            buildConfigField("Boolean", "ALLOW_DEMO_CAPTURE", "false")
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

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
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
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.analytics)
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.media3.effect)
    implementation(libs.stream.webrtc.android)
    implementation(libs.okhttp)
    implementation(libs.jsoup)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
