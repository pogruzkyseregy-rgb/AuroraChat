plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystore = file("aurora.jks")

android {
    namespace = "ru.avrora.chat"
    compileSdk = 34

    defaultConfig {
        applicationId = "ru.avrora.chat"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
    }

    // Постоянная подпись: без неё каждая сборка из Actions подписана новым ключом,
    // и обновление поверх старой версии не встанет (а удаление сотрёт переписку).
    signingConfigs {
        create("aurora") {
            if (keystore.exists()) {
                storeFile = keystore
                storePassword = System.getenv("KS_PASS")
                keyAlias = System.getenv("KS_ALIAS")
                keyPassword = System.getenv("KS_PASS")
            }
        }
    }

    buildTypes {
        debug {
            if (keystore.exists()) signingConfig = signingConfigs.getByName("aurora")
        }
        release { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(bom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
