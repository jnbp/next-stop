package io.github.jnbp.nextstop.data

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/*
 * On which side of a vehicle the sun shines in on a ride, worked out on the device: the
 * position of the sun from the time and place alone (the usual low-precision formulas of
 * astronomy, good to a fraction of a degree for this century), against the direction of the
 * ride along its way on the map. No network.
 */

/** The side of the vehicle, seen in the direction of travel. */
enum class SunSide { Left, Right }

/**
 * The sunny side a hint shows, from the setting ([on]), the developer test ([test], a side on
 * every ride), the side of the ride ([side], see sunSide) and whether the sun really shines
 * ([shines]: above the horizon, few clouds). Null: no hint. The phone's hint and the watch's
 * (sent in the trip state, #176) alike.
 */
fun shownSunSide(on: Boolean, test: Boolean, side: SunSide?, shines: Boolean): SunSide? = when {
    !on -> null
    test -> side ?: SunSide.Left
    shines -> side
    else -> null
}

/** Where the sun stands: [azimuth] in degrees clockwise from north, [elevation] in degrees above the horizon. */
data class SunPosition(val azimuth: Double, val elevation: Double)

/** Rides shorter than this get no hint about the sun: a seat on the other side is not worth it. */
const val SUN_SIDE_MIN_MINUTES = 20L

// The sun must stand at least this high to shine in (lower, buildings and trees are in the way).
private const val SUN_MIN_ELEVATION = 5.0

// A clear side needs the sun in at least this share of the ride ...
private const val SUN_MIN_DAYLIGHT_SHARE = 0.5

// ... and at least this strong a lead of one side (the mean of the sine of the angle to the
// direction of travel: 1 is the sun square to the side all the way, 0 ahead, behind or even).
private const val SUN_MIN_LEAD = 0.3

/** A ride with less than this share of its way above ground gets no hint about the sun (#219). */
const val SUN_MIN_OPEN_SHARE = 0.25

/** ... nor one with less than this many minutes above ground. */
const val SUN_MIN_OPEN_MINUTES = 3.0

/**
 * Whether enough of a ride of [durationMillis] is above ground for a hint about the sun:
 * [openShare] (0..1) of its way, at an even pace, at least [SUN_MIN_OPEN_SHARE] and
 * [SUN_MIN_OPEN_MINUTES].
 */
fun enoughAboveGround(openShare: Double, durationMillis: Long): Boolean =
    openShare >= SUN_MIN_OPEN_SHARE - 1e-9 && openShare * durationMillis / 60_000.0 >= SUN_MIN_OPEN_MINUTES

// The way is looked at in no more than this many pieces.
private const val SUN_MAX_PIECES = 48

private const val DEGREES = 180.0 / PI
private const val RADIANS = PI / 180.0

/** The position of the sun at [epochMillis] (UTC) seen from [latitude], [longitude]. */
fun sunPosition(epochMillis: Long, latitude: Double, longitude: Double): SunPosition {
    // Days since the epoch J2000.0 (2000-01-01 12:00 UTC).
    val days = epochMillis / 86_400_000.0 - 10_957.5
    val meanLongitude = normalize(280.460 + 0.9856474 * days)
    val anomaly = normalize(357.528 + 0.9856003 * days) * RADIANS
    val eclipticLongitude = (meanLongitude + 1.915 * sin(anomaly) + 0.020 * sin(2 * anomaly)) * RADIANS
    val obliquity = (23.439 - 0.0000004 * days) * RADIANS
    val rightAscension = atan2(cos(obliquity) * sin(eclipticLongitude), cos(eclipticLongitude))
    val declination = asin(sin(obliquity) * sin(eclipticLongitude))
    // The local sidereal time in degrees, and with it the hour angle of the sun.
    val sidereal = normalize(280.46061837 + 360.98564736629 * days + longitude)
    val hourAngle = (sidereal * RADIANS) - rightAscension
    val phi = latitude * RADIANS
    val elevation = asin(sin(phi) * sin(declination) + cos(phi) * cos(declination) * cos(hourAngle))
    val azimuth = atan2(sin(hourAngle), cos(hourAngle) * sin(phi) - tan(declination) * cos(phi)) * DEGREES + 180.0
    return SunPosition(normalize(azimuth), elevation * DEGREES)
}

/** The direction from the first point to the second, in degrees clockwise from north. */
fun bearingDegrees(fromLatitude: Double, fromLongitude: Double, toLatitude: Double, toLongitude: Double): Double {
    val phi1 = fromLatitude * RADIANS
    val phi2 = toLatitude * RADIANS
    val delta = (toLongitude - fromLongitude) * RADIANS
    val y = sin(delta) * cos(phi2)
    val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(delta)
    return normalize(atan2(y, x) * DEGREES)
}

/**
 * The side the sun shines in on a ride along [path] (latitude, longitude) from [departureMillis]
 * to [arrivalMillis], the vehicle moving at an even pace along it; null where the ride is
 * shorter than [SUN_SIDE_MIN_MINUTES], the sun is down or low for much of it, or no side
 * clearly gets more of it (ahead, behind, or now this side, now that one).
 *
 * With [underground] (see RailTunnels, #219) only the parts of the way above ground count:
 * a piece whose middle lies underground is left out, and where less than
 * [SUN_MIN_OPEN_SHARE] of the way or less than [SUN_MIN_OPEN_MINUTES] of the ride is above
 * ground (a line fully in a tunnel, like most of the U-Bahn), there is no hint at all.
 */
fun sunSide(
    path: List<Pair<Double, Double>>,
    departureMillis: Long,
    arrivalMillis: Long,
    underground: ((Double, Double) -> Boolean)? = null,
): SunSide? {
    val duration = arrivalMillis - departureMillis
    if (duration < SUN_SIDE_MIN_MINUTES * 60_000L || path.size < 2) return null
    // The length of the way so far at each point, to place each piece in time.
    val along = DoubleArray(path.size)
    for (index in 1 until path.size) {
        along[index] = along[index - 1] + flatDistance(path[index - 1], path[index])
    }
    val total = along.last()
    if (total <= 0.0) return null
    // Pieces of even length; each looks at its own direction and the sun at its middle.
    // With tunnels, always the most pieces: a way of few points still finds its open parts.
    val pieces = if (underground != null) SUN_MAX_PIECES else minOf(SUN_MAX_PIECES, path.size - 1).coerceAtLeast(1)
    var lit = 0.0
    var lead = 0.0
    var openShare = 0.0
    var cursor = 0
    for (piece in 0 until pieces) {
        val startShare = piece.toDouble() / pieces
        val endShare = (piece + 1).toDouble() / pieces
        val start = pointAt(path, along, total * startShare, cursor).also { cursor = it.second }.first
        val end = pointAt(path, along, total * endShare, cursor).first
        if (underground != null && underground((start.first + end.first) / 2, (start.second + end.second) / 2)) continue
        openShare += 1.0 / pieces
        if (start.first == end.first && start.second == end.second) continue
        val middleMillis = departureMillis + (duration * (startShare + endShare) / 2).toLong()
        val sun = sunPosition(middleMillis, (start.first + end.first) / 2, (start.second + end.second) / 2)
        if (sun.elevation < SUN_MIN_ELEVATION) continue
        val heading = bearingDegrees(start.first, start.second, end.first, end.second)
        // Positive: the sun lies to the right of the direction of travel.
        lead += sin((sun.azimuth - heading) * RADIANS) / pieces
        lit += 1.0 / pieces
    }
    if (underground != null && !enoughAboveGround(openShare, duration)) return null
    // The daylight share counts on the parts above ground alone.
    if (openShare <= 0.0 || lit / openShare < SUN_MIN_DAYLIGHT_SHARE) return null
    val mean = lead / lit
    return when {
        abs(mean) < SUN_MIN_LEAD -> null
        mean > 0 -> SunSide.Right
        else -> SunSide.Left
    }
}

/** The other side: where the shade is. */
val SunSide.shade: SunSide get() = if (this == SunSide.Left) SunSide.Right else SunSide.Left

// The point [distance] along the way, and the index of the piece of [path] it lies on (to go on from there).
private fun pointAt(path: List<Pair<Double, Double>>, along: DoubleArray, distance: Double, from: Int): Pair<Pair<Double, Double>, Int> {
    var index = from.coerceIn(0, path.size - 2)
    while (index < path.size - 2 && along[index + 1] < distance) index++
    val span = along[index + 1] - along[index]
    val share = if (span <= 0.0) 0.0 else ((distance - along[index]) / span).coerceIn(0.0, 1.0)
    val a = path[index]
    val b = path[index + 1]
    return (a.first + (b.first - a.first) * share to a.second + (b.second - a.second) * share) to index
}

// A distance good enough to compare pieces of one way: degrees, longitude shrunk by the latitude.
private fun flatDistance(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
    val dy = b.first - a.first
    val dx = (b.second - a.second) * cos((a.first + b.first) / 2 * RADIANS)
    return sqrt(dx * dx + dy * dy)
}

private fun normalize(degrees: Double): Double {
    val rest = degrees % 360.0
    return if (rest < 0) rest + 360.0 else rest
}

/**
 * Whether the explanation of the sunny side opens now (#90): the first time a hint of the
 * sunny side is shown ([hintShown]) and the explanation was never seen ([seen]); never while
 * the hint is off, and only once at a time ([open]: one is open already).
 */
fun sunIntroDue(seen: Boolean, hintShown: Boolean, open: Boolean): Boolean = !seen && hintShown && !open

/**
 * Where the sun of the explanation's picture stands at [phase] (0..1, there and back again):
 * on an arc over the sunny side of the train, as shares of the picture's width and height
 * (x from 0.18 to 0.82, highest, at y 0.16, in the middle; 0.30 at both ends).
 */
fun sunIntroSunPoint(phase: Float): Pair<Float, Float> {
    val p = phase.coerceIn(0f, 1f)
    val x = 0.18f + 0.64f * p
    val d = (p - 0.5f) * 2f
    return x to (0.16f + 0.14f * d * d)
}
