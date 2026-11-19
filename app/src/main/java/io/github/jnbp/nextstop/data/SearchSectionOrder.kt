package io.github.jnbp.nextstop.data

/*
 * The order of the sections of a search for places, fixed whatever answers first (#208):
 * the fixed row of the own position ("Aktueller Standort"), then the contacts and appointments
 * that fit, then what the app knows at once (places picked before, favourites), then the
 * stations (the near ones first, "Nahverkehr" apart from "Fernverkehr"), last the other places
 * (addresses, points of interest). An answer that comes late goes into its own section, so it
 * never pushes an earlier one down: a contact found while typing "Sarah" stays where it is when
 * the places of the internet arrive.
 * The order of the connections of a search is searchOrder in SearchOrder.kt.
 */

/** A section of the list of a search for places while typing. */
enum class SearchSection { Lead, People, Known, Local, LongDistance, Places }

/**
 * The sections in the order they are shown (Joel's order for #208: contacts and appointments
 * first, then stations, then places; the own position stays the fixed first row).
 */
val SEARCH_SECTION_ORDER: List<SearchSection> = listOf(
    SearchSection.Lead,
    SearchSection.People,
    SearchSection.Known,
    SearchSection.Local,
    SearchSection.LongDistance,
    SearchSection.Places,
)

/** The sections of the list of a search for places, in the order they are shown (see [searchSections]). */
data class SearchSections(
    /** Shown at once, before any answer: places picked before, favourites. */
    val instant: List<Stop>,
    /** Stations and stops of local transport ("Nahverkehr"). */
    val local: List<Stop>,
    /** Stations with long-distance trains, here or in a far town ("Fernverkehr"). */
    val longDistance: List<Stop>,
    /** Addresses and points of interest. */
    val places: List<Stop>,
    /** The fixed rows above everything else: the own position (and a place offered when the search opened). */
    val lead: List<Stop> = emptyList(),
) {
    /** All place rows, section after section (contacts and appointments are not places; see [SEARCH_SECTION_ORDER]). */
    val all: List<Stop> get() = lead + instant + local + longDistance + places
}

// Within this distance a stop counts as near and comes first in its group (as NEAR_METERS of the ranking).
private const val NEAR_STATION_METERS = 3_000

/** True for a station served by long-distance trains (ICE, IC, EC, ...; see LONG_DISTANCE_PRODUCTS). */
fun isLongDistanceStation(place: Stop): Boolean = place.kind == PlaceKind.Stop && "express" in place.products

/**
 * The list of a search in its sections: those of [instant] whose id is in [leadIds] as the fixed
 * [SearchSections.lead], the rest of [instant] as they are (each once), then the stations
 * among [found] (each once, none of [instant] again), the near ones (within
 * NEAR_STATION_METERS of [near] or by their own distance) first and else in the order given;
 * those with long-distance trains and the stations of a far town [far] in their own group;
 * then the other places of [found] in their order. Pure: the same input gives the same list,
 * and adding answers to [found] or [far] only ever adds rows to their own section.
 */
fun searchSections(
    instant: List<Stop>,
    found: List<Stop>,
    far: List<Stop> = emptyList(),
    near: Pair<Double, Double>? = null,
    leadIds: Set<String> = emptySet(),
): SearchSections {
    val fixed = instant.distinctBy { it.id }
    val (lead, known) = fixed.partition { it.id in leadIds }
    val seen = fixed.mapTo(HashSet()) { it.id }
    val local = ArrayList<Stop>()
    val longDistance = ArrayList<Stop>()
    val places = ArrayList<Stop>()
    for (place in found) {
        if (!seen.add(place.id)) continue
        when {
            place.kind != PlaceKind.Stop -> places.add(place)
            isLongDistanceStation(place) -> longDistance.add(place)
            else -> local.add(place)
        }
    }
    for (place in far) {
        if (seen.add(place.id)) longDistance.add(place)
    }
    return SearchSections(known, nearFirst(local, near), nearFirst(longDistance, near), places, lead)
}

// [stops] with those near [near] first, each part in the order given (a stable partition).
private fun nearFirst(stops: List<Stop>, near: Pair<Double, Double>?): List<Stop> {
    fun isNear(stop: Stop): Boolean {
        val meters = stop.distanceMeters?.toDouble()
            ?: near?.let { from -> stop.position?.let { groundDistance(from, it) } }
            ?: return false
        return meters <= NEAR_STATION_METERS
    }
    val (close, rest) = stops.partition { isNear(it) }
    return if (close.isEmpty() || rest.isEmpty()) stops else close + rest
}
