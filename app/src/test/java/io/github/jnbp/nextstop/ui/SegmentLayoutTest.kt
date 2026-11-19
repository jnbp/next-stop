package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Choices in the settings never scroll sideways: they wrap into a grid or a list (see segmentLayout). */
class SegmentLayoutTest {
    @Test
    fun labelsThatFitStandSideBySide() {
        assertEquals(SegmentLayout.Row, segmentLayout(listOf(40, 80, 50), available = 300, padding = 14, gap = 3, evenly = false))
    }

    @Test
    fun fourLabelsThatDoNotFitMakeAGrid() {
        // "Automatisch", "Ohne Karte", "Mit Karte", "Klassisch" on a narrow row.
        assertEquals(SegmentLayout.Grid, segmentLayout(listOf(90, 80, 70, 70), available = 280, padding = 4, gap = 3, evenly = false))
    }

    @Test
    fun threeLabelsThatDoNotFitMakeAList() {
        assertEquals(SegmentLayout.List, segmentLayout(listOf(60, 120, 60), available = 250, padding = 14, gap = 3, evenly = false))
    }

    @Test
    fun evenPartsCountTheWidestLabel() {
        // Side by side the sum fits (50 + 150 + 2 * 2 * 4 + 3 = 219), as even parts not (2 * 158 + 3 = 319).
        assertEquals(SegmentLayout.Row, segmentLayout(listOf(50, 150), available = 230, padding = 4, gap = 3, evenly = false))
        assertEquals(SegmentLayout.Grid, segmentLayout(listOf(50, 150), available = 230, padding = 4, gap = 3, evenly = true))
    }

    @Test
    fun anUnboundedWidthAndASinglePartAlwaysFit() {
        assertEquals(SegmentLayout.Row, segmentLayout(listOf(900, 900), available = Int.MAX_VALUE, padding = 14, gap = 3, evenly = false))
        assertEquals(SegmentLayout.Row, segmentLayout(listOf(900), available = 10, padding = 14, gap = 3, evenly = false))
    }
}
