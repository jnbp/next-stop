package io.github.jnbp.nextstop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import io.github.jnbp.nextstop.data.TripCourse
import io.github.jnbp.nextstop.data.plausible
import kotlinx.coroutines.delay
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.abs
import kotlin.math.floor

/**
 * How the camera of a line course opened from a vehicle looks: at the vehicle and its next
 * stops, following it ([whole] false and not [held]), or at the whole line ([whole]).
 */
@Stable
class CourseCamera {
    /** "Whole line" is shown; else the vehicle and its next stops. */
    var whole by mutableStateOf(false)

    /** The map was moved by hand: the camera no longer follows the vehicle. */
    var held by mutableStateOf(false)

    /** A course opens on its vehicle; [follow] false (the line is not to be framed) leaves the map as it is. */
    fun open(follow: Boolean) {
        whole = false
        held = !follow
    }

    /** "Whole line" and back to the vehicle: either way the camera takes over again. */
    fun toggleWhole() {
        whole = !whole
        held = false
    }
}

// The camera never comes closer than this to the vehicle and its next stops.
private const val FOLLOW_MAX_ZOOM = 16.0

// How often the free room and the vehicle are looked at.
private const val FOLLOW_POLL_MILLIS = 250L

// Where the vehicle left the inner part of the free room (see followPanDue), one ease follows it; then the camera rests.
private const val FOLLOW_PAN_MILLIS = 600

// A change of the frame (the sheet moved, a stop passed, back from the whole line) eases there.
private const val FRAME_EASE_MILLIS = 600

// A smaller zoom change keeps the zoom.
private const val ZOOM_SLACK = 0.3

// The room kept between the points and the cards around them (dp).
private const val FRAME_MARGIN_DP = 24.0

/**
 * Moves the camera of a course opened from a vehicle (see [CourseCamera]): the vehicle and its
 * next stops in the direction of travel framed in the part of the map between the header
 * ([cover] [0]) and the sheet ([cover] [1]), the vehicle near the edge it comes from, the frame
 * following it as it moves; framed anew when the
 * sheet moves or a stop is passed. "Whole line" eases out to all of the line, and back.
 * [vehicleAt] puts the vehicle's position into its array, false where it is not known.
 */
@Composable
fun CourseFollow(
    map: MapLibreMap?,
    camera: CourseCamera,
    course: TripCourse,
    track: TripTrack,
    vehicleAt: (DoubleArray) -> Boolean,
    cover: FloatArray,
) {
    if (map == null) return
    val density = LocalDensity.current.density.toDouble()
    // The size of the map, measured: the frame is worked out in its dp.
    val size = remember { FloatArray(2) { Float.NaN } }
    Box(modifier = Modifier.fillMaxSize().onSizeChanged { size[0] = it.width.toFloat(); size[1] = it.height.toFloat() })
    val shownCourse by rememberUpdatedState(course)
    val shownTrack by rememberUpdatedState(track)
    val locate by rememberUpdatedState(vehicleAt)
    val stops = remember(course.stops) { course.plausible().stops.map { it.place?.position } }
    val shownStops by rememberUpdatedState(stops)

    // The free room in dp: left, top, right, bottom; null while the map is not measured.
    fun room(): DoubleArray? {
        val width = size[0]
        val height = size[1]
        if (width.isNaN() || height.isNaN() || width <= 0f || height <= 0f) return null
        val margin = FRAME_MARGIN_DP * density
        val coverTop = cover[0]
        val coverBottom = cover[1]
        val panel = cover.getOrNull(2) ?: Float.NaN
        val top = if (!coverTop.isNaN() && coverTop in 0f..height) coverTop + margin else 130.0 * density
        val bottom = if (!coverBottom.isNaN() && coverBottom > 0f && coverBottom < height) height - coverBottom + margin else 360.0 * density
        val left = if (!panel.isNaN() && panel > 0f) panel + margin else margin
        return doubleArrayOf(left / density, top / density, margin / density, bottom / density, width / density, height / density)
    }

    fun ease(view: CourseView, millis: Int, smooth: Boolean = true) {
        val position = CameraPosition.Builder()
            .target(LatLng(view.latitude, view.longitude))
            .zoom(view.zoom)
            .bearing(0.0)
            .tilt(0.0)
            .build()
        // A map released meanwhile (its view destroyed) is not moved any more (#284).
        if (!MapAlive.alive(map)) return
        map.easeCameraScaled(CameraUpdateFactory.newCameraPosition(position), millis, smooth)
    }

    val whole = camera.whole
    val held = camera.held
    LaunchedEffect(map, course.tripId, whole, held) {
        if (held) return@LaunchedEffect
        if (whole) {
            // All of the line, eased out to; once the room is known.
            var free = room()
            while (free == null) {
                delay(FOLLOW_POLL_MILLIS)
                free = room()
            }
            val line = shownCourse.path.ifEmpty { shownStops.filterNotNull() }
            courseView(line, free[4], free[5], free[0], free[1], free[2], free[3], 18.0)?.let { ease(it, FRAME_EASE_MILLIS) }
            return@LaunchedEffect
        }
        val spot = DoubleArray(2)
        var lastRoom: List<Double>? = null
        var framedRoom: List<Double>? = null
        var framedPassed = Int.MIN_VALUE
        var framedZoom = Double.NaN
        // Where the last frame or pan put the vehicle on the screen (dp); NaN until the camera rests there.
        var anchorX = Double.NaN
        var anchorY = Double.NaN
        var settledAt = 0L
        while (true) {
            // The loop ends with the map it frames (#284).
            if (!MapAlive.alive(map)) break
            val free = room()?.toList()
            // The room has to stand still for one poll (the sheet slides in, the list grows).
            val steady = free != null && free == lastRoom
            lastRoom = free
            if (steady && free != null && locate(spot)) {
                val vehicle = spot[0] to spot[1]
                val millis = System.currentTimeMillis()
                // The timetable picks the lap where a ring line passes the place more than once.
                val progress = shownTrack.progressAt(spot[0], spot[1], near = shownTrack.clock.progress(millis))
                    ?: if (shownTrack.clock.active(millis)) shownTrack.clock.progress(millis) else null
                val passed = progress?.let { floor(it).toInt() } ?: -1
                if (free != framedRoom || passed != framedPassed) {
                    // Frame anew: the vehicle near the edge it comes from, its next stops filling the free part.
                    val ahead = aheadFrame(shownStops, progress, vehicle)
                    val view = courseView(ahead, free[4], free[5], free[0], free[1], free[2], free[3], FOLLOW_MAX_ZOOM)
                    if (view != null) {
                        // A small change of zoom (the next stop passed) keeps the zoom it has.
                        val keep = framedRoom == free && !framedZoom.isNaN() && abs(view.zoom - framedZoom) < ZOOM_SLACK
                        val shown = if (keep) courseView(ahead, free[4], free[5], free[0], free[1], free[2], free[3], FOLLOW_MAX_ZOOM, zoom = framedZoom)!! else view
                        ease(shown, FRAME_EASE_MILLIS)
                        framedZoom = shown.zoom
                    }
                    framedRoom = free
                    framedPassed = passed
                    anchorX = Double.NaN
                    settledAt = millis + SystemAnimation.millis(FRAME_EASE_MILLIS.toLong()) + FOLLOW_POLL_MILLIS
                } else if (millis >= settledAt) {
                    // Following: the camera rests while the vehicle stays in the inner part of the
                    // free room around where the frame put it; one ease where it left it, then rest.
                    val point = map.projection.toScreenLocation(LatLng(spot[0], spot[1]))
                    val x = point.x / density
                    val y = point.y / density
                    if (anchorX.isNaN()) {
                        anchorX = x
                        anchorY = y
                    } else if (followPanDue(x, y, anchorX, anchorY, free[0], free[1], free[4] - free[2], free[5] - free[3])) {
                        val ahead = aheadFrame(shownStops, progress, vehicle)
                        val view = courseView(ahead, free[4], free[5], free[0], free[1], free[2], free[3], FOLLOW_MAX_ZOOM, zoom = map.cameraPosition.zoom)
                        if (view != null) {
                            val position = CameraPosition.Builder()
                                .target(LatLng(view.latitude, view.longitude))
                                .zoom(view.zoom)
                                .bearing(0.0)
                                .tilt(0.0)
                                .build()
                            map.animateCameraScaled(CameraUpdateFactory.newCameraPosition(position), FOLLOW_PAN_MILLIS)
                        }
                        anchorX = Double.NaN
                        settledAt = millis + SystemAnimation.millis(FOLLOW_PAN_MILLIS.toLong()) + FOLLOW_POLL_MILLIS
                    }
                }
            }
            delay(FOLLOW_POLL_MILLIS)
            // Not while the app is in the background (battery rule).
            AppVisibility.awaitVisible()
        }
    }
}
