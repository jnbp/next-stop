package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The companion views: the setting, where the traveller is among the stops, and the folded stops. */
class CompanionLayoutTest {
    private val delta = 0.001f

    @Test
    fun theSettingDefaultsToAutomaticAndKeepsTheEarlierChoices() {
        assertEquals(CompanionLayoutMode.Auto, CompanionLayoutMode.fromStored(null))
        assertEquals(CompanionLayoutMode.Auto, CompanionLayoutMode.fromStored("Nonsense"))
        // The names stored by the earlier three modes keep their view.
        assertEquals(CompanionLayoutMode.Auto, CompanionLayoutMode.fromStored("Auto"))
        assertEquals(CompanionLayoutMode.AlwaysMap, CompanionLayoutMode.fromStored("AlwaysMap"))
        assertEquals(CompanionLayoutMode.Classic, CompanionLayoutMode.fromStored("Classic"))
        assertEquals(CompanionLayoutMode.NoMap, CompanionLayoutMode.fromStored("NoMap"))
        // Offered in this order: Automatisch, Ohne Karte, Mit Karte, Klassisch.
        assertEquals(
            listOf(CompanionLayoutMode.Auto, CompanionLayoutMode.NoMap, CompanionLayoutMode.AlwaysMap, CompanionLayoutMode.Classic),
            CompanionLayoutMode.entries,
        )
    }

    @Test
    fun theMapButtonIsKeptInAutomaticAndForOneLegWithoutMap() {
        // "Automatisch": the stored choice counts, for every later companion too (#65).
        assertEquals(true, companionMapChoice(CompanionLayoutMode.Auto, saved = true, leg = null))
        assertEquals(null, companionMapChoice(CompanionLayoutMode.Auto, saved = null, leg = true))
        // "Ohne Karte": only the choice of this leg.
        assertEquals(true, companionMapChoice(CompanionLayoutMode.NoMap, saved = false, leg = true))
        assertEquals(null, companionMapChoice(CompanionLayoutMode.NoMap, saved = true, leg = null))
        // The other views have no button.
        assertEquals(null, companionMapChoice(CompanionLayoutMode.AlwaysMap, saved = false, leg = false))
        // A stored "off" keeps the walk calm in "Automatisch"; a stored "on" shows the map on board.
        assertFalse(companionShowsMap(CompanionLayoutMode.Auto, RideStage.Walking, companionMapChoice(CompanionLayoutMode.Auto, false, null)))
        assertTrue(companionShowsMap(CompanionLayoutMode.Auto, RideStage.OnBoard, companionMapChoice(CompanionLayoutMode.Auto, true, null)))
    }

    @Test
    fun automaticShowsTheMapOnFootAndIsCalmOnBoard() {
        assertTrue(autoShowsMap(RideStage.Walking, null))
        assertTrue(autoShowsMap(RideStage.Before, null))
        assertFalse(autoShowsMap(RideStage.OnBoard, null))
        assertFalse(autoShowsMap(RideStage.Alighting, null))
        assertFalse(autoShowsMap(RideStage.Waiting, null))
        assertFalse(autoShowsMap(RideStage.Arrived, null))
        assertFalse(autoShowsMap(null, null))
        // The map button wins on its leg.
        assertFalse(autoShowsMap(RideStage.Walking, userChoiceThisLeg = false))
        assertTrue(autoShowsMap(RideStage.OnBoard, userChoiceThisLeg = true))
    }

    @Test
    fun aNewLegEndsTheChoiceOfTheButton() {
        val walking = autoLegKey("j", RideStage.Walking, 2)
        assertEquals(walking, autoLegKey("j", RideStage.Before, 2))
        // Waiting at the stop of the same ride is a new leg for the map: calm again.
        assertTrue(walking != autoLegKey("j", RideStage.Waiting, 2))
        assertEquals(autoLegKey("j", RideStage.Waiting, 2), autoLegKey("j", RideStage.OnBoard, 2))
        assertTrue(autoLegKey("j", RideStage.OnBoard, 2) != autoLegKey("j", RideStage.OnBoard, 4))
        assertTrue(autoLegKey("j", RideStage.OnBoard, 2) != autoLegKey("k", RideStage.OnBoard, 2))
    }

    @Test
    fun eachViewShowsTheMapAsItSays() {
        assertTrue(companionShowsMap(CompanionLayoutMode.Auto, RideStage.Walking, null))
        assertFalse(companionShowsMap(CompanionLayoutMode.Auto, RideStage.OnBoard, null))
        assertFalse(companionShowsMap(CompanionLayoutMode.NoMap, RideStage.Walking, null))
        assertTrue(companionShowsMap(CompanionLayoutMode.NoMap, RideStage.OnBoard, legChoice = true))
        assertTrue(companionShowsMap(CompanionLayoutMode.AlwaysMap, RideStage.OnBoard, legChoice = false))
        assertTrue(companionShowsMap(CompanionLayoutMode.Classic, null, null))
    }

    @Test
    fun theBatterySaverKeepsTheCompanionWithoutMap() {
        CompanionLayoutMode.entries.forEach { mode ->
            assertEquals(CompanionLayoutMode.NoMap, effectiveCompanionLayout(mode, saving = true))
            assertEquals(mode, effectiveCompanionLayout(mode, saving = false))
        }
        assertFalse(companionShowsMap(effectiveCompanionLayout(CompanionLayoutMode.AlwaysMap, saving = true), RideStage.Walking, null))
    }

    @Test
    fun onlyTheClassicViewKeepsTheTabBar() {
        assertTrue(companionHidesTabs(CompanionLayoutMode.Auto))
        assertTrue(companionHidesTabs(CompanionLayoutMode.NoMap))
        assertTrue(companionHidesTabs(CompanionLayoutMode.AlwaysMap))
        assertFalse(companionHidesTabs(CompanionLayoutMode.Classic))
    }

    @Test
    fun positionByTimeRunsBetweenTheStops() {
        val times = listOf(0L, 60_000L, 120_000L, 240_000L)
        assertEquals(0f, stopPosition(times, -5_000L), delta)
        assertEquals(0.5f, stopPosition(times, 30_000L), delta)
        assertEquals(2.5f, stopPosition(times, 180_000L), delta)
        assertEquals(3f, stopPosition(times, 999_000L), delta)
    }

    @Test
    fun stopsWithoutTimeAreSpreadBetweenTheirNeighbours() {
        val times = listOf(0L, null, 120_000L)
        assertEquals(1f, stopPosition(times, 60_000L), delta)
        assertEquals(0f, stopPosition(listOf(null, null), 10L), delta)
    }

    @Test
    fun thePositionOfTheDeviceWinsOverTheTimes() {
        // The timetable says the bus is past stop 3; the device is still before stop 2.
        assertEquals(2f - 0.05f, fixedStopPosition(3.4f, nextStop = 2, lastIndex = 6), delta)
        // The timetable lags behind: the device is already past stop 4.
        assertEquals(4f, fixedStopPosition(1.2f, nextStop = 5, lastIndex = 6), delta)
        // Without a next stop the times stand.
        assertEquals(1.2f, fixedStopPosition(1.2f, nextStop = -1, lastIndex = 6), delta)
    }

    @Test
    fun theMiddleStopsFoldIntoOneRow() {
        val rows = stopRows(count = 10, position = 2.4f, expanded = false)
        assertEquals(
            listOf(
                // #187: the boarding stop and the stops passed stay above the traveller.
                StopRowSpec.Single(0),
                StopRowSpec.Single(1),
                StopRowSpec.Single(2),
                StopRowSpec.Single(3),
                StopRowSpec.Single(4),
                StopRowSpec.Folded(5, 7),
                StopRowSpec.Single(8),
                StopRowSpec.Single(9),
            ),
            rows,
        )
    }

    @Test
    fun oneStopAloneIsNeverFoldedAndShortRidesShowAll() {
        assertEquals((0..5).map { StopRowSpec.Single(it) }, stopRows(count = 6, position = -1f, expanded = false))
        assertEquals((0..3).map { StopRowSpec.Single(it) }, stopRows(count = 4, position = 0.2f, expanded = false))
    }

    @Test
    fun unfoldedShowsEveryStop() {
        assertEquals((0..9).map { StopRowSpec.Single(it) }, stopRows(count = 10, position = 3.7f, expanded = true))
    }

    @Test
    fun nearTheEndTheStopsPassedFoldAndTheDestinationStays() {
        val rows = stopRows(count = 10, position = 8.5f, expanded = false)
        assertEquals(
            listOf(StopRowSpec.Single(0), StopRowSpec.Folded(1, 7, passed = true), StopRowSpec.Single(8), StopRowSpec.Single(9)),
            rows,
        )
    }

    // #187: the card showed only the last stop passed above the own dot, as if the ride had
    // begun there. The boarding stop always stays, every stop passed with it while few.
    @Test
    fun theBoardingStopAlwaysStaysAboveTheTraveller() {
        val few = stopRows(count = 8, position = 2.5f, expanded = false)
        assertEquals(listOf(0, 1, 2), few.take(3).map { (it as StopRowSpec.Single).index })
        // Four passed (0..3): all of them still single.
        val four = stopRows(count = 9, position = 3.2f, expanded = false)
        assertEquals((0..3).map { StopRowSpec.Single(it) }, four.take(4))
        // Five passed (0..4): boarding, "3 Halte gefahren", the last one passed.
        val many = stopRows(count = 12, position = 4.6f, expanded = false)
        assertEquals(listOf(StopRowSpec.Single(0), StopRowSpec.Folded(1, 3, passed = true), StopRowSpec.Single(4)), many.take(3))
        // The fold ahead is not one of the stops passed.
        assertTrue(many.filterIsInstance<StopRowSpec.Folded>().any { !it.passed })
        // The traveller's dot lies between the last stop passed and the next one.
        val dot = rowPosition(many, 4.6f)
        assertTrue(dot > 2f && dot < 3f)
    }

    @Test
    fun theDotMovesSmoothlyOverTheRowsAndThroughAFold() {
        val rows = stopRows(count = 10, position = 2.4f, expanded = false)
        assertEquals(2.4f, rowPosition(rows, 2.4f), delta)
        assertEquals(4f, rowPosition(rows, 4f), delta)
        // Entering the fold, and inside it, the dot never jumps back.
        val samples = generateSequence(2f) { it + 0.1f }.takeWhile { it <= 9f }.map { rowPosition(rows, it) }.toList()
        samples.zipWithNext().forEach { (a, b) -> assertTrue("$a then $b", b >= a - delta) }
        assertEquals(rows.lastIndex.toFloat(), rowPosition(rows, 9f), delta)
    }

    @Test
    fun aChangeIsSafeTightOrLate() {
        assertEquals(ChangeReach.Safe, changeReach(ChangeTime(13, 4, Tightness.Comfortable, 2)))
        assertEquals(ChangeReach.Safe, changeReach(ChangeTime(5, 4, Tightness.Tight, 2)))
        assertEquals(ChangeReach.Tight, changeReach(ChangeTime(3, 4, Tightness.Risky, 2)))
        assertEquals(ChangeReach.Late, changeReach(ChangeTime(1, 4, Tightness.VeryRisky, 2)))
    }
}
