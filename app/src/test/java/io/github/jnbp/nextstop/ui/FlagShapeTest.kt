package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class FlagShapeTest {
    // The colours of the Next Stop scheme: its blue (also the own position's dot), its lime, the map's ink.
    private val blue = 0xFF1B4FD8.toInt()
    private val lime = 0xFFC8F16B.toInt()
    private val ink = 0xFF1F1D18.toInt()

    @Test
    fun theFlagNeverTakesTheColourOfTheOwnDot() {
        // On the phone the own dot is drawn in the accent: the flag takes the second accent.
        assertEquals(lime, destinationFlagArgb(intArrayOf(blue, lime), ownDot = blue, fallback = ink))
        // A blue near the own dot's blue counts as the same (the watch: brand blue, light own blue).
        assertEquals(lime, destinationFlagArgb(intArrayOf(blue, lime), ownDot = 0xFF4FC3F7.toInt(), fallback = ink))
    }

    @Test
    fun theAccentIsTakenWhereItIsNotTheOwnDot() {
        val teal = 0xFF0D9488.toInt()
        assertEquals(teal, destinationFlagArgb(intArrayOf(teal, lime), ownDot = blue, fallback = ink))
    }

    @Test
    fun theFlagIsNeverRed() {
        val red = 0xFFDC2626.toInt()
        val rose = 0xFFE11D48.toInt()
        assertTrue(reddish(red))
        assertTrue(reddish(rose))
        assertFalse(reddish(lime))
        assertFalse(reddish(0xFFEA580C.toInt())) // orange is no disruption red
        assertEquals(lime, destinationFlagArgb(intArrayOf(red, lime), ownDot = blue, fallback = ink))
        // Neither fits: the map's ink.
        assertEquals(ink, destinationFlagArgb(intArrayOf(rose, blue), ownDot = blue, fallback = ink))
    }

    @Test
    fun greysOnlyMatchGreysOfALikeLightness() {
        assertFalse(looksAlike(0xFF808080.toInt(), blue))
        assertTrue(looksAlike(0xFF808080.toInt(), 0xFF8A8A8A.toInt()))
        assertFalse(looksAlike(0xFF202020.toInt(), 0xFFD0D0D0.toInt()))
    }

    @Test
    fun theFlagGlyphStaysInsideTheBadge() {
        for (radius in floatArrayOf(DESTINATION_BADGE_RADIUS_DP, 8f, 7.15f, 39f)) {
            val glyph = flagGlyph(radius)
            assertEquals(14, glyph.size)
            // Every corner well inside the circle, clear of its outline.
            val inner = radius - badgeOutlineDp(radius)
            for (i in glyph.indices step 2) {
                assertTrue(sqrt(glyph[i] * glyph[i] + glyph[i + 1] * glyph[i + 1]) < inner)
            }
            // About 45 percent of the diameter, high and wide, centred on the badge.
            val xs = glyph.filterIndexed { i, _ -> i % 2 == 0 }
            val ys = glyph.filterIndexed { i, _ -> i % 2 == 1 }
            assertEquals(radius * 0.9f, ys.max() - ys.min(), 1e-3f)
            assertEquals(radius * 0.9f, xs.max() - xs.min(), 1e-3f)
            assertEquals(0f, (xs.max() + xs.min()) / 2f, 1e-3f)
            assertEquals(0f, (ys.max() + ys.min()) / 2f, 1e-3f)
        }
    }

    @Test
    fun theFlagHasAPoleOnTheLeftAndItsClothOnTop() {
        val glyph = flagGlyph(DESTINATION_BADGE_RADIUS_DP)
        // The pole runs from the foot to the top on the left edge.
        assertEquals(glyph[0], glyph[2], 0f)
        assertTrue(glyph[1] > glyph[3])
        // The cloth hangs from the top and ends above the foot: no pole sticks out of the badge.
        assertTrue(glyph[9] < glyph[1])
        // The notch cuts into the far edge.
        assertTrue(glyph[6] < glyph[4] && glyph[6] < glyph[8])
    }

    @Test
    fun theBadgeIsRoundAndItsOutlineVisible() {
        assertEquals(13f, DESTINATION_BADGE_RADIUS_DP, 0f)
        assertEquals(2.5f, badgeOutlineDp(DESTINATION_BADGE_RADIUS_DP), 1e-4f)
        // Smaller badges get a thinner outline, but never thinner than 1.5 dp.
        assertEquals(1.5f, badgeOutlineDp(4f), 1e-4f)
        // The room no name goes over: the circle with the outer half of its outline.
        assertEquals(14.25f, badgeReachDp(DESTINATION_BADGE_RADIUS_DP), 1e-4f)
    }

    @Test
    fun theGlyphStandsOutOnTheFill() {
        val dark = 0xFF1F1D18.toInt()
        val light = 0xFFECECEC.toInt()
        assertEquals(dark, glyphInkArgb(lime, dark, light))
        assertEquals(light, glyphInkArgb(blue, dark, light))
        assertEquals(light, glyphInkArgb(ink, dark, light))
        assertEquals(dark, glyphInkArgb(light, dark, light))
    }

    @Test
    fun theBadgeIsLimeFirst() {
        // The lime goes first; the accent only where the lime looks like the own dot.
        assertEquals(lime, destinationFlagArgb(intArrayOf(lime, blue), ownDot = blue, fallback = ink))
        assertEquals(blue, destinationFlagArgb(intArrayOf(lime, blue), ownDot = 0xFFB5E61D.toInt(), fallback = ink))
    }

    @Test
    fun onlyTheLastWalkEndsAtTheDestination() {
        assertTrue(walkEndsAtDestination(index = 2, legCount = 3, walking = true))
        // The walk to the first stop, a walk between rides: no flag.
        assertFalse(walkEndsAtDestination(index = 0, legCount = 3, walking = true))
        assertFalse(walkEndsAtDestination(index = 1, legCount = 3, walking = true))
        // The last leg is a ride: no walk ends there.
        assertFalse(walkEndsAtDestination(index = 2, legCount = 3, walking = false))
        // A journey on foot only.
        assertTrue(walkEndsAtDestination(index = 0, legCount = 1, walking = true))
        assertFalse(walkEndsAtDestination(index = 0, legCount = 0, walking = true))
    }

    // The relative luminance of an ARGB colour (WCAG), and the contrast of two.
    private fun luminance(argb: Int): Double {
        fun channel(c: Int): Double = (c / 255.0).let { if (it <= 0.03928) it / 12.92 else Math.pow((it + 0.055) / 1.055, 2.4) }
        return 0.2126 * channel(argb shr 16 and 0xFF) + 0.7152 * channel(argb shr 8 and 0xFF) + 0.0722 * channel(argb and 0xFF)
    }

    private fun contrast(a: Int, b: Int): Double {
        val x = luminance(a)
        val y = luminance(b)
        return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05)
    }

    @Test
    fun theOutlineIsDarkOnEveryMapWithAThinLightLineOnTheDarkOne() {
        // A near black ink, no light ring: the outline reads as an edge, not as a target.
        assertTrue(luminance(BADGE_INK_ARGB) < 0.03)
        assertTrue(contrast(lime, BADGE_INK_ARGB) >= 7.0)
        // The flag on the lime is drawn in the same dark ink.
        assertEquals(BADGE_INK_ARGB, glyphInkArgb(lime, BADGE_INK_ARGB, 0xFFECECEC.toInt()))
        // On the dark map only a line of at most 1 dp outside; none on the light map.
        assertEquals(0f, badgeHaloDp(darkMap = false), 0f)
        assertTrue(badgeHaloDp(darkMap = true) in 0.5f..1f)
        assertTrue(luminance(BADGE_DARK_HALO_ARGB) > 0.5)
    }

    @Test
    fun aMarkOnTheBadgeIsLeftOut() {
        val reach = 14f
        // The route's end dot on the badge's centre, and one just past its edge within the margin.
        assertTrue(coversBadge(0f, 0f, reach, 3f))
        assertTrue(coversBadge(10f, 10f, reach, 3f))
        assertTrue(coversBadge(16.9f, 0f, reach, 3f))
        // Clear of it: drawn.
        assertFalse(coversBadge(17.1f, 0f, reach, 3f))
        assertFalse(coversBadge(0f, -40f, reach, 3f))
    }

    @Test
    fun aSignNearTheBadgeMovesOutToItsFreeSide() {
        val out = FloatArray(2)
        // An exit sign on the badge's upper edge goes straight up, out of the badge.
        assertTrue(clearOfBadge(100f, 90f, 100f, 100f, 25f, out))
        assertEquals(100f, out[0], 1e-3f)
        assertEquals(75f, out[1], 1e-3f)
        // To the lower right it moves on along its own direction.
        assertTrue(clearOfBadge(106f, 108f, 100f, 100f, 25f, out))
        assertEquals(115f, out[0], 1e-3f)
        assertEquals(120f, out[1], 1e-3f)
        // On the very centre: straight up.
        assertTrue(clearOfBadge(100f, 100f, 100f, 100f, 25f, out))
        assertEquals(100f, out[0], 1e-3f)
        assertEquals(75f, out[1], 1e-3f)
        // Far enough already: it stays.
        assertFalse(clearOfBadge(100f, 60f, 100f, 100f, 25f, out))
        assertEquals(60f, out[1], 0f)
    }

    @Test
    fun aSignClearOfTheBadgeDoesNotTouchIt() {
        for (radius in floatArrayOf(DESTINATION_BADGE_RADIUS_DP, MINI_BADGE_RADIUS_DP)) {
            val clear = badgeSignClearDp(radius, 9.5f)
            assertTrue(clear >= radius * 1.5f)
            assertTrue(clear - 9.5f > badgeReachDp(radius) + BADGE_DARK_HALO_DP)
        }
        assertTrue(BADGE_CLEAR_MARGIN_DP in 1f..6f)
    }
}
