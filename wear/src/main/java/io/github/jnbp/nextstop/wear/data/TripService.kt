package io.github.jnbp.nextstop.wear.data

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Keeps the process alive while the watch follows a trip itself ([LocalFollow]), as a
 * foreground service whose notification is the trip's ongoing activity ([TripOngoing]).
 * A trip of the phone needs no service: the phone wakes the watch with every new state.
 *
 * The service is never stopped from outside: it follows [LocalFollow.following] and stops
 * itself, always after its startForeground (a service started with startForegroundService
 * that stops before it makes the app crash). All of it runs on the main thread.
 */
class TripService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Main thread only.
    private var watching: Job? = null
    private var lastStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        WatchStore.load(this)
        // startForeground comes first in any case, before any decision to stop.
        val notification = TripOngoing.build(this, WatchStore.trip.value)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(TripOngoing.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(TripOngoing.NOTIFICATION_ID, notification)
            }
        } catch (e: RuntimeException) {
            // Not allowed now (from the background on Android 12+): the trip goes on in the app process.
            Log.d(LOG_TAG, "trip service: cannot start in the foreground: $e")
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (watching == null) {
            watching = scope.launch {
                // Also when the trip ended (or was handed over) before the service came up.
                LocalFollow.following.first { !it }
                leave()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /**
     * Stops the service. Its notification is the trip's ongoing activity: when the trip goes
     * on (handed over to the phone) it is detached and stays, to be updated by [TripOngoing];
     * otherwise it goes with the service. A notification of a foreground service cannot be
     * cancelled, so the current state is applied once more after detaching.
     */
    private fun leave() {
        val trip = WatchStore.trip.value
        val goesOn = trip.active && trip.kind != StepKind.Arrived
        stopForeground(if (goesOn) STOP_FOREGROUND_DETACH else STOP_FOREGROUND_REMOVE)
        if (goesOn) TripOngoing.update(this, WatchStore.trip.value)
        watching = null
        // Only up to the last start: a start that came meanwhile (a new trip) keeps the service,
        // and its onStartCommand calls startForeground again.
        stopSelf(lastStartId)
    }

    companion object {
        /** Starts the service for the trip [LocalFollow] follows; it ends by itself with [LocalFollow.following]. */
        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, TripService::class.java))
            } catch (e: RuntimeException) {
                Log.d(LOG_TAG, "trip service: cannot start: $e")
            }
        }
    }
}
