package io.github.jnbp.nextstop.data

import java.time.Duration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.OffsetDateTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/*
 * Air lines and real tracks. The regional HAFAS (BVG, VBB) knows the way of its own trips, but
 * for a train from elsewhere (an ICE, an IC, a regional train beyond Berlin and Brandenburg) it
 * sends only the straight lines from station to station: Berlin Südkreuz to Lutherstadt
 * Wittenberg as one line through the fields. Here such hops are found ([airHops]), and the way
 * of the same trip from a source that has its tracks (Transitous) is put in their place
 * ([spliceTrack]). Pure logic, no Android.
 */

/** A way with a gap longer than this between two of its points is no track there: no line of a feed is that coarse. */
const val AIR_GAP_METERS = 4000.0

/** A stop this close to a way lies on it. */
const val TRACK_STOP_METERS = 1000.0

/** The same run in two sources leaves a stop at most this far apart in their timetables. */
const val SAME_RUN_SECONDS = 180L

/** True for the products that run on rails across the land, whose way another source may know better. */
fun runsOnRails(product: String): Boolean = product == "express" || product == "regional" || product == "suburban"

/**
 * True for a ride of [product] named [line] that runs on rails across the land: an ICE, IC, EC,
 * ECE, EN, NJ, RJ, RJX, TGV, FLX or WB train ("express"), an RE, RB, IRE or MEX of any operator
 * ("regional"), an S-Bahn ("suburban"), and a ride of unknown product named as such a train.
 */
fun runsOnRails(product: String, line: String?): Boolean =
    runsOnRails(product) || product.isEmpty() && trainKind(line) != null

// Kinds of trains in front of a number in a line name, also behind the name of an operator ("ODEG RE1").
private val TRAIN_KINDS = setOf(
    "ICE", "IC", "EC", "ECE", "EN", "NJ", "RJ", "RJX", "TGV", "FLX", "WB", "ES", "EST", "THA", "EUR", "D",
    "IRE", "RE", "RB", "IR", "MEX", "FEX", "RS", "S",
)
private val KIND_AND_NUMBER = Regex("""(?<![A-Z])([A-Z]{1,4})\s*(\d{1,5})(?!\d)""")

/**
 * The train in [line] as kind and number without blanks: "ICE603" of "ICE 603", "IC2433" of
 * "IC2433", "RE1" of "ODEG RE 1" or "re1"; null where no kind of a train stands before a number.
 */
fun trainKey(line: String?): String? {
    val text = line?.uppercase() ?: return null
    val match = KIND_AND_NUMBER.findAll(text).lastOrNull { it.groupValues[1] in TRAIN_KINDS } ?: return null
    return match.groupValues[1] + match.groupValues[2].trimStart('0').ifEmpty { "0" }
}

/** The kind of the train in [line] ("ICE", "RE", "FLX"); null where it names none (see trainKey). */
fun trainKind(line: String?): String? {
    val text = line?.uppercase()?.trim() ?: return null
    trainKey(text)?.let { key -> return key.takeWhile { it.isLetter() } }
    return text.split(' ').firstOrNull { it in TRAIN_KINDS }
}

/**
 * For every stop with a position (in [stops], null where it has none) the index of the point
 * of [path] nearest to it, never before the one of the stop before; null for a stop without a
 * position. Empty when [path] is.
 */
internal fun stopIndexes(path: List<Pair<Double, Double>>, stops: List<Pair<Double, Double>?>): List<Int?> {
    if (path.isEmpty()) return stops.map { null }
    var from = 0
    return stops.map { stop ->
        if (stop == null) return@map null
        var best = from
        var bestDistance = Double.MAX_VALUE
        for (index in from until path.size) {
            val distance = metersBetween(stop, path[index])
            if (distance < bestDistance) {
                bestDistance = distance
                best = index
            }
        }
        from = best
        best
    }
}

/**
 * The hops of a trip, by the index of the stop they leave, whose way on [path] is only an air
 * line: a gap longer than [AIR_GAP_METERS] between two points of the way from one stop with a
 * position to the next (the stops' own positions at its ends). A stop without a position joins
 * the hop it lies in. Without a way of two points every hop between stops with positions is one.
 */
fun airHops(path: List<Pair<Double, Double>>, stops: List<Pair<Double, Double>?>): Set<Int> {
    val placed = stops.indices.filter { stops[it] != null }
    if (placed.size < 2) return emptySet()
    val hops = mutableSetOf<Int>()
    val indexes = if (path.size >= 2) stopIndexes(path, stops) else null
    for (pair in placed.zipWithNext()) {
        val (a, b) = pair
        val start = stops[a]!!
        val end = stops[b]!!
        val section = if (indexes == null) {
            listOf(start, end)
        } else {
            listOf(start) + path.subList(indexes[a]!!, indexes[b]!! + 1) + listOf(end)
        }
        if (longestGap(section) > AIR_GAP_METERS) (a until b).forEach { hops.add(it) }
    }
    return hops
}

/**
 * [path] with its air hops (see [airHops]) taken from [track], the way of the same trip from
 * another source, where that passes both stops of the hop (within [TRACK_STOP_METERS]) in order
 * and is no air line there itself. The other hops keep their own way. [path] itself where no
 * hop could be taken; the stops' positions stand in for a missing [path].
 */
fun spliceTrack(
    path: List<Pair<Double, Double>>,
    stops: List<Pair<Double, Double>?>,
    track: List<Pair<Double, Double>>,
): List<Pair<Double, Double>> {
    val air = airHops(path, stops)
    if (air.isEmpty() || track.size < 2) return path
    val placed = stops.indices.filter { stops[it] != null }
    val own = if (path.size >= 2) path else placed.map { stops[it]!! }
    val ownIndexes = stopIndexes(own, stops)
    val trackIndexes = stopIndexes(track, stops)
    val result = ArrayList<Pair<Double, Double>>(own.size + track.size)
    var taken = false
    for ((a, b) in placed.zipWithNext()) {
        val start = stops[a]!!
        val end = stops[b]!!
        val fromTrack = if (a in air) {
            val from = trackIndexes[a]!!
            val to = trackIndexes[b]!!
            val section = if (to > from) track.subList(from, to + 1) else null
            section?.takeIf {
                metersBetween(it.first(), start) <= TRACK_STOP_METERS &&
                    metersBetween(it.last(), end) <= TRACK_STOP_METERS &&
                    longestGap(it) <= AIR_GAP_METERS
            }
        } else {
            null
        }
        if (fromTrack != null) taken = true
        val section = fromTrack ?: own.subList(ownIndexes[a]!!, maxOf(ownIndexes[a]!!, ownIndexes[b]!!) + 1)
        section.forEachIndexed { index, point -> if (index > 0 || result.lastOrNull() != point) result.add(point) }
    }
    if (!taken) return path
    // What the own way has before the first stop and after the last stays.
    val head = own.subList(0, ownIndexes[placed.first()]!!)
    val tail = own.subList(minOf(own.size, ownIndexes[placed.last()]!! + 1), own.size)
    return head + result + tail
}

/** A vehicle this close to the straight line between two stations is on it. */
const val AIR_LINE_METERS = 150.0

/** Without a way to judge by, a vehicle on the straight line this far from both stations is taken to be on it. */
const val AIR_LINE_CLEAR_METERS = 2000.0

/**
 * True when a train at [position], reported between the station it reached last ([left]) and
 * the one it comes to ([next]), is placed on the air line between them: the hop is longer than
 * [AIR_GAP_METERS], [position] lies on its straight line (within [AIR_LINE_METERS]), and
 * [shape], the way the provider sends with it, has no point between them off that line (a
 * real track bends away from it). Without a shape the position must lie well away from both
 * stations ([AIR_LINE_CLEAR_METERS]), as a track leaves a station along the line at first.
 * The provider then only guessed the position by the time; the train stands at a station
 * instead (see parkedPoint).
 */
fun onAirLine(
    position: Pair<Double, Double>,
    left: Pair<Double, Double>?,
    next: Pair<Double, Double>?,
    shape: List<Pair<Double, Double>>,
): Boolean {
    if (left == null || next == null) return false
    val hop = metersBetween(left, next)
    if (hop <= AIR_GAP_METERS) return false
    val (offset, share) = offLine(position, left, next)
    if (offset > AIR_LINE_METERS || share < 0.0 || share > 1.0) return false
    val between = shape.filter { point ->
        val (away, along) = offLine(point, left, next)
        along > 0.0 && along < 1.0 && away < hop / 2
    }
    if (between.any { offLine(it, left, next).first > AIR_LINE_METERS }) return false
    if (shape.size >= 2) return true
    return metersBetween(position, left) > AIR_LINE_CLEAR_METERS && metersBetween(position, next) > AIR_LINE_CLEAR_METERS
}

// How far [point] lies from the straight line from [a] to [b] in metres, and where along it (0 at [a], 1 at [b]).
private fun offLine(point: Pair<Double, Double>, a: Pair<Double, Double>, b: Pair<Double, Double>): Pair<Double, Double> {
    val scale = cos((a.first + b.first) / 2 * PI / 180)
    val bx = (b.second - a.second) * scale * 111_320.0
    val by = (b.first - a.first) * 111_320.0
    val px = (point.second - a.second) * scale * 111_320.0
    val py = (point.first - a.first) * 111_320.0
    val length = bx * bx + by * by
    if (length <= 0.0) return sqrt(px * px + py * py) to 0.0
    val share = (px * bx + py * by) / length
    val clamped = share.coerceIn(0.0, 1.0)
    val dx = px - bx * clamped
    val dy = py - by * clamped
    return sqrt(dx * dx + dy * dy) to share
}

/** A line name for comparing two sources: no blanks, capitals ("ICE 603" and "ICE603", "RE 1" and "RE1"). */
fun lineKey(name: String?): String? = name?.filter { !it.isWhitespace() }?.uppercase()?.takeIf { it.isNotEmpty() }

/**
 * How well [departure] of another source fits the run of [line] towards [direction] that leaves
 * its stop at [planned] by the timetable; 0 when it is not that run. Both must leave within
 * [SAME_RUN_SECONDS]. 3: the same line name, however written ("ICE 603" and "ICE603", "RE 1" and
 * "ODEG RE1"). 1: names that differ (a line number "FLX10" for a train number "FLX 1234"), but
 * the same kind of train within a minute towards the same town.
 */
fun runScore(departure: Departure, line: String?, planned: OffsetDateTime, direction: String? = null): Int {
    val time = departure.plannedTime ?: departure.time ?: return 0
    val apart = Math.abs(Duration.between(time, planned).seconds)
    if (apart > SAME_RUN_SECONDS) return 0
    val key = lineKey(line) ?: return 0
    if (lineKey(departure.line) == key) return 3
    val train = trainKey(line)
    if (train != null && train == trainKey(departure.line)) return 3
    val kind = trainKind(line) ?: return 0
    if (kind != trainKind(departure.line) || apart > SAME_KIND_SECONDS) return 0
    // Only names that do not compare like for like: a line number against a train number
    // ("FLX10" for "FLX 1234"). Two line numbers that differ ("RE 10", "RE1"; "S42", "S41") are other lines.
    if (!lineAgainstTrainNumber(line, departure.line)) return 0
    val town = directionTown(direction) ?: return 0
    return if (town == directionTown(departure.direction)) 1 else 0
}

// True when one of the names carries a line number (1-2 digits) and the other a train number (3 or more).
private fun lineAgainstTrainNumber(a: String?, b: String?): Boolean {
    val one = nameDigits(a) ?: return false
    val other = nameDigits(b) ?: return false
    return (one <= 2 && other >= 3) || (one >= 3 && other <= 2)
}

// How many digits the number of a line name has ("FLX10" 2, "FLX 1234" 4); null without one.
private fun nameDigits(name: String?): Int? =
    (trainKey(name)?.dropWhile { it.isLetter() } ?: lineKey(name)?.takeLastWhile { it.isDigit() })
        ?.length?.takeIf { it > 0 }

/** True when [departure] is the run of [line] that leaves at [planned] by its name (see runScore). */
fun sameRun(departure: Departure, line: String?, planned: OffsetDateTime): Boolean = runScore(departure, line, planned) >= 3

/**
 * How well [departure] fits as the run that lends its track to a ride of [line] leaving at
 * [planned] (see runScore): also 2 for a long-distance train of the same number under another
 * kind within [SAME_RUN_SECONDS] ("RJ 384" of Rejseplanen's copy and "ICE 384" of DELFI's, #280).
 */
fun trackRunScore(departure: Departure, line: String?, planned: OffsetDateTime, direction: String? = null): Int {
    val score = runScore(departure, line, planned, direction)
    if (score >= 2) return score
    val number = longDistanceNumber(line) ?: return score
    if (longDistanceNumber(departure.line) != number) return score
    val time = departure.plannedTime ?: departure.time ?: return score
    return if (Math.abs(Duration.between(time, planned).seconds) <= SAME_RUN_SECONDS) 2 else score
}

/**
 * The handle of the run among [found] (departures at a station of the ride) that lends its
 * track to the ride of [line] towards [direction] leaving at [planned]: the best fitting one by
 * [trackRunScore], never [own] itself (a Transitous ride whose feed has no shape, as
 * Rejseplanen's copy of a German train, borrows the way of another feed's copy of the run,
 * #280). Null where none fits.
 */
fun trackLender(found: List<Departure>, own: String?, line: String?, planned: OffsetDateTime, direction: String?): String? =
    trackLenders(found, own, line, planned, direction).firstOrNull()

/**
 * The handles of the runs among [found] that may lend their track to the ride of [line] towards
 * [direction] leaving at [planned], best first: by [trackRunScore], then by their feed (see
 * [lenderFeedRank]: DELFI's copy of a German train before another German feed's, a foreign
 * feed's last), never [own]. Each is tried in turn until one has a real way (see [lendsWay]):
 * on 19 Nov 2026 the RJ 382 Berlin - Hamburg of Rejseplanen found the Dutch feed's copy of the
 * run first, itself only straight lines, and kept that (#280).
 */
fun trackLenders(found: List<Departure>, own: String?, line: String?, planned: OffsetDateTime, direction: String?): List<String> =
    found.filter { !it.cancelled && it.tripId != null && it.tripId != own }
        .map { it to trackRunScore(it, line, planned, direction) }
        .filter { it.second > 0 }
        .sortedWith(compareByDescending<Pair<Departure, Int>> { it.second }.thenBy { lenderFeedRank(it.first.tripId) })
        .mapNotNull { it.first.tripId }
        .distinct()

// The feed in a Transitous trip handle, with or without its "tr:" mark: country and name ("de", "DELFI").
private val LENDER_FEED = Regex("""^(?:tr:)?[0-9]{8}_[0-9]{1,2}:[0-9]{2}_([a-z]{2})-([A-Za-z0-9]+)""")

/**
 * How early the run [tripId] is asked to lend its track: 0 for a trip of DELFI (the German
 * feed with the shapes of German trains), 1 for another German feed, 2 for a handle without a
 * feed (another source), 3 for a foreign feed, whose copy of a German train often has no way.
 */
fun lenderFeedRank(tripId: String?): Int {
    val match = tripId?.let { LENDER_FEED.find(it) } ?: return 2
    val country = match.groupValues[1]
    return when {
        country == "de" && match.groupValues[2] == "DELFI" -> 0
        country == "de" -> 1
        else -> 3
    }
}

/** At most this share of a lent track may be air lines (see [airShare]); a copy drawn station to station lends nothing. */
const val LENDER_AIR_SHARE = 0.2

/**
 * The share of the length of [track] that lies in gaps longer than [AIR_GAP_METERS] (straight
 * lines from station to station); 1 for a way without length or with a point that is no
 * place on the earth (not finite, beyond the poles or the date line).
 */
fun airShare(track: List<Pair<Double, Double>>): Double {
    if (track.size < 2 || track.any { !placeOnEarth(it) }) return 1.0
    var total = 0.0
    var air = 0.0
    for (index in 1 until track.size) {
        val metres = metersBetween(track[index - 1], track[index])
        total += metres
        if (metres > AIR_GAP_METERS) air += metres
    }
    return if (total > 0.0) air / total else 1.0
}

/** True when [track] is a real way that may lend itself to a ride (not mostly air lines, see [airShare]). */
fun lendsWay(track: List<Pair<Double, Double>>): Boolean = airShare(track) <= LENDER_AIR_SHARE

/** True for a point with a finite latitude within the poles and a finite longitude within the date line. */
fun placeOnEarth(point: Pair<Double, Double>): Boolean =
    point.first.isFinite() && point.second.isFinite() && point.first in -90.0..90.0 && point.second in -180.0..180.0

/** The positions of the stops of the ride [leg] in order (null where one has none): where it is boarded, the stops between, where it is left. */
fun rideStopPositions(leg: Leg): List<Pair<Double, Double>?> =
    listOf(leg.fromPlace?.position ?: leg.path.firstOrNull()) + leg.stopovers.map { it.place?.position } +
        listOf(leg.toPlace?.position ?: leg.path.lastOrNull())

/** [leg] with its air hops taken from [track] (see [spliceTrack]); [leg] itself where nothing changes. */
fun withLentTrack(leg: Leg, track: List<Pair<Double, Double>>?): Leg {
    if (leg.walking || track == null || track.size < 2) return leg
    val path = spliceTrack(leg.path, rideStopPositions(leg), track)
    return if (path === leg.path) leg else leg.copy(path = path)
}

/**
 * [journey] with the tracks known by now ([trackOf], the TrackCache) put into its rides with air
 * hops, no request: a track lent after the journey was shown takes its place in it (#280).
 * [journey] itself where nothing changes.
 */
fun withLentTracks(journey: Journey, trackOf: (String) -> List<Pair<Double, Double>>? = { TrackCache.get(it) }): Journey {
    var changed = false
    val legs = journey.legs.map { leg ->
        val tripId = leg.tripId
        if (leg.walking || tripId == null) return@map leg
        val lent = withLentTrack(leg, trackOf(tripId))
        if (lent !== leg) changed = true
        lent
    }
    return if (changed) journey.copy(legs = legs) else journey
}

/**
 * [course] with the track known by now ([trackOf], the TrackCache) put into its air hops, and
 * its air hops found anew; [course] itself where nothing changes (see TripCourse.airHops).
 */
fun withLentTrack(course: TripCourse, trackOf: (String) -> List<Pair<Double, Double>>? = { TrackCache.get(it) }): TripCourse {
    if (course.airHops.isEmpty()) return course
    val track = trackOf(course.tripId)?.takeIf { it.size >= 2 } ?: return course
    val stops = course.stops.map { it.place?.position }
    val path = spliceTrack(course.path, stops, track)
    if (path === course.path) return course
    return course.copy(path = path, airHops = airHops(path, stops))
}

/** Trains of one kind towards one town that leave this close together are taken for one run when their names differ. */
const val SAME_KIND_SECONDS = 60L

// The town a direction names, for comparing: its first word without the signs of the network and
// without a leading "Berlin" (most directions here start with it, "Berlin, Ring" or "Berlin Ostkreuz"), small letters.
private fun directionTown(direction: String?): String? {
    val words = direction?.trim()?.removePrefix("S+U ")?.removePrefix("S ")?.removePrefix("U ")
        ?.split(' ', ',', '(')?.map { word -> word.lowercase().filter { it.isLetter() } }?.filter { it.isNotEmpty() }
        ?: return null
    val town = if (words.size > 1 && words.first() == "berlin") words[1] else words.firstOrNull()
    return town?.takeIf { it.length >= 2 }
}

/** The longest step between two neighbouring points of [points], in metres. */
internal fun longestGap(points: List<Pair<Double, Double>>): Double {
    var longest = 0.0
    for (index in 1 until points.size) longest = maxOf(longest, metersBetween(points[index - 1], points[index]))
    return longest
}

private fun metersBetween(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
    val north = (b.first - a.first) * 111_320.0
    val east = (b.second - a.second) * 111_320.0 * cos((a.first + b.first) / 2 * PI / 180)
    return sqrt(north * north + east * east)
}

/**
 * The tracks that Transitous lent trips of another source (see RoutingSource.withTrack), by the
 * trip's id, shared by every source of the process: a trip is looked up once. A trip it does
 * not know is kept as an empty way for [MISS_MILLIS], then asked for again; one whose lookup
 * failed (timeout, HTTP, I/O, see [putFailure]) only for [FAILURE_MILLIS]. At most [SIZE]
 * trips; the one used longest ago goes first. A trip being looked up is [claim]ed, so it is
 * not looked up twice at once.
 */
object TrackCache {
    private const val SIZE = 64
    private const val MISS_MILLIS = 30L * 60 * 1000
    private const val FAILURE_MILLIS = 3L * 60 * 1000

    private class Entry(val track: List<Pair<Double, Double>>, val at: Long, val life: Long = MISS_MILLIS)

    private val entries = object : LinkedHashMap<String, Entry>(SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean = size > SIZE
    }
    private val claimed = HashSet<String>()

    /** The track of [tripId]; empty when it is known that there is none; null when it was not looked up yet. */
    fun get(tripId: String, now: Long = System.currentTimeMillis()): List<Pair<Double, Double>>? = synchronized(this) {
        val entry = entries[tripId] ?: return null
        if (entry.track.isEmpty() && now - entry.at > entry.life) {
            entries.remove(tripId)
            return null
        }
        entry.track
    }

    /** Keeps [track] for [tripId]; an empty one when it has none. A new track counts [version] up. */
    fun put(tripId: String, track: List<Pair<Double, Double>>, now: Long = System.currentTimeMillis()) {
        synchronized(this) { entries[tripId] = Entry(track, now) }
        if (track.size >= 2) versionFlow.update { it + 1 }
    }

    private val versionFlow = MutableStateFlow(0)

    /** Counts up whenever a track is kept: what shows rides with air hops takes it in place (#280). */
    val version: StateFlow<Int> = versionFlow.asStateFlow()

    /**
     * Keeps for [tripId] that its lookup failed: it counts as without a track for
     * [FAILURE_MILLIS] (a back-off), then is asked for again. A known track is not replaced.
     */
    fun putFailure(tripId: String, now: Long = System.currentTimeMillis()) {
        synchronized(this) {
            if (entries[tripId]?.track?.isNotEmpty() == true) return
            entries[tripId] = Entry(emptyList(), now, FAILURE_MILLIS)
        }
    }

    /** True while [tripId] is being looked up (see [claim]). */
    fun isClaimed(tripId: String): Boolean = synchronized(this) { tripId in claimed }

    /** True when [tripId] was not being looked up and now is (see [release]). */
    fun claim(tripId: String): Boolean = synchronized(this) { claimed.add(tripId) }

    /** Ends the lookup of [tripId] claimed with [claim]. */
    fun release(tripId: String) {
        synchronized(this) { claimed.remove(tripId) }
    }

    /** Forgets everything (for tests). */
    fun clear() {
        synchronized(this) {
            entries.clear()
            claimed.clear()
        }
    }
}
