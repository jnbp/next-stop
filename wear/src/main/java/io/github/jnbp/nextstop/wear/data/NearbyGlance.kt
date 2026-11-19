package io.github.jnbp.nextstop.wear.data

import android.content.Context
import android.util.Log
import kotlin.coroutines.cancellation.CancellationException
import io.github.jnbp.nextstop.wear.complication.NearestStop

/*
 * The live card of the start page: the stop nearest to the user and its next departures, from
 * the same pipeline as the pages behind it (the position of currentPosition, the stops of
 * nearbyStopsWithFallback, the departures of TransitRouter, kept in DepartureCache). The card
 * asks again every NEARBY_GLANCE_REFRESH_MILLIS while the start page is on screen; the nearest
 * stop is looked up again only when the user moved more than STOP_REUSE_METRES or after
 * STOP_REUSE_MILLIS, so a refresh is one request for departures and nothing more.
 */

/** The nearest stop, its departures as loaded at [timeMillis], and whether this load reached the network ([fresh]). */
data class NearbyGlance(val stop: Place, val departures: List<Departure>, val timeMillis: Long, val fresh: Boolean)

/** What one load of the live card came back with. */
sealed interface NearbyGlanceResult {
    /** Neither the watch nor the phone gave a position. */
    data object NoPosition : NearbyGlanceResult

    /** Nothing could be loaded and nothing is cached; [error] for the log. */
    data class Failed(val error: Exception) : NearbyGlanceResult

    /** Departures to show, fresh or (offline) from the cache with their age. */
    data class Ready(val glance: NearbyGlance) : NearbyGlanceResult
}

object NearbyGlances {
    private val client = BvgClient()

    /** The last card shown while the app runs, so the start page opens with it at once. Main thread only. */
    var last: NearbyGlance? = null
        private set

    // Where the nearest stop was looked up last, and when.
    private var lookup: Lookup? = null

    private class Lookup(val latitude: Double, val longitude: Double, val stop: Place, val timeMillis: Long)

    /**
     * Loads the card once. The first load of the process waits for a position fix (as the
     * nearby page does); later ones take the position that is known. A failed request falls
     * back to the departures kept for the stop.
     */
    suspend fun load(context: Context, now: Long = System.currentTimeMillis()): NearbyGlanceResult {
        val position = currentPosition(context, waitForFix = last == null)
        val stop: Place = try {
            if (position == null) {
                // Without a position the stop seen last still has its kept departures.
                return last?.let { cached(context, it.stop) } ?: NearbyGlanceResult.NoPosition
            }
            nearestStop(position, now) ?: return NearbyGlanceResult.Failed(IllegalStateException("no stop nearby"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(LOG_TAG, "glance: stops failed: $e")
            return last?.let { cached(context, it.stop) } ?: NearbyGlanceResult.Failed(e)
        }
        return try {
            val departures = TransitRouter.departures(context, stop, results = GLANCE_DEPARTURES)
            DepartureCache.put(context, stop.id, departures)
            val glance = NearbyGlance(stop, departures, System.currentTimeMillis(), fresh = true)
            last = glance
            // The complications of nearby departures take this stop without a lookup of their own.
            position?.let { NearestStop.remember(context, stop, it) }
            NearbyGlanceResult.Ready(glance)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(LOG_TAG, "glance: departures of ${stop.id} failed: $e")
            cached(context, stop) ?: NearbyGlanceResult.Failed(e)
        }
    }

    /** The nearest stop to [position]: the one found last when the user has hardly moved since, else looked up. */
    private suspend fun nearestStop(position: Position, now: Long): Place? {
        val previous = lookup
        if (previous != null && now - previous.timeMillis < STOP_REUSE_MILLIS) {
            val moved = metresBetween(previous.latitude, previous.longitude, position.latitude, position.longitude)
            if (moved < STOP_REUSE_METRES) return withDistance(previous.stop, position)
        }
        val found = nearbyStopsWithFallback(client, position.latitude, position.longitude, results = GLANCE_STOPS)
            .minByOrNull { it.distanceMeters ?: Int.MAX_VALUE } ?: return null
        lookup = Lookup(position.latitude, position.longitude, found, now)
        return found
    }

    private fun withDistance(stop: Place, position: Position): Place {
        val latitude = stop.latitude ?: return stop
        val longitude = stop.longitude ?: return stop
        return stop.copy(distanceMeters = metresBetween(position.latitude, position.longitude, latitude, longitude))
    }

    /** The departures kept for [stop] as a card that is not fresh; null when none are kept. */
    private suspend fun cached(context: Context, stop: Place): NearbyGlanceResult? {
        val kept = DepartureCache.get(context, stop.id) ?: return null
        return NearbyGlanceResult.Ready(NearbyGlance(stop, kept.departures, kept.timeMillis, fresh = false))
    }
}

/** How often the live card loads again while the start page is on screen: often enough for live times, light on the battery. */
const val NEARBY_GLANCE_REFRESH_MILLIS = 45_000L

// The nearest stop is kept while the user stays within this distance of where it was looked up, and at most this long.
private const val STOP_REUSE_METRES = 80
private const val STOP_REUSE_MILLIS = 10 * 60_000L

// Stops asked for (the nearest of them is taken) and departures loaded for the card (it shows four or five).
private const val GLANCE_STOPS = 3
private const val GLANCE_DEPARTURES = 8
