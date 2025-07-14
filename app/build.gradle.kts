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
        versionName = "1.0"
        minSdk = 31
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
            ndk {
                abiFilters.add("arm64-v8a")
            }
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    val room_version = "2.7.2"

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.navigation:navigation-fragment-ktx:2.9.1")
    implementation("androidx.navigation:navigation-ui-ktx:2.9.1")
    implementation("androidx.room:room-runtime:$room_version")
    // You already have room-common-jvm, but it's often pulled in by runtime/compiler
    implementation("androidx.room:room-common-jvm:$room_version")
    // Recommended for Kotlin projects for coroutines support and extensions
    implementation("androidx.room:room-ktx:$room_version")

    implementation(files("libs/PaddlePredictor.jar"))
    implementation(project(":opencv"))
    implementation("com.github.micycle1:Clipper2-java:1.3.1")

    ksp("androidx.room:room-compiler:$room_version")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    val composeBom = platform("androidx.compose:compose-bom:2025.05.00")

    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.graphics:graphics-shapes:1.0.1")
    // Choose one of the following:
    // Material Design 3
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core:1.7.8")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")

    // Android Studio Preview support
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("com.squareup.okhttp3:okhttp:4.12.0") // or latest

    // UI Tests
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.apache.lucene:lucene-analyzers-kuromoji:8.11.0")

    debugImplementation("androidx.compose.ui:ui-tooling:1.8.3")
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")

}