package io.github.jnbp.nextstop.ui

import kotlin.math.abs

/*
 * When the names and pills along a journey on the map (see JourneyLabels) are placed anew, and
 * how a pan just moves them. Pure logic, no Android.
 */

/**
 * Where the names and pills of a journey were last placed: the camera's [zoom], [bearing] and
 * [tilt] then and the count of camera rests ([rests], see MapCamera). While the camera only pans
 * (same zoom, bearing, no tilt) their places on the screen just move with it ([panOnly]); a zoom,
 * a turn, a tilt or the camera coming to rest places them anew.
 */
class LabelPlacement {
    var zoom = Double.NaN
        private set
    var bearing = Double.NaN
        private set
    var tilt = Double.NaN
        private set
    var rests = Int.MIN_VALUE
        private set

    /** True when a camera at [zoom], [bearing], [tilt] after [rests] rests only panned since the last placement. */
    fun panOnly(zoom: Double, bearing: Double, tilt: Double, rests: Int): Boolean =
        !this.zoom.isNaN() && rests == this.rests && tilt == 0.0 && this.tilt == 0.0 &&
            abs(zoom - this.zoom) < SAME_ZOOM && abs(bearing - this.bearing) < SAME_BEARING

    /** Placed anew for this camera. */
    fun placed(zoom: Double, bearing: Double, tilt: Double, rests: Int) {
        this.zoom = zoom
        this.bearing = bearing
        this.tilt = tilt
        this.rests = rests
    }

    /** Placed nothing yet (a new journey or map). */
    fun reset() {
        zoom = Double.NaN
    }
}

// A zoom or bearing closer than this counts as the same: a pan in floating point.
private const val SAME_ZOOM = 1e-6
private const val SAME_BEARING = 1e-4

/** Moves the [count] places in [xs] and [ys] by [dx], [dy]: a pan of the camera, nothing worked out anew. */
fun shiftPlaces(xs: FloatArray, ys: FloatArray, count: Int, dx: Float, dy: Float) {
    for (index in 0 until count) {
        xs[index] += dx
        ys[index] += dy
    }
}
