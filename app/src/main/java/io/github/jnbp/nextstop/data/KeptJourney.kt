package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.json.JSONArray
import org.json.JSONObject

/**
 * The open journey page as it is kept on the device, so that it comes back when Android ends
 * the process in the background: the tab, whether the followed trip was open, the search (start,
 * destination, stop on the way and time) and the connections with the one picked. Journeys are
 * kept whole, with their refresh handles, so the page shows at once and is then loaded fresh.
 */
data class KeptPage(
    /** When it was written, in milliseconds since the epoch. */
    val savedAt: Long,
    /** The name of the tab in front (see ui.Tab). */
    val tab: String,
    /** True when the page of the followed trip was open. */
    val tripOpen: Boolean,
    /** The followed trip, null without one. */
    val followed: Journey?,
    val origin: Stop?,
    val destination: Stop?,
    val via: Via?,
    /** The time picked for the search, null for "now". */
    val time: OffsetDateTime?,
    val timeArrival: Boolean,
    /** The connections found, null when none were shown. */
    val journeys: List<Journey>?,
    val selectedId: String?,
    val detailPage: Boolean,
    val planning: Boolean,
) {
    /** The journey the page is about: the followed trip, else the picked connection. */
    val openJourney: Journey?
        get() = followed ?: journeys?.firstOrNull { it.id == selectedId }

    /** True when nothing worth keeping is open: no trip, no connections and no search. */
    val empty: Boolean
        get() = followed == null && journeys == null && (origin == null || destination == null)
}

/**
 * The start the start page keeps once the connections of a search are closed: the [origin] of
 * that search, except where it is [restored], the very start a kept page brought back after
 * Android ended the process. That one was picked in an earlier run of the app (at another place,
 * maybe hours ago); a new search starts where the user is, as on any fresh start (null: the
 * selected nearby stop). A start picked in this run is another object and stays.
 */
fun startAfterClose(origin: Stop?, restored: Stop?): Stop? =
    if (origin != null && origin === restored) null else origin

/** A kept page is dropped after this long. */
const val KEPT_PAGE_MAX_AGE_MILLIS = 12L * 60 * 60 * 1000

// Connections kept at most: the picked one is always among them.
private const val KEPT_JOURNEYS_MAX = 16

/**
 * True when [page] is still worth showing at [nowMillis] / [now]: not older than ~12 hours,
 * and the journey it is about (or, without one, the last of its connections) has not arrived.
 */
fun keptPageUsable(page: KeptPage, nowMillis: Long, now: OffsetDateTime): Boolean {
    val age = nowMillis - page.savedAt
    if (age < 0 || age > KEPT_PAGE_MAX_AGE_MILLIS) return false
    if (page.empty) return false
    val open = page.openJourney
    if (open != null) return open.arrival.isAfter(now)
    val list = page.journeys
    if (!list.isNullOrEmpty()) return list.any { it.arrival.isAfter(now) }
    return true
}

/** [page] as JSON text. */
fun keptPageJson(page: KeptPage): String {
    val json = JSONObject()
    json.put("v", 1)
    json.put("savedAt", page.savedAt)
    json.put("tab", page.tab)
    json.put("tripOpen", page.tripOpen)
    page.followed?.let { json.put("followed", keptJourneyJson(it)) }
    page.origin?.let { json.put("origin", keptStopJson(it)) }
    page.destination?.let { json.put("destination", keptStopJson(it)) }
    page.via?.let { json.put("via", JSONObject().put("place", keptStopJson(it.place)).put("minutes", it.minutes)) }
    page.time?.let { json.put("time", it.toString()) }
    json.put("timeArrival", page.timeArrival)
    page.journeys?.let { list ->
        val kept = list.take(KEPT_JOURNEYS_MAX).let { first ->
            val picked = list.firstOrNull { it.id == page.selectedId }
            if (picked != null && first.none { it.id == picked.id }) first + picked else first
        }
        json.put("journeys", JSONArray().apply { kept.forEach { put(keptJourneyJson(it)) } })
    }
    page.selectedId?.let { json.put("selectedId", it) }
    json.put("detailPage", page.detailPage)
    json.put("planning", page.planning)
    return json.toString()
}

/** The page of [text], or null when it cannot be read. */
fun keptPageOf(text: String): KeptPage? = try {
    val json = JSONObject(text)
    if (json.optInt("v") != 1) {
        null
    } else {
        KeptPage(
            savedAt = json.getLong("savedAt"),
            tab = json.optString("tab", ""),
            tripOpen = json.optBoolean("tripOpen"),
            followed = json.optJSONObject("followed")?.let { keptJourneyOf(it) },
            origin = json.optJSONObject("origin")?.let { keptStopOf(it) },
            destination = json.optJSONObject("destination")?.let { keptStopOf(it) },
            via = json.optJSONObject("via")?.let { v ->
                v.optJSONObject("place")?.let { keptStopOf(it) }?.let { Via(it, v.optInt("minutes")) }
            },
            time = str(json, "time")?.let { OffsetDateTime.parse(it) },
            timeArrival = json.optBoolean("timeArrival"),
            journeys = json.optJSONArray("journeys")?.let { list -> objects(list).mapNotNull { keptJourneyOf(it) } },
            selectedId = str(json, "selectedId"),
            detailPage = json.optBoolean("detailPage"),
            planning = json.optBoolean("planning"),
        )
    }
} catch (e: Exception) {
    // A file of another version or a broken one: nothing comes back.
    null
}

private fun str(json: JSONObject, key: String): String? =
    if (json.has(key) && !json.isNull(key)) json.getString(key) else null

private fun int(json: JSONObject, key: String): Int? =
    if (json.has(key) && !json.isNull(key)) json.getInt(key) else null

private fun dbl(json: JSONObject, key: String): Double? =
    if (json.has(key) && !json.isNull(key)) json.getDouble(key) else null

private fun time(json: JSONObject, key: String): OffsetDateTime? = str(json, key)?.let { OffsetDateTime.parse(it) }

private fun objects(list: JSONArray): List<JSONObject> = (0 until list.length()).mapNotNull { list.optJSONObject(it) }

private fun strings(list: JSONArray?): List<String> =
    if (list == null) emptyList() else (0 until list.length()).map { list.getString(it) }

private fun pathJson(path: List<Pair<Double, Double>>): JSONArray =
    JSONArray().apply { path.forEach { put(it.first); put(it.second) } }

private fun pathOf(list: JSONArray?): List<Pair<Double, Double>> {
    if (list == null) return emptyList()
    return (0 until list.length() / 2).map { list.getDouble(2 * it) to list.getDouble(2 * it + 1) }
}

internal fun keptStopJson(stop: Stop): JSONObject = JSONObject()
    .put("id", stop.id)
    .put("name", stop.name)
    .putOpt("lat", stop.latitude)
    .putOpt("lon", stop.longitude)
    .putOpt("dist", stop.distanceMeters)
    .put("products", JSONArray(stop.products.toList()))
    .put("kind", stop.kind.name)
    .putOpt("postcode", stop.postcode)
    .put("areas", JSONArray(stop.areas))
    .putOpt("detail", stop.detail)

internal fun keptStopOf(json: JSONObject): Stop = Stop(
    id = json.getString("id"),
    name = json.getString("name"),
    latitude = dbl(json, "lat"),
    longitude = dbl(json, "lon"),
    distanceMeters = int(json, "dist"),
    products = strings(json.optJSONArray("products")).toSet(),
    kind = PlaceKind.entries.firstOrNull { it.name == json.optString("kind") } ?: PlaceKind.Stop,
    postcode = str(json, "postcode"),
    areas = strings(json.optJSONArray("areas")),
    detail = str(json, "detail"),
)

private fun walkTargetJson(target: WalkTarget): JSONObject = JSONObject()
    .put("kind", target.kind.name)
    .putOpt("label", target.label)
    .put("lat", target.latitude)
    .put("lon", target.longitude)
    .put("lines", JSONArray().apply { target.lines.forEach { put(JSONObject().put("name", it.name).put("product", it.product)) } })
    .put("through", target.throughBuilding)
    .putOpt("level", target.level)
    .putOpt("climbBy", target.climbBy?.name)
    .put("climb", target.climb)

private fun walkTargetOf(json: JSONObject?): WalkTarget? {
    if (json == null) return null
    return WalkTarget(
        kind = WalkTargetKind.valueOf(json.getString("kind")),
        label = str(json, "label"),
        latitude = json.getDouble("lat"),
        longitude = json.getDouble("lon"),
        lines = json.optJSONArray("lines")?.let { list ->
            objects(list).map { OsmLine(it.getString("name"), it.getString("product")) }
        }.orEmpty(),
        throughBuilding = json.optBoolean("through"),
        level = str(json, "level"),
        climbBy = str(json, "climbBy")?.let { PassageKind.valueOf(it) },
        climb = json.optInt("climb"),
    )
}

private fun warningJson(warning: Warning): JSONObject = JSONObject()
    .put("summary", warning.summary)
    .put("text", warning.text)
    .putOpt("id", warning.id)
    .putOpt("from", warning.validFrom?.toString())
    .putOpt("until", warning.validUntil?.toString())
    .put("links", JSONArray().apply {
        warning.links.forEach { put(JSONObject().put("kind", it.kind.name).putOpt("url", it.url).putOpt("label", it.label)) }
    })
    .putOpt("stop", warning.stop?.let { keptStopJson(it) })
    .putOpt("code", warning.code)

private fun warningOf(json: JSONObject): Warning = Warning(
    summary = json.getString("summary"),
    text = json.getString("text"),
    id = str(json, "id"),
    validFrom = time(json, "from"),
    validUntil = time(json, "until"),
    links = json.optJSONArray("links")?.let { list ->
        objects(list).map { NoticeLink(NoticeLinkKind.valueOf(it.getString("kind")), str(it, "url"), str(it, "label")) }
    }.orEmpty(),
    stop = json.optJSONObject("stop")?.let { keptStopOf(it) },
    code = str(json, "code"),
)

private fun stopoverJson(stop: Stopover): JSONObject = JSONObject()
    .put("name", stop.name)
    .putOpt("arr", stop.arrival?.toString())
    .putOpt("dep", stop.departure?.toString())
    .putOpt("place", stop.place?.let { keptStopJson(it) })
    .putOpt("delay", stop.delaySeconds)
    .putOpt("pArr", stop.plannedArrival?.toString())
    .putOpt("pDep", stop.plannedDeparture?.toString())
    .put("est", stop.estimated)

private fun stopoverOf(json: JSONObject): Stopover = Stopover(
    name = json.getString("name"),
    arrival = time(json, "arr"),
    departure = time(json, "dep"),
    place = json.optJSONObject("place")?.let { keptStopOf(it) },
    delaySeconds = int(json, "delay"),
    plannedArrival = time(json, "pArr"),
    plannedDeparture = time(json, "pDep"),
    estimated = json.optBoolean("est"),
)

private fun throughRunJson(run: ThroughRun): JSONObject = JSONObject()
    .putOpt("tripId", run.tripId)
    .putOpt("line", run.line)
    .put("atName", run.atName)
    .putOpt("at", run.at?.let { keptStopJson(it) })
    .put("dep", run.departure.toString())
    .put("lineChanges", run.lineChanges)

private fun throughRunOf(json: JSONObject): ThroughRun = ThroughRun(
    tripId = str(json, "tripId"),
    line = str(json, "line"),
    atName = json.getString("atName"),
    at = json.optJSONObject("at")?.let { keptStopOf(it) },
    departure = OffsetDateTime.parse(json.getString("dep")),
    lineChanges = json.optBoolean("lineChanges"),
)

private fun legJson(leg: Leg): JSONObject = JSONObject()
    .putOpt("line", leg.line)
    .put("product", leg.product)
    .put("walking", leg.walking)
    .put("from", leg.from)
    .put("to", leg.to)
    .put("dep", leg.departure.toString())
    .putOpt("pDep", leg.plannedDeparture?.toString())
    .put("arr", leg.arrival.toString())
    .putOpt("pArr", leg.plannedArrival?.toString())
    .putOpt("depPlatform", leg.departurePlatform)
    .putOpt("arrPlatform", leg.arrivalPlatform)
    .putOpt("direction", leg.direction)
    .put("cancelled", leg.cancelled)
    .put("realtime", leg.realtime)
    .put("path", pathJson(leg.path))
    .putOpt("fromPlace", leg.fromPlace?.let { keptStopJson(it) })
    .putOpt("toPlace", leg.toPlace?.let { keptStopJson(it) })
    .put("warnings", JSONArray().apply { leg.warnings.forEach { put(warningJson(it)) } })
    .put("stopovers", JSONArray().apply { leg.stopovers.forEach { put(stopoverJson(it)) } })
    .putOpt("tripId", leg.tripId)
    .putOpt("walkTarget", leg.walkTarget?.let { walkTargetJson(it) })
    .putOpt("walkExit", leg.walkExit?.let { walkTargetJson(it) })
    .putOpt("boarding", leg.boardingSpot?.let { walkTargetJson(it) })
    .putOpt("alighting", leg.alightingSpot?.let { walkTargetJson(it) })
    .put("additional", leg.additional)
    .putOpt("walkMeters", leg.walkMeters)
    .put("arrEst", leg.arrivalEstimated)
    .put("stayOnBoard", leg.stayOnBoard)
    .put("throughRuns", JSONArray().apply { leg.throughRuns.forEach { put(throughRunJson(it)) } })
    .put("cycling", leg.cycling)
    .putOpt("color", leg.providerColor)
    .putOpt("textColor", leg.providerTextColor)

private fun legOf(json: JSONObject): Leg = Leg(
    line = str(json, "line"),
    product = json.getString("product"),
    walking = json.getBoolean("walking"),
    from = json.getString("from"),
    to = json.getString("to"),
    departure = OffsetDateTime.parse(json.getString("dep")),
    plannedDeparture = time(json, "pDep"),
    arrival = OffsetDateTime.parse(json.getString("arr")),
    plannedArrival = time(json, "pArr"),
    departurePlatform = str(json, "depPlatform"),
    arrivalPlatform = str(json, "arrPlatform"),
    direction = str(json, "direction"),
    cancelled = json.optBoolean("cancelled"),
    realtime = json.optBoolean("realtime"),
    path = pathOf(json.optJSONArray("path")),
    fromPlace = json.optJSONObject("fromPlace")?.let { keptStopOf(it) },
    toPlace = json.optJSONObject("toPlace")?.let { keptStopOf(it) },
    warnings = json.optJSONArray("warnings")?.let { list -> objects(list).map { warningOf(it) } }.orEmpty(),
    stopovers = json.optJSONArray("stopovers")?.let { list -> objects(list).map { stopoverOf(it) } }.orEmpty(),
    tripId = str(json, "tripId"),
    walkTarget = walkTargetOf(json.optJSONObject("walkTarget")),
    walkExit = walkTargetOf(json.optJSONObject("walkExit")),
    boardingSpot = walkTargetOf(json.optJSONObject("boarding")),
    alightingSpot = walkTargetOf(json.optJSONObject("alighting")),
    additional = json.optBoolean("additional"),
    walkMeters = int(json, "walkMeters"),
    arrivalEstimated = json.optBoolean("arrEst"),
    stayOnBoard = json.optBoolean("stayOnBoard"),
    throughRuns = json.optJSONArray("throughRuns")?.let { list -> objects(list).map { throughRunOf(it) } }.orEmpty(),
    cycling = json.optBoolean("cycling"),
    providerColor = int(json, "color"),
    providerTextColor = int(json, "textColor"),
)

/** [journey] as JSON, with its refresh handles. */
fun keptJourneyJson(journey: Journey): JSONObject = JSONObject()
    .put("legs", JSONArray().apply { journey.legs.forEach { put(legJson(it)) } })
    .putOpt("refreshToken", journey.refreshToken)
    .putOpt("split", journey.split?.let {
        JSONObject().put("headLegs", it.headLegs).putOpt("headToken", it.headToken).putOpt("tailToken", it.tailToken)
            .putOpt("headCut", it.headCut).putOpt("tailJoined", it.tailJoined.takeIf { joined -> joined })
    })
    .putOpt("via", journey.via?.let {
        JSONObject().put("place", keptStopJson(it.place)).put("minutes", it.minutes).putOpt("afterLegs", it.afterLegs)
    })
    .putOpt("foundLevel", journey.foundLevel?.name)

/** The journey of [json], or null without legs. */
fun keptJourneyOf(json: JSONObject): Journey? {
    val legs = json.optJSONArray("legs")?.let { list -> objects(list).map { legOf(it) } }.orEmpty()
    if (legs.isEmpty()) return null
    return Journey(
        legs = legs,
        refreshToken = str(json, "refreshToken"),
        split = json.optJSONObject("split")?.let {
            JourneySplit(
                it.getInt("headLegs"), str(it, "headToken"), str(it, "tailToken"), str(it, "headCut"),
                tailJoined = it.optBoolean("tailJoined"),
            )
        },
        via = json.optJSONObject("via")?.let {
            JourneyVia(keptStopOf(it.getJSONObject("place")), it.optInt("minutes"), int(it, "afterLegs"))
        },
        foundLevel = str(json, "foundLevel")?.let { name -> TransferComfort.entries.firstOrNull { it.name == name } },
    )
}
