package io.github.jnbp.nextstop.data

import kotlin.math.cos
import kotlin.math.sqrt

/*
 * What OpenStreetMap knows along a walk at a station: the stop positions of the bus and tram
 * stops at its ends (the one it leads to and the others), the entrances and exits it passes
 * and the lifts and escalators on its way. For the small maps of walks and their one-line
 * summary ("Ausgang B · Rolltreppe · zu Pos. 9"), at every station with OSM data.
 */

/** A thing a walk passes: an entrance or exit, a lift or an escalator, with [along] its place on the way (0 start, 1 end). */
data class WalkPassage(
    val kind: PassageKind,
    val label: String?,
    val latitude: Double,
    val longitude: Double,
    val along: Double,
    /** For an entrance: an access through a building. */
    val throughBuilding: Boolean = false,
    /** For an entrance: true when the walk leaves a station through it (an exit), false when it enters one. */
    val leaving: Boolean = false,
    /**
     * For a lift or escalator: 1 when the walk goes up with it, -1 down, 0 where the levels
     * along the way do not tell (see [climbs]).
     */
    val climb: Int = 0,
) {
    val position: Pair<Double, Double> get() = latitude to longitude
}

/** What a walk passes; [Stairs] only as the way up (or down) to the exit it leaves by (see WalkTarget.climbBy). */
enum class PassageKind { Entrance, Lift, Escalator, Stairs }

/**
 * The details of a walk: [positions] all stop positions of the stop areas at its ends, [target]
 * the one it leads to and [start] the one it starts at (null where it neither starts nor ends at
 * one), [passages] the entrances, lifts and escalators on its way in their order, and
 * [startsInStation] true when it starts inside a station complex (its first entrance is an exit).
 */
data class WalkDetails(
    val positions: List<OsmAreaPosition>,
    val target: OsmAreaPosition?,
    val start: OsmAreaPosition?,
    val passages: List<WalkPassage>,
    val startsInStation: Boolean,
    /** True when the walk ends inside a station complex (at an entrance it aims for, or at a platform). */
    val endsInStation: Boolean = false,
) {
    val isEmpty: Boolean get() = positions.isEmpty() && passages.isEmpty()

    companion object {
        val EMPTY = WalkDetails(emptyList(), null, null, emptyList(), false)
    }
}

// An entrance this close to the way counts as passed; a lift or escalator this close as used.
private const val ENTRANCE_ON_WAY_METERS = 14.0
private const val LIFT_ON_WAY_METERS = 9.0

// The stop position the walk starts or ends at lies this close to that end.
private const val END_POSITION_METERS = 20.0

// The most entrances and lifts named for one walk.
private const val MAX_ENTRANCES = 2
private const val MAX_LIFTS = 3

/**
 * The details of the walk along [points] (from start to end) from the loaded OSM data;
 * [targetPos] and [startPos] are the stop position numbers the walk leads to or starts at,
 * where known (they win over the nearest position); [entrance] and [exit] the station
 * entrance it aims for and the exit it leaves by, always counted as passed.
 */
fun walkDetails(
    points: List<Pair<Double, Double>>,
    targetPos: String? = null,
    startPos: String? = null,
    entrance: WalkTarget? = null,
    exit: WalkTarget? = null,
): WalkDetails {
    if (points.size < 2 || !OsmStations.loaded) return WalkDetails.EMPTY
    val first = points.first()
    val last = points.last()
    val areas = (OsmStations.areasNear(last) + OsmStations.areasNear(first)).distinct()
    val positions = areas.flatMap { it.positions }.distinct()
    fun endPosition(end: Pair<Double, Double>, number: String?): OsmAreaPosition? {
        val wanted = platformNumber(number)
        val near = positions.filter { metersBetween(end, it.position) <= END_POSITION_METERS * if (wanted != null) 3 else 1 }
        return (if (wanted != null) near.filter { platformNumber(it.pos) == wanted }.minByOrNull { metersBetween(end, it.position) } else null)
            ?: near.minByOrNull { metersBetween(end, it.position) }?.takeIf { metersBetween(end, it.position) <= END_POSITION_METERS }
    }
    val target = endPosition(last, targetPos)
    val start = endPosition(first, startPos)?.takeIf { it != target }
    val stations = (OsmStations.stationsNear(first, 500.0) + OsmStations.stationsNear(last, 500.0)).distinct()
    val way = Polyline(points)
    val passages = ArrayList<WalkPassage>()
    for (station in stations) {
        for (e in station.entrances) {
            val (meters, along) = way.nearest(e.position)
            if (meters <= ENTRANCE_ON_WAY_METERS) {
                val label = e.ref ?: e.name?.takeIf { osmNameKey(it) != station.key }
                passages.add(WalkPassage(PassageKind.Entrance, label, e.latitude, e.longitude, along, e.throughBuilding))
            }
        }
        for (lift in station.lifts) {
            val (meters, along) = way.nearest(lift.position())
            if (meters <= LIFT_ON_WAY_METERS) {
                passages.add(WalkPassage(if (lift.escalator) PassageKind.Escalator else PassageKind.Lift, null, lift.latitude, lift.longitude, along))
            }
        }
    }
    // The entrance aimed for and the exit left by count whatever the way does: an exit where
    // the way passes it (a way of the provider through it), else at its start.
    if (exit != null && exit.kind == WalkTargetKind.Elevator) {
        if (passages.none { it.kind == PassageKind.Lift && metersBetween(it.position, exit.position) <= 10.0 }) {
            val along = way.nearest(exit.position).let { (meters, along) -> if (meters <= ENTRANCE_ON_WAY_METERS) along else 0.0 }
            passages.add(WalkPassage(PassageKind.Lift, null, exit.latitude, exit.longitude, along))
        }
    }
    for ((spot, end) in listOf(exit to 0.0, entrance to 1.0)) {
        if (spot == null || spot.kind != WalkTargetKind.Entrance) continue
        if (passages.none { it.kind == PassageKind.Entrance && metersBetween(it.position, spot.position) <= 10.0 }) {
            val along = way.nearest(spot.position).let { (meters, along) -> if (meters <= ENTRANCE_ON_WAY_METERS) along else end }
            passages.add(WalkPassage(PassageKind.Entrance, spot.label, spot.latitude, spot.longitude, along, spot.throughBuilding))
        }
    }
    // One of each kind within a few metres (OSM maps a lift per level, an entrance per door).
    val merged = ArrayList<WalkPassage>()
    for (p in passages.sortedBy { it.along }) {
        if (merged.any { it.kind == p.kind && metersBetween(it.position, p.position) <= 12.0 }) continue
        merged.add(p)
    }
    val inside = stations.any { metersBetween(first, it.position) <= 150.0 }
    val startPlatform = platformAt(stations, first)
    val endPlatform = platformAt(stations, last)
    val endsInside = (entrance != null && entrance.kind == WalkTargetKind.Entrance) || (target == null && endPlatform != null)
    // Leaving the station the walk starts in, every entrance is an exit; entering the one it
    // ends in, an entrance; a walk between two stations leaves by the first half, enters by the second.
    fun leavingAt(along: Double): Boolean = inside && (!endsInside || along < 0.5)
    val directed = merged.map { if (it.kind == PassageKind.Entrance) it.copy(leaving = leavingAt(it.along)) else it }
    // A walk that only leaves (or only enters) goes through one door: the one it is said to use,
    // else the last one on its way out (the first on its way in). The others are doors it passes inside.
    val doors = directed.filter { it.kind == PassageKind.Entrance }
    val oneDoor: WalkPassage? = when {
        doors.size < 2 -> null
        inside && !endsInside -> exit?.let { spot -> doors.firstOrNull { metersBetween(it.position, spot.position) <= 12.0 } } ?: doors.last()
        !inside && endsInside -> entrance?.let { spot -> doors.firstOrNull { metersBetween(it.position, spot.position) <= 12.0 } } ?: doors.first()
        else -> null
    }
    val passed = leavingBy(
        if (oneDoor != null) directed.filter { it.kind != PassageKind.Entrance || it === oneDoor } else directed,
        exit?.takeIf { inside && !endsInside },
    )
    val entrances = passed.filter { it.kind == PassageKind.Entrance }.let { list ->
        // The labelled ones first, then the order on the way.
        list.sortedBy { if (it.label != null) 0 else 1 }.take(MAX_ENTRANCES).toSet()
    }
    val lifts = passed.filter { it.kind != PassageKind.Entrance }.take(MAX_LIFTS).toSet()
    val kept = passed.filter { it in entrances || it in lifts }
    // The level at each end where known: a platform there, a bus or tram stop in the street (0).
    val startLevel = startPlatform?.level?.let { levelNumbers(it).firstOrNull() }
        ?: if (start != null || (!inside && startPlatform == null)) 0.0 else null
    val endLevel = endPlatform?.takeIf { target == null }?.level?.let { levelNumbers(it).firstOrNull() }
        ?: entrance?.takeIf { it.kind == WalkTargetKind.Entrance }?.level?.let { levelNumbers(it).firstOrNull() }
        ?: if (target != null) 0.0 else null
    val doorLevels = HashMap<WalkPassage, Double>()
    for (door in kept) {
        if (door.kind != PassageKind.Entrance) continue
        val level = stations.flatMap { it.entrances }
            .filter { it.level != null && metersBetween(it.position, door.position) <= 12.0 }
            .minByOrNull { metersBetween(it.position, door.position) }?.level
            ?.let { levelNumbers(it).firstOrNull() }
        if (level != null) doorLevels[door] = level
    }
    val liftLevels = HashMap<WalkPassage, List<Double>>()
    for (lift in kept) {
        if (lift.kind == PassageKind.Entrance) continue
        val level = stations.flatMap { it.lifts }
            .filter { it.escalator == (lift.kind == PassageKind.Escalator) && it.level != null && metersBetween(it.position(), lift.position) <= 12.0 }
            .minByOrNull { metersBetween(it.position(), lift.position) }?.level
        liftLevels[lift] = levelNumbers(level)
    }
    val steps = kept.map { p ->
        when (p.kind) {
            PassageKind.Entrance -> LevelStep(p.along, doorLevels[p], null)
            else -> LevelStep(p.along, null, liftLevels[p].orEmpty())
        }
    }
    val climbOf = climbs(startLevel, steps, endLevel)
    val climbed = kept.mapIndexed { index, p -> if (p.kind == PassageKind.Entrance) p else p.copy(climb = climbOf[index]) }
    return WalkDetails(positions, target, start, withClimbToExit(climbed, exit?.takeIf { inside && !endsInside }), inside, endsInside)
}

/**
 * The [passages] of a walk that only leaves its station, by the access point [exit] (null: as
 * they are): that access is the one way out, named and drawn, and no other door; what lies on
 * the way after it is in the street, not used (an elevator beside the stairs the walk came up).
 * Lifts and escalators before it (inside) stay.
 */
internal fun leavingBy(passages: List<WalkPassage>, exit: WalkTarget?): List<WalkPassage> {
    if (exit == null) return passages
    val kind = if (exit.kind == WalkTargetKind.Elevator) PassageKind.Lift else PassageKind.Entrance
    val used = passages.filter { it.kind == kind }.minByOrNull { metersBetween(it.position, exit.position) }
        ?.takeIf { metersBetween(it.position, exit.position) <= 12.0 }
        ?: return passages.filter { it.kind != PassageKind.Entrance || metersBetween(it.position, exit.position) <= 12.0 }
    return passages.filter { p ->
        when {
            p === used -> true
            p.kind == PassageKind.Entrance -> false
            else -> p.along < used.along
        }
    }
}

/**
 * [passages] with the way up (or down) to the exit [exit] it leaves by, as chosen with it
 * ([WalkTarget.climbBy]): the stairs or the escalator right before the exit door, where nothing
 * on the way before it says how the walk gets there; the elevator it leaves by goes the way
 * the exit says. Unchanged without an exit or where its way up is not known.
 */
internal fun withClimbToExit(passages: List<WalkPassage>, exit: WalkTarget?): List<WalkPassage> {
    val by = exit?.climbBy ?: return passages
    if (exit.kind == WalkTargetKind.Elevator) {
        if (exit.climb == 0) return passages
        return passages.map { p ->
            if (p.kind == PassageKind.Lift && metersBetween(p.position, exit.position) <= 12.0) p.copy(climb = exit.climb) else p
        }
    }
    val door = passages.indexOfFirst { it.kind == PassageKind.Entrance && metersBetween(it.position, exit.position) <= 12.0 }
    if (door < 0) return passages
    // An escalator or lift on the way before the door is the way up already.
    if (passages.subList(0, door).any { it.kind != PassageKind.Entrance }) return passages
    val at = passages[door]
    val climb = WalkPassage(by, null, at.latitude, at.longitude, at.along, climb = exit.climb)
    return passages.subList(0, door) + climb + passages.subList(door, passages.size)
}

/**
 * One thing on the way as the level reckoning sees it: at [along] (0 to 1), a door at [level]
 * where known, or a lift or escalator ([span] not null) between the levels of [span] (empty
 * where not tagged).
 */
internal data class LevelStep(val along: Double, val level: Double?, val span: List<Double>?)

/**
 * For each of [steps] in order: 1 where a lift or escalator there goes up, -1 down, 0 where not
 * known or not a lift (a door). From [startLevel], the levels of the doors passed and [endLevel]:
 * a lift that spans levels goes from the side the walk is on to the other; one without levels
 * goes from the last known level towards the next known one, where they differ.
 */
internal fun climbs(startLevel: Double?, steps: List<LevelStep>, endLevel: Double?): List<Int> {
    val result = IntArray(steps.size)
    var current = startLevel
    for ((index, step) in steps.withIndex()) {
        if (step.span == null) {
            if (step.level != null) current = step.level
            continue
        }
        val next = steps.drop(index + 1).firstOrNull { it.span == null && it.level != null }?.level ?: endLevel
        val low = step.span.minOrNull()
        val high = step.span.maxOrNull()
        val climb = when {
            low != null && high != null && low < high && current != null && current >= high -> -1
            low != null && high != null && low < high && current != null && current <= low -> 1
            low != null && high != null && low < high && next != null && next >= high -> 1
            low != null && high != null && low < high && next != null && next <= low -> -1
            current != null && next != null && next > current -> 1
            current != null && next != null && next < current -> -1
            else -> 0
        }
        result[index] = climb
        // Over a span the walk is on its other side; without one it is not known where it got
        // to, so the next one without levels reads the same way (two escalators down in a row).
        current = when {
            climb > 0 && high != null && low != null && low < high -> high
            climb < 0 && high != null && low != null && low < high -> low
            else -> current
        }
    }
    return result.toList()
}

/** The levels of an OSM level tag ("-1;0", "1", "-2;-1;0"); empty where there are none to read. */
internal fun levelNumbers(level: String?): List<Double> =
    level?.split(';')?.mapNotNull { it.trim().toDoubleOrNull() }.orEmpty()

/** The platform of [stations] whose axis passes within 25 m of [point], the nearest; null where none. */
private fun platformAt(stations: List<OsmStation>, point: Pair<Double, Double>): OsmPlatform? =
    stations.flatMap { it.platforms }
        .map { it to segmentMeters(point, it.a, it.b) }
        .filter { it.second <= 25.0 }
        .minByOrNull { it.second }?.first

private fun OsmLift.position(): Pair<Double, Double> = latitude to longitude

/** A way in local metres, for the nearest point to a thing and how far along the way it lies. */
private class Polyline(private val points: List<Pair<Double, Double>>) {
    private val scale = cos(Math.toRadians(points.first().first)) * 111_320.0
    private val xs = DoubleArray(points.size) { points[it].second * scale }
    private val ys = DoubleArray(points.size) { points[it].first * 111_320.0 }
    private val cumulative = DoubleArray(points.size).also { c ->
        for (i in 1 until points.size) c[i] = c[i - 1] + hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
    }
    private val total = cumulative.last().coerceAtLeast(1e-6)

    /** The distance from [point] to the way in metres and its place on the way (0 to 1). */
    fun nearest(point: Pair<Double, Double>): Pair<Double, Double> {
        val px = point.second * scale
        val py = point.first * 111_320.0
        var best = Double.MAX_VALUE
        var bestAlong = 0.0
        for (i in 0 until points.size - 1) {
            val dx = xs[i + 1] - xs[i]
            val dy = ys[i + 1] - ys[i]
            val length2 = dx * dx + dy * dy
            val t = if (length2 <= 0.0) 0.0 else (((px - xs[i]) * dx + (py - ys[i]) * dy) / length2).coerceIn(0.0, 1.0)
            val d = hypot(px - (xs[i] + t * dx), py - (ys[i] + t * dy))
            if (d < best) {
                best = d
                bestAlong = (cumulative[i] + t * sqrt(length2)) / total
            }
        }
        return best to bestAlong
    }

    private fun hypot(a: Double, b: Double): Double = sqrt(a * a + b * b)
}

private fun metersBetween(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
    val north = (b.first - a.first) * 111_320.0
    val east = (b.second - a.second) * 111_320.0 * cos(Math.toRadians((a.first + b.first) / 2))
    return sqrt(north * north + east * east)
}
