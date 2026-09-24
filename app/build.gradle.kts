plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

// Release signing comes from environment variables (CI secrets or a developer's own shell),
// never from a file checked into the repo. Debug and CI's debug-only build never read these,
// and a release build without them falls back to unsigned rather than failing configuration.
val releaseKeystorePath: String? = System.getenv("RELEASE_KEYSTORE_PATH")
val releaseKeystorePassword: String? = System.getenv("RELEASE_KEYSTORE_PASSWORD")
val releaseKeyAlias: String? = System.getenv("RELEASE_KEY_ALIAS")
val releaseKeyPassword: String? = System.getenv("RELEASE_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseKeystorePath, releaseKeystorePassword, releaseKeyAlias, releaseKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "com.scrivtech.heartrate"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.scrivtech.heartrate"
        minSdk = 31
        targetSdk = 37
        versionCode = 8
        versionName = "1.5.1"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword!!
                keyAlias = releaseKeyAlias!!
                keyPassword = releaseKeyPassword!!
            }
        }
    }

    buildTypes {
        release {
            // R8 is ON again as of v1.6.0, after three releases off.
            //
            // It was switched off because v1.4.0 shipped minified and hung on connect. That
            // turned out to be a BLE race in BleHeartRateManager, not R8: discoverServices()
            // ran synchronously from the connection callback, and minification only changed
            // how fast execution reached that line. v1.5.0 proved it (an unminified release
            // still failed intermittently), and v1.5.1 fixed the race with explicit delays.
            //
            // v1.6.0 re-enabled it on a signed release build tested on a real phone over
            // repeated connect/disconnect cycles, including reconnects inside the stack's
            // 4-second idle-link window. If a connection problem ever appears in a release
            // build but not a debug one, suspect timing first (see BleHeartRateManager),
            // and re-test over many cycles: one successful connect is not evidence.
            //
            // Log.d/Log.v are deliberately kept (see proguard-rules.pro).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // AGP 8 stopped generating BuildConfig unless asked. The scan screen reads
        // VERSION_NAME from it so the version shown in the UI and the release-notes
        // link both track the version above, rather than being hardcoded and going
        // stale on the next bump.
        buildConfig = true
    }
}

// AGP 9 compiles Kotlin itself, so the kotlin-android plugin and android.kotlinOptions are gone.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
}
