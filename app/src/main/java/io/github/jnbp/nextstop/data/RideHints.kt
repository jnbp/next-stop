package io.github.jnbp.nextstop.data

/*
 * What the provider tells about a ride besides its times: how full it is expected to be
 * (HAFAS "loadFactor") and the hints among its remarks that the app shows as small icons,
 * bicycles allowed and step-free access.
 */

/** How full a ride is expected to be, from nearly empty to very full. */
enum class Occupancy(
    /** How many of the three segments of the gauge are filled. */
    val segments: Int,
) {
    Low(1), Medium(2), High(3), Full(3);

    companion object {
        /**
         * The load factor of hafas-client ("low-to-medium", "high", "very-high",
         * "exceptionally-high"), and the plain words some providers use; null where unknown.
         */
        fun fromLoadFactor(value: String?): Occupancy? = when (value?.trim()?.lowercase()) {
            null, "" -> null
            "low", "low-to-medium", "few-seats", "many_seats_available", "empty" -> Low
            "medium", "high", "few_seats_available" -> Medium
            "very-high", "standing_room_only", "crushed_standing_room_only" -> High
            "exceptionally-high", "full" -> Full
            else -> null
        }
    }
}

/** A hint about a ride the app shows as an icon. */
enum class RideFeature { BikeCarriage, StepFree }

// The codes of HAFAS remarks for bicycles allowed ("FB" limited, "FK" with ticket, "FR" with
// reservation, "FS" free) and for step-free access ("bf", "RG", "EA", "EH", "OA"), lower case.
private val BIKE_CODES = setOf("fb", "fk", "fr", "fs", "fa")
private val STEP_FREE_CODES = setOf("bf", "rg", "ea", "eh", "oa")

// Words of such hints where a provider sends no code, in German and English.
private val BIKE_WORDS = listOf("fahrradmitnahme", "bicycle conveyance", "bicycles allowed", "bikes allowed", "bicycle carriage")
private val STEP_FREE_WORDS = listOf("barrierefrei", "stufenfrei", "rollstuhl", "step-free", "step free", "wheelchair")

// Words that say the opposite: no bicycles, no step-free access.
private val NEGATIONS = listOf("nicht ", "keine ", "kein ", "not ", "no ", "ausgeschlossen")

/**
 * The features among the hints of a ride, each given as its remark [code] and [text] (either
 * may be null). A hint that says the opposite ("Fahrradmitnahme nicht möglich") counts not.
 */
fun rideFeatures(hints: List<Pair<String?, String?>>): Set<RideFeature> {
    if (hints.isEmpty()) return emptySet()
    val found = HashSet<RideFeature>(2)
    hints.forEach { (code, text) ->
        val words = text?.lowercase().orEmpty()
        if (NEGATIONS.any { it in words }) return@forEach
        val key = code?.trim()?.lowercase()
        if (key in BIKE_CODES || BIKE_WORDS.any { it in words }) found += RideFeature.BikeCarriage
        if (key in STEP_FREE_CODES || STEP_FREE_WORDS.any { it in words }) found += RideFeature.StepFree
    }
    return found
}

/**
 * #248: the provider's own words about bicycles on a ride, for the explanation behind the
 * bicycle chip: the text of the first hint about bicycles (by its code or its words) that does
 * not say the opposite, else the first one that does ("Fahrradmitnahme nicht möglich"); null
 * where no hint about bicycles has a text.
 */
fun bikeRemarkText(hints: List<Pair<String?, String?>>): String? {
    var negated: String? = null
    for ((code, text) in hints) {
        val shown = text?.trim()?.takeIf { it.isNotEmpty() } ?: continue
        val words = shown.lowercase()
        val key = code?.trim()?.lowercase()
        if (key !in BIKE_CODES && BIKE_WORDS.none { it in words }) continue
        if (NEGATIONS.none { it in words }) return shown
        if (negated == null) negated = shown
    }
    return negated
}

/**
 * #264: the hints among the notes of a leg of the official VBB HAFAS API ("Notes" / "Note",
 * each as its [type], [key] and text): the attributes ("A", or no type), such as "FB"
 * "Fahrradmitnahme begrenzt möglich", for [rideFeatures] and [bikeRemarkText]. Remarks and
 * disruption notes ("R", "M", "H" and the like) are no hints. Each note is given as type, key and text.
 */
fun vbbNoteHints(notes: List<Triple<String?, String?, String?>>): List<Pair<String?, String?>> =
    notes.filter { (type, _, _) -> type.isNullOrBlank() || type.trim().equals("A", ignoreCase = true) }
        .map { (_, key, text) -> key to text }
