package io.github.jnbp.nextstop.data

import java.time.Duration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.OffsetDateTime
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sqrt

/** What a place is: a stop of the network, a point of interest, or a street address. */
enum class PlaceKind { Stop, Poi, Address }

/**
 * A place that can be the start or the end of a journey. Mostly a stop or station,
 * but also a point of interest or an address (see [kind]).
 */
data class Stop(
    val id: String,
    val name: String,
    val latitude: Double?,
    val longitude: Double?,
    val distanceMeters: Int?,
    /** Products that serve a stop, as named by the provider: suburban, subway, tram, bus, ferry, express, regional, coach. */
    val products: Set<String> = emptySet(),
    val kind: PlaceKind = PlaceKind.Stop,
    /** The postcode of an address, where the provider gives it apart from the name. Not stored. */
    val postcode: String? = null,
    /** Names of the areas (town, district, state) a place lies in, where the provider gives them. Not stored. */
    val areas: List<String> = emptyList(),
    /** A second line for a list of search results, as "Church · Street 12, District". Not stored. */
    val detail: String? = null,
) {
    /** Latitude and longitude as a pair, or null when one of them is missing. */
    val position: Pair<Double, Double>?
        get() = if (latitude != null && longitude != null) latitude to longitude else null
}

/**
 * The products a search can be limited to, in the order the app shows them: long-distance
 * trains ("express") and coaches ("coach") each have their own (see [classifyProduct]).
 */
val PRODUCT_KEYS: List<String> = listOf("suburban", "subway", "tram", "bus", "ferry", "regional", "express", "coach")

/**
 * How tight changes may be: the least time the search leaves between two rides. [VeryRisky]
 * also takes connections that only work because a ride is late, or that leave less than
 * nothing by the timetable.
 */
enum class TransferComfort(val minutes: Int) { Relaxed(8), Normal(4), Risky(0), VeryRisky(0) }

enum class WalkingSpeed(val apiValue: String) { Slow("slow"), Normal("normal"), Fast("fast") }

/** How barrier-free a journey must be; [apiValue] is null when it does not matter. */
enum class Accessibility(val apiValue: String?) { None(null), Partial("partial"), Complete("complete") }

/**
 * A place a journey has to pass: with [minutes] 0 it only leads through it, from 1 minute on the
 * traveller gets off there, stays that long and then goes on.
 */
data class Via(val place: Stop, val minutes: Int = 0)

// The longest stay at a place on the way, in minutes.
const val MAX_VIA_MINUTES = 120

/**
 * What the user set for every search: change time, products, walking speed and accessibility.
 * [via] belongs to one search only and is never stored.
 */
data class SearchOptions(
    val comfort: TransferComfort = TransferComfort.Normal,
    val products: Set<String> = PRODUCT_KEYS.toSet(),
    /**
     * "Langsamer unterwegs" (luggage, a pram, a walking aid): every walk takes [SLOWER_WALK_FACTOR]
     * as long, at every transfer level, and the providers are asked for slow walking.
     */
    val slower: Boolean = false,
    val accessibility: Accessibility = Accessibility.None,
    val via: Via? = null,
    /** Only rides that take a bicycle along. */
    val bike: Boolean = false,
    /**
     * "Bike combo": the traveller cycles the first and last mile and stretches no ride with a
     * bicycle covers, and takes the bicycle on the rides that do (see [BikeRules]).
     */
    val bikeCombo: Boolean = false,
    /** For one search only, never stored: the searches of "Jede Minute zählt" walk at a brisk pace. */
    val brisk: Boolean = false,
    /**
     * "Eigener Faktor" of "Langsamer unterwegs" (#198): every walk takes this many times as long,
     * in place of [SLOWER_WALK_FACTOR]; null where it is not chosen. See [walkFactorInUse].
     */
    val ownWalkFactor: Double? = null,
) {
    /** The factor on every walk: the own one where chosen, else that of [slower] (see walkFactor). */
    val walkFactorInUse: Double get() = walkFactor(slower, ownWalkFactor)

    /**
     * Only local and regional transport, as the Deutschlandticket covers it: no long-distance
     * trains, no coaches. Not a setting of its own but what the products say (see [withLocalOnly]).
     */
    val localOnly: Boolean get() = products.none { it in LONG_DISTANCE_PRODUCTS }

    /** The rides must take a bicycle along: asked for by itself or by the bike combo. */
    val carriesBike: Boolean get() = bike || bikeCombo

    /** The walking speed to ask the providers for: slow when [slower], fast for a [brisk] search, else normal. */
    val walkingSpeed: WalkingSpeed
        get() = when {
            ownWalkFactor != null -> walkingSpeedFor(walkFactorInUse).let { if (brisk && it == WalkingSpeed.Normal) WalkingSpeed.Fast else it }
            slower -> WalkingSpeed.Slow
            brisk -> WalkingSpeed.Fast
            else -> WalkingSpeed.Normal
        }
}

/**
 * The place on the way of a journey that was searched with one: where and how long the
 * traveller stays, and [afterLegs], the number of legs before the stay. Null there when the
 * provider led the journey through the place itself and it is not known which leg passes it.
 */
data class JourneyVia(val place: Stop, val minutes: Int, val afterLegs: Int?) {
    val via: Via get() = Via(place, minutes)
}

/** How much room a change between two rides leaves. */
enum class Tightness { Comfortable, Tight, Risky, VeryRisky }

/**
 * What a change asks of the traveller, which sets how much time it needs (see
 * [neededMinutes]): [SamePlatform] across or along one platform, [SameStation] to another
 * platform of the station, [Walk] on foot to another station.
 */
enum class ChangeKind { SamePlatform, SameStation, Walk }

// Minutes a change needs: across one platform, to another platform of a station of U-Bahn and
// S-Bahn, and to another platform of a large rail station with regional or long-distance trains.
// A walk needs its own minutes and this one more.
const val SAME_PLATFORM_NEED = 1L
const val SAME_STATION_NEED = 3L
const val RAIL_STATION_NEED = 5L
const val WALK_EXTRA_NEED = 1L

// Minutes to spare beyond what a change needs from which it is relaxed or normal; from 0 it
// is risky, below 0 very risky. Set so that a change to another platform (3 minutes needed)
// is normal from 4 minutes and relaxed from 8, the change times of the provider's searches at
// those levels (TransferComfort): what a change needs already holds getting off and the way,
// so normal asks one minute more, not two (with two, every 4-minute change from the U-Bahn to
// a bus outside was "knapp").
// These are the defaults: the settings can change both (see [normalSpare], [relaxedSpare]).
const val RELAXED_SPARE = 5L
const val NORMAL_SPARE = 1L

// The kinds of transport of a large rail station.
private val RAIL_PRODUCTS = setOf("express", "regional")

/**
 * The minutes a change of [kind] needs: 1 across one platform, 3 to another platform (5 at a
 * [largeStation] with regional or long-distance trains), and for [walkMinutes] on foot those
 * minutes and one more; the most of these counts.
 */
fun neededMinutes(kind: ChangeKind, walkMinutes: Long = 0, largeStation: Boolean = false): Long {
    val base = when (kind) {
        ChangeKind.SamePlatform -> SAME_PLATFORM_NEED
        ChangeKind.SameStation -> if (largeStation) RAIL_STATION_NEED else SAME_STATION_NEED
        ChangeKind.Walk -> WALK_EXTRA_NEED
    }
    return if (walkMinutes > 0) maxOf(base, walkMinutes + WALK_EXTRA_NEED) else base
}

/**
 * The category of a change by the minutes it leaves beyond what it needs ([spare]), by the
 * live times: relaxed from [relaxedSpare], normal from [normalSpare] (5 and 1 unless the
 * settings change them), risky (tight) from 0, and very risky ("Sprint")
 * below 0: by the rule of what a change needs (walk included) it cannot be made, only by
 * running. The timetable alone plays no part: a delayed ride counts with its live time.
 */
fun spareTightness(spare: Long): Tightness = when {
    spare >= relaxedSpare -> Tightness.Comfortable
    spare >= normalSpare -> Tightness.Tight
    spare >= 0 -> Tightness.Risky
    else -> Tightness.VeryRisky
}

/**
 * The category of a change by walking pace: [gap] minutes from the live arrival to the live
 * departure, [need] minutes at the usual pace with its buffer (see [changeNeed]) and
 * [briskNeed] minutes at a brisk pace without buffer (see [changeBriskNeed]):
 *
 * - relaxed from [relaxedSpare] and normal from [normalSpare] minutes beyond [need], as before;
 * - knapp ("Risky") where the change still works at a brisk pace: [gap] at least [briskNeed]
 *   and at least [MIN_KNAPP_MINUTES];
 * - Sprint ("VeryRisky") otherwise: only by running or by luck, and every change of 0 or 1 minute.
 */
fun paceTightness(gap: Long, need: Long, briskNeed: Long): Tightness = when {
    gap - need >= relaxedSpare -> Tightness.Comfortable
    gap - need >= normalSpare -> Tightness.Tight
    // A brisk pace never needs more than the usual one with its buffer.
    gap >= maxOf(minOf(briskNeed, need), MIN_KNAPP_MINUTES) -> Tightness.Risky
    else -> Tightness.VeryRisky
}

/**
 * True when the change between [arriving] and [next] is one at a large rail station: one of the
 * rides is a regional or long-distance train, or the station has them and the change is
 * between two of its platforms. A change between a train and a bus or tram stop in the street
 * does not pass the platforms of regional trains: the station having them makes it no longer
 * (Lichterfelde Ost: S25 to the bus 184), so for it they do not count.
 */
fun largeStation(arriving: Leg, next: Leg): Boolean {
    if (arriving.product in RAIL_PRODUCTS || next.product in RAIL_PRODUCTS) return true
    if (modeFamily(arriving.product) in STOP_FAMILIES || modeFamily(next.product) in STOP_FAMILIES) return false
    return arriving.toPlace?.products.orEmpty().any { it in RAIL_PRODUCTS } ||
        next.fromPlace?.products.orEmpty().any { it in RAIL_PRODUCTS }
}

/**
 * Tells whether the dataset of change positions marks a change between two rides as one on
 * the same platform. Set by the interface once the dataset can be asked; until then no
 * change counts as one by the dataset, only by its platform numbers.
 */
@Volatile
var samePlatformRecord: (Leg, Leg) -> Boolean = { _, _ -> false }

/**
 * How tight a change is with [gap] minutes from arrival to departure, of which [walkMinutes]
 * on foot, by what a change of [kind] needs (see [neededMinutes] and [spareTightness]).
 */
fun transferTightness(
    gap: Long,
    kind: ChangeKind = ChangeKind.SameStation,
    walkMinutes: Long = 0,
    largeStation: Boolean = false,
): Tightness {
    val need = neededMinutes(kind, walkMinutes, largeStation)
    return paceTightness(gap, need, briskWalkNeed(walkMinutes) ?: need)
}

/** The level of [TransferComfort] a change of [tightness] belongs to. */
fun transferComfort(tightness: Tightness): TransferComfort = when (tightness) {
    Tightness.Comfortable -> TransferComfort.Relaxed
    Tightness.Tight -> TransferComfort.Normal
    Tightness.Risky -> TransferComfort.Risky
    Tightness.VeryRisky -> TransferComfort.VeryRisky
}

/** A platform number in one form: without "Gleis"/"Platform" and blanks, in small letters. */
private fun platformKey(platform: String?): String? =
    platform?.lowercase()?.replace(Regex("^(gleis|gl\\.|platform|bstg\\.?|steig)\\s*"), "")?.trim()?.takeIf { it.isNotEmpty() }

/** True when the two places are one station: by the VBB number where both ids hold one, else by name. */
fun sameStation(firstPlace: Stop?, firstName: String, secondPlace: Stop?, secondName: String): Boolean {
    val first = firstPlace?.id?.let { normalStationId(it) }
    val second = secondPlace?.id?.let { normalStationId(it) }
    fun vbb(id: String?) = id != null && id.length == 9 && id.startsWith("900") && id.all { it.isDigit() }
    if (vbb(first) && vbb(second)) return first == second
    val a = stationNameKey(firstPlace?.name ?: firstName)
    if (a.isNotEmpty() && a == stationNameKey(secondPlace?.name ?: secondName)) return true
    // One station of two feeds or under two names ("Francfort sur le Main" beside Frankfurt
    // (Main) Hbf, "Hauptbahnhof, Saarbrücken" and "Saarbrücken Hbf"): see StationIdentity.
    return firstPlace != null && secondPlace != null && StationIdentity.same(firstPlace, secondPlace)
}

/**
 * The families of transport whose platforms can be shared. S-Bahn, regional and long-distance
 * trains are one family: their tracks are numbered together at a station. U-Bahn, tram, bus
 * and ferry each have platforms of their own.
 */
enum class ModeFamily { Rail, Subway, Tram, Bus, Ferry, Other }

/** The family of a product as named by the provider (see [PRODUCT_KEYS]). */
fun modeFamily(product: String): ModeFamily = when (product) {
    "suburban", "regional", "express" -> ModeFamily.Rail
    "subway" -> ModeFamily.Subway
    "tram" -> ModeFamily.Tram
    "bus", "coach" -> ModeFamily.Bus
    "ferry" -> ModeFamily.Ferry
    else -> ModeFamily.Other
}

/** True when both places carry an id and it is the very same stop, not only the same station. */
fun sameStopId(first: Stop?, second: Stop?): Boolean {
    val a = first?.id?.trim().orEmpty()
    return a.isNotEmpty() && a == second?.id?.trim()
}

/** True for a change between S-Bahn and U-Bahn, either way: two separate systems. */
fun suburbanSubwayChange(arriving: Leg, next: Leg): Boolean =
    (arriving.product == "suburban" && next.product == "subway") ||
        (arriving.product == "subway" && next.product == "suburban")

/**
 * True when [arriving] ends and [next] starts on the same platform by the platform numbers of
 * the timetable (or live data): only where both rides belong to one [ModeFamily] and stop at
 * the very same stop id. Equal numbers of different systems at one station (U-Bahn track 2
 * and S-Bahn track 2) or of different stops of a station complex say nothing: they are
 * separate platforms. S-Bahn and U-Bahn are never on one platform by the numbers.
 */
fun samePlatformByNumbers(arriving: Leg, next: Leg): Boolean {
    if (arriving.walking || next.walking) return false
    val family = modeFamily(arriving.product)
    if (family == ModeFamily.Other || family != modeFamily(next.product)) return false
    if (!sameStopId(arriving.toPlace, next.fromPlace)) return false
    val a = platformKey(arriving.arrivalPlatform) ?: return false
    val b = platformKey(next.departurePlatform) ?: return false
    return a == b
}

/**
 * True when a record of the dataset of change positions that says "same platform" may be
 * believed for the change from [arriving] to [next]: both at one station, and both rides of
 * one [ModeFamily], or S-Bahn and U-Bahn, where the dataset names the few cross-platform
 * interchanges of the two systems explicitly. Any other mix is never one platform.
 */
fun samePlatformRecordAllowed(arriving: Leg, next: Leg): Boolean {
    if (arriving.walking || next.walking) return false
    if (!sameStation(arriving.toPlace, arriving.to, next.fromPlace, next.from)) return false
    val family = modeFamily(arriving.product)
    return (family != ModeFamily.Other && family == modeFamily(next.product)) || suburbanSubwayChange(arriving, next)
}

/**
 * True when the change from [arriving] to [next] stays on one platform: by the platform
 * numbers at the very same stop (see [samePlatformByNumbers]), or by a record of the dataset
 * where one may be believed (see [samePlatformRecordAllowed]). [record] asks the dataset.
 */
fun samePlatformChange(
    arriving: Leg,
    next: Leg,
    record: (Leg, Leg) -> Boolean = samePlatformRecord,
): Boolean = samePlatformByNumbers(arriving, next) ||
    (samePlatformRecordAllowed(arriving, next) && runCatching { record(arriving, next) }.getOrDefault(false))

/**
 * The kind of the change from [arriving] to [next] with [walkMinutes] on foot between them:
 * a walk to another station, the same platform (see [samePlatformChange]), or another
 * platform of the same station.
 */
fun changeKind(arriving: Leg, next: Leg, walkMinutes: Long = 0): ChangeKind = when {
    walkMinutes > 0 && !sameStation(arriving.toPlace, arriving.to, next.fromPlace, next.from) -> ChangeKind.Walk
    samePlatformChange(arriving, next) -> ChangeKind.SamePlatform
    else -> ChangeKind.SameStation
}

/**
 * How tight the change from [arriving] to [next] is, with [walkMinutes] on foot between
 * them, by walking pace (see [paceTightness]): what it needs at the usual pace with its
 * buffer ([changeNeed]) and at a brisk pace ([changeBriskNeed]).
 */
fun changeTightness(arriving: Leg, next: Leg, walkMinutes: Long = 0): Tightness =
    paceTightness(changeGapMinutes(arriving, next), changeNeed(arriving, next, walkMinutes), changeBriskNeed(arriving, next, walkMinutes))

/** The minutes the change from [arriving] to [next] leaves beyond what it needs, by the live times. */
fun changeSpare(arriving: Leg, next: Leg, walkMinutes: Long = 0): Long =
    changeGapMinutes(arriving, next) - changeNeed(arriving, next, walkMinutes)

/**
 * The minutes from the live arrival of [arriving] to the live departure of [next]: the time
 * the traveller has for the change. Whole minutes, rounded down (also below 0).
 */
fun changeGapMinutes(arriving: Leg, next: Leg): Long =
    Math.floorDiv(Duration.between(arriving.arrival, next.departure).seconds, 60L)

/**
 * The time of one change, the one source for every place that shows it (connection detail,
 * the choice of connecting departures, the trip companion and the watch): [minutes] from the
 * live arrival to the live departure (see [changeGapMinutes]), [need] what the change needs
 * (see [changeNeed]), and its [tightness].
 */
data class ChangeTime(val minutes: Long, val need: Long, val tightness: Tightness, val walk: Long = 0) {
    /** Minutes beyond what the change needs at the usual pace; below 0 only at a brisk pace (knapp) or running (Sprint). */
    val spare: Long get() = minutes - need

    /**
     * False ("too late") where the connecting ride leaves before the traveller can be there:
     * before the arrival, or before the walk ([walk]) to it is done.
     */
    val possible: Boolean get() = minutes >= walk
}

/**
 * The [ChangeTime] of the change from [arriving] to [next] with [walkMinutes] on foot; its
 * category is [rating] where the journey rates it (see [journeyChangeRatings]), else the
 * station rule.
 */
fun changeTime(arriving: Leg, next: Leg, walkMinutes: Long = 0, rating: Tightness? = null): ChangeTime {
    val minutes = changeGapMinutes(arriving, next)
    val need = changeNeed(arriving, next, walkMinutes)
    val tightness = rating ?: paceTightness(minutes, need, changeBriskNeed(arriving, next, walkMinutes))
    return ChangeTime(minutes, need, tightness, walkMinutes.coerceAtLeast(0))
}

/**
 * The minutes the change from [arriving] to [next] needs: by the walk measured over the
 * walking network of OpenStreetMap where it is known (see [exactWalkNeed]), by the stop
 * positions of a bus or tram change (see [stopSpotNeed]) or the platform of a train and the
 * stop of a bus or tram (see [platformStopNeed]), else by its kind (see [neededMinutes]).
 */
fun changeNeed(arriving: Leg, next: Leg, walkMinutes: Long = 0): Long =
    exactWalkNeed(arriving, next)
        ?: stopSpotNeed(arriving, next, walkMinutes)
        ?: platformStopNeed(arriving, next)
        ?: neededMinutes(changeKind(arriving, next, walkMinutes), walkMinutes, largeStation(arriving, next))

/**
 * The seconds on foot of the change from [arriving] to [next] over the walking network of
 * OpenStreetMap, between the platforms of a station complex or a platform and a bus or tram
 * stop near it (see [OsmStations.transferWalkSeconds]); step-free where [stepFreeWalks] is set.
 * Set by the interface once the data is loaded; until then none is known.
 */
@Volatile
var transferWalkSeconds: (arriving: Leg, next: Leg) -> Int? = { _, _ -> null }

/** True when walks of changes count without steps and escalators (the accessibility setting is not "none"). */
@Volatile
var stepFreeWalks: Boolean = false
    set(value) {
        if (field == value) return
        field = value
        stepFreeFlow.value = value
        ratingSettingsChanged()
    }

private val stepFreeFlow = MutableStateFlow(false)

/** [stepFreeWalks] to observe: the interface follows a change of the setting with it (see rememberStepFree). */
val stepFreeSetting: StateFlow<Boolean> get() = stepFreeFlow

// A change measured over the walking network needs its walk and this much more (getting off,
// finding the way; the default of [changeBufferSeconds], which the settings can change), and
// never less than [EXACT_CHANGE_MIN_NEED] between two places.
const val EXACT_WALK_EXTRA_SECONDS = 30L
const val EXACT_CHANGE_MIN_NEED = 2L

/**
 * What a change needs by its walk measured over the walking network (see
 * [transferWalkSeconds]), at the walking speed set (see [pacedSeconds]): 1 minute on the same
 * platform, else the walk and half a minute more, rounded up, at least 2. The provider's walk does not count here: it is measured from
 * the middle of the station, or stretched over the whole wait (BVG HAFAS at Lichterfelde Ost
 * and Rathaus Steglitz). Null where the walk is not known; then the rules of thumb decide.
 */
fun exactWalkNeed(arriving: Leg, next: Leg, walk: (Leg, Leg) -> Int? = transferWalkSeconds): Long? {
    if (arriving.walking || next.walking) return null
    val seconds = runCatching { walk(arriving, next) }.getOrNull()?.let { pacedSeconds(it) } ?: return null
    if (seconds <= 0) return SAME_PLATFORM_NEED
    return maxOf(EXACT_CHANGE_MIN_NEED, ceil((seconds + changeBufferSeconds) / 60.0).toLong())
}

/** Whole minutes of a walk of [seconds], rounded up. */
fun walkSecondsToMinutes(seconds: Int): Long = ceil(seconds / 60.0).toLong()

// Two stop positions this close are one stop: the change needs no walk.
const val SAME_SPOT_METERS = 12.0

// The way on foot between two stop positions is this much longer than the straight line,
// walked at this pace.
private const val SPOT_DETOUR = 1.3
private const val SPOT_WALK_METERS_PER_MINUTE = 75.0

/**
 * The metres between where [arriving] is left and where [next] is boarded, by their stop
 * positions from OpenStreetMap; null unless both are known.
 */
fun stopSpotMeters(arriving: Leg, next: Leg): Double? {
    val from = arriving.alightingSpot ?: return null
    val to = next.boardingSpot ?: return null
    return spotMeters(from.position, to.position)
}

/**
 * The metres and minutes on foot of the way [way] as drawn: its length, and the minutes for
 * that length at an easy pace (a way with only its two ends counts [SPOT_DETOUR] longer, as a
 * way on foot is). For the watch, whose change card shows both: from one way, not the
 * straight line beside the minutes of the provider. Null with fewer than two points.
 */
fun drawnWalk(way: List<Pair<Double, Double>>): Pair<Int, Long>? {
    if (way.size < 2) return null
    val meters = way.zipWithNext().sumOf { (a, b) -> spotMeters(a, b) }
    val walked = if (way.size == 2) meters * SPOT_DETOUR else meters
    val minutes = if (meters <= SAME_SPOT_METERS) 0L else ceil(walked * walkSpeedFactor / SPOT_WALK_METERS_PER_MINUTE).toLong().coerceAtLeast(1L)
    return Math.round(walked).toInt() to minutes
}

/**
 * The minutes on foot between two stop positions [meters] apart at the walking speed set (see
 * [walkSpeedFactor]); 0 for one stop (see [SAME_SPOT_METERS]).
 */
fun spotWalkMinutes(meters: Double, factor: Double = walkSpeedFactor): Long =
    if (meters <= SAME_SPOT_METERS) 0L else ceil(meters * SPOT_DETOUR * factor / SPOT_WALK_METERS_PER_MINUTE).toLong().coerceAtLeast(1L)

/**
 * What a change between two rides of bus or tram needs where the provider has no walk
 * between them and OpenStreetMap knows both stop positions (see [stopSpotMeters]): 1 minute
 * at the same stop, else the walk between the two positions and one minute more. Null where
 * this does not apply and the station rule decides.
 */
fun stopSpotNeed(arriving: Leg, next: Leg, walkMinutes: Long = 0): Long? {
    if (walkMinutes > 0 || arriving.walking || next.walking) return null
    if (modeFamily(arriving.product) !in STOP_FAMILIES || modeFamily(next.product) !in STOP_FAMILIES) return null
    val meters = stopSpotMeters(arriving, next) ?: return null
    val walk = spotWalkMinutes(meters)
    return if (walk == 0L) SAME_PLATFORM_NEED else walk + WALK_EXTRA_NEED
}

// The families of transport that stop at stop positions in the street.
private val STOP_FAMILIES = setOf(ModeFamily.Bus, ModeFamily.Tram)

// The families of transport that stop at the platforms of a station.
private val PLATFORM_FAMILIES = setOf(ModeFamily.Rail, ModeFamily.Subway)

/**
 * The platforms of the station where the train or U-Bahn [ride] is boarded ([boarding]) or
 * left, from OpenStreetMap: those of its line, or else of its platform number. Set by the
 * interface once the data is loaded; until then none is known.
 */
@Volatile
var ridePlatforms: (ride: Leg, boarding: Boolean) -> List<OsmPlatform> = { _, _ -> emptyList() }

// A street stop farther than this from the platform is not judged by the straight line.
private const val PLATFORM_STOP_MAX_METERS = 400.0

// A change between a platform and a stop in the street needs, beyond the walk on the level and
// the buffer of every change ([changeBufferSeconds]), this much for the stairs, escalator or
// lift between platform and street; with the default buffer 60 s in all.
const val PLATFORM_STOP_STAIRS_SECONDS = 30.0

/**
 * The minutes a change between a platform and a stop position [meters] apart needs: the walk
 * at the pace set ([walkSpeedFactor], the way [SPOT_DETOUR] longer than the straight line) and
 * [changeBufferSeconds] and [PLATFORM_STOP_STAIRS_SECONDS] for getting off and the stairs,
 * rounded up once, at least
 * [EXACT_CHANGE_MIN_NEED]. (Before, the walk was rounded up to whole minutes, one minute was
 * added and the result was raised to 3 whatever the walk: a stop 50 m from the platform needed
 * 3 minutes.)
 */
fun platformStopMinutes(meters: Double, factor: Double = walkSpeedFactor): Long {
    val seconds = meters * SPOT_DETOUR * factor / SPOT_WALK_METERS_PER_MINUTE * 60.0 + changeBufferSeconds + PLATFORM_STOP_STAIRS_SECONDS
    return maxOf(EXACT_CHANGE_MIN_NEED, ceil(seconds / 60.0 - 1e-9).toLong())
}

/**
 * What a change between a train or U-Bahn and a bus or tram needs where the stop position of
 * the bus or tram is known: the walk from the nearest point of the platform of the train (see
 * [ridePlatforms]) to the stop position, else, where OpenStreetMap does not know that
 * platform, from where the way of the train ends or starts at the station (its stop), see
 * [platformStopMinutes]. The provider measures its walk from the middle of the station or
 * stretches it over the whole wait (BVG HAFAS at Lichterfelde Ost: the whole wait from the S25
 * to the bus 184 as the walk), so this comes before its walk. Null where a position is missing
 * or the stop lies far away; then the walk of the provider and the station rule decide.
 */
fun platformStopNeed(arriving: Leg, next: Leg, platforms: (Leg, Boolean) -> List<OsmPlatform> = ridePlatforms): Long? {
    if (arriving.walking || next.walking) return null
    val from = modeFamily(arriving.product)
    val to = modeFamily(next.product)
    val (train, boarding, spot) = when {
        from in PLATFORM_FAMILIES && to in STOP_FAMILIES -> Triple(arriving, false, next.boardingSpot)
        from in STOP_FAMILIES && to in PLATFORM_FAMILIES -> Triple(next, true, arriving.alightingSpot)
        else -> return null
    }
    if (spot == null) return null
    val axes = runCatching { platforms(train, boarding) }.getOrDefault(emptyList())
    val meters = axes.minOfOrNull { segmentMeters(spot.position, it.a, it.b) }
        ?: (if (boarding) train.path.firstOrNull() else train.path.lastOrNull())?.let { spotMeters(spot.position, it) }
        ?: return null
    if (meters > PLATFORM_STOP_MAX_METERS) return null
    return platformStopMinutes(meters)
}

/** Metres from [point] to the nearest point of the line from [a] to [b], flat over short distances. */
fun segmentMeters(point: Pair<Double, Double>, a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
    val scale = cos(Math.toRadians(point.first))
    val ax = (a.second - point.second) * scale
    val ay = a.first - point.first
    val dx = (b.second - a.second) * scale
    val dy = b.first - a.first
    val length = dx * dx + dy * dy
    val share = if (length <= 0.0) 0.0 else (-(ax * dx + ay * dy) / length).coerceIn(0.0, 1.0)
    val x = ax + dx * share
    val y = ay + dy * share
    return sqrt(x * x + y * y) * 111_320.0
}

/** Metres between two points (latitude, longitude), flat over short distances. */
internal fun spotMeters(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
    val north = (b.first - a.first) * 111_320.0
    val east = (b.second - a.second) * 111_320.0 * cos(Math.toRadians((a.first + b.first) / 2))
    return sqrt(north * north + east * east)
}

// A walk of a change takes at most this much longer than its length at an easy pace: stairs,
// lifts and the way out of a station.
const val WALK_SLACK_MINUTES = 2L

/**
 * The minutes the walk [walk] of a change takes, for what the change needs: those of the
 * provider, but never more than the walk measured over the walking network of OpenStreetMap
 * (see [transferWalkSeconds]) or, where that is not known, than its length allows (see [WALK_SLACK_MINUTES]), by the metres
 * the provider gives or else by its way on the map. A provider may stretch a walk between two
 * rides over the whole wait for the next one (BVG HAFAS at S+U Rathaus Steglitz: 10 minutes
 * for about 200 m from the S-Bahn to the bus); counted in full, a change of 10 minutes would
 * need 11 and look risky.
 */
fun changeWalkMinutes(walk: Leg, arriving: Leg? = null, next: Leg? = null): Long {
    val provider = Duration.between(walk.departure, walk.arrival).toMinutes().coerceAtLeast(0)
    // The walk measured over the walking network, where it is known, before any estimate.
    if (arriving != null && next != null && !arriving.walking && !next.walking) {
        runCatching { transferWalkSeconds(arriving, next) }.getOrNull()?.let { seconds ->
            return minOf(provider, walkSecondsToMinutes(pacedSeconds(seconds)))
        }
    }
    val known = walk.walkMeters?.takeIf { it > 0 }?.toDouble() ?: pathMeters(walk.path)
    // Without a length of its own the walk is judged by the two rides it joins.
    val meters = known ?: if (arriving != null && next != null) ridesGapMeters(arriving, next) else null
    if (meters != null) {
        val estimate = ceil(meters * SPOT_DETOUR * walkSpeedFactor / SPOT_WALK_METERS_PER_MINUTE).toLong() + WALK_SLACK_MINUTES
        return minOf(provider, estimate)
    }
    // Two rides at one station and no length at all: a walk inside a station is short.
    if (arriving != null && next != null && sameStation(walk, arriving, next)) return minOf(provider, STATION_WALK_CAP_MINUTES)
    return provider
}

/**
 * The most a walk between two rides at one station counts where nothing tells its length:
 * stairs, lifts and the way to another platform or stop outside. A longer walk of the
 * provider there is a stretch over the wait (see [changeWalkMinutes]).
 */
const val STATION_WALK_CAP_MINUTES = 6L

/**
 * The straight line in metres from where [arriving] ends to where [next] starts (stop
 * positions or platform coordinates of the legs, else the coordinates of their stations);
 * null where a position is missing or both lie at the same point, which says nothing.
 */
private fun ridesGapMeters(arriving: Leg, next: Leg): Double? {
    val from = arriving.alightingSpot?.position ?: arriving.path.lastOrNull() ?: arriving.toPlace?.position ?: return null
    val to = next.boardingSpot?.position ?: next.path.firstOrNull() ?: next.fromPlace?.position ?: return null
    return spotMeters(from, to).takeIf { it > 0.0 }
}

/** True when the walk joins two rides at one station: equal normalised station ids, at either end of the walk. */
private fun sameStation(walk: Leg, arriving: Leg, next: Leg): Boolean {
    // By the stop place: the last part of a platform id alone ("…:3:4") says nothing.
    fun same(a: Stop?, b: Stop?) = a != null && b != null && StationIdentity.sameStopPlace(a, b)
    return same(arriving.toPlace, next.fromPlace) || same(walk.fromPlace, walk.toPlace)
}

/** The length of a way in metres; null for a way of fewer than two points. */
private fun pathMeters(path: List<Pair<Double, Double>>): Double? =
    if (path.size < 2) null else path.zipWithNext().sumOf { (a, b) -> spotMeters(a, b) }

/** One departure of a line at a stop. Times are null when the provider sends none. */
data class Departure(
    val line: String,
    val product: String,
    val direction: String,
    val time: OffsetDateTime?,
    val plannedTime: OffsetDateTime?,
    val delaySeconds: Int?,
    val platform: String?,
    val cancelled: Boolean,
    /** Handle of the vehicle run this departure belongs to; null when the provider gives none. */
    val tripId: String? = null,
    /** Where the vehicle is heading, as a place a search can lead to; null when the provider names none. */
    val destination: Stop? = null,
    /**
     * The stop it leaves from, where a list mixes several stops (departures of all stops
     * nearby); null where the list belongs to one stop.
     */
    val stop: Stop? = null,
    /** An extra run the provider announces as additional ("Zusatzfahrt"), not in the timetable. */
    val additional: Boolean = false,
    /**
     * The line of a cancelled run this additional run stands in for (same line, way, platform
     * and time; see [mergeReplacements]); null where it replaces none.
     */
    val replaces: String? = null,
    /** The disruption notices of the provider for this run; empty where it sends none. */
    val warnings: List<Warning> = emptyList(),
    /** The colour of the line as the provider sends it with this run (ARGB); null where it sends none. */
    val providerColor: Int? = null,
    /** The colour of the text on [providerColor] as the provider sends it (ARGB); null where it sends none. */
    val providerTextColor: Int? = null,
    /**
     * Where the run leaves (the stop of the board), as latitude and longitude, for what
     * depends on the place, such as the colour of its line; null where it is not known.
     */
    val at: Pair<Double, Double>? = null,
    /** How full the run is expected to be, where the provider says so (see [Occupancy]). */
    val occupancy: Occupancy? = null,
)

/** A vehicle that reaches a stop: which run, which line, and when. */
data class Arrival(val tripId: String?, val line: String, val product: String, val time: OffsetDateTime?)

/**
 * A disruption notice of the provider for a ride. Either part may be empty, never both.
 * [text] is plain text without link placeholders; the links are in [links].
 */
data class Warning(
    val summary: String,
    val text: String,
    /** The provider's own id of the notice, the same on every run that carries it; null where it sends none. */
    val id: String? = null,
    /** From when until when the notice holds, where the provider says so. */
    val validFrom: OffsetDateTime? = null,
    val validUntil: OffsetDateTime? = null,
    /** The links of the notice ("more", a map), taken out of its text (see parseNoticeText). */
    val links: List<NoticeLink> = emptyList(),
    /** The stop of the departure that carried it; null for the notices of a journey. */
    val stop: Stop? = null,
    /** The provider's code of the remark (HAFAS "bf" for step-free access, see [noticeKind]); null where it sends none. */
    val code: String? = null,
)

/**
 * A stop a ride calls at on its way. Times are null where the provider sends none.
 * [place] is the stop as a place a search can start from or lead to.
 */
data class Stopover(
    val name: String,
    val arrival: OffsetDateTime?,
    val departure: OffsetDateTime?,
    val place: Stop? = null,
    /** Seconds behind the timetable at this stop; null without live data. */
    val delaySeconds: Int? = null,
    /** The times of the timetable, where the provider sends them apart from the live ones. */
    val plannedArrival: OffsetDateTime? = null,
    val plannedDeparture: OffsetDateTime? = null,
    /**
     * True when [arrival] and [departure] are not the provider's prognosis but an estimate of
     * the app, because that prognosis was implausible (see [plausibleStops]). Never set by a source.
     */
    val estimated: Boolean = false,
) {
    val time: OffsetDateTime? get() = arrival ?: departure
}

/** One vehicle run from its first to its last stop: what it is, where it calls and its way on the map. */
data class TripCourse(
    val tripId: String,
    val line: String?,
    val product: String,
    val direction: String?,
    val stops: List<Stopover>,
    val path: List<Pair<Double, Double>>,
    /** The colour of the line as the provider sends it with this trip (ARGB); null where it sends none. */
    val providerColor: Int? = null,
    /** The colour of the text on [providerColor] as the provider sends it (ARGB); null where it sends none. */
    val providerTextColor: Int? = null,
    /**
     * The hops of [path] that are only air lines between stations, by the index of the stop they
     * leave (see airHops): a vehicle there stands at a station instead of crossing the fields.
     * Found on the whole way as the provider sent it, before it is thinned for the map.
     */
    val airHops: Set<Int> = emptySet(),
)

/** One section of a journey: a ride on a line or a walk. */
data class Leg(
    val line: String?,
    val product: String,
    val walking: Boolean,
    val from: String,
    val to: String,
    val departure: OffsetDateTime,
    val plannedDeparture: OffsetDateTime?,
    val arrival: OffsetDateTime,
    val plannedArrival: OffsetDateTime?,
    val departurePlatform: String?,
    val arrivalPlatform: String?,
    val direction: String?,
    val cancelled: Boolean,
    /** True when the times come from live data, false when they are only the timetable. */
    val realtime: Boolean = false,
    /**
     * The way of this leg on the map, as latitude and longitude; empty when none is known.
     * A walk has no shape from the provider: it is the straight line between its two ends.
     */
    val path: List<Pair<Double, Double>> = emptyList(),
    /** Where this leg starts, as a place a new search can start from; null when the provider names none. */
    val fromPlace: Stop? = null,
    /** Where this leg ends, as a place a new search can lead to; null when the provider names none. */
    val toPlace: Stop? = null,
    val warnings: List<Warning> = emptyList(),
    /** All stops of a ride from boarding to getting off; empty unless they were asked for. */
    val stopovers: List<Stopover> = emptyList(),
    /** Handle of the vehicle run this ride is part of; null for walks and when the provider gives none. */
    val tripId: String? = null,
    /**
     * For a walk before a ride: the stop position ("Pos.") or station entrance from OpenStreetMap
     * it leads to, which is also the end of its [path]; null where none is known (see [OsmStations.refine]).
     */
    val walkTarget: WalkTarget? = null,
    /**
     * For a walk after a ride of the U-Bahn, S-Bahn or a train: the exit of its station from
     * OpenStreetMap it leaves by, toward where the walk goes; also the start of its [path].
     * Null where none is known or the walk stays in the station (see [OsmStations.refine]).
     */
    val walkExit: WalkTarget? = null,
    /**
     * For a bus or tram ride: the stop position from OpenStreetMap where it is boarded, which
     * is also the start of its [path]; null where none is known (see [OsmStations.refine]).
     * Its label is the "Pos." number, or null where the position has none.
     */
    val boardingSpot: WalkTarget? = null,
    /** For a bus or tram ride: the stop position from OpenStreetMap where it is left, the end of its [path]. */
    val alightingSpot: WalkTarget? = null,
    /** An extra run the provider announces as additional ("Zusatzfahrt"), not in the timetable. */
    val additional: Boolean = false,
    /** For a walk: its length in metres where the provider gives it; null else. */
    val walkMeters: Int? = null,
    /**
     * True when [arrival] is the app's estimate instead of the provider's implausible prognosis
     * (see [plausible]); never set by a source.
     */
    val arrivalEstimated: Boolean = false,
    /** The provider says this ride goes on in the vehicle of the ride before ("stay seated"). */
    val stayOnBoard: Boolean = false,
    /**
     * Where this ride was joined from two or more rides of the provider that are one ride for
     * the traveller (see [mergeThroughRuns]): each later part with its handle and line. Empty
     * for a ride as the provider sent it.
     */
    val throughRuns: List<ThroughRun> = emptyList(),
    /**
     * For a leg on the street ([walking]): it is ridden by bicycle, not walked (the bike combo,
     * see [SearchOptions.bikeCombo]); its [path] follows ways for bicycles.
     */
    val cycling: Boolean = false,
    /** The colour of the line as the provider sends it with this ride (ARGB); null where it sends none. */
    val providerColor: Int? = null,
    /** The colour of the text on [providerColor] as the provider sends it (ARGB); null where it sends none. */
    val providerTextColor: Int? = null,
    /** How full the ride is expected to be, where the provider says so (see [Occupancy]). */
    val occupancy: Occupancy? = null,
    /** The hints of the provider shown as icons: bicycles allowed, step-free (see [rideFeatures]). */
    val features: Set<RideFeature> = emptySet(),
    /** The provider's own words about bicycles on this ride, shown behind the bicycle chip (#248, see [bikeRemarkText]); null where it sends none. */
    val bikeNote: String? = null,
    /**
     * The platform of the timetable where the provider sends it apart from the live one in
     * [departurePlatform]; null where it sends none. A difference is a change of platform (see PlatformWatch).
     */
    val plannedDeparturePlatform: String? = null,
    /** The platform of the timetable where the ride arrives, as [plannedDeparturePlatform]. */
    val plannedArrivalPlatform: String? = null,
    /**
     * Where this ride was joined from a change into a later vehicle of the same line and
     * direction that staying seated beats (see [staysSeatedAhead]): the legs as the provider
     * sent them, the ride and the later ride. Empty else. The times
     * from the change on are those of the later vehicle, at the latest, until another journey
     * of the same search tells those of the vehicle itself (see [withSeatedTimes]).
     */
    val seatedParts: List<Leg> = emptyList(),
    /**
     * The name the provider's timetable gave this ride where the app named it after another
     * source that knows the same run ("ICE 171" for a ride shown as "RJ 171", see RideKinds);
     * null else. Ids and keys of journeys go by it, so a journey keeps them when it is renamed.
     */
    val timetableLine: String? = null,
    /**
     * True when [realtime] comes from a HAFAS source that knows the same run, standing in for a
     * provider without live data for it (#274, see RideLive); such times may be replaced by newer ones.
     */
    val hafasLive: Boolean = false,
) {
    val departureDelayMinutes: Long
        get() = plannedDeparture?.let { Duration.between(it, departure).toMinutes() } ?: 0

    val arrivalDelayMinutes: Long
        get() = plannedArrival?.let { Duration.between(it, arrival).toMinutes() } ?: 0
}

/**
 * A later part of a ride that the provider sent as a ride of its own: from [at] (named
 * [atName]) on, leaving at [departure], the vehicle runs under the handle [tripId] as line
 * [line]. [lineChanges] where the name of the line is another one ("stay seated, it goes on
 * as X").
 */
data class ThroughRun(
    val tripId: String?,
    val line: String?,
    val atName: String,
    val at: Stop?,
    val departure: OffsetDateTime,
    val lineChanges: Boolean,
)

/** The handle of the run this ride is on at [time]: after a junction that of the later part. */
fun Leg.tripIdAt(time: OffsetDateTime): String? =
    throughRuns.lastOrNull { !time.isBefore(it.departure) && it.tripId != null }?.tripId ?: tripId

/** Every handle of the runs of this ride, in order (one for a ride as the provider sent it). */
val Leg.tripIds: List<String> get() = (listOfNotNull(tripId) + throughRuns.mapNotNull { it.tripId }).distinct()

/** The line this ride goes on as, where it changes its name on the way; null else. */
val Leg.continuesAs: String? get() = throughRuns.lastOrNull { it.lineChanges }?.line

/** The longest wait at a junction of two parts of one ride that are still one ride. */
const val THROUGH_RUN_MAX_WAIT_MINUTES = 2L

/**
 * One ride for every ride the traveller does not leave: the provider sometimes splits a run
 * into two rides of the same line at a stop (a new handle on the way, through-running), which
 * would show a change of 0 minutes that is none, one change too many, the line twice in the
 * chart and connections offered where nobody changes. Two rides in a row are joined when
 * (a) they have the same line name (see [rapidLineName]) and family, meet at one station
 * (see [sameStation]) with at most [THROUGH_RUN_MAX_WAIT_MINUTES] between arrival and
 * departure, and on the same platform or one of them names none; or (b) the provider says
 * the vehicle is the same: the same handle, or [Leg.stayOnBoard]. The line, its direction or
 * end may differ. Cancelled rides are never joined. Stops and ways are put together, and each
 * later part is kept as a [ThroughRun], so its run can be loaded again.
 *
 * Also joined: a change into a later vehicle of the same line in the same direction where
 * staying seated is not slower (see [staysSeatedAhead], #34: two bunched trams 66, the
 * traveller's one ahead); the vehicle stays the first one.
 */
fun mergeThroughRuns(journey: Journey): Journey {
    val legs = journey.legs
    if (legs.count { !it.walking } < 2) return journey
    val merged = mutableListOf<Leg>()
    // For each leg as it came, the index of the leg it is (part of) in [merged].
    val at = IntArray(legs.size) { -1 }
    val stayAfter = journey.via?.takeIf { it.minutes > 0 }?.afterLegs
    // True when the stay at the place on the way lies between the ride before [index] and it.
    fun stayBreak(index: Int): Boolean {
        if (stayAfter == null) return false
        val ride = (index - 1 downTo 0).firstOrNull { !legs[it].walking } ?: return false
        return stayAfter in (ride + 1)..index
    }
    for ((index, leg) in legs.withIndex()) {
        val last = merged.lastOrNull()
        // The walk between the two feeds' stops of one train at one station (#96) is none.
        if (last != null && leg.walking && !stayBreak(index + 1)) {
            val next = legs.getOrNull(index + 1)
            if (next != null && isFeedSplit(last, next)) {
                at[index] = merged.lastIndex
                continue
            }
        }
        if (last != null && (isThroughRun(last, leg) || (!stayBreak(index) && isFeedSplit(last, leg)))) {
            merged[merged.lastIndex] = joinRides(last, leg)
            at[index] = merged.lastIndex
            continue
        }
        // A change right into a later vehicle of the same line and direction that staying
        // seated beats (see staysSeatedAhead): the traveller stays on board. Never across a
        // place on the way with a stay, where the traveller gets off, nor where the provider
        // sends a walk between the two rides.
        if (last != null && !stayBreak(index) && staysSeatedAhead(last, leg)) {
            merged[merged.lastIndex] = staySeated(last, emptyList(), leg)
            at[index] = merged.lastIndex
            continue
        }
        merged += leg
        at[index] = merged.lastIndex
    }
    if (merged.size == legs.size) return journey
    // A journey put together from two (a place on the way, a swapped rest) can be one ride over
    // the seam: the count of legs of its first part follows the joined legs, and the seam is
    // marked as inside the last leg of that part (see [JourneySplit.tailJoined]).
    fun countBefore(count: Int): Int = if (count <= 0) count else at[(count - 1).coerceAtMost(legs.lastIndex)] + 1
    val split = journey.split?.let { split ->
        val count = split.headLegs
        if (count <= 0 || count >= legs.size) return@let split.copy(headLegs = countBefore(count))
        split.copy(headLegs = countBefore(count), tailJoined = split.tailJoined || at[count - 1] == at[count])
    }
    // A place on the way at a seam that is now inside a joined ride moves after that ride: the
    // traveller stays on board there, so the stay counts after the whole ride (see refreshed,
    // which counts it one more when the ride is split in two again).
    val via = journey.via?.let { via -> via.copy(afterLegs = via.afterLegs?.let { countBefore(it) }) }
    return journey.copy(legs = merged, split = split, via = via)
}

/** True when [next] goes on with the vehicle of [ride] for the traveller (see [mergeThroughRuns]). */
fun isThroughRun(ride: Leg, next: Leg): Boolean {
    if (ride.walking || next.walking || ride.cancelled || next.cancelled) return false
    val lastHandle = ride.throughRuns.lastOrNull()?.tripId ?: ride.tripId
    if (next.stayOnBoard || (lastHandle != null && lastHandle == next.tripId)) return true
    val line = rapidLineName(ride.line, ride.product) ?: return false
    if (line != rapidLineName(next.line, next.product) || isProductWord(line)) return false
    if (modeFamily(ride.product) != modeFamily(next.product)) return false
    if (!sameStation(ride.toPlace, ride.to, next.fromPlace, next.from)) return false
    val wait = Duration.between(ride.arrival, next.departure).toMinutes()
    if (wait < 0 || wait > THROUGH_RUN_MAX_WAIT_MINUTES) return false
    val arriving = platformKey(ride.arrivalPlatform)
    val leaving = platformKey(next.departurePlatform)
    return arriving == null || leaving == null || arriving == leaving
}

// One train found in two feeds stops this long at most between the two parts.
private const val FEED_SPLIT_MAX_WAIT_MINUTES = 10L

/**
 * True when [next] is the train of [ride] going on in another feed's timetable (#96, Transitous
 * Frankfurt → Paris: the ICE 9552 of DELFI to "Mannheim, Hauptbahnhof" at 12:38, then the ICE
 * 9552 of the SNCF from "Mannheim Hbf" at 12:42 after a "walk" of 36 m): the same train run
 * (see trainRunOf), one station by StationIdentity, at most FEED_SPLIT_MAX_WAIT_MINUTES between
 * arrival and departure. The traveller stays on board.
 */
fun isFeedSplit(ride: Leg, next: Leg): Boolean {
    if (ride.walking || next.walking || ride.cancelled || next.cancelled) return false
    val first = trainRunOf(ride) ?: return false
    val second = trainRunOf(next) ?: return false
    if (first.number != second.number) return false
    if (first.kind != null && second.kind != null && first.kind != second.kind) return false
    if (!sameStation(ride.toPlace, ride.to, next.fromPlace, next.from)) return false
    val wait = Duration.between(ride.arrival, next.departure).toMinutes()
    return wait in 0..FEED_SPLIT_MAX_WAIT_MINUTES
}

/** [ride] and [next] as one ride: from where the first starts to where the second ends. */
private fun joinRides(ride: Leg, next: Leg): Leg {
    val firstStops = ride.stopovers
    val secondStops = next.stopovers
    // The junction once: arriving with the first ride, leaving with the second.
    val stopovers = if (firstStops.isNotEmpty() && secondStops.isNotEmpty() &&
        sameStation(firstStops.last().place, firstStops.last().name, secondStops.first().place, secondStops.first().name)
    ) {
        firstStops.dropLast(1) + firstStops.last().copy(departure = secondStops.first().departure) + secondStops.drop(1)
    } else {
        firstStops + secondStops
    }
    val path = if (ride.path.isNotEmpty() && next.path.isNotEmpty() && ride.path.last() == next.path.first()) {
        ride.path + next.path.drop(1)
    } else {
        ride.path + next.path
    }
    val firstLine = rapidLineName(ride.throughRuns.lastOrNull()?.line ?: ride.line, ride.product)
    val run = ThroughRun(
        tripId = next.tripId,
        line = next.line,
        atName = next.from,
        at = next.fromPlace,
        departure = next.departure,
        lineChanges = firstLine != rapidLineName(next.line, next.product),
    )
    return ride.copy(
        to = next.to,
        arrival = next.arrival,
        plannedArrival = next.plannedArrival,
        arrivalPlatform = next.arrivalPlatform,
        plannedArrivalPlatform = next.plannedArrivalPlatform,
        direction = ride.direction ?: next.direction,
        realtime = ride.realtime || next.realtime,
        path = path,
        toPlace = next.toPlace,
        warnings = (ride.warnings + next.warnings).distinct(),
        stopovers = stopovers,
        tripId = ride.tripId ?: next.tripId,
        alightingSpot = next.alightingSpot,
        arrivalEstimated = next.arrivalEstimated,
        additional = ride.additional || next.additional,
        throughRuns = ride.throughRuns + run + next.throughRuns,
    )
}

/**
 * Marks a journey that was put together from two: the first [headLegs] legs come from one
 * journey, the others from a second one, and each half is loaded again by its own handle.
 * A half without a handle keeps its last known times.
 */
data class JourneySplit(
    val headLegs: Int,
    val headToken: String?,
    val tailToken: String?,
    /**
     * The name of the stop where the last leg of the first part was cut short: the traveller
     * gets off that ride before its end. Loaded again, that leg is cut at the same stop.
     */
    val headCut: String? = null,
    /**
     * The first ride of the second part goes on in the vehicle of the last ride of the first
     * one and is joined with it (see [mergeThroughRuns]): the leg at [headLegs] - 1 holds both.
     * Loaded again, the parts are joined anew; one part alone cannot be put in its place.
     */
    val tailJoined: Boolean = false,
)

/**
 * Another stop where the rides of a journey can be changed: the change number [changeIndex]
 * (0 between the first and the second ride) at [at] instead, with [walkMinutes] on foot and
 * rated [rating]. [journey] is that variant as the search found it (without its own variants).
 */
data class JourneyAltChange(
    val changeIndex: Int,
    val at: String,
    val walkMinutes: Long,
    val rating: Tightness,
    val journey: Journey,
)

/**
 * An id kept by a journey (see [Journey.keptId]): [id], for the rides of [rides] (see
 * [journeyRideStamp]); [loose] for rides of a provider without a timetable, whose times move
 * with every refresh: then [rides] is their shape without times (see [journeyRideShape]).
 */
data class KeptJourneyId(val id: String, val rides: String, val loose: Boolean = false) {
    /** True while this id holds for a journey of [legs]. */
    fun holdsFor(legs: List<Leg>): Boolean =
        rides == if (loose) journeyRideShape(legs) else journeyRideStamp(legs)
}

/** The id of a journey of [legs]: planned times, lines and starts of all legs, walks too. */
fun journeyLegsId(legs: List<Leg>): String = legs.joinToString("|") { leg ->
    "${leg.plannedDeparture ?: leg.departure}>${leg.plannedArrival ?: leg.arrival}:${leg.timetableLine ?: leg.line ?: "walk"}:${leg.from}"
}

/** The part of [journeyLegsId] that belongs to the rides of [legs] alone: the walks left out. */
fun journeyRideStamp(legs: List<Leg>): String = journeyLegsId(legs.filter { !it.walking })

/**
 * The rides of [legs] without their times: lines and where each starts and ends. What stays of
 * a journey over refreshes where the provider sends no planned times (see [KeptJourneyId.loose]).
 */
fun journeyRideShape(legs: List<Leg>): String = legs.filter { !it.walking }.joinToString("|") { leg ->
    "${leg.timetableLine ?: leg.line ?: "?"}:${leg.from}>${leg.to}"
}

/**
 * Whether a planned time is known for every ride of [legs]. Where one is not (a provider
 * without a timetable), no planned time is made up for it: it would show as a delay.
 */
fun ridesPlanned(legs: List<Leg>): Boolean =
    legs.all { it.walking || (it.plannedDeparture != null && it.plannedArrival != null) }

/** A connection from A to B, made of legs. */
data class Journey(
    val legs: List<Leg>,
    /** Handle to load this same journey again with fresh live data, when the provider gives one. */
    val refreshToken: String? = null,
    /** Set when the rest of this journey was swapped for another connection at a change. */
    val split: JourneySplit? = null,
    /** The place on the way the search asked for, when it did. */
    val via: JourneyVia? = null,
    /**
     * The loosest change time among the searches that found this journey (see
     * [journeyChangeRatings]): [TransferComfort.Risky] when only the search with no change time
     * at all found it. Null where it is not known (a journey made of two, or not from a search).
     * Not part of [id].
     */
    val foundLevel: TransferComfort? = null,
    /**
     * Other stops where the very same rides can be changed (see withAltChanges): the search found
     * the journey once per change point; it is shown once, with the others on offer. Not part of [id].
     */
    val altChanges: List<JourneyAltChange> = emptyList(),
    /**
     * How many other journeys of the search this one stands for in the chart: the same rides
     * with only another short last bus or tram that gets there about as early (see
     * groupFeederAlternatives, #57). Not part of [id].
     */
    val groupedAlternatives: Int = 0,
    /**
     * The id this journey keeps after it came back from a stand-in provider with other walks
     * (see ontoKnown, #185); it holds only while the rides stay the same (see [journeyRideStamp]),
     * so a journey with other rides gets its own id again.
     */
    val keptId: KeptJourneyId? = null,
) {
    /** Tells journeys apart: planned times and lines of all legs (or the [keptId] while it holds). */
    val id: String = keptId?.takeIf { it.holdsFor(legs) }?.id ?: journeyLegsId(legs)
    val departure: OffsetDateTime get() = legs.first().departure
    val arrival: OffsetDateTime get() = legs.last().arrival
    val durationMinutes: Long get() = Duration.between(departure, arrival).toMinutes()
    val rides: List<Leg> get() = legs.filter { !it.walking }
    val transfers: Int get() = (rides.size - 1).coerceAtLeast(0)
    val cancelled: Boolean get() = legs.any { it.cancelled }

    /** True when fresh live data can be asked for: by the handle of the journey or of one of its halves. */
    val refreshable: Boolean
        get() = if (split == null) refreshToken != null else split.headToken != null || split.tailToken != null

    /** Minutes on foot before the first ride; for a journey without a ride, all of it. */
    val walkBeforeMinutes: Long
        get() = legs.takeWhile { it.walking }.sumOf { Duration.between(it.departure, it.arrival).toMinutes() }

    /** Minutes on foot after the last ride. */
    val walkAfterMinutes: Long
        get() = if (legs.none { !it.walking }) {
            0
        } else {
            legs.takeLastWhile { it.walking }.sumOf { Duration.between(it.departure, it.arrival).toMinutes() }
        }

    /** The disruption notices of all legs, each one once. */
    // Each text once: the notice of a station comes on the ride that arrives there and on the
    // one that leaves (MOTIS puts it on both places), each time with its own platform as its stop.
    val warnings: List<Warning> get() = legs.flatMap { it.warnings }.distinctBy { it.summary to it.text }
}

/**
 * [journey] as far as its first [keepLegs] legs, then [option]: the rest of a journey swapped
 * for another connection from a transfer stop on. [option] is a journey as the search found it.
 */
fun spliceJourney(journey: Journey, keepLegs: Int, option: Journey): Journey {
    val before = journey.split
    val headToken = when {
        before == null -> journey.refreshToken
        // A kept ride that goes on into the second part of an earlier swap: no handle loads it whole.
        before.tailJoined && keepLegs >= before.headLegs -> null
        // The kept legs all come from the first journey of an earlier swap.
        keepLegs <= before.headLegs -> before.headToken
        else -> null
    }
    // A ride cut short by an earlier swap stays cut as long as it is the last kept leg.
    val headCut = before?.headCut?.takeIf { keepLegs == before.headLegs }
    // The new rest can go on in the vehicle the traveller sits in: one ride, no change there.
    return mergeThroughRuns(
        Journey(
            legs = journey.legs.take(keepLegs) + option.legs,
            split = JourneySplit(headLegs = keepLegs, headToken = headToken, tailToken = option.refreshToken, headCut = headCut),
            via = splicedVia(journey, keepLegs, option),
        ),
    )
}

/**
 * The place on the way of a journey made of the first [keepLegs] legs of [journey] and then
 * [option]: the one of [option] where it has one, else the one of [journey] while it lies
 * among the kept legs.
 */
fun splicedVia(journey: Journey, keepLegs: Int, option: Journey): JourneyVia? {
    option.via?.let { via -> return via.copy(afterLegs = via.afterLegs?.plus(keepLegs)) }
    val via = journey.via ?: return null
    val after = via.afterLegs ?: return via
    return via.takeIf { after < keepLegs }
}

/**
 * The place on the way of [journey] that still lies ahead of a new plan that keeps [kept]
 * legs, as a [Via] for that search; null when there is none or it was passed already.
 */
fun viaAhead(journey: Journey, kept: List<Leg>): Via? {
    val via = journey.via ?: return null
    val after = via.afterLegs
    if (after != null) return via.via.takeIf { kept.size < after }
    val passed = kept.any { leg -> sameStation(leg.toPlace, leg.to, via.place, via.place.name) }
    return via.via.takeIf { !passed }
}

/** A pair of start and destination the user wants to search again with one tap. */
data class SavedRoute(val from: Stop, val to: Stop) {
    val id: String get() = routeId(from, to)
}

/** The icons a favourite place can carry. The names are stored, so they must stay as they are. */
/** The icons of saved places, in the order the pickers offer them; stored by name. */
/** The icons of places. New ones go at the end: the first free one is taken for a new favourite place. */
enum class PlaceIcon { Home, Work, School, Gym, Family, Friends, Shop, Party, Heart, Star, Pin, Station, Doctor, Food, Airport, Culture, Park, Hotel }

/**
 * A place the user wants on the start page: an icon, a name and where it is. An empty
 * [name] stands for the name of the icon; [place] is null until the user has chosen one.
 */
data class FavoritePlace(val icon: PlaceIcon, val name: String = "", val place: Stop? = null)

/**
 * A place the user keeps beyond the few favourite places: as many as wanted, in the order the
 * user put them. [id] stays the same through renaming and moving, for the widgets that show it.
 */
data class SavedPlace(val id: String, val icon: PlaceIcon, val name: String, val place: Stop) {
    /** The same place in the shape of a favourite place, for code that shows both alike. */
    fun asFavorite(): FavoritePlace = FavoritePlace(icon, name, place)
}

/** Tells saved routes apart: the ids of both ends. */
fun routeId(from: Stop, to: Stop): String = "${from.id}>${to.id}"

/**
 * A vehicle that is on the road right now. [path] is where it will be over the next
 * half minute, as latitude and longitude, starting at its current position.
 */
data class Vehicle(
    val tripId: String,
    val line: String,
    val product: String,
    val direction: String,
    val path: List<Pair<Double, Double>>,
    val nextStop: String?,
    val nextStopTime: OffsetDateTime?,
    /**
     * True when the position rests on live data of the provider (a forecast or a delay),
     * false when it is only worked out from the timetable. Never a GPS position either way.
     */
    val realtime: Boolean = false,
    /** The colour of the line as the provider sends it with this vehicle (ARGB); null where it sends none. */
    val providerColor: Int? = null,
    /** The colour of the text on [providerColor] as the provider sends it (ARGB); null where it sends none. */
    val providerTextColor: Int? = null,
    /**
     * True when the provider placed a train on the air line between two stations (see
     * onAirLine) and it is shown at a station instead: a course that knows the real tracks
     * places it by its timetable there.
     */
    val onAirLine: Boolean = false,
)

/**
 * Where a ride runs, for what depends on the place (the colour of its line, rules of one
 * network): where it is boarded, else the start of its way, a stop on it or where it ends.
 * The place of the ride itself, never where the app looks, so the same ride always comes
 * out the same.
 */
val Leg.ridePlace: Pair<Double, Double>?
    get() = fromPlace?.position ?: path.firstOrNull()
        ?: stopovers.firstNotNullOfOrNull { it.place?.position } ?: toPlace?.position

/** Where a departure leaves (see [Leg.ridePlace]): its stop, else where it is heading. */
val Departure.ridePlace: Pair<Double, Double>?
    get() = at ?: stop?.position ?: destination?.position

/** Where a vehicle is (see [Leg.ridePlace]). */
val Vehicle.ridePlace: Pair<Double, Double>?
    get() = path.firstOrNull()

/** Where a trip runs (see [Leg.ridePlace]): its first stop with a place, else the start of its way. */
val TripCourse.ridePlace: Pair<Double, Double>?
    get() = stops.firstNotNullOfOrNull { it.place?.position } ?: path.firstOrNull()

/**
 * The line of a known ride by the handle of its run: [tripId] runs as [line] of [product] with
 * the colours of its provider ([color], [textColor]) as the journey, the board or the course
 * names it (see [withRideLines]).
 */
data class RideName(val tripId: String, val line: String, val product: String, val color: Int?, val textColor: Int?)

/**
 * The names of the runs of [rides]: each ride by its handle, and each later part of a ride
 * joined from several runs (see [mergeThroughRuns]) by its own handle with the line it runs
 * as there. Walks and rides without a handle or a line give none.
 */
fun rideNames(rides: List<Leg>): List<RideName> = buildList {
    for (ride in rides) {
        if (ride.walking) continue
        val line = ride.line?.takeIf { it.isNotBlank() } ?: continue
        ride.tripId?.let { add(RideName(it, line, ride.product, ride.providerColor, ride.providerTextColor)) }
        for (run in ride.throughRuns) {
            val id = run.tripId ?: continue
            val runLine = run.line?.takeIf { it.isNotBlank() } ?: line
            // A part under another name may have another colour: the provider's of the ride is not its.
            val same = !run.lineChanges
            add(RideName(id, runLine, ride.product, if (same) ride.providerColor else null, if (same) ride.providerTextColor else null))
        }
    }
}

/**
 * [vehicles] with each vehicle of a known ride ([names], by the handle of its run) named and
 * coloured as that ride: the provider's vehicle or trip endpoint may name a run by the line it
 * began as (a night line that goes on as a day line), while the journey names the line the
 * traveller rides. A vehicle of no known ride keeps its own name. The first name of a handle
 * counts. The same list where nothing changes.
 */
fun withRideLines(vehicles: List<Vehicle>, names: List<RideName>): List<Vehicle> {
    if (names.isEmpty() || vehicles.isEmpty()) return vehicles
    val byTrip = HashMap<String, RideName>()
    for (name in names) byTrip.putIfAbsent(name.tripId, name)
    var changed = false
    val result = vehicles.map { vehicle ->
        val name = byTrip[vehicle.tripId] ?: return@map vehicle
        val renamed = vehicle.copy(
            line = name.line,
            product = name.product.ifEmpty { vehicle.product },
            providerColor = name.color ?: vehicle.providerColor,
            providerTextColor = if (name.color != null) name.textColor else vehicle.providerTextColor,
        )
        if (renamed != vehicle) changed = true
        renamed
    }
    return if (changed) result else vehicles
}

/** [this] with [place] as where each departure leaves, where a departure does not know it yet. */
fun List<Departure>.placedAt(place: Pair<Double, Double>?): List<Departure> =
    if (place == null) this else map { if (it.at == null) it.copy(at = place) else it }

/** One page of journey results. The references fetch the journeys before and after it, when the provider offers that. */
data class JourneyPage(val journeys: List<Journey>, val earlierRef: String?, val laterRef: String?)

/** The stop name without city suffix and without bracket notes such as "[Gleis 1-8]". */
fun String.plainStopName(): String = shortStopName().replace(TRAILING_BRACKET_NOTE, "")

// A note in square brackets at the end of a stop name, made once (it is used for every key typed in the search).
private val TRAILING_BRACKET_NOTE = Regex("\\s*\\[[^\\]]*\\]$")

/** Short enough for a narrow label: without the signs of the network in front and "Bhf" behind. */
fun String.briefStopName(): String =
    plainStopName().removePrefix("S+U ").removePrefix("S ").removePrefix("U ").removeSuffix(" Bhf")

/**
 * A place as short as a small chip needs it: a stop by its name, an address or a point of
 * interest by the part before the first comma (street and number, without postcode, town or
 * district), with "straße" and "strasse" written "str." ("Mainstraße 12" as "Mainstr. 12").
 */
fun chipPlaceName(place: Stop): String {
    val name = place.name.shortStopName()
    if (place.kind == PlaceKind.Stop) return name
    return name.substringBefore(',').trim().ifEmpty { name }.abbreviateStreet()
}

/** "straße" and "strasse" at the end of a word as "str.", "Straße" alone as "Str.". */
fun String.abbreviateStreet(): String =
    replace(Regex("(?<=\\p{L})(straße|strasse)\\b", RegexOption.IGNORE_CASE), "str.")
        .replace(Regex("\\b(Straße|Strasse)\\b"), "Str.")

/**
 * The name of a stop as the app shows it (see [stopNameParts]): without the town in brackets
 * where it is the town the app is used in ("(Berlin)", "(Bln)") and without the part of a big
 * station in square brackets behind it ("[Bus K.-Liebknecht-Str]", shown as a line of its own
 * where there is room, see [stopNamePart]). Requests and ids keep the raw name.
 */
fun String.shortStopName(): String = stopNameParts(this).title

/**
 * The part of [shape] that lies ahead of a vehicle at [position], starting at [position]:
 * the vehicle is put on the nearest stretch of the shape, and the points before it are left
 * out. A shape of fewer than two points is only appended. Positions are latitude and longitude.
 */
fun wayAhead(position: Pair<Double, Double>, shape: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
    val way = mutableListOf(position)
    fun add(point: Pair<Double, Double>) {
        if (point != way.last()) way.add(point)
    }
    if (shape.size < 2) {
        shape.forEach { add(it) }
        return way
    }
    // Flat measure around the vehicle: east scaled by the cosine of the latitude.
    val scale = cos(Math.toRadians(position.first))
    var best = Double.MAX_VALUE
    var bestIndex = 0
    for (index in 0 until shape.size - 1) {
        val ax = (shape[index].second - position.second) * scale
        val ay = shape[index].first - position.first
        val dx = (shape[index + 1].second - position.second) * scale - ax
        val dy = shape[index + 1].first - position.first - ay
        val length = dx * dx + dy * dy
        val share = if (length <= 0.0) 0.0 else (-(ax * dx + ay * dy) / length).coerceIn(0.0, 1.0)
        val px = ax + dx * share
        val py = ay + dy * share
        val distance = px * px + py * py
        // The first of equally near stretches: the vehicle has not yet passed the later ones.
        if (distance < best - 1e-18) {
            best = distance
            bestIndex = index
        }
    }
    for (index in bestIndex + 1 until shape.size) add(shape[index])
    return way
}

/**
 * The compass bearing from [from] to [to] in degrees, 0 north and 90 east, clockwise;
 * NaN for the same point. Positions are latitude and longitude.
 */
fun bearingDegrees(from: Pair<Double, Double>, to: Pair<Double, Double>): Double {
    val north = to.first - from.first
    val east = (to.second - from.second) * cos(Math.toRadians((from.first + to.first) / 2.0))
    if (north == 0.0 && east == 0.0) return Double.NaN
    return (Math.toDegrees(atan2(east, north)) + 360.0) % 360.0
}

/**
 * One change of a journey: the ride that arrives, the one that leaves and the minutes on foot
 * between them. [nextIndex] is the index of [next] among the legs of the journey; [rating] is
 * the category of the change within its journey (see [journeyChangeRatings]), null where the
 * change stands alone and only the station rule rates it.
 */
data class JourneyChange(
    val arriving: Leg,
    val next: Leg,
    val walkMinutes: Long,
    val nextIndex: Int = -1,
    val rating: Tightness? = null,
) {
    /**
     * Minutes to spare by the live times beyond what the change needs (see [neededMinutes]).
     * Worked out once per change: the changes of a journey are kept (see [journeyChangesOf]),
     * and what a change needs looks up its station and platforms in OpenStreetMap.
     */
    val spare: Long by lazy(LazyThreadSafetyMode.PUBLICATION) {
        if (knownSpare != NO_SPARE) knownSpare else changeSpare(arriving, next, walkMinutes)
    }

    // The spare of the change this one was copied from where that had worked it out already
    // (see ChangeRatings.of): set right after the copy, before anyone reads [spare].
    internal var knownSpare: Long = NO_SPARE
    val tightness: Tightness get() = rating ?: uncertainTightness(this, changeTightness(arriving, next, walkMinutes))

    /**
     * True when the arrival of [arriving] is the app's estimate, as the provider's prognosis was
     * implausible (see [plausible]): whether the change works is uncertain.
     */
    val uncertain: Boolean get() = arriving.arrivalEstimated

    /**
     * The time of this change as every screen shows it (see [changeTime]): by the plausible
     * arrival where [arriving] comes from [rawChangesOf], rated by [tightness] (an uncertain
     * change normal at best).
     */
    val time: ChangeTime by lazy(LazyThreadSafetyMode.PUBLICATION) { changeTime(arriving, next, walkMinutes, tightness) }
}

// No spare known yet (see JourneyChange.knownSpare).
private const val NO_SPARE = Long.MIN_VALUE

/**
 * Every change of [journey] in order, with the walks between the two rides, rated by the station
 * rule alone. The rides are taken with plausible times (see [Journey.plausible]): a change is
 * judged by the estimated arrival where the provider's prognosis is implausible.
 */
private fun rawChangesOf(journey: Journey): List<JourneyChange> {
    val legs = journey.plausible().legs
    val rides = legs.indices.filter { !legs[it].walking }
    return rides.zipWithNext().mapNotNull { (from, to) ->
        val arriving = legs[from]
        val next = legs[to]
        if (arriving.tripId != null && arriving.tripId == next.tripId) {
            null
        } else {
            val walks = legs.subList(from + 1, to).filter { it.walking }.sumOf { changeWalkMinutes(it, arriving, next) }
            JourneyChange(arriving, next, walks, to)
        }
    }
}

/**
 * The category of every change of [journey], in order, by the change itself: the station rule
 * (see [changeTightness]): what the change needs at its station, 1 minute on one platform, 3 to
 * another, 5 at a station with regional or long-distance trains, a walk and one more, and what
 * it leaves beyond that; between bus and tram stop positions known from OpenStreetMap 1 minute
 * at one stop, else the walk between them and one more (see [stopSpotNeed]); an arrival that is
 * only estimated makes it normal at best (see [uncertainTightness]).
 *
 * Which search found the journey ([Journey.foundLevel]) plays no part any more. Before, a
 * journey found only by the search without any change time had its changes raised to "knapp",
 * so the very same change (the same bus to the same train) was "normal" in one journey and
 * "knapp" in another found by another search. A change is now rated the same in every journey
 * that holds it; what it leaves below [normalSpare] makes it "knapp" by the rule already.
 * The overview chart rates its changes by these too (see journeyChangesOf), so a change looks
 * the same there as in the detail view.
 */
fun journeyChangeRatings(journey: Journey): List<Tightness> = ChangeRatings.of(journey).ratings

private fun maxTightness(a: Tightness, b: Tightness): Tightness = if (a.ordinal >= b.ordinal) a else b

/**
 * A change whose arrival is only estimated ([JourneyChange.uncertain]) is never rated safe:
 * normal at best, and risky where the estimate leaves less than [relaxedSpare] to spare.
 */
private fun uncertainTightness(change: JourneyChange, rating: Tightness): Tightness = when {
    !change.uncertain -> rating
    change.spare < relaxedSpare -> maxTightness(rating, Tightness.Risky)
    else -> maxTightness(rating, Tightness.Tight)
}

/**
 * Every change of [journey] in order, with the walks between the two rides and its category
 * within the journey (see [journeyChangeRatings]). Staying on board (two rides of the same
 * trip) is no change.
 */
fun journeyChangesOf(journey: Journey): List<JourneyChange> = ChangeRatings.of(journey).changes

/** The changes of a journey with their ratings, as [ChangeRatings] keeps them. */
internal class RatedChanges(val changes: List<JourneyChange>, val ratings: List<Tightness>)

/**
 * The rated changes of the journeys asked for last, by the identity of the journey (a journey
 * never changes; fresh live data is a new one). Rating a change is not free: the plausible
 * times of its rides (see [Journey.plausible]), the station, platforms and walks of
 * OpenStreetMap (see [transferWalkSeconds]), the records of the same platform. Every view of a
 * search asks for the same changes again and again (the filter of the levels, the tags, the
 * chart columns, the detail card), for each journey, on every composition of AppRoot (the clock
 * every 15 s, every journey refreshed with live data), which kept the connections page
 * allocating some 40 MB/s while the user only looked at it. An answer is kept as long as
 * everything the rating reads is the same: the rating settings ([ratingSettingsVersion]), the
 * data of OpenStreetMap and of the change positions, and the lookups the interface installed.
 */
internal object ChangeRatings {
    // A power of two: the slot of a journey is its identity hash, masked. 256 entries hold the
    // journeys of several searches; more only kept old live data alive.
    private const val SIZE = 128
    private const val MASK = SIZE - 1
    private const val WAYS = 2
    private val journeys = arrayOfNulls<Journey>(SIZE * WAYS)
    private val rated = arrayOfNulls<RatedChanges>(SIZE * WAYS)
    private val versions = IntArray(SIZE * WAYS)
    // Per slot the way used last: a new entry takes the other one (least recently used).
    private val recent = ByteArray(SIZE)
    // Counts every clearing of the kept answers: an answer worked out before one is not kept.
    private var epoch = 0
    private var walks: Any? = null
    private var platforms: Any? = null
    private var records: Any? = null
    private var osm: Any? = null
    private var positions: Any? = null
    private var regions: Any? = null
    // The tuning as plain values too: tests set them without telling (see setTransferTuning).
    private var normal = Long.MIN_VALUE
    private var relaxed = Long.MIN_VALUE
    private var buffer = Long.MIN_VALUE
    private var pace = Double.NaN
    private var stepFree = false

    /** How often a rating was asked for, and how often it had to be worked out (for the performance lab). */
    @Volatile
    var asked = 0L
        private set

    @Volatile
    var computed = 0L
        private set

    /** The rated changes of [journey]: kept ones where they are still right, else worked out (outside the lock) and kept. */
    fun of(journey: Journey): RatedChanges {
        val version = ratingSettingsVersion
        // The epoch the answer is worked out in: cleared meanwhile (other data, other settings),
        // it is not kept (see keep).
        val since = synchronized(this) {
            kept(journey, version)?.let { return it }
            epoch
        }
        val raw = rawChangesOf(journey)
        val ratings = raw.map { uncertainTightness(it, changeTightness(it.arriving, it.next, it.walkMinutes)) }
        val result = RatedChanges(
            raw.mapIndexed { index, change ->
                // An uncertain change has worked out its spare for the rating already.
                change.copy(rating = ratings[index]).also { if (change.uncertain) it.knownSpare = change.spare }
            },
            ratings,
        )
        keep(journey, version, since, result)
        return result
    }

    /** Drops every kept answer, as when memory runs short (see NextStopApp.onTrimMemory). */
    @Synchronized
    fun clear() {
        journeys.fill(null)
        rated.fill(null)
        epoch++
    }

    /** Clears what is kept when anything the rating reads (not a setting) is another object now. Under the lock. */
    private fun validate() {
        // Anything the rating reads that is not a setting: when one of them is another object, nothing kept is right.
        val nowWalks: Any = transferWalkSeconds
        val nowPlatforms: Any = ridePlatforms
        val nowRecords: Any = samePlatformRecord
        val nowOsm = OsmStations.ratingData
        val nowPositions = ChangePositions.ratingRecords
        val nowRegions = ChangePositions.ratingRegions
        if (nowWalks !== walks || nowPlatforms !== platforms || nowRecords !== records || nowOsm !== osm ||
            nowPositions !== positions || nowRegions !== regions || normalSpare != normal || relaxedSpare != relaxed ||
            changeBufferSeconds != buffer || walkSpeedFactor != pace || stepFreeWalks != stepFree
        ) {
            journeys.fill(null)
            rated.fill(null)
            epoch++
            walks = nowWalks
            platforms = nowPlatforms
            records = nowRecords
            osm = nowOsm
            positions = nowPositions
            regions = nowRegions
            normal = normalSpare
            relaxed = relaxedSpare
            buffer = changeBufferSeconds
            pace = walkSpeedFactor
            stepFree = stepFreeWalks
        }
    }

    /** The kept answer for [journey] at [version], or null. Under the lock (see of). */
    private fun kept(journey: Journey, version: Int): RatedChanges? {
        asked++
        validate()
        val index = System.identityHashCode(journey) and MASK
        val slot = index * WAYS
        for (way in 0 until WAYS) {
            val at = slot + way
            if (journeys[at] === journey && versions[at] == version) {
                recent[index] = way.toByte()
                return rated[at]
            }
        }
        computed++
        return null
    }

    @Synchronized
    private fun keep(journey: Journey, version: Int, since: Int, result: RatedChanges) {
        // Data that changed while it was worked out (a pack of OpenStreetMap came in, the cache
        // was cleared): not kept, the next call works it out again with the new data.
        validate()
        if (epoch != since) return
        val index = System.identityHashCode(journey) and MASK
        val slot = index * WAYS
        // The same journey (an older version of the settings) or an empty way first, else the
        // way not used last in this slot.
        var way = -1
        for (w in 0 until WAYS) if (journeys[slot + w] === journey) way = w
        if (way < 0) for (w in 0 until WAYS) if (way < 0 && journeys[slot + w] == null) way = w
        if (way < 0) way = (recent[index] + 1) % WAYS
        recent[index] = way.toByte()
        val at = slot + way
        journeys[at] = journey
        rated[at] = result
        versions[at] = version
    }
}

/**
 * The category of the change that leads to the leg at [nextIndex] of [journey]; null when
 * that leg starts no change (see [journeyChangesOf]).
 */
fun changeRatingAt(journey: Journey, nextIndex: Int): Tightness? =
    journeyChangesOf(journey).firstOrNull { it.nextIndex == nextIndex }?.tightness

/**
 * The level of the search that found a journey, for [Journey.foundLevel]: of the [levels]
 * whose searches found it, the loosest (the one with the most change time).
 */
fun loosestLevel(levels: Collection<TransferComfort>): TransferComfort? = levels.minByOrNull { it.ordinal }

/**
 * The time to spare at every change of [journey], in minutes, in order: from getting off one
 * ride to the departure of the next, less what the change needs (walks included).
 */
fun transferBuffers(journey: Journey): List<Long> = journeyChangesOf(journey).map { it.spare }

/** How tight the tightest change of [journey] is; null for a journey without a change. */
fun journeyTightness(journey: Journey): Tightness? =
    journeyChangeRatings(journey).maxByOrNull { it.ordinal }

/** The category of [journey] by its tightest change; a journey without a change is relaxed. */
fun journeyComfort(journey: Journey): TransferComfort =
    journeyTightness(journey)?.let { transferComfort(it) } ?: TransferComfort.Relaxed

/**
 * Tells journeys apart by what they are, not by the handle of the request that found them:
 * the line, planned times and stations of every ride. The same connection found by two
 * searches (another level of transfer comfort, or a walk timed a little differently) has the
 * same key.
 */
fun journeyKey(journey: Journey): String = journey.rides.joinToString("|") { ride ->
    "${(ride.timetableLine ?: ride.line)?.let { normalLine(it) } ?: "?"}@${ride.plannedDeparture ?: ride.departure}>" +
        "${ride.plannedArrival ?: ride.arrival}:${stationKey(ride.fromPlace, ride.from)}>${stationKey(ride.toPlace, ride.to)}"
}

/** A station in one form for [journeyKey]: its VBB number where the id holds one, else its name. */
private fun stationKey(place: Stop?, name: String): String {
    val id = place?.id?.let { normalStationId(it) }
    if (id != null && id.length == 9 && id.startsWith("900") && id.all { it.isDigit() }) return id
    return stationNameKey(place?.name ?: name)
}

/** The words providers use for a product alone, without the number of a line. */
private val PRODUCT_ONLY = setOf(
    "bus", "metrobus", "nachtbus", "expressbus", "tram", "metrotram", "str", "straßenbahn", "strassenbahn",
    "u", "ubahn", "u-bahn", "s", "sbahn", "s-bahn", "fähre", "faehre", "ferry", "ruftaxi", "rufbus",
)

/** True when [name] is only the word for a product ("Bus", "Tram"), not the name of a line. */
fun isProductWord(name: String): Boolean = name.trim().lowercase() in PRODUCT_ONLY

/**
 * The name of a line as the app uses it, from the name a provider gives. Every source puts
 * its line names through here.
 *
 * - An S- or U-Bahn line that the provider names by its number alone gets its letter in
 *   front: an additional S1 train comes from BVG HAFAS as line "1" with product "suburban"
 *   and is "S1" in the app, so it gets the name and colour of the S1. Only by the product:
 *   a train of another product keeps its name.
 * - The kind of a train in front of its number is written as everywhere ("rj 171" is
 *   "RJ 171", "ice 29" is "ICE 29"), see [canonicalTrainKind].
 * - The word for a local product in front of the line goes: "Tram 50", "STR 50" and
 *   "Bus 585" are "50" and "585", "Bus M48" is "M48" (see [withoutProductWord]).
 * - FlixBus and FlixTrain read alike from every provider: "FlixBus 260", "FLX 30" (see
 *   [flixLineName]).
 *
 * Other names stay as they are.
 */
fun rapidLineName(name: String?, product: String): String? {
    val plain = flixLineName(withoutProductWord(name ?: return null), product)
    val letter = when (product) {
        "suburban" -> "S"
        "subway" -> "U"
        else -> return canonicalTrainKind(plain, product)
    }
    return if (plain.length in 1..2 && plain.all { it.isDigit() }) "$letter$plain" else plain
}

/** The remark code of HAFAS for a run that is not in the timetable but added ("Zusatzfahrt"). */
const val ADDITIONAL_SERVICE_CODE = "text.realtime.journey.additional.service"

/** How far apart the planned times of a cancelled run and the extra run that replaces it may be. */
const val REPLACEMENT_WINDOW_MINUTES = 3L

/**
 * A board where a cancelled run and an additional run of the same line, the same way and
 * platform leave at the same planned time (within [REPLACEMENT_WINDOW_MINUTES]) shows one
 * row: the additional run, marked as standing in for the cancelled one, which is left out.
 * BVG HAFAS announces a replacement S1 that way: the S1 "fällt aus" and an extra "1" leaves
 * from the same platform at the same minute. An additional run that doubles a regular run
 * which still goes (same line, way, platform and planned minute) is one train shown twice: only the
 * regular run stays. Other runs stay as they are, in their order.
 */
fun mergeReplacements(departures: List<Departure>): List<Departure> {
    if (departures.none { it.additional }) return departures
    val replaced = mutableSetOf<Int>()
    val merged = departures.mapIndexed { index, departure ->
        if (!departure.additional || departure.cancelled) return@mapIndexed departure
        val match = departures.indices.firstOrNull { other ->
            other != index && other !in replaced && replaces(departure, departures[other])
        } ?: return@mapIndexed departure
        replaced += match
        departure.copy(replaces = departures[match].line)
    }
    val doubled = merged.indices.filter { index ->
        val extra = merged[index]
        index !in replaced && extra.additional && !extra.cancelled && extra.replaces == null &&
            merged.indices.any { other -> other != index && other !in replaced && doubles(extra, merged[other]) }
    }.toSet()
    return merged.filterIndexed { index, _ -> index !in replaced && index !in doubled }
}

/** True when [extra] (an additional run) stands in for [regular], a cancelled run. */
private fun replaces(extra: Departure, regular: Departure): Boolean {
    if (!regular.cancelled || regular.additional) return false
    return sameRun(extra, regular, REPLACEMENT_WINDOW_MINUTES)
}

/** True when [extra] (an additional run that goes) is the same train as [regular], a regular run that goes too. */
private fun doubles(extra: Departure, regular: Departure): Boolean {
    if (regular.cancelled || regular.additional) return false
    // Two trains can follow each other within minutes; the very same minute is one train.
    return sameRun(extra, regular, windowMinutes = 0)
}

/** Same line, way, platform, station complex and planned minute (within [windowMinutes]). */
private fun sameRun(extra: Departure, regular: Departure, windowMinutes: Long): Boolean {
    if (!extra.line.equals(regular.line, ignoreCase = true) || extra.product != regular.product) return false
    val sameWay = extra.direction.trim().equals(regular.direction.trim(), ignoreCase = true) ||
        (extra.destination != null && extra.destination.id == regular.destination?.id)
    if (!sameWay) return false
    if (extra.platform?.trim().orEmpty() != regular.platform?.trim().orEmpty()) return false
    if (!sameStopComplex(extra.stop, regular.stop)) return false
    val a = (extra.plannedTime ?: extra.time)?.toEpochSecond()?.div(60) ?: return false
    val b = (regular.plannedTime ?: regular.time)?.toEpochSecond()?.div(60) ?: return false
    return kotlin.math.abs(a - b) <= windowMinutes
}

// Stops this close with the same name are one station complex.
private const val SAME_COMPLEX_METERS = 150.0

/**
 * True when two stops belong to one station complex: no stop given for either (one list), the
 * same normalised station id, the same parent station (the id before a "::" platform), or
 * stops within [SAME_COMPLEX_METERS] with the same normalised name.
 */
private fun sameStopComplex(a: Stop?, b: Stop?): Boolean {
    if (a == null || b == null) return a == null && b == null
    if (StationIdentity.sameStopPlace(a, b)) return true
    if (a.id.contains("::") && a.id.substringBefore("::") == b.id.substringBefore("::")) return true
    val pa = a.position ?: return false
    val pb = b.position ?: return false
    val name = stationNameKey(a.name)
    return name.isNotEmpty() && name == stationNameKey(b.name) && spotMeters(pa, pb) <= SAME_COMPLEX_METERS
}

/**
 * [journeys] without those that are cancelled only because a ride was replaced by an extra
 * run another journey of the list takes: the same rides and stations, the same planned times
 * for the rides that run, and for each cancelled ride a running ride of the same line within
 * [REPLACEMENT_WINDOW_MINUTES]. BVG HAFAS offers both: the connection with the cancelled S1
 * and the same connection with the extra "1" (an S1) in its place. The order stays.
 */
fun withoutReplacedJourneys(journeys: List<Journey>): List<Journey> {
    if (journeys.none { it.cancelled }) return journeys
    val running = journeys.filter { !it.cancelled }
    return journeys.filter { journey -> !journey.cancelled || running.none { replacesJourney(it, journey) } }
}

/** True when [replacement] is [cancelled] with its cancelled rides swapped for runs that go (see [withoutReplacedJourneys]). */
fun replacesJourney(replacement: Journey, cancelled: Journey): Boolean {
    val old = cancelled.rides
    val new = replacement.rides
    if (old.size != new.size || old.none { it.cancelled } || new.any { it.cancelled }) return false
    return old.indices.all { index -> rideStandsFor(new[index], old[index]) }
}

private fun rideStandsFor(new: Leg, old: Leg): Boolean {
    val line = { leg: Leg -> leg.line?.let { normalLine(rapidLineName(it, leg.product) ?: it) } }
    if (line(new) != line(old)) return false
    if (stationKey(new.fromPlace, new.from) != stationKey(old.fromPlace, old.from)) return false
    if (stationKey(new.toPlace, new.to) != stationKey(old.toPlace, old.to)) return false
    val newLeaves = new.plannedDeparture ?: new.departure
    val oldLeaves = old.plannedDeparture ?: old.departure
    val newArrives = new.plannedArrival ?: new.arrival
    val oldArrives = old.plannedArrival ?: old.arrival
    if (!old.cancelled) return newLeaves == oldLeaves && newArrives == oldArrives
    return kotlin.math.abs(Duration.between(newLeaves, oldLeaves).toMinutes()) <= REPLACEMENT_WINDOW_MINUTES &&
        kotlin.math.abs(Duration.between(newArrives, oldArrives).toMinutes()) <= REPLACEMENT_WINDOW_MINUTES
}
