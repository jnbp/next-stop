package io.github.jnbp.nextstop.ui

import android.content.Context
import io.github.jnbp.nextstop.data.AppPrefs
import io.github.jnbp.nextstop.data.DiagLog
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.RescueSearches
import io.github.jnbp.nextstop.data.RoutingSource
import io.github.jnbp.nextstop.data.plainStopName
import io.github.jnbp.nextstop.data.plausible
import io.github.jnbp.nextstop.data.rescueChoice
import io.github.jnbp.nextstop.data.sameChoiceRide
import io.github.jnbp.nextstop.data.viaAhead
import io.github.jnbp.nextstop.ui.companion.CompanionOffers
import java.time.OffsetDateTime
import kotlinx.coroutines.CancellationException

/**
 * The connection rescue (#98) while the app's companion is not on screen: the trip service
 * calls [check] after each refresh of the followed trip (it runs as a foreground service only
 * while a trip is followed). Where the change ahead can no longer be made, the ways on from
 * there are searched once per broken change ([searches], shared with the companion of the app,
 * which stores what it loads there, so neither searches what the other has); after a failure
 * again after two minutes; never with the battery saver at Extreme. Each refresh judges the
 * kept ways anew by the live times, so the offer follows the delays without a new search. The
 * offer goes to [ConnectionRescue]: its notification (once per change) and the watch's card.
 */
object RescueInBackground {
    private const val TAG = "RescueInBackground"

    /** The ways on found at broken changes, by journey and change (see [RescueSearches]). */
    val searches = RescueSearches<List<Journey>>()

    suspend fun check(context: Context, source: RoutingSource, followed: Journey) {
        val now = OffsetDateTime.now()
        val ahead = CompanionOffers.changeFor(rideJourney(followed, now), now)
        val shown = ConnectionRescue.suggestion.value?.takeIf { it.journeyId == followed.id }
        if (ahead == null || ahead.time.possible) {
            // The change works again (or lies behind): its offer goes.
            if (shown != null && (ahead == null || shown.keepLegs == ahead.keepLegs)) ConnectionRescue.update(context, null)
            return
        }
        val to = followed.legs.last().toPlace ?: return
        val key = RescueSearches.changeKey(followed.id, ahead.keepLegs)
        val options = searches.options(key) ?: run {
            val clock = System.currentTimeMillis()
            if (!searches.mayBackgroundSearch(key, clock, BatterySaver.extreme)) return
            try {
                val page = source.journeys(
                    from = ahead.stop.copy(distanceMeters = null),
                    to = to,
                    options = AppPrefs(context).searchOptions.copy(via = viaAhead(followed, followed.legs.take(ahead.keepLegs))),
                    results = OFFER_RESULTS,
                    departure = offerStart(ahead.arriving),
                )
                page.journeys.distinctBy { it.id }.sortedBy { it.departure }.also {
                    searches.put(key, it)
                    DiagLog.i(TAG) { "ways on at a broken change searched in the background: ${it.size}" }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                searches.failed(key, clock)
                DiagLog.i(TAG) { "background search at a broken change failed: ${e.javaClass.simpleName}" }
                return
            }
        }
        val arriving = ahead.arriving.plausible()
        val alternatives = Alternatives(
            stopId = ahead.stop.id,
            stopName = ahead.stop.name,
            keepLegs = ahead.keepLegs,
            arrival = arriving.arrival,
            arrivalEstimated = arriving.arrivalEstimated,
            loading = false,
            options = options,
            current = Journey(legs = followed.legs.drop(ahead.keepLegs)),
        )
        // As the companion of the app works it out (AppRoot, #98).
        val offered = connectionOptions(ahead, alternatives)
        val chosenIndex = offered.indexOfFirst { sameChoiceRide(it.rides.first(), ahead.next) }
        val rescue = rescueChoice(chosenIndex, optionTimes(ahead, offered))?.let { offered[it] }
        ConnectionRescue.update(context, rescue?.let { RescueSuggestion(followed.id, ahead.keepLegs, ahead.stop.name.plainStopName(), it) })
    }
}
