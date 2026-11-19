package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.LineColors
import io.github.jnbp.nextstop.ui.theme.AppColors
import io.github.jnbp.nextstop.ui.theme.Appearance
import io.github.jnbp.nextstop.ui.theme.MARK_CONTRAST
import io.github.jnbp.nextstop.ui.theme.contrastRatio
import io.github.jnbp.nextstop.ui.theme.readableArgb

/*
 * Casings (outlines) under lines and badges on the map only where they are really needed: a
 * line colour that stands out from the ground of the map even a little is drawn bare, one that
 * melts into it (a pale yellow on a light map, a dark blue on a dark one) gets a thin edge in its own
 * colour, darkened on a light map and lightened on a dark one, never a white or black ring.
 * The standard colours of the BVG and DB lines are drawn bare on the light map in any case
 * (#83): they are made to be seen on light paper, the U4's yellow included.
 * Pure logic on ARGB ints, worked out once per colour and ground and kept (see LineCasings).
 */

/** Below this contrast to the ground of the map a line or badge gets a casing (#83: was 2). */
const val LINE_CASING_CONTRAST = 1.5f

// The grounds of the map styles (see mapStyleUrl): the light and colourful ones are close
// enough to share one, the dark one has its own.
const val LIGHT_MAP_GROUND = 0xFFF2F3F5.toInt()
const val DARK_MAP_GROUND = 0xFF1B1E24.toInt()

/** The ground of the map, light or [darkMap]. */
fun mapGroundArgb(darkMap: Boolean): Int = if (darkMap) DARK_MAP_GROUND else LIGHT_MAP_GROUND

/**
 * True when a line of the colour [line] melts into the map's [ground] and needs a casing: its
 * contrast to the ground is below [LINE_CASING_CONTRAST], and on the light map it is not one of
 * the standard line colours ([isStandardLineColor]).
 */
fun lineNeedsCasing(line: Int, ground: Int): Boolean {
    if ((ground or OPAQUE) == LIGHT_MAP_GROUND && isStandardLineColor(line)) return false
    return contrastRatio(line or OPAQUE, ground or OPAQUE) < LINE_CASING_CONTRAST
}

/** True for a standard colour of the BVG and DB lines (S- and U-Bahn, tram, bus, trains), alpha ignored. */
fun isStandardLineColor(line: Int): Boolean = (line or OPAQUE) in STANDARD_LINE_COLORS

// The network map of Berlin (S- and U-Bahn) and the house colours of the other BVG and DB lines.
private val STANDARD_LINE_COLORS: Set<Int> = LineColors.BERLIN_RAPID.values.mapTo(HashSet()) { it or 0xFF000000.toInt() } +
    setOf(
        0xFFBE1414.toInt(), // BVG tram
        0xFFCC0A22.toInt(), // BVG tram (MetroTram)
        0xFFA5027D.toInt(), // BVG bus
        0xFF95276E.toInt(), // BVG bus (MetroBus)
        0xFF528DBA.toInt(), // BVG ferry
        0xFFEC0016.toInt(), // DB red (regional)
        0xFFE30613.toInt(), // DB regional
        0xFF646973.toInt(), // DB long-distance grey
        0xFF008D4F.toInt(), // S-Bahn green
    )

/**
 * The casing of a line of the colour [line] on the map's [ground]: its own colour moved towards
 * near black (light ground) or white (dark ground) until it stands out as a mark does, opaque;
 * 0 where the line needs none.
 */
fun lineCasingArgb(line: Int, ground: Int): Int =
    if (!lineNeedsCasing(line, ground)) 0 else readableArgb(ground or OPAQUE, line or OPAQUE, MARK_CONTRAST) or OPAQUE

private const val OPAQUE = 0xFF000000.toInt()

/** The casings worked out so far, by colour and ground: a handful of line colours per map. */
object LineCasings {
    private val known = HashMap<Long, Int>()

    /** The casing of [line] on [ground] (see [lineCasingArgb]), worked out the first time only. */
    fun of(line: Int, ground: Int): Int = synchronized(known) {
        val key = (line.toLong() shl 32) or (ground.toLong() and 0xFFFFFFFFL)
        known.getOrPut(key) { lineCasingArgb(line, ground) }
    }
}

/** The ground of the map as it is shown now (its style follows the setting and the app's look). */
fun currentMapGround(): Int = mapGroundArgb(isDarkMapStyle(mapStyleUrl(Appearance.mapStyle, AppColors.palette.dark)))
