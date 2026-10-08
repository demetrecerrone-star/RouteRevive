plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.routerevive.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.routerevive.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "0.0.7"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    val signedStorePath = System.getenv("RR_SIGNING_FILE")
    val signedPassword = System.getenv("RR_STORE_PASSWORD")
    val hasStableSigning = !signedStorePath.isNullOrBlank() && !signedPassword.isNullOrBlank()

    signingConfigs {
        if (hasStableSigning) {
            create("stable") {
                storeFile = file(signedStorePath!!)
                storePassword = signedPassword
                keyAlias = "routerevive"
                keyPassword = signedPassword
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (hasStableSigning) signingConfig = signingConfigs.getByName("stable")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    implementation("androidx.core:core-ktx:1.15.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
