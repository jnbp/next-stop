package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/*
 * The kind of a long-distance train as HAFAS knows it (#271). DELFI's timetable at Transitous
 * names some runs by a kind that does not run: line 27 (Hamburg/Berlin - Dresden - Decin/Prague)
 * comes as "ICE 171" with the mode of an ICE, while most days the run is the Railjet "RJ 171"
 * (DB and VBB HAFAS name it so, also in their timetable). Where a HAFAS source of the app
 * knows the same run - the same train number, leaving the same station within
 * KIND_MATCH_MINUTES of the same planned time - its name wins, with its product and colour.
 * Only that per-run answer decides: no rule by line, as a real ICE does run there now and then.
 * Without an answer the name of Transitous stays. Pure logic here; the lookups are in RideKindCheck.
 */

/** The name of a ride as HAFAS knows the same run: [line] of [product] in the provider's colours (null: the app's own for [line]). */
data class RideKindFix(val line: String, val product: String, val color: Int?, val textColor: Int?)

/** Two departures of one train number this far apart as planned are one run. */
internal const val KIND_MATCH_MINUTES = 3L

// The train number at the end of a long-distance train's name: "ICE 171", "RJ171", "TGV INOUI 9898".
private val KIND_TRAIN_NUMBER = Regex("(?:^|[^0-9])([0-9]{1,6})$")

/** The number of the long-distance train [line] ("171" for "ICE 171"), without leading zeros; null for any other line. */
fun longDistanceNumber(line: String?): String? {
    if (line == null || !isLongDistanceTrainName(line)) return null
    val number = KIND_TRAIN_NUMBER.find(line.trim())?.groupValues?.get(1)?.trimStart('0') ?: return null
    return number.ifEmpty { null }
}

// A long-distance train's kind and its number with leading zeros: "RJ 000384", "ICE0601".
private val ZEROED_TRAIN_NUMBER = Regex("^(.*?[^0-9 ]) ?0+([1-9][0-9]*)$")

/**
 * [line] without the leading zeros of its train number where it names a long-distance train
 * ("RJ 000384" as "RJ 384", "ICE0601" as "ICE 601"); every other name stays as it is (#279).
 * FLX stays: as a FlixBus the zeros are part of its line's code ("FLX 040").
 */
fun withoutTrainNumberZeros(line: String?): String? {
    if (line == null || !isLongDistanceTrainName(line) || lineKind(line) == "FLX") return line
    val match = ZEROED_TRAIN_NUMBER.matchEntire(line.trim()) ?: return line
    return "${match.groupValues[1]} ${match.groupValues[2]}"
}

/**
 * True when [theirs] (the name HAFAS gives a run) is to replace [ours] (Transitous' name of the
 * same run): both long-distance trains with the same number, written differently ("ICE 171"
 * and "RJ 171", "ICE 179" and "EC 179"). The same name, another number or a name that is no
 * long-distance train never wins.
 */
fun hafasNameWins(ours: String?, theirs: String?): Boolean {
    val number = longDistanceNumber(ours) ?: return false
    if (theirs == null || longDistanceNumber(theirs) != number) return false
    return compactName(ours!!) != compactName(theirs)
}

private fun compactName(line: String): String = line.filter { !it.isWhitespace() }.uppercase()

/**
 * The run among [theirs] (departures of a HAFAS board) that is the long-distance train [ours]
 * leaving at [planned]: the same train number, planned within [KIND_MATCH_MINUTES]; the one
 * nearest in time where several are. Null where none is.
 */
fun matchingRun(ours: String?, planned: OffsetDateTime?, theirs: List<Departure>): Departure? {
    val number = longDistanceNumber(ours) ?: return null
    val at = planned?.toEpochSecond() ?: return null
    return theirs.asSequence()
        .filter { !it.additional && longDistanceNumber(it.line) == number }
        .mapNotNull { run -> (run.plannedTime ?: run.time)?.toEpochSecond()?.let { run to kotlin.math.abs(it - at) } }
        .filter { it.second <= KIND_MATCH_MINUTES * 60 }
        .minByOrNull { it.second }?.first
}

/** The fix for a ride named [ours] that is [run] at HAFAS; null where [ours] stays (see [hafasNameWins]). */
fun kindFixOf(ours: String?, run: Departure): RideKindFix? =
    if (hafasNameWins(ours, run.line)) {
        RideKindFix(run.line.trim(), run.product.ifEmpty { "express" }, run.providerColor, run.providerTextColor)
    } else {
        null
    }

/**
 * [this] named as [fix] says; the name of the timetable stays in [Leg.timetableLine], so the
 * journey keeps its id (see journeyLegsId). The same leg where the name is already right.
 */
fun Leg.withKindFix(fix: RideKindFix): Leg {
    if (walking || line == fix.line) return this
    return copy(
        line = fix.line,
        product = fix.product,
        providerColor = fix.color,
        providerTextColor = fix.textColor,
        timetableLine = timetableLine ?: line,
    )
}

/** [this] named as [fix] says (see [withKindFix]). */
fun Departure.withKindFix(fix: RideKindFix): Departure =
    if (line == fix.line) this else copy(line = fix.line, product = fix.product, providerColor = fix.color, providerTextColor = fix.textColor)

/** [this] named as [fix] says (see [withKindFix]). */
fun Vehicle.withKindFix(fix: RideKindFix): Vehicle =
    if (line == fix.line) this else copy(line = fix.line, product = fix.product, providerColor = fix.color, providerTextColor = fix.textColor)

/** [journey] with the fixes [fixOf] knows for its rides, by their handles; the same journey where none applies. */
fun withKindFixes(journey: Journey, fixOf: (String) -> RideKindFix?): Journey {
    var changed = false
    val legs = journey.legs.map { leg ->
        val fix = leg.tripId?.takeIf { !leg.walking }?.let(fixOf) ?: return@map leg
        leg.withKindFix(fix).also { if (it !== leg) changed = true }
    }
    return if (changed) journey.copy(legs = legs) else journey
}

/** [departures] with the fixes [fixOf] knows (see [withKindFixes]); the same list where none applies. */
fun withDepartureKindFixes(departures: List<Departure>, fixOf: (String) -> RideKindFix?): List<Departure> {
    var changed = false
    val result = departures.map { departure ->
        val fix = departure.tripId?.let(fixOf) ?: return@map departure
        departure.withKindFix(fix).also { if (it !== departure) changed = true }
    }
    return if (changed) result else departures
}

/** [vehicles] with the fixes [fixOf] knows (see [withKindFixes]); the same list where none applies. */
fun withVehicleKindFixes(vehicles: List<Vehicle>, fixOf: (String) -> RideKindFix?): List<Vehicle> {
    var changed = false
    val result = vehicles.map { vehicle ->
        val fix = fixOf(vehicle.tripId) ?: return@map vehicle
        vehicle.withKindFix(fix).also { if (it !== vehicle) changed = true }
    }
    return if (changed) result else vehicles
}

/**
 * [fresh] with the names [known] (the same journey, loaded before) had found at HAFAS: a
 * followed trip restored after a restart knows "RJ 171" while its refresh still says
 * "ICE 171" until the lookup has answered again. A ride keeps its fixed name where it is the
 * same run (handle) and the fresh name is still the timetable's one it was fixed from.
 */
fun keptKinds(fresh: Journey, known: Journey): Journey {
    val fixed = known.legs.filter { !it.walking && it.tripId != null && it.timetableLine != null }
        .associateBy { it.tripId!! }
    if (fixed.isEmpty()) return fresh
    return withKindFixes(fresh) { tripId ->
        val leg = fixed[tripId] ?: return@withKindFixes null
        val line = leg.line ?: return@withKindFixes null
        val ride = fresh.legs.firstOrNull { it.tripId == tripId } ?: return@withKindFixes null
        if (ride.line != leg.timetableLine) return@withKindFixes null
        RideKindFix(line, leg.product, leg.providerColor, leg.providerTextColor)
    }
}

/**
 * True when [leg] is a ride whose name a HAFAS board may correct: a long-distance train with
 * a handle (see [longDistanceNumber]).
 */
fun wantsKindCheck(leg: Leg): Boolean =
    !leg.walking && leg.tripId != null && longDistanceNumber(leg.line) != null

/**
 * The places where a HAFAS board may be asked for the run of [leg], best first: where it is
 * boarded, then the stops it leaves on its way, with their planned departures; those in the
 * area of the regional HAFAS ([inArea]) first, as it surely knows its stations, the others
 * after them. At most [max].
 */
fun kindLookupPlaces(
    leg: Leg,
    inArea: (Double, Double) -> Boolean,
    max: Int = 2,
): List<Pair<Pair<Double, Double>, OffsetDateTime>> {
    val all = buildList {
        val board = leg.fromPlace?.position
        if (board != null) add(board to (leg.plannedDeparture ?: leg.departure))
        for (stop in leg.stopovers) {
            val at = stop.place?.position ?: continue
            val time = stop.plannedDeparture ?: stop.departure ?: continue
            add(at to time)
        }
    }
    val (near, far) = all.partition { inArea(it.first.first, it.first.second) }
    return (near + far).take(max)
}

/**
 * The names found at HAFAS, by the handle of the run (see [RideKindFix]), and the runs looked
 * at without a fix (the name is right, or HAFAS does not know the run), each kept for a while
 * so a run is asked for once. [version] counts the fixes found, so what is shown can take
 * them in place.
 */
object RideKindFixes {
    // Fixes are kept for a day: a run's handle belongs to its day.
    private const val FIX_MILLIS = 24L * 60 * 60 * 1000

    // A run without a fix is not asked for again for this long.
    private const val CHECKED_MILLIS = 30L * 60 * 1000

    // At most this many entries of each kind; the older half goes when it is reached.
    private const val MAX_ENTRIES = 400

    private val fixes = ConcurrentHashMap<String, Pair<RideKindFix, Long>>()
    private val checked = ConcurrentHashMap<String, Long>()
    private val pending = ConcurrentHashMap.newKeySet<String>()
    private val counter = MutableStateFlow(0)

    // The run of HAFAS each ride was found as (its trip handle there), and what HAFAS knows
    // live of it (#274, see RideLive).
    private val links = ConcurrentHashMap<String, Pair<String, Long>>()
    private val lives = ConcurrentHashMap<String, RunLive>()
    private val livePending = ConcurrentHashMap.newKeySet<String>()

    // Live times are used this long after HAFAS sent them, and asked for anew after LIVE_REFRESH_MILLIS.
    private const val LIVE_KEEP_MILLIS = 10L * 60 * 1000
    private const val LIVE_REFRESH_MILLIS = 40L * 1000

    /** Counts up with every fix and every live answer found. */
    val version: StateFlow<Int> get() = counter

    /** The fix for the run [tripId]; null where none is known. */
    fun get(tripId: String, nowMillis: Long = System.currentTimeMillis()): RideKindFix? {
        val (fix, at) = fixes[tripId] ?: return null
        return fix.takeIf { nowMillis - at < FIX_MILLIS }
    }

    /** True when the run [tripId] has neither a fix nor an answer of late, nor a lookup running. */
    fun wanted(tripId: String, nowMillis: Long = System.currentTimeMillis()): Boolean =
        get(tripId, nowMillis) == null && tripId !in pending &&
            checked[tripId]?.let { nowMillis - it < CHECKED_MILLIS } != true

    /** Claims the lookup of [tripId]; false where one runs already. */
    fun claim(tripId: String): Boolean = pending.add(tripId)

    fun release(tripId: String) {
        pending.remove(tripId)
    }

    fun put(tripId: String, fix: RideKindFix, nowMillis: Long = System.currentTimeMillis()) {
        if (fixes.size >= MAX_ENTRIES) dropOlderHalf(fixes) { it.second }
        fixes[tripId] = fix to nowMillis
        checked.remove(tripId)
        // Atomic: two fixes found at once must both count, or the second is not taken in place.
        counter.update { it + 1 }
    }

    /** Notes that the ride [tripId] is the run [hafasTripId] of HAFAS. */
    fun link(tripId: String, hafasTripId: String, nowMillis: Long = System.currentTimeMillis()) {
        if (links.size >= MAX_ENTRIES) dropOlderHalf(links) { it.second }
        links[tripId] = hafasTripId to nowMillis
    }

    /** The trip handle at HAFAS of the ride [tripId]; null where it was not found there. */
    fun linkOf(tripId: String, nowMillis: Long = System.currentTimeMillis()): String? {
        val (id, at) = links[tripId] ?: return null
        return id.takeIf { nowMillis - at < FIX_MILLIS }
    }

    /** Takes in what HAFAS knows live of the ride [tripId] (joined with what was known, see mergedLive). */
    fun putLive(tripId: String, live: RunLive) {
        if (lives.size >= MAX_ENTRIES) dropOlderHalf(lives) { it.atMillis }
        lives.compute(tripId) { _, old -> mergedLive(old, live) }
        if (live.stops.isNotEmpty()) counter.update { it + 1 }
    }

    /** What HAFAS knew live of the ride [tripId] within the last minutes; null else. */
    fun live(tripId: String, nowMillis: Long = System.currentTimeMillis()): RunLive? =
        lives[tripId]?.takeIf { nowMillis - it.atMillis < LIVE_KEEP_MILLIS && it.stops.isNotEmpty() }

    /** True when the course of the ride [tripId] may be asked for at HAFAS again: it is linked, and nothing newer is known or asked for. */
    fun liveDue(tripId: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (linkOf(tripId, nowMillis) == null || tripId in livePending) return false
        val known = lives[tripId] ?: return true
        return !known.wholeRun || nowMillis - known.atMillis >= LIVE_REFRESH_MILLIS
    }

    fun claimLive(tripId: String): Boolean = livePending.add(tripId)

    fun releaseLive(tripId: String) {
        livePending.remove(tripId)
    }

    fun markChecked(tripId: String, nowMillis: Long = System.currentTimeMillis()) {
        if (checked.size >= MAX_ENTRIES) dropOlderHalf(checked) { it }
        checked[tripId] = nowMillis
    }

    /** Forgets everything; for tests. */
    fun clear() {
        fixes.clear()
        checked.clear()
        pending.clear()
        links.clear()
        lives.clear()
        livePending.clear()
    }

    private fun <V> dropOlderHalf(map: ConcurrentHashMap<String, V>, timeOf: (V) -> Long) {
        map.entries.sortedBy { timeOf(it.value) }.take(map.size / 2).forEach { map.remove(it.key) }
    }
}
