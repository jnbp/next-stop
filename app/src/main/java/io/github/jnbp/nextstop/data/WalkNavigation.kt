package io.github.jnbp.nextstop.data

/**
 * Where the button "Google Maps" of a walk leads: the point OpenStreetMap leads it to (a stop
 * position "Pos. 4", an entrance), else where the ride after it leaves ([nextRide]), else the
 * end of the walk or of its way. Null where none of them has a position.
 */
fun walkNavigationTarget(walk: Leg, nextRide: Leg? = null): Pair<Double, Double>? =
    walk.walkTarget?.position ?: nextRide?.fromPlace?.position ?: walk.toPlace?.position ?: walk.path.lastOrNull()

/**
 * True when a walk offers "Google Maps": every walk with a point to go to ([target]) that is not
 * [over] yet. Also a walk the provider times at 0 minutes, as to another stop position of the
 * same station ("Zoo, Pos. 4"): it still leads somewhere else (#131).
 */
fun offersWalkNavigation(target: Pair<Double, Double>?, over: Boolean): Boolean = target != null && !over
