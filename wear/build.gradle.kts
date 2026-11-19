plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    // The code lives in its own package; the application id is the one of the phone app, so
    // both find each other over the Wearable Data Layer (same id and same signing key).
    namespace = "io.github.jnbp.nextstop.wear"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.jnbp.nextstop"
        // Wear OS 3 and later.
        minSdk = 30
        // Wear OS 6 is API 36; the watch images of the emulator go no higher yet.
        targetSdk = 36
        versionCode = 1_000_004
        versionName = "0.2.0"
    }

    buildTypes {
        release {
            // No R8 for now: optimising only some packages (packageScope) moved Kotlin classes
            // into the obfuscated namespace while our own subclasses stayed outside, so the
            // release build crashed at start with IllegalAccessError (kotlin.collections /
            // kotlin.text). Turn it back on only together with a run of the staging variant.
            optimization {
                enable = false
            }
        }
        // The release build (same optimisation) signed with the debug key, to run and test it
        // from Android Studio before a bundle goes to Play: pick "staging" in Build Variants.
        create("staging") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    // Play services (wearable) pull in Fragment 1.0, whose FragmentActivity breaks the
    // ActivityResult API the permission request uses; lint stops a release build over it.
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.wear.compose.material3)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.wear.compose.navigation)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.play.services.wearable)
    implementation(libs.androidx.wear.input)
    implementation(libs.androidx.wear.ongoing)
    implementation(libs.androidx.wear.remote.interactions)
    implementation(libs.guava.listenablefuture)
    implementation(libs.androidx.wear.tiles)
    implementation(libs.androidx.wear.protolayout)
    implementation(libs.androidx.wear.protolayout.expression)
    implementation(libs.androidx.wear.protolayout.material3)
    implementation(libs.androidx.wear.complications.data.source.ktx)
    testImplementation(libs.junit)
}
