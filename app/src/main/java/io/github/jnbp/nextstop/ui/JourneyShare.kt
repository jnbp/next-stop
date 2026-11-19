package io.github.jnbp.nextstop.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.CalendarContract
import android.widget.Toast
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Base64
import java.util.zip.Deflater
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.AppPrefs
import io.github.jnbp.nextstop.data.Backend
import io.github.jnbp.nextstop.data.FavoritePlace
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.Leg
import io.github.jnbp.nextstop.data.PlaceIcon
import io.github.jnbp.nextstop.data.PlaceKind
import io.github.jnbp.nextstop.data.WalkTargetKind
import io.github.jnbp.nextstop.data.plainStopName
import io.github.jnbp.nextstop.data.samePlatformByNumbers
import java.time.OffsetDateTime

/** A platform as people read it: "Platform 4", "Bay 13" for a tram or bus, texts such as "Pos. 11" as they are. */
private fun sharePlatform(context: Context, platform: String?, product: String?): String? = platformLabel(context, platform, product)

/**
 * The legs of [journey] as plain lines: each ride with its line, where it starts with
 * time and platform, and where it ends; each walk (or ride by bike) with its minutes.
 */
fun journeyLegsText(context: Context, journey: Journey): String = journey.legs.mapNotNull { leg ->
    if (leg.walking) {
        val minutes = minutesBetween(leg.departure, leg.arrival)
        if (minutes > 0) context.getString(if (leg.cycling) R.string.share_cycle else R.string.share_walk, durationText(context, minutes)) else null
    } else {
        val start = sharePlatform(context, leg.departurePlatform, leg.product)
            ?.let { context.getString(R.string.share_platform, leg.departure.clock(), it) }
            ?: leg.departure.clock()
        context.getString(
            R.string.share_ride,
            leg.line ?: leg.product,
            leg.from.plainStopName(),
            start,
            leg.to.plainStopName(),
            leg.arrival.clock(),
        )
    }
}.joinToString("\n")

/** The whole journey as a readable text: day, from and to, times, duration and changes, then its legs. */
fun journeyText(context: Context, journey: Journey, fromName: String, toName: String): String {
    val day = journey.departure
        .atZoneSameInstant(BERLIN)
        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.getDefault()))
    return listOf(
        day,
        context.getString(R.string.calendar_title, fromName, toName),
        context.getString(R.string.time_range, journey.departure.clock(), journey.arrival.clock()),
        context.getString(R.string.duration_and_transfers, durationText(context, journey.durationMinutes), journey.transfers),
        "",
        journeyLegsText(context, journey),
    ).joinToString("\n")
}

/** Opens the share sheet of the system with [journey] as text ("Nur als Text" of [ShareSheet]). */
internal fun shareConnectionText(context: Context, journey: Journey, fromName: String, toName: String) {
    val text = journeyText(context, journey, fromName, toName)
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.calendar_title, fromName, toName))
        .putExtra(Intent.EXTRA_TEXT, text)
    try {
        context.startActivity(Intent.createChooser(send, context.getString(R.string.share_chooser)))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.no_share_app, Toast.LENGTH_SHORT).show()
    }
}

/**
 * Opens the dialog of the calendar app for a new event from the first departure of [journey]
 * to its arrival, at the stop where it starts. The calendar app writes the event itself, so
 * no permission is needed. Without a start name of its own (the current location) the title
 * only names where the trip leads.
 */
fun addJourneyToCalendar(context: Context, journey: Journey, fromName: String, toName: String) {
    val title = if (fromName.isBlank() || fromName == context.getString(R.string.current_location)) {
        context.getString(R.string.calendar_title_to, toName)
    } else {
        context.getString(R.string.calendar_title, fromName, toName)
    }
    // Where the user sets off: the stop of the first ride, or the start of a journey on foot.
    val start = (journey.rides.firstOrNull() ?: journey.legs.first()).from.plainStopName()
    val insert = Intent(Intent.ACTION_INSERT)
        .setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, journey.departure.toInstant().toEpochMilli())
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, journey.arrival.toInstant().toEpochMilli())
        .putExtra(CalendarContract.Events.TITLE, title)
        .putExtra(CalendarContract.Events.EVENT_LOCATION, start)
        .putExtra(CalendarContract.Events.DESCRIPTION, journeyText(context, journey, fromName, toName))
    try {
        context.startActivity(insert)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.no_calendar_app, Toast.LENGTH_SHORT).show()
    }
}

/**
 * A followed trip as it stands at [now], to send to someone: where it leads, the next step
 * with its live time, the expected arrival by the live times ("I expect to arrive at 14:32",
 * with the delay where there is one), and the legs still ahead.
 */
fun tripShareText(context: Context, journey: Journey, now: OffsetDateTime): String {
    val destination = journey.legs.last().to.plainStopName()
    val step = tripStep(journey, now)
    val late = journey.legs.last().arrivalDelayMinutes
    val arrival = context.getString(R.string.trip_share_arrival, journey.arrival.clock()) +
        (if (late > 0) context.getString(R.string.trip_share_late, late) else "") + "."
    val ahead = journey.legs.filter { it.arrival.isAfter(now) }
    return listOfNotNull(
        tripShareHeading(context, sharedDestination(context, journey, destination)),
        when {
            step.kind == StepKind.Arrived -> null
            // "Leave at 05:07" already says its time.
            step.leave -> context.getString(R.string.trip_share_now_plain, stepLabel(context, step))
            else -> context.getString(R.string.trip_share_now, stepLabel(context, step), step.time.clock())
        },
        arrival,
        "",
        if (ahead.isEmpty()) null else journeyLegsText(context, Journey(ahead)),
    ).joinToString("\n").trimEnd()
}

/** Opens the share sheet of the system with the followed [journey] as it stands at [now]. */
internal fun shareTripText(context: Context, journey: Journey, now: OffsetDateTime) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, tripShareText(context, journey, now))
    try {
        context.startActivity(Intent.createChooser(send, context.getString(R.string.trip_share)))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.no_share_app, Toast.LENGTH_SHORT).show()
    }
}

/* ---------- Live link ---------- */

// TODO: after "Übernehmen" of a faster connection the shared live link still shows the old
// journey; offer to share it again then.

/** Opens the share sheet of the system with a live [url], with the line [lead] before it when given. */
internal fun shareLiveLink(context: Context, url: String, lead: String? = null) {
    val text = listOfNotNull(lead, context.getString(R.string.live_link_text, url)).joinToString("\n")
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, text)
    try {
        context.startActivity(Intent.createChooser(send, context.getString(R.string.live_link_share)))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.no_share_app, Toast.LENGTH_SHORT).show()
    }
}

/**
 * Where a shared trip leads as others may read it: a saved place of the user (home, work, a
 * favourite) by its label instead of its address, [kind] "h" for home and "w" for work (the
 * texts then say "nach Hause", "zur Arbeit"); any other destination by its [name] as it is.
 */
internal data class SharedDestination(val name: String, val kind: String? = null, val saved: Boolean = false)

/** The destination of [journey], named [name] in the app, as [SharedDestination] for a live link and its text. */
internal fun sharedDestination(context: Context, journey: Journey, name: String): SharedDestination {
    val last = journey.legs.last()
    val end = last.toPlace?.position ?: last.path.lastOrNull()
    val prefs = AppPrefs(context)
    val places = prefs.loadFavoritePlaces() + prefs.loadSavedPlaces().map { it.asFavorite() }
    val saved = savedPlaceAt(places, last.toPlace?.id, end?.first, end?.second) ?: return SharedDestination(name)
    val iconLabel = context.getString(placeIconLabelRes(saved.icon))
    val label = saved.name.trim().ifEmpty { iconLabel }
    // "Nach Hause" only for the home as such; a place with a name of its own goes by that name.
    val kind = when {
        label != iconLabel -> null
        saved.icon == PlaceIcon.Home -> "h"
        saved.icon == PlaceIcon.Work -> "w"
        else -> null
    }
    return SharedDestination(label, kind, saved = true)
}

// How near the end of a journey must be to a saved place to count as it, in metres.
internal const val SAVED_PLACE_MATCH_M = 150.0

/** The saved place among [places] that a journey ending at [id] or at the position ends at; null for none. */
internal fun savedPlaceAt(places: List<FavoritePlace>, id: String?, latitude: Double?, longitude: Double?): FavoritePlace? =
    places.firstOrNull { favorite ->
        val place = favorite.place ?: return@firstOrNull false
        val sameId = !id.isNullOrBlank() && place.id == id && id != CURRENT_LOCATION_ID
        val lat = place.latitude
        val lon = place.longitude
        sameId || (
            latitude != null && longitude != null && lat != null && lon != null &&
                metersBetween(latitude, longitude, lat, lon) <= SAVED_PLACE_MATCH_M
            )
    }

/** The first line of a shared trip: "Ich bin unterwegs nach …", "… auf dem Weg nach Hause". */
internal fun tripShareHeading(context: Context, target: SharedDestination): String = when (target.kind) {
    "h" -> context.getString(R.string.trip_share_heading_home)
    "w" -> context.getString(R.string.trip_share_heading_work)
    else -> context.getString(R.string.trip_share_heading, target.name)
}

/** The language of the app as the links pass it on ("de", "en"): the pages use it when the recipient's is neither. */
internal fun shareLanguage(context: Context): String? =
    context.resources.configuration.locales.takeIf { it.size() > 0 }?.get(0)?.language

/**
 * The live link of [journey] to [destination] on the live page at [base] (the address from the
 * settings), with the times the journey has at [now] and the first [name] the page shows (none
 * when blank); null when [base] is no web address. [destination] is what the page calls it: the
 * label of a saved place ([destinationKind] "h" or "w" for home and work, see
 * [SharedDestination]), whose position then goes only to about 100 m. The start of the trip is
 * never in the link: a first walk starts at the stop it leads to, named after the start only
 * when that is a stop itself. [language] is the sender's ([shareLanguage]).
 */
fun liveLink(
    base: String,
    journey: Journey,
    destination: String,
    now: OffsetDateTime,
    name: String? = null,
    destinationKind: String? = null,
    savedDestination: Boolean = destinationKind != null,
    language: String? = null,
): String? {
    val root = liveBase(base) ?: return null
    val last = journey.legs.last()
    val end = (last.toPlace?.position ?: last.path.lastOrNull())
        ?.let { if (savedDestination) coarse(it.first) to coarse(it.second) else it }
    val target = LivePlace(destination, null, end?.first, end?.second, null, 0)
    val legs = journey.legs.map { leg -> liveLeg(leg) }.toMutableList()
    // A walk at the end leads to the destination as the page shows it, not to its address.
    if (last.walking) {
        val walk = legs.last()
        legs[legs.size - 1] = walk.copy(to = target.copy(time = walk.to.time))
    }
    val first = journey.legs.first()
    val origin = first.fromPlace
        ?.takeIf { first.walking && it.kind == PlaceKind.Stop && it.id != CURRENT_LOCATION_ID }
        ?.let { first.from.plainStopName() }
    val json = liveLinkCompactJson(
        name = name?.trim()?.takeIf { it.isNotEmpty() },
        legs = legs,
        destination = target,
        created = now.toEpochSecond(),
        origin = origin,
        destinationKind = destinationKind,
    )
    return siteLink(root, LIVE_PAGE, json, language)
}

/** A coordinate to 3 decimals, about 100 m: a saved place shows where it is, not which door. */
private fun coarse(value: Double): Double = Math.round(value * 1000.0) / 1000.0

private fun liveLeg(leg: Leg): LiveLeg {
    val start = leg.fromPlace?.position ?: leg.path.firstOrNull()
    val end = leg.toPlace?.position ?: leg.path.lastOrNull()
    // Trips of the backends with a tag (VBB HAFAS "vbb~", public-transport-enabler "pte~") are
    // unknown to the live page, which asks BVG REST or Transitous: such a leg goes without live data.
    val trip = leg.tripId?.takeIf { !leg.walking && Backend.entries.none { b -> b.tag.isNotEmpty() && it.startsWith(b.tag) } }
    return LiveLeg(
        provider = trip?.let { if (it.startsWith(TRANSITOUS_PREFIX)) "tr" else "bvg" },
        trip = trip?.removePrefix(TRANSITOUS_PREFIX),
        line = if (leg.walking) null else leg.line ?: leg.product,
        direction = if (leg.walking) null else leg.direction,
        walk = leg.walking,
        from = LivePlace(
            name = leg.from.plainStopName(),
            id = leg.fromPlace?.id?.removePrefix(TRANSITOUS_PREFIX),
            latitude = start?.first,
            longitude = start?.second,
            time = (leg.plannedDeparture ?: leg.departure).toEpochSecond(),
            delayMinutes = leg.departureDelayMinutes,
        ),
        to = LivePlace(
            name = leg.to.plainStopName(),
            id = leg.toPlace?.id?.removePrefix(TRANSITOUS_PREFIX),
            latitude = end?.first,
            longitude = end?.second,
            time = (leg.plannedArrival ?: leg.arrival).toEpochSecond(),
            delayMinutes = leg.arrivalDelayMinutes,
        ),
    )
}

// The prefix of Transitous ids in the app (see DataSource.Transitous).
private const val TRANSITOUS_PREFIX = "tr:"

/**
 * The site of the web pages that shared links lead to while the setting is empty: the project
 * website, which hosts web/live and web/route. Users who host the pages themselves set their own.
 */
const val DEFAULT_SITE_ADDRESS = "https://nextstop.bapo.me"

// The folders of the two pages on the site (web/live, web/route) and the link format they read.
private const val LIVE_PAGE = "live"
private const val ROUTE_PAGE = "route"
// v1: base64url JSON (still read by the pages); v2: the JSON raw-deflated, then base64url.
private const val LINK_VERSION = 2

/** The address of the web pages for the [setting] as typed: the setting, or [DEFAULT_SITE_ADDRESS] when it is blank. */
internal fun siteAddress(setting: String): String = setting.trim().ifEmpty { DEFAULT_SITE_ADDRESS }

/**
 * The link to [page] ("live" or "route") of the site at [base] with [json] in its fragment, in the
 * form the pages read (readFragment in web/live/app.js, readRoute in web/route/app.js):
 * `<site>/<page>/#v=2&l=<language>&d=<base64url of the raw-deflated JSON>`, without "l" when
 * [language] is no language code. Null when [base] is no web address.
 */
internal fun siteLink(base: String, page: String, json: String, language: String? = null): String? {
    val root = liveBase(base) ?: return null
    val lang = language?.lowercase()?.takeIf { it.matches(LANGUAGE_CODE) }
    return "$root/$page/#v=$LINK_VERSION" + (lang?.let { "&l=$it" } ?: "") + "&d=" + base64Url(deflateRaw(json))
}

private val LANGUAGE_CODE = Regex("[a-z]{2,3}")

/** The address of the web pages as typed, made a plain https root without a trailing "/live" or "/route". */
internal fun liveBase(base: String): String? {
    var root = base.trim().trimEnd('/')
    if (root.isEmpty()) return null
    if (!root.startsWith("https://") && !root.startsWith("http://")) root = "https://$root"
    root = root.removeSuffix("/$LIVE_PAGE").removeSuffix("/$ROUTE_PAGE").trimEnd('/')
    val host = root.substringAfter("://").substringBefore('/')
    return if (host.isEmpty() || host.contains(' ')) null else root
}

/* ---------- Route link ---------- */

/**
 * The route link of [journey] to [destination] on the route page at [base] (the address from
 * the settings): the connection step by step for someone else to travel, with platforms, stop
 * positions, the station exits and entrances of the walks, where to board, and the ways of the
 * legs. Null when [base] is no web address. Carries no name; the start is in it as the journey
 * has it (which is the current position when the search started there, as in the live link).
 */
fun routeLink(context: Context, base: String, journey: Journey, destination: String, now: OffsetDateTime): String? {
    val root = liveBase(base) ?: return null
    val hints = AppPrefs(context).boardingHints
    val last = journey.legs.last()
    val end = last.toPlace?.position ?: last.path.lastOrNull()
    val legs = journey.legs.map { leg -> routeLeg(leg, if (hints) boardingCode(journey, leg) else null) }
    val json = routeLinkJson(legs, destination, end?.first, end?.second, now.toEpochSecond())
    return siteLink(root, ROUTE_PAGE, json, shareLanguage(context))
}

/**
 * Where to board [leg] for the change after it, as the route link writes it: "f", "m" or "r"
 * and whether it is an estimate; null where it does not matter or is not known.
 */
private fun boardingCode(journey: Journey, leg: Leg): Pair<String, Boolean>? {
    if (leg.walking) return null
    val next = rideAfter(journey, leg) ?: return null
    if (leg.tripId != null && leg.tripId == next.tripId) return null
    if (samePlatformByNumbers(leg, next)) return null
    // The dataset may not be loaded yet; then the estimate from the map, or nothing.
    val position = runCatching { boardingPositionOf(leg, next) }.getOrNull() ?: return null
    if (position.samePlatform) return null
    val part = when (position.part) {
        0 -> "r"
        2 -> "f"
        else -> "m"
    }
    return part to position.estimated
}

private fun routeLeg(leg: Leg, board: Pair<String, Boolean>?): RouteLeg {
    val start = leg.fromPlace?.position ?: leg.path.firstOrNull()
    val end = leg.toPlace?.position ?: leg.path.lastOrNull()
    // As for the live link: trips of tagged backends are unknown to the page (no live data then).
    val trip = leg.tripId?.takeIf { !leg.walking && Backend.entries.none { b -> b.tag.isNotEmpty() && it.startsWith(b.tag) } }
    val walk = leg.walking
    return RouteLeg(
        provider = trip?.let { if (it.startsWith(TRANSITOUS_PREFIX)) "tr" else "bvg" },
        trip = trip?.removePrefix(TRANSITOUS_PREFIX),
        line = if (walk) null else leg.line ?: leg.product,
        product = if (walk) null else leg.product,
        direction = if (walk) null else leg.direction,
        walk = walk,
        bike = walk && leg.cycling,
        from = RoutePlace(
            name = leg.from.plainStopName(),
            id = leg.fromPlace?.id?.removePrefix(TRANSITOUS_PREFIX),
            latitude = start?.first,
            longitude = start?.second,
            time = (leg.plannedDeparture ?: leg.departure).toEpochSecond(),
            delayMinutes = if (walk) 0L else leg.departureDelayMinutes,
            platform = if (walk) null else leg.departurePlatform,
            spot = if (walk) null else leg.boardingSpot?.label,
            // A walk out of a station: the exit it leaves by.
            exit = if (walk) leg.walkExit?.takeIf { it.kind == WalkTargetKind.Entrance }?.label else null,
        ),
        to = RoutePlace(
            name = leg.to.plainStopName(),
            id = leg.toPlace?.id?.removePrefix(TRANSITOUS_PREFIX),
            latitude = end?.first,
            longitude = end?.second,
            time = (leg.plannedArrival ?: leg.arrival).toEpochSecond(),
            delayMinutes = if (walk) 0L else leg.arrivalDelayMinutes,
            platform = if (walk) null else leg.arrivalPlatform,
            // A walk to a ride: the stop position or the station entrance it leads to.
            spot = if (walk) leg.walkTarget?.takeIf { it.kind == WalkTargetKind.StopPosition }?.label else leg.alightingSpot?.label,
            entrance = if (walk) leg.walkTarget?.takeIf { it.kind == WalkTargetKind.Entrance }?.label else null,
        ),
        meters = if (walk) leg.walkMeters else null,
        stopCount = if (!walk && leg.stopovers.size >= 2) leg.stopovers.size - 2 else null,
        board = board?.first,
        boardEstimated = board?.second ?: false,
        cancelled = !walk && leg.cancelled,
        shape = routeShape(leg.path, if (walk) WALK_SHAPE_TOLERANCE_M else RIDE_SHAPE_TOLERANCE_M),
    )
}

/** Opens the share sheet of the system with a route [url] to [destination]. */
internal fun shareRouteLink(context: Context, url: String, destination: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, context.getString(R.string.route_link_text, destination, url))
    try {
        context.startActivity(Intent.createChooser(send, context.getString(R.string.share_chooser)))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.no_share_app, Toast.LENGTH_SHORT).show()
    }
}

/**
 * Puts [url] on the clipboard ("Link kopieren" of [ShareSheet]). From Android 13 the system shows
 * what was copied itself; before it a short toast says so.
 */
internal fun copyLink(context: Context, url: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.share_link_title), url))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, R.string.share_link_copied, Toast.LENGTH_SHORT).show()
    }
}

// region live-link-core (plain Kotlin, also run by the test harness in web/test)

/** A place of the live link: [time] in epoch seconds (planned), [delayMinutes] as known when shared. */
internal data class LivePlace(
    val name: String,
    val id: String?,
    val latitude: Double?,
    val longitude: Double?,
    val time: Long?,
    val delayMinutes: Long,
)

/** A leg of the live link; [provider] "bvg" or "tr", null for a walk. */
internal data class LiveLeg(
    val provider: String?,
    val trip: String?,
    val line: String?,
    val direction: String?,
    val walk: Boolean,
    val from: LivePlace,
    val to: LivePlace,
)

/**
 * The journey as compact JSON for the fragment of the live link (format v2, read by expandLive in
 * web/live/app.js, which documents the keys; encodeLive there writes exactly the same JSON):
 * {n, b, c, s:[[name,id,lat,lon]], r:[{f,t,d,a,dd,ad,p,i,l,r,w}], o, e, z:[name,lat,lon], k}.
 * Times are seconds after the first departure [b], each stop is written once, positions have 4
 * decimals, and a walk between two legs is only its two times on the leg after it ([w]; [e] for
 * the walk to the destination). The start of a first walk is never written: it starts at the
 * stop it leads to, named [origin] when given.
 */
internal fun liveLinkCompactJson(
    name: String?,
    legs: List<LiveLeg>,
    destination: LivePlace,
    created: Long,
    origin: String? = null,
    destinationKind: String? = null,
): String {
    val base = legs.first().from.time ?: created
    fun at(place: LivePlace): String = ((place.time ?: base) - base).toString()
    fun position(value: Double?): String? = value?.takeIf { it.isFinite() }?.let { jsonNumber(it, 4) }
    val stops = mutableListOf<String>()
    val keys = HashMap<String, Int>()
    fun stopIndex(place: LivePlace): String {
        val id = place.id?.takeIf { it.isNotEmpty() }
        val lat = position(place.latitude)
        val lon = position(place.longitude)
        val where = if (lat != null && lon != null) "$lat,$lon" else ""
        val key = if (id != null) "i:$id|${place.name}" else "n:${place.name}|$where"
        keys[key]?.let { return it.toString() }
        val entry = mutableListOf(jsonString(place.name))
        when {
            lat != null && lon != null -> entry += listOf(id?.let { jsonString(it) } ?: "null", lat, lon)
            id != null -> entry += jsonString(id)
        }
        stops += entry.joinToString(",", "[", "]")
        keys[key] = stops.size - 1
        return (stops.size - 1).toString()
    }
    // The same stop: the same id where both have one, else the same name.
    fun same(a: LivePlace, b: LivePlace): Boolean =
        if (!a.id.isNullOrEmpty() && !b.id.isNullOrEmpty()) a.id == b.id else a.name == b.name
    val rides = mutableListOf<String>()
    var walkBefore: String? = null
    var end: String? = null
    legs.forEachIndexed { i, leg ->
        val next = legs.getOrNull(i + 1)
        val prev = legs.getOrNull(i - 1)
        if (leg.walk) {
            val linksFrom = i == 0 || (prev != null && !prev.walk && same(prev.to, leg.from))
            if (next != null && !next.walk && linksFrom && same(leg.to, next.from)) {
                walkBefore = "[${at(leg.from)},${at(leg.to)}]"
            } else if (next == null && prev != null && !prev.walk && same(prev.to, leg.from)) {
                end = "[${at(leg.from)},${at(leg.to)}]"
            } else {
                rides += jsonObject(
                    "k" to "1",
                    // The start of the trip is never in the link: a first walk starts where it leads.
                    "f" to stopIndex(if (i == 0) leg.to else leg.from),
                    "t" to stopIndex(leg.to),
                    "d" to at(leg.from),
                    "a" to at(leg.to),
                )
            }
            return@forEachIndexed
        }
        rides += jsonObject(
            "f" to stopIndex(leg.from),
            "t" to stopIndex(leg.to),
            "d" to at(leg.from),
            "a" to at(leg.to),
            "dd" to leg.from.delayMinutes.takeIf { it != 0L }?.toString(),
            "ad" to leg.to.delayMinutes.takeIf { it != 0L }?.toString(),
            "p" to leg.provider?.takeIf { it.isNotEmpty() }?.let { jsonString(it) },
            "i" to leg.trip?.takeIf { it.isNotEmpty() }?.let { jsonString(it) },
            "l" to leg.line?.takeIf { it.isNotEmpty() }?.let { jsonString(it) },
            "r" to leg.direction?.takeIf { it.isNotEmpty() }?.let { jsonString(it) },
            "w" to walkBefore,
        )
        walkBefore = null
    }
    val destLat = position(destination.latitude)
    val destLon = position(destination.longitude)
    val z = listOf(jsonString(destination.name)) + if (destLat != null && destLon != null) listOf(destLat, destLon) else emptyList()
    return jsonObject(
        "n" to name?.trim()?.takeIf { it.isNotEmpty() }?.let { jsonString(it) },
        "b" to base.toString(),
        "c" to (created - base).toString(),
        "s" to stops.joinToString(",", "[", "]"),
        "r" to rides.joinToString(",", "[", "]"),
        "o" to origin?.takeIf { it.isNotEmpty() }?.let { jsonString(it) },
        "e" to end,
        "z" to z.joinToString(",", "[", "]"),
        "k" to destinationKind?.takeIf { it == "h" || it == "w" }?.let { jsonString(it) },
    )
}

/* ---------- Route link ("So kommst du hin", web/route) ---------- */

/**
 * A place of the route link: [time] in epoch seconds (planned), [delayMinutes] as known when
 * shared, the [platform] ("2", "Pos. 11"), the OpenStreetMap stop position [spot] ("3"), and for
 * walks the label of the station [exit] it starts from or the [entrance] it leads to.
 */
internal data class RoutePlace(
    val name: String,
    val id: String?,
    val latitude: Double?,
    val longitude: Double?,
    val time: Long?,
    val delayMinutes: Long = 0,
    val platform: String? = null,
    val spot: String? = null,
    val exit: String? = null,
    val entrance: String? = null,
)

/**
 * A leg of the route link: a ride with [provider] "bvg" or "tr" and its [trip], or a walk with
 * its [meters]. [stopCount] counts the stops in between, [board] says where to board for the
 * change after it ("f" front, "m" middle, "r" rear; [boardEstimated] when guessed from the map),
 * [shape] is its way as an encoded polyline (precision 5), null when it is a straight line.
 */
internal data class RouteLeg(
    val provider: String?,
    val trip: String?,
    val line: String?,
    val product: String?,
    val direction: String?,
    val walk: Boolean,
    val from: RoutePlace,
    val to: RoutePlace,
    val meters: Int? = null,
    /** For a walk: it is ridden by bike (the bike combo). */
    val bike: Boolean = false,
    val stopCount: Int? = null,
    val board: String? = null,
    val boardEstimated: Boolean = false,
    val cancelled: Boolean = false,
    val shape: String? = null,
)

/**
 * The connection as compact JSON for the fragment of the route link (format v1, read by
 * web/route/app.js, the same key order as its encodeRoute):
 * {legs:[{p, trip, line, prod, dir, from:{name,id,lat,lon,dep,dd,pl,pos,exit,entr}, to:{…,arr,ad,…},
 * walk, bike, m, n, b, be, x, poly}], dest:{name,lat,lon}, t}. No name of the person who shares it.
 */
internal fun routeLinkJson(
    legs: List<RouteLeg>,
    destination: String,
    latitude: Double?,
    longitude: Double?,
    created: Long,
): String {
    fun text(value: String?) = value?.trim()?.takeIf { it.isNotEmpty() }?.let { jsonString(it) }
    fun place(place: RoutePlace, timeKey: String, delayKey: String): String = jsonObject(
        "name" to jsonString(place.name),
        "id" to text(place.id),
        "lat" to place.latitude?.takeIf { it.isFinite() }?.let { jsonNumber(it) },
        "lon" to place.longitude?.takeIf { it.isFinite() }?.let { jsonNumber(it) },
        timeKey to place.time?.toString(),
        delayKey to place.delayMinutes.takeIf { it != 0L }?.toString(),
        "pl" to text(place.platform),
        "pos" to text(place.spot),
        "exit" to text(place.exit),
        "entr" to text(place.entrance),
    )
    val legList = legs.joinToString(",", "[", "]") { leg ->
        jsonObject(
            "p" to leg.provider?.let { jsonString(it) },
            "trip" to leg.trip?.let { jsonString(it) },
            "line" to text(leg.line),
            "prod" to text(leg.product),
            "dir" to text(leg.direction),
            "from" to place(leg.from, "dep", "dd"),
            "to" to place(leg.to, "arr", "ad"),
            "walk" to if (leg.walk) "true" else null,
            "bike" to if (leg.walk && leg.bike) "true" else null,
            "m" to leg.meters?.takeIf { it > 0 }?.toString(),
            "n" to leg.stopCount?.takeIf { it >= 0 }?.toString(),
            "b" to leg.board?.takeIf { it == "f" || it == "m" || it == "r" }?.let { jsonString(it) },
            "be" to if (leg.board != null && leg.boardEstimated) "true" else null,
            "x" to if (leg.cancelled) "true" else null,
            "poly" to leg.shape?.takeIf { it.isNotEmpty() }?.let { jsonString(it) },
        )
    }
    return jsonObject(
        "legs" to legList,
        "dest" to jsonObject(
            "name" to jsonString(destination),
            "lat" to latitude?.takeIf { it.isFinite() }?.let { jsonNumber(it) },
            "lon" to longitude?.takeIf { it.isFinite() }?.let { jsonNumber(it) },
        ),
        "t" to created.toString(),
    )
}

/** [points] (latitude, longitude) as a Google encoded polyline with [precision] decimals (decodePolyline in web/shared/core.js). */
internal fun encodePolyline(points: List<Pair<Double, Double>>, precision: Int = 5): String {
    val factor = Math.pow(10.0, precision.toDouble())
    val out = StringBuilder()
    var lastLat = 0L
    var lastLon = 0L
    fun put(value: Long) {
        var v = if (value < 0) (value shl 1).inv() else value shl 1
        while (v >= 0x20) {
            out.append(((0x20L or (v and 0x1f)) + 63).toInt().toChar())
            v = v shr 5
        }
        out.append((v + 63).toInt().toChar())
    }
    for ((lat, lon) in points) {
        val a = Math.round(lat * factor)
        val o = Math.round(lon * factor)
        put(a - lastLat)
        put(o - lastLon)
        lastLat = a
        lastLon = o
    }
    return out.toString()
}

/**
 * [points] with fewer points (Douglas–Peucker): every left-out point lies within [toleranceMeters]
 * of the line that is kept. The ends always stay.
 */
internal fun simplifyPath(points: List<Pair<Double, Double>>, toleranceMeters: Double): List<Pair<Double, Double>> {
    if (points.size < 3) return points
    val keep = BooleanArray(points.size)
    keep[0] = true
    keep[points.size - 1] = true
    // A flat frame in metres around the first point (good within a region).
    val k = Math.cos(Math.toRadians(points[0].first))
    fun x(p: Pair<Double, Double>) = p.second * k * 111320.0
    fun y(p: Pair<Double, Double>) = p.first * 110540.0
    fun offLine(p: Pair<Double, Double>, a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val dx = x(b) - x(a)
        val dy = y(b) - y(a)
        val length = dx * dx + dy * dy
        val t = if (length > 0) (((x(p) - x(a)) * dx + (y(p) - y(a)) * dy) / length).coerceIn(0.0, 1.0) else 0.0
        return Math.hypot(x(p) - (x(a) + dx * t), y(p) - (y(a) + dy * t))
    }
    val stack = ArrayDeque<Pair<Int, Int>>()
    stack.addLast(0 to points.size - 1)
    while (stack.isNotEmpty()) {
        val (first, last) = stack.removeLast()
        var worst = -1
        var worstDistance = toleranceMeters
        for (i in first + 1 until last) {
            val d = offLine(points[i], points[first], points[last])
            if (d > worstDistance) {
                worst = i
                worstDistance = d
            }
        }
        if (worst > 0) {
            keep[worst] = true
            stack.addLast(first to worst)
            stack.addLast(worst to last)
        }
    }
    return points.filterIndexed { i, _ -> keep[i] }
}

// How closely the shared ways follow those of the app, in metres: fewer points, a shorter link.
// A ride keeps its curves round (a curve of 300 m radius bends by about 23 degrees per point at
// most); 25 m made the lines angular next to other apps.
internal const val WALK_SHAPE_TOLERANCE_M = 4.0
internal const val RIDE_SHAPE_TOLERANCE_M = 6.0

// Points of one way at most; beyond it the tolerance grows in small steps. Some 2 kB for a long ride.
internal const val ROUTE_SHAPE_MAX_POINTS = 300

/**
 * The way of a leg for the route link: simplified to [toleranceMeters] (more if it still has more
 * than [maxPoints] points) and encoded; null for a straight line or no way, which the page draws
 * between the two ends itself.
 */
internal fun routeShape(points: List<Pair<Double, Double>>, toleranceMeters: Double, maxPoints: Int = ROUTE_SHAPE_MAX_POINTS): String? {
    val clean = points.filter { it.first.isFinite() && it.second.isFinite() && Math.abs(it.first) <= 90 && Math.abs(it.second) <= 180 }
    if (clean.size < 3) return null
    var tolerance = toleranceMeters
    var simple = simplifyPath(clean, tolerance)
    while (simple.size > maxPoints && tolerance < 5000) {
        tolerance *= 1.5
        simple = simplifyPath(clean, tolerance)
    }
    return if (simple.size < 3) null else encodePolyline(simple)
}

/** UTF-8 [text] as base64url without padding, safe in a URL fragment. */
internal fun base64Url(text: String): String = base64Url(text.toByteArray(Charsets.UTF_8))

/** [bytes] as base64url without padding. */
internal fun base64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

/**
 * UTF-8 [text] as raw deflate data (RFC 1951, no zlib header), as the pages unpack it with
 * DecompressionStream("deflate-raw"). A JSON of some 4 kB shrinks to about a quarter.
 */
internal fun deflateRaw(text: String): ByteArray {
    val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
    try {
        deflater.setInput(text.toByteArray(Charsets.UTF_8))
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (!deflater.finished()) {
            val n = deflater.deflate(buffer)
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    } finally {
        deflater.end()
    }
}

private fun jsonObject(vararg entries: Pair<String, String?>): String =
    entries.filter { it.second != null }.joinToString(",", "{", "}") { (key, value) -> jsonString(key) + ":" + value }

/** A coordinate rounded to [decimals] (5: about a metre, 4: about 10 m), without trailing zeros. */
private fun jsonNumber(value: Double, decimals: Int = 5): String =
    BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

private fun jsonString(value: String): String {
    val out = StringBuilder("\"")
    for (c in value) {
        when {
            c == '"' -> out.append("\\\"")
            c == '\\' -> out.append("\\\\")
            c == '\n' -> out.append("\\n")
            c == '\r' -> out.append("\\r")
            c == '\t' -> out.append("\\t")
            c < ' ' || c == ' ' || c == ' ' -> out.append(String.format("\\u%04x", c.code))
            else -> out.append(c)
        }
    }
    return out.append('"').toString()
}

// endregion
