package io.github.jnbp.nextstop.wear.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import io.github.jnbp.nextstop.wear.R
import io.github.jnbp.nextstop.wear.ui.AppForeground
import io.github.jnbp.nextstop.wear.ui.OpenRequest
import kotlinx.coroutines.flow.MutableStateFlow

/*
 * The option "Open companion automatically" (WatchSettingKeys.OPEN_COMPANION, off by default,
 * switched on the watch or on the phone): when the phone starts a trip while the watch app is
 * closed, the watch shows the trip companion by itself, as Google Maps does with navigation:
 * start on the phone, raise the wrist, the companion is there. It rides on the trip state the
 * phone writes anyway (Protocol.PATH_TRIP, seen by WatchDataService); nothing new goes over the
 * wire.
 *
 * Only a trip that has just begun opens it (startedOnPhone): not a trip the watch started
 * itself and the phone took over, not the same trip written again (a sync, a reconnect), and not
 * one that has been running for a while when the watch comes back in reach.
 *
 * How, within the rules of Android and Wear OS:
 * - The trip itself is an Ongoing Activity (TripOngoing, posted for every state of a trip of the
 *   phone): on the watch face and in the recents, one tap away, and the app the watch returns to.
 * - A service of the Data Layer may not start an activity from the background (background
 *   activity launch limits), and from Android 12 on it may not start a foreground service
 *   either. The start is tried once; what works is a notification of the category navigation
 *   with a full-screen intent (USE_FULL_SCREEN_INTENT, see docs/play-console-0.2.md, "Wear
 *   OS"): with the screen off or dimmed the system opens the companion right away, so it is on
 *   screen when the wrist comes up; with the screen in use it shows as a heads-up that opens
 *   it with one tap. Where the permission is not granted (Android 14+ lets the user take it
 *   back) the heads-up alone remains. The app cancels the nudge as soon as it is in front.
 * - With the option off nothing of this happens: the trip shows as its ongoing activity only.
 * - The activity opened this way shows over a locked watch face and turns the screen on
 *   (EXTRA_WAKE, see MainActivity), like the wake-up alarm.
 * - What came of the last try is kept ([last]) and shown under the option in the settings, so
 *   a companion that does not open tells why (notifications off, full screen not allowed, ...).
 */
object CompanionAutoOpen {
    private const val CHANNEL = "trip-start"
    private const val NOTIFICATION_ID = 12

    // The nudge goes away by itself after this long: by then the trip page is no news.
    private const val NOTIFICATION_TIMEOUT_MILLIS = 2 * 60_000L

    private const val PREFS = "companion-open"
    private const val KEY_OUTCOME = "outcome"
    private const val KEY_AT = "at"

    /**
     * What happened at the last try to open the companion (a trip just started on the phone,
     * or the test from the phone's developer options), for the status line of the watch's
     * settings: only the reason and the time, nothing of the trip.
     */
    enum class Outcome {
        /** Notification posted with its full-screen intent: the system opens the companion. */
        Opened,

        /** The app was in front already: it turned to the trip page by itself. */
        AppInFront,

        /** A trip started, but the option is off on the watch. */
        SettingOff,

        /** Notifications of the app are off on the watch (or POST_NOTIFICATIONS not granted). */
        NotificationsOff,

        /** The channel "trip started" was muted or lowered by the user: no heads-up, no full screen. */
        ChannelMuted,

        /** Android 14+: the full-screen permission was taken back; a heads-up only. */
        NoFullScreen,
    }

    data class LastOutcome(val outcome: Outcome, val at: Long)

    /** The last [Outcome], null before the first try; see [load]. */
    val last = MutableStateFlow<LastOutcome?>(null)

    /** Reads the last outcome stored (cheap; with every [WatchStore.load]). */
    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_OUTCOME, null) ?: return
        val outcome = Outcome.entries.firstOrNull { it.name == name } ?: return
        last.value = LastOutcome(outcome, prefs.getLong(KEY_AT, 0L))
    }

    private fun note(context: Context, outcome: Outcome) {
        val now = System.currentTimeMillis()
        last.value = LastOutcome(outcome, now)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_OUTCOME, outcome.name)
            .putLong(KEY_AT, now)
            .apply()
        Log.d(LOG_TAG, "companion auto-open: $outcome")
    }

    /**
     * Opens the companion for [after], a trip of the phone the watch just took over from
     * [before], when the option is on, the trip has just started and the app is not in front
     * (an open app turns to the trip page by itself, see WearApp).
     */
    fun onPhoneTrip(context: Context, before: TripSnapshot, after: TripSnapshot) {
        if (!startedOnPhone(before, after, System.currentTimeMillis())) return
        if (!WatchStore.openCompanion.value) {
            note(context, Outcome.SettingOff)
            return
        }
        if (AppForeground.resumed.value) {
            note(context, Outcome.AppInFront)
            return
        }
        open(context)
    }

    /**
     * The test of the phone's developer options ("Test: Begleiter auf der Uhr öffnen"): the
     * same way as a trip started on the phone, whatever the option and without a trip (the
     * companion then shows that no trip runs).
     */
    fun test(context: Context) {
        if (AppForeground.resumed.value) {
            OpenRequest.pending.value = OpenRequest.Trip
            note(context, Outcome.AppInFront)
            return
        }
        open(context)
    }

    private fun open(context: Context) {
        // The nudge first: it is what the system lets through when the start below is kept back.
        note(context, notify(context))
        try {
            context.startActivity(wakeIntent(context))
        } catch (e: RuntimeException) {
            Log.d(LOG_TAG, "companion auto-open: start refused: ${e.javaClass.simpleName}")
        }
    }

    // Opens the trip page and lets the activity show over the watch face and light the screen (see MainActivity).
    private fun wakeIntent(context: Context) =
        OpenRequest.intent(context, OpenRequest.Trip).putExtra(EXTRA_WAKE, true)

    /** Extra of the intent of an automatic open: the activity shows over the lock screen and turns the screen on. */
    const val EXTRA_WAKE = "io.github.jnbp.nextstop.wear.WAKE"

    /** What keeps the companion from opening over the watch face now, or null when nothing does. */
    fun blocker(context: Context): Outcome? {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return Outcome.NotificationsOff
        if (!manager.areNotificationsEnabled()) return Outcome.NotificationsOff
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return Outcome.NotificationsOff
        }
        val channel = manager.getNotificationChannel(CHANNEL)
        if (channel != null && channel.importance < NotificationManager.IMPORTANCE_HIGH) return Outcome.ChannelMuted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && !manager.canUseFullScreenIntent()) return Outcome.NoFullScreen
        return null
    }

    // The fallback when the system kept the activity back: one tap opens the companion. Returns what came of it.
    private fun notify(context: Context): Outcome {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return Outcome.NotificationsOff
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.trip_start_channel), NotificationManager.IMPORTANCE_HIGH),
            )
        }
        val blocked = blocker(context)
        // Notifications of the app are off on the watch: neither the nudge nor the ongoing activity shows.
        if (blocked == Outcome.NotificationsOff) return blocked
        val open = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            wakeIntent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(context.getString(R.string.trip_started_title))
            .setContentText(context.getString(R.string.trip_started_text))
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setTimeoutAfter(NOTIFICATION_TIMEOUT_MILLIS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            // Shown over the watch face where the system grants USE_FULL_SCREEN_INTENT (declared
            // once in the manifest, also for the wake-up alarm, see WakeRing); elsewhere a heads-up.
            .setFullScreenIntent(open, true)
            .build()
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            return Outcome.NotificationsOff
        }
        return blocked ?: Outcome.Opened
    }

    /** Removes the nudge: the app is in front now. */
    fun dismiss(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }
}
