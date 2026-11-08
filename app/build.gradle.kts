plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.khizrim.dialer"
    compileSdk = 35

    defaultConfig {
        // только языки, которые мы переводим
        resourceConfigurations += listOf("en", "ru")
        applicationId = "com.khizrim.dialer"
        minSdk = 26
        targetSdk = 35
        // каждая сборка CI получает новый номер, чтобы APK ставился поверх старого
        versionCode = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0) + 100
        versionName = "0.8.5"
    }

    // Один постоянный ключ для всех сборок: иначе каждый APK из CI подписан новым
    // ключом и не ставится поверх, а вместе с удалением пропадают настройки контактов.
    // Ключ приходит из секретов GitHub (см. README); без них сборка подписывается как раньше.
    val keystore = System.getenv("OZERO_KEYSTORE")?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (keystore != null) create("ozero") {
            storeFile = keystore
            storePassword = System.getenv("OZERO_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("OZERO_KEY_ALIAS")
            keyPassword = System.getenv("OZERO_KEY_PASSWORD") ?: System.getenv("OZERO_KEYSTORE_PASSWORD")
        }
    }
    val ozero = signingConfigs.findByName("ozero")

    buildTypes {
        debug {
            if (ozero != null) signingConfig = ozero
        }
        release {
            isMinifyEnabled = false
            // без своего ключа release подписываем debug-ключом, чтобы APK всё равно ставился
            signingConfig = ozero ?: signingConfigs.getByName("debug")
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        // переводы проверяем, но отсутствующий перевод не валит сборку
        warning += listOf("MissingTranslation")
        htmlReport = true
        sarifReport = true
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
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("com.github.petrovich4j:petrovich4j:1.1.3")
    // висячее стекло, как в Ozero Mail: размытая лента под плавающими кнопками (Android 12+)
    implementation("dev.chrisbanes.haze:haze:1.1.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
