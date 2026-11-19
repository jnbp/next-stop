import java.io.StringReader
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// public-transport-enabler (GPL-3.0) comes from JitPack, which builds it from GitHub. Should that
// fail, set nextstop.pte=false in gradle.properties: the app then builds without the library and
// shows that source as "not included in this build".
val withPte = providers.gradleProperty("nextstop.pte").orNull?.toBoolean() ?: true

// Keys built into the app, from secrets.properties in the project root (gitignored; see
// secrets.properties.example and docs/keys.md). A missing file or key gives an empty string:
// the app then works with the keys the user enters. Read through a provider so the
// configuration cache notices a changed file.
val secrets = Properties().apply {
    providers.fileContents(rootProject.layout.projectDirectory.file("secrets.properties"))
        .asText.orNull?.let { load(StringReader(it)) }
}

/**
 * A secret as a Java string literal for a BuildConfig field: from secrets.properties, else from
 * the environment variable NEXTSTOP_<KEY_IN_SNAKE_CASE> (for CI, e.g. NEXTSTOP_VBB_ACCESS_ID),
 * else "".
 */
fun secretField(key: String): String {
    val env = "NEXTSTOP_" + key.replace(Regex("([a-z])([A-Z])"), "\$1_\$2").uppercase()
    val value = secrets.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }
        ?: providers.environmentVariable(env).orNull?.trim()
        ?: ""
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}

android {
    namespace = "io.github.jnbp.nextstop"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.jnbp.nextstop"
        minSdk = 31
        targetSdk = 37
        versionCode = 4
        versionName = "0.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Built-in keys (see BuiltInKeys.kt); a key entered in the settings wins over them.
        buildConfigField("String", "ACCESSIBILITY_CLOUD_TOKEN", secretField("accessibilityCloudToken"))
        buildConfigField("String", "VBB_ACCESS_ID", secretField("vbbAccessId"))
        buildConfigField("String", "FASTA_CLIENT_ID", secretField("fastaClientId"))
        buildConfigField("String", "FASTA_API_KEY", secretField("fastaApiKey"))
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
        buildConfig = true
    }
    // The source over public-transport-enabler, or its stand-in without the library (see below).
    sourceSets {
        getByName("main") {
            // With AGP 9's built-in Kotlin, Kotlin files are compiled from the kotlin source
            // directories: both are set so the folder is picked up either way.
            val pteDir = if (withPte) "src/pte/java" else "src/nopte/java"
            java.srcDir(pteDir)
            kotlin.srcDir(pteDir)
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.maplibre.android)
    // One OkHttp for MapLibre (asks for 4.12) and public-transport-enabler (asks for 5.4): see
    // the okhttp entry in libs.versions.toml.
    implementation(platform(libs.okhttp.bom))
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.metrics.performance)
    implementation(libs.play.services.wearable)
    implementation(libs.androidx.wear.remote.interactions)
    implementation(libs.kotlinx.coroutines.play.services)
    // Berlin data through Öffi's library (GPL-3.0, see README), unless nextstop.pte=false.
    // Android has org.json and kxml2 itself: the copies the library asks for would clash with them.
    if (withPte) {
        implementation(libs.public.transport.enabler) {
            exclude(group = "org.json", module = "json")
            exclude(group = "net.sf.kxml", module = "kxml2")
        }
    }
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}