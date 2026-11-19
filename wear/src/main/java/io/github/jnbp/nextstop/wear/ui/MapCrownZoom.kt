package io.github.jnbp.nextstop.wear.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import io.github.jnbp.nextstop.wear.data.MapZoom

/**
 * The crown zoom of an opened map with a fixed framing (see [MapZoom]): the notch the crown
 * has reached, the pan in pixels of the framing (from its middle, kept inside it), and the
 * factor on screen ([shown], animated towards the notch's).
 */
@Stable
class MapZoomState internal constructor() {
    internal var shownState: State<Float>? = null
    var step by mutableIntStateOf(0)
        internal set
    var panX by mutableDoubleStateOf(0.0)
        internal set
    var panY by mutableDoubleStateOf(0.0)
        internal set
    internal var size by mutableStateOf(IntSize.Zero)
    internal var crownRest by mutableFloatStateOf(0f)

    /** The zoom of the crown's notch. */
    val factor: Double get() = MapZoom.factorOf(step)

    /** The zoom on screen now, on its way to [factor]. */
    val shown: Float get() = shownState?.value ?: factor.toFloat()

    /** The scale to ask the phone for a picture at (sharper above twice, see [MapZoom.requestFactor]). */
    val requestFactor: Double get() = MapZoom.requestFactor(factor)

    internal fun moveBy(notches: Int): Boolean {
        val next = MapZoom.stepBy(step, notches)
        if (next == step) return false
        step = next
        clamp()
        return true
    }

    internal fun panBy(dx: Double, dy: Double) {
        panX += dx
        panY += dy
        clamp()
    }

    private fun clamp() {
        val (x, y) = MapZoom.clampPan(panX, panY, factor, size.width, size.height, 1.0)
        panX = x
        panY = y
    }
}

/**
 * A new zoom state for each [key] (another change: back to the framing). The factor springs
 * to each notch (Material 3 motion), at once with animations off (see rememberWatchAnimationsOn).
 */
@Composable
fun rememberMapZoomState(key: Any?): MapZoomState {
    val animate = rememberWatchAnimationsOn()
    val state = remember(key) { MapZoomState() }
    state.shownState = animateFloatAsState(
        state.factor.toFloat(),
        animationSpec = if (animate) spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow) else snap(),
        label = "map zoom",
    )
    return state
}

/**
 * The input of an opened map: the crown zooms in by notches (a tick of the haptics each) and
 * back out to the framing, a drag pans while zoomed (not from the left edge, which stays the
 * swipe back), a tap calls [onTap]. Takes the crown while [active] (see crownFocus).
 */
@Composable
fun Modifier.mapCrownZoom(state: MapZoomState, active: Boolean, refocus: Any?, onTap: () -> Unit): Modifier {
    val view = LocalView.current
    return this
        .onSizeChanged { state.size = it }
        .onRotaryScrollEvent { event ->
            if (!active) return@onRotaryScrollEvent false
            val (notches, rest) = MapZoom.notches(state.crownRest + event.verticalScrollPixels)
            state.crownRest = rest
            if (notches != 0 && state.moveBy(notches)) mapZoomTick(view)
            true
        }
        .crownFocus(active, refocus)
        .focusable()
        .pointerInput(state) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (down.position.x < size.width * EDGE_FRACTION) return@awaitEachGesture
                var moving = false
                var sum = Offset.Zero
                do {
                    val event = awaitPointerEvent()
                    if (state.step == 0 || event.changes.any { it.isConsumed }) break
                    val change = event.changes.firstOrNull() ?: break
                    val delta = change.position - change.previousPosition
                    if (!moving) {
                        sum += delta
                        moving = sum.getDistance() > viewConfiguration.touchSlop
                    }
                    if (moving) {
                        // Screen pixels at the zoom on screen into pixels of the framing; the map follows the finger.
                        val zoom = state.shown.coerceAtLeast(1f)
                        state.panBy(-delta.x / zoom.toDouble(), -delta.y / zoom.toDouble())
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                } while (event.changes.any { it.pressed })
            }
        }
        .pointerInput(onTap) { detectTapGestures(onTap = { onTap() }) }
}

/** One tick per notch: the light segment tick where the watch has it, else the clock tick. */
fun mapZoomTick(view: View) {
    val type = if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK else HapticFeedbackConstants.CLOCK_TICK
    view.performHapticFeedback(type)
}

// As on the map of stops: a drag from the left edge is left to the swipe back.
private const val EDGE_FRACTION = 0.15f
