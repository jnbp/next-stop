package io.github.jnbp.nextstop.ui

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import java.time.OffsetDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import io.github.jnbp.nextstop.data.ownServerUrl
import io.github.jnbp.nextstop.data.AppPrefs
import io.github.jnbp.nextstop.data.ChangePositions
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.RoutingSource
import io.github.jnbp.nextstop.data.TransitousSource
import io.github.jnbp.nextstop.data.refreshed
import io.github.jnbp.nextstop.wear.PhoneBridge
import io.github.jnbp.nextstop.widget.WidgetUpdates

/**
 * The followed trip, shared by the app and [TripService] for as long as the process lives.
 * Nothing of it is stored.
 */
object TripState {
    /** The journey that is followed, with its latest live data; null without a trip. */
    val journey = MutableStateFlow<Journey?>(null)

    /** True while [TripService] keeps the trip fresh: the app then leaves the refresh to it. */
    val serviceRunning = MutableStateFlow(false)

    /** Set by a tap on the notification of the trip: the app opens the page of the trip. */
    val openRequested = MutableStateFlow(false)

    /**
     * True while the trip is in the mode "Every minute counts" (see [FasterWatch]): Sprint
     * changes and the shortcut alert for this trip. It belongs to this trip and is not kept.
     */
    val everyMinute = MutableStateFlow(false)

    /**
     * True while this trip looks for something faster in the background and notifies
     * ("Abkürzungs-Alarm", see [FasterWatch]): from the setting at the start of the trip,
     * switched on with [everyMinute], and switchable for this trip alone.
     */
    val shortcutAlert = MutableStateFlow(false)

    /** A faster way found on the way, until it is taken or put aside. */
    val faster = MutableStateFlow<FasterOffer?>(null)
}

/**
 * A process-wide flow used like a variable of a composable: reading it in composition
 * follows [seen], so a change from anywhere draws the page again; a read always gets the
 * latest value and a write goes straight to the flow.
 */
class FlowVar<T>(
    private val flow: MutableStateFlow<T>,
    private val seen: State<T>,
) : MutableState<T> {
    // A MutableState, so the standard delegate functions of Compose apply to it.
    override var value: T
        get() {
            seen.value
            return flow.value
        }
        set(value) {
            flow.value = value
        }

    override fun component1(): T = value

    override fun component2(): (T) -> Unit = { value = it }
}

/**
 * [fresh] with the stops on the way that [known] had for a ride where the fresh answer
 * brings none: the timeline of a followed trip keeps every ride with all its stops.
 */
internal fun withKnownStops(fresh: Journey, known: Journey): Journey {
    if (fresh.legs.size != known.legs.size) return fresh
    return fresh.copy(
        legs = fresh.legs.mapIndexed { index, leg ->
            val before = known.legs[index]
            if (leg.stopovers.isEmpty() && before.line == leg.line && before.from == leg.from) {
                leg.copy(stopovers = before.stopovers)
            } else {
                leg
            }
        },
    )
}

// The followed journey is loaded again this often.
private const val REFRESH_MILLIS = 30_000L

// The notification is drawn again this often, and at once when the journey changes.
private const val NOTE_MILLIS = 15_000L

// The trip lets go of the notification this long after its arrival, and the app ends the trip then.
internal const val AFTER_ARRIVAL_MINUTES = 5L

internal const val ACTION_END_TRIP = "io.github.jnbp.nextstop.END_TRIP"

/**
 * Starts [TripService] for the trip in [TripState]. Only called while the app is in front,
 * where Android allows it. Returns false when it could not be started.
 */
fun startTripService(context: Context): Boolean {
    TripState.serviceRunning.value = true
    return try {
        context.startForegroundService(Intent(context, TripService::class.java))
        true
    } catch (e: IllegalStateException) {
        // Includes the refusal to start a foreground service from the background.
        TripState.serviceRunning.value = false
        false
    }
}

/**
 * Keeps a followed trip going while the screen is off or the app is in the background:
 * loads the journey again every 30 seconds and shows its next step as an ongoing
 * notification. It ends with the trip, five minutes after the arrival, or when the
 * notification can no longer be seen.
 */
class TripService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    // The settings are read anew for every request, like in the app.
    private val source by lazy {
        val prefs = AppPrefs(applicationContext)
        RoutingSource(choice = { prefs.sourceChoice }, userAgent = TransitousSource.userAgent(applicationContext), bvgUrl = { ownServerUrl(prefs.ownServer) })
    }
    private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_END_TRIP) {
            TripState.journey.value = null
            finish()
            return START_NOT_STICKY
        }
        // Every start as a foreground service has to show its notification right away.
        val journey = TripState.journey.value
        if (!enterForeground(tripNotification(this, journey, OffsetDateTime.now())) || journey == null) {
            finish()
            return START_NOT_STICKY
        }
        if (!running) {
            running = true
            TripState.serviceRunning.value = true
            scope.launch { keepFresh() }
            scope.launch { keepShown() }
            scope.launch { PhoneBridge.mirrorTrip(this@TripService) }
            scope.launch { AlightReminders.follow(this@TripService) }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        AlightReminders.cancel(this)
        TripState.serviceRunning.value = false
        WidgetUpdates.tripChanged(this)
        super.onDestroy()
    }

    private fun enterForeground(notification: Notification): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(TRIP_NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(TRIP_NOTIFICATION, notification)
        }
        true
    } catch (e: IllegalStateException) {
        false
    } catch (e: SecurityException) {
        false
    }

    private fun finish() {
        running = false
        TripState.serviceRunning.value = false
        scope.coroutineContext.cancelChildren()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // The first request for a journey comes at once: it brings the stops on the way of
    // every ride, which the search left out. A journey changed in the meantime, by a switch
    // at a change for example, keeps that change.
    private suspend fun keepFresh() {
        TripState.journey.map { it?.id }.distinctUntilChanged().collectLatest { id ->
            if (id == null) return@collectLatest
            while (true) {
                val known = TripState.journey.value ?: return@collectLatest
                if (!known.refreshable) return@collectLatest
                try {
                    // After a switch on the way each half is loaded by its own handle.
                    val fresh = source.refreshed(known, stopovers = true, polylines = true)
                    if (fresh != null) TripState.journey.compareAndSet(known, withKnownStops(fresh, known))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Keep the last known times and try again.
                }
                delay(REFRESH_MILLIS)
            }
        }
    }

    private suspend fun keepShown() {
        // Where to board for a change is read from the dataset that comes with the app.
        ChangePositions.load(this)
        TripState.journey.collectLatest { journey ->
            if (journey == null) {
                WidgetUpdates.tripChanged(this@TripService)
                finish()
                return@collectLatest
            }
            while (true) {
                val now = OffsetDateTime.now()
                if (now.isAfter(journey.arrival.plusMinutes(AFTER_ARRIVAL_MINUTES)) ||
                    !tripNotificationsAllowed(this)
                ) {
                    finish()
                    return@collectLatest
                }
                postTripNotification(this, tripNotification(this, journey, now))
                // The trip and automatic widgets follow every change of the next step.
                WidgetUpdates.tripChanged(this)
                delay(NOTE_MILLIS)
            }
        }
    }
}
