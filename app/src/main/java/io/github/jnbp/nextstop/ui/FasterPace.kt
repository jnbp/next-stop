package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.Journey

/*
 * How often the watch for a faster way (see FasterWatch) searches: often while a change of the
 * trip is near and the app is in front, calmly otherwise. Pure logic, no Android.
 */

/** A search this often while a change is near and the app is in front (ms)… */
const val FASTER_NEAR_ROUND_MILLIS = 75_000L

/** …and this often otherwise: no change soon, or the app in the background (ms). */
const val FASTER_CALM_ROUND_MILLIS = 150_000L

/** A change counts as near within this long (ms). */
const val FASTER_NEAR_CHANGE_MILLIS = 15 * 60_000L

/**
 * True when a change of [journey] (the end of a leg that another one follows, or the start of
 * that next one) lies between [nowMillis] and [windowMillis] later.
 */
fun changeNear(journey: Journey, nowMillis: Long, windowMillis: Long = FASTER_NEAR_CHANGE_MILLIS): Boolean {
    val legs = journey.legs
    val until = nowMillis + windowMillis
    for (index in 0 until legs.size - 1) {
        val arrival = legs[index].arrival.toInstant().toEpochMilli()
        val departure = legs[index + 1].departure.toInstant().toEpochMilli()
        if (arrival in nowMillis..until || departure in nowMillis..until) return true
    }
    return false
}

/** How long (ms) the watch waits between two searches: [changeIsNear] and [foreground] keep the short pace. */
fun fasterRoundMillis(changeIsNear: Boolean, foreground: Boolean): Long =
    if (changeIsNear && foreground) FASTER_NEAR_ROUND_MILLIS else FASTER_CALM_ROUND_MILLIS
