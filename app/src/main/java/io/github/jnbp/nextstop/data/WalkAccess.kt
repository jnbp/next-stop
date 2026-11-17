package io.github.jnbp.nextstop.data

/*
 * The one access point a walk leaves its station by: an entrance (stairs, a door) or an
 * elevator up to the street. Chosen once (OsmStations.refine, as the walk's walkExit) and from
 * then on the one source for the way drawn, the marks on the map and the words of the walk
 * ("Aufzug hoch · zu Pos. 1"): the way is led through it, and the summary names it and no
 * other door. Before this, the exit was chosen by straight lines while the provider's own way
 * (MOTIS routes through the elevators of OSM) went out by another one, and a refresh kept the
 * old way with the new exit: the text said "Ausgang A" while the line went by the elevator.
 * Elevators are taken only by a traveller who needs step-free ways (the accessibility setting,
 * stepFreeWalks); everybody else leaves by an entrance, up the escalator beside it or the
 * stairs ("Treppe hoch · Ausgang A", see withWayUp).
 */
object WalkAccess {
    /** A provider's way passes an access point when it comes this close to it (it runs through its node). */
    const val USED_METERS = 4.0

    /** Only so far along a provider's way from its start an access point it uses is looked for. */
    const val WINDOW_METERS = 400.0

    /** A door this soon after an elevator of unknown levels on the way is the way out (the lift was inside). */
    const val DOOR_AFTER_LIFT_METERS = 40.0

    /**
     * The access point of [accesses] (entrances and elevators of the station, as walk ends)
     * that the provider's way [path] (from the platform outward) actually goes through: those
     * it comes within [USED_METERS] of in its first [WINDOW_METERS], the first along it. An
     * elevator known to stay below the street ([levels] all below 0) is no way out; an
     * elevator of unknown levels followed shortly by a door is one inside, and the door the
     * way out. Null for a straight way (two points) and where the way passes none.
     */
    fun usedBy(
        path: List<Pair<Double, Double>>,
        accesses: List<WalkTarget>,
        levels: (WalkTarget) -> List<Double> = { levelNumbers(it.level) },
    ): WalkTarget? {
        if (path.size < 3 || accesses.isEmpty()) return null
        val used = accesses.mapNotNull { access ->
            val (meters, along) = nearestAlong(path, access.position)
            if (meters > USED_METERS || along > WINDOW_METERS) return@mapNotNull null
            if (access.kind == WalkTargetKind.Elevator) {
                val span = levels(access)
                if (span.isNotEmpty() && span.all { it < 0.0 }) return@mapNotNull null
            }
            access to along
        }.sortedBy { it.second }
        val first = used.firstOrNull() ?: return null
        if (first.first.kind == WalkTargetKind.Elevator && levels(first.first).isEmpty()) {
            val door = used.firstOrNull { it.first.kind == WalkTargetKind.Entrance && it.second > first.second }
            if (door != null && door.second - first.second <= DOOR_AFTER_LIFT_METERS) return door.first
        }
        return first.first
    }

    /**
     * The way of a walk out of its station through [exit]: a provider's way that already goes
     * through it ([usedBy]) stays as it is, from the platform through the access to the street;
     * any other way is led from the access (cut where the access lies beside it, see
     * [PathJoin.attachStart]); a straight walk runs from the access to its end ([end] where it
     * has no way).
     */
    fun pathOut(path: List<Pair<Double, Double>>, exit: WalkTarget, end: Pair<Double, Double>? = null): List<Pair<Double, Double>> =
        when {
            path.size > 2 && passes(path, exit) -> path
            path.size > 2 -> PathJoin.attachStart(path.drop(1), exit.position, always = true)
            path.size == 2 -> listOf(exit.position, path.last())
            else -> listOfNotNull(exit.position, end).takeIf { it.size == 2 } ?: path
        }

    /** True when [path] goes through [access] (within [USED_METERS], anywhere along it). */
    fun passes(path: List<Pair<Double, Double>>, access: WalkTarget): Boolean =
        path.size >= 2 && nearestAlong(path, access.position).first <= USED_METERS

    /**
     * What of a walk to keep when fresh live data comes for it while its way stays the known one
     * ([known]): the way, the target and the exit belong together, so they are taken as one,
     * from [known]; only where [known] was never led anywhere (no target, no exit; the station
     * data was not there yet) and [fresh] was, from [fresh]. Rides keep the known way as before.
     */
    fun keptWay(fresh: Leg, known: Leg): Leg = when {
        !fresh.walking || !known.walking -> fresh.copy(path = known.path)
        known.walkTarget == null && known.walkExit == null && (fresh.walkTarget != null || fresh.walkExit != null) -> fresh
        else -> fresh.copy(path = known.path, walkTarget = known.walkTarget, walkExit = known.walkExit)
    }

    /** The distance from [point] to [path] in metres and the metres along [path] to its nearest point. */
    internal fun nearestAlong(path: List<Pair<Double, Double>>, point: Pair<Double, Double>): Pair<Double, Double> {
        if (path.size == 1) return PathJoin.meters(path[0], point) to 0.0
        val hit = PathJoin.nearest(path, point)
        var along = 0.0
        for (i in 0 until hit.index) along += PathJoin.meters(path[i], path[i + 1])
        along += PathJoin.meters(path[hit.index], hit.point)
        return hit.meters to along
    }
}

/** An escalator this close to an exit leads up (or down) to it; else the walk takes the stairs. */
const val ESCALATOR_AT_EXIT_METERS = 30.0

/**
 * [exit] with how the walk gets there from a platform at [platformLevel] (a level as tagged,
 * null where not known): an elevator by itself; an entrance by an escalator where one of
 * [escalators] lies within [ESCALATOR_AT_EXIT_METERS] of it (the stairs are shorter only where
 * there is none), else by the stairs; up where the entrance (its level, the street, 0, where
 * it has none) lies above the platform, down where below. Unchanged where the platform's level
 * is not known or the same: nothing to climb is said then.
 */
fun WalkAccess.withWayUp(exit: WalkTarget, platformLevel: Double?, escalators: List<Pair<Double, Double>>): WalkTarget {
    if (platformLevel == null) return if (exit.kind == WalkTargetKind.Elevator) exit.copy(climbBy = PassageKind.Lift) else exit
    val top = if (exit.kind == WalkTargetKind.Elevator) {
        levelNumbers(exit.level).filter { it != platformLevel }.maxByOrNull { kotlin.math.abs(it - platformLevel) } ?: 0.0
    } else {
        levelNumbers(exit.level).firstOrNull() ?: 0.0
    }
    val climb = when {
        top > platformLevel -> 1
        top < platformLevel -> -1
        else -> 0
    }
    if (exit.kind == WalkTargetKind.Elevator) return exit.copy(climbBy = PassageKind.Lift, climb = climb)
    if (climb == 0) return exit
    val escalator = escalators.any { PathJoin.meters(it, exit.position) <= ESCALATOR_AT_EXIT_METERS }
    return exit.copy(climbBy = if (escalator) PassageKind.Escalator else PassageKind.Stairs, climb = climb)
}

/**
 * For a traveller who needs step-free ways: the elevator of [accesses] on the shortest way
 * from the platforms [platforms] (their ends, else the point [inside]) to [outside], where the
 * walk ends (straight lines); null where the station has none.
 */
fun WalkAccess.nearestElevator(
    accesses: List<WalkTarget>,
    outside: Pair<Double, Double>,
    platforms: List<Pair<Pair<Double, Double>, Pair<Double, Double>>>,
    inside: Pair<Double, Double>?,
): WalkTarget? {
    fun inner(lift: WalkTarget): Double = when {
        platforms.isNotEmpty() -> platforms.minOf { (a, b) -> PathJoin.nearest(listOf(a, b), lift.position).meters }
        inside != null -> PathJoin.meters(inside, lift.position)
        else -> 0.0
    }
    return accesses.filter { it.kind == WalkTargetKind.Elevator }
        .filter { lift -> levelNumbers(lift.level).let { it.isEmpty() || it.any { level -> level >= 0.0 } } }
        .minByOrNull { PathJoin.meters(outside, it.position) + inner(it) }
}
