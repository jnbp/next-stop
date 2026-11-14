package io.github.jnbp.nextstop.wear.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import io.github.jnbp.nextstop.wear.R
import io.github.jnbp.nextstop.wear.ui.MainActivity

/**
 * The two alerts of a trip, each with a pattern of its own that can be told apart without
 * looking: getting off at the end of the trip, and getting off to change to another ride.
 */
enum class TripAlert(val channel: String, val channelName: Int, val title: Int, val timings: LongArray) {
    /** The last ride ends at the next stop: three long pulses. */
    GetOff("get_off", R.string.alert_channel, R.string.next_stop_get_off, longArrayOf(0, 400, 200, 400, 200, 700)),

    /** A change at the next stop: two quick double taps. */
    Change("change", R.string.alert_channel_change, R.string.next_stop_change, longArrayOf(0, 120, 90, 120, 450, 120, 90, 120)),
}

/** Vibrates the pattern of [alert], unlike the short buzz of a notification. */
fun vibrate(context: Context, alert: TripAlert) {
    val vibrator = context.getSystemService(Vibrator::class.java) ?: return
    if (!vibrator.hasVibrator()) return
    // Every second entry (from the first) is a pause, the others pulses at full strength.
    val amplitudes = IntArray(alert.timings.size) { index -> if (index % 2 == 0) 0 else 255 }
    vibrator.vibrate(VibrationEffect.createWaveform(alert.timings, amplitudes, -1))
}

/**
 * The alert as a notification as well: a vibration of an app in the background may be held
 * back by the system, a notification of its own channel is not. Needs the permission to post
 * notifications, which the app asks for on its first start. Each alert has its own channel,
 * since a channel keeps the vibration pattern it was created with.
 */
fun notifyAlert(context: Context, alert: TripAlert, place: String) {
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    if (manager.getNotificationChannel(alert.channel) == null) {
        val channel = NotificationChannel(alert.channel, context.getString(alert.channelName), NotificationManager.IMPORTANCE_HIGH).apply {
            enableVibration(true)
            vibrationPattern = alert.timings
        }
        manager.createNotificationChannel(channel)
    }
    if (!manager.areNotificationsEnabled()) return
    val open = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    val notification = NotificationCompat.Builder(context, alert.channel)
        .setSmallIcon(R.drawable.ic_launcher_monochrome)
        .setContentTitle(context.getString(alert.title))
        .setContentText(place)
        .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(open)
        .setAutoCancel(true)
        .build()
    try {
        manager.notify(ALERT_NOTIFICATION, notification)
    } catch (e: SecurityException) {
        // No permission to post: the vibration alone has to do.
    }
}

/** Vibrates and notifies, unless the reminder is off: the one call for "get off at the next stop", [change] for a change there. */
fun alertGetOff(context: Context, place: String, change: Boolean = false) {
    // Switched off (on the phone or the watch): no vibration, no alert notification; the trip stays.
    if (!WatchStore.alightRemindersOn(context)) return
    val alert = if (change) TripAlert.Change else TripAlert.GetOff
    vibrate(context, alert)
    notifyAlert(context, alert, place)
}

private const val ALERT_NOTIFICATION = 7
