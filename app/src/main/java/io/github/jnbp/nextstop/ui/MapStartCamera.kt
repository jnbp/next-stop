package io.github.jnbp.nextstop.ui

/*
 * Where a map that was just made is put when nothing else places it (#228). Pure logic, no
 * Android.
 *
 * MapLibre makes a new map facing the whole world (zoom 0). MapBackground puts it on the own
 * position only while the map follows it; a map made while the view is held (a stop picked by
 * hand, a place pinned, a stop shown from Departures) skipped that placement, and the camera
 * calls made before the map existed (showStop, showStationOnMap) went to a map that was still
 * null. So the Map tab opened on the world under an open stop card.
 */

/** Where to put a fresh map: the place and whether it is a stop (stop zoom) or the own area (start zoom). */
data class StartCameraPlace(val latitude: Double, val longitude: Double, val atStop: Boolean)

/**
 * The first camera of a map facing the world ([zoom], see [freshCamera]), or null when the map
 * is already placed, when MapBackground places it itself ([follow]) or when the page frames it
 * on its own ([framesItself]: a trip, a plan, a course or a walk on the map, see #122).
 * A held view goes to the stop marked on the map ([markedStop]), else to a pinned place
 * ([pinned]); without either to the own area ([own]).
 */
fun mapStartCamera(
    zoom: Double,
    follow: Boolean,
    framesItself: Boolean,
    markedStop: Pair<Double, Double>?,
    pinned: Pair<Double, Double>?,
    own: Pair<Double, Double>,
): StartCameraPlace? {
    if (!freshCamera(zoom) || follow || framesItself) return null
    val place = markedStop ?: pinned
    return if (place != null) {
        StartCameraPlace(place.first, place.second, atStop = true)
    } else {
        StartCameraPlace(own.first, own.second, atStop = false)
    }
}
