package io.github.jnbp.nextstop.ui

/*
 * What the map engine may be given (#284). MapLibre's native renderer does not check the
 * numbers it gets: a point that is NaN or beyond the poles, a line of one point, a camera with
 * a NaN zoom or a NaN offset of an icon can bring its render thread down with the whole app
 * (SIGSEGV in MapRenderer::render). Every value handed to the map passes these checks first
 * (see MapSafety.kt). Pure logic, no Android.
 */

/** The highest zoom MapLibre draws. */
const val MAP_GUARD_MAX_ZOOM = 25.5

/** The steepest tilt MapLibre takes, in degrees. */
const val MAP_GUARD_MAX_TILT = 60.0

/** True for a place on the earth: finite, within the poles and within the date line. */
fun mapPointOk(latitude: Double, longitude: Double): Boolean =
    latitude.isFinite() && longitude.isFinite() && latitude >= -90.0 && latitude <= 90.0 &&
        longitude >= -180.0 && longitude <= 180.0

/**
 * The points of [points] the map may draw as a line: the places on the earth (see
 * [mapPointOk]) without a point repeated right after itself; null where fewer than two
 * different points are left, which is no line.
 */
fun mapLine(points: List<Pair<Double, Double>>): List<Pair<Double, Double>>? {
    val kept = ArrayList<Pair<Double, Double>>(points.size)
    for (point in points) {
        if (!mapPointOk(point.first, point.second)) continue
        if (kept.isNotEmpty() && kept.last() == point) continue
        kept.add(point)
    }
    return kept.takeIf { it.size >= 2 }
}

/** [value] where it is finite, else [fallback]: for offsets, opacities and angles handed to the map. */
fun mapNumber(value: Double, fallback: Double = 0.0): Double = if (value.isFinite()) value else fallback

/** [zoom] within what MapLibre draws; [fallback] where it is no number. */
fun mapZoom(zoom: Double, fallback: Double): Double =
    if (zoom.isFinite()) zoom.coerceIn(0.0, MAP_GUARD_MAX_ZOOM) else fallback

/** MapLibre's sign in a camera update for a zoom, bearing or tilt that stays as it is. */
const val MAP_GUARD_KEEP = -1.0

/**
 * True when a camera may go to [latitude], [longitude] (both NaN for a camera that keeps its
 * target) at [zoom], turned by [bearing] and tilted by [tilt], with [padding] (left, top, right,
 * bottom in pixels; null for none): every value finite, the target on the earth, the zoom and
 * the tilt within what MapLibre takes, no padding below zero. A zoom or tilt of
 * [MAP_GUARD_KEEP] keeps the camera's own (as CameraUpdateFactory.newLatLng sends them).
 */
fun cameraOk(
    latitude: Double,
    longitude: Double,
    zoom: Double,
    bearing: Double,
    tilt: Double,
    padding: DoubleArray? = null,
): Boolean {
    val keepsTarget = latitude.isNaN() && longitude.isNaN()
    if (!keepsTarget && !mapPointOk(latitude, longitude)) return false
    if (zoom != MAP_GUARD_KEEP && !(zoom >= 0.0 && zoom <= MAP_GUARD_MAX_ZOOM)) return false
    if (!bearing.isFinite()) return false
    if (tilt != MAP_GUARD_KEEP && !(tilt >= 0.0 && tilt <= MAP_GUARD_MAX_TILT)) return false
    return padding == null || padding.all { it.isFinite() && it >= 0.0 }
}
