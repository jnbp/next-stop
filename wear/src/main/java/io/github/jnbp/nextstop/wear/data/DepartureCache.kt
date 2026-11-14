package io.github.jnbp.nextstop.wear.data

import android.content.Context
import android.util.Log
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Departures of a stop as last loaded, and when (epoch milliseconds). */
data class CachedDepartures(val departures: List<Departure>, val timeMillis: Long)

/**
 * The last loaded departures of the favourite stops and of the stop viewed last, kept in
 * the preferences of the watch, so the app, the tile and the complication still have them
 * without a connection (shown with their age). Departures that have left are dropped when
 * read. At most [MAX_STOPS] stops are kept: the favourites first, then the latest viewed.
 *
 * The app, the tile and the complication read and write it at once, each from a thread of
 * its own: every access runs off the main thread and under [lock], so a write (read, change,
 * write back) never loses another.
 */
object DepartureCache {
    private val lock = Any()

    private fun prefs(context: Context) = context.getSharedPreferences("departure-cache", Context.MODE_PRIVATE)

    /** The cached departures of [stopId] that have not left yet, or null without any. */
    suspend fun get(context: Context, stopId: String, now: Long = System.currentTimeMillis()): CachedDepartures? =
        withContext(Dispatchers.IO) { synchronized(lock) { read(context, stopId, now) } }

    private fun read(context: Context, stopId: String, now: Long): CachedDepartures? {
        val text = prefs(context).getString(KEY_STOPS, null) ?: return null
        return try {
            val entry = JSONObject(text).optJSONObject(stopId) ?: return null
            val list = entry.optJSONArray("departures") ?: return null
            // Through the rules of the board again: a board cached before them may show a run twice.
            val departures = departureBoard((0 until list.length()).mapNotNull { index -> list.optJSONObject(index)?.let { parse(it) } })
                .filter { it.time == null || it.time > now - LEFT_GRACE_MILLIS }
            CachedDepartures(departures, entry.optLong("time"))
        } catch (e: Exception) {
            null
        }
    }

    /** Keeps [departures] of [stopId] as the newest entry; the oldest non-favourites go first when full. */
    suspend fun put(context: Context, stopId: String, departures: List<Departure>, now: Long = System.currentTimeMillis()) {
        withContext(Dispatchers.IO) { synchronized(lock) { write(context, stopId, departures, now) } }
    }

    private fun write(context: Context, stopId: String, departures: List<Departure>, now: Long) {
        val prefs = prefs(context)
        val root = try {
            JSONObject(prefs.getString(KEY_STOPS, null) ?: "{}")
        } catch (e: Exception) {
            JSONObject()
        }
        root.put(
            stopId,
            JSONObject()
                .put("time", now)
                .put("departures", JSONArray().apply { departures.forEach { put(toJson(it)) } }),
        )
        val favorites = favoriteStopIds()
        val keys = root.keys().asSequence().toList()
        if (keys.size > MAX_STOPS) {
            val drop = keys.filter { it !in favorites && it != stopId }
                .sortedBy { root.optJSONObject(it)?.optLong("time") ?: 0 }
                .take(keys.size - MAX_STOPS)
            drop.forEach { root.remove(it) }
        }
        // Written through at once (on IO), so the next reader under the lock sees it.
        prefs.edit().putString(KEY_STOPS, root.toString()).commit()
    }

    /**
     * Loads the departures of the first favourite stops whose cache is older than
     * [STALE_MILLIS], so they are there when the watch goes offline. Failures are ignored.
     */
    suspend fun prefetchFavorites(context: Context, client: BvgClient) {
        val now = System.currentTimeMillis()
        for (stopId in favoriteStopIds().take(PREFETCH_STOPS)) {
            val cached = get(context, stopId, now)
            if (cached != null && now - cached.timeMillis < STALE_MILLIS) continue
            try {
                put(context, stopId, client.departures(stopId, results = 12))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(LOG_TAG, "cache: prefetch of $stopId failed: $e")
            }
        }
    }

    private fun favoriteStopIds(): List<String> {
        val places = WatchStore.places.value
        return (places.favorites + places.saved)
            .map { it.place }
            .filter { it.kind == PlaceKind.Stop && !it.id.contains(':') }
            .map { it.id }
            .distinct()
    }

    private fun toJson(departure: Departure): JSONObject = JSONObject().apply {
        put("line", departure.line)
        put("product", departure.product)
        put("direction", departure.direction)
        put("time", departure.time ?: JSONObject.NULL)
        put("delay", departure.delayMinutes ?: JSONObject.NULL)
        put("platform", departure.platform ?: JSONObject.NULL)
        put("cancelled", departure.cancelled)
        put("planned", departure.plannedTime ?: JSONObject.NULL)
        if (departure.additional) put("additional", true)
        departure.replaces?.let { put("replaces", it) }
    }

    private fun parse(item: JSONObject): Departure = Departure(
        // A board cached before line names were completed may still have "1" for an S1.
        line = rapidLineName(item.optString("line", "?"), item.optString("product", "")) ?: "?",
        product = item.optString("product", ""),
        direction = item.optString("direction", ""),
        time = if (item.isNull("time")) null else item.optLong("time"),
        delayMinutes = if (item.isNull("delay")) null else item.optInt("delay"),
        platform = if (item.isNull("platform")) null else item.optString("platform"),
        cancelled = item.optBoolean("cancelled", false),
        plannedTime = if (item.isNull("planned")) null else item.optLong("planned"),
        additional = item.optBoolean("additional", false),
        replaces = if (item.isNull("replaces")) null else item.optString("replaces"),
    )

    private const val KEY_STOPS = "stops"
    private const val MAX_STOPS = 8
    private const val PREFETCH_STOPS = 3
    private const val STALE_MILLIS = 5 * 60_000L

    // A departure still shows this long after its time (it may be late without live data).
    private const val LEFT_GRACE_MILLIS = 60_000L
}
