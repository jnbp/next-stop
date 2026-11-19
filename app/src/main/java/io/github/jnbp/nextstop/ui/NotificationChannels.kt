package io.github.jnbp.nextstop.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.provider.Settings
import io.github.jnbp.nextstop.R

/**
 * The notification channels of the app, in one place: each with its kind (id, see
 * [ChannelKind]), its name and description in the system settings and how it alerts. Which
 * notification goes out on which channel is [channelKindOf]. Sound and vibration of a channel
 * belong to the user once it exists (Android keeps them); the settings of the app open them
 * per channel.
 */
enum class AppChannel(val kind: ChannelKind, val title: Int, val description: Int) {
    Live(ChannelKind.Live, R.string.r6chan_live, R.string.r6chan_live_text),
    Important(ChannelKind.Important, R.string.r6chan_important, R.string.r6chan_important_text),
    Suggestions(ChannelKind.Suggestions, R.string.r6chan_suggestions, R.string.r6chan_suggestions_text),
    Alarm(ChannelKind.Alarm, R.string.r6chan_alarm, R.string.r6chan_alarm_text),
    Disruptions(ChannelKind.Disruptions, R.string.r6chan_disruptions, R.string.r6chan_disruptions_text),
    ;

    val id: String get() = kind.id

    companion object {
        /** The channel [feature] goes out on. */
        fun of(feature: NotifyFeature): AppChannel = channelKindOf(feature).let { kind -> entries.first { it.kind == kind } }
    }
}

/** The pattern of the wake-up alarm: three long pulses. */
internal val WAKE_VIBRATION = longArrayOf(0, 800, 400, 800, 400, 800)

/**
 * Removes the channels from before [ChannelKind] (see [RETIRED_CHANNEL_IDS]); nothing is
 * carried over, the new channels start with their own alerts. Called on every start.
 */
fun retireOldChannels(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    manager.notificationChannels
        .filter { it.id in RETIRED_CHANNEL_IDS }
        .forEach { manager.deleteNotificationChannel(it.id) }
}

/**
 * Creates [channel] with its alerts; creating one that exists changes nothing but its name
 * and description, so the user's choice of sound and vibration stays.
 */
fun ensureChannel(context: Context, channel: AppChannel) {
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    val name = context.getString(channel.title)
    val made = when (channel) {
        // Importance default, so the trip shows on the lock screen and the always-on display at
        // once, but no sound and no vibration, public on the lock screen, no badge.
        AppChannel.Live -> NotificationChannel(channel.id, name, NotificationManager.IMPORTANCE_DEFAULT).apply {
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        // Get off, platform changed, connection broke, time to leave: sound, vibration, pops up.
        AppChannel.Important -> NotificationChannel(channel.id, name, NotificationManager.IMPORTANCE_HIGH).apply {
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        // Shown in the list, but no sound, no vibration and no pop-up.
        AppChannel.Suggestions -> NotificationChannel(channel.id, name, NotificationManager.IMPORTANCE_LOW).apply {
            setSound(null, null)
            enableVibration(false)
        }
        // Like an alarm clock: the sound of the alarm on the stream of alarms, long vibration.
        AppChannel.Alarm -> NotificationChannel(channel.id, name, NotificationManager.IMPORTANCE_HIGH).apply {
            val sound = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            setSound(
                sound,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            enableVibration(true)
            vibrationPattern = WAKE_VIBRATION
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        AppChannel.Disruptions -> NotificationChannel(channel.id, name, NotificationManager.IMPORTANCE_DEFAULT)
    }
    made.description = context.getString(channel.description)
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
