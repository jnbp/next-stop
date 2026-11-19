package io.github.jnbp.nextstop.ui

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.time.OffsetDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import io.github.jnbp.nextstop.MainActivity
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.AlightEvent
import io.github.jnbp.nextstop.data.AppPrefs
import io.github.jnbp.nextstop.data.CompanionPosition
import io.github.jnbp.nextstop.data.alightEventsByPosition
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.TripNotice
import io.github.jnbp.nextstop.data.alightEvents
import io.github.jnbp.nextstop.data.noticeJourneyHash
import io.github.jnbp.nextstop.data.dueAlight
import io.github.jnbp.nextstop.data.nextAlight
import io.github.jnbp.nextstop.data.plainStopName
import io.github.jnbp.nextstop.ui.theme.storedPalette

private val ALIGHT_CHANNEL = AppChannel.of(NotifyFeature.Alight).id
private const val ALIGHT_NOTIFICATION = 3
private const val ALIGHT_ALARM_REQUEST = 20
private const val ACTION_ALIGHT = "io.github.jnbp.nextstop.ALIGHT"

// The service looks at the trip this often; the alarm wakes the device for the next reminder in between.
private const val ALIGHT_TICK_MILLIS = 10_000L

// A reminder goes away by itself this long after it came.
private const val ALIGHT_TIMEOUT_MILLIS = 5 * 60_000L

/**
 * Reminders to get off during a followed trip, with sound and vibration on a channel of their
 * own: when the vehicle leaves the stop before the one to get off at ("Next stop: … – get
 * off, then U7 towards …"), and again about a minute before the arrival, at every change and
 * at the end. The times are the live times of the stops of the ride, as the trip service
 * loads them every 30 seconds. The service looks every few seconds, and an alarm wakes the
 * device for the next reminder when it sleeps. Every reminder is sent once; a new plan or the
 * end of the trip takes the alarm back. Setting: [AppPrefs.alightReminders].
 */
object AlightReminders {
    // The reminders sent on this trip, by their key.
    private val sent = mutableSetOf<String>()

    /** Follows the trip in [TripState] for as long as the trip service runs. */
    suspend fun follow(context: Context) {
        TripState.journey.collectLatest { journey ->
            if (journey == null) {
                cancel(context)
                synchronized(sent) { sent.clear() }
                return@collectLatest
            }
            while (true) {
                check(context)
                delay(ALIGHT_TICK_MILLIS)
            }
        }
    }

    /** Sends the reminder that is due now, if any, and sets the alarm for the next one. */
    fun check(context: Context) {
        val journey = TripState.journey.value
        if (journey == null || !AppPrefs(context).alightReminders) {
            cancel(context)
            return
        }
        val now = OffsetDateTime.now()
        // Timed by the position as well, while the companion follows it (see alightEventsByPosition).
        val fix = CompanionPosition.fixFor(journey, now.toInstant().toEpochMilli())
        // The times by the ride state (see rideJourney): on board by position the reminders move with the blended arrival.
        val events = alightEventsByPosition(alightEvents(rideJourney(journey, now), timesShown = true), journey, fix, now)
        val due = synchronized(sent) {
            dueAlight(events, sent, now)?.also { chosen ->
                // The ones before it are over with it: never sent late after a later one.
                events.filter { !it.at.isAfter(chosen.at) }.forEach { sent.add(it.key) }
            }
        }
        if (due != null) notify(context, journey, due)
        // Takes back what no longer applies, on the look the service makes anyway (see TripNoticeSweep).
        TripNoticeSweep.sweep(context)
        val next = synchronized(sent) { nextAlight(events, sent, now) }
        if (next == null) cancelAlarm(context) else setAlarm(context, next.at.toInstant().toEpochMilli())
    }

    /** Takes the alarm back; a reminder already shown goes away by itself. */
    fun cancel(context: Context) = cancelAlarm(context)

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        ALIGHT_ALARM_REQUEST,
        Intent(context, AlightReceiver::class.java).setAction(ACTION_ALIGHT),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun setAlarm(context: Context, at: Long) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        try {
            // Needs no permission for exact alarms: Android may move it a little, the service
            // looks every few seconds besides while the device is awake.
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarmIntent(context))
        } catch (e: SecurityException) {
            // The service still looks every few seconds while it runs.
        }
    }

    private fun cancelAlarm(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(alarmIntent(context))
    }

    @SuppressLint("MissingPermission")
    private fun notify(context: Context, journey: Journey, event: AlightEvent) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(context, AppChannel.of(NotifyFeature.Alight))
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        // The plausible times, as the reminders were timed by them (see alightEvents).
        // By the ride state, so an arrival estimated from the position is the one it names.
        val shown = rideJourney(journey, OffsetDateTime.now())
        val leg = shown.legs.getOrNull(event.legIndex) ?: return
        // As the companion names it (#157).
        val exit = io.github.jnbp.nextstop.data.journeyStopName(shown, leg.to)
        val title = context.getString(if (event.early) R.string.alight_next_stop else R.string.alight_approach, exit)
        val next = rideAfter(shown, leg)
        val body = if (next == null) {
            context.getString(R.string.alight_final)
        } else {
            val line = shortLine(next.line) ?: next.line ?: ""
            val direction = next.direction?.plainStopName()
            val then = if (direction != null) {
                context.getString(R.string.retarget_then_line_towards, line, direction)
            } else {
                context.getString(R.string.retarget_then_line, line)
            }
            // At the change: the same platform, or where to board the next ride for the change after it.
            val hint = changeText(context, leg, next, walkBetween(shown, leg))
                ?: boardingHint(context, TripStep(StepKind.Board, next.departure, next, next.from), shown)
            listOfNotNull(then.replaceFirstChar { it.uppercase() }, hint).joinToString(" · ")
        }
        // Timed by the app's estimate: it says so, with the estimated arrival.
        val estimate = if (event.estimated || leg.arrivalEstimated) {
            context.getString(R.string.alight_estimated_arrival, context.getString(R.string.estimated_prefix), leg.arrival.clock())
        } else {
            null
        }
        val text = listOfNotNull(estimate, body).joinToString(" · ")
        // While the live notification of the trip is up, the reminder is that notification: it
        // alerts once with sound and vibration and says it in its title; no second one beside it.
        if (TripState.serviceRunning.value && tripNotificationsAllowed(context)) {
            postTripNotification(context, tripNotification(context, journey, OffsetDateTime.now(), alert = title))
            return
        }
        val open = PendingIntent.getActivity(
            context,
            21,
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_OPEN_TRIP)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, ALIGHT_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_trip)
            .setColor(storedPalette(context).blue.toArgb())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setTimeoutAfter(ALIGHT_TIMEOUT_MILLIS)
            .setContentIntent(open)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(TripNotice.Alight(noticeJourneyHash(journey), event.legIndex).tag, ALIGHT_NOTIFICATION, notification)
        } catch (e: SecurityException) {
            // The permission was taken away in the meantime.
        }
    }
}

/** The alarm of the next reminder to get off: looks at the trip and sends what is due. */
class AlightReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlightReminders.check(context)
    }
}
