package io.github.jnbp.nextstop.widget

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.location.LocationRequest
import android.os.CancellationSignal
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import io.github.jnbp.nextstop.data.AppPrefs

/*
 * Where the widgets think the user is.
 *
 * Widgets load in a WorkManager job, with the app in the background. With the location
 * permission "while using the app" only (the app does not ask for ACCESS_BACKGROUND_LOCATION),
 * Android gives a background job no fresh position: a request returns nothing. So the widgets
 * rely on what is known:
 *
 * 1. the position the app itself stores whenever it gets one (start page, map, companion,
 *    walk view: see [recordWidgetPosition]), with the time it was taken;
 * 2. the newest position Android still keeps for the app (getLastKnownLocation);
 * 3. a single, balanced request of the fused provider of the platform (no Google Play
 *    services needed) when both are older than [FRESH_POSITION_MILLIS]. It costs little,
 *    answers whenever Android lets the app locate at that moment (the app is open, or the
 *    user granted "allow all the time" in the system settings), and simply times out otherwise.
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

// How long the widget job waits for the one balanced position request.
private const val FRESH_FIX_TIMEOUT_MILLIS = 8_000L

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

/**
 * One balanced request of the fused provider of the platform (network and Wi-Fi rather than
 * satellites), or of the network where it is missing. Null without permission, when Android
 * gives the app no position in the background, or when none comes in time.
 */
internal suspend fun balancedWidgetLocation(context: Context): Location? {
    if (!hasLocationPermission(context)) return null
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    val enabled = manager.getProviders(true)
    val provider = listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER)
        .firstOrNull { it in enabled } ?: return null
    val request = LocationRequest.Builder(0L)
        .setQuality(LocationRequest.QUALITY_BALANCED_POWER_ACCURACY)
        .setDurationMillis(FRESH_FIX_TIMEOUT_MILLIS)
        .build()
    return withTimeoutOrNull(FRESH_FIX_TIMEOUT_MILLIS) {
        suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            try {
                manager.getCurrentLocation(provider, request, signal, context.mainExecutor) { location ->
                    if (continuation.isActive) continuation.resume(location)
                }
            } catch (e: SecurityException) {
                continuation.resume(null)
            } catch (e: IllegalArgumentException) {
                continuation.resume(null)
            }
        }
    }
}
