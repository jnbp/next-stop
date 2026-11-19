package io.github.jnbp.nextstop.ui.companion

import io.github.jnbp.nextstop.data.PickerMode
import io.github.jnbp.nextstop.data.StopRowSpec
import io.github.jnbp.nextstop.data.routeMeters
import io.github.jnbp.nextstop.data.stopRows

/*
 * The plan of the calm companion's journey timeline, free of Compose and of the data of the
 * trip, so it can be tested: which rows stand in which order (walks, rides, changes between
 * rides), what state each ride is in, how its stops fold and where the connection picker is
 * open. The views (CompanionTimeline) only draw what this decides.
 */

/** Where a ride of the followed journey stands: ridden, on board, the one to board next, or later. */
enum class JourneyRidePhase { Done, Riding, Next, Later }

/** One row of the journey timeline, top to bottom. [key] tells the rows apart for Compose. */
sealed class JourneyRow {
    abstract val key: String

    /** A walk before the first ride or after the last one (a walk between rides belongs to its change). */
    data class Walk(val legIndex: Int, val endsTrip: Boolean, val over: Boolean) : JourneyRow() {
        override val key: String get() = "walk$legIndex"
    }

    /** A ride in its own card. */
    data class Ride(val legIndex: Int, val phase: JourneyRidePhase) : JourneyRow() {
        override val key: String get() = "ride$legIndex"
    }

    /**
     * The change from the ride at [arrivingIndex] to the ride at [nextIndex], with the walk at
     * [walkIndex] between them where there is one; [over] once the connecting ride is under way.
     */
    data class Change(val arrivingIndex: Int, val nextIndex: Int, val walkIndex: Int?, val over: Boolean) : JourneyRow() {
        /** How many legs of the journey stay as they are when another connection is taken here. */
        val keepLegs: Int get() = arrivingIndex + 1
        override val key: String get() = "change$keepLegs"
    }
}

/**
 * The rows of a journey whose legs are on foot where [walking] says so, top to bottom: walks
 * at the start and the end (those of [walkMinutes] 0 left out), every ride, and between two
 * rides their change where [changes] (the keepLegs of the changes known) has it. [focus] is
 * the leg index of the ride the companion is about (on board when [onBoard], else the next to
 * board), null once every ride is over. Rides before it are done, those after it later.
 */
fun journeyRows(
    walking: List<Boolean>,
    walkMinutes: List<Long>,
    changes: Set<Int>,
    focus: Int?,
    onBoard: Boolean,
): List<JourneyRow> {
    val rides = walking.indices.filter { !walking[it] }
    if (rides.isEmpty()) {
        return walking.indices.filter { walkMinutes.getOrElse(it) { 0L } > 0 }
            .map { JourneyRow.Walk(it, endsTrip = it == walking.lastIndex, over = false) }
    }
    val firstRide = rides.first()
    val lastRide = rides.last()
    fun phase(index: Int): JourneyRidePhase = when {
        focus == null -> JourneyRidePhase.Done
        index < focus -> JourneyRidePhase.Done
        index == focus -> if (onBoard) JourneyRidePhase.Riding else JourneyRidePhase.Next
        else -> JourneyRidePhase.Later
    }
    fun underWay(index: Int): Boolean = phase(index).let { it == JourneyRidePhase.Done || it == JourneyRidePhase.Riding }
    val rows = mutableListOf<JourneyRow>()
    walking.indices.forEach { index ->
        if (walking[index]) {
            val between = index in (firstRide + 1) until lastRide
            if (!between && walkMinutes.getOrElse(index) { 0L } > 0) {
                // A walk at the start is over once the first ride is under way; the last one never is here.
                val over = index < firstRide && underWay(firstRide)
                rows += JourneyRow.Walk(index, endsTrip = index == walking.lastIndex, over = over)
            }
        } else {
            rows += JourneyRow.Ride(index, phase(index))
            val next = rides.firstOrNull { it > index }
            if (next != null && (index + 1) in changes) {
                val walk = (index + 1 until next).firstOrNull { walking[it] }
                rows += JourneyRow.Change(index, next, walk, over = underWay(next))
            }
        }
    }
    return rows
}

/**
 * The stop rows of a ride card in [phase] with [count] stops, the traveller at [position]
 * (stop units, below 0 before boarding): a ride on board or the next one shows the stops
 * ahead and folds the middle; a later ride only its first and last stop with the middle
 * folded; a ride that is over none at all (its card says how many it had). [expanded] shows
 * them all.
 */
fun journeyStopRows(count: Int, position: Float, phase: JourneyRidePhase, expanded: Boolean): List<StopRowSpec> = when (phase) {
    JourneyRidePhase.Done -> if (expanded) stopRows(count, -1f, expanded = true) else emptyList()
    JourneyRidePhase.Riding -> stopRows(count, position, expanded)
    JourneyRidePhase.Next -> stopRows(count, -1f, expanded)
    JourneyRidePhase.Later -> stopRows(count, -1f, expanded, ahead = 0, beforeEnd = 0)
}

/**
 * Whether a ride card shows where to board for the change after it (CarriageHint) or the way
 * out at the destination (#127): for every ride not yet over, as the detail of the connection.
 */
fun rideHintShown(phase: JourneyRidePhase): Boolean = phase != JourneyRidePhase.Done

/**
 * The platform shown at the stop row [index] of a ride whose last stop is [last] (#153): where
 * to board ([board]) at the first, where one arrives ([alight]) at the last, none between.
 */
fun stopRowPlatform(index: Int, last: Int, board: String?, alight: String?): String? = when (index) {
    0 -> board
    last -> alight
    else -> null
}

/** The planned time to strike beside the live [live] one: [planned] where it is a minute or more earlier; null otherwise. */
fun plannedShown(live: java.time.OffsetDateTime?, planned: java.time.OffsetDateTime?): java.time.OffsetDateTime? =
    if (live != null && planned != null && java.time.Duration.between(planned, live).seconds >= 60) planned else null

/** The entries of "Plan B" in the companion's bottom bar (#116), in their order. */
enum class PlanB { MissedTrain, MissedChange, Late, WrongTrain }

/**
 * What "Plan B" offers (#116): "Bus/Zug/… verpasst" (by the kind of ride, #212) only while it matters ([missedRelevant], before
 * boarding and near the departure, never once on board), "Anschluss verpasst" only with a change
 * ahead ([changeAhead]); "zu spät / fällt aus" and "im falschen Bus/Zug/…" lead to a new search from
 * here, so only where one can be made ([canReplan]).
 */
fun planBItems(missedRelevant: Boolean, changeAhead: Boolean, canReplan: Boolean): List<PlanB> = buildList {
    if (missedRelevant) add(PlanB.MissedTrain)
    if (changeAhead) add(PlanB.MissedChange)
    if (canReplan) {
        add(PlanB.Late)
        add(PlanB.WrongTrain)
    }
}

/** Whether ending the trip asks once more (#116): only while it still runs, not once arrived. */
fun endAsksFirst(arrived: Boolean): Boolean = !arrived

/** How the connection picker shows at a change: not at all, open (the chips), or as a small link. */
enum class PickerShow { None, Open, Link }

/**
 * The picker of the change keeping [keepLegs] legs (#94): none once it is [over]; chips only
 * while the connecting departures are loaded for it ([active]), a link otherwise. By [mode]
 * (#138): [PickerMode.Folded] opens only the one opened by hand ([open]); [PickerMode.NextOpen]
 * opens the one with the departures by itself unless it was folded by hand ([folded]);
 * [PickerMode.AllOpen] opens it too; in both it can be folded by hand (r7plan: "Alle ausgeklappt" only
 * says how it starts, the link keeps its arrow). The detail of a journey: see detailPickerOpen.
 */
fun pickerShow(
    keepLegs: Int,
    over: Boolean,
    active: Int?,
    open: Int?,
    mode: PickerMode = PickerMode.Folded,
    folded: Int? = null,
): PickerShow = when {
    over -> PickerShow.None
    keepLegs != active -> PickerShow.Link
    keepLegs == open -> PickerShow.Open
    (mode == PickerMode.AllOpen || mode == PickerMode.NextOpen) && folded != keepLegs -> PickerShow.Open
    else -> PickerShow.Link
}

/**
 * The connecting departures to offer at a change with the one now taken among them (#94): where
 * [options] lack it ([isCurrent] finds it) and [current] stands for it, it comes first, so the
 * choice in use is always shown as chosen.
 */
fun <T> withCurrentChoice(options: List<T>, current: T?, isCurrent: (T) -> Boolean): List<T> =
    if (current == null || options.any(isCurrent)) options else listOf(current) + options

/**
 * What the shortcut guard compares of the own journey (#75, #120): when it gets the traveller
 * there ([arrivalMillis], epoch millis) and whether it [broken] (a change that can no longer be
 * made, or a ride cancelled). Not its live times as text: those change with every refresh by a
 * few seconds, and the companion and the notification read them from two slightly different
 * journeys (the provider's and the steadied one), so the shared guard saw a "change" at once.
 */
data class ShortcutOwn(val arrivalMillis: Long, val broken: Boolean)

/** The own journey has to arrive at least this much later than when chosen before a shortcut is offered early. */
const val SHORTCUT_OWN_LATER_MILLIS = 2 * 60_000L

/** The offered way has to arrive at least this much earlier than when first seen before it is offered early. */
const val SHORTCUT_OFFER_EARLIER_MILLIS = 3 * 60_000L

/**
 * Whether a shortcut ("faster way") may be offered in the companion (#75, #120): not right after
 * the traveller chose this connection. From the start of following it, [sinceStartMillis] ago, the
 * offer stays quiet until something meaningful changed: the own journey arrives at least
 * [SHORTCUT_OWN_LATER_MILLIS] later than at the start ([ownAtStart], [own]) or broke since, or the
 * offered way arrives ([offerArrival]) at least [SHORTCUT_OFFER_EARLIER_MILLIS] earlier than the
 * first offer seen ([firstOfferArrival]); at most for [SHORTCUT_QUIET_MAX_MILLIS].
 */
fun shortcutOfferAllowed(
    sinceStartMillis: Long,
    ownAtStart: ShortcutOwn,
    own: ShortcutOwn,
    firstOfferArrival: Long?,
    offerArrival: Long,
): Boolean {
    if (sinceStartMillis >= SHORTCUT_QUIET_MAX_MILLIS) return true
    if (own.arrivalMillis - ownAtStart.arrivalMillis >= SHORTCUT_OWN_LATER_MILLIS) return true
    if (own.broken && !ownAtStart.broken) return true
    return firstOfferArrival != null && firstOfferArrival - offerArrival >= SHORTCUT_OFFER_EARLIER_MILLIS
}

/** At most this long a shortcut stays quiet after the start of following a connection. */
const val SHORTCUT_QUIET_MAX_MILLIS = 15 * 60_000L

/**
 * Remembers, per followed journey, what [shortcutOfferAllowed] compares (#75): when following it
 * began, its state then ([own] at the first call for a journey id) and the arrival of the first
 * offer seen. Once an offer was allowed, offers stay allowed for that journey. One instance
 * ([ShortcutGuard]) serves the companion and the notification, so both are quiet and speak up
 * together. A simulated offer of the developer options ([bypass]) is always let through, so the
 * card, its preview and the notification can be tried at once; it does not open the guard.
 */
class ShortcutQuiet {
    private var journeyId: String? = null
    private var startedAt = 0L
    private var ownAtStart = ShortcutOwn(0L, false)
    private var firstOffer: Long? = null
    private var opened = false

    /** Whether an offer arriving at [offerArrival] (epoch millis; null for none) may be shown now, at [nowMillis]. */
    fun allows(journeyId: String, own: ShortcutOwn, offerArrival: Long?, nowMillis: Long, bypass: Boolean = false): Boolean {
        if (journeyId != this.journeyId) {
            this.journeyId = journeyId
            startedAt = nowMillis
            ownAtStart = own
            firstOffer = null
            opened = false
        }
        if (offerArrival == null) return false
        if (bypass) return true
        if (opened) return true
        val first = firstOffer ?: offerArrival.also { firstOffer = it }
        opened = shortcutOfferAllowed(nowMillis - startedAt, ownAtStart, own, first, offerArrival)
        return opened
    }
}

/** The one [ShortcutQuiet] of the app: the companion's card and the notification of FasterWatch. */
val ShortcutGuard = ShortcutQuiet()

/**
 * The change whose connecting departures are loaded: the one picked by hand ([chosen]) while
 * it still lies ahead, else the first of those [ahead] (keepLegs, in order). Null without one.
 */
fun activeChangeKeepLegs(chosen: Int?, ahead: List<Int>, broken: Int? = null, opened: Int? = null): Int? = when {
    // The picker open by hand gets its departures: a tap on "Anschluss wechseln" always opens (#171).
    opened != null && opened in ahead -> opened
    // A change ahead that a delay broke needs a new choice first (#98).
    broken != null && broken in ahead -> broken
    chosen != null && chosen in ahead -> chosen
    else -> ahead.firstOrNull()
}

/**
 * Whether "Missed it" belongs in the bar: not on board, and the ride to board leaves within
 * [MISSED_RELEVANT_MINUTES] or has left ([minutesToDeparture] below 0). Null minutes: no ride.
 */
fun missedRelevant(onBoard: Boolean, minutesToDeparture: Long?): Boolean =
    !onBoard && minutesToDeparture != null && minutesToDeparture <= MISSED_RELEVANT_MINUTES

/** Up to this many minutes before the departure of the ride to board, "Missed it" is offered. */
const val MISSED_RELEVANT_MINUTES = 30L

/** The one subline of the compact blue box: the non-empty [parts] joined by a middle dot. */
fun heroSubline(parts: List<String?>): String = parts.mapNotNull { it?.trim()?.takeIf { s -> s.isNotEmpty() } }.joinToString(" · ")

/**
 * Whether the map of the app exists at all: not while the calm companion is the page in front
 * and settled ([calmAlone]); then no map view is made or kept, and it is made (and fades in)
 * once anything else needs it.
 */
fun mapComposed(calmAlone: Boolean): Boolean = !calmAlone

/**
 * Where the way to the platform starts (#87): at the own position [own] where it is recent (at
 * most [OWN_FIX_MAX_AGE_MILLIS] old, [ownAgeMillis]) and within [PLATFORM_WALK_OWN_METERS] of the
 * [station], else at the station's point.
 */
fun platformWalkStart(station: Pair<Double, Double>, own: Pair<Double, Double>?, ownAgeMillis: Long): Pair<Double, Double> =
    if (own != null && ownAgeMillis in 0..OWN_FIX_MAX_AGE_MILLIS && routeMeters(listOf(own, station)) <= PLATFORM_WALK_OWN_METERS) own else station

/** The own position counts for the way to the platform within this distance of the station. */
const val PLATFORM_WALK_OWN_METERS = 300.0

/** A position of the device older than this no longer says where the traveller is. */
const val OWN_FIX_MAX_AGE_MILLIS = 2 * 60_000L

/**
 * The last position of the device the app took for the dot on the map (written by the root of
 * the app, see usePosition), with when it came (uptime ms). Plain fields: read when composing.
 */
object RecentOwnPosition {
    @Volatile
    var position: Pair<Double, Double>? = null
        private set

    @Volatile
    var atMillis: Long = 0L
        private set

    fun update(position: Pair<Double, Double>, atMillis: Long) {
        this.position = position
        this.atMillis = atMillis
    }
}

/** A change missed by a delay stays one to choose anew at for this long after its connection left (#171). */
const val MISSED_CHANGE_AHEAD_MINUTES = 30L

/**
 * Whether a change still lies ahead at [now] for its picker and its connecting departures: its
 * connection ([nextDeparture]) has not left yet, or the ride into it ([arrivingArrival], by the
 * ride state) has not arrived yet, or it can no longer be made ([possible] false: a delay) and
 * its connection left less than [MISSED_CHANGE_AHEAD_MINUTES] ago. #171: the connection left
 * while the traveller was still on the late train; the change counted as passed, so its picker
 * never opened ("Anschluss wechseln" did nothing) and no other connection was loaded.
 */
fun changeAhead(nextDeparture: java.time.OffsetDateTime, arrivingArrival: java.time.OffsetDateTime, possible: Boolean, now: java.time.OffsetDateTime): Boolean =
    now.isBefore(nextDeparture) ||
        (!possible && (now.isBefore(arrivingArrival) || now.isBefore(nextDeparture.plusMinutes(MISSED_CHANGE_AHEAD_MINUTES))))


// The chip "Andere Wege" (#243) decides by waysChipTap and waysChipOn (data/ChangeTileRow.kt).
