package io.github.jnbp.nextstop.data

import kotlin.math.ceil

/*
 * The transfer levels by walking pace. The walks the app measures itself (changes over the
 * walking network of OpenStreetMap, transfer-walks-*.json; the walks it adds between two
 * platforms of one complex, PlatformChange; walks between stop positions of buses and trams)
 * are timed at the usual pace of 75 m a minute, stairs, escalators and lifts included.
 *
 * - Relaxed and normal: the walk at the usual pace and its buffer (changeNeed), and 5 or 1
 *   minutes to spare beyond that.
 * - Knapp: the change still works at a brisk pace, the walk x [BRISK_WALK_FACTOR] and no buffer
 *   beyond rounding up to whole minutes (changeBriskNeed), and it is at least
 *   [MIN_KNAPP_MINUTES] long.
 * - Sprint: anything else; only by running or by luck (and every change of 0 or 1 minute).
 *
 * "Langsamer unterwegs" (luggage, a pram, a walking aid; SearchOptions.slower) makes every walk
 * [SLOWER_WALK_FACTOR] as long, at every level, and asks the providers for slow walking.
 */

/** Every walk takes this long with "Langsamer unterwegs". */
const val SLOWER_WALK_FACTOR = 1.3

/** A brisk pace: the walk takes this share of its usual time. */
const val BRISK_WALK_FACTOR = 0.8

/** A change shorter than this is a sprint whatever its walk: getting off and on alone takes that. */
const val MIN_KNAPP_MINUTES = 2L

/** The smallest and the largest own factor of "Langsamer unterwegs" (#198), and its step. */
const val MIN_OWN_WALK_FACTOR = 0.8
const val MAX_OWN_WALK_FACTOR = 2.0
const val OWN_WALK_FACTOR_STEP = 0.05

/**
 * The factor on every walk for "Langsamer unterwegs" ([slower]) or not; an own factor ([own],
 * "Eigener Faktor", #198) wins, kept within its range (see [ownWalkFactor]).
 */
fun walkFactor(slower: Boolean, own: Double? = null): Double =
    own?.let { ownWalkFactor(it) } ?: if (slower) SLOWER_WALK_FACTOR else 1.0

/**
 * [value] as an own factor: within [MIN_OWN_WALK_FACTOR]..[MAX_OWN_WALK_FACTOR], on a step of
 * [OWN_WALK_FACTOR_STEP]; [SLOWER_WALK_FACTOR] for a value that is no number (a broken store).
 */
fun ownWalkFactor(value: Double): Double {
    if (value.isNaN() || value.isInfinite()) return SLOWER_WALK_FACTOR
    val steps = kotlin.math.round(value.coerceIn(MIN_OWN_WALK_FACTOR, MAX_OWN_WALK_FACTOR) / OWN_WALK_FACTOR_STEP)
    // Two decimals, so that 1.35 stays 1.35 and compares equal after storing.
    return kotlin.math.round(steps * OWN_WALK_FACTOR_STEP * 100.0) / 100.0
}

/** The whole minutes a walk of [minutes] at the usual pace takes at [factor] (the preview "5 min werden zu 7 min"). */
fun pacedWalkMinutes(minutes: Int, factor: Double): Int =
    if (minutes <= 0) minutes else ceil(minutes * factor - 1e-9).toInt()

/**
 * The walking speed to ask the providers for at [factor]: slow from 1.15 on (1.3, the factor of
 * "Langsamer unterwegs", is slow), fast up to 0.9, else normal. The walks the app times itself
 * use the exact factor.
 */
fun walkingSpeedFor(factor: Double): WalkingSpeed = when {
    factor >= 1.15 -> WalkingSpeed.Slow
    factor <= 0.9 -> WalkingSpeed.Fast
    else -> WalkingSpeed.Normal
}

/**
 * The walking speed in m/s for a journey search of Transitous (MOTIS, its pedestrianSpeed) with
 * an own factor: its usual 1.2 m/s divided by [factor], two decimals; null for the usual pace.
 */
fun motisPedestrianSpeed(factor: Double): String? {
    if (kotlin.math.abs(factor - 1.0) < 1e-6) return null
    return String.format(java.util.Locale.ROOT, "%.2f", MOTIS_PEDESTRIAN_SPEED / factor)
}

// The usual walking speed of MOTIS (m/s), from which an own factor counts.
private const val MOTIS_PEDESTRIAN_SPEED = 1.2

/** The factor of the setting now (see [walkFactor]); set by the interface. */
@Volatile
var walkSpeedFactor: Double = 1.0
    set(value) {
        if (field == value) return
        field = value
        ratingSettingsChanged()
    }

/** [seconds] of a walk at the pace of [factor], rounded up; 0 (one place) stays 0. */
fun pacedSeconds(seconds: Int, factor: Double = walkSpeedFactor): Int =
    if (seconds <= 0) seconds else ceil(seconds * factor - 1e-9).toInt()

/**
 * The whole minutes a walk measured at [seconds] (usual pace, before [pacedSeconds]) takes at a
 * brisk pace: x [BRISK_WALK_FACTOR], rounded up, at least [MIN_KNAPP_MINUTES].
 */
fun briskMinutes(seconds: Int, factor: Double = walkSpeedFactor): Long {
    val paced = pacedSeconds(seconds, factor)
    if (paced <= 0) return MIN_KNAPP_MINUTES
    return maxOf(MIN_KNAPP_MINUTES, ceil(paced * BRISK_WALK_FACTOR / 60.0 - 1e-9).toLong())
}

/**
 * What the change from [arriving] to [next] needs at a brisk pace by its walk measured over the
 * walking network (see [transferWalkSeconds] and [briskMinutes]); null where it is not known.
 */
fun exactBriskNeed(arriving: Leg, next: Leg, walk: (Leg, Leg) -> Int? = transferWalkSeconds): Long? {
    if (arriving.walking || next.walking) return null
    val seconds = runCatching { walk(arriving, next) }.getOrNull() ?: return null
    return briskMinutes(seconds)
}

/**
 * The minutes of a walk of [walkMinutes] (already at the pace set) at a brisk pace, rounded up,
 * at least [MIN_KNAPP_MINUTES]; null for no walk.
 */
fun briskWalkNeed(walkMinutes: Long): Long? =
    if (walkMinutes <= 0) null else maxOf(MIN_KNAPP_MINUTES, ceil(walkMinutes * BRISK_WALK_FACTOR - 1e-9).toLong())

/**
 * What the change from [arriving] to [next] needs at a brisk pace (the boundary between knapp
 * and Sprint, see [paceTightness]): by its walk measured over the walking network where known
 * ([exactBriskNeed]), else by the walk of [walkMinutes] ([briskWalkNeed]), else what it needs
 * by the rules of thumb ([changeNeed], which have no walk to make brisk); at least
 * [MIN_KNAPP_MINUTES].
 */
fun changeBriskNeed(arriving: Leg, next: Leg, walkMinutes: Long = 0): Long =
    maxOf(
        MIN_KNAPP_MINUTES,
        exactBriskNeed(arriving, next) ?: briskWalkNeed(walkMinutes) ?: changeNeed(arriving, next, walkMinutes),
    )
