plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
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
    compileSdk = 35

    defaultConfig {
        applicationId = "com.scrivtech.heartrate"
        minSdk = 31
        targetSdk = 35
        versionCode = 7
        versionName = "1.5.0"
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
            // Minification is ON. It was never what broke v1.4.0.
            //
            // v1.4.0 hung on connect and shipped minified, so minification looked guilty and
            // v1.4.1 shipped unminified at 18 MB to stop the bleeding. But v1.4.1 changed two
            // things at once: it disabled minification AND stopped stripping Log.d/Log.v.
            // Testing those separately settled it -- a minified build with log stripping off
            // connects normally on a real device, at 2.2 MB.
            //
            // The culprit is -assumenosideeffects on android.util.Log, which stays commented
            // out in proguard-rules.pro. Do not restore it without re-testing the connection
            // on a real device; that file records the likely mechanism.
            isMinifyEnabled = true
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

    kotlinOptions {
        jvmTarget = "17"
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
