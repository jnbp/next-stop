package io.github.jnbp.nextstop.ui

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import io.github.jnbp.nextstop.MainActivity
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.AppPrefs
import io.github.jnbp.nextstop.data.DEFAULT_PLACE_RADIUS
import io.github.jnbp.nextstop.data.PLACE_RADII
import io.github.jnbp.nextstop.data.Disruption
import io.github.jnbp.nextstop.data.NoticeLine
import io.github.jnbp.nextstop.data.NoticeLink
import io.github.jnbp.nextstop.data.NoticeLinkKind
import io.github.jnbp.nextstop.data.RouteStatus
import io.github.jnbp.nextstop.data.RoutingSource
import io.github.jnbp.nextstop.data.SavedRoute
import io.github.jnbp.nextstop.data.Stop
import io.github.jnbp.nextstop.data.mergeDisruptions
import io.github.jnbp.nextstop.data.TransitousSource
import io.github.jnbp.nextstop.data.newDisruptions
import io.github.jnbp.nextstop.data.placeDisruptions
import io.github.jnbp.nextstop.data.ownServerUrl
import io.github.jnbp.nextstop.data.routeStatus
import io.github.jnbp.nextstop.data.shortStopName
import io.github.jnbp.nextstop.ui.theme.storedPalette
import org.json.JSONObject

/** The last known state of a saved route, as the favourites show it under the route. */
data class StoredRouteStatus(
    val count: Int,
    val cancelled: Boolean,
    val firstSummary: String?,
    val lines: List<String>,
    val checkedMillis: Long,
    /** Each notice with its lines; empty for a status stored by an older version. */
    val items: List<StoredDisruption> = emptyList(),
    /** Where the notices around the route were looked for (see RouteStatus.spots). */
    val spots: List<Stop> = emptyList(),
    /** The radius they were looked for in; 0 for a status stored before there was one. */
    val radiusMeters: Int = 0,
) {
    val clear: Boolean get() = count == 0 && !cancelled
}

/**
 * One stored notice of a route: a short text, the lines it was found on and their kind of
 * transport. A version that knows [Disruption.about] keeps the whole notice too: its
 * headline, text, the runs it was found on (each line with its own kind), when it holds, its
 * links and stops; entries of older versions have only the first four.
 */
data class StoredDisruption(
    val id: String,
    val text: String,
    val lines: List<String>,
    val product: String,
    val summary: String = "",
    val body: String = "",
    val carriers: List<NoticeLine> = emptyList(),
    val validFrom: String? = null,
    val validUntil: String? = null,
    val links: List<NoticeLink> = emptyList(),
    val stops: List<Stop> = emptyList(),
    val providerIds: Set<String> = emptySet(),
    /** A notice around the route's spots rather than on its rides (see RouteStatus.around). */
    val around: Boolean = false,
) {
    /**
     * The notice again, for the cards and the sheet of "My routes": its badges and colours come
     * out of [Disruption.about] and [Disruption.carriers], as in the list of all disruptions.
     * An entry of an older version has only a short text and one kind for all its lines: its
     * text stands as the headline (the lines it names are read from there), its lines are the
     * runs.
     */
    fun toDisruption(): Disruption {
        val whole = summary.isNotEmpty() || body.isNotEmpty()
        val runs = carriers.ifEmpty { lines.map { NoticeLine(it, product) } }
        return Disruption(
            id = id,
            summary = if (whole) summary else text,
            text = if (whole) body else "",
            lines = lines.toSortedSet(),
            products = (runs.map { it.product } + product).filter { it.isNotEmpty() }.toSet(),
            validFrom = validFrom?.let(::parseTime),
            validUntil = validUntil?.let(::parseTime),
            links = links,
            stops = stops,
            providerIds = providerIds,
            carriers = runs,
        )
    }
}

private fun parseTime(text: String): OffsetDateTime? =
    try {
        OffsetDateTime.parse(text)
    } catch (e: Exception) {
        null
    }

/** The notices on the rides of a stored status, each once (not those around its spots). */
fun StoredRouteStatus.rideDisruptions(): List<Disruption> = mergeDisruptions(listOf(items.filter { !it.around }.map { it.toDisruption() }))

/** The notices around the spots of a stored status (see RouteStatus.around), each once. */
fun StoredRouteStatus.aroundDisruptions(): List<Disruption> = mergeDisruptions(listOf(items.filter { it.around }.map { it.toDisruption() }))

/** The notices of a stored status as notices again, each once; a status of a version before the items only knows its first. */
fun StoredRouteStatus.disruptions(): List<Disruption> = when {
    items.isNotEmpty() -> mergeDisruptions(listOf(items.map { it.toDisruption() }))
    count > 0 && !firstSummary.isNullOrEmpty() -> listOf(
        StoredDisruption(id = "first:" + firstSummary, text = firstSummary.orEmpty(), lines = lines, product = "").toDisruption(),
    )
    else -> emptyList()
}

/**
 * The disruption watch ("Störungswächter"): it looks at the saved routes with the bell on
 * (the notices on their next journeys) and, where the user switches it on, at the favourite
 * and saved places (the notices on the lines of the stops in their reach, as their cards on
 * "My routes" show them). A WorkManager job does so every few minutes as the user picks
 * (15, 30 or 60, or not at all), and every day at the times the user picks (the disruption
 * checks, any number of them; none is off). Nothing runs while nothing is watched (see
 * [watchSchedule]). A notice is reported once per route or place: what was reported is kept
 * for [SEEN_DAYS] days. Notifications go to the channel "Störungen". Everything is kept in
 * its own small preferences file.
 */
object DisruptionWatch {
    private fun store(context: Context) = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)

    /** The ids of the saved routes whose disruptions are reported. */
    fun watched(context: Context): Set<String> = store(context).getStringSet(KEY_WATCHED, null).orEmpty()

    fun setWatched(context: Context, routeId: String, on: Boolean) {
        val now = watched(context).let { if (on) it + routeId else it - routeId }
        store(context).edit().putStringSet(KEY_WATCHED, now).apply()
        schedule(context)
    }

    /**
     * The times of the daily checks in minutes after midnight, sorted; empty when there is none.
     * Before they were stored, the single time of the former morning check stands in.
     */
    fun checkTimes(context: Context): List<Int> {
        val store = store(context)
        val text = store.getString(KEY_CHECK_TIMES, null)
            ?: return listOfNotNull(store.getInt(KEY_MORNING, -1).takeIf { it >= 0 })
        return parseCheckTimes(text)
    }

    /** Whether the times were ever chosen (or the former morning check was set or switched off). */
    fun checkTimesChosen(context: Context): Boolean =
        store(context).let { it.contains(KEY_CHECK_TIMES) || it.contains(KEY_MORNING) }

    fun setCheckTimes(context: Context, times: List<Int>) {
        store(context).edit()
            .putString(KEY_CHECK_TIMES, times.filter { it in 0 until DAY_MINUTES }.distinct().sorted().joinToString(","))
            .remove(KEY_MORNING)
            .apply()
        schedule(context)
    }

    /** Whether the favourite and saved places are watched too (the notices in their reach). Off until switched on. */
    fun checkPlaces(context: Context): Boolean = store(context).getBoolean(KEY_PLACES, false)

    fun setCheckPlaces(context: Context, on: Boolean) {
        store(context).edit().putBoolean(KEY_PLACES, on).apply()
        schedule(context)
    }

    /** How often the watched routes and places are checked in between, in minutes; 0 is not at all (see [watchPeriod]). */
    fun periodMinutes(context: Context): Int = watchPeriod(store(context).getInt(KEY_PERIOD, DEFAULT_PERIOD_MINUTES))

    fun setPeriodMinutes(context: Context, minutes: Int) {
        store(context).edit().putInt(KEY_PERIOD, watchPeriod(minutes)).apply()
        schedule(context)
    }

    /** Whether the bell was explained once (the first time a bell is switched on). */
    fun bellExplained(context: Context): Boolean = store(context).getBoolean(KEY_BELL_EXPLAINED, false)

    fun setBellExplained(context: Context) {
        store(context).edit().putBoolean(KEY_BELL_EXPLAINED, true).apply()
    }

    /** The saved routes that still exist and have the bell on. */
    fun watchedRoutes(context: Context): List<SavedRoute> {
        val watched = watched(context)
        return AppPrefs(context).loadRoutes().filter { it.id in watched }
    }

    /**
     * The places the watch looks at when places are on: the favourite places that are set and
     * the saved places, each with its name, each once (by [placeDisruptionKey]).
     */
    fun watchablePlaces(context: Context): List<Pair<String, Stop>> {
        val prefs = AppPrefs(context)
        val favorites = prefs.loadFavoritePlaces().mapNotNull { favorite ->
            val place = favorite.place ?: return@mapNotNull null
            favorite.name.ifBlank { place.name.shortStopName() } to place
        }
        val saved = prefs.loadSavedPlaces().map { savedPlaceName(it) to it.place }
        return (favorites + saved).filter { placeDisruptionKey(it.second) != null }
            .distinctBy { placeDisruptionKey(it.second) }
            .take(MAX_WATCHED_PLACES)
    }

    /** What the watch runs now (see [watchSchedule]). */
    fun plan(context: Context): WatchSchedule = watchSchedule(
        watchedRoutes = watchedRoutes(context).size,
        checkPlaces = checkPlaces(context),
        places = watchablePlaces(context).size,
        periodMinutes = periodMinutes(context),
        times = checkTimes(context),
    )

    /** The lines of the saved routes, as last found: they come first in the overview. */
    fun myLines(context: Context): Set<String> = store(context).getStringSet(KEY_MY_LINES, null).orEmpty()

    fun statusOf(context: Context, routeId: String): StoredRouteStatus? {
        val text = store(context).getString(KEY_STATUS + routeId, null) ?: return null
        return try {
            val json = JSONObject(text)
            val lines = json.optJSONArray("lines")
            StoredRouteStatus(
                count = json.optInt("count"),
                cancelled = json.optBoolean("cancelled"),
                firstSummary = json.optString("first").takeIf { it.isNotEmpty() },
                lines = if (lines == null) emptyList() else (0 until lines.length()).map { lines.getString(it) },
                checkedMillis = json.optLong("checked"),
                items = json.optJSONArray("items")?.let { array ->
                    (0 until array.length()).mapNotNull { at ->
                        val item = array.optJSONObject(at) ?: return@mapNotNull null
                        val itemLines = item.optJSONArray("lines")
                        StoredDisruption(
                            id = item.optString("id"),
                            text = item.optString("text"),
                            lines = if (itemLines == null) emptyList() else (0 until itemLines.length()).map { itemLines.getString(it) },
                            product = item.optString("product"),
                            summary = item.optString("summary"),
                            body = item.optString("body"),
                            carriers = objects(item.optJSONArray("carriers")).map {
                                NoticeLine(it.optString("line"), it.optString("product"))
                            }.filter { it.line.isNotEmpty() },
                            validFrom = item.optString("from").takeIf { it.isNotEmpty() },
                            validUntil = item.optString("until").takeIf { it.isNotEmpty() },
                            links = objects(item.optJSONArray("links")).mapNotNull { link ->
                                val kind = NoticeLinkKind.entries.firstOrNull { it.name == link.optString("kind") }
                                    ?: return@mapNotNull null
                                NoticeLink(
                                    kind,
                                    link.optString("url").takeIf { it.isNotEmpty() },
                                    link.optString("label").takeIf { it.isNotEmpty() },
                                )
                            },
                            stops = storedStops(item.optJSONArray("stops")),
                            providerIds = item.optJSONArray("pids").let { ids ->
                                if (ids == null) emptySet() else (0 until ids.length()).map { ids.optString(it) }.filter { it.isNotEmpty() }.toSet()
                            },
                            around = item.optBoolean("around"),
                        )
                    }
                }.orEmpty(),
                spots = storedStops(json.optJSONArray("spots")),
                radiusMeters = json.optInt("radius"),
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun storedStops(array: org.json.JSONArray?): List<Stop> = objects(array).mapNotNull { stop ->
        val stopId = stop.optString("id")
        if (stopId.isEmpty()) return@mapNotNull null
        Stop(
            id = stopId,
            name = stop.optString("name"),
            latitude = if (stop.has("lat")) stop.optDouble("lat") else null,
            longitude = if (stop.has("lon")) stop.optDouble("lon") else null,
            distanceMeters = null,
        )
    }

    private fun stopsJson(stops: List<Stop>): org.json.JSONArray = org.json.JSONArray(
        stops.map { stop ->
            JSONObject().put("id", stop.id).put("name", stop.name).also { json ->
                stop.latitude?.let { json.put("lat", it) }
                stop.longitude?.let { json.put("lon", it) }
            }
        },
    )

    /**
     * The radius around places and routes in metres (see placeDisruptions, routeStatus): one of
     * [PLACE_RADII], [DEFAULT_PLACE_RADIUS] until chosen. The cards of "My routes", their map
     * and the watch all look this far.
     */
    fun radiusMeters(context: Context): Int =
        store(context).getInt(KEY_RADIUS, DEFAULT_PLACE_RADIUS).takeIf { it in PLACE_RADII } ?: DEFAULT_PLACE_RADIUS

    fun setRadiusMeters(context: Context, meters: Int) {
        if (meters in PLACE_RADII) store(context).edit().putInt(KEY_RADIUS, meters).apply()
    }

    /** Keeps [status] of a route, and the lines its journeys ride as "my lines". */
    fun saveStatus(context: Context, status: RouteStatus) {
        val all = status.disruptions.map { it to false } + status.around.map { it to true }
        val first = all.firstOrNull()?.first?.let { it.summary.ifEmpty { it.text } }
        val json = JSONObject()
            .put("count", all.size)
            .put("cancelled", status.cancelled)
            .put("first", first ?: "")
            .put("lines", org.json.JSONArray(status.disruptions.flatMap { it.lines }.distinct()))
            .put("checked", System.currentTimeMillis())
            .put("spots", stopsJson(status.spots))
            .put("radius", status.radiusMeters)
            .put(
                "items",
                org.json.JSONArray(
                    all.map { (item, around) ->
                        JSONObject()
                            .put("id", item.id)
                            .put("text", item.summary.ifEmpty { item.text })
                            .put("lines", org.json.JSONArray(item.lines.toList()))
                            .put("product", item.products.firstOrNull() ?: "")
                            // The whole notice, so the cards show the lines it is about in their colours.
                            .put("summary", item.summary)
                            .put("body", item.text)
                            .put(
                                "carriers",
                                org.json.JSONArray(item.carriers.map { JSONObject().put("line", it.line).put("product", it.product) }),
                            )
                            .put("from", item.validFrom?.toString() ?: "")
                            .put("until", item.validUntil?.toString() ?: "")
                            .put(
                                "links",
                                org.json.JSONArray(
                                    item.links.map { link ->
                                        JSONObject().put("kind", link.kind.name).put("url", link.url ?: "").put("label", link.label ?: "")
                                    },
                                ),
                            )
                            .put("stops", stopsJson(item.stops.take(MAX_STORED_STOPS)))
                            .put("pids", org.json.JSONArray(item.providerIds.toList()))
                            .put("around", around)
                    },
                ),
            )
        store(context).edit()
            .putString(KEY_STATUS + status.routeId, json.toString())
            .putStringSet(KEY_MY_LINES, (myLines(context) + status.rideLines).toList().takeLast(MAX_MY_LINES).toSet())
            .apply()
    }

    /**
     * Starts, changes or stops the jobs as [plan] says: they run only while a route has the
     * bell on or places are watched. A changed interval replaces the job of the old one.
     */
    fun schedule(context: Context) {
        val work = WorkManager.getInstance(context)
        val period = plan(context).periodicMinutes
        if (period == null) {
            work.cancelUniqueWork(WORK_PERIODIC)
        } else {
            val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val periodic = PeriodicWorkRequestBuilder<DisruptionWorker>(period.toLong(), TimeUnit.MINUTES)
                .setConstraints(network)
                .build()
            work.enqueueUniquePeriodicWork(WORK_PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, periodic)
        }
        scheduleChecks(context)
    }

    /**
     * The next run of every daily check: one job per time (all tagged [TAG_CHECK], so the ones
     * of times taken away go); none when no time is set or nothing is watched. The single job
     * of the former morning check goes too.
     */
    fun scheduleChecks(context: Context) {
        val work = WorkManager.getInstance(context)
        work.cancelUniqueWork(WORK_MORNING)
        work.cancelAllWorkByTag(TAG_CHECK)
        plan(context).times.forEach { minutes -> scheduleCheck(context, minutes) }
    }

    /** The next run of the daily check at [minutes] after midnight. */
    fun scheduleCheck(context: Context, minutes: Int) {
        val request = OneTimeWorkRequestBuilder<DisruptionWorker>()
            .setInitialDelay(delayUntil(LocalDateTime.now(), minutes).toMillis(), TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf(KEY_MORNING_RUN to true, KEY_CHECK_MINUTES to minutes))
            .addTag(TAG_CHECK)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_CHECK + minutes, ExistingWorkPolicy.REPLACE, request)
    }

    /**
     * Checks the watched routes and reports what is new on them. A daily check ([scheduled])
     * also says that a route is cancelled, even when no notice explains it.
     */
    suspend fun check(context: Context, scheduled: Boolean) {
        val routes = watchedRoutes(context)
        val places = if (checkPlaces(context)) watchablePlaces(context) else emptyList()
        if (routes.isEmpty() && places.isEmpty()) return
        val prefs = AppPrefs(context)
        val source = RoutingSource(
            choice = { prefs.sourceChoice },
            userAgent = TransitousSource.userAgent(context),
            bvgUrl = { ownServerUrl(prefs.ownServer) },
        )
        val seen = seen(context).toMutableMap()
        val now = System.currentTimeMillis()
        val radius = radiusMeters(context)
        for (route in routes) {
            val status = try {
                routeStatus(source, route, prefs.searchOptionsInUse, radius)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                continue
            }
            saveStatus(context, status)
            val prefix = route.id + SEP
            val known = seen.keys.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }.toSet()
            // The notices around its spots as those around a place: the first look in a radius
            // only learns what is there already (a wider radius is no news).
            val aroundBaseline = AROUND_BASELINE + status.radiusMeters
            val freshAround = if (aroundBaseline in known) newDisruptions(status.around, known) else emptyList()
            val fresh = newDisruptions(status.disruptions, known) + freshAround
            val cancelledKey = prefix + "cancelled:" + LocalDateTime.now().toLocalDate()
            val reportCancelled = scheduled && status.cancelled && cancelledKey !in seen
            if (fresh.isNotEmpty() || reportCancelled) notify(context, route, fresh, status.cancelled)
            status.all.forEach { seen[prefix + it.id] = now }
            if (status.radiusMeters > 0) seen[prefix + aroundBaseline] = now
            if (reportCancelled) seen[cancelledKey] = now
        }
        // The places: what is new in their reach. A place looked at for the first time only
        // learns what is there already; from then on, what comes is reported.
        for ((name, place) in places) {
            val key = placeDisruptionKey(place) ?: continue
            val found = try {
                placeDisruptions(source, place, radius)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                continue
            }
            PlaceDisruptionCache.put(key, found)
            // By radius too: a place looked at in a new radius learns what is there first.
            val prefix = PLACE_PREFIX + key + "@" + radius + SEP
            val known = seen.keys.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }.toSet()
            val fresh = newDisruptions(found.disruptions, known)
            if (fresh.isNotEmpty() && PLACE_BASELINE in known) notifyPlace(context, key, name, fresh)
            found.disruptions.forEach { seen[prefix + it.id] = now }
            seen[prefix + PLACE_BASELINE] = now
        }
        // What has not been seen for some days may be reported again when it comes back.
        val keep = seen.filterValues { now - it < SEEN_DAYS * DAY_MILLIS }
        store(context).edit().putString(KEY_SEEN, JSONObject(keep).toString()).apply()
    }

    private fun objects(array: org.json.JSONArray?): List<JSONObject> =
        if (array == null) emptyList() else (0 until array.length()).mapNotNull { array.optJSONObject(it) }

    private fun seen(context: Context): Map<String, Long> {
        val text = store(context).getString(KEY_SEEN, null) ?: return emptyMap()
        return try {
            val json = JSONObject(text)
            json.keys().asSequence().associateWith { json.optLong(it) }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun notify(context: Context, route: SavedRoute, fresh: List<Disruption>, cancelled: Boolean) {
        val title = context.getString(
            R.string.disruption_notification_title,
            route.from.name.shortStopName(),
            route.to.name.shortStopName(),
        )
        val lines = noticeLines(fresh) + if (cancelled) listOf(context.getString(R.string.disruption_cancelled)) else emptyList()
        post(context, route.id, title, lines)
    }

    /** New notices in the reach of the place [name]. */
    private fun notifyPlace(context: Context, key: String, name: String, fresh: List<Disruption>) {
        post(context, PLACE_PREFIX + key, context.getString(R.string.disruption_notification_place_title, name), noticeLines(fresh))
    }

    private fun noticeLines(fresh: List<Disruption>): List<String> = fresh.map { item ->
        val on = item.badges.map { it.line }.ifEmpty { item.lines.toList() }.distinct().joinToString(", ")
        val text = item.summary.ifEmpty { item.text }
        if (on.isEmpty()) text else "$on: $text"
    }

    /** One notification of the watch, one per route or place ([tag]). */
    @SuppressLint("MissingPermission")
    private fun post(context: Context, tag: String, title: String, lines: List<String>) {
        ensureChannel(context, AppChannel.Disruptions)
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(
            context,
            NOTIFICATION_BASE,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_trip)
            .setColor(storedPalette(context).blue.toArgb())
            .setContentTitle(title)
            .setContentText(lines.firstOrNull())
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_BASE + (tag.hashCode() and 0xfff), notification)
        } catch (e: SecurityException) {
            // The permission was taken away in the meantime.
        }
    }

    // Kept as "morning" so jobs planned by an older version still count as a daily check.
    internal const val KEY_MORNING_RUN = "morning"
    internal const val KEY_CHECK_MINUTES = "checkMinutes"
    private const val STORE = "disruptions"
    private const val SEP = "\u001F"
    private const val KEY_WATCHED = "watched"
    // The single time of the former morning check; read once to carry it over.
    private const val KEY_MORNING = "morningMinutes"
    private const val KEY_CHECK_TIMES = "checkTimes"
    private const val KEY_MY_LINES = "myLines"
    private const val KEY_STATUS = "status:"
    private const val KEY_SEEN = "seen"
    private const val KEY_PLACES = "checkPlaces"
    private const val KEY_RADIUS = "placeRadius"
    // The mark of a route whose surroundings were looked at in a radius (followed by it).
    private const val AROUND_BASELINE = "*around"
    private const val KEY_PERIOD = "periodMinutes"
    private const val KEY_BELL_EXPLAINED = "bellExplained"
    // The keys of what was reported near a place, and the mark of a place looked at before.
    private const val PLACE_PREFIX = "place:"
    private const val PLACE_BASELINE = "*"
    private const val MAX_WATCHED_PLACES = 12
    private const val WORK_PERIODIC = "disruptions-periodic"
    private const val WORK_MORNING = "disruptions-morning"
    private const val WORK_CHECK = "disruptions-check-"
    private const val TAG_CHECK = "disruptions-check"
    private const val DAY_MINUTES = 24 * 60
    private val CHANNEL = AppChannel.Disruptions.id
    private const val NOTIFICATION_BASE = 3000
    private const val SEEN_DAYS = 3
    private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
    private const val MAX_MY_LINES = 40
    // The stops kept per stored notice: enough for its details and small map.
    private const val MAX_STORED_STOPS = 6
}

/**
 * What the disruption watch runs: the job every [periodicMinutes] minutes (null: none) and the
 * daily checks at [times] (minutes after midnight).
 */
data class WatchSchedule(val periodicMinutes: Int?, val times: List<Int>) {
    val runs: Boolean get() = periodicMinutes != null || times.isNotEmpty()

    companion object {
        val NONE = WatchSchedule(null, emptyList())
    }
}

/** The intervals of the watch to choose from, in minutes; 0 is none. WorkManager runs a job at most every 15 minutes. */
val WATCH_PERIODS: List<Int> = listOf(0, 15, 30, 60)

/** The interval of the watch until another is chosen: every half hour, as before it could be chosen. */
const val DEFAULT_PERIOD_MINUTES = 30

/** A stored interval as one of [WATCH_PERIODS]; anything else is the default. */
fun watchPeriod(minutes: Int): Int = if (minutes in WATCH_PERIODS) minutes else DEFAULT_PERIOD_MINUTES

/**
 * What the watch runs, from what is watched: nothing while no route has the bell on and no
 * place is watched (places on, but none set, is nothing either); else the job every
 * [periodMinutes] (none for 0) and the daily checks at [times].
 */
fun watchSchedule(watchedRoutes: Int, checkPlaces: Boolean, places: Int, periodMinutes: Int, times: List<Int>): WatchSchedule {
    val active = watchedRoutes > 0 || (checkPlaces && places > 0)
    if (!active) return WatchSchedule.NONE
    val period = watchPeriod(periodMinutes).takeIf { it > 0 }
    return WatchSchedule(period, times.filter { it in 0 until 24 * 60 }.distinct().sorted())
}

/** Check times as stored ("420,1080"): minutes after midnight, sorted, without doubles or nonsense. */
fun parseCheckTimes(text: String): List<Int> =
    text.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 0 until 24 * 60 }.distinct().sorted()

/** The time from [now] to the next [minutes] after midnight: later today, or tomorrow. */
fun delayUntil(now: LocalDateTime, minutes: Int): Duration {
    val today = now.toLocalDate().atTime(LocalTime.of(minutes / 60, minutes % 60))
    val next = if (today.isAfter(now)) today else today.plusDays(1)
    return Duration.between(now, next)
}

/** Runs the disruption watch; see [DisruptionWatch]. */
class DisruptionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val scheduled = inputData.getBoolean(DisruptionWatch.KEY_MORNING_RUN, false)
        try {
            DisruptionWatch.check(applicationContext, scheduled)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Nothing to report this time; the next run tries again.
        }
        // A daily check plans its run of the next day, if its time is still chosen; a job of
        // the former morning check (without a time) plans all of them.
        if (scheduled) {
            val minutes = inputData.getInt(DisruptionWatch.KEY_CHECK_MINUTES, -1)
            if (minutes < 0) {
                DisruptionWatch.scheduleChecks(applicationContext)
            } else if (minutes in DisruptionWatch.plan(applicationContext).times) {
                DisruptionWatch.scheduleCheck(applicationContext, minutes)
            }
        }
        return Result.success()
    }
}
