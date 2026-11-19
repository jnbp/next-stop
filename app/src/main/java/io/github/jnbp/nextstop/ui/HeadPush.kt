package io.github.jnbp.nextstop.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * #251 (variant A): the pinned ride head of the detail page and the next ride's head coming up
 * to it. Over the last 40 dp of the approach the pinned head's bottom corners round to the
 * card's radius, and the next head pushes it up keeping 8 dp between them (pushCornerRadius and
 * pushLift in HeadFadeCurve.kt). Where the heads lie is read from the list's layout info while
 * drawing, so scrolling recomposes nothing and redraws only the ground (the head's content lies
 * in a layer of its own); the ground's outline is one Path kept and rebuilt only when the radius
 * or the size changes. With nothing approaching it draws as before: the
 * card's ground, rounded at the top only.
 */

// The gap the next head keeps to the pinned one it pushes, and how long before it the corners round.
private val PUSH_GAP = 8.dp
private val PUSH_RAMP = 40.dp

// The share of a corner's radius at which a cubic curve's control points lie for a quarter circle.
private const val ARC_KAPPA = 0.5523f

/** Where the head [key] lies against the next head; read while drawing, holds no state of the composition. */
private class HeadPushProbe(
    private val list: LazyListState,
    private val key: Any,
    private val headType: Any,
    private val gap: Float,
    private val ramp: Float,
    private val radius: Float,
) {
    var lift = 0f
        private set
    var bottomRadius = 0f
        private set

    private val path = Path()
    private var pathWidth = -1f
    private var pathHeight = -1f
    private var pathBottom = -1f

    /** Reads where this head and the next one lie and sets [lift] and [bottomRadius]. */
    fun update() {
        val items = list.layoutInfo.visibleItemsInfo
        var ownIndex = -1
        var ownOffset = 0
        var ownSize = 0
        var nextOffset = Int.MIN_VALUE
        for (i in items.indices) {
            val item = items[i]
            if (ownIndex < 0) {
                if (item.key == key) {
                    ownIndex = item.index
                    ownOffset = item.offset
                    ownSize = item.size
                }
            } else if (item.contentType == headType && item.index > ownIndex) {
                nextOffset = item.offset
                break
            }
        }
        val pinned = ownIndex >= 0 && nextOffset != Int.MIN_VALUE &&
            headCoversContent(ownIndex, ownOffset, list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset)
        if (!pinned) {
            lift = 0f
            bottomRadius = 0f
            return
        }
        val distance = (nextOffset - (ownOffset + ownSize)).toFloat()
        lift = pushLift(distance, gap)
        bottomRadius = pushCornerRadius(distance, gap, ramp, radius)
    }

    /** The outline of the ground: [radius] at the top, [bottomRadius] at the bottom; the same Path, rebuilt only on a change. */
    fun ground(width: Float, height: Float): Path {
        val bottom = bottomRadius
        if (width == pathWidth && height == pathHeight && bottom == pathBottom) return path
        pathWidth = width
        pathHeight = height
        pathBottom = bottom
        val top = radius.coerceAtMost(minOf(width, height) / 2f)
        val low = bottom.coerceAtMost(minOf(width, height) / 2f)
        path.reset()
        path.moveTo(0f, top)
        path.cubicTo(0f, top * (1f - ARC_KAPPA), top * (1f - ARC_KAPPA), 0f, top, 0f)
        path.lineTo(width - top, 0f)
        path.cubicTo(width - top * (1f - ARC_KAPPA), 0f, width, top * (1f - ARC_KAPPA), width, top)
        path.lineTo(width, height - low)
        path.cubicTo(width, height - low * (1f - ARC_KAPPA), width - low * (1f - ARC_KAPPA), height, width - low, height)
        path.lineTo(low, height)
        path.cubicTo(low * (1f - ARC_KAPPA), height, 0f, height - low * (1f - ARC_KAPPA), 0f, height - low)
        path.close()
        return path
    }
}

/**
 * The modifier of the ride head [key] of [list] (the heads are the items of the content type
 * [headType]) that draws its ground in [color], rounded by [radius] at the top, and lets the
 * next head push it (#251). Put it where the head's ground was (before its padding).
 */
@Composable
internal fun rememberHeadPush(list: LazyListState, key: Any, headType: Any, color: Color, radius: Dp): Modifier {
    val density = LocalDensity.current
    return remember(list, key, headType, color, radius, density) {
        val probe = with(density) { HeadPushProbe(list, key, headType, PUSH_GAP.toPx(), PUSH_RAMP.toPx(), radius.toPx()) }
        Modifier
            .graphicsLayer {
                probe.update()
                translationY = -probe.lift
            }
            .drawBehind {
                // Read here too, so the corners follow the scroll with a redraw of the ground only.
                probe.update()
                drawPath(probe.ground(size.width, size.height), color)
            }
            // The head's content in a layer of its own: a scrolled pixel redraws the ground only,
            // not the line, texts and chips of the head (as the items' mask in DetailHeadFade.kt).
            .graphicsLayer()
    }
}
