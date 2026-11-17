package io.github.jnbp.nextstop.wear.data

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

/**
 * Asks the phone to look something up with its own sources (BVG, Transitous and the rest of
 * its chain), so the watch finds journeys and departures all over Germany as the phone does.
 * One [Protocol.MSG_QUERY] goes to one phone with Next Stop; its answer comes back as a
 * message at [Protocol.MSG_ANSWER] with the same "id", caught by a listener that lives only
 * as long as the question.
 */
object PhoneQuery {
    const val KIND_JOURNEYS = "journeys"
    const val KIND_DEPARTURES = "departures"
    const val KIND_SEARCH = "search"

    private val ids = AtomicLong(System.currentTimeMillis())

    /**
     * Sends [body] as a query of [kind] and waits at most [millis] for the answer. Null when no
     * phone with Next Stop was reached or it did not answer in time; otherwise the answer,
     * whose "ok" tells whether the phone found something (false with an "error").
     */
    suspend fun ask(context: Context, kind: String, body: JSONObject, millis: Long): JSONObject? {
        val app = context.applicationContext
        val id = ids.incrementAndGet().toString()
        val answer = CompletableDeferred<JSONObject>()
        val listener = MessageClient.OnMessageReceivedListener { event ->
            if (event.path == Protocol.MSG_ANSWER) {
                val json = try {
                    JSONObject(String(event.data, Charsets.UTF_8))
                } catch (e: Exception) {
                    null
                }
                if (json != null && json.optString("id") == id) answer.complete(json)
            }
        }
        val client = Wearable.getMessageClient(app)
        try {
            client.addListener(listener).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // No Google Play services on this watch: nothing to ask.
            return null
        }
        val started = System.currentTimeMillis()
        try {
            body.put("id", id).put("kind", kind)
            val sent = WatchStore.sendToPhone(app, Protocol.MSG_QUERY, body.toString().toByteArray(Charsets.UTF_8), single = true)
            // Sent to a phone without Next Stop (or none at all): no answer will come.
            if (!sent || WatchStore.phone.value != PhoneLink.Connected) {
                Log.d(LOG_TAG, "phone query $kind: no phone with Next Stop")
                return null
            }
            val result = withTimeoutOrNull(millis) { answer.await() }
            Log.d(
                LOG_TAG,
                "phone query $kind: ${if (result == null) "no answer" else "ok=${result.optBoolean("ok")}"} " +
                    "in ${System.currentTimeMillis() - started} ms",
            )
            return result
        } finally {
            withContext(NonCancellable) {
                try {
                    client.removeListener(listener).await()
                } catch (e: Exception) {
                    // Gone with the services: nothing to remove.
                }
            }
        }
    }
}

/** The journeys of an answer of the phone ([Protocol.MSG_ANSWER], "journeys"); broken entries are left out. */
fun parseJourneysJson(list: JSONArray?): List<Journey> {
    if (list == null) return emptyList()
    return (0 until list.length()).mapNotNull { index -> list.optJSONObject(index)?.let { parseJourneyJson(it) } }
}

/**
 * One journey as the phone sends it: "token" (its refresh token, with the provider's prefix
 * of the phone, or missing) and "legs", each with "line", "product", "walk", "from", "to",
 * "dep" and "arr" (epoch ms), "depDelay" and "arrDelay" (minutes), "platform", "arrPlatform",
 * "dir", "cancelled", "stops" (arrival times in epoch ms after boarding) and the positions
 * "fromLat", "fromLon", "toLat", "toLon". Null without a leg that can be read.
 */
fun parseJourneyJson(item: JSONObject): Journey? {
    val list = item.optJSONArray("legs") ?: return null
    val legs = (0 until list.length()).map { index ->
        val leg = list.optJSONObject(index) ?: return null
        parseLegJson(leg) ?: return null
    }
    if (legs.isEmpty()) return null
    return Journey(legs, item.textOrNull("token"))
}

private fun parseLegJson(item: JSONObject): Leg? {
    val departure = item.optLong("dep", 0)
    val arrival = item.optLong("arr", 0)
    if (departure <= 0 || arrival <= 0) return null
    val product = item.optString("product", "")
    val stops = item.optJSONArray("stops")
    return Leg(
        line = rapidLineName(item.textOrNull("line"), product),
        product = product,
        walking = item.optBoolean("walk", false),
        from = item.optString("from", ""),
        to = item.optString("to", ""),
        departure = departure,
        arrival = arrival,
        departureDelayMinutes = item.optInt("depDelay", 0),
        arrivalDelayMinutes = item.optInt("arrDelay", 0),
        platform = item.textOrNull("platform"),
        direction = item.textOrNull("dir"),
        cancelled = item.optBoolean("cancelled", false),
        stopTimes = if (stops == null) emptyList() else (0 until stops.length()).map { stops.optLong(it) }.filter { it > 0 },
        arrivalPlatform = item.textOrNull("arrPlatform"),
        fromPosition = item.position("fromLat", "fromLon"),
        toPosition = item.position("toLat", "toLon"),
        inStation = item.optBoolean("inStation", false),
    )
}

/**
 * The departures of an answer of the phone ("departures"): "line", "product", "dir", "time"
 * and "planned" (epoch ms), "delay" (minutes), "platform", "cancelled", "additional", "replaces".
 */
fun parseDeparturesJson(list: JSONArray?): List<Departure> {
    if (list == null) return emptyList()
    return (0 until list.length()).mapNotNull { index ->
        val item = list.optJSONObject(index) ?: return@mapNotNull null
        val product = item.optString("product", "")
        Departure(
            line = rapidLineName(item.textOrNull("line"), product) ?: "?",
            product = product,
            direction = item.optString("dir", ""),
            time = item.millisOrNull("time"),
            delayMinutes = if (item.has("delay") && !item.isNull("delay")) item.optInt("delay") else null,
            platform = item.textOrNull("platform"),
            cancelled = item.optBoolean("cancelled", false),
            plannedTime = item.millisOrNull("planned"),
            additional = item.optBoolean("additional", false),
            replaces = item.textOrNull("replaces"),
        )
    }
}

/** The places of an answer of the phone ("places"), in the form of [placeJson]. */
fun parsePlacesJson(list: JSONArray?): List<Place> {
    if (list == null) return emptyList()
    return (0 until list.length()).mapNotNull { index -> list.optJSONObject(index)?.let { parsePlaceJson(it) } }
}

private fun JSONObject.textOrNull(key: String): String? = if (!has(key) || isNull(key)) null else optString(key).ifEmpty { null }

private fun JSONObject.millisOrNull(key: String): Long? = if (!has(key) || isNull(key)) null else optLong(key).takeIf { it > 0 }

private fun JSONObject.position(latitudeKey: String, longitudeKey: String): Pair<Double, Double>? {
    if (!has(latitudeKey) || !has(longitudeKey)) return null
    val latitude = optDouble(latitudeKey)
    val longitude = optDouble(longitudeKey)
    return if (latitude.isNaN() || longitude.isNaN()) null else latitude to longitude
}
