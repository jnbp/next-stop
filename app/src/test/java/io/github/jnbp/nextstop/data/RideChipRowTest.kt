package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The small chips of a compact ride card (#236) and the badge of a change card (#242). */
class RideChipRowTest {
    @Test
    fun chipsSitBesideAHintThatFitsInFull() {
        assertEquals(ChipRowPlacement.Beside, chipRowPlacement(mainWidth = 200, chipsWidth = 80, gap = 8, maxWidth = 300))
        assertEquals(ChipRowPlacement.Beside, chipRowPlacement(mainWidth = 212, chipsWidth = 80, gap = 8, maxWidth = 300))
    }

    @Test
    fun chipsGoBelowRatherThanCutTheHint() {
        // Seen: "Vorne einsteigen – k…" beside the chips.
        assertEquals(ChipRowPlacement.Below, chipRowPlacement(mainWidth = 213, chipsWidth = 80, gap = 8, maxWidth = 300))
        assertEquals(ChipRowPlacement.Below, chipRowPlacement(mainWidth = 500, chipsWidth = 30, gap = 8, maxWidth = 300))
    }

    @Test
    fun withoutAHintOrChipsNothingMovesBelow() {
        assertEquals(ChipRowPlacement.Beside, chipRowPlacement(mainWidth = 0, chipsWidth = 120, gap = 8, maxWidth = 300))
        assertEquals(ChipRowPlacement.Beside, chipRowPlacement(mainWidth = 400, chipsWidth = 0, gap = 8, maxWidth = 300))
    }

    @Test
    fun chipsEndAtTheRightEdge() {
        // Also without a boarding hint: right-aligned, not at the start of the row.
        assertEquals(220, chipsStartX(rowWidth = 300, chipsWidth = 80))
        assertEquals(0, chipsStartX(rowWidth = 60, chipsWidth = 80))
    }

    @Test
    fun onePageKeepsOneFormOfTheSunChip() {
        val page = SunChipPage()
        assertTrue(page.prominent(9))
        // The tenth ride was counted meanwhile: the page still shows the large chip.
        assertTrue(page.prominent(10))
        assertTrue(page.prominent(14))
        val next = SunChipPage()
        assertFalse(next.prominent(10))
        assertFalse(next.prominent(0))
    }

    @Test
    fun aHintThatFitsInFullKeepsTheChipsBesideIt() {
        assertEquals(HintChipArrangement.Row, hintChipArrangement(mainWidth = 200, chipWidths = listOf(40, 34), gap = 6, maxWidth = 300))
    }

    @Test
    fun theHintKeepsAtLeastTwoThirdsBesideChipsSideBySide() {
        // #264: on a 360 px row the hint keeps 240 px; 360 - 6 - 240 = 114 px are left for the chips.
        assertEquals(114, chipRoom(maxWidth = 360, gap = 6, hintShare = HINT_MIN_SHARE))
        // Bicycle and step-free side by side: 30 + 6 + 30 = 66 px fit, the hint wraps in the rest.
        assertEquals(HintChipArrangement.Row, hintChipArrangement(mainWidth = 500, chipWidths = listOf(30, 30), gap = 6, maxWidth = 360))
        // 114 px exactly still fit side by side.
        assertEquals(HintChipArrangement.Row, hintChipArrangement(mainWidth = 500, chipWidths = listOf(54, 54), gap = 6, maxWidth = 360))
    }

    @Test
    fun chipsThatDoNotFitSideBySideStackInTheRightQuarter() {
        // The right quarter of 360 px: 360 - 6 - 270 = 84 px.
        assertEquals(84, chipRoom(maxWidth = 360, gap = 6, hintShare = HINT_STACKED_SHARE))
        // Occupancy (80), bicycle (30), step-free (30) and sun (30): 188 px side by side, too wide; each fits in 84.
        assertEquals(
            HintChipArrangement.Stacked,
            hintChipArrangement(mainWidth = 500, chipWidths = listOf(80, 30, 30, 30), gap = 6, maxWidth = 360),
        )
    }

    @Test
    fun theChipsGoBelowOnlyWhereOneOfThemDoesNotFitInTheRightQuarter() {
        // A large sun chip (100 px) with occupancy: neither side by side in 114 px nor alone in 84 px.
        assertEquals(HintChipArrangement.Below, hintChipArrangement(mainWidth = 500, chipWidths = listOf(80, 100), gap = 6, maxWidth = 360))
        // The large sun chip alone fits beside two thirds of the row.
        assertEquals(HintChipArrangement.Row, hintChipArrangement(mainWidth = 500, chipWidths = listOf(100), gap = 6, maxWidth = 360))
    }

    @Test
    fun theHintNeverSharesTheRowHalfAndHalf() {
        // #264: what #247 allowed (chips taking up to 60 % of the row) now moves the chips.
        val arrangement = hintChipArrangement(mainWidth = 400, chipWidths = listOf(90, 60), gap = 6, maxWidth = 300)
        assertEquals(HintChipArrangement.Below, arrangement)
    }

    @Test
    fun withoutAHintOrChipsNothingMovesOrStacks() {
        assertEquals(HintChipArrangement.Row, hintChipArrangement(mainWidth = 0, chipWidths = listOf(250), gap = 6, maxWidth = 300))
        assertEquals(HintChipArrangement.Row, hintChipArrangement(mainWidth = 900, chipWidths = emptyList(), gap = 6, maxWidth = 300))
        // A sun chip still 0 px wide while it fades in counts not.
        assertEquals(HintChipArrangement.Row, hintChipArrangement(mainWidth = 900, chipWidths = listOf(0, 30), gap = 6, maxWidth = 300))
    }
}
