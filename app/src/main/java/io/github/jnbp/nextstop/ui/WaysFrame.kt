package io.github.jnbp.nextstop.ui

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/*
 * What the map of "My routes" frames: the user's home region, not every place ever saved.
 * One place in another city (a trip to Dresden beside a life in Berlin) would otherwise pull
 * the picture out to the empty land in between, with every pin of the home city in a heap.
 * Pure logic, no Android.
 */

/** Places farther than this from the middle of the home region are left out of the frame. */
const val WAYS_REGION_METERS = 40_000.0

/**
 * The middle of the home region among [places]: [home] where there is one; else the place
 * with the most others within [radiusMeters] (the first of them on a tie); else, without any
 * place, [here]. Null with neither.
 */
fun homeRegionAnchor(
    places: List<Pair<Double, Double>>,
    home: Pair<Double, Double>?,
    here: Pair<Double, Double>?,
    radiusMeters: Double = WAYS_REGION_METERS,
): Pair<Double, Double>? {
    if (home != null) return home
    if (places.isEmpty()) return here
    return places.maxByOrNull { place -> places.count { regionMeters(place, it) <= radiusMeters } }
}

/** True when [point] lies in the home region around [anchor] (see homeRegionAnchor); everything does without an anchor. */
fun inHomeRegion(point: Pair<Double, Double>, anchor: Pair<Double, Double>?, radiusMeters: Double = WAYS_REGION_METERS): Boolean =
    anchor == null || regionMeters(point, anchor) <= radiusMeters

private fun regionMeters(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
    val north = (b.first - a.first) * 111_320.0
    val east = (b.second - a.second) * 111_320.0 * cos((a.first + b.first) / 2 * PI / 180)
    return sqrt(north * north + east * east)
}
