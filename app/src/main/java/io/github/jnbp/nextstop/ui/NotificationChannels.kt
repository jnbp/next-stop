package io.github.jnbp.nextstop.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import io.github.jnbp.nextstop.R

/**
 * The notification channels of the app, in one place: each with its id, its name in the
 * system settings and how it alerts. Sound and vibration of a channel belong to the user
 * once it exists (Android keeps them); the settings of the app open them per channel.
 */
enum class AppChannel(val id: String, val title: Int) {
    /** Get off: one stop before and shortly before the arrival, at every change and the destination. */
    Alight("alight", R.string.alight_channel),

    /** The followed trip, live and quiet. */
    Trip("trip_live", R.string.trip_channel),

    /** "Abkürzungs-Alarm": something faster turned up for a followed trip. */
    Shortcut("faster", R.string.faster_channel),

    /** A trip started on the watch. */
    Watch("watch", R.string.wear_channel),

    /** "Störungen": new disruptions on a watched route (see DisruptionWatch). */
    Disruptions("disruptions", R.string.disruption_channel),
}

/** The channel id of the trip before it became quiet; removed when the trip channel is made. */
private const val OLD_TRIP_CHANNEL = "trip"

/**
 * Creates [channel] with its alerts; creating one that exists changes nothing but its name,
 * so the user's choice of sound and vibration stays.
 */
fun ensureChannel(context: Context, channel: AppChannel) {
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    val name = context.getString(channel.title)
    val made = when (channel) {
        AppChannel.Alight -> NotificationChannel(channel.id, name, NotificationManager.IMPORTANCE_HIGH).apply {
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        // Importance default, so the trip shows on the lock screen and the always-on display at
        // once, but no sound and no vibration, public on the lock screen, no badge.
        AppChannel.Trip -> {
            if (manager.getNotificationChannel(OLD_TRIP_CHANNEL) != null) manager.deleteNotificationChannel(OLD_TRIP_CHANNEL)
            NotificationChannel(channel.id, name, NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        }
        // It alerts with sound and vibration and pops up, unlike the quiet one of the trip.
        AppChannel.Shortcut -> NotificationChannel(channel.id, name, NotificationManager.IMPORTANCE_HIGH).apply {
            enableVibration(true)
        }
        AppChannel.Watch -> NotificationChannel(channel.id, name, NotificationManager.IMPORTANCE_DEFAULT)
        AppChannel.Disruptions -> NotificationChannel(channel.id, name, NotificationManager.IMPORTANCE_DEFAULT)
    }
    manager.createNotificationChannel(made)
}

/**
 * Opens the system settings of [channel] (sound, vibration, importance), creating the
 * channel first, as Android shows only channels that exist; the app's notification settings
 * where the channel page cannot be opened.
 */
fun openChannelSettings(context: Context, channel: AppChannel) {
    ensureChannel(context, channel)
    val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .putExtra(Settings.EXTRA_CHANNEL_ID, channel.id)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
