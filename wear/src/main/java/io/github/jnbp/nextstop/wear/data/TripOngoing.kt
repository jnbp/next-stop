package io.github.jnbp.nextstop.wear.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import io.github.jnbp.nextstop.wear.R
import io.github.jnbp.nextstop.wear.complication.statusLead
import io.github.jnbp.nextstop.wear.complication.statusText
import io.github.jnbp.nextstop.wear.complication.tripGlyph
import io.github.jnbp.nextstop.wear.complication.tripGlyphIcon
import io.github.jnbp.nextstop.wear.complication.tripIcons
import io.github.jnbp.nextstop.wear.complication.tripState
import io.github.jnbp.nextstop.wear.tile.tripLines
import io.github.jnbp.nextstop.wear.ui.OpenRequest
import io.github.jnbp.nextstop.wear.ui.clock

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
        // On the watch face: the app's icon (#282; also with the option, where the complications
        // show the state, #253) and a short status ("U3 · 6'", "Aussteigen 2'"),
        // renewed with every new state of the trip (at least once a minute), no wakeups of its own.
        val now = System.currentTimeMillis()
        val state = tripState(trip)
        val count = if (watchShowsClock(trip.time - now)) clock(trip.time) else "${minutesUntil(trip.time, now)}'"
        val text = statusText(
            lead = statusLead(state, trip.kind, trip.leave, trip.line),
            line = trip.line,
            count = count,
            getOff = context.getString(R.string.r6ideas_ongoing_get_off),
            change = context.getString(R.string.r6ideas_ongoing_change),
            leave = context.getString(R.string.leave_short),
        )
        val status = Status.Builder()
            .addTemplate("#status#")
            .addPart("status", Status.TextPart(text))
            .build()
        // A trip of the phone that goes silent leaves the watch face when it turns stale, as on the tile.
        val staleAt = trip.staleAt()
        if (staleAt > now) builder.setTimeoutAfter(staleAt - now)
        OngoingActivity.Builder(context, NOTIFICATION_ID, builder)
            .setStaticIcon(tripIcons(WatchStore.stateIcons.value, tripGlyphIcon(tripGlyph(state, trip.product)), R.drawable.ic_launcher_monochrome).ongoing)
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
