package io.github.jnbp.nextstop.data

/*
 * What the settings page "About Next Stop" lists: the project's own addresses, the third-party
 * libraries in the phone and watch apps, and the fonts bundled with them. Plain data without
 * Android classes, so a JVM test can hold the list against gradle/libs.versions.toml
 * (ThirdPartyNoticesTest): a library added there without an entry here fails the build's tests.
 * The data sources (timetables, map, places) are listed in the UI (see AboutUi), since what they
 * are used for is a text in two languages. The NOTICE file at the root of the repository is the
 * same list for people who read the source.
 */

/** The addresses of the project: its code, its website and how to report a problem. */
object ProjectLinks {
    const val SOURCE_CODE = "https://github.com/jnbp/next-stop"
    const val ISSUES = "https://github.com/jnbp/next-stop/issues"
    const val LICENSE_TEXT = "https://github.com/jnbp/next-stop/blob/main/LICENSE"
    const val APP_STORE_PERMISSION = "https://github.com/jnbp/next-stop/blob/main/LICENSE-EXCEPTION.md"
    const val NOTICE = "https://github.com/jnbp/next-stop/blob/main/NOTICE"

    // The website: German at its root, English under /en/ (see web/index.html and web/en/).
    private const val SITE = "https://nextstop.bapo.me"

    /**
     * The license of Next Stop as SPDX names it. Plain GPL version 3 ("only", not "or later"):
     * LICENSE, NOTICE and the README name no later version. The app store permission of
     * LICENSE-EXCEPTION.md is an additional permission on top of it, not another license.
     */
    const val LICENSE_ID = "GPL-3.0"

    /** The website in the app's language [language] ("de" or "en", see AppLanguage). */
    fun website(language: String): String = if (language == "de") "$SITE/" else "$SITE/en/"

    /** The privacy policy in [language]: web/datenschutz/ in German, web/en/privacy/ in English. */
    fun privacyPolicy(language: String): String =
        if (language == "de") "$SITE/datenschutz/" else "$SITE/en/privacy/"
}

/**
 * A third-party component of the apps: its [name], who makes it ([maintainer]), its [license]
 * as an SPDX id (or the name of the terms where there is none), and where to read about it
 * ([url]). [aliases] are its entries in the [libraries] table of gradle/libs.versions.toml; a
 * component that comes with the build tools (the Kotlin standard library) or a bundled file
 * (a font) has none.
 */
data class ThirdPartyNotice(
    val name: String,
    val maintainer: String,
    val license: String,
    val url: String,
    val aliases: List<String> = emptyList(),
)

// The maintainers that come up more than once.
private const val ANDROIDX = "Google, The Android Open Source Project"
private const val JETBRAINS = "JetBrains"
private const val APACHE = "Apache-2.0"

/** The libraries in the apps (release builds), in the order the settings list them. */
val THIRD_PARTY_LIBRARIES: List<ThirdPartyNotice> = listOf(
    ThirdPartyNotice(
        name = "public-transport-enabler",
        maintainer = "Andreas Schildbach and contributors",
        license = "GPL-3.0",
        url = "https://github.com/schildbach/public-transport-enabler",
        aliases = listOf("public-transport-enabler"),
    ),
    ThirdPartyNotice(
        name = "MapLibre Native for Android",
        maintainer = "MapLibre",
        license = "BSD-2-Clause",
        url = "https://github.com/maplibre/maplibre-native",
        aliases = listOf("maplibre-android"),
    ),
    ThirdPartyNotice(
        name = "OkHttp",
        maintainer = "Square",
        license = APACHE,
        url = "https://square.github.io/okhttp/",
        aliases = listOf("okhttp-bom"),
    ),
    ThirdPartyNotice(
        name = "Kotlin Standard Library",
        maintainer = JETBRAINS,
        license = APACHE,
        url = "https://kotlinlang.org",
    ),
    ThirdPartyNotice(
        name = "kotlinx.coroutines",
        maintainer = JETBRAINS,
        license = APACHE,
        url = "https://github.com/Kotlin/kotlinx.coroutines",
        aliases = listOf("kotlinx-coroutines-android", "kotlinx-coroutines-play-services"),
    ),
    ThirdPartyNotice(
        name = "Jetpack Compose (UI, Material 3)",
        maintainer = ANDROIDX,
        license = APACHE,
        url = "https://developer.android.com/jetpack/androidx/releases/compose",
        aliases = listOf(
            "androidx-compose-bom",
            "androidx-compose-ui",
            "androidx-compose-ui-graphics",
            "androidx-compose-ui-tooling-preview",
            "androidx-compose-material3",
        ),
    ),
    ThirdPartyNotice(
        name = "AndroidX Core",
        maintainer = ANDROIDX,
        license = APACHE,
        url = "https://developer.android.com/jetpack/androidx/releases/core",
        aliases = listOf("androidx-core-ktx"),
    ),
    ThirdPartyNotice(
        name = "AndroidX Activity",
        maintainer = ANDROIDX,
        license = APACHE,
        url = "https://developer.android.com/jetpack/androidx/releases/activity",
        aliases = listOf("androidx-activity-compose"),
    ),
    ThirdPartyNotice(
        name = "AndroidX Fragment",
        maintainer = ANDROIDX,
        license = APACHE,
        url = "https://developer.android.com/jetpack/androidx/releases/fragment",
        aliases = listOf("androidx-fragment"),
    ),
    ThirdPartyNotice(
        name = "AndroidX Lifecycle",
        maintainer = ANDROIDX,
        license = APACHE,
        url = "https://developer.android.com/jetpack/androidx/releases/lifecycle",
        aliases = listOf("androidx-lifecycle-runtime-ktx"),
    ),
    ThirdPartyNotice(
        name = "Jetpack Glance",
        maintainer = ANDROIDX,
        license = APACHE,
        url = "https://developer.android.com/jetpack/androidx/releases/glance",
        aliases = listOf("androidx-glance-appwidget", "androidx-glance-material3"),
    ),
    ThirdPartyNotice(
        name = "WorkManager",
        maintainer = ANDROIDX,
        license = APACHE,
        url = "https://developer.android.com/jetpack/androidx/releases/work",
        aliases = listOf("androidx-work-runtime"),
    ),
    ThirdPartyNotice(
        name = "JankStats",
        maintainer = ANDROIDX,
        license = APACHE,
        url = "https://developer.android.com/jetpack/androidx/releases/metrics",
        aliases = listOf("androidx-metrics-performance"),
    ),
    ThirdPartyNotice(
        name = "Wear Compose",
        maintainer = ANDROIDX,
        license = APACHE,
        url = "https://developer.android.com/jetpack/androidx/releases/wear-compose",
        aliases = listOf(
            "androidx-wear-compose-material3",
            "androidx-wear-compose-foundation",
            "androidx-wear-compose-navigation",
        ),
    ),
    ThirdPartyNotice(
        name = "Wear OS (Input, Ongoing Activity, Remote Interactions)",
        maintainer = ANDROIDX,
        license = APACHE,
        url = "https://developer.android.com/jetpack/androidx/releases/wear",
        aliases = listOf("androidx-wear-input", "androidx-wear-ongoing", "androidx-wear-remote-interactions"),
    ),
    ThirdPartyNotice(
        name = "Wear Tiles and ProtoLayout",
        maintainer = ANDROIDX,
        license = APACHE,
        url = "https://developer.android.com/jetpack/androidx/releases/wear-tiles",
        aliases = listOf(
            "androidx-wear-tiles",
            "androidx-wear-protolayout",
            "androidx-wear-protolayout-expression",
            "androidx-wear-protolayout-material3",
        ),
    ),
    ThirdPartyNotice(
        name = "Wear Watch Face complications",
        maintainer = ANDROIDX,
        license = APACHE,
        url = "https://developer.android.com/jetpack/androidx/releases/wear-watchface",
        aliases = listOf("androidx-wear-complications-data-source-ktx"),
    ),
    ThirdPartyNotice(
        name = "Guava ListenableFuture",
        maintainer = "Google",
        license = APACHE,
        url = "https://github.com/google/guava",
        aliases = listOf("guava-listenablefuture"),
    ),
    ThirdPartyNotice(
        name = "Google Play services Wearable",
        maintainer = "Google",
        // Not open source: the terms of the Android SDK apply.
        license = "Android SDK License",
        url = "https://developer.android.com/studio/terms",
        aliases = listOf("play-services-wearable"),
    ),
)

/** The fonts bundled with the apps (see NOTICE). */
val BUNDLED_FONTS: List<ThirdPartyNotice> = listOf(
    ThirdPartyNotice(
        name = "Google Sans Flex",
        maintainer = "Google LLC",
        license = "OFL-1.1",
        url = "https://github.com/jnbp/next-stop/blob/main/docs/licenses/GoogleSansFlex-OFL.txt",
    ),
)

/**
 * The entries of gradle/libs.versions.toml that are not part of a release build: tests,
 * and the tools of debug builds. Not listed in the app.
 */
val NOT_SHIPPED_ALIASES: Set<String> = setOf(
    "junit",
    "androidx-junit",
    "androidx-espresso-core",
    "androidx-compose-ui-tooling",
    "androidx-compose-ui-test-manifest",
    "androidx-compose-ui-test-junit4",
)
