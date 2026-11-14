package io.github.jnbp.nextstop.data

import io.github.jnbp.nextstop.BuildConfig

/**
 * Keys built into this build from the gitignored secrets.properties in the project root (see
 * docs/keys.md); each is empty in a build without it. A key the user enters in the settings
 * always wins over the built-in one. The values never go into a log.
 */
object BuiltInKeys {
    val accessibilityCloudToken: String get() = BuildConfig.ACCESSIBILITY_CLOUD_TOKEN
    val vbbAccessId: String get() = BuildConfig.VBB_ACCESS_ID
    val fastaClientId: String get() = BuildConfig.FASTA_CLIENT_ID
    val fastaApiKey: String get() = BuildConfig.FASTA_API_KEY
}

/** The key the user entered, else the built-in one; empty when there is neither. */
fun effectiveKey(user: String, builtIn: String): String = user.trim().ifEmpty { builtIn.trim() }

/**
 * A key that comes in two parts (client id and API key): the user's pair when both parts are
 * entered, else the built-in pair when both are there, else null. Parts are never mixed.
 */
fun effectiveKeyPair(userId: String, userKey: String, builtInId: String, builtInKey: String): Pair<String, String>? = when {
    userId.isNotBlank() && userKey.isNotBlank() -> userId.trim() to userKey.trim()
    builtInId.isNotBlank() && builtInKey.isNotBlank() -> builtInId.trim() to builtInKey.trim()
    else -> null
}
