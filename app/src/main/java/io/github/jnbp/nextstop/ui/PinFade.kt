package io.github.jnbp.nextstop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset

/*
 * #245: a scrolled column fades out beneath a line pinned over its top, as the detail page's
 * rides beneath their pinned heads (#238, same curve: underHeadAlpha in HeadFadeCurve.kt): fully
 * transparent at the line's vertical middle, fully visible a little below its bottom edge, so
 * nothing shows behind its rounded corners. Used by the status line of the Live-Fahrt sheet over
 * the map (#225) and the sticky blue box of the calm view (#156). The column (its viewport,
 * outside verticalScroll) masks itself with one gradient laid over it with DstIn in an offscreen
 * layer, only while the line is there and the content is scrolled (pinCoversContent); at rest
 * nothing changes. Where the line lies and how far the content is scrolled are read while
 * drawing, so scrolling recomposes nothing; the gradient is made anew only when the line's band
 * changes (it slides in, the blue box settles smaller), never while scrolling. #258: while the
 * line is there, the content is also clipped above the line's middle, at rest too (pinClipTop),
 * so nothing of it shows in the gap between the line and the header above the column.
 */

// Opaque again this far below the bottom edge of the pinned line (as under the detail page's heads).
private val PIN_FADE_CLEAR = 10.dp

/** The mask of one column; read while drawing, holds no state of the composition. */
private class PinFadeMask(private val clear: Float) {
    var offscreen = false
    private var brush: Brush? = null
    private var brushZero = Float.NaN
    private var brushOne = Float.NaN

    /** The gradient from transparent (the line's middle) to opaque (a little below it), in the column's coordinates. */
    fun brush(top: Float, bottom: Float): Brush {
        val zero = underHeadZero(top, bottom)
        val one = underHeadOne(bottom, clear)
        val made = brush
        if (made != null && zero == brushZero && one == brushOne) return made
        val fresh = Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = zero, endY = one)
        brush = fresh
        brushZero = zero
        brushOne = one
        return fresh
    }
}

/**
 * The modifier of a scrolling column (put before its verticalScroll) over whose top a line is
 * pinned from [top] to [bottom] (pixels below the column's top, read while drawing; a [bottom]
 * of 0 is no line), with the content scrolled by [scrolled] pixels: the content fades out
 * beneath the line while it is scrolled.
 */
@Composable
internal fun rememberPinFade(top: () -> Float, bottom: () -> Float, scrolled: () -> Int): Modifier {
    val clear = with(LocalDensity.current) { PIN_FADE_CLEAR.toPx() }
    val topNow by rememberUpdatedState(top)
    val bottomNow by rememberUpdatedState(bottom)
    val scrolledNow by rememberUpdatedState(scrolled)
    return remember(clear) {
        val mask = PinFadeMask(clear)
        Modifier
            .graphicsLayer {
                val bottomAt = bottomNow()
                mask.offscreen = pinCoversContent(bottomAt, scrolledNow()) && bottomAt > topNow()
                compositingStrategy = if (mask.offscreen) CompositingStrategy.Offscreen else CompositingStrategy.Auto
            }
            .drawWithContent {
                val topAt = topNow()
                val bottomAt = bottomNow()
                // #258: while the line is there nothing of the content draws above its middle,
                // scrolled or not (a plain clip, no offscreen layer): the gap between the line and
                // what stands above the column stays empty. Wide to the sides and below, where the
                // scroll container clips as before (shadows of cards may reach out sideways).
                val clipTop = pinClipTop(topAt, bottomAt)
                if (clipTop == null) {
                    drawContent()
                } else {
                    clipRect(left = -size.width, top = clipTop, right = size.width * 2f, bottom = size.height * 2f) {
                        this@drawWithContent.drawContent()
                    }
                }
                if (mask.offscreen && pinCoversContent(bottomAt, scrolledNow()) && bottomAt > topAt) {
                    drawRect(mask.brush(topAt, bottomAt), size = size, blendMode = BlendMode.DstIn)
                }
            }
    }
}

/**
 * Room of [room] pixels (read while measuring) above the content, as a top padding that follows
 * a pinned line growing or shrinking without recomposing: the content of a column that scrolls
 * beneath that line starts below it at rest.
 */
internal fun Modifier.pinRoom(room: () -> Int): Modifier = layout { measurable, constraints ->
    val top = room().coerceAtLeast(0)
    val placeable = measurable.measure(constraints.offset(vertical = -top))
    layout(placeable.width, placeable.height + top) { placeable.place(0, top) }
}
