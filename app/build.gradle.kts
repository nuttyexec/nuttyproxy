plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val releaseKeystoreFile = providers.environmentVariable("ANDROID_KEYSTORE_FILE").orNull
val releaseSigningPassword = providers.environmentVariable("ANDROID_SIGNING_PASSWORD").orNull

android {
    namespace = "dev.nutty.proxy"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "dev.nutty.proxy"
        minSdk = 26
        targetSdk = 35
        versionCode = 13
        versionName = "0.1.18"
    }

    signingConfigs {
        create("release") {
            // The keystore is decoded from a GitHub Actions secret into the
            // runner's temporary directory. Neither it nor its password is
            // part of this repository.
            if (releaseKeystoreFile != null && releaseSigningPassword != null) {
                storeFile = file(releaseKeystoreFile)
                storePassword = releaseSigningPassword
                keyAlias = "nuttyproxy"
                keyPassword = releaseSigningPassword
                storeType = "pkcs12"
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.okhttp)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.ui.tooling.preview)

    debugImplementation(libs.androidx.ui.tooling)
}
