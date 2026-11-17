package io.github.jnbp.nextstop.wear.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp

/*
 * The clock at the top of a round screen (the scaffold's TimeText) is drawn over the list: a
 * header that scrolls up would slide under it and both would be unreadable ("Stop name" behind
 * "08:09"). The list fades out where the clock stands instead.
 */

// Where the fade is fully clear, as a share of the room above the list's content (the clock's room).
private const val TOP_FADE_CLEAR = 0.5f

/**
 * Fades what is drawn in the top [top] of this list (its content padding at the top, the room of
 * the clock) out towards the edge: clear in the upper half, full again at [top], where the
 * content starts at rest, so nothing at rest is faded.
 */
fun Modifier.fadeUnderTimeText(top: Dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val end = top.toPx().coerceAtLeast(1f)
        if (end >= size.height) return@drawWithContent
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                TOP_FADE_CLEAR to Color.Transparent,
                1f to Color.Black,
                startY = 0f,
                endY = end,
            ),
            blendMode = BlendMode.DstIn,
        )
    }
