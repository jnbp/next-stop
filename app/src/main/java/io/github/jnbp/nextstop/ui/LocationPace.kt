package io.github.jnbp.nextstop.ui

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sqrt

/*
 * How often the start page and the map tab ask for the position while the own dot is shown:
 * every second while the user moves, calmly while they stand. Pure logic, no Android.
 */

/** Faster than this (m/s) the user moves. */
const val USER_MOVING_SPEED = 0.7f

/** Further than this (metres, or the fix's accuracy where that is wider) from where they stood, the user moves. */
const val USER_MOVING_METRES = 6.0

/** This long (ms) without a sign of moving, the user stands again. */
const val STANDING_AFTER_MILLIS = 10_000L

/** The calm pace while the user stands: this interval (ms), balanced, and this distance (metres). */
const val STANDING_INTERVAL_MILLIS = 5_000L
const val STANDING_MIN_METERS = 3f

/**
 * Whether the user moves, from the fixes of the device: a speed above [USER_MOVING_SPEED], or a
 * fix further than [USER_MOVING_METRES] (widened by its accuracy) from where they last stood. It
 * says "moving" at the first such fix and "standing" [STANDING_AFTER_MILLIS] after the last one.
 */
class MovementWatch {
    private var anchorLat = Double.NaN
    private var anchorLon = Double.NaN
    private var movedAt = Long.MIN_VALUE

    /** A fix at [atMillis]: [speed] m/s (NaN where unknown), [accuracy] metres (0 where unknown). True while the user moves. */
    fun fix(atMillis: Long, latitude: Double, longitude: Double, speed: Float, accuracy: Float): Boolean {
        val fast = !speed.isNaN() && speed > USER_MOVING_SPEED
        val away = !anchorLat.isNaN() &&
            distance(anchorLat, anchorLon, latitude, longitude) > max(USER_MOVING_METRES, accuracy.toDouble())
        if (anchorLat.isNaN() || fast || away) {
            anchorLat = latitude
            anchorLon = longitude
        }
        if (fast || away) movedAt = atMillis
        return moving(atMillis)
    }

    /** True while the last sign of moving is less than [STANDING_AFTER_MILLIS] old at [nowMillis]. */
    fun moving(nowMillis: Long): Boolean =
        movedAt != Long.MIN_VALUE && nowMillis - movedAt < STANDING_AFTER_MILLIS

    private fun distance(lat: Double, lon: Double, otherLat: Double, otherLon: Double): Double {
        val north = (otherLat - lat) * 111_320.0
        val east = (otherLon - lon) * 111_320.0 * cos(Math.toRadians(lat))
        return sqrt(north * north + east * east)
    }
}

/** A request for the position: every [intervalMillis], after [minMeters], [precise] or balanced. */
data class LocationAsk(val intervalMillis: Long, val minMeters: Float, val precise: Boolean)

/**
 * The request of the start page and the map tab while only the own dot asks for fast fixes
 * ([dotOnly]: no trip, walk or riding follow of their own): every second (precise; [dotMillis]
 * with the battery saver) while the user [moving], else every five seconds, balanced, after
 * three metres. [base] is the request otherwise.
 */
fun dotLocationAsk(dotOnly: Boolean, moving: Boolean, dotMillis: Long, precise: Boolean, base: LocationAsk): LocationAsk = when {
    !dotOnly -> base
    moving -> LocationAsk(dotMillis, 0f, precise)
    else -> LocationAsk(STANDING_INTERVAL_MILLIS, STANDING_MIN_METERS, false)
}
