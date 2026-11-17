package io.github.jnbp.nextstop.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
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

    /**
     * False while the map draws into a surface of its own (a SurfaceView, see
     * PerfLab.textureMap): such a map cannot be read back at once, its picture comes only from
     * snapshots taken while the camera rests (see MapBackground). Until the first one is there,
     * or with no snapshots at all, the glass shows a frosted tint (see glassGround).
     */
    var readback by mutableStateOf(true)

    // While the camera pans or zooms, the picture is not taken again for every frame: it moves
    // and grows with the map instead (see BackdropCamera.follow in MapBackground). [shiftX] and [shiftY]
    // are where the top left corner of the picture lies on the screen now, in pixels, [grow]
    // how much the map was zoomed since it was taken. Read only while drawing.
    var shiftX by mutableFloatStateOf(0f)
    var shiftY by mutableFloatStateOf(0f)
    var grow by mutableFloatStateOf(1f)

    // The picture as a brush that repeats its edge pixels, for a picture that was moved: the
    // map that came into view since then is filled with the blurred colours at its edge.
    internal var brush: ShaderBrush? = null

    /** Puts the picture back where it was taken: the map has not moved since. */
    fun settle() {
        shiftX = 0f
        shiftY = 0f
        grow = 1f
    }
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
            // Glass over a map that cannot be read back (a SurfaceView): the tint alone, made
            // opaque enough that the sharp map under it does not show through the text, a touch
            // lighter at the top, like the soft light of the glass. Smooth, with no noise.
            val frost = Brush.verticalGradient(listOf(frosted(tint, FROST_TOP_ALPHA), frosted(tint, FROST_BOTTOM_ALPHA)))
            onDrawBehind {
                AllocProbe.begin(AllocProbe.GLASS_DRAW)
                val image = backdrop?.image
                if (backdrop == null || image == null) {
                    // No picture yet: a map that is a surface gets the frosted tint until its first snapshot.
                    if (backdrop != null && !backdrop.readback) drawRect(frost) else drawRect(fallback)
                } else {
                    val shiftX = backdrop.shiftX
                    val shiftY = backdrop.shiftY
                    val ratio = backdrop.scale * backdrop.grow
                    val at = origin.value
                    val brush = backdrop.brush
                    withTransform({
                        translate(left = shiftX - at.x, top = shiftY - at.y)
                        scale(ratio, ratio, Offset.Zero)
                    }) {
                        if (brush == null || (shiftX == 0f && shiftY == 0f && backdrop.grow == 1f)) {
                            drawImage(image)
                        } else {
                            // Moved with the map: the part of the picture behind this element,
                            // its edge colours carried on where the picture ends.
                            drawRect(
                                brush,
                                topLeft = Offset((at.x - shiftX) / ratio, (at.y - shiftY) / ratio),
                                size = Size(size.width / ratio, size.height / ratio),
                            )
                        }
                    }
                    // As opaque as the text on it needs over this map (see GlassContrast).
                    drawRect(readableGlassTint(tint, inner))
                }
                if (inner.alpha > 0f) drawRect(inner)
                if (shine != null) drawRect(shine, size = size.copy(height = depth))
                AllocProbe.end(AllocProbe.GLASS_DRAW)
            }
        }
}

// How opaque the tint of the glass is over a map that cannot be read back, at the top and at
// the bottom of the element: the map under it is sharp, not blurred, so it may only shimmer through.
private const val FROST_TOP_ALPHA = 0.88f
private const val FROST_BOTTOM_ALPHA = 0.94f

/** [tint] at least as opaque as [alpha]. */
private fun frosted(tint: Color, alpha: Float): Color = if (tint.alpha >= alpha) tint else tint.copy(alpha = alpha)

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
    // The fade erases what is drawn; in a layer of its own it cannot erase what lies behind. Only
    // a fade needs that layer: without one the content is drawn straight, with no extra pass.
    .graphicsLayer {
        compositingStrategy = if ((cutAtStart() || cutAtEnd()) && edgeAlpha() < 1f) {
            CompositingStrategy.Offscreen
        } else {
            CompositingStrategy.Auto
        }
    }
    .drawWithContent {
        drawContent()
        AllocProbe.begin(AllocProbe.FADE_EDGES)
        fadeMask(horizontal, length, cutAtStart, cutAtEnd, edgeAlpha)
        AllocProbe.end(AllocProbe.FADE_EDGES)
    }

/** The mask of [fadeEdges] over what was drawn. */
private fun ContentDrawScope.fadeMask(
    horizontal: Boolean,
    length: Dp,
    cutAtStart: () -> Boolean,
    cutAtEnd: () -> Boolean,
    edgeAlpha: () -> Float,
) {
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
    // The fade erases what is drawn; in a layer of its own it cannot erase what lies behind. Only
    // content that can scroll fades: other content (a chart that fits its card) needs no layer.
    .graphicsLayer {
        compositingStrategy = if (state.maxValue > 0) CompositingStrategy.Offscreen else CompositingStrategy.Auto
    }
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
    private var brushes: List<ShaderBrush> = emptyList()
    private var pixels = IntArray(0)
    private var scratch = IntArray(0)
    private var turn = 0

    /**
     * Reads the map as it is drawn right now and publishes it, small and blurred; false where
     * there was nothing to read. Call on the main thread.
     */
    suspend fun update(texture: TextureView): Boolean {
        if (!texture.isAvailable) return false
        val source = prepare(texture.width, texture.height) ?: return false
        texture.getBitmap(source)
        publish(source, texture.width)
        return true
    }

    /**
     * Publishes a [snapshot] of a map of [viewWidth] by [viewHeight] pixels (MapLibreMap.snapshot),
     * small and blurred; false where the map is too small. The snapshot is only read: the caller
     * still owns it. Call on the main thread.
     */
    suspend fun update(snapshot: Bitmap, viewWidth: Int, viewHeight: Int): Boolean {
        val source = prepare(viewWidth, viewHeight) ?: return false
        withContext(Dispatchers.Default) {
            Canvas(source).drawBitmap(snapshot, null, Rect(0, 0, source.width, source.height), scalePaint)
        }
        publish(source, viewWidth)
        return true
    }

    // Smooth scaling of a snapshot down to the small picture.
    private val scalePaint = Paint(Paint.FILTER_BITMAP_FLAG)

    /** The bitmap a map of [viewWidth] by [viewHeight] pixels is read into, made anew for a new size; null where it is too small. */
    private fun prepare(viewWidth: Int, viewHeight: Int): Bitmap? {
        val width = viewWidth / BACKDROP_DOWNSCALE
        val height = viewHeight / BACKDROP_DOWNSCALE
        if (width < 8 || height < 8) return null
        if (raw?.width != width || raw?.height != height) {
            release()
            raw = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            frames = List(2) { Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) }
            images = frames.map { it.asImageBitmap() }
            brushes = images.map { ShaderBrush(ImageShader(it, TileMode.Clamp, TileMode.Clamp)) }
            pixels = IntArray(width * height)
            scratch = IntArray(width * height)
        }
        return raw
    }

    /** Blurs [source] (the small picture of a map [viewWidth] pixels wide) into the frame not on screen and shows it. */
    private suspend fun publish(source: Bitmap, viewWidth: Int) {
        val width = source.width
        val height = source.height
        turn = 1 - turn
        val frame = frames[turn]
        val buffer = pixels
        val spare = scratch
        withContext(Dispatchers.Default) {
            source.getPixels(buffer, 0, width, 0, 0, width, height)
            blurPixels(buffer, spare, width, height, blurRadius)
            frame.setPixels(buffer, 0, width, 0, 0, width, height)
        }
        backdrop.scale = viewWidth.toFloat() / width
        backdrop.brush = brushes[turn]
        backdrop.image = images[turn]
    }

    /** Takes the picture off the screen first, then gives the bitmaps back. */
    fun release() {
        backdrop.image = null
        backdrop.brush = null
        backdrop.settle()
        raw?.recycle()
        frames.forEach { it.recycle() }
        raw = null
        frames = emptyList()
        images = emptyList()
        brushes = emptyList()
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
