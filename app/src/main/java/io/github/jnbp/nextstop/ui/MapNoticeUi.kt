package io.github.jnbp.nextstop.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.AlertHarvest
import io.github.jnbp.nextstop.data.AppPrefs
import io.github.jnbp.nextstop.data.Disruption
import io.github.jnbp.nextstop.data.HIDDEN_NOTICE_KEEP_MILLIS
import io.github.jnbp.nextstop.data.HiddenNotice
import io.github.jnbp.nextstop.data.NOTICE_AREA_METERS
import io.github.jnbp.nextstop.data.NamedStops
import io.github.jnbp.nextstop.data.NoticeMarker
import io.github.jnbp.nextstop.data.OsmStations
import io.github.jnbp.nextstop.data.PlaceIcon
import io.github.jnbp.nextstop.data.RoutingSource
import io.github.jnbp.nextstop.data.Stop
import io.github.jnbp.nextstop.data.TransitSource
import io.github.jnbp.nextstop.data.bigStopsIn
import io.github.jnbp.nextstop.data.departureNotices
import io.github.jnbp.nextstop.data.mergeDisruptions
import io.github.jnbp.nextstop.data.noticeAreaKey
import io.github.jnbp.nextstop.data.prunedHidden
import io.github.jnbp.nextstop.data.shortStopName
import io.github.jnbp.nextstop.ui.theme.AppColors
import kotlin.math.cos
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject

/** The setting "Störungen auf der Karte" while the app runs; stored in [AppPrefs.mapNotices], on until switched off. */
object MapNoticeSetting {
    var shown by mutableStateOf(true)
        private set
    private var loaded = false

    /** Reads the stored setting once per process. */
    fun load(context: Context) {
        if (loaded) return
        loaded = true
        shown = AppPrefs(context).mapNotices
    }

    fun update(context: Context, value: Boolean) {
        shown = value
        AppPrefs(context).mapNotices = value
    }
}

/**
 * The notices hidden from the maps by the user, by their id (see [Disruption.id]), kept in a
 * small preferences file of their own. Hiding is about the maps only: "My routes" counts them
 * as before. One not seen among the known notices for [HIDDEN_NOTICE_KEEP_MILLIS] is dropped
 * (see [prune]), as the notice is over.
 */
object HiddenNotices {
    var list by mutableStateOf<List<HiddenNotice>>(emptyList())
        private set
    private var loaded = false

    val ids: Set<String> get() = list.mapTo(HashSet()) { it.id }

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val text = store(context).getString(KEY_HIDDEN, null) ?: return
        list = try {
            val array = JSONArray(text)
            (0 until array.length()).mapNotNull { at ->
                val item = array.optJSONObject(at) ?: return@mapNotNull null
                val id = item.optString("id")
                if (id.isEmpty()) {
                    null
                } else {
                    HiddenNotice(
                        id = id,
                        title = item.optString("title"),
                        line = item.optString("line").takeIf { it.isNotEmpty() },
                        product = item.optString("product").takeIf { it.isNotEmpty() },
                        lastSeen = item.optLong("seen"),
                    )
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun hide(context: Context, item: Disruption) {
        val badge = item.badges.firstOrNull()
        val hidden = HiddenNotice(
            id = item.id,
            title = item.summary.ifEmpty { item.text },
            line = badge?.line,
            product = badge?.product,
            lastSeen = System.currentTimeMillis(),
        )
        save(context, list.filter { it.id != item.id } + hidden)
    }

    fun show(context: Context, id: String) = save(context, list.filter { it.id != id })

    fun showAll(context: Context) = save(context, emptyList())

    /** Marks those among [seen] as seen now and drops those not seen for long (see prunedHidden). */
    fun prune(context: Context, seen: Set<String>) {
        if (list.isEmpty()) return
        val now = System.currentTimeMillis()
        val pruned = prunedHidden(list, seen, now, HIDDEN_NOTICE_KEEP_MILLIS)
        // Stored again only when something changed more than the time it was seen.
        if (pruned.map { it.id } != list.map { it.id } || pruned.any { fresh -> list.any { it.id == fresh.id && now - it.lastSeen > SEEN_WRITE_MILLIS && fresh.lastSeen == now } }) {
            save(context, pruned)
        }
    }

    private fun save(context: Context, items: List<HiddenNotice>) {
        list = items
        val array = JSONArray(
            items.map { item ->
                JSONObject()
                    .put("id", item.id)
                    .put("title", item.title)
                    .put("line", item.line ?: "")
                    .put("product", item.product ?: "")
                    .put("seen", item.lastSeen)
            },
        )
        store(context).edit().putString(KEY_HIDDEN, array.toString()).apply()
    }

    private fun store(context: Context) = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)

    private const val STORE = "map-notices"
    private const val KEY_HIDDEN = "hidden"

    // The time a hidden notice was seen is written again at most this often.
    private const val SEEN_WRITE_MILLIS = 6 * 60 * 60 * 1000L
}

/**
 * The notices the maps can show, from what the app knows anyway: the overview of all current
 * disruptions, the departures it loaded, and those it asked around the map (see
 * [refreshArea]); together with the statuses of the saved routes and the notices around the
 * places of "My routes" (see [known]). Kept while the app runs; [version] changes with each
 * new list.
 */
object MapNoticePool {
    private class Entry(val items: List<Disruption>, val millis: Long)

    private val lists = LinkedHashMap<String, Entry>()
    private val areasAsked = HashMap<String, Long>()

    var version by mutableIntStateOf(0)
        private set

    /** Keeps [items] under [key] ("all", the departures of a stop, an area), in place of what was there. */
    fun put(key: String, items: List<Disruption>) {
        synchronized(lists) {
            lists.remove(key)
            lists[key] = Entry(items, System.currentTimeMillis())
            while (lists.size > MAX_POOL_LISTS) lists.remove(lists.keys.first())
        }
        version++
    }

    /** The notices of [departures] of the stop [at] (see departureNotices), kept under that stop. */
    fun putDepartures(key: String, departures: List<io.github.jnbp.nextstop.data.Departure>, at: Stop?) {
        val found = departureNotices(departures, at)
        val before = synchronized(lists) { lists[key]?.items }
        // The same notices again (the departures of a stop come every half minute): no new round.
        if (before != null && before.map { it.id } == found.map { it.id }) return
        put(key, found)
    }

    /**
     * Every notice known now, each once: those kept here (no older than [POOL_MAX_AGE_MILLIS]),
     * those of the stored statuses of the routes [routeIds], those around the places of
     * "My routes", and those noted from the journeys and departures loaded lately (see
     * AlertHarvest: the notices of Transitous away from Berlin). Reads the stored statuses:
     * call off the main thread.
     */
    fun known(context: Context, routeIds: List<String>): List<Disruption> {
        val now = System.currentTimeMillis()
        val pooled = synchronized(lists) { lists.values.filter { now - it.millis <= POOL_MAX_AGE_MILLIS }.map { it.items } }
        val routes = routeIds.distinct().mapNotNull { DisruptionWatch.statusOf(context, it)?.disruptions() }
        val places = PlaceDisruptionCache.all().map { it.disruptions }
        return mergeDisruptions(pooled + routes + places + listOf(AlertHarvest.current()))
    }

    /**
     * Asks the departures of up to three big stops of [stops] in the [box] the map shows (see
     * bigStopsIn) for their notices, at most once in [AREA_REFRESH_MILLIS] per area of the map
     * (see noticeAreaKey); one request per stop. A stop that fails is left out.
     */
    suspend fun refreshArea(source: TransitSource, box: List<Double>, stops: List<Stop>) {
        if (box.size < 4) return
        val center = (box[0] + box[2]) / 2 to (box[1] + box[3]) / 2
        val key = noticeAreaKey(center)
        val now = System.currentTimeMillis()
        synchronized(areasAsked) {
            val last = areasAsked[key]
            if (last != null && now - last < AREA_REFRESH_MILLIS) return
        }
        val big = bigStopsIn(stops, box, center)
        if (big.isEmpty()) return
        synchronized(areasAsked) { areasAsked[key] = now }
        try {
            val found = coroutineScope {
                big.map { stop ->
                    async {
                        try {
                            departureNotices(source.departures(stop.id, results = AREA_DEPARTURES), stop)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            null
                        }
                    }
                }.awaitAll()
            }
            put("area:$key", mergeDisruptions(found.filterNotNull()))
        } catch (e: CancellationException) {
            // Moved on before the answer: the area may be asked again.
            synchronized(areasAsked) { areasAsked.remove(key) }
            throw e
        }
    }

    private var namesKey: String? = null
    private var namesCache: NamedStops? = null

    /**
     * The stops the notices of [items] may name (see noticePlaces): [extra] and the stations
     * and stop areas of the offline data (see OsmStations.mapStops) within NOTICE_AREA_METERS
     * of the stops that carry them, looked up per region of half a degree around those stops.
     * The stops of the offline data are kept while the regions and [generation] (of the
     * offline data) stay. Call off the main thread.
     */
    fun namesFor(items: List<Disruption>, extra: List<Stop>, generation: Any?): NamedStops {
        val anchors = items.filter { !it.station }.flatMap { it.stops }.mapNotNull { it.position }
        val reachLatitude = NOTICE_AREA_METERS / 111_320.0
        val boxes = anchors.groupBy { Math.floor(it.first / NAME_REGION).toLong() to Math.floor(it.second / NAME_REGION).toLong() }
            .values.map { points ->
                val reachLongitude = NOTICE_AREA_METERS / (111_320.0 * cos(Math.toRadians(points.first().first)).coerceAtLeast(0.2))
                listOf(
                    Math.floor((points.minOf { it.first } - reachLatitude) * NAME_SNAP) / NAME_SNAP,
                    Math.floor((points.minOf { it.second } - reachLongitude) * NAME_SNAP) / NAME_SNAP,
                    Math.ceil((points.maxOf { it.first } + reachLatitude) * NAME_SNAP) / NAME_SNAP,
                    Math.ceil((points.maxOf { it.second } + reachLongitude) * NAME_SNAP) / NAME_SNAP,
                )
            }
        val key = boxes.map { it.joinToString(",") }.sorted().joinToString(";") + "|" + generation
        val cached = synchronized(this) { if (key == namesKey) namesCache else null }
        val offline = cached ?: NamedStops(
            boxes.flatMap { box ->
                OsmStations.mapStops(box[0], box[1], box[2], box[3], stationsOnly = false, limit = Int.MAX_VALUE, maxCells = MAX_NAME_CELLS)
            },
        ).also { made ->
            synchronized(this) {
                namesKey = key
                namesCache = made
            }
        }
        return NamedStops(extra) + offline
    }

    // The regions the names are looked up in (degrees), how their boxes are rounded (to 0.05
    // degrees, so a small change keeps the answer), and the largest box in cells of 0.01 degrees.
    private const val NAME_REGION = 0.5
    private const val NAME_SNAP = 20.0
    private const val MAX_NAME_CELLS = 12_000

    // Lists kept at most (the oldest go first), and how long one counts.
    private const val MAX_POOL_LISTS = 40
    private const val POOL_MAX_AGE_MILLIS = 45 * 60 * 1000L

    // An area of the map is asked again after this long.
    private const val AREA_REFRESH_MILLIS = 5 * 60 * 1000L

    // Departures asked per big stop of an area: enough to meet most of its lines.
    private const val AREA_DEPARTURES = 40
}

/**
 * The notices of a sign on the map as the sheet of "My routes" shows them (see
 * WaysDisruptionsSheet), the stop as its title, each with "Hide on map". It closes once
 * all of them are hidden.
 */
@Composable
fun MapNoticeSheet(marker: NoticeMarker, onAll: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val hidden = HiddenNotices.list
    val items = marker.items.filter { item -> hidden.none { it.id == item.id } }
    LaunchedEffect(items.isEmpty()) { if (items.isEmpty()) onDismiss() }
    if (items.isEmpty()) return
    WaysDisruptionsSheet(
        title = marker.stop.name.shortStopName(),
        subtitle = pluralStringResource(R.plurals.map_notices_here, items.size, items.size),
        items = items,
        cancelled = emptyList(),
        onOpenRoute = {},
        onAll = {
            onDismiss()
            onAll()
        },
        onShowOnMap = null,
        onDismiss = onDismiss,
        onHide = { item -> HiddenNotices.hide(context, item) },
    )
}

/** The quiet row at the end of "My routes" that opens the notices hidden from the maps; nothing while none is. */
@Composable
fun HiddenNoticesLink(onClick: () -> Unit) {
    val count = HiddenNotices.list.size
    if (count == 0) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = 44.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.hidden_notices_row, count),
            color = AppColors.Muted,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 25.dp),
        )
        Chevron(open = false, modifier = Modifier.rotate(-90f))
    }
}

/** The notices hidden from the maps, each to show again, and all of them at once. Closes once none is left. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiddenNoticesSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val items = HiddenNotices.list
    LaunchedEffect(items.isEmpty()) { if (items.isEmpty()) onDismiss() }
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.PlainGround,
        contentColor = AppColors.Ink,
    ) {
        // A sheet is a window of its own: the picture of the map does not line up behind it.
        CompositionLocalProvider(LocalBackdrop provides null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = stringResource(R.string.hidden_notices_title),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(start = 8.dp, bottom = 2.dp),
                )
                Text(
                    text = stringResource(R.string.hidden_notices_subtitle),
                    color = AppColors.Muted,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(start = 8.dp, bottom = 10.dp),
                )
                items.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(groupShape(index, items.lastIndex))
                            .background(AppColors.Card)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        val line = item.line
                        if (line != null) {
                            LineBadge(line, item.product.orEmpty(), minWidth = 34.dp)
                        } else {
                            WarningMark(size = 15.dp)
                        }
                        Text(
                            text = item.title,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            lineHeight = 18.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = stringResource(R.string.hidden_notice_show),
                            color = AppColors.Blue,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(AppColors.Chip)
                                .clickable(role = Role.Button) { HiddenNotices.show(context, item.id) }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                }
                if (items.size > 1) {
                    Text(
                        text = stringResource(R.string.hidden_notices_show_all),
                        color = AppColors.Blue,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(AppColors.Card)
                            .clickable(role = Role.Button) { HiddenNotices.showAll(context) }
                            .defaultMinSize(minHeight = 52.dp)
                            .padding(horizontal = 16.dp, vertical = 15.dp),
                    )
                }
            }
        }
    }
}

/**
 * The places the overview of disruptions treats as the region (see nationwideDisruptions):
 * where the user is (or the middle of the map), and home. Positions only; nothing is stored.
 */
internal fun disruptionAnchors(prefs: AppPrefs, source: RoutingSource): List<Pair<Double, Double>> =
    listOfNotNull(
        source.searchNear ?: source.searchRegion,
        prefs.loadFavoritePlaces().firstOrNull { it.icon == PlaceIcon.Home }?.place?.position,
    )
