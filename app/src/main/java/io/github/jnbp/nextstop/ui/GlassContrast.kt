package io.github.jnbp.nextstop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import kotlin.math.pow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import io.github.jnbp.nextstop.ui.theme.AppColors
import io.github.jnbp.nextstop.ui.theme.contrastOf

/**
 * How light the blurred map behind the glass is: the relative luminance of its darkest and
 * of its lightest tenth (0 to 1), NaN while unknown. The glass tint of the palette is made
 * for a map of the same lightness as the app; a dark map under the light app (or a light one
 * under the dark app) showed through enough to put dark text on a dark ground, or white on
 * white. [readableGlassTint] makes the tint as opaque as the text on it needs.
 */
internal object GlassTone {
    var darkest by mutableFloatStateOf(Float.NaN)
        private set
    var lightest by mutableFloatStateOf(Float.NaN)
        private set

    internal fun set(dark: Float, light: Float) {
        darkest = dark
        lightest = light
    }
}

// The secondary text (AppColors.Muted) on glass reads at least at this contrast (WCAG AA).
private const val GLASS_MIN_CONTRAST = 4.5f

// How long a new picture of the map must stay before its lightness is measured.
private const val TONE_SETTLE_MILLIS = 200L

// Every so many pixels of the small picture are measured, in both directions.
private const val TONE_STEP = 2

/** Measures each new picture of [backdrop] for [GlassTone]. Put it once where the backdrop is made. */
@Composable
internal fun TrackBackdropTone(backdrop: Backdrop) {
    LaunchedEffect(backdrop) {
        snapshotFlow { backdrop.image }.collectLatest { image ->
            if (image == null) return@collectLatest
            delay(TONE_SETTLE_MILLIS)
            val tone = withContext(Dispatchers.Default) { toneOf(image) } ?: return@collectLatest
            GlassTone.set(tone.first, tone.second)
        }
    }
}

/** The luminance of the darkest and the lightest tenth of [image]; null when it cannot be read. */
private fun toneOf(image: ImageBitmap): Pair<Float, Float>? {
    val width = image.width
    val height = image.height
    if (width <= 0 || height <= 0) return null
    val pixels = IntArray(width * height)
    try {
        image.readPixels(pixels)
    } catch (e: RuntimeException) {
        // Recycled meanwhile: the next picture is measured instead.
        return null
    }
    val bins = IntArray(TONE_BINS)
    var count = 0
    var y = 0
    while (y < height) {
        var x = 0
        while (x < width) {
            val argb = pixels[y * width + x]
            val lum = 0.2126f * LINEAR[(argb shr 16) and 0xFF] + 0.7152f * LINEAR[(argb shr 8) and 0xFF] + 0.0722f * LINEAR[argb and 0xFF]
            bins[(lum * (TONE_BINS - 1)).toInt().coerceIn(0, TONE_BINS - 1)]++
            count++
            x += TONE_STEP
        }
        y += TONE_STEP
    }
    if (count == 0) return null
    fun percentile(share: Float): Float {
        val wanted = (count * share).toInt()
        var seen = 0
        for (bin in bins.indices) {
            seen += bins[bin]
            if (seen > wanted) return bin / (TONE_BINS - 1f)
        }
        return 1f
    }
    return percentile(0.1f) to percentile(0.9f)
}

private const val TONE_BINS = 64

// sRGB channel value (0 to 255) to linear light.
private val LINEAR = FloatArray(256) { value ->
    val c = value / 255f
    if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
}

/** The sRGB value (0 to 1) of a grey of relative [luminance]. */
private fun greyOf(luminance: Float): Float {
    val y = luminance.coerceIn(0f, 1f)
    return if (y <= 0.0031308f) y * 12.92f else (1.055f * y.toDouble().pow(1 / 2.4).toFloat() - 0.055f)
}

/** [top] laid over the opaque [below], as drawing does (in sRGB). */
private fun over(top: Color, below: Color): Color {
    val a = top.alpha
    return Color(
        red = top.red * a + below.red * (1f - a),
        green = top.green * a + below.green * (1f - a),
        blue = top.blue * a + below.blue * (1f - a),
    )
}

/**
 * [tint] of glass over the blurred map, made more opaque where the map behind it would leave
 * the text of the palette (its secondary text, [AppColors.Muted]) below a contrast of 4.5:1:
 * measured against the part of the map that is worst for that text (the darkest tenth under
 * dark text, the lightest under light text), with [inner] (the inner card) laid over it too.
 * Read while drawing; unchanged while the map is not measured yet.
 */
internal fun readableGlassTint(tint: Color, inner: Color): Color {
    val ink = AppColors.Muted
    val worst = if (ink.luminance() < 0.5f) GlassTone.darkest else GlassTone.lightest
    if (worst.isNaN() || tint.alpha >= 1f) return tint
    val grey = greyOf(worst)
    val map = Color(grey, grey, grey)
    var alpha = tint.alpha
    while (alpha < 1f) {
        var ground = over(tint.copy(alpha = alpha), map)
        if (inner.alpha > 0f) ground = over(inner, ground)
        if (contrastOf(ink, ground) >= GLASS_MIN_CONTRAST) break
        alpha = (alpha + 0.04f).coerceAtMost(1f)
    }
    return if (alpha == tint.alpha) tint else tint.copy(alpha = alpha)
}
