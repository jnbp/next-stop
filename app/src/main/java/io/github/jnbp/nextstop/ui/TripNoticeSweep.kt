package io.github.jnbp.nextstop.ui

import android.app.NotificationManager
import android.content.Context
import java.time.OffsetDateTime
import io.github.jnbp.nextstop.data.TripNotice
import io.github.jnbp.nextstop.data.keepTripNotice
import io.github.jnbp.nextstop.data.noticeJourneyHash

/**
 * Takes back the notifications about the followed trip that no longer apply (#172): a platform
 * change of a ride that has left or is no longer part of the trip, a broken connection that
 * works again, was switched or passed, a reminder to get off after the arrival; at the end of
 * the trip all of them (not the disruptions, which are about the routes). Runs with what wakes
 * up anyway: every new state of the journey and the trip service's look at the reminders.
 */
object TripNoticeSweep {
    /** Looks at the shown notifications of the trip; [ended]: the trip is over, all of them go. */
    fun sweep(context: Context, ended: Boolean = false) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val shown = try {
            manager.activeNotifications
        } catch (e: Exception) {
            return
        }
        val journey = if (ended) null else TripState.journey.value
        val now = OffsetDateTime.now()
        val shifts = PlatformAlerts.shifts.value
        val rescue = ConnectionRescue.suggestion.value
            ?.takeIf { journey != null && it.journeyId == journey.id }
            ?.let { TripNotice.Rescue(noticeJourneyHash(journey!!), it.keepLegs) }
        for (status in shown) {
            val notice = TripNotice.parse(status.tag) ?: continue
            if (!keepTripNotice(notice, journey, now, shifts, rescue)) manager.cancel(status.tag, status.id)
        }
    }
}
