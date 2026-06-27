plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.dehumidifier"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dehumidifier"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("BUILD_NUMBER")?.toIntOrNull() ?: 3)
        versionName = "1.${System.getenv("BUILD_NUMBER") ?: "dev"}"
        buildConfigField("String", "GITHUB_TOKEN", "\"${System.getenv("GITHUB_READ_TOKEN") ?: project.findProperty("github.token") ?: ""}\"")
        buildConfigField("String", "GITHUB_REPO", "\"Nildyanna/DavesGovee\"")
    }

    signingConfigs {
        val keystorePath = System.getenv("KEYSTORE_PATH")
        val keystorePassword = System.getenv("KEYSTORE_PASSWORD")
        val keyAlias = System.getenv("KEY_ALIAS")
        val keyPassword = System.getenv("KEY_PASSWORD")
        if (keystorePath != null && keystorePassword != null && keyAlias != null && keyPassword != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            val releaseSigning = signingConfigs.findByName("release")
            if (releaseSigning != null) signingConfig = releaseSigning
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
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi.kotlin)
    implementation(libs.kotlinx.coroutines.android)
}
