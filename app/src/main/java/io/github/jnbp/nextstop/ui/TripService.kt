package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.withKnownNames
import io.github.jnbp.nextstop.data.keptKinds
import io.github.jnbp.nextstop.data.ReturnProbe
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import io.github.jnbp.nextstop.data.followedTripLive
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
import io.github.jnbp.nextstop.data.DiagLog
import io.github.jnbp.nextstop.data.ownServerUrl
import io.github.jnbp.nextstop.data.AppPrefs
import io.github.jnbp.nextstop.data.ChangePositions
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.RoutingSource
import io.github.jnbp.nextstop.data.TransitousSource
import io.github.jnbp.nextstop.data.TripStats
import io.github.jnbp.nextstop.data.refreshed
import io.github.jnbp.nextstop.data.tripMarkKey
import io.github.jnbp.nextstop.data.tripRefreshMillis
import io.github.jnbp.nextstop.data.withKnownSeating
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
 * Ends the followed trip: kept for the travel statistics when it was ridden to its end (see
 * TripStats), its mode "Every minute counts" and its shortcut alert off, the trip gone (the
 * service stops with it). The one end of every way out: the app's own (AppRoot.endTrip), "Beenden"
 * and "Maps & beenden" of the notification, "Open and end" of the last walk and the end on the
 * watch, so they cannot drift apart. What the app shows of the trip goes in AppRoot when the trip is gone.
 */
fun endFollowedTrip(context: Context) {
    TripStats.ended(context.applicationContext, TripState.journey.value)
    TripState.everyMinute.value = false
    TripState.shortcutAlert.value = false
    TripState.journey.value = null
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

/**
 * The followed [known] journey loaded again, for the refresh of the trip service and of the app
 * in front. While it runs on a stand-in (Transitous for a trip in Berlin, see
 * RoutingSource.onStandIn), the preferred provider is asked for it now and then, as [probe]
 * says (#185); once it answers with the same journey, that one comes back with its handle and
 * the trip goes on there. Names and planned times stay those of [known] (see withKnownNames),
 * so nothing jumps on screen.
 */
internal suspend fun RoutingSource.followedFresh(known: Journey, probe: ReturnProbe): Journey? {
    val now = System.currentTimeMillis()
    if (onStandIn(known) && probe.due(now)) {
        val back = try {
            preferredAgain(known)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        if (back != null) {
            probe.reset(now)
            DiagLog.d(TRIP_SOURCE_LOG_TAG) { "followed trip back on the preferred provider" }
            return back
        }
        probe.failed(now)
    }
    return refreshed(known, stopovers = true, polylines = true)?.let { withKnownNames(it, known) }
}

private const val TRIP_SOURCE_LOG_TAG = "Sources"

// The followed journey is loaded again this often.
private const val REFRESH_MILLIS = 30_000L

// The notification is drawn again this often, and at once when the journey changes.
private const val NOTE_MILLIS = 15_000L


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
    private companion object {
        const val TAG = "TripService"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    // The settings are read anew for every request, like in the app.
    private val source by lazy {
        val prefs = AppPrefs(applicationContext)
        RoutingSource(choice = { prefs.sourceChoice }, userAgent = TransitousSource.userAgent(applicationContext), bvgUrl = { ownServerUrl(prefs.ownServer) })
    }
    private var running = false

    // True while the service runs with the type location (the position is followed).
    private var locationType = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_END_TRIP) {
            // Ended from the notification: as the app ends it.
            endFollowedTrip(applicationContext)
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
            // A planned trip's reminder for the journey now followed goes (#172).
            scope.launch(Dispatchers.IO) { PlannedTripAlarms.sweepNotices(this@TripService) }
            scope.launch { keepShown() }
            scope.launch { PhoneBridge.mirrorTrip(this@TripService) }
            scope.launch { AlightReminders.follow(this@TripService) }
            scope.launch { WakeAlarm.follow(this@TripService) }
            scope.launch {
                TripState.journey.collect {
                    PlatformAlerts.update(this@TripService, it)
                    // With every new state of the trip: what no longer applies goes (#172).
                    TripNoticeSweep.sweep(this@TripService, ended = it == null)
                }
            }
            // The mark of the trip, so an update of the app does not end it (#117): written
            // only when another trip is followed, not with every refresh of the live times.
            scope.launch(Dispatchers.IO) {
                var written: String? = null
                TripState.journey.collect { journey ->
                    val key = tripMarkKey(journey)
                    if (journey != null && key != written) {
                        TripMark.write(this@TripService, journey)
                        written = key
                    }
                }
            }
        }
        // The position of the device, only while the companion runs and only where the service
        // could take the type location (see enterForeground). On every start, so a later start
        // from the front or the setting switched on during the trip brings it (start is idempotent).
        if (locationType) CompanionLocation.start(this)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        // Ended as it should: nothing to follow again after an update (see TripMark).
        TripMark.clear(this)
        CompanionLocation.stop()
        WakeAlarm.reset(this)
        PlatformAlerts.update(this, null)
        ConnectionRescue.clear()
        AlightReminders.cancel(this)
        // The trip is over: its notifications go with it, not those of disruptions (#172).
        TripNoticeSweep.sweep(this, ended = true)
        TripState.serviceRunning.value = false
        WidgetUpdates.tripChanged(this)
        super.onDestroy()
    }

    /**
     * Enters the foreground with [notification]: with the type location as well where the
     * position is wanted ("Standort im Begleiter nutzen" and the permission), else as before.
     * Started from the background (a planned trip, the watch) Android may refuse the type
     * location; the service then runs without the position.
     */
    private fun enterForeground(notification: Notification): Boolean {
        if (CompanionLocation.wanted(this)) {
            try {
                // Before Android 14 the system knows no special use: the type location alone.
                val types = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                }
                startForeground(TRIP_NOTIFICATION, notification, types)
                locationType = true
                return true
            } catch (e: SecurityException) {
                DiagLog.w(TAG, e) { "type location refused" }
            } catch (e: IllegalArgumentException) {
                DiagLog.w(TAG, e) { "type location refused" }
            } catch (e: IllegalStateException) {
                DiagLog.w(TAG, e) { "type location refused" }
            }
        }
        locationType = false
        CompanionLocation.stop()
        return try {
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
    }

    private fun finish() {
        running = false
        TripMark.clear(this)
        CompanionLocation.stop()
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
            // Back to the preferred provider, should the trip run on a stand-in (#185).
            val probe = ReturnProbe(System.currentTimeMillis())
            while (true) {
                val known = TripState.journey.value ?: return@collectLatest
                if (!known.refreshable) return@collectLatest
                try {
                    // After a switch on the way each half is loaded by its own handle.
                    val fresh = source.followedFresh(known, probe)
                    if (fresh != null && TripState.journey.compareAndSet(known, followedTripLive.steady(withKnownPlannedTimes(withKnownStops(withKnownSeating(keptKinds(fresh, known), known), known), known), known, OffsetDateTime.now()))) {
                        LiveFreshness.mark(LiveSource.Trip)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Keep the last known times and try again.
                }
                // The change ahead broke by a delay: the ways on, once per change, for the
                // notification and the watch's card, also with the app off the screen (#98).
                TripState.journey.value?.let { followed ->
                    try {
                        RescueInBackground.check(applicationContext, source, followed)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Next round again.
                    }
                }
                // Sooner when the user asks for fresh times (a tap on the age of the data).
                // In the last minutes before a departure every 20 s (see tripRefreshMillis): the board moves then.
                val next = TripState.journey.value?.let { tripRefreshMillis(it, OffsetDateTime.now(), REFRESH_MILLIS) } ?: REFRESH_MILLIS
                LiveFreshness.awaitRefresh(LiveSource.Trip, next)
            }
        }
    }

    private suspend fun keepShown() {
        // Where to board for a change is read from the dataset that comes with the app.
        ChangePositions.load(this)
        // The trip before, for the statistics when it ends (see TripStats.ended).
        var before: Journey? = null
        TripState.journey.collectLatest { journey ->
            if (journey == null) {
                TripStats.ended(applicationContext, before)
                before = null
                WidgetUpdates.tripChanged(this@TripService)
                finish()
                return@collectLatest
            }
            before = journey
            while (true) {
                val now = OffsetDateTime.now()
                // Still on board by position (a train later than its data), the trip goes on, for an hour at most.
                val over = tripOver(journey, now)
                if (over) TripStats.ended(applicationContext, journey, now)
                if (over ||
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
