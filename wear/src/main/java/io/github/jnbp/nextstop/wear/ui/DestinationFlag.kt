package io.github.jnbp.nextstop.wear.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

/*
 * The destination of the trip on the watch's map (#181), as on the phone: a still round badge
 * in the lime, edged in a dark ink on every map (on a dark one with a thin light line outside),
 * with a small solid flag inside. Scaled to the watch's
 * maps: the small map of the card and the opened one. In ambient mode only its white outline
 * and a white flag on black.
 */

/** The badge's radius on the small map of the card, as a share of the phone's (about 7 dp). */
const val FLAG_SCALE_SMALL = 0.55f

/** The badge's radius on the opened map, as a share of the phone's (about 10 dp). */
const val FLAG_SCALE_LARGE = 0.77f

/**
 * The fill of the badge on the watch: the brand lime, unless it looks like the own position's
 * dot [ownDot], then the brand blue; never red; [ink] where neither fits.
 */
fun watchFlagColor(ownDot: Color, ink: Color): Color =
    Color(destinationFlagArgb(intArrayOf(Brand.Lime.toArgb(), Brand.Blue.toArgb()), ownDot.toArgb(), ink.toArgb()))

/** The colour of the flag glyph on a badge filled with [fill]: dark on the lime, white on a dark fill. */
fun watchGlyphColor(fill: Color): Color =
    Color(glyphInkArgb(fill.toArgb(), dark = BADGE_INK_ARGB, light = 0xFFFFFFFF.toInt()))

/** The dark outline of the badge on every map of the watch (see BADGE_INK_ARGB). */
val WATCH_BADGE_INK = Color(BADGE_INK_ARGB)

/** The thin light line outside the outline on a dark map of the watch, null on a light one. */
fun watchBadgeHalo(darkMap: Boolean): Color? = if (darkMap) Color(BADGE_DARK_HALO_ARGB) else null

/** The flag glyph of a badge [scale] times the phone's size, in pixels at [density], centred on 0, 0. */
fun watchFlagPath(density: Float, scale: Float): Path {
    val g = flagGlyph(BADGE_RADIUS_DP * density * scale)
    return Path().apply {
        moveTo(g[0], g[1])
        for (i in 2 until g.size step 2) lineTo(g[i], g[i + 1])
        close()
    }
}

/**
 * The destination at [at]: a circle in [fill] edged in [ink], the flag [glyph] (see
 * [watchFlagPath], made at the same [scale]) in [glyphColor] in its middle; a thin [halo]
 * line outside the outline where given (a dark map). In [ambient] mode
 * the circle is black inside a white outline and the flag white. Still: no frame asked for.
 */
fun DrawScope.drawWatchDestinationFlag(
    at: Offset,
    glyph: Path,
    fill: Color,
    ink: Color,
    glyphColor: Color,
    scale: Float,
    ambient: Boolean,
    halo: Color? = null,
) {
    val radiusDp = BADGE_RADIUS_DP * scale
    val radius = radiusDp.dp.toPx()
    if (!ambient && halo != null) {
        val width = BADGE_DARK_HALO_DP.dp.toPx()
        drawCircle(halo, radius = radius + badgeOutlineDp(radiusDp).dp.toPx() / 2f + width / 2f, center = at, style = Stroke(width = width))
    }
    drawCircle(if (ambient) Color.Black else fill, radius = radius, center = at)
    drawCircle(if (ambient) Color.White else ink, radius = radius, center = at, style = Stroke(width = badgeOutlineDp(radiusDp).dp.toPx()))
    translate(at.x, at.y) {
        drawPath(glyph, if (ambient) Color.White else glyphColor)
    }
}
