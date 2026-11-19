package io.github.jnbp.nextstop.wear.data

import org.json.JSONObject

/**
 * What the phone's own searches ask for besides the change time ("searchOptions" of the places
 * the phone syncs, from its searchOptionsInUse): the walking pace ("Langsamer unterwegs" is
 * "slow"), step-free ways and the products (none of the long-distance ones with "Nur
 * Deutschlandticket"). A search of the watch itself (no phone in reach) asks the same, so it
 * neither offers an ICE the phone would not, nor walks a change at a pace the user cannot.
 * Null fields (an older phone app) ask nothing, as before.
 */
data class PhoneSearchOptions(
    /** "slow", "normal" or "fast" (the phone's WalkingSpeed.apiValue). */
    val walkingSpeed: String? = null,
    /** "partial" or "complete" (the phone's Accessibility.apiValue); null when it does not matter. */
    val accessibility: String? = null,
    /** The products to ride (keys of [SEARCH_PRODUCT_KEYS]); null for all. */
    val products: Set<String>? = null,
    /** The own factor on every walk ("Eigener Faktor" of "Langsamer unterwegs", #198); null without one. */
    val walkFactor: Double? = null,
) {
    /** The parameters of a journey search of BVG (transport.rest), as the phone asks them (optionQuery and hafasProductQuery there). */
    fun hafasQuery(): String = buildString {
        walkingSpeed?.takeIf { it in WALKING_SPEEDS }?.let { append("&walkingSpeed=").append(it) }
        accessibility?.takeIf { it in ACCESSIBILITY }?.let { append("&accessibility=").append(it) }
        val wanted = products ?: return@buildString
        // HAFAS knows no coaches: they come as buses, so buses stay on while coaches are.
        SEARCH_PRODUCT_KEYS.filter { it !in wanted }.forEach { key ->
            when (key) {
                "coach" -> Unit
                "bus" -> if ("coach" !in wanted) append("&bus=false")
                else -> append("&").append(key).append("=false")
            }
        }
    }

    /** The parameters of a journey search of Transitous (MOTIS), as the phone asks them (TransitousSource.plan there). */
    fun motisQuery(): String = buildString {
        val ownSpeed = walkFactor?.let { ownPedestrianSpeed(it) }
        when {
            ownSpeed != null -> append("&pedestrianSpeed=").append(ownSpeed)
            walkingSpeed == "slow" -> append("&pedestrianSpeed=0.9")
            walkingSpeed == "fast" -> append("&pedestrianSpeed=1.7")
        }
        if (accessibility == "complete") append("&pedestrianProfile=WHEELCHAIR")
        val wanted = products ?: return@buildString
        if (SEARCH_PRODUCT_KEYS.all { it in wanted }) return@buildString
        val modes = wanted.flatMap { MOTIS_MODES[it].orEmpty() }
        if (modes.isNotEmpty()) append("&transitModes=").append(modes.joinToString(","))
    }

    companion object {
        /** The options as the phone writes them; all null without them. */
        fun parse(json: JSONObject?): PhoneSearchOptions {
            if (json == null) return PhoneSearchOptions()
            val list = json.optJSONArray("products")
            return PhoneSearchOptions(
                walkingSpeed = json.optString("walkingSpeed", "").ifEmpty { null },
                accessibility = json.optString("accessibility", "").ifEmpty { null },
                products = list?.let { (0 until it.length()).map { index -> it.optString(index) }.filter { key -> key in SEARCH_PRODUCT_KEYS }.toSet() },
                walkFactor = json.optDouble("walkFactor", Double.NaN).takeIf { it.isFinite() && it in 0.5..3.0 },
            )
        }
    }
}

/** The products of a search, as the phone names them (PRODUCT_KEYS there). */
val SEARCH_PRODUCT_KEYS: List<String> = listOf("suburban", "subway", "tram", "bus", "ferry", "regional", "express", "coach")

private val WALKING_SPEEDS = setOf("slow", "normal", "fast")

/**
 * The walking speed in m/s for Transitous at an own [factor], as the phone asks it
 * (motisPedestrianSpeed there): 1.2 m/s divided by it, two decimals; null for the usual pace.
 */
private fun ownPedestrianSpeed(factor: Double): String? {
    if (kotlin.math.abs(factor - 1.0) < 1e-6) return null
    return String.format(java.util.Locale.ROOT, "%.2f", 1.2 / factor)
}
private val ACCESSIBILITY = setOf("partial", "complete")

// The transit modes of MOTIS for each product, as the phone asks them (modesOf in TransitousSource).
private val MOTIS_MODES: Map<String, List<String>> = mapOf(
    "suburban" to listOf("SUBURBAN"),
    "subway" to listOf("SUBWAY"),
    "tram" to listOf("TRAM"),
    "bus" to listOf("BUS"),
    "coach" to listOf("COACH"),
    "ferry" to listOf("FERRY"),
    "regional" to listOf("REGIONAL_RAIL", "REGIONAL_FAST_RAIL"),
    "express" to listOf("HIGHSPEED_RAIL", "LONG_DISTANCE", "NIGHT_RAIL"),
)
