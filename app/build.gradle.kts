import org.jetbrains.kotlin.js.inline.clean.removeUnusedImports

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    kotlin("plugin.serialization")
}

android {
    namespace = "com.farhannz.kaitou"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.farhannz.kaitou"
        versionCode = 1
        versionName = "0.1.0"
        minSdk = 31
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }


    buildFeatures {
        compose = true
    }
    buildTypes {
        release {
//            isMinifyEnabled = true
//            isShrinkResources = true
//            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
//
//    ndk {
//        abiFilters 'armeabi-v7a', 'arm64-v8a'
//    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    val room = "2.7.2"
    val composeBom = platform("androidx.compose:compose-bom:2025.05.00")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.compose.material3:material3:1.3.2")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-core:1.7.8")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")

    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    implementation(files("libs/PaddlePredictor.jar"))
//    implementation(project(":opencv"))
    implementation("org.opencv:opencv:4.12.0")
    implementation("com.github.micycle1:Clipper2-java:1.3.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.apache.lucene:lucene-analyzers-kuromoji:8.11.0")

    debugImplementation("androidx.compose.ui:ui-tooling:1.8.3")
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}