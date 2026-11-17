package io.github.jnbp.nextstop.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.jnbp.nextstop.ui.theme.AppColors
import kotlinx.coroutines.delay
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sqrt

// The own position glides to a new fix over this time, linearly; the camera that follows it as well.
internal const val OWN_GLIDE_MILLIS = 1_000

// A fix farther than this from the one before is a jump (the first real fix after the
// timetable's guess, a fix after a tunnel): the dot is put there at once.
private const val OWN_JUMP_METRES = 2_000.0

// A framing of the trip that just started is left to finish before the camera follows again.
private const val FOLLOW_SETTLE_MILLIS = 1_100L

/**
 * Where the traveller is shown on the map: the position it glides from, the one it glides to
 * and how far it has come. One holder serves the dot ([PositionDot]), the camera that follows
 * it ([OwnFollow]) and the vehicle badge that carries it while the traveller rides
 * ([carriedBy], see TrackMarkers).
 */
@Stable
class OwnDot {
    private val from = DoubleArray(2) { Double.NaN }
    private val to = DoubleArray(2) { Double.NaN }
    private val glide = Animatable(1f)

    /** Counts the new positions to glide to: the camera follows each. */
    var serial by mutableIntStateOf(0)
        private set

    /** The trip whose vehicle badge carries the traveller (the dot of its own is hidden then); null for none. */
    var carriedBy by mutableStateOf<String?>(null)

    /** The position the dot glides to, null without one. */
    fun target(): Pair<Double, Double>? = if (to[0].isNaN()) null else to[0] to to[1]

    /** Writes the position shown now into [out]; false without one. Reads the glide: call it while placing. */
    fun shown(out: DoubleArray): Boolean {
        val share = glide.value.toDouble()
        if (to[0].isNaN()) return false
        if (from[0].isNaN()) {
            out[0] = to[0]
            out[1] = to[1]
        } else {
            out[0] = from[0] + (to[0] - from[0]) * share
            out[1] = from[1] + (to[1] - from[1]) * share
        }
        return true
    }

    /** Glides to [position] (at once without [animate], the first time, or after a jump). */
    suspend fun moveTo(position: Pair<Double, Double>, animate: Boolean) {
        if (position.first == to[0] && position.second == to[1]) return
        val now = DoubleArray(2)
        val known = shown(now)
        val jump = !known || metresApart(now[0], now[1], position.first, position.second) > OWN_JUMP_METRES
        from[0] = if (jump) position.first else now[0]
        from[1] = if (jump) position.second else now[1]
        to[0] = position.first
        to[1] = position.second
        serial++
        if (jump || !animate) {
            glide.snapTo(1f)
        } else {
            glide.snapTo(0f)
            glide.animateTo(1f, tween(OWN_GLIDE_MILLIS, easing = LinearEasing))
        }
    }

    /** No position any more. */
    fun clear() {
        from.fill(Double.NaN)
        to.fill(Double.NaN)
    }
}

/** Near enough for a few kilometres: a degree of longitude shrinks with the latitude. */
internal fun metresApart(latitude: Double, longitude: Double, otherLatitude: Double, otherLongitude: Double): Double {
    val north = (otherLatitude - latitude) * 111_320.0
    val east = (otherLongitude - longitude) * 111_320.0 * cos(Math.toRadians(latitude))
    return sqrt(north * north + east * east)
}

/**
 * The traveller on the map: a blue dot with a white rim inside a soft halo. It glides from fix
 * to fix (see [OwnDot]) and steps back while a vehicle badge carries the traveller.
 */
@Composable
internal fun PositionDot(map: MapLibreMap?, cameraTick: () -> Int, position: Pair<Double, Double>?, dot: OwnDot) {
    val shown by rememberMapInAlpha()
    val animate = rememberAnimationsOn()
    LaunchedEffect(dot, position) {
        if (position == null) dot.clear() else dot.moveTo(position, animate)
    }
    if (map == null || position == null) return
    val spot = androidx.compose.runtime.remember { DoubleArray(2) }
    Box(
        modifier = Modifier
            .graphicsLayer { alpha = if (dot.carriedBy != null) 0f else shown }
            .offset {
                cameraTick()
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
            .size(34.dp)
            .clip(CircleShape)
            .background(AppColors.Blue.copy(alpha = 0.2f))
            .padding(8.dp)
            .clip(CircleShape)
            .background(AppColors.Card)
            .padding(3.dp)
            .clip(CircleShape)
            .background(AppColors.Blue),
    )
}

/**
 * The camera of a followed trip moves along with the traveller while [active] (the map not
 * moved by hand): each new position eases the camera by the same way the dot glides, over the
 * same time, so the dot stands still on the screen and the frame keeps its zoom. A new framing
 * ([frameKey]) is left to finish first.
 */
@Composable
internal fun OwnFollow(map: MapLibreMap?, active: Boolean, dot: OwnDot, frameKey: Any?) {
    if (map == null) return
    LaunchedEffect(map, active, frameKey) {
        if (!active) return@LaunchedEffect
        delay(FOLLOW_SETTLE_MILLIS)
        var last = dot.target()
        snapshotFlow { dot.serial }.collect {
            val target = dot.target() ?: return@collect
            val before = last
            last = target
            if (before == null) return@collect
            val north = target.first - before.first
            val east = target.second - before.second
            if (abs(north) < 1e-7 && abs(east) < 1e-7) return@collect
            val camera = map.cameraPosition
            val centre = camera.target ?: return@collect
            val moved = CameraPosition.Builder(camera)
                .target(LatLng(centre.latitude + north, centre.longitude + east))
                .build()
            map.easeCamera(CameraUpdateFactory.newCameraPosition(moved), OWN_GLIDE_MILLIS, false)
        }
    }
}
