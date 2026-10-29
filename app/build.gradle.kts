import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

/**
 * Release signing comes from WUNDER_KEYSTORE_* environment variables (CI) or keystore.properties (local).
 * Without either, release builds fall back to the debug key.
 */
val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun signing(env: String, prop: String): String? = System.getenv(env)?.takeIf { it.isNotEmpty() } ?: keystoreProps.getProperty(prop)
val releaseStoreFile = signing("WUNDER_KEYSTORE_FILE", "storeFile")?.let { rootProject.file(it) }

/** CI sets GITHUB_RUN_NUMBER so every build installs over the previous one. */
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "app.wunder"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.wunder"
        minSdk = 26
        targetSdk = 37
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
        buildConfigField("String", "WU_API_KEY", "\"99d32948179b0c5dcc8970c4d68d5ba1\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        if (releaseStoreFile?.exists() == true) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = signing("WUNDER_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signing("WUNDER_KEY_ALIAS", "keyAlias")
                keyPassword = signing("WUNDER_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.work:work-runtime-ktx:2.10.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
