import java.util.Properties
import java.util.regex.Pattern

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

// Extract version constants from AppVersion.kt using regex (more reliable)
val versionFile = file("${projectDir}/src/main/java/com/evergreen/trackora/util/AppVersion.kt")
val versionFileContent = versionFile.readText()

fun extractVersionConstant(name: String): Int {
    val pattern = Pattern.compile("const val $name = (\\d+)")
    val matcher = pattern.matcher(versionFileContent)
    return if (matcher.find()) {
        matcher.group(1).toInt()
    } else {
        throw GradleException("Could not find $name constant in AppVersion.kt")
    }
}

val versionMajor = extractVersionConstant("VERSION_MAJOR")
val versionMinor = extractVersionConstant("VERSION_MINOR")
val versionPatch = extractVersionConstant("VERSION_PATCH")
val versionBuild = extractVersionConstant("VERSION_BUILD")

android {
    namespace = "com.evergreen.trackora"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.evergreen.trackora"
        minSdk = 24
        targetSdk = 36

        // Semantic versioning components from AppVersion.kt (single source of truth)
        // Construct version name: MAJOR.MINOR.PATCH
        versionName = "$versionMajor.$versionMinor.$versionPatch"

        // Version code: Increment for each release (uses VERSION_BUILD from AppVersion)
        versionCode = versionBuild

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Signing configuration (load from keystore.properties if exists)
    signingConfigs {
        val keystorePropertiesFile = rootProject.file("keystore.properties")
        if (keystorePropertiesFile.exists()) {
            val keystoreProperties = Properties()
            keystoreProperties.load(keystorePropertiesFile.inputStream())
            
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile") ?: "")
                storePassword = keystoreProperties.getProperty("storePassword") ?: ""
                keyAlias = keystoreProperties.getProperty("keyAlias") ?: ""
                keyPassword = keystoreProperties.getProperty("keyPassword") ?: ""
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Use release signing config if available
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Module dependencies
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:common"))
    implementation(project(":feature:today"))
    implementation(project(":feature:addedit"))
    implementation(project(":feature:allwork"))
    implementation(project(":feature:reports"))

    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)

    // Compose BOM
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.activity)
    implementation(libs.compose.viewmodel)
    implementation(libs.compose.runtime.livedata)
    debugImplementation(libs.compose.ui.tooling)

    // Lifecycle
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Navigation
    implementation(libs.navigation.compose)

    // Kotlinx Serialization
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.datastore.preferences)

    // Hilt
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}