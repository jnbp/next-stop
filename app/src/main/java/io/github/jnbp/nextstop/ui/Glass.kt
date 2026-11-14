package io.github.jnbp.nextstop.ui

import android.graphics.Bitmap
import android.view.TextureView
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.jnbp.nextstop.ui.theme.AppColors

// The map is read at this fraction of its size: small enough to be cheap, and the
// enlargement back to full size already softens it.
private const val BACKDROP_DOWNSCALE = 8

/**
 * What lies behind the glass: a small, blurred picture of the map as it is drawn right now.
 * [scale] is how much larger the map is than the picture. Without a picture, glass
 * surfaces fall back to a nearly opaque white.
 */
@Stable
class Backdrop {
    var image by mutableStateOf<ImageBitmap?>(null)
    var scale by mutableFloatStateOf(1f)
}

/**
 * How many glass surfaces lie around this point: 0 on the map, 1 inside a sheet or card. A
 * glass surface or glass chip inside another one is drawn as an inner card instead (see
 * AppColors.InnerCard), so it stands out from the glass it lies on.
 */
val LocalGlassDepth = compositionLocalOf { 0 }

/** The rim of a glass element here: the light rim of glass on the map, a faint light edge in a sheet. */
@Composable
fun glassRim(): Color = if (LocalGlassDepth.current > 0) AppColors.InnerBorder else AppColors.GlassBorder

/** The backdrop of the map that fills the window; null where there is no map behind, as in a dialog. */
val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }

// How far down the soft light at the top edge of glass reaches, at most.
private val GLASS_HIGHLIGHT_DEPTH = 36.dp

/**
 * Frosted glass as a background: the part of the blurred map that lies behind this
 * element, under a translucent [tint], with a soft [highlight] fading down from the top
 * edge. The picture is only read while drawing, so a new one or a move of the element
 * costs no recomposition, and no blur is worked out for it: every glass element shows a
 * part of the one picture the map already delivers. The colours come from the palette
 * (AppColors.Glass*), the single place for them.
 */
@Composable
fun glassGround(
    tint: Color = AppColors.GlassTint,
    fallback: Color = AppColors.Glass,
    highlight: Color = AppColors.GlassHighlight,
): Modifier {
    val backdrop = LocalBackdrop.current
    val origin = remember { mutableStateOf(Offset.Zero) }
    // Inside a sheet a glass element is an inner card: the same glass with a second, brighter
    // layer over it, so it stands out from the sheet by light alone.
    val inner = if (LocalGlassDepth.current > 0) AppColors.InnerCard else Color.Transparent
    return Modifier
        .onGloballyPositioned { origin.value = it.positionInRoot() }
        .drawWithCache {
            // Made again only when the size or the colour changes.
            val depth = minOf(GLASS_HIGHLIGHT_DEPTH.toPx(), size.height * 0.5f)
            val shine = if (highlight.alpha > 0f && depth > 0f) {
                Brush.verticalGradient(listOf(highlight, highlight.copy(alpha = 0f)), startY = 0f, endY = depth)
            } else {
                null
            }
            onDrawBehind {
                val image = backdrop?.image
                if (backdrop == null || image == null) {
                    drawRect(fallback)
                } else {
                    val ratio = backdrop.scale
                    val at = origin.value
                    withTransform({
                        translate(left = -at.x, top = -at.y)
                        scale(ratio, ratio, Offset.Zero)
                    }) {
                        drawImage(image)
                    }
                    drawRect(tint)
                }
                if (inner.alpha > 0f) drawRect(inner)
                if (shine != null) drawRect(shine, size = size.copy(height = depth))
            }
        }
}

/** Frosted surface that sits above the map. */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    content: @Composable () -> Unit,
) {
    val depth = LocalGlassDepth.current
    // Inside another glass surface: an inner card, lifted by a soft wide shadow and its
    // brighter glass, with no outline, only a hairline of light along its top edge.
    val nested = depth > 0
    val rim = if (nested) {
        Modifier.border(
            0.5.dp,
            Brush.verticalGradient(listOf(AppColors.InnerBorder, Color.Transparent), endY = INNER_RIM_DEPTH),
            shape,
        )
    } else {
        Modifier.border(1.dp, AppColors.GlassBorder, shape)
    }
    Box(
        modifier = modifier
            .shadow(
                if (nested) INNER_ELEVATION else 6.dp,
                shape,
                clip = false,
                ambientColor = if (nested) AppColors.InnerShadow else AppColors.GlassShadow,
                spotColor = if (nested) AppColors.InnerShadow else AppColors.GlassShadow,
            )
            .clip(shape)
            .then(glassGround())
            .then(rim)
            // Takes the touches on the card, so a drag on it does not move the map underneath.
            .pointerInput(Unit) {},
    ) {
        CompositionLocalProvider(LocalGlassDepth provides depth + 1) {
            content()
        }
    }
}

// The shadow of an inner card: wide and faint (see AppColors.InnerShadow), no hard edge.
private val INNER_ELEVATION = 10.dp

// How far down the hairline of light at the top edge of an inner card reaches, in pixels.
private const val INNER_RIM_DEPTH = 24f

/**
 * Lets scrolling content fade out softly where its container cuts it off, instead of
 * ending at a hard edge. [cutAtStart] and [cutAtEnd] say whether there is more beyond
 * that edge; they are read while drawing. [edgeAlpha] is how much of the content is left
 * at the very edge, also read while drawing: 0 lets it vanish, 1 shows no fade. Must come
 * before the scroll modifier.
 */
fun Modifier.fadeEdges(
    horizontal: Boolean,
    cutAtStart: () -> Boolean,
    cutAtEnd: () -> Boolean,
    length: Dp = 20.dp,
    edgeAlpha: () -> Float = { 0f },
): Modifier = this
    // The fade erases what is drawn; in a layer of its own it cannot erase what lies behind.
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val extent = if (horizontal) size.width else size.height
        val fade = length.toPx()
        val start = cutAtStart()
        val end = cutAtEnd()
        val edge = Color.Black.copy(alpha = edgeAlpha().coerceIn(0f, 1f))
        if ((start || end) && extent > fade * 2f && edge.alpha < 1f) {
            val share = fade / extent
            val stops = arrayOf(
                0f to (if (start) edge else Color.Black),
                share to Color.Black,
                1f - share to Color.Black,
                1f to (if (end) edge else Color.Black),
            )
            val mask = if (horizontal) Brush.horizontalGradient(*stops) else Brush.verticalGradient(*stops)
            drawRect(brush = mask, blendMode = BlendMode.DstIn)
        }
    }

/**
 * Fading edges for content moved by a scroll state. Each fade grows with the scroll offset on
 * its side, from nothing at the very edge to full depth once the content has moved [length]
 * past it, so it never pops in as scrolling starts or stops.
 */
fun Modifier.fadeEdges(state: ScrollState, horizontal: Boolean, length: Dp = 20.dp): Modifier = this
    // The fade erases what is drawn; in a layer of its own it cannot erase what lies behind.
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val extent = if (horizontal) size.width else size.height
        val fade = length.toPx()
        if (fade <= 0f || extent <= fade * 2f) return@drawWithContent
        // How deep each side fades, 0..1, as a smooth function of how far the content is
        // scrolled past that side.
        val startDepth = (state.value / fade).coerceIn(0f, 1f)
        val endDepth = ((state.maxValue - state.value) / fade).coerceIn(0f, 1f)
        if (startDepth <= 0f && endDepth <= 0f) return@drawWithContent
        val share = fade / extent
        val stops = arrayOf(
            0f to Color.Black.copy(alpha = 1f - startDepth),
            share to Color.Black,
            1f - share to Color.Black,
            1f to Color.Black.copy(alpha = 1f - endDepth),
        )
        val mask = if (horizontal) Brush.horizontalGradient(*stops) else Brush.verticalGradient(*stops)
        drawRect(brush = mask, blendMode = BlendMode.DstIn)
    }

/** Fading edges for a lazy row or column; [edgeAlpha] as in the general form. */
fun Modifier.fadeEdges(
    state: LazyListState,
    horizontal: Boolean,
    length: Dp = 20.dp,
    edgeAlpha: () -> Float = { 0f },
): Modifier = fadeEdges(horizontal, { state.canScrollBackward }, { state.canScrollForward }, length, edgeAlpha)

/**
 * Turns the map view into the backdrop of the glass surfaces. Works on bitmaps that are
 * allocated once and used again for every picture: one the map is read into, and two
 * that take turns on screen, so the one being shown is never written to.
 */
internal class BackdropCapture(private val backdrop: Backdrop, private val blurRadius: Int) {
    private var raw: Bitmap? = null
    private var frames: List<Bitmap> = emptyList()
    private var images: List<ImageBitmap> = emptyList()
    private var pixels = IntArray(0)
    private var scratch = IntArray(0)
    private var turn = 0

    /** Reads the map as it is drawn right now and publishes it, small and blurred. Call on the main thread. */
    suspend fun update(texture: TextureView) {
        val width = texture.width / BACKDROP_DOWNSCALE
        val height = texture.height / BACKDROP_DOWNSCALE
        if (!texture.isAvailable || width < 8 || height < 8) return
        if (raw?.width != width || raw?.height != height) {
            release()
            raw = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            frames = List(2) { Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) }
            images = frames.map { it.asImageBitmap() }
            pixels = IntArray(width * height)
            scratch = IntArray(width * height)
        }
        val source = raw ?: return
        texture.getBitmap(source)
        turn = 1 - turn
        val frame = frames[turn]
        val buffer = pixels
        val spare = scratch
        withContext(Dispatchers.Default) {
            source.getPixels(buffer, 0, width, 0, 0, width, height)
            blurPixels(buffer, spare, width, height, blurRadius)
            frame.setPixels(buffer, 0, width, 0, 0, width, height)
        }
        backdrop.scale = texture.width.toFloat() / width
        backdrop.image = images[turn]
    }

    /** Takes the picture off the screen first, then gives the bitmaps back. */
    fun release() {
        backdrop.image = null
        raw?.recycle()
        frames.forEach { it.recycle() }
        raw = null
        frames = emptyList()
        images = emptyList()
    }

    companion object {
        /** The blur of the design (22 dp) in pixels of the small picture. */
        fun radiusFor(density: Float): Int = (22f * density / BACKDROP_DOWNSCALE).toInt().coerceIn(2, 12)
    }
}

/** Three passes of a box blur come close to a Gaussian blur. [pixels] holds the result. */
private fun blurPixels(pixels: IntArray, scratch: IntArray, width: Int, height: Int, radius: Int) {
    repeat(3) {
        boxPass(pixels, scratch, lines = height, length = width, lineStep = width, step = 1, radius = radius)
        boxPass(scratch, pixels, lines = width, length = height, lineStep = 1, step = width, radius = radius)
    }
}

/**
 * One pass of a box blur along rows or along columns: every pixel becomes the mean of
 * its neighbours within [radius]. The sum moves along the line, so the cost does not
 * grow with the radius. Beyond the ends of a line its last pixel repeats.
 */
private fun boxPass(
    source: IntArray,
    target: IntArray,
    lines: Int,
    length: Int,
    lineStep: Int,
    step: Int,
    radius: Int,
) {
    val window = radius * 2 + 1
    val last = length - 1
    for (line in 0 until lines) {
        val start = line * lineStep
        var red = 0
        var green = 0
        var blue = 0
        for (offset in -radius..radius) {
            val color = source[start + offset.coerceIn(0, last) * step]
            red += (color shr 16) and 0xFF
            green += (color shr 8) and 0xFF
            blue += color and 0xFF
        }
        for (position in 0 until length) {
            target[start + position * step] =
                (0xFF shl 24) or ((red / window) shl 16) or ((green / window) shl 8) or (blue / window)
            val leaving = source[start + (position - radius).coerceIn(0, last) * step]
            val entering = source[start + (position + radius + 1).coerceIn(0, last) * step]
            red += ((entering shr 16) and 0xFF) - ((leaving shr 16) and 0xFF)
            green += ((entering shr 8) and 0xFF) - ((leaving shr 8) and 0xFF)
            blue += (entering and 0xFF) - (leaving and 0xFF)
        }
    }
}
