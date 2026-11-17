package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Dragging a row of the data sources to a new place: the slot it drops into and who steps aside. */
class ReorderSlotsTest {
    // Four rows of different heights with a gap of 4 between them.
    private val heights = intArrayOf(100, 60, 80, 120)
    private val gap = 4f

    @Test
    fun theDeltaToASlotSumsThePassedRowsAndGaps() {
        assertEquals(0f, slotDelta(1, 1, heights, gap), 0.001f)
        assertEquals(84f + 124f, slotDelta(1, 3, heights, gap), 0.001f)
        assertEquals(-104f, slotDelta(1, 0, heights, gap), 0.001f)
        assertEquals(-(104f + 64f + 84f), slotDelta(3, 0, heights, gap), 0.001f)
    }

    @Test
    fun aRowTakesTheNextSlotOnceItCoversHalfOfIt() {
        // The neighbour below row 1 is 80 high: half of it with its gap is 42.
        assertEquals(1, dropSlot(1, 41f, heights, gap))
        assertEquals(2, dropSlot(1, 42f, heights, gap))
        // Past row 2 (84) and half of row 3 with its gap (62).
        assertEquals(2, dropSlot(1, 145f, heights, gap))
        assertEquals(3, dropSlot(1, 146f, heights, gap))
        // Upwards: half of row 0 with its gap is 52.
        assertEquals(1, dropSlot(1, -51f, heights, gap))
        assertEquals(0, dropSlot(1, -52f, heights, gap))
        assertEquals(1, dropSlot(1, 0f, heights, gap))
    }

    @Test
    fun theSlotStaysInsideTheList() {
        assertEquals(3, dropSlot(1, 10_000f, heights, gap))
        assertEquals(0, dropSlot(1, -10_000f, heights, gap))
        assertEquals(0, dropSlot(0, -50f, heights, gap))
        assertEquals(3, dropSlot(3, 50f, heights, gap))
    }

    @Test
    fun thePassedRowsStepTowardsWhereTheHeldOneCameFrom() {
        // Row 1 (60 high) held over slot 3: rows 2 and 3 move up by its height and gap.
        assertEquals(0f, stepAside(0, 1, 3, heights, gap), 0.001f)
        assertEquals(0f, stepAside(1, 1, 3, heights, gap), 0.001f)
        assertEquals(-64f, stepAside(2, 1, 3, heights, gap), 0.001f)
        assertEquals(-64f, stepAside(3, 1, 3, heights, gap), 0.001f)
        // Row 2 (80 high) held over slot 0: rows 0 and 1 move down.
        assertEquals(84f, stepAside(0, 2, 0, heights, gap), 0.001f)
        assertEquals(84f, stepAside(1, 2, 0, heights, gap), 0.001f)
        assertEquals(0f, stepAside(3, 2, 0, heights, gap), 0.001f)
        // No row held: nobody moves.
        assertEquals(0f, stepAside(2, -1, -1, heights, gap), 0.001f)
    }

    @Test
    fun movingAnItemKeepsTheOthersInOrder() {
        val list = listOf("a", "b", "c", "d")
        assertEquals(listOf("b", "c", "a", "d"), list.movedTo(0, 2))
        assertEquals(listOf("d", "a", "b", "c"), list.movedTo(3, 0))
        assertEquals(list, list.movedTo(1, 1))
    }
}
