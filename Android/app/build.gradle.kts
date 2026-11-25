import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val keystoreProperties = Properties().apply {
    val propsFile = rootProject.file("keystore.properties")
    if (propsFile.isFile) {
        propsFile.inputStream().use(::load)
    }
}

fun signingValue(propKey: String, envKey: String): String? {
    return keystoreProperties.getProperty(propKey)?.takeIf { it.isNotBlank() }
        ?: System.getenv(envKey)?.takeIf { it.isNotBlank() }
}

val releaseStorePath = signingValue("storeFile", "AURACLE_KEYSTORE_FILE")
val releaseStorePassword = signingValue("storePassword", "AURACLE_KEYSTORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "AURACLE_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "AURACLE_KEY_PASSWORD")
val hasReleaseKeystore = listOf(
    releaseStorePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { it != null }

android {
    namespace = "io.github.areyoufeelingitnowmrkrebs.auracle"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.areyoufeelingitnowmrkrebs.auracle"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(checkNotNull(releaseStorePath))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    dependenciesInfo {
        // Keep standalone GitHub Release APKs free of Google's encrypted dependency blob,
        // while keeping App Bundles (.aab) Play Store-ready.
        includeInApk = false
        includeInBundle = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        aidl = false
        buildConfig = false
        shaders = false
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
}
