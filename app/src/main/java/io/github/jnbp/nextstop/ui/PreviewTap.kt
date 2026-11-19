package io.github.jnbp.nextstop.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/*
 * Previews in the settings that answer a tap (see PreviewExpander, SketchOptions): a tap on
 * the preview of a switch turns it over, a tap on the preview of a choice moves on to the next
 * value, and a tap on one of the side-by-side sketches of a choice picks that value. The
 * preview dips a little under the finger. The real controls stay as they are: the previews
 * remain hidden from accessibility services, so nothing changes for them.
 */

/** What a tap on a preview does: [tap] on the preview as a whole, [pick] on one of its options (by index). */
internal class PreviewActions(val tap: (() -> Unit)? = null, val pick: ((Int) -> Unit)? = null)

/** The actions of the preview being drawn now; null outside a row that offers them. */
internal val LocalPreviewActions = compositionLocalOf<PreviewActions?> { null }

/**
 * The value after [current] of [count] in a choice that a tap on its preview steps through,
 * round again after the last; a [current] outside the choice (e.g. an own value) goes on with the first.
 */
internal fun cycleIndex(current: Int, count: Int): Int = when {
    count <= 0 -> 0
    current !in 0 until count -> 0
    else -> (current + 1) % count
}

// How small a pressed preview gets: far less than a button, it is a large area.
private const val PREVIEW_PRESS_SCALE = 0.97f

/**
 * A tap on this element calls [onClick], with a small haptic tick; while pressed it gives way a
 * little and springs back (not with animations off or the battery saver at "Extreme"). No
 * ripple: the sketch itself shows the change.
 */
internal fun Modifier.previewPress(onClick: () -> Unit): Modifier = composed {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val animate = rememberAnimationsOn()
    val scale by animateFloatAsState(
        targetValue = if (pressed && animate) PREVIEW_PRESS_SCALE else 1f,
        animationSpec = if (animate) spring<Float>(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium) else snap<Float>(),
        label = "previewPress",
    )
    val ticks = rememberTicks()
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(interactionSource = interactions, indication = null) {
            ticks.tick()
            onClick()
        }
}
