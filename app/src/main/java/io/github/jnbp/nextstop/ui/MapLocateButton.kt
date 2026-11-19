package io.github.jnbp.nextstop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * The edges of what covers the map, as the pages report them, made observable for the floating
 * "centre on me" button (#165): a report that changes an edge counts [tick] on, so the button
 * moves with a sheet as it is dragged. The arrays stay the ones the framing reads (LocalMapCover).
 */
object MapEdges {
    /** Counts the changes of a reported edge. */
    var tick by mutableIntStateOf(0)
        private set

    /** The top edge of the card of a walk on the map (pixels of the screen), NaN without. */
    val walkCard = floatArrayOf(Float.NaN)

    /** True while the floating button shows: the framing keeps room for it above the bottom cover. */
    var locateShown = false
        internal set

    /** Sets [edges] at [index] to [value]; counts a change. */
    fun report(edges: FloatArray, index: Int, value: Float) {
        if (edges[index] == value) return
        edges[index] = value
        tick++
    }

    /** Edges were cleared or changed together (a new page). */
    fun changed() {
        tick++
    }
}

// The floating button: as big as the other round buttons of the map, this far above what covers the bottom and from the right edge.
internal val LOCATE_BUTTON_SIZE = 48.dp
private val LOCATE_GAP = 12.dp
private val LOCATE_END = 12.dp

/** The room the framing keeps above the bottom cover while the floating button shows (dp). */
internal const val LOCATE_FRAME_ROOM_DP = 60

/**
 * The "centre on me" button of every map page (#165), at the bottom right, floating just above
 * [bottomEdge] (the top edge of what covers the bottom of the map, pixels of the screen) and
 * moving with it, eased with a stiff spring (the animator scale applies; at once without
 * animations). Gone where [onLocate] is null or a sheet leaves no room below [topCover].
 */
@Composable
internal fun BoxScope.FloatingLocateButton(
    onLocate: (() -> Unit)?,
    description: String,
    bottomEdge: () -> Float,
    topCover: () -> Float,
    height: () -> Float,
    busy: Boolean = false,
) {
    // Read here: the button follows every reported move of an edge.
    MapEdges.tick
    val density = LocalDensity.current
    val origin = remember { floatArrayOf(0f) }
    val top = with(density) { locateButtonTop(bottomEdge(), topCover(), height(), LOCATE_BUTTON_SIZE.toPx(), LOCATE_GAP.toPx()) }
    val shown = onLocate != null && top != null
    SideEffect { MapEdges.locateShown = shown }
    // Where it last stood: it fades out there, and comes back from there.
    val lastTop = remember { floatArrayOf(Float.NaN) }
    if (top != null) lastTop[0] = top
    val animate = rememberAnimationsOn()
    val y by animateFloatAsState(
        if (lastTop[0].isNaN()) 0f else lastTop[0],
        if (animate) spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessHigh) else snap(),
        label = "locateTop",
    )
    // Where this box lies on the screen: the edges are measured from the top of the screen.
    Box(modifier = Modifier.matchParentSize().onGloballyPositioned { origin[0] = it.positionInRoot().y })
    // Kept for the fade out.
    val lastLocate = remember { arrayOf<(() -> Unit)?>(null) }
    if (onLocate != null) lastLocate[0] = onLocate
    AnimatedVisibility(
        visible = shown,
        enter = if (animate) fadeIn(tween(LOCATE_FADE_MILLIS)) + scaleIn(initialScale = 0.8f) else EnterTransition.None,
        exit = if (animate) fadeOut(tween(LOCATE_FADE_MILLIS)) + scaleOut(targetScale = 0.8f) else ExitTransition.None,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .offset { IntOffset(0, (y - origin[0]).roundToInt()) }
            .padding(end = LOCATE_END),
    ) {
        RoundButton(
            glyph = Glyph.Locate,
            description = description,
            onClick = { lastLocate[0]?.invoke() },
            busy = busy,
        )
    }
}

private const val LOCATE_FADE_MILLIS = 180
