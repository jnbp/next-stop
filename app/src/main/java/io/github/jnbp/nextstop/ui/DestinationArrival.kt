package io.github.jnbp.nextstop.ui

import kotlin.math.cos
import kotlin.math.hypot

/*
 * When the destination badge of a followed journey goes (#222): once the traveller has arrived,
 * the badge fades out, once, and only the own dot is left where the journey ends. Pure logic,
 * no Android.
 */

/** The own position this near the end of the journey counts as arrived (metres). */
const val DESTINATION_REACHED_METRES = 35.0

/**
 * True when the traveller has arrived: the followed trip says so ([tripArrived], its ride state
 * at the destination), or the own position ([own]) lies within [DESTINATION_REACHED_METRES] of
 * the end of the journey ([end]).
 */
fun destinationReached(tripArrived: Boolean, own: Pair<Double, Double>?, end: Pair<Double, Double>?): Boolean {
    if (tripArrived) return true
    if (own == null || end == null) return false
    val north = (end.first - own.first) * 111_320.0
    val east = (end.second - own.second) * 111_320.0 * cos(Math.toRadians((own.first + end.first) / 2))
    return hypot(north, east) <= DESTINATION_REACHED_METRES
}
