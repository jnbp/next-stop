package io.github.jnbp.nextstop.wear.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import io.github.jnbp.nextstop.wear.R
import io.github.jnbp.nextstop.wear.ui.OpenRequest

/**
 * The two alerts of a trip, each with a pattern of its own that can be told apart without
 * looking (see [TripBuzz]): getting off at the end of the trip, and getting off to change to
 * another ride. The channel ids carry a version: a channel keeps the pattern it was created
 * with, so new patterns need new channels ([RETIRED_ALERT_CHANNELS] go).
 */
enum class TripAlert(val channel: String, val channelName: Int, val title: Int, val buzz: TripBuzz) {
    /** The last ride ends at the next stop: one long, one short. */
    GetOff("get_off_2", R.string.alert_channel, R.string.next_stop_get_off, TripBuzz.GetOffNext),

    /** A change at the next stop: three short taps. */
    Change("change_2", R.string.alert_channel_change, R.string.next_stop_change, TripBuzz.ChangeNow),
    ;

    val timings: LongArray get() = buzzPattern(buzz).timings
}

/** The channels of the alerts with their first patterns, removed when an alert channel is made. */
private val RETIRED_ALERT_CHANNELS = listOf("get_off", "change")

/** Vibrates the pattern of [alert], unlike the short buzz of a notification. */
fun vibrate(context: Context, alert: TripAlert) {
    playBuzz(context, alert.buzz)
}

/**
 * Plays the pattern of [event] (see [buzzPattern]) once, as a notification vibration: the
 * system's intensity of notification vibration applies (off is off), and nothing plays while
 * Do Not Disturb or bedtime holds notifications back.
 */
fun playBuzz(context: Context, event: TripBuzz) {
    val vibrator = context.getSystemService(Vibrator::class.java) ?: return
    if (!vibrator.hasVibrator()) return
    val filter = context.getSystemService(NotificationManager::class.java)?.currentInterruptionFilter
    if (filter != null && filter != NotificationManager.INTERRUPTION_FILTER_ALL && filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN) return
    val pattern = buzzPattern(event)
    val effect = VibrationEffect.createWaveform(pattern.timings, pattern.amplitudes, -1)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_NOTIFICATION))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).build())
    }
}

/**
 * The alert as a notification as well: a vibration of an app in the background may be held
 * back by the system, a notification of its own channel is not. Needs the permission to post
 * notifications, which the app asks for on its first start. Each alert has its own channel,
 * since a channel keeps the vibration pattern it was created with.
 */
fun notifyAlert(context: Context, alert: TripAlert, place: String, key: String = "") {
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    if (manager.getNotificationChannel(alert.channel) == null) {
        RETIRED_ALERT_CHANNELS.forEach { manager.deleteNotificationChannel(it) }
        val channel = NotificationChannel(alert.channel, context.getString(alert.channelName), NotificationManager.IMPORTANCE_HIGH).apply {
            enableVibration(true)
            vibrationPattern = alert.timings
        }
        manager.createNotificationChannel(channel)
    }
    if (!manager.areNotificationsEnabled()) return
    // The one action opens the live trip on the watch (#183). No content intent: Wear OS would
    // show it as a second button "App öffnen" that only opens the start page.
    val open = PendingIntent.getActivity(
        context,
        0,
        OpenRequest.intent(context, OpenRequest.Trip),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    val notification = NotificationCompat.Builder(context, alert.channel)
        .setSmallIcon(R.drawable.ic_launcher_monochrome)
        .setContentTitle(context.getString(alert.title))
        .setContentText(place)
        .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .addAction(R.drawable.ic_route, context.getString(R.string.r6live_open_live_trip), open)
        .setAutoCancel(true)
        // It goes once the trip moves on (see clearOutlivedAlert); this is the net below that.
        .setTimeoutAfter(ALERT_TIMEOUT_MILLIS)
        .build()
    try {
        manager.notify(ALERT_NOTIFICATION, notification)
        alertPrefs(context).edit().putString(KEY_SHOWN, alert.name + "|" + key).apply()
    } catch (e: SecurityException) {
        // No permission to post: the vibration alone has to do.
    }
}

/** Vibrates and notifies, unless the reminder is off: the one call for "get off at the next stop", [change] for a change there. */
fun alertGetOff(context: Context, place: String, change: Boolean = false, key: String = "") {
    // Switched off (on the phone or the watch): no vibration, no alert notification; the trip stays.
    if (!WatchStore.alightRemindersOn(context)) return
    val alert = if (change) TripAlert.Change else TripAlert.GetOff
    vibrate(context, alert)
    notifyAlert(context, alert, place, key)
}

/**
 * Takes the alert away once the trip [trip] has moved on from the ride it was posted for (see
 * [alertOutlived]): the change is done or the train has left. Cheap: one preference read.
 */
fun clearOutlivedAlert(context: Context, trip: TripSnapshot) {
    val prefs = alertPrefs(context)
    val shown = prefs.getString(KEY_SHOWN, null) ?: return
    if (!alertOutlived(shown.substringAfter('|'), trip)) return
    context.getSystemService(NotificationManager::class.java)?.cancel(ALERT_NOTIFICATION)
    prefs.edit().remove(KEY_SHOWN).apply()
}

private fun alertPrefs(context: Context) = context.getSharedPreferences(ALERT_PREFS, Context.MODE_PRIVATE)

private const val ALERT_PREFS = "trip_alert"
private const val KEY_SHOWN = "shown"

private const val ALERT_NOTIFICATION = 7
