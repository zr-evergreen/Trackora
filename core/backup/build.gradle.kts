plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

// The backup file format: plain Kotlin over java.util.zip, java.security and
// kotlinx-serialization. No Room, Hilt or DataStore, and nothing in the app
// depends on it yet: export and restore (later PRs) are what will.
android {
    namespace = "com.evergreen.trackora.backup"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    testOptions {
        // The instrumented-test APK otherwise targets minSdk, and Play Protect
        // refuses to install apps that old on a real phone.
        targetSdk = 36
    }

    // The frozen example backups and the code describing them are shared by
    // the JVM tests and the instrumented ones, so both check the same files:
    // once against the JVM's zip and java.time, once against Android's.
    sourceSets {
        getByName("test") {
            java.srcDir("src/sharedTest/java")
            resources.srcDir("fixtures")
        }
        getByName("androidTest") {
            java.srcDir("src/sharedTest/java")
            assets.srcDir("fixtures")
        }
    }
}

dependencies {
    // Backports java.time to API < 26 (minSdk is 24)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
