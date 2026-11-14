package io.github.jnbp.nextstop.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

// How far a card has to be pulled down, or how fast it has to be flung (px per second), to go.
private val SWIPE_DISMISS_DISTANCE = 72.dp
private const val SWIPE_DISMISS_VELOCITY = 1400f

/**
 * A card that follows the finger downwards and is let go with [onDismiss] when pulled far or
 * fast enough; otherwise it springs back. Upwards it gives only a little. While [shownKey]
 * changes (a new stop is marked), a card still pulled down springs back into place.
 */
@Composable
fun Modifier.swipeDownToDismiss(shownKey: Any?, onDismiss: () -> Unit): Modifier {
    var pulled by remember { mutableFloatStateOf(0f) }
    val dismiss by rememberUpdatedState(onDismiss)
    val threshold = with(LocalDensity.current) { SWIPE_DISMISS_DISTANCE.toPx() }
    LaunchedEffect(shownKey) {
        if (shownKey != null && pulled != 0f) {
            animate(pulled, 0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { value, _ -> pulled = value }
        }
    }
    val state = rememberDraggableState { delta ->
        // Up only a little, with resistance; down one to one.
        pulled = if (pulled + delta < 0f) (pulled + delta / 4f).coerceAtLeast(-threshold / 4f) else pulled + delta
    }
    return this
        .graphicsLayer { translationY = pulled }
        .draggable(
            state = state,
            orientation = Orientation.Vertical,
            onDragStopped = { velocity ->
                if (pulled > threshold || velocity > SWIPE_DISMISS_VELOCITY) {
                    // The card leaves from where it is (its exit animation takes over).
                    dismiss()
                } else {
                    animate(pulled, 0f, animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)) { value, _ ->
                        pulled = value
                    }
                }
            },
        )
}
