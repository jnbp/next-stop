package io.github.jnbp.nextstop.ui

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.jnbp.nextstop.MainActivity
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.DiagLog
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.RescueNotices
import io.github.jnbp.nextstop.data.TripNotice
import io.github.jnbp.nextstop.data.noticeJourneyHash
import kotlinx.coroutines.flow.MutableStateFlow
import io.github.jnbp.nextstop.ui.theme.storedPalette
import kotlinx.coroutines.flow.StateFlow

private const val RESCUE_NOTIFICATION = 8

/**
 * The best connection that still works where a delay broke the chosen one at a change of the
 * followed trip (#98): [option] at the change keeping [keepLegs] legs of [journeyId], at [stop].
 */
data class RescueSuggestion(val journeyId: String, val keepLegs: Int, val stop: String, val option: Journey)

/**
 * "Anschluss klappt nicht mehr – nimm S7 um 16:06?": the suggestion for the companion's hero
 * area ([suggestion], with one tap to switch there) and a notification once per broken change
 * on a channel of its own (the user can mute it in the system settings, as every channel).
 * Fed by the root of the app with every load of the connections at the change ahead.
 */
object ConnectionRescue {
    private const val TAG = "ConnectionRescue"
    private val state = MutableStateFlow<RescueSuggestion?>(null)
    private val notices = RescueNotices()

    /** The suggestion of the moment; null while the chosen connection works. */
    val suggestion: StateFlow<RescueSuggestion?> get() = state

    /** Takes the newest [found] for the followed trip (null: nothing to suggest). */
    fun update(context: Context, found: RescueSuggestion?) {
        state.value = found
        // One that works again, was switched or is another now goes (see TripNoticeSweep).
        TripNoticeSweep.sweep(context)
        if (found == null || !notices.firstTime(found.journeyId, found.keepLegs)) return
        DiagLog.i(TAG) { "connection broken at a change" }
        notify(context, found)
    }

    /** The trip ended: nothing to suggest, every change news again. */
    fun clear() {
        state.value = null
        notices.clear()
        RescueInBackground.searches.clear()
    }

    /** "Take the S7 at 16:06?" for [found]. */
    fun text(context: Context, found: RescueSuggestion): String {
        val ride = found.option.rides.first()
        return context.getString(R.string.r5_rescue, shortLine(ride.line) ?: ride.line.orEmpty(), ride.departure.clock())
    }

    @SuppressLint("MissingPermission")
    private fun notify(context: Context, found: RescueSuggestion) {
        // Tagged by its journey and change, so it can be taken back (see TripNoticeSweep).
        val followed = TripState.journey.value?.takeIf { it.id == found.journeyId } ?: return
        val tag = TripNotice.Rescue(noticeJourneyHash(followed), found.keepLegs).tag
        ensureChannel(context, AppChannel.of(NotifyFeature.Connection))
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val text = text(context, found)
        // A tap opens the companion, where the suggestion waits with its button to switch.
        val open = PendingIntent.getActivity(
            context,
            23,
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_OPEN_TRIP)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, AppChannel.of(NotifyFeature.Connection).id)
            .setSmallIcon(R.drawable.ic_stat_trip)
            .setColor(storedPalette(context).blue.toArgb())
            .setContentTitle(context.getString(R.string.r5_rescue_title, found.stop))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(tag, RESCUE_NOTIFICATION, notification)
        } catch (e: SecurityException) {
            // The permission was taken away in the meantime.
        }
    }
}
