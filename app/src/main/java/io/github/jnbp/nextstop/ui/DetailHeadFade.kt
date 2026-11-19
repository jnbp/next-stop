package io.github.jnbp.nextstop.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/*
 * #238: the items of the detail page's lazy list fade out beneath a pinned ride head (see
 * underHeadAlpha in HeadFadeCurve.kt). Each item masks itself: a vertical gradient from
 * transparent (the head's middle) to opaque (a little below its bottom), laid over the item
 * with DstIn in an offscreen layer, only while the item reaches into that band. Where the item
 * and the head lie is read while drawing (the list's layout info), so scrolling recomposes
 * nothing; the content sits in a layer of its own, so a frame only redraws the mask; and the
 * gradient is made anew only when the band changes (another head, of another height).
 */

// Opaque again this far below the bottom edge of the pinned head.
private val HEAD_FADE_CLEAR = 10.dp

/** Where one item lies against the pinned head; read while drawing, holds no state. */
private class HeadFadeProbe(
    private val list: LazyListState,
    private val key: Any,
    private val headType: Any,
    private val clear: Float,
) {
    // Where the item is fully transparent (in its own coordinates), and how tall the band up to opaque is.
    var zero = 0f
        private set
    var band = 0f
        private set

    // Whether the layer of the item is offscreen right now: only then is the mask drawn.
    var offscreen = false

    private var brush: Brush? = null
    private var brushBand = -1f

    /** Reads where the item and the pinned head lie; true while the item reaches into the fade. */
    fun update(height: Float): Boolean {
        val items = list.layoutInfo.visibleItemsInfo
        var own = Int.MIN_VALUE
        var headIndex = -1
        var headOffset = 0
        var headSize = 0
        for (i in items.indices) {
            val item = items[i]
            if (own == Int.MIN_VALUE && item.key == key) own = item.offset
            if (headIndex < 0 && item.contentType == headType && item.offset <= 0) {
                headIndex = item.index
                headOffset = item.offset
                headSize = item.size
            }
        }
        if (own == Int.MIN_VALUE || headIndex < 0 || height <= 0f) return false
        if (!headCoversContent(headIndex, headOffset, list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset)) return false
        val zeroAt = headOffset + headSize / 2f
        val oneAt = headOffset + headSize + clear
        // The item starts below the band: nothing of it fades.
        if (own >= oneAt) return false
        zero = zeroAt - own
        band = oneAt - zeroAt
        return band > 0f
    }

    /** The mask over the band: transparent at its top, opaque at its bottom and below (underHeadAlpha, linear). */
    fun brush(): Brush {
        val made = brush
        if (made != null && brushBand == band) return made
        val fresh = Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = 0f, endY = band)
        brush = fresh
        brushBand = band
        return fresh
    }
}

/**
 * The modifier of the item [key] of [list] that fades it out beneath a pinned head (the items of
 * the content type [headType]); the heads themselves do not take it.
 */
@Composable
internal fun rememberHeadFade(list: LazyListState, key: Any, headType: Any): Modifier {
    val clear = with(LocalDensity.current) { HEAD_FADE_CLEAR.toPx() }
    return remember(list, key, headType, clear) {
        val probe = HeadFadeProbe(list, key, headType, clear)
        Modifier
            .graphicsLayer {
                probe.offscreen = probe.update(size.height)
                compositingStrategy = if (probe.offscreen) CompositingStrategy.Offscreen else CompositingStrategy.Auto
            }
            .drawWithContent {
                drawContent()
                // Read here too, so a scrolled pixel redraws the mask (not the content, see below).
                if (probe.update(size.height) && probe.offscreen) {
                    val top = probe.zero
                    translate(top = top) {
                        drawRect(probe.brush(), topLeft = Offset(0f, -top), size = size, blendMode = BlendMode.DstIn)
                    }
                }
            }
            // The content in a layer of its own: redrawing the mask does not draw it anew.
            .graphicsLayer()
    }
}
