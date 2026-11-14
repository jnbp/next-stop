package io.github.jnbp.nextstop.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import kotlin.math.roundToInt

/**
 * A part of a page above a list that folds away while the list is scrolled down and comes back
 * with any scroll up, wherever the list is (a "quick return" toolbar). [connection] goes on a
 * parent of the list (nestedScroll); the part itself is a [QuickReturnSection]. While it folds,
 * it takes the scroll, so the list grows into the room it frees and follows the finger without
 * a jump. Let go half way, it settles open or closed: with a spring when [animated], else at once
 * (and without fading). [canFold] says whether the list has anything to scroll at all.
 */
@Stable
class QuickReturnState(private val animated: Boolean, private val canFold: () -> Boolean) {
    /** The full height of the part, in pixels. */
    var height by mutableIntStateOf(0)
        internal set

    /** How far the part is folded away, from 0 (all shown) to [height]. */
    var offset by mutableFloatStateOf(0f)
        private set

    /** How far the part is folded away, from 0 to 1. */
    val fraction: Float get() = if (height <= 0) 0f else (offset / height).coerceIn(0f, 1f)

    /** Whether more than half of the part is away. */
    val mostlyFolded: Boolean get() = height > 0 && offset > height / 2f

    /** Shows the part again at once (another stop, another list). */
    fun show() {
        offset = 0f
    }

    internal val fades: Boolean get() = animated

    val connection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val dy = available.y
            if (dy == 0f || height <= 0) return Offset.Zero
            // Down the list (finger up) folds only a list that can scroll; up always unfolds.
            if (dy < 0f && !canFold()) return Offset.Zero
            val before = offset
            offset = (offset - dy).coerceIn(0f, height.toFloat())
            return Offset(0f, before - offset)
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            val full = height.toFloat()
            if (offset <= 0f || offset >= full) return Velocity.Zero
            val target = if (offset > full / 2f) full else 0f
            if (animated) {
                animate(
                    initialValue = offset,
                    targetValue = target,
                    animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow),
                ) { value, _ -> offset = value }
            } else {
                offset = target
            }
            return Velocity.Zero
        }
    }
}

/** A [QuickReturnState] for a list that [canFold] (has anything to scroll); see there. */
@Composable
fun rememberQuickReturnState(animate: Boolean, canFold: () -> Boolean): QuickReturnState =
    remember(animate) { QuickReturnState(animate, canFold) }

/**
 * The part of [state] that folds away: measured at its full height, it is moved up by the
 * folded share and cut off at its new lower edge (and fades with it), so what lies below
 * moves up into the room it frees.
 */
@Composable
fun QuickReturnSection(state: QuickReturnState, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(
        content = content,
        modifier = modifier
            .clipToBounds()
            .graphicsLayer { if (state.fades) alpha = 1f - state.fraction },
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)) }
        val full = placeables.sumOf { it.height }
        val width = (placeables.maxOfOrNull { it.width } ?: 0).coerceIn(constraints.minWidth, constraints.maxWidth)
        if (state.height != full) state.height = full
        val folded = state.offset.roundToInt().coerceIn(0, full)
        layout(width, (full - folded).coerceIn(constraints.minHeight, constraints.maxHeight)) {
            var y = -folded
            placeables.forEach { placeable ->
                placeable.place(0, y)
                y += placeable.height
            }
        }
    }
}
