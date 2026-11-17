package io.github.jnbp.nextstop.data

/*
 * Whether a change between two rides gets a small map in the journey detail where it has no
 * walk leg of its own (or one of no minutes): every change where the next ride is boarded
 * elsewhere than the ride before is left, also from a stop in the street to a platform of the
 * same name, also where OpenStreetMap knows neither stop position.
 */

// Two ends of a change further apart than this are two places, whatever their stops say.
const val CHANGE_MAP_METERS = 30.0

/** Where a change is walked: from [start], where the ride before is left, to [end], where the next is boarded. */
data class ChangeMapEnds(val start: Pair<Double, Double>, val end: Pair<Double, Double>)

/**
 * The two ends of the change from [arriving] to [next] for a small map: the end of the way of
 * the arriving ride, else its stop position from OpenStreetMap, else [alightPlatform] (a
 * platform of a rail ride), else its stop's own point; the same at the start of [next] with
 * [boardPlatform]. Null for a walk and where an end is not known at all.
 */
fun changeMapEnds(
    arriving: Leg,
    next: Leg,
    alightPlatform: Pair<Double, Double>? = null,
    boardPlatform: Pair<Double, Double>? = null,
): ChangeMapEnds? {
    if (arriving.walking || next.walking) return null
    val start = arriving.path.lastOrNull() ?: arriving.alightingSpot?.position ?: alightPlatform ?: arriving.toPlace?.position
    val end = next.path.firstOrNull() ?: next.boardingSpot?.position ?: boardPlatform ?: next.fromPlace?.position
    if (start == null || end == null) return null
    return ChangeMapEnds(start, end)
}

/**
 * True when [next] is boarded elsewhere than [arriving] is left, by [ends]: more than
 * [CHANGE_MAP_METERS] apart, or at two stops (by their ids) that do not lie on one spot (see
 * [SAME_SPOT_METERS]). False where the ends are not known.
 */
fun changeMovesPlace(arriving: Leg, next: Leg, ends: ChangeMapEnds?): Boolean {
    if (ends == null) return false
    val meters = spotMeters(ends.start, ends.end)
    if (meters > CHANGE_MAP_METERS) return true
    val fromId = arriving.toPlace?.id
    val toId = next.fromPlace?.id
    return fromId != null && toId != null && fromId != toId && meters > SAME_SPOT_METERS
}

/** Which small map a change shows (see changeMapKind). */
enum class ChangeMapKind {
    /** None: the change stays where it is, or nothing of it is known. */
    None,

    /** The map of the walk leg of the change. */
    Walk,

    /** Both stop positions of buses or trams from OpenStreetMap and the way between them. */
    Spots,

    /** From where the ride before is left to where the next is boarded, as known (see changeMapEnds). */
    Change,
}

/**
 * Which small map the change from [arriving] to [next] shows: the map of its walk leg where
 * it has one ([walkHasMap]) with minutes on it ([walkMinutes]) or leading elsewhere; else,
 * also beside a walk leg of no minutes or without a way of its own (as "Other stop position,
 * 90 m on foot" at one stop), the map of two stop positions more than [SAME_SPOT_METERS] apart;
 * else the map of the change wherever it leads elsewhere (see changeMovesPlace) and
 * [samePlatform] does not say otherwise.
 */
fun changeMapKind(
    arriving: Leg,
    next: Leg,
    ends: ChangeMapEnds?,
    walkMinutes: Long = 0,
    walkHasMap: Boolean = false,
    samePlatform: Boolean = false,
): ChangeMapKind {
    if (arriving.walking || next.walking) return ChangeMapKind.None
    val moves = changeMovesPlace(arriving, next, ends)
    if (walkHasMap && (walkMinutes > 0 || moves)) return ChangeMapKind.Walk
    val spots = stopSpotMeters(arriving, next)
    if (spots != null && spots > SAME_SPOT_METERS) return ChangeMapKind.Spots
    if (!samePlatform && moves) return ChangeMapKind.Change
    return ChangeMapKind.None
}
