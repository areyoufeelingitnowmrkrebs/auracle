plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "io.github.areyoufeelingitnowmrkrebs.auracle"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.areyoufeelingitnowmrkrebs.auracle"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
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
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
}
