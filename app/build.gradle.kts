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
            // Minification is OFF, deliberately. The APK is ~18 MB instead of ~2.2 MB.
            //
            // That size costs nothing here: this app is sideloaded onto personal phones, so
            // there is no store limit, no download budget, and no user paying for the bytes.
            // R8's other benefits are just as irrelevant -- the app spends its life idle
            // waiting on BLE notifications, so optimisation buys no measurable speed, and the
            // repository is public, so obfuscation protects nothing.
            //
            // Against that, minification has twice cost real diagnostic effort. v1.4.0 shipped
            // minified and hung on connect; it was eventually traced to -assumenosideeffects
            // on android.util.Log (still commented out in proguard-rules.pro, and it should
            // stay that way). A later minified build then failed a device test for reasons
            // never established. Both investigations were spent buying a smaller APK that
            // nobody needed.
            //
            // If you turn this back on, you are re-opening that thread. It needs repeated
            // connection trials on a real device to prove anything -- a single successful
            // connect is not evidence, which is the specific mistake that produced the
            // confident and wrong conclusion this comment replaces.
            isMinifyEnabled = false
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
