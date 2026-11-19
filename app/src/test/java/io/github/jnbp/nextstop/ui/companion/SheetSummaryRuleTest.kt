package io.github.jnbp.nextstop.ui.companion

import io.github.jnbp.nextstop.ui.StepKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** #246: the handle line of the sheet over the map is never empty. */
class SheetSummaryRuleTest {
    @Test
    fun aRideAheadShowsTheRide() {
        assertEquals(SheetSummaryKind.Ride, sheetSummaryKind(hasRide = true, kind = StepKind.Board))
        assertEquals(SheetSummaryKind.Ride, sheetSummaryKind(hasRide = true, kind = StepKind.GetOff))
    }

    @Test
    fun theFinalWalkShowsTheWalkNotAnEmptyStrip() {
        // Seen: "2′ Zu Fuß nach …" in the blue box and a bare strip where the sheet was.
        assertEquals(SheetSummaryKind.Step, sheetSummaryKind(hasRide = false, kind = StepKind.WalkToEnd))
    }

    @Test
    fun arrivedShowsTheArrival() {
        assertEquals(SheetSummaryKind.Arrived, sheetSummaryKind(hasRide = false, kind = StepKind.Arrived))
    }

    @Test
    fun everyStepWithoutARideStillShowsSomething() {
        for (kind in StepKind.entries) assertNotEquals(SheetSummaryKind.Ride, sheetSummaryKind(hasRide = false, kind = kind))
    }
}
