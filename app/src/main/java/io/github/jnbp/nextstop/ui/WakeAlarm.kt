package io.github.jnbp.nextstop.ui

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.time.OffsetDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.AppPrefs
import io.github.jnbp.nextstop.data.DiagLog
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.WakeAction
import io.github.jnbp.nextstop.data.WAKE_TEST_PREFIX
import io.github.jnbp.nextstop.data.WakeLead
import io.github.jnbp.nextstop.data.WakeTarget
import io.github.jnbp.nextstop.data.nextWakeLook
import io.github.jnbp.nextstop.data.plainStopName
import io.github.jnbp.nextstop.data.wakeAction
import io.github.jnbp.nextstop.data.wakeTargets
import io.github.jnbp.nextstop.data.wakeTestTarget
import io.github.jnbp.nextstop.ui.theme.storedPalette
import io.github.jnbp.nextstop.wear.PhoneBridge

private const val WAKE_NOTIFICATION = 6
private const val WAKE_TICK_REQUEST = 30
private const val WAKE_CONFIRM_REQUEST = 31
private const val WAKE_PAGE_REQUEST = 32
internal const val ACTION_WAKE_TICK = "io.github.jnbp.nextstop.WAKE_TICK"
internal const val ACTION_WAKE_CONFIRM = "io.github.jnbp.nextstop.WAKE_CONFIRM"

// The service looks at the alarm this often while a trip runs; an alarm wakes the device between.
private const val WAKE_LOOK_MILLIS = 10_000L

/**
 * The wake-up alarm ("Aufwach-Alarm") of a followed trip: before getting off a long ride (from
 * 60 min by itself, see [io.github.jnbp.nextstop.data.WakeAlarmMode]) it rings like an alarm
 * clock, on the phone (an alarm notification with the sound of alarms that opens
 * [WakeAlarmActivity] over the lock screen) and on the watch (over [PhoneBridge.PATH_WAKE]),
 * every minute again until "Ich bin wach" is tapped on either; confirming on one stops both.
 * Per trip it is switched in the companion ([forTrip]). Times: see [wakeTargets], [wakeAction].
 */
object WakeAlarm {
    private const val TAG = "WakeAlarm"

    /** The switch of this trip: null follows the setting, true rings on every long-enough ride, false never. */
    val forTrip = MutableStateFlow<Boolean?>(null)

    /** When it rings on this trip, chosen in the companion (#64); null: the lead of the settings. */
    val leadForTrip = MutableStateFlow<WakeLead?>(null)

    /** What rings right now, for the companion and the page of the alarm; null while nothing does. */
    val ringing = MutableStateFlow<WakeTarget?>(null)

    private val confirmed = mutableSetOf<String>()
    private var lastRing: OffsetDateTime? = null
    private var ringCount = 0
    // Rings and stops go to the watch one after the other, in the order they happened: one
    // thread starts them in order and the fair lock keeps that order while they wait.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private val publishing = Mutex()

    /**
     * The rings of [journey] by the setting and the switch of this trip, timed by the ride state
     * at [now] (see [rideJourney]): on board by position the ring moves with the blended arrival.
     */
    fun targets(context: Context, journey: Journey, now: OffsetDateTime = OffsetDateTime.now()): List<WakeTarget> {
        val prefs = AppPrefs(context)
        return wakeTargets(rideJourney(journey, now), prefs.wakeAlarmMode, prefs.wakeAlarmMinutes, forTrip.value, timesShown = true, lead = leadForTrip.value)
    }

    /**
     * Rings the alarm at once, on the phone and the watch, as a test (developer options): to the
     * destination of the followed trip, or to "Example Square" without one. It rings until
     * "Ich bin wach" is tapped on either; the looks of a running trip leave it alone.
     */
    fun test(context: Context) {
        val app = context.applicationContext
        val journey = TripState.journey.value
        val stop = journey?.legs?.lastOrNull()?.to ?: "Example Square"
        val target = wakeTestTarget(OffsetDateTime.now(), stop)
        synchronized(this) {
            lastRing = OffsetDateTime.now()
            ringCount++
        }
        DiagLog.i(TAG) { "test ring" }
        ring(app, journey, target, first = true)
    }

    /** True when the alarm is on for [journey]: it has at least one ring left. */
    fun on(context: Context, journey: Journey): Boolean = targets(context, journey).isNotEmpty()

    /** Follows the trip for as long as [TripService] runs: looks every few seconds. */
    suspend fun follow(context: Context) {
        TripState.journey.collectLatest { journey ->
            if (journey == null) {
                reset(context)
                return@collectLatest
            }
            while (true) {
                check(context)
                delay(WAKE_LOOK_MILLIS)
            }
        }
    }

    /** Rings what is due, stops what is over, and sets the alarm for the next look. */
    fun check(context: Context) {
        val app = context.applicationContext
        val journey = TripState.journey.value
        if (journey == null) {
            if (ringing.value?.key?.startsWith(WAKE_TEST_PREFIX) != true) stopRinging(app)
            arm(app, null)
            return
        }
        val now = OffsetDateTime.now()
        // A test ring (see test) goes on until it is confirmed.
        if (ringing.value?.key?.startsWith(WAKE_TEST_PREFIX) == true) return
        val targets = targets(app, journey, now)
        val (action, next) = synchronized(this) {
            val ringingKey = ringing.value?.key
            val action = wakeAction(targets, confirmed, ringingKey, lastRing, now)
            if (action is WakeAction.Ring) {
                lastRing = now
                ringCount++
            }
            val ringingAfter = when (action) {
                is WakeAction.Ring -> action.target.key
                WakeAction.Stop -> null
                WakeAction.None -> ringingKey
            }
            action to nextWakeLook(targets, confirmed, ringingAfter, if (ringingAfter != null) lastRing else null, now)
        }
        when (action) {
            is WakeAction.Ring -> ring(app, journey, action.target, action.first)
            WakeAction.Stop -> stopRinging(app)
            WakeAction.None -> Unit
        }
        arm(app, next?.toInstant()?.toEpochMilli())
    }

    // The time the alarm is set for; it is set again only when that changes, not at every look.
    private var armedAt: Long? = null

    /** The alarm went off: it has to be set again even for the same time. */
    internal fun alarmFired() {
        synchronized(this) { armedAt = null }
    }

    private fun arm(context: Context, at: Long?) {
        synchronized(this) {
            if (at == armedAt) return
            armedAt = at
        }
        if (at == null) {
            cancelWakeUpAlarm(context, tickIntent(context))
        } else {
            // An alarm clock of the system where exact alarms are allowed: Doze does not hold it back.
            setAlarmClockOrWakeUp(context, at, tickIntent(context), pageIntent(context))
        }
    }

    private fun pageIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        WAKE_PAGE_REQUEST,
        Intent(context, WakeAlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** "Ich bin wach", on the phone or the watch: stops the ringing on both. */
    fun confirm(context: Context) {
        val app = context.applicationContext
        val key = ringing.value?.key
        synchronized(this) { if (key != null) confirmed.add(key) }
        DiagLog.i(TAG) { "confirmed" }
        stopRinging(app)
        check(app)
    }

    /** Switches the alarm of this trip; ringing stops when it goes off. */
    fun setForTrip(context: Context, on: Boolean) {
        forTrip.value = on
        if (!on) stopRinging(context.applicationContext)
        check(context)
    }

    /**
     * Sets when it rings on this trip ([lead], null for the setting) and switches it on: a ring
     * already confirmed for the old time counts no longer, so the new time rings.
     */
    fun setLeadForTrip(context: Context, lead: WakeLead?) {
        leadForTrip.value = lead
        forTrip.value = true
        synchronized(this) { confirmed.clear() }
        check(context)
    }

    /** The trip ended: everything of this trip is forgotten. */
    fun reset(context: Context) {
        val app = context.applicationContext
        forTrip.value = null
        leadForTrip.value = null
        synchronized(this) {
            confirmed.clear()
            lastRing = null
        }
        stopRinging(app)
        arm(app, null)
    }

    private fun tickIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        WAKE_TICK_REQUEST,
        Intent(context, WakeAlarmReceiver::class.java).setAction(ACTION_WAKE_TICK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** The action "Ich bin wach" of the notification (also when it is swiped away). */
    internal fun confirmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        WAKE_CONFIRM_REQUEST,
        Intent(context, WakeAlarmReceiver::class.java).setAction(ACTION_WAKE_CONFIRM),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    @SuppressLint("MissingPermission")
    private fun ring(context: Context, journey: Journey?, target: WakeTarget, first: Boolean) {
        ringing.value = target
        DiagLog.i(TAG) { if (first) "rings" else "rings again" }
        val manager = context.getSystemService(NotificationManager::class.java)
        ensureChannel(context, AppChannel.of(NotifyFeature.WakeAlarm))
        val stop = target.stop.plainStopName()
        val title = context.getString(R.string.wake_title)
        val text = context.getString(R.string.wake_text, stop, target.arrival.clock())
        val page = pageIntent(context)
        val confirm = confirmIntent(context)
        val notification = NotificationCompat.Builder(context, AppChannel.of(NotifyFeature.WakeAlarm).id)
            .setSmallIcon(R.drawable.ic_stat_trip)
            .setColor(storedPalette(context).blue.toArgb())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(page)
            // Over the lock screen like an alarm clock; where Android does not allow it, a heads-up.
            .setFullScreenIntent(page, true)
            .setDeleteIntent(confirm)
            // The watch rings by itself (see PhoneBridge.PATH_WAKE): not bridged to it as well.
            .setLocalOnly(true)
            .addAction(R.drawable.ic_stat_trip, context.getString(R.string.wake_confirm), confirm)
            .build()
        try {
            // Posted anew every minute, so it alerts again (one id: never two at once).
            manager?.cancel(WAKE_NOTIFICATION)
            if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                NotificationManagerCompat.from(context).notify(WAKE_NOTIFICATION, notification)
            }
        } catch (e: SecurityException) {
            // The permission was taken away: the watch still rings.
        }
        val count = ringCount
        scope.launch { publishing.withLock { PhoneBridge.publishWake(context, target, journey, count) } }
    }

    private fun stopRinging(context: Context) {
        val was = ringing.value
        ringing.value = null
        context.getSystemService(NotificationManager::class.java)?.cancel(WAKE_NOTIFICATION)
        if (was != null) scope.launch { publishing.withLock { PhoneBridge.publishWake(context, null, null, 0) } }
    }
}

/** The alarm of the next ring and the button "Ich bin wach" of the notification. */
class WakeAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_WAKE_CONFIRM -> WakeAlarm.confirm(context)
            else -> {
                // The alarm that was set is used up: the next one is set whatever its time.
                WakeAlarm.alarmFired()
                WakeAlarm.check(context)
            }
        }
    }
}
