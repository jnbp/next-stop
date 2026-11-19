package io.github.jnbp.nextstop.ui

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sqrt

/*
 * How often the map work that runs while the map is in view is done: the picture behind the
 * glass (see MapBackground) and the vehicles in their layer (see VehicleLayer). Kept apart from
 * the Android parts so it can be tested on its own.
 */

// Metres per degree of latitude.
private const val METERS_PER_DEGREE = 111_320.0

/** How far apart two points close to each other are drawn on the map at [zoom], in dp. */
fun distanceDp(latitude: Double, longitude: Double, toLatitude: Double, toLongitude: Double, zoom: Double): Double {
    val north = (toLatitude - latitude) * METERS_PER_DEGREE
    val east = (toLongitude - longitude) * METERS_PER_DEGREE * cos(Math.toRadians(latitude))
    val perDp = metersPerDp(zoom, latitude)
    if (perDp <= 0.0) return 0.0
    return sqrt(north * north + east * east) / perDp
}

/**
 * The point drawn ([dx], [dy]) dp from ([latitude], [longitude]) on the screen (right and down
 * positive) at [zoom] with the map turned by [bearing] degrees, into [into] (latitude,
 * longitude) without making a pair: for a dot beside a stop, which a circle layer can only
 * move by its point.
 */
fun offsetByDp(latitude: Double, longitude: Double, dx: Float, dy: Float, zoom: Double, bearing: Double, into: DoubleArray) {
    val perDp = metersPerDp(zoom, latitude)
    val angle = Math.toRadians(bearing)
    val sin = kotlin.math.sin(angle)
    val cos = cos(angle)
    // Screen right is the compass direction bearing + 90, screen down bearing + 180.
    val north = (-dx * sin - dy * cos) * perDp
    val east = (dx * cos - dy * sin) * perDp
    into[0] = latitude + north / METERS_PER_DEGREE
    into[1] = longitude + east / (METERS_PER_DEGREE * cos(Math.toRadians(latitude)))
}

/** How far, in dp, a point placed [reachDp] from its anchor at [fromZoom] is off at [zoom]. */
fun dotDriftDp(reachDp: Float, fromZoom: Double, zoom: Double): Double =
    if (reachDp <= 0f || fromZoom.isNaN()) 0.0 else reachDp * abs(2.0.pow(zoom - fromZoom) - 1.0)

// Far out, below the zoom the stop signs start at, the row of dots beside a station shrinks to
// this share of the row of its badges over DOT_ROW_SHRINK_ZOOMS zoom levels: there is no sign
// to keep clear of, and a dot a sign's width away would look like it stood beside the track.
const val DOT_ROW_FAR_SCALE = 0.3f
const val DOT_ROW_SHRINK_ZOOMS = 1.5

/**
 * How much of the offset of its badge the dot of a vehicle standing at a station keeps at
 * [zoom], where the stop signs are drawn from [signsFrom] on: all of it there and closer (so the
 * dot stays right of the sign and does not move when the badges come in), then less and less
 * further out down to DOT_ROW_FAR_SCALE: the dots stay right next to their station, never jump
 * onto its point.
 */
fun dotRowScale(zoom: Double, signsFrom: Double): Float {
    if (zoom >= signsFrom) return 1f
    val share = ((signsFrom - zoom) / DOT_ROW_SHRINK_ZOOMS).coerceIn(0.0, 1.0).toFloat()
    return 1f - (1f - DOT_ROW_FAR_SCALE) * share
}

/**
 * How far, in dp, a dot placed [reachDp] (at full scale) from its station at [fromZoom] is off
 * at [zoom], with its row scaled by dotRowScale ([signsFrom]): the drift of the zoom and the
 * change of the scale together.
 */
fun dotRowDriftDp(reachDp: Float, fromZoom: Double, zoom: Double, signsFrom: Double): Double {
    if (reachDp <= 0f || fromZoom.isNaN()) return 0.0
    val placed = dotRowScale(fromZoom, signsFrom) * 2.0.pow(zoom - fromZoom)
    return reachDp * abs(placed - dotRowScale(zoom, signsFrom))
}

/** The smallest difference between two compass bearings, in degrees (0 to 180). */
fun bearingApart(a: Double, b: Double): Double {
    val apart = abs(a - b) % 360.0
    return if (apart > 180.0) 360.0 - apart else apart
}

// A vehicle is moved on the map once it has come this far on the screen since it was last
// drawn, in dp. Below it the step is too small to see, so drawing it costs only work.
const val VEHICLE_MIN_STEP_DP = 0.5

// While the camera moves, the vehicles are put into their layer at most this often: the
// whole map moves then anyway, and every update of the layer competes with the gesture.
const val VEHICLE_MOVING_CAMERA_MILLIS = 100L

// In the first seconds of the map (see MapQuiet.fresh), while it loads its tiles, a moving
// camera takes the vehicles at most this often: each update makes the map cut the source into
// tiles and place its symbols anew, beside tiles still coming in.
const val VEHICLE_FRESH_CAMERA_MILLIS = 250L

// The map turned by at least this much, in degrees, places the noses and badge edges anew.
const val VEHICLE_MIN_TURN_DEGREES = 0.5

/**
 * Whether the vehicles have to be put into their layer now. [changed] is true for anything
 * that changes what is drawn other than a small glide: new vehicles, a row beside a stop, a
 * fade, the map turned. [movedDp] is the farthest any vehicle moved on the screen since the
 * last update. While [cameraMoving], updates come at most every [movingEveryMillis] (VEHICLE_MOVING_CAMERA_MILLIS)
 * ([sinceLastMillis] is the time since the last one); [force] always updates (a new style, the
 * layer shown again).
 */
fun vehiclesDue(
    force: Boolean,
    changed: Boolean,
    movedDp: Double,
    cameraMoving: Boolean,
    sinceLastMillis: Long,
    movingEveryMillis: Long = VEHICLE_MOVING_CAMERA_MILLIS,
): Boolean {
    if (force) return true
    if (!changed && movedDp < VEHICLE_MIN_STEP_DP) return false
    return !cameraMoving || sinceLastMillis >= movingEveryMillis
}

// The picture behind the glass is taken this often while the map changes and the camera can
// not move it along (a turn or tilt of the map), and in the first moments after the camera
// came to rest, when the tiles of the new place come in.
const val BACKDROP_FAST_MILLIS = 100L

// How long after the camera came to rest the picture is renewed at the fast pace.
const val BACKDROP_SETTLE_MILLIS = 1_500L

// Later, changes of the map alone (vehicles that glide, the beat of the live dots) renew it
// only this often: behind the strong blur of the glass such small changes do not show.
const val BACKDROP_CALM_MILLIS = 1_000L

/** The pause before the picture behind the glass is taken again, [sinceIdleMillis] after the camera last came to rest. */
fun backdropPause(sinceIdleMillis: Long): Long =
    if (sinceIdleMillis < BACKDROP_SETTLE_MILLIS) BACKDROP_FAST_MILLIS else BACKDROP_CALM_MILLIS

// A camera that has not moved for this long counts as resting, even without the idle callback.
const val CAMERA_STILL_MILLIS = 1_000L

/**
 * Whether the picture behind the glass can simply move with the camera instead of being taken
 * again: only when the map is seen from straight above and turned as it was when the picture
 * was taken, so a pan or a zoom moves and scales the whole picture alike.
 */
fun backdropCanFollow(tilt: Double, bearing: Double, takenTilt: Double, takenBearing: Double): Boolean =
    tilt < 0.5 && takenTilt < 0.5 && bearingApart(bearing, takenBearing) < 0.05

/** How much larger the map is drawn at [zoom] than at [takenZoom]. */
fun backdropGrow(zoom: Double, takenZoom: Double): Float = 2.0.pow(zoom - takenZoom).toFloat()
