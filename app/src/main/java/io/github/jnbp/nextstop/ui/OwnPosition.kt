package io.github.jnbp.nextstop.ui

import android.location.Location
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.jnbp.nextstop.data.CompanionPosition
import io.github.jnbp.nextstop.data.FixKind
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.plausible
import io.github.jnbp.nextstop.data.rideWay
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import io.github.jnbp.nextstop.ui.theme.AppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sqrt

// A framing of the trip that just started is left to finish before the camera follows again.
private const val FOLLOW_SETTLE_MILLIS = 1_100L

// A fix of the device this fresh is the dot's own: the position given to PositionDot then
// only stands in when none came (ms).
private const val DEVICE_FIX_FRESH_MILLIS = 4_000L

// A camera within this of where the dot left it has not been moved by anything else (degrees, about 1 cm).
private const val CAMERA_SAME_DEGREES = 1e-7

// A stale own position (#159): drawn at this opacity, faded over this long, and checked again this
// long after it would go stale.
private const val STALE_ALPHA = 0.55f
private const val STALE_FADE_MILLIS = 300
private const val STALE_CHECK_SLACK_MILLIS = 50L

// The halo of the dot (its radius); an accuracy circle smaller than it is not drawn.
private val DOT_HALO_RADIUS = 17.dp

/**
 * Where the traveller is shown on the map. One holder serves the dot ([PositionDot]), the
 * camera that follows it ([OwnFollow]) and the vehicle badge that carries it while the
 * traveller rides ([carriedBy], see TrackMarkers). The motion between fixes is [OwnMotion]:
 * one frame loop ([step]) places the dot and moves the following camera with it, and runs
 * only while the dot moves.
 */
@Stable
class OwnDot {
    private val motion = OwnMotion()

    /** Counts the frames that moved the dot: the dot and the badges that carry it read it while placing. */
    var tick by mutableIntStateOf(0)
        private set

    /** Counts the fixes: each one wakes the frame loop. */
    var serial by mutableIntStateOf(0)
        private set

    /** The trip whose vehicle badge carries the traveller (the dot of its own is hidden then); null for none. */
    var carriedBy by mutableStateOf<String?>(null)

    /** The map whose camera moves along with the dot, null for none (see [OwnFollow]). */
    internal var follow: MapLibreMap? = null
        set(value) {
            field = value
            cameraLeft[0] = Double.NaN
            pending[0] = 0.0
            pending[1] = 0.0
        }

    // The way the dot went (north, east in degrees) that the following camera has not taken yet:
    // it moves once that is a visible step (see followMoveDue), or when the dot comes to rest.
    private val pending = DoubleArray(2)

    // Where the following camera was left by the last frame (latitude, longitude; NaN for not yet).
    private val cameraLeft = DoubleArray(2) { Double.NaN }

    /** The position shown is the device's own: its fixes move the dot (see [deviceFix]). */
    internal var live = false

    /**
     * True while the device's position shown is stale (#159): no fix of the device yet, or the
     * last one older than OWN_STALE_MILLIS. The dot is drawn faded and grey then, without its
     * halo, so it does not look current. Set by the dot's own loop (see PositionDot).
     */
    var staleShown by mutableStateOf(false)
        internal set

    /** The device's position shown, where it is fresh at [nowMillis] (#166); null when stale, not the device's or none. */
    internal fun freshPosition(nowMillis: Long): Pair<Double, Double>? =
        if (live && motion.has && !motion.staleAt(nowMillis)) motion.lat to motion.lon else null

    /** True while the position is the device's and stale at [nowMillis]. */
    internal fun staleAt(nowMillis: Long): Boolean = live && motion.staleAt(nowMillis)

    /** How long until the position goes stale from [nowMillis] (ms), 0 when it is already. */
    internal fun freshForMillis(nowMillis: Long): Long =
        motion.lastFixMillis?.let { (it + OWN_STALE_MILLIS - nowMillis).coerceAtLeast(0L) } ?: 0L

    // When the last fix of the device came in (uptime ms), 0 for none.
    private var deviceAt = 0L

    // The way the dot was kept on last, as given and as arrays.
    private var pathKey: List<Pair<Double, Double>>? = null

    /** Writes the position shown now into [out]; false without one. Reads [tick]: call it while placing. */
    fun shown(out: DoubleArray): Boolean {
        tick
        if (!motion.has) return false
        out[0] = motion.lat
        out[1] = motion.lon
        return true
    }

    /** The radius of the accuracy circle now (metres), 0 for none. Reads [tick]. */
    fun accuracyMetres(): Double {
        tick
        return if (motion.has && live) motion.accuracy else 0.0
    }

    /** A fix of the device (from the one listener of the app): moves the dot while it is [live]. */
    fun deviceFix(location: Location) {
        if (!live) return
        val now = SystemClock.uptimeMillis()
        val age = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000L
        motion.fix(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMetres = if (location.hasAccuracy()) location.accuracy.toDouble() else 0.0,
            speed = if (location.hasSpeed()) location.speed.toDouble() else Double.NaN,
            bearing = if (location.hasBearing() && location.hasSpeed() && location.speed >= 1f) location.bearing.toDouble() else Double.NaN,
            atMillis = now - age.coerceAtLeast(0L),
            nowMillis = now,
        )
        deviceAt = now
        serial++
    }

    /**
     * The position to show as given by the page: a guess of the timetable is eased to; the
     * device's own only where no fix of the device came in a moment ago (the first one).
     */
    fun place(position: Pair<Double, Double>) {
        val now = SystemClock.uptimeMillis()
        if (live && deviceAt != 0L && now - deviceAt < DEVICE_FIX_FRESH_MILLIS) return
        motion.ease(position.first, position.second, now)
        serial++
    }

    /**
     * How the dot moves: [animate] (else it is put at each fix), [predict] between fixes (not
     * with the battery saver), its glide to a fix at the system's [durationScale].
     */
    fun setMotion(animate: Boolean, predict: Boolean, durationScale: Float = 1f) {
        motion.smooth = animate
        motion.extrapolate = predict
        motion.durationScale = durationScale
    }

    /** The way of the ride the traveller is on, to keep the dot on; null for none. */
    fun setPath(path: List<Pair<Double, Double>>?) {
        if (path === pathKey) return
        // The same points again (a refresh of the trip brings them as a new list): the arrays
        // made of them stay, nothing is copied (see sameWay).
        if (path != null && path.size >= 2 && sameWay(path, pathKey)) {
            pathKey = path
            return
        }
        pathKey = path
        if (path == null || path.size < 2) {
            motion.setPath(null, null)
            return
        }
        motion.setPath(DoubleArray(path.size) { path[it].first }, DoubleArray(path.size) { path[it].second })
    }

    /**
     * One frame at [nowMillis]: places the dot and moves a following camera by the same way.
     * False once the dot rests: no frames are needed until the next fix.
     */
    fun step(nowMillis: Long): Boolean {
        if (!motion.has) return false
        val beforeLat = motion.lat
        val beforeLon = motion.lon
        motion.evaluate(nowMillis)
        val north = motion.lat - beforeLat
        val east = motion.lon - beforeLon
        tick++
        val resting = motion.resting(nowMillis)
        val map = follow
        if (map != null) {
            pending[0] += north
            pending[1] += east
        }
        if (map != null && MapAlive.alive(map) && (abs(pending[0]) > 1e-9 || abs(pending[1]) > 1e-9)) {
            val centre = map.cameraPosition.target
            if (centre != null) {
                // Moved by another animation since the last frame (a framing, back to the
                // user): that one is left to finish, the camera follows from where it ends.
                val elsewhere = !cameraLeft[0].isNaN() &&
                    (abs(centre.latitude - cameraLeft[0]) > CAMERA_SAME_DEGREES || abs(centre.longitude - cameraLeft[1]) > CAMERA_SAME_DEGREES)
                if (elsewhere) {
                    cameraLeft[0] = centre.latitude
                    cameraLeft[1] = centre.longitude
                    pending[0] = 0.0
                    pending[1] = 0.0
                } else if (resting ||
                    followMoveDue(pending[0], pending[1], centre.latitude, map.projection.getMetersPerPixelAtLatitude(centre.latitude))
                ) {
                    // A step too small to be seen waits for the next ones (the map is not drawn
                    // again for it); what is left is taken when the dot comes to rest.
                    cameraLeft[0] = centre.latitude + pending[0]
                    cameraLeft[1] = centre.longitude + pending[1]
                    pending[0] = 0.0
                    pending[1] = 0.0
                    map.moveCameraSafely(CameraUpdateFactory.newLatLng(LatLng(cameraLeft[0], cameraLeft[1])))
                } else if (cameraLeft[0].isNaN()) {
                    cameraLeft[0] = centre.latitude
                    cameraLeft[1] = centre.longitude
                }
            }
        }
        return !resting
    }

    /** No position any more. */
    fun clear() {
        motion.clear()
        deviceAt = 0L
        tick++
    }
}

/** Near enough for a few kilometres: a degree of longitude shrinks with the latitude. */
internal fun metresApart(latitude: Double, longitude: Double, otherLatitude: Double, otherLongitude: Double): Double {
    val north = (otherLatitude - latitude) * 111_320.0
    val east = (otherLongitude - longitude) * 111_320.0 * cos(Math.toRadians(latitude))
    return sqrt(north * north + east * east)
}

/**
 * The way of the ride of [journey] the companion has the traveller on by position (see
 * CompanionPosition), to keep the dot on its rails; null for none.
 */
internal fun ownRidePath(journey: Journey?): List<Pair<Double, Double>>? {
    if (journey == null) return null
    val fix = CompanionPosition.fixFor(journey) ?: return null
    if (fix.kind != FixKind.OnRide && fix.kind != FixKind.AtEnd || fix.byTimes) return null
    val leg = journey.plausible().legs.getOrNull(fix.legIndex) ?: return null
    return rideWay(leg).takeIf { it.size >= 2 }
}

/**
 * The traveller on the map: a blue dot with a white rim inside a soft halo, in a circle of its
 * accuracy where that is wider. With [live] the dot is the device's: its fixes move it (see
 * [OwnDot.deviceFix]), predicted between them; else it eases to [position] (the timetable's
 * guess). On a ride of [journey] that the companion matched the position to, the dot keeps
 * to the way of the ride. It steps back while a vehicle badge carries the traveller.
 */
@Composable
internal fun PositionDot(
    map: MapLibreMap?,
    cameraTick: () -> Int,
    position: Pair<Double, Double>?,
    dot: OwnDot,
    live: Boolean = false,
    journey: Journey? = null,
) {
    val shown by rememberMapInAlpha()
    val animate = rememberAnimationsOn()
    val saver = BatterySaver.active
    val durationScale = SystemAnimation.scale.value
    val companionFix by CompanionPosition.fix.collectAsState()
    val path = remember(journey, companionFix?.legIndex, companionFix?.kind) { ownRidePath(journey) }
    SideEffect {
        dot.live = live && position != null
        dot.setMotion(animate, predict = !saver, durationScale = durationScale)
        dot.setPath(path)
    }
    LaunchedEffect(dot, position) {
        if (position == null) dot.clear() else dot.place(position)
    }
    // The one frame loop of the dot: woken by each fix, it runs until the dot rests; at most
    // about 60 frames a second on a faster display (see FramePace).
    LaunchedEffect(dot) {
        val pace = FramePace()
        snapshotFlow { dot.serial }.collectLatest {
            while (true) {
                val frame = withFrameNanos { it }
                if (!pace.due(frame)) continue
                if (!dot.step(SystemClock.uptimeMillis())) break
            }
        }
    }
    // Whether the position shown is stale (#159): checked at each fix, and again when it would go stale.
    LaunchedEffect(dot, live) {
        snapshotFlow { dot.serial }.collectLatest {
            while (true) {
                val now = SystemClock.uptimeMillis()
                val stale = dot.staleAt(now)
                if (dot.staleShown != stale) dot.staleShown = stale
                if (stale || !live) break
                delay(dot.freshForMillis(now) + STALE_CHECK_SLACK_MILLIS)
            }
        }
    }
    if (map == null || position == null) return
    val spot = remember { DoubleArray(2) }
    val stale = dot.staleShown
    // A stale position fades to grey and back to blue once fresh, not at once (unless animations are off).
    val dotInk by animateColorAsState(
        if (stale) AppColors.Muted else AppColors.Blue,
        if (animate) tween(STALE_FADE_MILLIS) else snap(),
        label = "ownDotInk",
    )
    val staleAlpha by animateFloatAsState(
        if (stale) STALE_ALPHA else 1f,
        if (animate) tween(STALE_FADE_MILLIS) else snap(),
        label = "ownDotAlpha",
    )
    // The inks of the accuracy circle and the halo, made in composition, not in every frame.
    val circleInk = AppColors.Blue.copy(alpha = 0.12f)
    val haloInk = AppColors.Blue.copy(alpha = 0.2f)
    Box(
        modifier = Modifier
            .graphicsLayer { alpha = if (dot.carriedBy != null) 0f else shown * staleAlpha }
            .offset {
                cameraTick()
                if (!MapAlive.alive(map)) return@offset IntOffset(MAP_GONE_OFFSET, MAP_GONE_OFFSET)
                if (!dot.shown(spot)) {
                    spot[0] = position.first
                    spot[1] = position.second
                }
                val point = map.projection.toScreenLocation(LatLng(spot[0], spot[1]))
                IntOffset(point.x.roundToInt(), point.y.roundToInt())
            }
            .graphicsLayer {
                translationX = -size.width / 2f
                translationY = -size.height / 2f
            }
            .size(DOT_HALO_RADIUS * 2)
            .drawBehind {
                cameraTick()
                // No accuracy circle around a stale position: it would tell an accuracy it no longer has.
                if (stale || !MapAlive.alive(map)) return@drawBehind
                val metres = dot.accuracyMetres()
                if (metres <= 0.0) return@drawBehind
                val perPixel = map.projection.getMetersPerPixelAtLatitude(spot[0])
                if (perPixel <= 0.0) return@drawBehind
                val radius = (metres / perPixel).toFloat()
                if (radius <= DOT_HALO_RADIUS.toPx()) return@drawBehind
                drawCircle(circleInk, radius)
            }
            .clip(CircleShape)
            .background(if (stale) Color.Transparent else haloInk)
            .padding(8.dp)
            .clip(CircleShape)
            .background(AppColors.Card)
            .padding(3.dp)
            .clip(CircleShape)
            .background(dotInk),
    )
}

/**
 * The camera moves along with the traveller while [active] (the map not moved by hand): the
 * frame loop of the dot moves it by the same way as the dot in the same frame ([OwnDot.step]),
 * so the dot stands still on the screen, the frame keeps its zoom, and there is one animation
 * per frame. A new framing ([frameKey]) is left to finish first.
 */
@Composable
internal fun OwnFollow(map: MapLibreMap?, active: Boolean, dot: OwnDot, frameKey: Any?) {
    DisposableEffect(dot) {
        onDispose { dot.follow = null }
    }
    LaunchedEffect(map, active, frameKey) {
        dot.follow = null
        if (map == null || !active) return@LaunchedEffect
        delay(FOLLOW_SETTLE_MILLIS)
        dot.follow = map
    }
}
