package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.Departure
import io.github.jnbp.nextstop.data.OsmLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rules that keep big stations readable on the map. */
class MapDeclutterTest {
    // About 1 m in degrees of latitude.
    private val meter = 1.0 / 111_320.0

    @Test
    fun zoomsByKindAndMode() {
        assertEquals(16.0, detailMinZoom(DetailKind.Entrance, marked = false)!!, 0.0)
        assertEquals(17.0, detailMinZoom(DetailKind.Lift, marked = false)!!, 0.0)
        assertEquals(18.0, detailMinZoom(DetailKind.Escalator, marked = false)!!, 0.0)
        // The marked station a step earlier.
        assertEquals(SELECTED_POSITION_ZOOM, detailMinZoom(DetailKind.Entrance, marked = true)!!, 0.0)
        assertEquals(17.0, detailMinZoom(DetailKind.Escalator, marked = true)!!, 0.0)
        // Always: everything from 16.
        assertEquals(16.0, detailMinZoom(DetailKind.Escalator, marked = false, mode = EntranceMode.Always)!!, 0.0)
        assertEquals(16.0, detailMinZoom(DetailKind.Lift, marked = true, mode = EntranceMode.Always)!!, 0.0)
        // Only the marked station.
        assertNull(detailMinZoom(DetailKind.Entrance, marked = false, mode = EntranceMode.MarkedOnly))
        assertEquals(16.5, detailMinZoom(DetailKind.Lift, marked = true, mode = EntranceMode.MarkedOnly)!!, 0.0)
    }

    @Test
    fun modesReadBackFromTheirKeys() {
        EntranceMode.entries.forEach { assertEquals(it, EntranceMode.of(it.key)) }
        assertEquals(EntranceMode.Auto, EntranceMode.of(null))
        assertEquals(EntranceMode.Auto, EntranceMode.of("nonsense"))
    }

    @Test
    fun namedEntrancesWinOverLiftsOverEscalators() {
        val named = detailSortKey(DetailKind.Entrance, named = true)
        val plain = detailSortKey(DetailKind.Entrance, named = false)
        val lift = detailSortKey(DetailKind.Lift, named = false)
        val escalator = detailSortKey(DetailKind.Escalator, named = false)
        assertTrue(named < plain && plain < lift && lift < escalator)
    }

    @Test
    fun nearIdenticalSymbolsMergeIntoOne() {
        val symbols = listOf(
            DetailSymbol(DetailKind.Lift, "lift", 52.5250, 13.3690),
            DetailSymbol(DetailKind.Lift, "lift", 52.5250 + 3 * meter, 13.3690),
            DetailSymbol(DetailKind.Lift, "lift", 52.5250 + 7 * meter, 13.3690),
            // Far enough away: stays.
            DetailSymbol(DetailKind.Lift, "lift", 52.5250 + 30 * meter, 13.3690),
            // Another sign at the same spot: stays.
            DetailSymbol(DetailKind.Escalator, "escalator", 52.5250, 13.3690),
        )
        assertEquals(listOf(0, 3, 4), mergeNearSymbols(symbols))
    }

    @Test
    fun mergeKeepsTheNamedEntrance() {
        val symbols = listOf(
            DetailSymbol(DetailKind.Entrance, "S", 53.5530, 10.0060),
            DetailSymbol(DetailKind.Entrance, "S", 53.5530 + 2 * meter, 10.0060, named = true),
        )
        assertEquals(listOf(1), mergeNearSymbols(symbols))
    }

    @Test
    fun mergeLooksEastAndWestToo() {
        // 6 m east at the latitude of Hamburg: about 0.000091 degrees of longitude.
        val symbols = listOf(
            DetailSymbol(DetailKind.Lift, "lift", 53.5530, 10.0060),
            DetailSymbol(DetailKind.Lift, "lift", 53.5530, 10.0060 + 0.000091),
            DetailSymbol(DetailKind.Lift, "lift", 53.5530, 10.0060 + 0.0003),
        )
        assertEquals(listOf(0, 2), mergeNearSymbols(symbols))
    }

    @Test
    fun overlappingPillsGiveWayToTheMoreImportant() {
        val pills = listOf(
            PillBox(52.5250, 13.3690, widthDp = 100f, heightDp = 30f, priority = 2),
            // 10 m east: at zoom 16 about 14 dp, the pills overlap.
            PillBox(52.5250, 13.3690 + 10 * meter / 0.608, widthDp = 100f, heightDp = 30f, priority = 6),
            // 200 m north: clear of both.
            PillBox(52.5250 + 200 * meter, 13.3690, widthDp = 100f, heightDp = 30f, priority = 1),
        )
        val shown = declutterPills(pills, zoom = 16.0)
        assertEquals(setOf(1, 2), shown)
        // Close enough in, they all fit.
        assertEquals(setOf(0, 1, 2), declutterPills(pills, zoom = 21.0))
    }

    @Test
    fun singlePillAlwaysWhole() {
        assertEquals(setOf(0), declutterPills(listOf(PillBox(52.5, 13.4, 80f, 30f, 0)), zoom = 10.0))
    }

    @Test
    fun metresPerDpHalvesWithEachZoom() {
        val at16 = metersPerDp(16.0, 52.5)
        assertEquals(at16 / 2, metersPerDp(17.0, 52.5), 1e-9)
        assertTrue(at16 > 0.5 && at16 < 1.0)
    }

    private fun departure(line: String, product: String, platform: String?) = Departure(
        line = line,
        product = product,
        direction = "X",
        time = null,
        plannedTime = null,
        delaySeconds = null,
        platform = platform,
        cancelled = false,
    )

    @Test
    fun positionDeparturesByPlatformNumber() {
        val all = listOf(
            departure("120", "bus", "3"),
            departure("147", "bus", "Pos. 4"),
            departure("S5", "suburban", "3"),
            departure("N5", "bus", null),
        )
        val lines = listOf(OsmLine("120", "bus"), OsmLine("N5", "bus"))
        val here = positionDepartures(all, "3", lines)
        // The bus at "3", the night bus without a platform by its line; never the S-Bahn on track 3.
        assertEquals(listOf("120", "N5"), here.map { it.line })
    }

    @Test
    fun positionDeparturesByLineWithoutNumbers() {
        val all = listOf(departure("M10", "tram", null), departure("M8", "tram", null))
        assertEquals(listOf("M10"), positionDepartures(all, null, listOf(OsmLine("M10", "tram"))).map { it.line })
        assertFalse(positionDepartures(all, null, emptyList()).isNotEmpty())
    }
}
