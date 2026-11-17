package io.github.jnbp.nextstop.data

import android.os.SystemClock
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/** How a walk is drawn (see [FootPaths.status]). */
enum class FootWay {
    /** Along a real way on foot: from the provider, or asked for at the foot router. */
    Real,

    /** Straight for now: its real way is asked for. */
    Pending,

    /** Straight, and not on purpose: the foot router had no way for it (or it is too long to ask). */
    Straight,

    /** Too short to need a way of its own: straight is right. */
    Short,
}

/**
 * Real ways on foot for walks that the provider only knows as a straight line (two points):
 * asked for at the public OSRM foot server of FOSSGIS, and only for walks on screen. Fair use:
 * one request at a time and at most one per second, every way cached by its rounded ends for
 * as long as the process lives. A failed request is tried again after a short pause (see
 * [retryDelayMillis]); a way that still failed is not asked for again for a while, and is
 * reported as [FootWay.Straight] meanwhile, so the screens can say so instead of pretending.
 * The times of a walk stay those of the provider; only the line on the map changes.
 *
 * The requests run in a scope of their own, not in that of the screen that wanted them: a
 * page that goes away (the plan becoming the journey detail, say) does not throw away a way
 * that is half loaded, and a way wanted [urgent]ly (the walk on the big map or a small map
 * in view) is asked for before the rest of the queue.
 */
object FootPaths {
    private const val BASE_URL = "https://routing.openstreetmap.de/routed-foot/route/v1/foot/"
    // The bicycle router of the same servers, for the ways by bike of the bike combo (Leg.cycling).
    private const val BIKE_URL = "https://routing.openstreetmap.de/routed-bike/route/v1/bike/"
    private const val USER_AGENT = "NextStop/0.1"
    private const val MIN_PAUSE_MILLIS = 1_000L
    private const val TIMEOUT_MILLIS = 8_000
    private const val MAX_CACHED = 256
    private const val RETRY_MILLIS = 2 * 60_000L

    // A walk shorter than this stays straight; one longer than this is not asked for.
    private const val MIN_METERS = 40.0
    private const val MAX_METERS = 5_000.0
    // A way by bike may be longer: half an hour at a relaxed pace.
    private const val MAX_BIKE_METERS = 12_000.0

    private val cache = object : LinkedHashMap<String, List<Pair<Double, Double>>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Pair<Double, Double>>>?): Boolean =
            size > MAX_CACHED
    }

    // Guarded by [cache]: the ways that gave up (when), those waiting or being asked for.
    private val failedAt = HashMap<String, Long>()
    private val queued = HashSet<String>()
    private val queue = ArrayDeque<Way>()
    private var worker: Job? = null
    private var lastRequest = 0L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val changes = MutableStateFlow(0)

    // The walk re-routed from where the traveller left its way (see [setDetour]), by [detourKey], and its new way.
    @Volatile private var detour: Pair<String, List<Pair<Double, Double>>>? = null

    /** Counts up whenever a new way came in or one was given up, so maps can draw their walks again. */
    val version: StateFlow<Int> get() = changes

    /**
     * The way of [leg] to draw: a known real way for a walk with only its two ends, from its
     * start to its end; the leg's own way otherwise.
     */
    fun pathFor(leg: Leg): List<Pair<Double, Double>> {
        detourOf(leg)?.let { return it }
        if (!leg.walking || leg.path.size != 2) return leg.path
        val start = leg.path.first()
        val end = leg.path.last()
        val found = synchronized(cache) { cache[key(start, end, leg.cycling)] } ?: return leg.path
        return listOf(start) + found + listOf(end)
    }

    /**
     * The way on foot from [start] to [end] to draw: a known real way between them, else the
     * straight line. For changes between two stop positions of buses and trams, which have
     * no walk leg of their own (see [changeLinks]).
     */
    fun wayBetween(start: Pair<Double, Double>, end: Pair<Double, Double>): List<Pair<Double, Double>> {
        val found = synchronized(cache) { cache[key(start, end)] } ?: return listOf(start, end)
        return listOf(start) + found + listOf(end)
    }

    /**
     * The length in metres of the real way on foot from [start] to [end] where one is already
     * known, else null. Asks for nothing: for estimates that may only use what is at hand.
     */
    fun knownWayMeters(start: Pair<Double, Double>, end: Pair<Double, Double>): Double? {
        val found = synchronized(cache) { cache[key(start, end)] } ?: return null
        return routeMeters(listOf(start) + found + listOf(end))
    }

    /** How the walk [leg] is drawn now (see [FootWay]). */
    fun status(leg: Leg): FootWay = when {
        detourOf(leg) != null -> FootWay.Real
        !leg.walking || leg.path.size > 2 -> FootWay.Real
        leg.path.size < 2 -> FootWay.Short
        else -> statusBetween(leg.path.first(), leg.path.last(), leg.cycling)
    }

    /** How the way on foot (or by [bike]) from [start] to [end] is drawn now (see [FootWay]). */
    fun statusBetween(start: Pair<Double, Double>, end: Pair<Double, Double>, bike: Boolean = false): FootWay {
        val meters = distance(start, end)
        if (meters < MIN_METERS) return FootWay.Short
        if (meters > maxMeters(bike)) return FootWay.Straight
        val key = key(start, end, bike)
        return synchronized(cache) {
            when {
                cache.containsKey(key) -> FootWay.Real
                queued.contains(key) -> FootWay.Pending
                failedAt.containsKey(key) -> FootWay.Straight
                // Not asked for yet: about to be, by the screen that shows it.
                else -> FootWay.Pending
            }
        }
    }

    /**
     * Draws the walk [leg] along [path] from now on, instead of its own way: the new way
     * from where the traveller left it to where the walk leads. One walk at a time; maps
     * draw it at once (see [version]).
     */
    fun setDetour(leg: Leg, path: List<Pair<Double, Double>>) {
        val legKey = detourKey(leg) ?: return
        if (path.size < 2) return
        detour = legKey to path
        changes.value = changes.value + 1
    }

    /** Ends the new way of [setDetour]: the walk is drawn along its own way again. */
    fun clearDetour() {
        if (detour == null) return
        detour = null
        changes.value = changes.value + 1
    }

    /** True while [leg] is drawn along a new way (see [setDetour]). */
    fun hasDetour(leg: Leg): Boolean = detourOf(leg) != null

    private fun detourOf(leg: Leg): List<Pair<Double, Double>>? {
        val current = detour ?: return null
        return if (current.first == detourKey(leg)) current.second else null
    }

    // A walk by its places and the ends of its own way, which stay when live times move.
    private fun detourKey(leg: Leg): String? {
        if (!leg.walking || leg.path.isEmpty()) return null
        return leg.from + ">" + leg.to + "|" + key(leg.path.first(), leg.path.last())
    }

    /**
     * Asks for the real ways of the straight walks of [journeys], and of the changes between
     * two bus or tram stop positions without a walk (see [changeLinks]); known ones are not
     * asked again. Returns at once: the ways come in one after another (see [version]).
     */
    fun want(journeys: List<Journey>, urgent: Boolean = false) {
        val walks = journeys.flatMap { it.legs }
            .filter { it.walking && it.path.size == 2 }
            .map { Way(it.path.first(), it.path.last(), it.cycling) }
        wantWays(walks + journeys.flatMap { changeLinks(it) }.map { Way(it.first, it.second) }, urgent)
    }

    /** Asks for the real way of the walk (or ride by bike) [leg] before all others (see [want]). */
    fun wantWalk(leg: Leg) {
        if (leg.walking && leg.path.size == 2) wantWays(listOf(Way(leg.path.first(), leg.path.last(), leg.cycling)), urgent = true)
    }

    /** Asks for the real way from [start] to [end] before all others (see [want]). */
    fun wantWay(start: Pair<Double, Double>, end: Pair<Double, Double>) {
        wantWays(listOf(Way(start, end)), urgent = true)
    }

    /**
     * The changes of [journey] from one bus or tram stop position to another with no walk leg
     * between the two rides, as the end of the way of the arriving ride (where it is left)
     * and the start of the way of the next one (where it is boarded).
     */
    fun changeLinks(journey: Journey): List<Pair<Pair<Double, Double>, Pair<Double, Double>>> =
        journey.legs.zipWithNext().mapNotNull { (arriving, next) ->
            if (arriving.walking || next.walking || arriving.alightingSpot == null || next.boardingSpot == null) return@mapNotNull null
            val start = arriving.path.lastOrNull() ?: return@mapNotNull null
            val end = next.path.firstOrNull() ?: return@mapNotNull null
            if (start == end) null else start to end
        }

    private fun wantWays(ways: List<Way>, urgent: Boolean) {
        val wanted = ways
            .filter { way -> distance(way.start, way.end) in MIN_METERS..maxMeters(way.bike) }
            .distinctBy { way -> way.key }
        if (wanted.isEmpty()) return
        synchronized(cache) {
            val now = SystemClock.elapsedRealtime()
            var added = false
            for (way in (if (urgent) wanted.asReversed() else wanted)) {
                val wayKey = way.key
                if (cache.containsKey(wayKey)) continue
                val failed = failedAt[wayKey]
                if (failed != null && now - failed < RETRY_MILLIS) continue
                if (queued.contains(wayKey)) {
                    // Wanted now: to the front of the queue.
                    if (urgent && queue.removeAll { it.key == wayKey }) queue.addFirst(way)
                    continue
                }
                queued.add(wayKey)
                if (urgent) queue.addFirst(way) else queue.addLast(way)
                added = true
            }
            // The worker needs the lock to start, so it is [worker] before it can end.
            if (added && worker == null) worker = scope.launch { drain() }
        }
    }

    // Asks for the ways of the queue one after another, until it is empty; then the next want starts another.
    private suspend fun drain() {
        while (true) {
            val way = synchronized(cache) { queue.removeFirstOrNull().also { if (it == null) worker = null } } ?: break
            val key = way.key
            val path = fetch(way)
            synchronized(cache) {
                queued.remove(key)
                if (path != null) {
                    cache[key] = path
                    failedAt.remove(key)
                } else {
                    failedAt[key] = SystemClock.elapsedRealtime()
                }
            }
            changes.value = changes.value + 1
        }
    }

    // The way from [start] to [end], tried again after a pause while the server gives no answer; null without one.
    private suspend fun fetch(way: Way): List<Pair<Double, Double>>? {
        var attempt = 0
        while (true) {
            val wait = lastRequest + MIN_PAUSE_MILLIS - SystemClock.elapsedRealtime()
            if (wait > 0) delay(wait)
            lastRequest = SystemClock.elapsedRealtime()
            val answer = try {
                request(way)
            } catch (e: IOException) {
                DiagLog.w(LOG_TAG) { "foot way: ${e.javaClass.simpleName}" }
                FootAnswer.Failed
            } catch (e: org.json.JSONException) {
                FootAnswer.NoWay
            } catch (e: RuntimeException) {
                // Never let one odd answer stop the worker (and with it every later way).
                DiagLog.w(LOG_TAG, e) { "foot way" }
                FootAnswer.NoWay
            }
            when (answer) {
                is FootAnswer.Way -> return answer.path
                FootAnswer.NoWay -> return null
                FootAnswer.Failed -> {
                    val pause = retryDelayMillis(attempt) ?: return null
                    attempt++
                    delay(pause)
                }
            }
        }
    }

    private fun request(way: Way): FootAnswer {
        val url = (if (way.bike) BIKE_URL else BASE_URL) + String.format(
            Locale.ROOT,
            "%.6f,%.6f;%.6f,%.6f?overview=full&geometries=polyline6",
            way.start.second,
            way.start.first,
            way.end.second,
            way.end.first,
        )
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.setRequestProperty("User-Agent", USER_AGENT)
            val code = connection.responseCode
            // Busy or broken for a moment: worth another try. Any other refusal stays one.
            if (code == 429 || code >= 500) {
                DiagLog.w(LOG_TAG) { "foot way: HTTP $code" }
                return FootAnswer.Failed
            }
            if (code != HttpURLConnection.HTTP_OK) return FootAnswer.NoWay
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            val path = parseFootRoute(text)
            return if (path == null || path.size < 2) FootAnswer.NoWay else FootAnswer.Way(path)
        } finally {
            connection.disconnect()
        }
    }

    // The ends rounded to 4 decimals (about 10 m): the same walk of another journey finds the same
    // way. A way by bike is another way than the one on foot between the same ends.
    private fun key(start: Pair<Double, Double>, end: Pair<Double, Double>, bike: Boolean = false): String =
        (if (bike) "bike;" else "") +
            String.format(Locale.ROOT, "%.4f,%.4f;%.4f,%.4f", start.first, start.second, end.first, end.second)

    private fun maxMeters(bike: Boolean): Double = if (bike) MAX_BIKE_METERS else MAX_METERS

    /** A way to ask for: on foot, or by [bike]. */
    private data class Way(val start: Pair<Double, Double>, val end: Pair<Double, Double>, val bike: Boolean = false) {
        val key: String get() = FootPaths.key(start, end, bike)
    }

    private fun distance(from: Pair<Double, Double>, to: Pair<Double, Double>): Double {
        val north = (to.first - from.first) * 111_320.0
        val east = (to.second - from.second) * 111_320.0 * cos(Math.toRadians((from.first + to.first) / 2))
        return sqrt(north * north + east * east)
    }

    private const val LOG_TAG = "FootPaths"
}

/** What the foot router said: a way, no way at all, or nothing (no network, busy, timed out). */
private sealed interface FootAnswer {
    class Way(val path: List<Pair<Double, Double>>) : FootAnswer
    data object NoWay : FootAnswer
    data object Failed : FootAnswer
}

/** The retries of a way on foot the server gave no answer for: after 2, then 5 seconds. */
private val RETRY_PAUSES_MILLIS = longArrayOf(2_000L, 5_000L)

/** The pause before another try after [attempt] failed tries (0 the first); null: give up. */
internal fun retryDelayMillis(attempt: Int): Long? = RETRY_PAUSES_MILLIS.getOrNull(attempt)

/** The way of the first route of an OSRM answer (geometries=polyline6) as latitude and longitude; null without one. */
internal fun parseFootRoute(text: String): List<Pair<Double, Double>>? {
    val root = JSONObject(text)
    if (root.optString("code") != "Ok") return null
    val geometry = root.optJSONArray("routes")?.optJSONObject(0)?.optString("geometry").orEmpty()
    if (geometry.isEmpty()) return null
    return decodePolyline(geometry, 6).takeIf { it.size >= 2 }
}
