package io.github.jnbp.nextstop.widget

import android.content.Context
import android.location.Location
import io.github.jnbp.nextstop.data.AppPrefs

/*
 * Where the widgets think the user is.
 *
 * Widgets load with the app in the background (a tap on ↻, the screen coming on, the app
 * coming to the front). They never ask for a position of their own: a request would wake a
 * location provider, and the app may use the battery only in the front or during a followed
 * trip (battery rule). The widgets rely on what is known:
 *
 * 1. the position the app itself stores whenever it gets one (start page, map, companion,
 *    walk view: see [recordWidgetPosition]), with the time it was taken;
 * 2. the newest position Android still keeps for the app (getLastKnownLocation), which costs
 *    nothing and wakes nothing.
 *
 * A widget whose content depends on a position older than [POSITION_NOTE_MINUTES] says how old
 * it is ("Location from 25 min ago"), so a stale nearest stop is never taken for the current one.
 */

/** A position of the user and when it was taken, in epoch milliseconds (0: unknown). */
data class TimedPosition(val latitude: Double, val longitude: Double, val at: Long) {
    val pair: Pair<Double, Double> get() = latitude to longitude
}

// A known position younger than this needs no new request.
internal const val FRESH_POSITION_MILLIS = 2 * 60_000L

// A widget mentions the age of its position from this many minutes on.
internal const val POSITION_NOTE_MINUTES = 10L

// The app stores a new position for the widgets at most this often.
private const val RECORD_EVERY_MILLIS = 30_000L

/** The newer of two positions; one with an unknown time loses against one with a time. */
internal fun newerPosition(a: TimedPosition?, b: TimedPosition?): TimedPosition? = when {
    a == null -> b
    b == null -> a
    b.at > a.at -> b
    else -> a
}

/** True when a position taken at [at] is too old to go without a new request at [now]. */
internal fun needsFreshPosition(position: TimedPosition?, now: Long): Boolean =
    position == null || position.at <= 0L || now - position.at > FRESH_POSITION_MILLIS

/**
 * How many minutes old the position taken at [at] is at [now], when that is worth saying on a
 * widget; null when it is fresh enough, or its time is unknown.
 */
internal fun positionAgeMinutes(at: Long?, now: Long): Long? {
    if (at == null || at <= 0L) return null
    val minutes = (now - at) / 60_000L
    return if (minutes >= POSITION_NOTE_MINUTES) minutes else null
}

internal fun Location.timed(): TimedPosition = TimedPosition(latitude, longitude, time)

/** The position the app stored for the widgets, or null. */
internal fun storedWidgetPosition(prefs: AppPrefs): TimedPosition? =
    prefs.widgetPosition?.let { (latitude, longitude) -> TimedPosition(latitude, longitude, prefs.widgetPositionAt) }

/**
 * Keeps [location] for the widgets when it is newer than the stored one by
 * [RECORD_EVERY_MILLIS] (so a trip that reports every few seconds writes twice a minute at most).
 */
fun recordWidgetPosition(context: Context, location: Location) {
    val prefs = AppPrefs(context)
    val stored = prefs.widgetPositionAt
    if (prefs.widgetPosition != null && location.time - stored < RECORD_EVERY_MILLIS) return
    prefs.widgetPosition = location.latitude to location.longitude
    prefs.widgetPositionAt = location.time
}

/** Stores [position] for the widgets when it is newer than the stored one. */
internal fun recordWidgetPosition(prefs: AppPrefs, position: TimedPosition) {
    val stored = storedWidgetPosition(prefs)
    if (stored != null && position.at <= stored.at) return
    prefs.widgetPosition = position.pair
    prefs.widgetPositionAt = position.at
}
