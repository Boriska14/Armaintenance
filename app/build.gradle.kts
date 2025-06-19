plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.arcore"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.arcore"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // Core AndroidX dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // UI
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.appcompat)

    // ARCore - consolidated to single version
    implementation(libs.core.v1400) {
        exclude(group = "com.android.support")
    }
    implementation(libs.sceneform.ux) {
        exclude(group = "com.android.support")
    }

    // ViewBinding
    implementation(libs.androidx.ui.viewbinding)
}

configurations.all {
    resolutionStrategy {
        // Force consistent versions
        force("androidx.core:core:1.12.0")
        force("androidx.core:core-ktx:1.12.0")

        // Remove any support library dependencies
        exclude(group = "com.android.support", module = "support-compat")
        exclude(group = "com.android.support", module = "support-v4")

        // Prefer AndroidX artifacts
        preferProjectModules()
    }
}