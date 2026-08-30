import java.util.Properties

// Signing material is never hardcoded. Local builds: keystore.properties
// (gitignored). CI: env vars (KEY_STORE_PASSWORD, KEY_PASSWORD, ALIAS).
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun signingProp(key: String, env: String): String? =
    keystoreProps.getProperty(key) ?: System.getenv(env)

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
        // Lets test/androidTest variants resolve without picking a flavor
        missingDimensionStrategy("accel", "standard")
    }

    // standard: plain onnxruntime-android + NNAPI (~25 MB native libs)
    // qnn:      onnxruntime-android-qnn + Hexagon HTP (~210 MB native libs)
    flavorDimensions += "accel"
    productFlavors {
        create("standard") {
            dimension = "accel"
        }
        create("qnn") {
            dimension = "accel"
            applicationIdSuffix = ".qnn"
            versionNameSuffix = "-qnn"
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
    // Must match the NDK provisioned in CI (r27d = 27.2.12479018) so
    // stripReleaseDebugSymbols can find the strip tool.
    ndkVersion = "27.2.12479018"
    signingConfigs {
        create("release") {
            val storePass = signingProp("storePassword", "KEY_STORE_PASSWORD")
            val keyPass = signingProp("keyPassword", "KEY_PASSWORD")
            if (storePass != null && keyPass != null) {
                storeFile = file(signingProp("storeFile", "KEY_STORE_FILE") ?: "../release-key.jks")
                storePassword = storePass
                keyAlias = signingProp("keyAlias", "ALIAS")
                keyPassword = keyPass
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            setProguardFiles(
                listOf(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
                )
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
//    kotlinOptions {
//        jvmTarget = "21"
//    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.test.ext:junit-ktx:1.2.1")
    implementation("androidx.compose.ui:ui-tooling:1.8.3")
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

    implementation("org.opencv:opencv:4.12.0")
    implementation("com.github.micycle1:Clipper2-java:1.3.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.apache.lucene:lucene-analyzers-kuromoji:8.11.4")
    "standardImplementation"("com.microsoft.onnxruntime:onnxruntime-android:1.29.0")
    "qnnImplementation"("com.microsoft.onnxruntime:onnxruntime-android-qnn:1.29.0")
    //noinspection Aligned16KB
    implementation("ai.djl.huggingface:tokenizers:0.36.0") // HuggingFace tokenizers
    //noinspection Aligned16KB
    implementation("ai.djl.android:tokenizer-native:0.33.0")

    debugImplementation("androidx.compose.ui:ui-tooling:1.8.3")
//    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.xerial:sqlite-jdbc:3.53.4.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("edu.stanford.nlp:stanford-corenlp:4.5.10")
//    testImplementation("edu.stanford.nlp:stanford-corenlp:4.5.1:models")
//    testImplementation("edu.stanford.nlp:stanford-corenlp:4.5.1:pipeline")
    testImplementation("org.openpnp:opencv:4.9.0-0")
    testImplementation("ai.djl.huggingface:tokenizers:0.36.0") // HuggingFace tokenizers
    testImplementation("com.microsoft.onnxruntime:onnxruntime:1.29.0")
    testImplementation(kotlin("test"))
}