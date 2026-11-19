package io.github.jnbp.nextstop.ui

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import java.time.OffsetDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.jnbp.nextstop.MainActivity
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.AppPrefs
import io.github.jnbp.nextstop.data.DiagLog
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.PlannedPhase
import io.github.jnbp.nextstop.data.PlannedTrip
import io.github.jnbp.nextstop.data.PlannedTrips
import io.github.jnbp.nextstop.data.PLANNED_NOTICE_PREFIX
import io.github.jnbp.nextstop.data.keepPlannedNotice
import io.github.jnbp.nextstop.data.plannedNext
import io.github.jnbp.nextstop.data.plannedNoticeId
import io.github.jnbp.nextstop.data.plannedWakeUp
import io.github.jnbp.nextstop.ui.theme.storedPalette

private const val PLANNED_TICK_REQUEST = 40
private const val PLANNED_NOTIFICATION_BASE = 4000
internal const val ACTION_PLANNED_TICK = "io.github.jnbp.nextstop.PLANNED_TICK"
internal const val ACTION_PLANNED_START = "io.github.jnbp.nextstop.PLANNED_START"
internal const val ACTION_PLANNED_CANCEL = "io.github.jnbp.nextstop.PLANNED_CANCEL"
internal const val EXTRA_PLANNED_ID = "plannedId"
internal const val ACTION_OPEN_PLANNED = "io.github.jnbp.nextstop.OPEN_PLANNED"

// What is due within this many seconds counts as due now.
private const val PLANNED_GRACE_SECONDS = 30L

/**
 * The alarms of the trips planned ahead ("Fahrt planen", see [PlannedTrips]): "Bald losgehen"
 * the set minutes before leaving (with "Jetzt starten" and "Absagen"), and the start of the
 * trip companion at the time to leave. One alarm wakes the app for whatever comes first; it is
 * set again after every change, after a restart of the device and after an update of the app
 * ([PlannedTripReceiver]). Where Android does not let the app start the companion by itself
 * (no exact alarms allowed), the start is offered by a notification instead.
 */
object PlannedTripAlarms {
    private const val TAG = "PlannedTrips"

    /** Plans [journey] and sets the alarms; returns the planned trip. */
    fun plan(context: Context, journey: Journey, fromName: String, toName: String): PlannedTrip {
        val trip = PlannedTrips.add(context, journey, fromName, toName, AppPrefs(context).plannedLeadMinutes)
        DiagLog.i(TAG) { "planned for ${trip.leaveAt}" }
        schedule(context)
        return trip
    }

    /** Cancels the planned trip [id] with its notification. */
    fun cancel(context: Context, id: String) {
        PlannedTrips.remove(context, id)
        notifications(context)?.cancel(plannedTag(id), notificationId(id))
        schedule(context)
    }

    /** Plans [trip] again as it was, after a cancel ("Undo"), and sets its alarms anew. */
    fun restore(context: Context, trip: PlannedTrip) {
        PlannedTrips.restore(context, trip)
        DiagLog.i(TAG) { "restored" }
        schedule(context)
    }

    /** Sets the one alarm for what comes next of all planned trips, or takes it back. */
    fun schedule(context: Context) {
        val app = context.applicationContext
        PlannedTrips.prune(app)
        sweepNotices(app)
        val next = plannedWakeUp(PlannedTrips.load(app), OffsetDateTime.now())
        if (next == null) cancelWakeUpAlarm(app, tickIntent(app)) else setWakeUpAlarm(app, next.toInstant().toEpochMilli(), tickIntent(app))
    }

    /** Does what is due: reminds, starts; then sets the alarm for what comes next. */
    fun tick(context: Context) {
        val app = context.applicationContext
        val now = OffsetDateTime.now()
        for (trip in PlannedTrips.load(app)) {
            val (phase, at) = plannedNext(trip, now) ?: continue
            if (at.isAfter(now.plusSeconds(PLANNED_GRACE_SECONDS))) continue
            when (phase) {
                PlannedPhase.Remind -> {
                    PlannedTrips.markReminded(app, trip.id)
                    remind(app, trip)
                }
                PlannedPhase.Start -> start(app, trip)
            }
        }
        schedule(app)
    }

    /**
     * Starts the companion for [trip] (from its alarm, or from "Jetzt starten"). A trip that runs
     * already stays; the planned one is then dropped. Where Android refuses the start in the
     * background, the start is offered by a notification.
     */
    fun start(context: Context, trip: PlannedTrip) {
        val app = context.applicationContext
        if (TripState.journey.value != null) {
            DiagLog.i(TAG) { "start skipped: a trip runs" }
            PlannedTrips.remove(app, trip.id)
            notifications(app)?.cancel(plannedTag(trip.id), notificationId(trip.id))
            return
        }
        TripState.shortcutAlert.value = AppPrefs(app).shortcutAlert
        TripState.journey.value = trip.journey
        if (startTripService(app)) {
            DiagLog.i(TAG) { "companion started" }
            PlannedTrips.remove(app, trip.id)
            notifications(app)?.cancel(plannedTag(trip.id), notificationId(trip.id))
            return
        }
        DiagLog.w(TAG) { "start refused in the background: offered" }
        TripState.journey.compareAndSet(trip.journey, null)
        PlannedTrips.markOffered(app, trip.id)
        remind(app, trip, startNow = true)
    }

    private fun notifications(context: Context): NotificationManager? = context.getSystemService(NotificationManager::class.java)

    private fun notificationId(id: String): Int = PLANNED_NOTIFICATION_BASE + (id.hashCode() and 0x3ff)

    // The tag of the reminder of the planned trip [id], so a stale one is found again (see sweepNotices).
    private fun plannedTag(id: String): String = PLANNED_NOTICE_PREFIX + id

    /**
     * Takes back reminders of planned trips that no longer apply (#172): started, cancelled or
     * past, or started another way (the journey followed now). Reads the planned trips: not on
     * the main thread.
     */
    fun sweepNotices(context: Context) {
        val manager = notifications(context) ?: return
        val shown = try {
            manager.activeNotifications.filter { plannedNoticeId(it.tag) != null }
        } catch (e: Exception) {
            return
        }
        if (shown.isEmpty()) return
        val planned = PlannedTrips.load(context.applicationContext)
        val followed = TripState.journey.value?.id
        for (status in shown) {
            val id = plannedNoticeId(status.tag) ?: continue
            if (!keepPlannedNotice(planned.firstOrNull { it.id == id }?.let { it.journey.id }, followed)) manager.cancel(status.tag, status.id)
        }
    }

    private fun tickIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        PLANNED_TICK_REQUEST,
        Intent(context, PlannedTripReceiver::class.java).setAction(ACTION_PLANNED_TICK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun actionIntent(context: Context, action: String, trip: PlannedTrip): PendingIntent = PendingIntent.getBroadcast(
        context,
        notificationId(trip.id) + if (action == ACTION_PLANNED_START) 0 else 0x400,
        Intent(context, PlannedTripReceiver::class.java).setAction(action).putExtra(EXTRA_PLANNED_ID, trip.id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** "Bald losgehen: um 08:05 los nach …", or at the time to leave ([startNow]) "Jetzt losgehen". */
    @SuppressLint("MissingPermission")
    private fun remind(context: Context, trip: PlannedTrip, startNow: Boolean = false) {
        ensureChannel(context, AppChannel.of(NotifyFeature.Planned))
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val title = context.getString(if (startNow) R.string.planned_go_now else R.string.planned_soon, trip.leaveAt.clock())
        val text = context.getString(R.string.planned_to, trip.toName)
        val open = PendingIntent.getActivity(
            context,
            notificationId(trip.id),
            // A tap opens that trip (see PlannedTripTap), not just the app.
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_OPEN_PLANNED)
                .putExtra(EXTRA_PLANNED_ID, trip.id)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, AppChannel.of(NotifyFeature.Planned).id)
            .setSmallIcon(R.drawable.ic_stat_trip)
            .setColor(storedPalette(context).blue.toArgb())
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(R.drawable.ic_stat_trip, context.getString(R.string.planned_start_now), actionIntent(context, ACTION_PLANNED_START, trip))
            .addAction(R.drawable.ic_stat_trip, context.getString(R.string.planned_cancel), actionIntent(context, ACTION_PLANNED_CANCEL, trip))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(plannedTag(trip.id), notificationId(trip.id), notification)
        } catch (e: SecurityException) {
            // The permission was taken away in the meantime.
        }
    }
}

/**
 * A tap on the reminder of a planned trip: the trip's own page, as a tap on its card opens it
 * (see PlannedTripActions). Once it started (it is no longer planned), the companion opens.
 */
object PlannedTripTap {
    fun follow(activity: ComponentActivity, intent: Intent?) {
        if (intent?.action != ACTION_OPEN_PLANNED) return
        val id = intent.getStringExtra(EXTRA_PLANNED_ID) ?: return
        val app = activity.applicationContext
        // The planned trips are read from the device: off the main thread.
        activity.lifecycleScope.launch {
            val trip = withContext(Dispatchers.IO) { PlannedTrips.find(app, id) }
            if (trip != null) {
                PlannedTripActions.open(trip)
            } else if (TripState.journey.value != null) {
                TripState.openRequested.value = true
            }
        }
    }
}

/**
 * The alarm of the planned trips, their buttons "Jetzt starten" and "Absagen", and the restart of
 * the device or an update of the app, after which the alarm is set again.
 */
class PlannedTripReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_PLANNED_ID)
        when (intent.action) {
            ACTION_PLANNED_START -> {
                // A tap on the button of a notification may start the service from the background.
                val trip = id?.let { PlannedTrips.find(context, it) } ?: return
                PlannedTripAlarms.start(context, trip)
                PlannedTripAlarms.schedule(context)
            }
            ACTION_PLANNED_CANCEL -> if (id != null) PlannedTripAlarms.cancel(context, id)
            ACTION_PLANNED_TICK -> PlannedTripAlarms.tick(context)
            // Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED: alarms are gone after these.
            else -> {
                PlannedTripAlarms.schedule(context)
                // The exact-alarm right may have changed: an armed wake-up alarm is set anew.
                WakeAlarm.alarmFired()
            }
        }
    }
}
