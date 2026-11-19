package io.github.jnbp.nextstop.wear.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import io.github.jnbp.nextstop.wear.R
import io.github.jnbp.nextstop.wear.ui.WakeAlarmActivity

/** What the wake-up alarm of the phone says: where to get off ([stop]) and when ([arrival], a clock time "03:11"). */
data class WakeInfo(val key: String, val stop: String, val arrival: String, val ring: Int)

/**
 * The wake-up alarm of the phone on the watch ([Protocol.PATH_WAKE]): every ring of the phone
 * vibrates the watch long and shows [WakeAlarmActivity] over the watch face (through a
 * full-screen notification); "Ich bin wach" here tells the phone ([Protocol.MSG_WAKE_OK]),
 * which stops on both. When the phone stops, the watch stops too.
 */
object WakeRing {
    private const val CHANNEL = "wake_alarm"
    private const val NOTIFICATION = 9
    private val PATTERN = longArrayOf(0, 800, 400, 800, 400, 800)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // An item of the phone older than this is a ring that is over (the phone writes every minute).
    private const val MAX_AGE_MILLIS = 3 * 60_000L

    // One state after the other: a stop is never overtaken by the ring before it.
    private val order = Any()
    private var lastUpdated = 0L

    /** What rings now; null while nothing does. */
    val ringing = MutableStateFlow<WakeInfo?>(null)

    /** Takes the state the phone wrote. */
    fun take(context: Context, json: String) = synchronized(order) { takeInOrder(context, json) }

    private fun takeInOrder(context: Context, json: String) {
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            return
        }
        if (!root.optBoolean("ringing", false)) {
            val stopped = root.optLong("updated", 0L)
            if (stopped in 1 until lastUpdated) return
            if (stopped > 0) lastUpdated = stopped
            stop(context)
            return
        }
        // A ring delivered late (the watch was away from the phone): over by now.
        val updated = root.optLong("updated", 0L)
        // Older than the last state taken: overtaken, nothing to do.
        if (updated in 1 until lastUpdated) return
        if (updated > 0) lastUpdated = updated
        if (updated > 0 && System.currentTimeMillis() - updated > MAX_AGE_MILLIS) {
            stop(context)
            return
        }
        val arrival = root.optString("arrival").let { text ->
            // "2026-01-01T03:11+01:00" → "03:11"
            text.substringAfter('T', "").take(5)
        }
        val info = WakeInfo(root.optString("key"), root.optString("stop"), arrival, root.optInt("ring"))
        if (ringing.value == info) return
        ringing.value = info
        ring(context, info)
    }

    /** "Ich bin wach" on the watch: stops here and tells the phone. */
    fun confirm(context: Context) {
        val app = context.applicationContext
        synchronized(order) { stop(app) }
        scope.launch { sending.withLock { WatchStore.sendToPhone(app, Protocol.MSG_WAKE_OK, ByteArray(0)) } }
    }

    // The confirmations go to the phone one after the other.
    private val sending = Mutex()

    private fun stop(context: Context) {
        ringing.value = null
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION)
        context.getSystemService(Vibrator::class.java)?.cancel()
    }

    private fun ring(context: Context, info: WakeInfo) {
        context.getSystemService(Vibrator::class.java)?.takeIf { it.hasVibrator() }?.let { vibrator ->
            val amplitudes = IntArray(PATTERN.size) { index -> if (index % 2 == 0) 0 else 255 }
            val effect = VibrationEffect.createWaveform(PATTERN, amplitudes, -1)
            // As an alarm, so it comes through where touch vibration is switched off.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(effect, android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_ALARM).build())
            }
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.wake_channel), NotificationManager.IMPORTANCE_HIGH).apply {
                    enableVibration(true)
                    vibrationPattern = PATTERN
                },
            )
        }
        if (!manager.areNotificationsEnabled()) return
        val page = PendingIntent.getActivity(
            context,
            NOTIFICATION,
            Intent(context, WakeAlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(context.getString(R.string.wake_title))
            .setContentText(context.getString(R.string.wake_text, info.stop, info.arrival))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setContentIntent(page)
            .setFullScreenIntent(page, true)
            .build()
        try {
            manager.cancel(NOTIFICATION)
            manager.notify(NOTIFICATION, notification)
        } catch (e: SecurityException) {
            // No permission to post: the vibration alone has to do.
        }
    }
}
