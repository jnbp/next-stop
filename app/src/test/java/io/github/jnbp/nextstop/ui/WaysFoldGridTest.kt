package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.Disruption
import io.github.jnbp.nextstop.data.NoticeLine
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The folded notices of "My routes" and the place grid with rows of their own height. */
class WaysFoldGridTest {
    private fun notice(id: String, vararg lines: Pair<String, String>) = Disruption(
        id = id,
        summary = "Bauarbeiten",
        text = "Ersatzverkehr zwischen zwei Halten.",
        lines = lines.mapTo(HashSet()) { it.first },
        products = lines.mapTo(HashSet()) { it.second },
        carriers = lines.map { NoticeLine(it.first, it.second) },
    )

    @Test
    fun foldedOnlyWithMoreThanTwo() {
        assertFalse(waysNoticesFoldedByDefault(0))
        assertFalse(waysNoticesFoldedByDefault(2))
        assertTrue(waysNoticesFoldedByDefault(3))
    }

    @Test
    fun oneNoticeHasNoFoldRow() {
        assertFalse(waysNoticesHaveFoldRow(1))
        assertTrue(waysNoticesHaveFoldRow(2))
    }

    @Test
    fun eachLineOnceAndTheRestCounted() {
        val items = listOf(
            notice("a", "U9" to "subway", "S41" to "suburban"),
            notice("b", "U 9" to "subway", "M10" to "tram"),
            notice("c", "RE1" to "regional", "100" to "bus", "X7" to "bus"),
        )
        val (badges, more) = foldedNoticeBadges(items, 4)
        assertEquals(listOf("U9", "S41", "M10", "RE1"), badges.map { it.line })
        assertEquals(2, more)
        assertEquals(0, foldedNoticeBadges(items.take(1), 4).second)
    }

    @Test
    fun rowsStackWithTheirOwnHeights() {
        assertArrayEquals(floatArrayOf(0f, 108f, 324f), rowTopsOf(floatArrayOf(100f, 208f, 96f), 8f), 0.001f)
        assertEquals(0, rowTopsOf(FloatArray(0), 8f).size)
    }

    @Test
    fun dropSlotFollowsRowsOfDifferentHeights() {
        val heights = floatArrayOf(100f, 200f, 100f)
        val tops = rowTopsOf(heights, 8f)
        // No move: stays.
        assertEquals(0, gridDropSlotRows(0, 0f, 0f, 150f, tops, heights, 8f, 6))
        // Across: the other column.
        assertEquals(1, gridDropSlotRows(0, 120f, 0f, 150f, tops, heights, 8f, 6))
        // Down by the tall second row's middle (50 → 208): row 1.
        assertEquals(2, gridDropSlotRows(0, 0f, 158f, 150f, tops, heights, 8f, 6))
        // Not yet past halfway to the middle of row 1: still row 0.
        assertEquals(0, gridDropSlotRows(0, 0f, 60f, 150f, tops, heights, 8f, 6))
        // From the last row up to the first.
        assertEquals(1, gridDropSlotRows(5, 0f, -400f, 150f, tops, heights, 8f, 6))
        // Five tiles: the empty sixth cell is not a place.
        assertEquals(4, gridDropSlotRows(0, 200f, 400f, 150f, tops, heights, 8f, 5))
        assertEquals(0, gridDropSlotRows(0, 50f, 50f, 150f, tops, heights, 8f, 1))
    }

    @Test
    fun foldedBadgesTakeThePlaceOfTheirNotice() {
        val berlin = io.github.jnbp.nextstop.data.Stop("900100003", "S+U Alexanderplatz", 52.5219, 13.4132, null)
        val stuttgart = io.github.jnbp.nextstop.data.Stop("de:08111:6118", "Stuttgart Hbf", 48.784, 9.182, null)
        fun notice(id: String, stop: io.github.jnbp.nextstop.data.Stop?) = io.github.jnbp.nextstop.data.Disruption(
            id, "U9: Kein Halt", "", setOf("U9"), setOf("subway"), stops = listOfNotNull(stop),
        )
        val (badges, more) = foldedNoticeBadgesPlaced(listOf(notice("a", berlin), notice("b", null), notice("c", stuttgart)), 4)
        // Berlin's U9 once (with a place and without one, both coloured in Berlin), Stuttgart's apart.
        assertEquals(2, badges.size)
        assertEquals(0, more)
        assertEquals(berlin.latitude!! to berlin.longitude!!, badges[0].place)
        assertEquals(stuttgart.latitude!! to stuttgart.longitude!!, badges[1].place)
        // The same place as the rows use.
        assertEquals(io.github.jnbp.nextstop.data.NOTICE_COLOUR_PLACE, notice("b", null).let { foldedNoticeBadgesPlaced(listOf(it), 4).first.single().place })
    }
}
