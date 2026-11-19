package io.github.jnbp.nextstop.ui

/*
 * When the beat of the live pulse on the map (see VehicleLayer) is drawn: every beat has the map
 * draw a whole frame, so it beats only while a live train is on the screen, and a frame the map
 * draws anyway for the vehicles carries the beat with it. Pure logic, no Android.
 */

/** How long (ms) to wait at [now] before the next beat after the last one at [lastAt], every [frameMillis]; 0 when due. */
fun pulseWait(now: Long, lastAt: Long, frameMillis: Long): Long = (lastAt + frameMillis - now).coerceAtLeast(0L)

/**
 * True when [latitude], [longitude] lies within the box from [south] to [north] and [west] to
 * [east] (degrees; a box across the date line has west > east), widened by [margin] of its
 * size on every side, so a train just entering the screen beats already.
 */
fun inViewBox(
    latitude: Double,
    longitude: Double,
    south: Double,
    west: Double,
    north: Double,
    east: Double,
    margin: Double = 0.1,
): Boolean {
    if (latitude.isNaN() || longitude.isNaN()) return false
    val padLat = (north - south) * margin
    if (latitude < south - padLat || latitude > north + padLat) return false
    return if (west <= east) {
        val padLon = (east - west) * margin
        longitude >= west - padLon && longitude <= east + padLon
    } else {
        longitude >= west || longitude <= east
    }
}
