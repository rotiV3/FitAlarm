import java.util.Base64
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

// ── Signing helpers ──────────────────────────────────────────────────────────
// Priority: CI environment variables → local.properties → unsigned (debug only)

fun envOrLocal(envKey: String, localKey: String = envKey): String =
    System.getenv(envKey) ?: localProperties.getProperty(localKey, "")

val keystoreFile = rootProject.file("release.keystore")

android {
    namespace = "com.rotiv3.fitalarm"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rotiv3.fitalarm"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("VERSION_CODE") ?: localProperties.getProperty("VERSION_CODE", "1")).toInt()
        versionName = System.getenv("VERSION_NAME") ?: localProperties.getProperty("VERSION_NAME", "1.0")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val mapsApiKey = envOrLocal("MAPS_API_KEY")
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        buildConfigField("String", "MAPS_API_KEY", "\"$mapsApiKey\"")

        val webClientId = envOrLocal("WEB_CLIENT_ID")
        buildConfigField("String", "WEB_CLIENT_ID", "\"$webClientId\"")
    }

    // ── Decode keystore from Base64 env var in CI ────────────────────────────
    val keystoreBase64 = System.getenv("KEYSTORE_BASE64")
    if (keystoreBase64 != null && keystoreBase64.isNotEmpty()) {
        val decoded = Base64.getDecoder().decode(keystoreBase64)
        keystoreFile.writeBytes(decoded)
    }

    signingConfigs {
        create("release") {
            if (keystoreFile.exists()) {
                storeFile     = keystoreFile
                storePassword = envOrLocal("KEYSTORE_PASSWORD")
                keyAlias      = envOrLocal("KEY_ALIAS")
                keyPassword   = envOrLocal("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = if (keystoreFile.exists()) signingConfigs.getByName("release") else null
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
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
        viewBinding = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/license.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/notice.txt",
                "META-INF/ASL2.0",
                "META-INF/*.kotlin_module",
                "META-INF/INDEX.LIST"
            )
        }
    }
}

dependencies {
    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.constraintlayout)
    implementation(libs.recyclerview)

    // Material Design 3
    implementation(libs.material)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Lifecycle
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.livedata.ktx)

    // Navigation
    implementation(libs.navigation.fragment.ktx)
    implementation(libs.navigation.ui.ktx)

    // Coroutines
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.core)

    // Play Services
    implementation(libs.play.services.auth)
    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)

    // Google Calendar API
    implementation(libs.google.api.services.calendar)
    implementation(libs.google.http.client)
    implementation(libs.google.api.client)

    // Glide
    implementation(libs.glide)
    ksp(libs.glide.compiler)

    // Retrofit & Gson
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.gson)

    // DataStore
    implementation(libs.datastore.preferences)

    // Google Play Billing (subscriptions / Pro upgrade)
    implementation("com.android.billingclient:billing-ktx:7.1.1")

    // Custom Tabs — used for Apple Sign-In OAuth2 flow
    implementation("androidx.browser:browser:1.8.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
