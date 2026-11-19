package io.github.jnbp.nextstop.ui.companion

import io.github.jnbp.nextstop.data.PickerMode
import io.github.jnbp.nextstop.data.StopRowSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The plan of the calm companion's whole-journey timeline. Example journey (made up, Bonn/Köln
 * style): walk 4 min to the stop, S-Bahn, walk 6 min, bus 184, walk 3 min to the destination.
 */
class CompanionRowsTest {
    // walk, S, walk, bus, walk
    private val walking = listOf(true, false, true, false, true)
    private val minutes = listOf(4L, 20L, 6L, 12L, 3L)
    private val changes = setOf(2) // the change after the S-Bahn keeps 2 legs

    @Test
    fun wholeJourneyInOrderWithChangeBetweenRides() {
        val rows = journeyRows(walking, minutes, changes, focus = 1, onBoard = false)
        assertEquals(
            listOf(
                JourneyRow.Walk(0, endsTrip = false, over = false),
                JourneyRow.Ride(1, JourneyRidePhase.Next),
                JourneyRow.Change(1, 3, walkIndex = 2, over = false),
                JourneyRow.Ride(3, JourneyRidePhase.Later),
                JourneyRow.Walk(4, endsTrip = true, over = false),
            ),
            rows,
        )
    }

    @Test
    fun onBoardTheFirstWalkIsOverAndLaterRidesStayWithStops() {
        val rows = journeyRows(walking, minutes, changes, focus = 1, onBoard = true)
        assertEquals(JourneyRow.Walk(0, endsTrip = false, over = true), rows[0])
        assertEquals(JourneyRow.Ride(1, JourneyRidePhase.Riding), rows[1])
        assertEquals(JourneyRow.Ride(3, JourneyRidePhase.Later), rows[3])
    }

    @Test
    fun onTheSecondRideTheChangeIsOverAndTheFirstRideDone() {
        val rows = journeyRows(walking, minutes, changes, focus = 3, onBoard = true)
        assertEquals(JourneyRow.Ride(1, JourneyRidePhase.Done), rows[1])
        assertEquals(JourneyRow.Change(1, 3, walkIndex = 2, over = true), rows[2])
        assertEquals(JourneyRow.Ride(3, JourneyRidePhase.Riding), rows[3])
    }

    @Test
    fun threeRidesGiveTwoChangesAndSamePlatformHasNoWalk() {
        // S, S (same platform), walk, tram
        val legs = listOf(false, false, true, false)
        val rows = journeyRows(legs, listOf(10L, 8L, 5L, 9L), setOf(1, 2), focus = 0, onBoard = true)
        val found = rows.filterIsInstance<JourneyRow.Change>()
        assertEquals(listOf(JourneyRow.Change(0, 1, null, false), JourneyRow.Change(1, 3, 2, false)), found)
        assertEquals(listOf("ride0", "change1", "ride1", "change2", "ride3"), rows.map { it.key })
    }

    @Test
    fun zeroMinuteWalksAndUnknownChangesAreLeftOut() {
        val rows = journeyRows(walking, listOf(0L, 20L, 6L, 12L, 0L), emptySet(), focus = 1, onBoard = false)
        assertEquals(listOf("ride1", "ride3"), rows.map { it.key })
    }

    @Test
    fun everyRideOverWithoutFocus() {
        val rows = journeyRows(walking, minutes, changes, focus = null, onBoard = false)
        assertTrue(rows.filterIsInstance<JourneyRow.Ride>().all { it.phase == JourneyRidePhase.Done })
        assertTrue(rows.filterIsInstance<JourneyRow.Change>().all { it.over })
    }

    @Test
    fun walkOnlyJourney() {
        val rows = journeyRows(listOf(true), listOf(12L), emptySet(), focus = null, onBoard = false)
        assertEquals(listOf(JourneyRow.Walk(0, endsTrip = true, over = false)), rows)
    }

    @Test
    fun laterRideFoldsToFirstAndLast() {
        val rows = journeyStopRows(8, -1f, JourneyRidePhase.Later, expanded = false)
        assertEquals(listOf(StopRowSpec.Single(0), StopRowSpec.Folded(1, 6), StopRowSpec.Single(7)), rows)
        assertEquals(8, journeyStopRows(8, -1f, JourneyRidePhase.Later, expanded = true).size)
    }

    @Test
    fun shortLaterRideIsNotFolded() {
        val rows = journeyStopRows(3, -1f, JourneyRidePhase.Later, expanded = false)
        assertEquals(listOf(StopRowSpec.Single(0), StopRowSpec.Single(1), StopRowSpec.Single(2)), rows)
    }

    @Test
    fun doneRideShowsNoStopsUntilUnfolded() {
        assertTrue(journeyStopRows(6, 5f, JourneyRidePhase.Done, expanded = false).isEmpty())
        assertEquals(6, journeyStopRows(6, 5f, JourneyRidePhase.Done, expanded = true).size)
    }

    @Test
    fun ridingShowsTheBoardingStopAndTheStopsPassed() {
        // #187: from the boarding stop on, not from the last stop passed.
        val rows = journeyStopRows(10, 3.4f, JourneyRidePhase.Riding, expanded = false)
        assertEquals(StopRowSpec.Single(0), rows.first())
        assertEquals((0..3).map { StopRowSpec.Single(it) }, rows.take(4))
        assertEquals(StopRowSpec.Single(9), rows.last())
        assertTrue(rows.any { it is StopRowSpec.Folded })
    }

    @Test
    fun pickersAreFoldedUntilOneIsOpenedAndOnlyOneIsOpen() {
        // Folded at first, also at the change the departures are loaded for (#94).
        assertEquals(PickerShow.Link, pickerShow(2, over = false, active = 2, open = null))
        // Opened by hand: that one shows its chips, every other one is a link.
        assertEquals(PickerShow.Open, pickerShow(2, over = false, active = 2, open = 2))
        assertEquals(PickerShow.Link, pickerShow(4, over = false, active = 2, open = 2))
        // Opened, but its departures are not the ones loaded (yet): a link.
        assertEquals(PickerShow.Link, pickerShow(4, over = false, active = 2, open = 4))
        assertEquals(PickerShow.None, pickerShow(2, over = true, active = 2, open = 2))
        assertEquals(PickerShow.Link, pickerShow(2, over = false, active = null, open = null))
    }

    @Test
    fun plannedTimeBesideALateOne() {
        val planned = java.time.OffsetDateTime.parse("2026-11-18T15:20:00+01:00")
        assertEquals(planned, plannedShown(planned.plusMinutes(3), planned))
        assertEquals(null, plannedShown(planned.plusSeconds(40), planned))
        assertEquals(null, plannedShown(planned, null))
        assertEquals(null, plannedShown(null, planned))
    }

    @Test
    fun planBOffersOnlyWhatFits() {
        // #116: on board with a change ahead: no "Zug verpasst".
        assertEquals(listOf(PlanB.MissedChange, PlanB.Late, PlanB.WrongTrain), planBItems(missedRelevant = false, changeAhead = true, canReplan = true))
        // Waiting for the first train of a trip without a change.
        assertEquals(listOf(PlanB.MissedTrain, PlanB.Late, PlanB.WrongTrain), planBItems(missedRelevant = true, changeAhead = false, canReplan = true))
        assertEquals(emptyList<PlanB>(), planBItems(missedRelevant = false, changeAhead = false, canReplan = false))
        assertTrue(endAsksFirst(arrived = false))
        assertFalse(endAsksFirst(arrived = true))
    }

    @Test
    fun platformsAtTheFirstAndTheLastStop() {
        // #153: "Berlin Hbf · Gleis 16 · 15:23" at the stop to get off, the departure platform where one boards.
        assertEquals("Gleis 4", stopRowPlatform(0, 5, "Gleis 4", "Gleis 16"))
        assertEquals("Gleis 16", stopRowPlatform(5, 5, "Gleis 4", "Gleis 16"))
        assertEquals(null, stopRowPlatform(2, 5, "Gleis 4", "Gleis 16"))
        assertEquals(null, stopRowPlatform(5, 5, "Gleis 4", null))
    }

    @Test
    fun boardingHintsAtEveryRideNotOver() {
        // #127: the detail of the connection shows where to board at every ride; so does the companion now.
        assertTrue(rideHintShown(JourneyRidePhase.Next))
        assertTrue(rideHintShown(JourneyRidePhase.Riding))
        assertTrue(rideHintShown(JourneyRidePhase.Later))
        assertFalse(rideHintShown(JourneyRidePhase.Done))
    }

    @Test
    fun theSettingOpensTheNextPickerOrAll() {
        // #138: "Nur der nächste offen": the one with the departures opens by itself, until folded by hand.
        assertEquals(PickerShow.Open, pickerShow(2, over = false, active = 2, open = null, mode = PickerMode.NextOpen))
        assertEquals(PickerShow.Link, pickerShow(2, over = false, active = 2, open = null, mode = PickerMode.NextOpen, folded = 2))
        assertEquals(PickerShow.Link, pickerShow(4, over = false, active = 2, open = null, mode = PickerMode.NextOpen))
        // Opened again by hand after folding: open.
        assertEquals(PickerShow.Open, pickerShow(2, over = false, active = 2, open = 2, mode = PickerMode.NextOpen, folded = 2))
        // "Alle offen": open wherever the departures are, not foldable; without them a link that loads them.
        assertEquals(PickerShow.Open, pickerShow(2, over = false, active = 2, open = null, mode = PickerMode.AllOpen))
        // "Alle ausgeklappt" only starts open: folded by hand it is the link again (r7plan).
        assertEquals(PickerShow.Link, pickerShow(2, over = false, active = 2, open = null, mode = PickerMode.AllOpen, folded = 2))
        assertEquals(PickerShow.Link, pickerShow(4, over = false, active = 2, open = null, mode = PickerMode.AllOpen))
        // Past changes never show one, whatever the setting.
        assertEquals(PickerShow.None, pickerShow(2, over = true, active = 2, open = null, mode = PickerMode.AllOpen))
        // Nothing chosen yet: only the next one open (#203).
        assertEquals(PickerMode.NextOpen, PickerMode.fromStored(null))
        assertEquals(PickerMode.Folded, PickerMode.fromStored("Folded"))
        assertEquals(PickerMode.NextOpen, PickerMode.fromStored("NextOpen"))
        // An unknown stored name reads as the default: the active change opens by itself.
        assertEquals(PickerShow.Open, pickerShow(2, over = false, active = 2, open = null, mode = PickerMode.fromStored("x")))
    }

    @Test
    fun theConnectionTakenIsAlwaysAmongTheChoices() {
        // Missing among the loaded ones: first, as the chosen one (#94).
        assertEquals(listOf("FEX", "S7", "RE3"), withCurrentChoice(listOf("S7", "RE3"), "FEX") { it == "FEX" })
        // Already there: as it is.
        assertEquals(listOf("S1", "FEX"), withCurrentChoice(listOf("S1", "FEX"), "FEX") { it == "FEX" })
        // Nothing to stand for it: as it is.
        assertEquals(listOf("S1"), withCurrentChoice(listOf("S1"), null) { it == "FEX" })
    }

    @Test
    fun noShortcutRightAfterChoosingUntilSomethingChanged() {
        val minute = 60_000L
        val own = ShortcutOwn(100 * minute, broken = false)
        // The first offer, nothing changed: quiet (#75).
        assertFalse(shortcutOfferAllowed(2 * minute, own, own, 50 * minute, 50 * minute))
        // #120: live times that move a little (refresh noise, a minute of delay) are no change.
        assertFalse(shortcutOfferAllowed(2 * minute, own, own.copy(arrivalMillis = 101 * minute + 59_000L), 50 * minute, 50 * minute))
        assertFalse(shortcutOfferAllowed(2 * minute, own, own, 50 * minute, 48 * minute))
        // The own journey arrives 2 minutes later: offered.
        assertTrue(shortcutOfferAllowed(2 * minute, own, own.copy(arrivalMillis = 102 * minute), 50 * minute, 50 * minute))
        // A change of it broke (or a ride is cancelled): offered.
        assertTrue(shortcutOfferAllowed(2 * minute, own, own.copy(broken = true), 50 * minute, 50 * minute))
        // The offered way got 3 minutes faster since it was first seen: offered.
        assertTrue(shortcutOfferAllowed(2 * minute, own, own, 50 * minute, 47 * minute))
        // Never quiet for longer than the limit.
        assertTrue(shortcutOfferAllowed(SHORTCUT_QUIET_MAX_MILLIS, own, own, 50 * minute, 50 * minute))
    }

    @Test
    fun chosenChangeWinsWhileAhead() {
        assertEquals(4, activeChangeKeepLegs(4, listOf(2, 4)))
        assertEquals(2, activeChangeKeepLegs(null, listOf(2, 4)))
        // Picked by hand, but already passed: back to the next one.
        assertEquals(4, activeChangeKeepLegs(2, listOf(4)))
        assertEquals(null, activeChangeKeepLegs(2, emptyList()))
    }

    @Test
    fun aChangeThatNoLongerWorksGetsTheChoice() {
        // #98: a delay breaks the change at 2 while the traveller looks at the one at 4: the
        // connections are loaded where something has to be chosen anew.
        assertEquals(2, activeChangeKeepLegs(4, listOf(2, 4), broken = 2))
        assertEquals(2, activeChangeKeepLegs(null, listOf(2, 4), broken = 2))
        // A broken change no longer ahead counts for nothing.
        assertEquals(4, activeChangeKeepLegs(4, listOf(4), broken = 2))
    }

    @Test
    fun missedOnlyBeforeBoardingAndNearTheDeparture() {
        assertTrue(missedRelevant(onBoard = false, minutesToDeparture = 5))
        assertTrue(missedRelevant(onBoard = false, minutesToDeparture = -3))
        assertFalse(missedRelevant(onBoard = false, minutesToDeparture = 45))
        assertFalse(missedRelevant(onBoard = true, minutesToDeparture = -3))
        assertFalse(missedRelevant(onBoard = false, minutesToDeparture = null))
    }

    @Test
    fun sublineJoinsWhatIsThere() {
        assertEquals("Vorne einsteigen · ↘ Gl. 2 · 6 min", heroSubline(listOf("Vorne einsteigen", null, " ", "↘ Gl. 2 · 6 min")))
        assertEquals("", heroSubline(listOf(null, "")))
    }

    @Test
    fun noMapWhileTheCalmCompanionIsAlone() {
        assertFalse(mapComposed(calmAlone = true))
        assertTrue(mapComposed(calmAlone = false))
    }

    @Test
    fun theGuardIsQuietForTheFirstOfferAndSharedByCardAndNotification() {
        val guard = ShortcutQuiet()
        val minute = 60_000L
        val own = ShortcutOwn(100 * minute, false)
        // The companion asks first: the offer is quiet (#75).
        assertFalse(guard.allows("j1", own, 90 * minute, 0L))
        // #120: the notification asks a minute later with its own reading of the journey (seconds apart): still quiet.
        assertFalse(guard.allows("j1", own.copy(arrivalMillis = 100 * minute + 40_000L), 90 * minute, 60_000L))
        // The own journey got 3 minutes later: allowed, and stays allowed for this journey.
        assertTrue(guard.allows("j1", own.copy(arrivalMillis = 103 * minute), 90 * minute, 90_000L))
        assertTrue(guard.allows("j1", own, 90 * minute, 100_000L))
        // A newly chosen connection starts quiet again.
        assertFalse(guard.allows("j2", own, 80 * minute, 110_000L))
        // The simulation of the developer options goes through, without opening the guard.
        assertTrue(guard.allows("j2", own, 80 * minute, 115_000L, bypass = true))
        assertFalse(guard.allows("j2", own, 80 * minute, 116_000L))
        // No offer: nothing to show.
        assertFalse(guard.allows("j2", own, null, 120_000L))
    }

    @Test
    fun theWayToThePlatformStartsAtTheOwnPositionWhenRecentAndNear() {
        val station = 52.0 to 13.0
        val near = 52.001 to 13.0 // about 111 m
        val far = 52.01 to 13.0 // about 1.1 km
        assertEquals(near, platformWalkStart(station, near, 10_000L))
        assertEquals(station, platformWalkStart(station, far, 10_000L))
        assertEquals(station, platformWalkStart(station, near, OWN_FIX_MAX_AGE_MILLIS + 1))
        assertEquals(station, platformWalkStart(station, null, 0L))
    }

    @Test
    fun aChangeMissedByADelayStaysAheadWhileItMatters() {
        val now = java.time.OffsetDateTime.parse("2026-11-18T15:34:30+01:00")
        // #171: still on the late train, the connection left at 15:34: the change is still to be
        // made (another connection), so its picker opens and its departures load.
        assertTrue(changeAhead(nextDeparture = now.minusSeconds(30), arrivingArrival = now.plusMinutes(1), possible = false, now = now))
        // Off the train, the connection gone without the traveller: still to choose anew there.
        assertTrue(changeAhead(nextDeparture = now.minusMinutes(5), arrivingArrival = now.minusMinutes(2), possible = false, now = now))
        // Made: the connecting ride left with the traveller.
        assertFalse(changeAhead(nextDeparture = now.minusMinutes(1), arrivingArrival = now.minusMinutes(6), possible = true, now = now))
        // Long gone: no longer.
        assertFalse(changeAhead(nextDeparture = now.minusMinutes(40), arrivingArrival = now.minusMinutes(35), possible = false, now = now))
        // Ahead as before.
        assertTrue(changeAhead(nextDeparture = now.plusMinutes(5), arrivingArrival = now.plusMinutes(1), possible = true, now = now))
    }

    @Test
    fun aPickerOpenedByHandGetsTheDeparturesEvenBesideABrokenChange() {
        // #171: tapped at the change at 4 while the one at 2 is broken: the tap wins.
        assertEquals(4, activeChangeKeepLegs(4, listOf(2, 4), broken = 2, opened = 4))
        assertEquals(PickerShow.Open, pickerShow(4, over = false, active = activeChangeKeepLegs(4, listOf(2, 4), 2, 4), open = 4))
        // Folded again: the broken one has the departures.
        assertEquals(2, activeChangeKeepLegs(4, listOf(2, 4), broken = 2, opened = null))
    }
}
