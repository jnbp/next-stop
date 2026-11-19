package io.github.jnbp.nextstop.ui

/*
 * Where a pick on the map (MapPick) starts and where its "centre on me" button goes (#205).
 * Pure logic, no Android. The pin stands in the middle of the screen, so the camera itself is
 * put on the place: the user's own position where it is known, else the map stays where it is.
 */

/** The zoom a pick on the map looks at the own position with, at least: streets and houses. */
const val MAP_PICK_ZOOM = 16.0

/** The camera of a pick on the map at the own position: the point and the zoom. */
data class MapPickCamera(val latitude: Double, val longitude: Double, val zoom: Double)

/**
 * Where the camera of a pick on the map goes: the own position ([own], the device's where known,
 * else the place the app last had, [ownIsReal] false for the fallback with no position at all)
 * at [currentZoom] or closer (see [MAP_PICK_ZOOM]); null without a real position (the map stays).
 */
fun mapPickCamera(own: Pair<Double, Double>?, ownIsReal: Boolean, currentZoom: Double): MapPickCamera? {
    if (own == null || !ownIsReal) return null
    val zoom = if (currentZoom.isNaN() || currentZoom < MAP_PICK_ZOOM) MAP_PICK_ZOOM else currentZoom
    return MapPickCamera(own.first, own.second, zoom)
}
