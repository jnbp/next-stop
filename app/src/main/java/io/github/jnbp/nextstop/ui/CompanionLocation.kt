package io.github.jnbp.nextstop.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import android.os.SystemClock
import androidx.core.content.ContextCompat
import java.time.OffsetDateTime
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import io.github.jnbp.nextstop.data.AppPrefs
import io.github.jnbp.nextstop.data.CompanionPosition
import io.github.jnbp.nextstop.data.DiagLog
import io.github.jnbp.nextstop.data.FixKind
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.PositionSample
import io.github.jnbp.nextstop.data.RideTracker
import io.github.jnbp.nextstop.data.matchPosition

/**
 * The position of the device for the trip companion ("Standort im Begleiter nutzen"): asked for
 * only while [TripService] runs and stopped with it, never in the background otherwise (the
 * service is a foreground service of type location while it uses it). Balanced positions every
 * [INTERVAL_MILLIS]; precise ones only on a ride close to where to get off, where the stop
 * matters. Each position is matched to the followed journey ([matchPosition]) and kept in
 * [CompanionPosition], which the step of the trip reads; when the step changes by it, the
 * notification and the reminders follow at once. The fixes come in on a thread of their own,
 * where they are matched; only the results are posted to the main thread.
 */
object CompanionLocation {
    private const val TAG = "CompanionLoc"

    // A position at most this often; and no more often than this.
    private const val INTERVAL_MILLIS = 15_000L
    private const val MIN_INTERVAL_MILLIS = 10_000L

    // Within this distance of the end of a ride precise positions are asked for.
    private const val PRECISE_METERS = 2_500.0

    private var manager: LocationManager? = null
    private var listener: LocationListener? = null
    private var precise = false
    @Volatile
    private var app: Context? = null

    // Counts the starts and stops: work for a fix of an earlier start is dropped.
    @Volatile
    private var round = 0

    // Guards the fix kept against a stop that clears it meanwhile.
    private val lock = Any()

    // The one thread the fixes come in on and are matched on (made at the first start).
    private val worker: Executor by lazy {
        Executors.newSingleThreadExecutor { task -> Thread(task, "companion-location").apply { isDaemon = true } }
    }

    /**
     * True when the setting is on, a location permission is granted and the battery saver is
     * not active (no position in the background then: the companion goes by the times).
     */
    fun wanted(context: Context): Boolean =
        AppPrefs(context).companionLocation && permitted(context) && !BatterySaver.active

    /** True when a location permission (precise or approximate) is granted. */
    fun permitted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Starts the positions for the companion; does nothing when they run or are not [wanted]. On the main thread. */
    fun start(context: Context) {
        if (listener != null || !wanted(context)) return
        val appContext = context.applicationContext
        val locations = appContext.getSystemService(LocationManager::class.java) ?: return
        app = appContext
        manager = locations
        val started = ++round
        val made = LocationListener { location -> take(location, started) }
        listener = made
        precise = false
        request(locations, made, precise = false)
        DiagLog.i(TAG) { "companion positions on" }
    }

    /** Stops the positions and forgets the last fix. */
    fun stop() {
        val locations = manager
        val made = listener
        if (locations != null && made != null) {
            try {
                locations.removeUpdates(made)
            } catch (e: SecurityException) {
                // The permission went away: so did the updates.
            }
            DiagLog.i(TAG) { "companion positions off" }
        }
        manager = null
        listener = null
        app = null
        synchronized(lock) {
            round++
            CompanionPosition.clear()
            RideTracker.clear()
        }
    }

    private fun request(locations: LocationManager, made: LocationListener, precise: Boolean) {
        val context = app ?: return
        val request = LocationRequest.Builder(INTERVAL_MILLIS)
            .setMinUpdateIntervalMillis(MIN_INTERVAL_MILLIS)
            .setQuality(if (precise) LocationRequest.QUALITY_HIGH_ACCURACY else LocationRequest.QUALITY_BALANCED_POWER_ACCURACY)
            .build()
        val enabled = locations.getProviders(true)
        // The fused source combines the others; without it satellites and network both report.
        val providers = if (LocationManager.FUSED_PROVIDER in enabled) {
            listOf(LocationManager.FUSED_PROVIDER)
        } else {
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { it in locations.allProviders }
        }
        for (provider in providers) {
            try {
                locations.requestLocationUpdates(provider, request, worker, made)
            } catch (e: SecurityException) {
                // Approximate permission only: the precise source refuses, the others report.
            } catch (e: IllegalArgumentException) {
                // A source switched off between asking and using it.
            }
        }
    }

    /** A fix, on the worker thread: matched there; what follows from it is posted to the main thread. */
    private fun take(location: Location, started: Int) {
        val context = app ?: return
        if (started != round) return
        val journey = TripState.journey.value ?: return
        val now = OffsetDateTime.now()
        val sample = PositionSample(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracy = if (location.hasAccuracy()) location.accuracy else 0f,
            speed = if (location.hasSpeed()) location.speed else null,
            atMillis = System.currentTimeMillis(),
            // A cached position (underground) comes old: it is no evidence then (#224).
            ageMillis = ((SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000L).coerceAtLeast(0L),
        )
        val before = tripStep(journey, now)
        val fix = matchPosition(journey, sample, now, CompanionPosition.fix.value)
        synchronized(lock) {
            // Stopped meanwhile: the fix is not kept.
            if (started != round) return
            // The arrival on board, blended and eased, before anything reads the new fix.
            RideTracker.take(journey, fix, now)
            CompanionPosition.fix.value = fix
        }
        val after = tripStep(journey, now)
        // A new step, or the time of getting off moved by the position: notification and watch follow at once.
        val moved = after.kind == StepKind.GetOff && before.kind == StepKind.GetOff &&
            kotlin.math.abs(java.time.Duration.between(before.time, after.time).seconds) >= 60
        val stepChanged = before.kind != after.kind || before.leg !== after.leg || before.waiting != after.waiting || moved
        // Precise positions only close to where to get off.
        val wantPrecise = (fix.kind == FixKind.OnRide || fix.kind == FixKind.AtEnd || fix.kind == FixKind.OnBoardEarly) &&
            fix.metersToEnd <= PRECISE_METERS
        context.mainExecutor.execute { follow(context, journey, now, fix.kind, after.kind, stepChanged, wantPrecise, started) }
    }

    /** What follows from a fix, on the main thread: the notification, the reminders, the precision asked for. */
    private fun follow(
        context: Context,
        journey: Journey,
        now: OffsetDateTime,
        fixKind: FixKind,
        stepKind: StepKind,
        stepChanged: Boolean,
        wantPrecise: Boolean,
        started: Int,
    ) {
        if (started != round || listener == null) return
        if (stepChanged) {
            DiagLog.d(TAG) { "step by position: $stepKind ($fixKind)" }
            if (TripState.serviceRunning.value) postTripNotification(context, tripNotification(context, journey, now))
        }
        AlightReminders.check(context)
        WakeAlarm.check(context)
        if (wantPrecise != precise) {
            precise = wantPrecise
            val locations = manager ?: return
            val made = listener ?: return
            try {
                locations.removeUpdates(made)
            } catch (e: SecurityException) {
                return
            }
            request(locations, made, wantPrecise)
        }
    }
}
