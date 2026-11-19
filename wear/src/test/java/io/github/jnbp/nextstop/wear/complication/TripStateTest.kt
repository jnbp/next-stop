package io.github.jnbp.nextstop.wear.complication

import io.github.jnbp.nextstop.wear.data.StepKind
import io.github.jnbp.nextstop.wear.data.TripSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

/** The state of a trip a complication shows by its icon, and that the icon never stands beside the line's title. */
class TripStateTest {
    @Test
    fun boardingIsWaitingSettingOffIsWalkingAndBetweenRidesIsChanging() {
        assertEquals(TripState.Wait, tripState(StepKind.Board))
        assertEquals(TripState.Walk, tripState(StepKind.Board, leave = true))
        assertEquals(TripState.Cycle, tripState(StepKind.Board, leave = true, cycling = true))
        assertEquals(TripState.Change, tripState(StepKind.Board, afterRide = true))
        // Seen at the stop of the next ride: the change is done, the wait begins.
        assertEquals(TripState.Wait, tripState(StepKind.Board, afterRide = true, waiting = true))
    }

    @Test
    fun ridingTurnsIntoGettingOffOrChangingBeforeTheLastStop() {
        assertEquals(TripState.Ride, tripState(StepKind.GetOff, stopsLeft = 4))
        assertEquals(TripState.Ride, tripState(StepKind.GetOff))
        assertEquals(TripState.GetOff, tripState(StepKind.GetOff, stopsLeft = 1))
        assertEquals(TripState.GetOff, tripState(StepKind.GetOff, stopsLeft = 0))
        assertEquals(TripState.GetOff, tripState(StepKind.GetOff, stopsLeft = 3, alert = true))
        assertEquals(TripState.Change, tripState(StepKind.GetOff, stopsLeft = 1, change = true))
        assertEquals(TripState.Ride, tripState(StepKind.GetOff, stopsLeft = 5, change = true))
    }

    @Test
    fun theWalkToTheEndAndTheArrival() {
        assertEquals(TripState.Walk, tripState(StepKind.WalkToEnd))
        assertEquals(TripState.Cycle, tripState(StepKind.WalkToEnd, cycling = true))
        assertEquals(TripState.Arrived, tripState(StepKind.Arrived))
    }

    @Test
    fun aSnapshotAfterAnotherRideIsAChange() {
        val board = TripSnapshot(active = true, kind = StepKind.Board, line = "U3", product = "subway")
        assertEquals(TripState.Wait, tripState(board))
        assertEquals(TripState.Change, tripState(board.copy(planIndex = 1)))
        assertEquals(TripState.Wait, tripState(board.copy(planIndex = 1, waiting = true)))
        assertEquals(TripState.Walk, tripState(board.copy(leave = true)))
        assertEquals(TripState.GetOff, tripState(board.copy(kind = StepKind.GetOff, stopsLeft = 1)))
    }

    @Test
    fun ridingShowsTheVehicle() {
        assertEquals(TripGlyph.Bus, tripGlyph(TripState.Ride, "bus"))
        assertEquals(TripGlyph.Tram, tripGlyph(TripState.Ride, "tram"))
        assertEquals(TripGlyph.Train, tripGlyph(TripState.Ride, "subway"))
        assertEquals(TripGlyph.Train, tripGlyph(TripState.Ride, "suburban"))
        assertEquals(TripGlyph.Train, tripGlyph(TripState.Ride, null))
        assertEquals(TripGlyph.Hourglass, tripGlyph(TripState.Wait, "bus"))
        assertEquals(TripGlyph.Exit, tripGlyph(TripState.GetOff, "tram"))
    }

    @Test
    fun theStateIconNeverStandsBesideTheLine() {
        val app = "app"
        for (state in TripState.values()) {
            for (product in listOf("bus", "tram", "subway", null)) {
                val glyph = tripGlyph(state, product)
                val (icon, title) = textSlotExtras<Any>(glyph, "U3", app)
                assertEquals(glyph, icon)
                assertNull(title)
            }
        }
    }

    @Test
    fun byDefaultTheComplicationAndTheOngoingActivityShowTheAppIcon() {
        // #282: off by default, the watch face shows the app's icon only, never a state glyph.
        assertEquals(TripIcons(complication = "app", ongoing = "app"), tripIcons(stateIcons = false, state = "walk", app = "app"))
        assertEquals(TripIcons(complication = "app", ongoing = "app"), tripIcons(stateIcons = false, state = "change", app = "app"))
        // With the option the state goes to the complication; the chip keeps the app's icon (#253: no state twice).
        assertEquals(TripIcons(complication = "walk", ongoing = "app"), tripIcons(stateIcons = true, state = "walk", app = "app"))
        val chosen = tripIcons(true, state = "bus", app = "app")
        assertTrue(chosen.complication != chosen.ongoing)
    }

    @Test
    fun everyComplicationShowsTheAppIconUnlessTheOptionIsOn() {
        for (own in listOf("home", "warning", "refresh", "place", "route", "check", "walk")) {
            assertEquals("app", complicationIcon(stateIcons = false, specific = own, app = "app"))
            assertEquals(own, complicationIcon(stateIcons = true, specific = own, app = "app"))
        }
        // The app's icon stays, and a slide without an icon gets none either way.
        assertEquals("app", complicationIcon(stateIcons = true, specific = "app", app = "app"))
        assertEquals(null, complicationIcon(stateIcons = false, specific = null, app = "app"))
        assertEquals(null, complicationIcon(stateIcons = true, specific = null, app = "app"))
    }
}
