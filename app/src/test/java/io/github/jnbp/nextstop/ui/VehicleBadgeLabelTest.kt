package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** A replacement bus's badge is short and stands in its row beside the stop like the others. */
class VehicleBadgeLabelTest {
    @Test
    fun replacementBadgeIsShort() {
        assertEquals("SEV S7", replacementBadgeText("S7A"))
        assertEquals("SEV S7", replacementBadgeText("S7"))
        assertEquals("SEV RE1", replacementBadgeText("RE 1"))
        assertEquals("SEV", replacementBadgeText(null))
        assertEquals("SEV S7", vehicleBadgeLabel("SEV S7A", "bus"))
        assertEquals("SEV S7", vehicleBadgeLabel("S7", "bus"))
        assertEquals("M10", vehicleBadgeLabel("M10", "tram"))
        assertEquals("S7", vehicleBadgeLabel("S7", "suburban"))
    }

    @Test
    fun replacementBadgeStandsLeftAlignedBesideTheStop() {
        // A bus that runs under the name of the train line it replaces: its badge reads "SEV S7".
        // The row is laid out by the text that is drawn, so its left edge meets those of the others.
        val signHalfWidth = 9f
        val members = listOf(
            GroupMember("a", 3, vehicleBadgeLabel("S7", "bus")),
            GroupMember("b", 3, vehicleBadgeLabel("200", "bus")),
        )
        val layout = layoutGroup(signHalfWidth, members)
        val drawn = mapOf("a" to "SEV S7", "b" to "200")
        val edges = drawn.map { (key, text) -> layout.slots.getValue(key).dx - badgeHalfWidthDp(text) }
        edges.forEach { assertEquals((signHalfWidth + GROUP_SIGN_GAP_DP).toDouble(), it.toDouble(), 0.001) }
    }
}
