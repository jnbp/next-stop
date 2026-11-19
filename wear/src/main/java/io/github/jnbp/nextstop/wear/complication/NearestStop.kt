package io.github.jnbp.nextstop.wear.complication

import android.content.Context
import io.github.jnbp.nextstop.wear.data.BvgClient
import io.github.jnbp.nextstop.wear.data.Place
import io.github.jnbp.nextstop.wear.data.Position
import io.github.jnbp.nextstop.wear.data.currentPosition
import io.github.jnbp.nextstop.wear.data.metresBetween
import io.github.jnbp.nextstop.wear.data.nearbyStopsWithFallback
import io.github.jnbp.nextstop.wear.data.parsePlaceJson
import io.github.jnbp.nextstop.wear.data.placeJson
import org.json.JSONObject

/*
 * The nearest stop for the complications "Departures nearby" and "Automatic". A complication
 * runs in the background, where the watch gives no fresh position fix: it takes the position
 * that is known (the last fix of the watch or the position the phone sent, see currentPosition
 * without waiting) and keeps the stop it found with where it was looked up. The stop is looked
 * up again only when the user moved more than REUSE_METRES or after REUSE_MILLIS, as the live
 * card of the start page does; the start page also hands its own nearest stop over (remember),
 * so the complication follows the app without a request of its own. Without any position the
 * stop kept stays for STORED_MAX_AGE_MILLIS.
 */
object NearestStop {
    private val client = BvgClient()

    private fun prefs(context: Context) = context.getSharedPreferences("complication-stop", Context.MODE_PRIVATE)

    /** The stop kept, where it was looked up and when. */
    private class Kept(val stop: Place, val latitude: Double, val longitude: Double, val timeMillis: Long)

    /** The nearest stop as well as the background allows; null without position and without a kept stop. */
    suspend fun find(context: Context, now: Long = System.currentTimeMillis()): Place? {
        val kept = read(context)
        val position = currentPosition(context, waitForFix = false)
        if (position != null) {
            if (kept != null && reusable(kept, position, now)) return kept.stop
            val found = loadOrNull("nearest stop") {
                nearbyStopsWithFallback(client, position.latitude, position.longitude, results = LOOKUP_STOPS)
                    .minByOrNull { it.distanceMeters ?: Int.MAX_VALUE }
            }
            if (found != null) {
                write(context, found, position, now)
                return found
            }
        }
        return kept?.takeIf { now - it.timeMillis < STORED_MAX_AGE_MILLIS }?.stop
    }

    /**
     * Keeps [stop], the nearest stop to [position] the app found itself; asks the complications
     * of nearby departures for new data when it is another stop than before, and with each
     * rewrite of the same stop (at most every REWRITE_MILLIS while the start page is open) only
     * those that show its board, so they show the board the app just loaded (#235: they take it
     * from DepartureCache without a request of their own; the disruptions would make one).
     */
    fun remember(context: Context, stop: Place, position: Position, now: Long = System.currentTimeMillis()) {
        val kept = read(context)
        // Every load of the start page's card ends here: write only what changed, or rarely.
        if (kept != null && kept.stop.id == stop.id && now - kept.timeMillis < REWRITE_MILLIS) return
        write(context, stop, position, now)
        if (kept?.stop?.id != stop.id) Complications.onNearestStopChanged(context) else Complications.onNearestBoardLoaded(context)
    }

    private fun reusable(kept: Kept, position: Position, now: Long): Boolean =
        now - kept.timeMillis < REUSE_MILLIS &&
            metresBetween(kept.latitude, kept.longitude, position.latitude, position.longitude) < REUSE_METRES

    private fun read(context: Context): Kept? {
        val text = prefs(context).getString(KEY_STOP, null) ?: return null
        return try {
            val root = JSONObject(text)
            val stop = parsePlaceJson(root.getJSONObject("stop")) ?: return null
            Kept(stop, root.getDouble("lat"), root.getDouble("lon"), root.getLong("at"))
        } catch (e: Exception) {
            null
        }
    }

    private fun write(context: Context, stop: Place, position: Position, now: Long) {
        val root = JSONObject()
            .put("stop", placeJson(stop))
            .put("lat", position.latitude)
            .put("lon", position.longitude)
            .put("at", now)
        prefs(context).edit().putString(KEY_STOP, root.toString()).apply()
    }

    private const val KEY_STOP = "nearest"

    // Stops asked for; the nearest of them is taken (as the live card of the start page does).
    private const val LOOKUP_STOPS = 3

    // The stop found is kept while the user stays within this distance (m) of where it was looked up, and at most this long.
    private const val REUSE_METRES = 80
    private const val REUSE_MILLIS = 10 * 60_000L

    // Without any position the stop kept stays this long: a watch at home overnight still shows its stop in the morning.
    private const val STORED_MAX_AGE_MILLIS = 12 * 60 * 60_000L

    // The same stop handed over by the start page is written again at most this often.
    private const val REWRITE_MILLIS = 5 * 60_000L
}
