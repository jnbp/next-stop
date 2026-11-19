package io.github.jnbp.nextstop.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.jnbp.nextstop.data.DiagLog
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.resumableTrip
import io.github.jnbp.nextstop.data.tripMarkJson
import io.github.jnbp.nextstop.data.tripMarkOf
import java.io.File
import java.time.OffsetDateTime

/**
 * #117: the mark of the trip [TripService] follows, so the companion survives an update of the
 * app (see data/TripResume.kt). Written by the service when it follows another trip, removed
 * when it ends as it should; an update ends the process without that, and the mark stays.
 */
internal object TripMark {
    private const val FILE_NAME = "followed_trip.json"
    private const val TAG = "TripMark"

    private fun file(context: Context) = File(context.applicationContext.filesDir, FILE_NAME)

    /** Keeps [journey] as the followed trip. Call off the main thread. */
    fun write(context: Context, journey: Journey) {
        val file = file(context)
        val temp = File(file.parentFile, "$FILE_NAME.tmp")
        try {
            temp.writeText(tripMarkJson(journey, System.currentTimeMillis()))
            if (!temp.renameTo(file)) {
                file.delete()
                temp.renameTo(file)
            }
        } catch (e: Throwable) {
            // Only a copy for the case of an update: the trip goes on without it.
            temp.delete()
        }
    }

    /** Forgets the mark: the trip ended as it should. */
    fun clear(context: Context) {
        try {
            file(context).delete()
        } catch (e: Exception) {
            DiagLog.w(TAG, e) { "mark not removed" }
        }
    }

    /** The trip of the mark, where it is still worth following now; null otherwise. */
    fun resumable(context: Context): Journey? = try {
        val file = file(context)
        val mark = if (file.exists()) tripMarkOf(file.readText()) else null
        resumableTrip(mark?.second, mark?.first ?: 0L, System.currentTimeMillis(), OffsetDateTime.now())
    } catch (e: Exception) {
        null
    }
}

/**
 * After an update of the app (MY_PACKAGE_REPLACED): a trip the companion followed when the
 * update ended the process is followed again, its service started anew (Android allows a
 * foreground service to start from this broadcast). A mark of an ended trip is removed.
 */
class TripResumeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (TripState.journey.value != null) return
        val trip = TripMark.resumable(context)
        if (trip == null) {
            TripMark.clear(context)
            return
        }
        TripState.journey.value = trip
        val started = startTripService(context)
        DiagLog.i("TripResume") { "followed trip resumed after update, service started: $started" }
    }
}
