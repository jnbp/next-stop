package io.github.jnbp.nextstop.ui

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.time.OffsetDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import io.github.jnbp.nextstop.MainActivity
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.AppPrefs
import io.github.jnbp.nextstop.data.DiagLog
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.PlatformShift
import io.github.jnbp.nextstop.data.newPlatformShifts
import io.github.jnbp.nextstop.data.plainStopName
import io.github.jnbp.nextstop.data.keepTripNotice
import io.github.jnbp.nextstop.data.platformNotice
import io.github.jnbp.nextstop.data.platformShifts
import io.github.jnbp.nextstop.data.plausible
import io.github.jnbp.nextstop.data.rememberPlatforms
import io.github.jnbp.nextstop.ui.theme.storedPalette

private const val PLATFORM_NOTIFICATION = 7

/**
 * Changes of platform on a followed trip: the platform of every ride is remembered as it was
 * first seen (or the timetable's, where the source sends it apart), and a change notifies once
 * on a channel of its own (setting [AppPrefs.platformAlerts], on by default). [shifts] holds the
 * changes of the trip for the companion and the details, which show the new platform marked.
 * Fed by [TripService] with every new state of the journey; nothing of it is stored.
 */
object PlatformAlerts {
    private const val TAG = "PlatformAlerts"
    private val state = MutableStateFlow<List<PlatformShift>>(emptyList())
    private var firstSeen: Map<String, String> = emptyMap()
    private val told = mutableSetOf<String>()

    /** The platform changes of the followed trip; empty without a trip or a change. */
    val shifts: StateFlow<List<PlatformShift>> get() = state

    /** Takes the newest state of the followed journey ([journey] null: the trip ended). */
    fun update(context: Context, journey: Journey?) {
        if (journey == null) {
            synchronized(this) {
                firstSeen = emptyMap()
                told.clear()
            }
            state.value = emptyList()
            return
        }
        val now = OffsetDateTime.now()
        val legs = journey.plausible().legs
        val rideOver = legs.indices.lastOrNull { !legs[it].walking && !legs[it].arrival.isAfter(now) } ?: -1
        val fresh = synchronized(this) {
            firstSeen = rememberPlatforms(journey, firstSeen)
            val all = platformShifts(journey, firstSeen)
            state.value = all
            newPlatformShifts(all, told, rideOver).also { list -> list.forEach { told.add(it.key) } }
        }
        if (fresh.isEmpty() || !AppPrefs(context).platformAlerts) return
        DiagLog.i(TAG) { "platform changed: ${fresh.size}" }
        // One notification per ride and stop, under its own tag, so it can be taken back (see TripNoticeSweep).
        fresh.forEach { notify(context, journey, it) }
    }

    /** "S1 now leaves Example A from platform 5 (was 3)". */
    fun text(context: Context, journey: Journey, shift: PlatformShift): String {
        val leg = journey.legs.getOrNull(shift.legIndex)
        val line = leg?.let { shortLine(it.line) ?: it.line }.orEmpty()
        val stop = shift.stop.plainStopName()
        return context.getString(
            if (shift.departure) R.string.platform_changed_dep else R.string.platform_changed_arr,
            line,
            stop,
            shift.now,
            shift.planned,
        )
    }

    @SuppressLint("MissingPermission")
    private fun notify(context: Context, journey: Journey, shift: PlatformShift) {
        // Only for a ride of the followed journey (never one merely offered, #172).
        val notice = platformNotice(journey, shift) ?: return
        // A ride that already left that stop: nothing to tell any more.
        if (!keepTripNotice(notice, journey, OffsetDateTime.now(), state.value, null)) return
        ensureChannel(context, AppChannel.of(NotifyFeature.Platform))
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val text = text(context, journey, shift)
        val open = PendingIntent.getActivity(
            context,
            22,
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_OPEN_TRIP)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, AppChannel.of(NotifyFeature.Platform).id)
            .setSmallIcon(R.drawable.ic_stat_trip)
            .setColor(storedPalette(context).blue.toArgb())
            .setContentTitle(context.getString(R.string.platform_changed_title, shift.now))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(notice.tag, PLATFORM_NOTIFICATION, notification)
        } catch (e: SecurityException) {
            // The permission was taken away in the meantime.
        }
    }
}
