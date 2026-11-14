package io.github.jnbp.nextstop.wear.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/** A position, where it came from, and when it was taken (epoch milliseconds, 0 when unknown). */
data class Position(val latitude: Double, val longitude: Double, val fromPhone: Boolean, val timeMillis: Long = 0)

/**
 * Counts the times the location permission was granted while the app runs, so screens that
 * found no position load again (keys of their effects).
 */
val locationGrants = MutableStateFlow(0)

fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

/**
 * Where the user is: a recent last known position of the watch right away; else a fresh fix
 * (its own GPS, or the phone's position that Wear OS hands through), waiting at most
 * [FIX_TIMEOUT_MILLIS]; else the newer of the last known position of the watch and the last
 * position the phone sent. Null only when none of these exists. Without [waitForFix] the
 * fresh fix is skipped. Each step is logged under [LOG_TAG].
 */
suspend fun currentPosition(context: Context, waitForFix: Boolean = true): Position? {
    val now = System.currentTimeMillis()
    var watch: Position? = null
    if (!hasLocationPermission(context)) {
        Log.d(LOG_TAG, "position: no location permission on the watch")
    } else {
        val manager = context.getSystemService(LocationManager::class.java)
        if (manager == null) {
            Log.d(LOG_TAG, "position: no LocationManager")
        } else {
            val known = lastKnown(manager)
            if (known != null && now - known.time < RECENT_MILLIS) {
                Log.d(LOG_TAG, "position: recent last known fix of ${known.provider}, ${(now - known.time) / 1000} s old")
                return Position(known.latitude, known.longitude, fromPhone = false, timeMillis = known.time)
            }
            // A tile has no time to wait for a fix: it takes what is known.
            val fresh = if (waitForFix) withTimeoutOrNull(FIX_TIMEOUT_MILLIS) { freshFix(context, manager) } else null
            if (fresh != null) {
                Log.d(LOG_TAG, "position: fresh fix of ${fresh.provider}")
                return Position(fresh.latitude, fresh.longitude, fromPhone = false, timeMillis = fresh.time.takeIf { it > 0 } ?: now)
            }
            Log.d(LOG_TAG, "position: no fresh fix within $FIX_TIMEOUT_MILLIS ms; last known: ${known?.let { "${(now - it.time) / 1000} s old" } ?: "none"}")
            if (known != null) watch = Position(known.latitude, known.longitude, fromPhone = false, timeMillis = known.time)
        }
    }
    val places = WatchStore.places.value
    val latitude = places.phoneLatitude
    val longitude = places.phoneLongitude
    val phone = if (latitude != null && longitude != null) {
        Position(latitude, longitude, fromPhone = true, timeMillis = places.phoneLocationTime)
    } else {
        null
    }
    val chosen = listOfNotNull(watch, phone).maxByOrNull { it.timeMillis }
    Log.d(
        LOG_TAG,
        when {
            chosen == null -> "position: none (no watch fix, no position from the phone)"
            chosen.fromPhone -> "position: from the phone, ${(now - chosen.timeMillis) / 1000} s old"
            else -> "position: last known fix of the watch, ${(now - chosen.timeMillis) / 1000} s old"
        },
    )
    return chosen
}

@Suppress("MissingPermission")
private suspend fun freshFix(context: Context, manager: LocationManager): Location? {
    val providers = listOf("fused", LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
    val provider = providers.firstOrNull { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
    if (provider == null) {
        Log.d(LOG_TAG, "position: none of $providers is enabled")
        return null
    }
    return suspendCancellableCoroutine { continuation ->
        val cancel = android.os.CancellationSignal()
        continuation.invokeOnCancellation { cancel.cancel() }
        try {
            manager.getCurrentLocation(provider, cancel, ContextCompat.getMainExecutor(context)) { location ->
                if (location == null) Log.d(LOG_TAG, "position: $provider gave no fix")
                if (continuation.isActive) continuation.resume(location)
            }
        } catch (e: Exception) {
            Log.d(LOG_TAG, "position: getCurrentLocation($provider) failed: $e")
            if (continuation.isActive) continuation.resume(null)
        }
    }
}

@Suppress("MissingPermission")
private fun lastKnown(manager: LocationManager): Location? =
    manager.getProviders(true).mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
        .maxByOrNull { it.time }

// The emulator and a watch indoors often get no fix at all; the fallbacks follow after this.
private const val FIX_TIMEOUT_MILLIS = 10_000L

// A last known fix younger than this is good enough to skip waiting for a fresh one.
private const val RECENT_MILLIS = 2 * 60_000L
