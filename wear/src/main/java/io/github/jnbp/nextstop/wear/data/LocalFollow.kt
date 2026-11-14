package io.github.jnbp.nextstop.wear.data

import android.content.Context
import android.util.Log
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import io.github.jnbp.nextstop.wear.complication.NextComplicationService
import io.github.jnbp.nextstop.wear.tile.NextTileService

/**
 * A trip the watch follows on its own ("Hier verfolgen"), without the phone: the journey is
 * loaded again every 30 seconds by its handle and its next step worked out every 15 seconds.
 *
 * While it runs, [TripService] keeps the process alive as a foreground service whose
 * notification is the trip's ongoing activity; the service follows [following] and stops
 * itself. Where Android does not allow the service, the loop lives as long as the process
 * of the app.
 *
 * Called from the app (main thread) and from the data listener (a thread of its own): the
 * changes of the followed trip are serialised by [lock].
 */
object LocalFollow {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val lock = Any()

    @Volatile
    private var job: Job? = null
    private val client = BvgClient()

    private val followingState = MutableStateFlow(false)

    /** True while the watch follows a trip itself; [TripService] runs as long as this holds. */
    val following: StateFlow<Boolean> = followingState.asStateFlow()

    @Volatile
    var journey: Journey? = null
        private set

    /** Where the followed journey leads, for "Missed it"; null when not known. */
    @Volatile
    var destination: Place? = null
        private set

    /** The step of the journey at [now], with the destination for "Missed it". */
    fun snapshot(chosen: Journey, now: Long): TripSnapshot = snapshotOf(chosen, now).copy(target = destination)

    /**
     * The one "Start" of the watch: follows [chosen] here at once and asks the phone to
     * follow it too. Once the phone's trip state arrives, the watch hands over to it
     * ([handOver]); without a phone the watch goes on alone.
     */
    fun startEverywhere(context: Context, chosen: Journey, target: Place?) {
        val app = context.applicationContext
        start(app, chosen, target)
        val token = chosen.refreshToken ?: return
        scope.launch {
            val sent = WatchStore.startOnPhone(app, token)
            Log.d(LOG_TAG, "trip: sent to the phone: $sent")
        }
    }

    /**
     * The phone follows a trip now: the watch stops following its own (without clearing the
     * trip state, which is the phone's from now on). Its ongoing activity stays and shows the
     * phone's states.
     */
    fun handOver(context: Context) {
        synchronized(lock) {
            if (job == null) return
            Log.d(LOG_TAG, "trip: handed over to the phone")
            stop(context, clear = false)
        }
    }

    /** Ends the trip on the watch and on the phone (the "End trip" of the trip screen). */
    fun endEverywhere(context: Context) {
        val app = context.applicationContext
        synchronized(lock) {
            // The trip shown: the phone's (by its key) or the watch's own (by its handle, which
            // the phone uses as the key of a trip the watch started there).
            val shown = WatchStore.trip.value
            WatchStore.rememberEnded(app, if (shown.fromPhone) shown.tripKey else journey?.refreshToken)
            stop(app, clear = false)
            WatchStore.saveTrip(app, TripSnapshot.NONE)
        }
        NextTileService.requestUpdate(app)
        NextComplicationService.requestUpdate(app)
        scope.launch { WatchStore.sendToPhone(app, Protocol.MSG_STOP_TRIP, ByteArray(0)) }
    }

    fun start(context: Context, chosen: Journey, target: Place?) {
        synchronized(lock) {
            val app = context.applicationContext
            stop(app, clear = false)
            journey = chosen
            destination = target
            WatchStore.saveTrip(app, snapshot(chosen, System.currentTimeMillis()))
            followingState.value = true
            // Keeps the process (and this loop) alive while the app is off the screen.
            TripService.start(app)
            job = scope.launch {
                var lastLoad = System.currentTimeMillis()
                while (isActive) {
                    val current = journey ?: break
                    // The phone follows a trip: its states win, this loop ends (one source of truth).
                    val shown = WatchStore.trip.value
                    if (shown.active && shown.fromPhone && !shown.stale(System.currentTimeMillis())) {
                        handOver(app)
                        break
                    }
                    val now = System.currentTimeMillis()
                    val token = current.refreshToken
                    // A handle of another provider (a journey the phone found with Transitous, "tr:")
                    // is only the phone's to load: the watch keeps its times.
                    if (token != null && !token.startsWith(FOREIGN_TOKEN_PREFIX) && now - lastLoad >= REFRESH_MILLIS) {
                        lastLoad = now
                        try {
                            client.refreshJourney(token)?.let { journey = it.copy(refreshToken = it.refreshToken ?: token) }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // Keep the last known times.
                        }
                    }
                    val snapshot = snapshot(journey ?: current, System.currentTimeMillis())
                    // Under the lock and only while this loop is still the trip's: a hand-over from
                    // the data listener must not be overwritten by a state of the watch.
                    synchronized(lock) {
                        if (!isActive) return@launch
                        WatchStore.saveTrip(app, snapshot)
                    }
                    if (snapshot.alert && WatchStore.takeAlert(app, snapshot.alertKey)) alertGetOff(app, snapshot.place, snapshot.change)
                    NextTileService.requestUpdate(app)
                    NextComplicationService.requestUpdate(app)
                    if (snapshot.kind == StepKind.Arrived && now > (journey ?: current).arrival + AFTER_ARRIVAL_MILLIS) {
                        stop(app)
                        break
                    }
                    delay(STEP_MILLIS)
                }
            }
        }
    }

    /** Ends the trip of the watch; with [clear] its state disappears from the tile as well. */
    fun stop(context: Context, clear: Boolean = true) {
        synchronized(lock) {
            job?.cancel()
            job = null
            journey = null
            destination = null
            // TripService sees this and stops itself (never stopService from here: a service that
            // stops before its startForeground makes the app crash).
            followingState.value = false
            if (clear && !WatchStore.trip.value.fromPhone) {
                WatchStore.saveTrip(context.applicationContext, TripSnapshot.NONE)
                NextTileService.requestUpdate(context)
                NextComplicationService.requestUpdate(context)
            }
        }
    }

    private const val REFRESH_MILLIS = 30_000L
    private const val FOREIGN_TOKEN_PREFIX = "tr:"
    private const val STEP_MILLIS = 15_000L
    private const val AFTER_ARRIVAL_MILLIS = 5 * 60_000L
}
