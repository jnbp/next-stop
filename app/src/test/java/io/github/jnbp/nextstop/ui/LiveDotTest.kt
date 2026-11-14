package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The live dot of vehicles placed by live data: its beat, where it sits on a badge, and when
 * the hint about vehicle positions belongs on the map.
 */
class LiveDotTest {
    @Test
    fun pulseRestartsEveryBeat() {
        assertEquals(0f, livePulsePhase(0), 1e-6f)
        assertEquals(0.5f, livePulsePhase(LIVE_PULSE_MILLIS / 2), 1e-6f)
        assertEquals(0f, livePulsePhase(LIVE_PULSE_MILLIS * 7), 1e-6f)
        assertEquals(livePulsePhase(123), livePulsePhase(123 + LIVE_PULSE_MILLIS * 3), 1e-6f)
    }

    @Test
    fun pulseGrowsAndFades() {
        assertEquals(0f, livePulseReach(0f), 1e-6f)
        assertEquals(LIVE_PULSE_REACH_DP, livePulseReach(1f), 1e-6f)
        assertEquals(LIVE_PULSE_OPACITY, livePulseOpacity(0f), 1e-6f)
        assertEquals(0f, livePulseOpacity(1f), 1e-6f)
        var reach = -1f
        var opacity = 2f
        for (step in 0..20) {
            val phase = step / 20f
            assertTrue(livePulseReach(phase) >= reach)
            assertTrue(livePulseOpacity(phase) <= opacity)
            reach = livePulseReach(phase)
            opacity = livePulseOpacity(phase)
        }
        // Subtle: never wider than the reach the distance field of the map holds.
        assertTrue(livePulseReach(5f) <= LIVE_PULSE_REACH_DP)
    }

    @Test
    fun dotSitsAtTheUpperRightCornerOfTheBadge() {
        val short = liveDotOffset(2)
        val long = liveDotOffset(4)
        assertTrue(short[0] > 0f)
        assertTrue(short[1] < 0f)
        // A longer text, a wider badge: the dot moves right by the width of the characters,
        // never up or down.
        assertEquals((2 * BADGE_CHAR_DP / 2).toFloat(), long[0] - short[0], 1e-4f)
        assertEquals(short[1], long[1], 1e-6f)
        // Linear in the length: the map interpolates between two lengths and lands right.
        val mid = liveDotOffset(3)
        assertEquals((short[0] + long[0]) / 2f, mid[0], 1e-4f)
        assertEquals(liveDotOffset(1)[0], liveDotOffset(0)[0], 1e-6f)
    }

    @Test
    fun badgeImageIdPerColourAndKind() {
        assertEquals("ns-badge-1D9E75", badgeImageId("#1d9e75", live = false))
        assertEquals("ns-badge-1D9E75-live", badgeImageId("#1D9E75", live = true))
        assertTrue(badgeImageId("#FF0000", live = true) != badgeImageId("#00FF00", live = true))
    }

    @Test
    fun dotIsSmallAndReachesJustBeyondTheCorner() {
        // About 30 % smaller than the first dot (3 dp with a 1.5 dp outline), with a thin outline.
        assertTrue(LIVE_DOT_RADIUS_DP + LIVE_DOT_OUTLINE_DP <= 4.5f * 0.75f)
        assertTrue(LIVE_DOT_OUTLINE_DP <= 1f)
        val overhang = liveDotOverhangDp()
        assertTrue(overhang > 0f)
        assertTrue(overhang < LIVE_DOT_RADIUS_DP + LIVE_DOT_OUTLINE_DP)
    }

    @Test
    fun hintOnlyWithVehiclesOnTheMap() {
        assertTrue(vehicleHintWanted(hintOn = true, vehiclesShown = true, vehicleCount = 3))
        assertFalse(vehicleHintWanted(hintOn = false, vehiclesShown = true, vehicleCount = 3))
        assertFalse(vehicleHintWanted(hintOn = true, vehiclesShown = false, vehicleCount = 3))
        assertFalse(vehicleHintWanted(hintOn = true, vehiclesShown = true, vehicleCount = 0))
    }
}
