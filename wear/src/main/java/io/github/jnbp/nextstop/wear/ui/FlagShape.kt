package io.github.jnbp.nextstop.wear.ui

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/*
 * The destination of a trip on the map (#181): a round badge like the other round marks of
 * the map, filled with the app's lime, edged in a dark ink on every map, with a small solid flag in the
 * middle. It stands still: nothing here asks the map's overlay for another frame.
 *
 * The same as on the phone (app ui/FlagShape.kt). Pure: no Android, so the shape and the
 * choice of its colours are tested on the JVM.
 */

/** The radius of the round destination badge on the big map, in dp (the watch scales it). */
const val BADGE_RADIUS_DP = 13f

/** The width of the badge's outline in the map's ink on the big map, in dp. */
const val BADGE_OUTLINE_DP = 2.5f

// The height (and width) of the flag glyph inside the badge, as a share of the badge's diameter.
private const val GLYPH_SHARE = 0.45f

/**
 * The outline of a badge of [radiusDp] (dp): as wide as on the big map for its size, never
 * thinner than 1.5 dp, so the small maps and the watch keep a visible edge.
 */
fun badgeOutlineDp(radiusDp: Float): Float = max(BADGE_OUTLINE_DP * radiusDp / BADGE_RADIUS_DP, 1.5f)

/**
 * How far the badge of [radiusDp] reaches from its centre, in dp: its radius and the outer half
 * of its outline. The room no name goes over (the box around this circle).
 */
fun badgeReachDp(radiusDp: Float): Float = radiusDp + badgeOutlineDp(radiusDp) / 2f

/**
 * The ink of the badge's outline and of the flag on the lime, on every map, light or dark: a
 * near black. A light outline on the dark map read as a white target ring around the lime.
 */
const val BADGE_INK_ARGB: Int = 0xFF1D1D1B.toInt()

/** The thin light line just outside the outline on a dark map, in dp: it parts the dark edge from the dark ground. */
const val BADGE_DARK_HALO_DP = 1f

/** The colour (ARGB) of that thin line on a dark map. */
const val BADGE_DARK_HALO_ARGB: Int = 0xCCF2F2EE.toInt()

/** The light line outside the badge's outline, in dp: [BADGE_DARK_HALO_DP] on a [darkMap], none on a light one. */
fun badgeHaloDp(darkMap: Boolean): Float = if (darkMap) BADGE_DARK_HALO_DP else 0f

/**
 * True where a round mark at [dx], [dy] (px) from the badge's centre lies on the badge whose
 * outer edge is [reach] (px) away from its centre, or within [margin] (px) of it: such a mark
 * (the route's end dot, the last stop's dot, a stop position at the destination) is not drawn,
 * the badge already marks the end.
 */
fun coversBadge(dx: Float, dy: Float, reach: Float, margin: Float): Boolean {
    val limit = reach + margin
    return dx * dx + dy * dy < limit * limit
}

/**
 * Where a sign at [x], [y] (px) goes when it lies within [clear] (px) of the badge's centre
 * [bx], [by]: pushed straight away from the badge, to the side it already lies on, until its
 * centre is [clear] away; straight up where it sits on the very centre. Writes x and y into
 * [out]; true where it was moved.
 */
fun clearOfBadge(x: Float, y: Float, bx: Float, by: Float, clear: Float, out: FloatArray): Boolean {
    val dx = x - bx
    val dy = y - by
    val distance = kotlin.math.sqrt(dx * dx + dy * dy)
    if (distance >= clear) {
        out[0] = x
        out[1] = y
        return false
    }
    if (distance < 0.01f) {
        out[0] = bx
        out[1] = by - clear
    } else {
        out[0] = bx + dx / distance * clear
        out[1] = by + dy / distance * clear
    }
    return true
}

/**
 * The flag glyph inside a badge of [radius] (any unit), relative to the badge's centre: one
 * solid shape of seven corners (x, y each, 14 numbers), a short pole on the left with a
 * notched flag at its top. It is [GLYPH_SHARE] of the diameter high and wide, centred.
 * Corners: foot of the pole outside, its top, the flag's far top, the notch, the flag's far
 * bottom, the flag's foot at the pole, the foot of the pole inside.
 */
fun flagGlyph(radius: Float): FloatArray {
    val size = radius * 2f * GLYPH_SHARE
    val left = -size / 2f
    val right = size / 2f
    val top = -size / 2f
    val bottom = size / 2f
    val pole = size * 0.18f
    val cloth = size * 0.6f
    val notch = size * 0.22f
    return floatArrayOf(
        left, bottom,
        left, top,
        right, top,
        right - notch, top + cloth / 2f,
        right, top + cloth,
        left + pole, top + cloth,
        left + pole, bottom,
    )
}

/**
 * The colour (ARGB) of the glyph on a badge filled with [fill]: [dark] on a light fill (the
 * lime), [light] on a dark one, so the flag always stands out.
 */
fun glyphInkArgb(fill: Int, dark: Int, light: Int): Int {
    val r = (fill shr 16 and 0xFF) / 255f
    val g = (fill shr 8 and 0xFF) / 255f
    val b = (fill and 0xFF) / 255f
    return if (0.2126f * r + 0.7152f * g + 0.0722f * b > 0.5f) dark else light
}

/**
 * The colour of the destination flag (ARGB): the first of [candidates] (the accent of the
 * app's colours first, then its second accent) that neither looks like the own position's
 * dot [ownDot] (a blue dot on the map is the traveller) nor is red (red on the map reads as
 * a disruption); [fallback] (the map's ink) when none is.
 */
fun destinationFlagArgb(candidates: IntArray, ownDot: Int, fallback: Int): Int {
    for (colour in candidates) {
        if (!reddish(colour) && !looksAlike(colour, ownDot)) return colour
    }
    return fallback
}

/** True for a colour that reads as red: a clear hue near red, neither near black nor near white. */
internal fun reddish(argb: Int): Boolean {
    val hsl = hslOf(argb)
    return hsl[1] >= 0.35f && hsl[2] in 0.15f..0.85f && (hsl[0] < 20f || hsl[0] > 340f)
}

/**
 * True where [a] and [b] would be taken for one another on the map: two clear colours of a
 * near hue (within 40 degrees), or two greys of a near lightness.
 */
internal fun looksAlike(a: Int, b: Int): Boolean {
    val x = hslOf(a)
    val y = hslOf(b)
    val greyX = x[1] < 0.2f || x[2] < 0.1f || x[2] > 0.92f
    val greyY = y[1] < 0.2f || y[2] < 0.1f || y[2] > 0.92f
    if (greyX || greyY) return greyX && greyY && abs(x[2] - y[2]) < 0.15f
    val apart = abs(x[0] - y[0]).let { min(it, 360f - it) }
    return apart < 40f
}

/** Hue (0 to 360), saturation and lightness (0 to 1) of [argb]. */
internal fun hslOf(argb: Int): FloatArray {
    val r = (argb shr 16 and 0xFF) / 255f
    val g = (argb shr 8 and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val high = max(r, max(g, b))
    val low = min(r, min(g, b))
    val lightness = (high + low) / 2f
    val span = high - low
    if (span == 0f) return floatArrayOf(0f, 0f, lightness)
    val saturation = span / (1f - abs(2f * lightness - 1f))
    val hue = when (high) {
        r -> 60f * (((g - b) / span) % 6f)
        g -> 60f * ((b - r) / span + 2f)
        else -> 60f * ((r - g) / span + 4f)
    }.let { if (it < 0f) it + 360f else it }
    return floatArrayOf(hue, saturation, lightness)
}
