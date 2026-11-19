package io.github.jnbp.nextstop.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.github.jnbp.nextstop.ui.theme.AppColors
import io.github.jnbp.nextstop.ui.theme.Appearance
import org.maplibre.android.maps.MapLibreMap

/**
 * The colours of the destination badge (#181) on the map as it is shown now: its [fill] (see
 * [destinationFlagArgb]), the dark [ink] of its outline (on every map), the [glyph] colour of
 * the flag inside it (dark on the lime, see [glyphInkArgb]) and, on a dark map, the thin light
 * [halo] outside the outline that parts it from the dark ground (null on a light map).
 */
class FlagLook(val fill: Color, val ink: Color, val glyph: Color, val halo: Color? = null)

// The ink of the outline (see BADGE_INK_ARGB) and the light glyph on a dark fill.
private val FLAG_INK = Color(BADGE_INK_ARGB)
private val FLAG_GLYPH_LIGHT = Color(0xFFECECEC)

/**
 * The look of the destination badge for the map style and the app's colours now: filled with
 * the app's lime (its second accent with Material You or an own accent), or its accent where
 * the lime would look like the own position's dot (the accent, see PositionDot), never red.
 * Edged in the dark ink on the light and the dark map alike; on the dark map a 1 dp light
 * line outside it, no thick light ring.
 */
@Composable
fun rememberFlagLook(): FlagLook {
    val dark = isDarkMapStyle(mapStyleUrl(Appearance.mapStyle, AppColors.palette.dark))
    val accent = AppColors.Blue
    val second = AppColors.Lime
    return remember(dark, accent, second) {
        val fill = destinationFlagArgb(intArrayOf(second.toArgb(), accent.toArgb()), ownDot = accent.toArgb(), fallback = FLAG_INK.toArgb())
        val glyph = glyphInkArgb(fill, dark = FLAG_INK.toArgb(), light = FLAG_GLYPH_LIGHT.toArgb())
        FlagLook(Color(fill), FLAG_INK, Color(glyph), if (dark) Color(BADGE_DARK_HALO_ARGB) else null)
    }
}

/** The flag glyph of the big map's badge (see [flagGlyph]) in pixels around 0, 0, made once per density. */
@Composable
fun rememberFlagPath(): Path {
    val density = LocalDensity.current.density
    return remember(density) { flagGlyphPath(DESTINATION_BADGE_RADIUS_DP * density) }
}

/** The flag glyph inside a badge of [radius] pixels as a path, centred on 0, 0. */
fun flagGlyphPath(radius: Float): Path {
    val g = flagGlyph(radius)
    return Path().apply {
        moveTo(g[0], g[1])
        for (i in 2 until g.size step 2) lineTo(g[i], g[i + 1])
        close()
    }
}

/**
 * The destination at [at] as a round badge (#181): a circle in the [look]'s fill, edged in the
 * dark ink (on a dark map with a thin light line outside), the flag [glyph] (see
 * [rememberFlagPath]) in its middle. Still: nothing here asks for another frame.
 */
fun DrawScope.drawDestinationFlag(at: Offset, look: FlagLook, glyph: Path, alpha: Float = 1f) {
    val radius = DESTINATION_BADGE_RADIUS_DP.dp.toPx()
    val outline = BADGE_OUTLINE_DP.dp.toPx()
    look.halo?.let { halo ->
        val width = BADGE_DARK_HALO_DP.dp.toPx()
        drawCircle(halo, radius = radius + outline / 2f + width / 2f, center = at, alpha = alpha, style = Stroke(width = width))
    }
    drawCircle(look.fill, radius = radius, center = at, alpha = alpha)
    drawCircle(look.ink, radius = radius, center = at, alpha = alpha, style = Stroke(width = outline))
    translate(at.x, at.y) {
        drawPath(glyph, look.glyph, alpha = alpha)
    }
}

/**
 * The destination badge of a journey at [at] (latitude, longitude) over [map], on a canvas of
 * its own: placed after every other mark of the journey and its vehicles, so nothing is drawn
 * over it (#181c). It fades in once, in [appearMillis], with the journey's names, and fades out
 * once the traveller has [arrived] (#222).
 */
@Composable
fun JourneyDestinationBadge(
    map: MapLibreMap,
    cameraTick: () -> Int,
    at: Pair<Double, Double>?,
    appearMillis: Int,
    arrived: Boolean = false,
) {
    if (at == null) return
    val look = rememberFlagLook()
    val glyph = rememberFlagPath()
    val geo = remember(at) { doubleArrayOf(at.first, at.second) }
    val out = remember(at) { DoubleArray(2) }
    val appear = remember(at) { Animatable(0f) }
    // Once the traveller has arrived the badge fades out, once (#222): only the own dot stays.
    val gone = remember(at) { booleanArrayOf(false) }
    val animate = rememberAnimationsOn()
    LaunchedEffect(appear, arrived) {
        if (arrived || gone[0]) {
            gone[0] = true
            if (animate) appear.animateTo(0f, tween(ARRIVED_FADE_MILLIS, easing = FastOutSlowInEasing)) else appear.snapTo(0f)
        } else {
            if (animate) appear.animateTo(1f, tween(appearMillis, easing = FastOutSlowInEasing)) else appear.snapTo(1f)
        }
    }
    if (gone[0] && appear.value == 0f) return
    Canvas(modifier = Modifier.fillMaxSize()) {
        cameraTick()
        if (!MapAlive.alive(map)) return@Canvas
        map.projection.toScreenLocations(geo, out)
        drawDestinationFlag(Offset(out[0].toFloat(), out[1].toFloat()), look, glyph, alpha = appear.value)
    }
}

// How long the badge takes to fade out once the traveller has arrived (#222).
private const val ARRIVED_FADE_MILLIS = 600
