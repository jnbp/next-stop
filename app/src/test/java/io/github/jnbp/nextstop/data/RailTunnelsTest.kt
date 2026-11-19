package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rail tunnels of the hint of the sunny side (#219): only the parts above ground count. */
class RailTunnelsTest {
    private fun millis(text: String): Long = OffsetDateTime.parse(text).toInstant().toEpochMilli()

    // A grid of 0.01 degree cells over 52..53 N, 13..14 E: 100 columns.
    private fun grid(tunnel: List<Int>, open: List<Int>) =
        TunnelGrid(52.0, 13.0, 53.0, 14.0, 0.01, 0.01, 100, tunnel.sorted().toIntArray(), open.sorted().toIntArray())

    // The cells of the row at 52.52 N from 13.20 to 13.50 E (a straight way east of about 20 km).
    private val row = 52
    private fun cells(fromCol: Int, toCol: Int) = (fromCol..toCol).map { row * 100 + it }
    private val east = listOf(52.525 to 13.2, 52.525 to 13.35, 52.525 to 13.5)
    private val start = millis("2024-06-21T10:50:00Z")
    private val end = start + 30 * 60_000L

    @Test
    fun decodesGapsInBase36() {
        assertEquals(listOf(10, 46, 47), decodeTunnelCells("a,10,1").toList())
        assertEquals(emptyList<Int>(), decodeTunnelCells("").toList())
    }

    @Test
    fun parsesTheAsset() {
        val text = """{"license":"ODbL-1.0","box":[52.0,13.0,53.0,14.0],"cellLat":0.01,"cellLon":0.01,"cols":100,"tunnel":"410","open":"411"}"""
        val parsed = parseTunnelGrid(text)!!
        val cell = parsed.cellAt(52.525, 13.205)!!
        assertEquals(52 * 100 + 20, cell)
        assertNull(parseTunnelGrid("""{"box":[1,2]}"""))
    }

    @Test
    fun aCellDecidesAndItsNeighboursBreakTies() {
        val g = grid(tunnel = listOf(5220, 5221, 5222), open = listOf(5222, 5260))
        assertEquals(true, g.undergroundAt(52.525, 13.205))
        assertEquals(false, g.undergroundAt(52.525, 13.605))
        // Both in the cell: the neighbours (two tunnels, one open) decide.
        assertEquals(true, g.undergroundAt(52.525, 13.225))
        assertNull(g.undergroundAt(52.525, 13.905))
        assertNull(g.undergroundAt(51.0, 13.5))
    }

    @Test
    fun aRideFullyUndergroundGetsNoSun() {
        val g = grid(tunnel = cells(15, 55), open = emptyList())
        // Without the tunnels the sun stands on the right (see SunSideTest), in the tunnel: nothing.
        assertEquals(SunSide.Right, sunSide(east, start, end))
        assertNull(sunSide(east, start, end, undergroundTest("subway", g)))
    }

    @Test
    fun aMixedRideCountsOnlyItsOpenPart() {
        // The first half in a tunnel, the second half open: the side of the open half.
        val g = grid(tunnel = cells(15, 34), open = cells(35, 55))
        assertEquals(SunSide.Right, sunSide(east, start, end, undergroundTest("suburban", g)))
        // Back west: the open half comes first, the sun is on the left.
        assertEquals(SunSide.Left, sunSide(east.reversed(), start, end, undergroundTest("suburban", g)))
    }

    @Test
    fun aShortOpenPartGetsNoSun() {
        // Only the last 0.02 degrees (under a tenth of the way) above ground.
        val g = grid(tunnel = cells(15, 47), open = cells(48, 55))
        assertNull(sunSide(east, start, end, undergroundTest("subway", g)))
    }

    @Test
    fun theOpenPartNeedsAQuarterAndThreeMinutes() {
        assertTrue(enoughAboveGround(0.25, 30 * 60_000L))
        assertFalse(enoughAboveGround(0.2, 30 * 60_000L))
        // A quarter of a ride of ten minutes is 2.5 minutes: too short.
        assertFalse(enoughAboveGround(0.25, 10 * 60_000L))
        assertTrue(enoughAboveGround(1.0, 3 * 60_000L))
    }

    @Test
    fun roadVehiclesAndNoGridMeanAllOpen() {
        val g = grid(tunnel = cells(15, 55), open = emptyList())
        assertNull(undergroundTest("bus", g))
        assertNull(undergroundTest("tram", g))
        assertNull(undergroundTest("subway", null))
        assertEquals(SunSide.Right, sunSide(east, start, end, undergroundTest("bus", g)))
    }

    @Test
    fun noSunHintOnRailsInGermanyUntilTheTunnelsAreRead() {
        // A made-up ride inside the box of the asset (Germany), and one south of it.
        val inBerlin = listOf(52.50 to 13.30, 52.51 to 13.45)
        val outside = listOf(46.50 to 10.00, 46.51 to 10.05)
        assertTrue(sunWaitsForTunnels("subway", inBerlin, tunnelsRead = false))
        assertTrue(sunWaitsForTunnels("suburban", inBerlin, tunnelsRead = false))
        // Read (or failed): the hint as the grid says.
        assertFalse(sunWaitsForTunnels("subway", inBerlin, tunnelsRead = true))
        // Off the rails or outside the box nothing is waited for.
        assertFalse(sunWaitsForTunnels("bus", inBerlin, tunnelsRead = false))
        assertFalse(sunWaitsForTunnels("tram", inBerlin, tunnelsRead = false))
        assertFalse(sunWaitsForTunnels("regional", outside, tunnelsRead = false))
        assertFalse(sunWaitsForTunnels("subway", emptyList(), tunnelsRead = false))
    }

    // A made-up grid over a Germany-sized box (47..55 N, 6..15 E, 0.01 degree cells: 900 columns)
    // in the asset's format. A made-up city tunnel far from Berlin: two tracks at 48.505 and
    // 48.515 N from 9.505 to 9.525 E (cells 135350..135352 and 136250..136252), one open track
    // leaving its portal to the east (cells 135352..135354, the portal cell 135352 in both lists).
    private val germanyText = """{"license":"ODbL-1.0","box":[47.0,6.0,55.0,15.0],"cellLat":0.01,"cellLon":0.01,""" +
        """"cols":900,"tunnel":"2wfq,1,1,oy,1,1","open":"2wfs,1,1"}"""

    @Test
    fun decodesLargeGapsOfTheGermanyGrid() {
        assertEquals(listOf(135350, 135351, 135352, 136250, 136251, 136252), decodeTunnelCells("2wfq,1,1,oy,1,1").toList())
        // A cell near the end of the real Germany grid (about 17 600 rows of 13 000 columns).
        assertEquals(listOf(228_000_000), decodeTunnelCells(228_000_000.toString(36)).toList())
        assertNull(parseTunnelGrid(germanyText.replace("2wfs,1,1", "2wfs,,1")))
        assertNull(parseTunnelGrid(germanyText.replace("\"cols\":900", "\"cols\":900000000")))
    }

    @Test
    fun aCityTunnelOutsideBerlinIsUnderground() {
        val g = parseTunnelGrid(germanyText)!!
        assertEquals(135350, g.cellAt(48.505, 9.505))
        assertEquals(true, g.undergroundAt(48.505, 9.505))
        assertEquals(true, g.undergroundAt(48.515, 9.515))
        assertTrue(undergroundTest("suburban", g)!!(48.505, 9.505))
    }

    @Test
    fun thePortalCellIsDecidedByItsNeighbours() {
        val g = parseTunnelGrid(germanyText)!!
        // In both lists: around it four tunnel cells against two open ones.
        assertEquals(true, g.undergroundAt(48.505, 9.525))
        // Just outside the portal only the open track: above ground.
        assertEquals(false, g.undergroundAt(48.505, 9.535))
        assertEquals(false, g.undergroundAt(48.505, 9.545))
        assertFalse(undergroundTest("regional", g)!!(48.505, 9.545))
    }

    @Test
    fun aPointFarFromAnyTunnelCountsAsOpen() {
        val g = parseTunnelGrid(germanyText)!!
        // Open cells far from a tunnel are not in the asset: nothing known, which counts as open.
        assertNull(g.undergroundAt(53.205, 11.605))
        assertFalse(undergroundTest("regional", g)!!(53.205, 11.605))
        // Outside the box too.
        assertNull(g.undergroundAt(46.5, 10.0))
    }
}
