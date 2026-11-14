package io.github.jnbp.nextstop.wear.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import io.github.jnbp.nextstop.wear.R
import io.github.jnbp.nextstop.wear.tile.tripLines
import io.github.jnbp.nextstop.wear.ui.OpenRequest

/**
 * The followed trip on the watch face and in the recents: an Ongoing Activity with the
 * minutes to the next departure or getting off counting down, and the next step. A tap
 * opens the trip screen. Shown for a trip of the phone as for one the watch follows itself;
 * [update] is called with every new state of the trip ([WatchStore.saveTrip]).
 */
object TripOngoing {
    const val NOTIFICATION_ID = 11
    private const val CHANNEL = "trip"

    /** Shows [trip] as the ongoing activity, or removes it when there is no trip (or it arrived). */
    fun update(context: Context, trip: TripSnapshot) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (!trip.active || trip.kind == StepKind.Arrived) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        try {
            manager.notify(NOTIFICATION_ID, build(context, trip))
        } catch (e: SecurityException) {
            // No permission to post notifications: the trip shows in the app and the tile only.
            Log.d(LOG_TAG, "ongoing: cannot post: $e")
        } catch (e: RuntimeException) {
            Log.d(LOG_TAG, "ongoing: failed: $e")
        }
    }

    /** The notification of [trip] with its ongoing activity; also the one of [TripService]. */
    fun build(context: Context, trip: TripSnapshot): Notification {
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            OpenRequest.intent(context, OpenRequest.Trip),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // Head ("U7 · 5'"), step ("Get off at ..."), and the stops left or the hint.
        val lines = tripLines(context, trip)
        val step = lines.getOrNull(1) ?: lines.first()
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(step)
            .setContentText(listOfNotNull(trip.line, lines.getOrNull(2)).joinToString(" · ").ifEmpty { trip.destination })
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
        // The countdown runs on the watch face by itself, also between two states of the trip.
        // The parts of Status count in the time base of SystemClock.elapsedRealtime().
        val zero = SystemClock.elapsedRealtime() + (trip.time - System.currentTimeMillis()).coerceAtLeast(0)
        val label = when (trip.kind) {
            StepKind.Board, StepKind.GetOff -> trip.line ?: trip.place
            else -> trip.place
        }
        val status = Status.Builder()
            .addTemplate("#label# · #time#")
            .addTemplate("#time#")
            .addPart("label", Status.TextPart(label))
            .addPart("time", Status.TimerPart(zero))
            .build()
        OngoingActivity.Builder(context, NOTIFICATION_ID, builder)
            .setStaticIcon(R.drawable.ic_launcher_monochrome)
            .setTouchIntent(open)
            .setStatus(status)
            .build()
            .apply(context)
        return builder.build()
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL) != null) return
        val channel = NotificationChannel(CHANNEL, context.getString(R.string.trip_channel), NotificationManager.IMPORTANCE_LOW).apply {
            enableVibration(false)
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }
}
