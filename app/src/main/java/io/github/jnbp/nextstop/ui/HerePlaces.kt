package io.github.jnbp.nextstop.ui

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Whether the user already is at one of their saved places (home, work, …), for the tiles of
 * the start page. There is no journey to a place one is at: the tile says "You are here"
 * instead of "No connection found", which looked broken; with more than two places it is left
 * out, so the row shows the places one can go to.
 */

/** Closer than this to a saved place (the device or the start of the search), the user is there. */
const val HERE_METERS = 150.0

/**
 * Where the provider finds no journey at all from a start this close to the place, it is because
 * start and destination are one (a walk too short to be a connection), not a routing error.
 */
const val HERE_NO_JOURNEY_METERS = 500.0

/** With more saved places than this, the tile of the place the user is at is left out. */
const val HERE_TILE_MAX_PLACES = 2

/**
 * True when the user is at [place] (latitude, longitude; [placeId] its id): the search starts
 * at it ([startId]), the [device] or the [start] of the search lies within [HERE_METERS], or the
 * search to it found nothing ([noJourney]) from a start within [HERE_NO_JOURNEY_METERS]. A
 * search that finds nothing from further away is a real "no connection".
 */
fun atSavedPlace(
    place: Pair<Double, Double>?,
    placeId: String?,
    startId: String?,
    start: Pair<Double, Double>?,
    device: Pair<Double, Double>?,
    noJourney: Boolean,
): Boolean {
    if (placeId != null && placeId == startId) return true
    if (place == null) return false
    if (device != null && metresBetween(device, place) <= HERE_METERS) return true
    if (start == null) return false
    val fromStart = metresBetween(start, place)
    return fromStart <= HERE_METERS || (noJourney && fromStart <= HERE_NO_JOURNEY_METERS)
}

/** True when the tile of a place the user is at is left out of the start page, with [places] places saved. */
fun hideHereTile(places: Int): Boolean = places > HERE_TILE_MAX_PLACES

/** The distance between two positions (latitude, longitude) in metres, on a sphere. */
internal fun metresBetween(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
    val lat1 = Math.toRadians(a.first)
    val lat2 = Math.toRadians(b.first)
    val dLat = lat2 - lat1
    val dLon = Math.toRadians(b.second - a.second)
    val h = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * 6_371_000.0 * asin(sqrt(h.coerceIn(0.0, 1.0)))
}
