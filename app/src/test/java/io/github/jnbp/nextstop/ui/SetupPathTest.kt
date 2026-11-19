package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.wear.WatchState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupPathTest {

    @Test
    fun quickPathIsWelcomeViewBasicsDone() {
        assertEquals(
            listOf(SetupStep.Welcome, SetupStep.QuickView, SetupStep.QuickBasics, SetupStep.Done),
            setupSteps(quick = true),
        )
    }

    @Test
    fun bothPathsStartWithWelcomeAndEndWithDone() {
        for (quick in listOf(true, false)) {
            val steps = setupSteps(quick)
            assertEquals(SetupStep.Welcome, steps.first())
            assertEquals(SetupStep.Done, steps.last())
        }
    }

    @Test
    fun fullPathHasNoQuickPagesAndEveryStepOnce() {
        val steps = setupSteps(quick = false)
        assertFalse(SetupStep.QuickView in steps)
        assertFalse(SetupStep.QuickBasics in steps)
        assertEquals(steps.size, steps.toSet().size)
        assertEquals(SetupStep.entries.size - 2, steps.size)
    }

    @Test
    fun fullPathAlwaysAsksAboutTheWatchRightBeforeDone() {
        val steps = setupSteps(quick = false)
        assertEquals(steps.indexOf(SetupStep.Done) - 1, steps.indexOf(SetupStep.Watch))
    }

    @Test
    fun fullPathKeepsTheCompanionViewAndPermissions() {
        val steps = setupSteps(quick = false)
        assertTrue(SetupStep.CompanionView in steps)
        assertTrue(steps.indexOf(SetupStep.Permissions) < steps.indexOf(SetupStep.Watch))
        assertEquals(SetupStep.Connections, steps[1])
    }

    @Test
    fun fullPathAsksForThePlacesBeforeTheDisruptionCheck() {
        // The places of step "Orte" (#229) are what the disruption check looks at.
        val steps = setupSteps(quick = false)
        assertTrue(SetupStep.Places in steps)
        assertTrue(steps.indexOf(SetupStep.Places) < steps.indexOf(SetupStep.DisruptionCheck))
        assertFalse(SetupStep.Places in setupSteps(quick = true))
    }

    @Test
    fun theShortcutAlertIsOneStepThatAlsoExplainsEveryMinuteCounts() {
        // #260: "Jede Minute zählt" is part of the step of the shortcut alert, no step of its own.
        val steps = setupSteps(quick = false)
        assertTrue(SetupStep.ShortcutAlert in steps)
        assertFalse(SetupStep.entries.any { it.name == "EveryMinute" })
        // The shortcut alert comes after the notifications it speaks through, and the quick path keeps neither.
        assertTrue(steps.indexOf(SetupStep.Notifications) < steps.indexOf(SetupStep.ShortcutAlert))
        assertFalse(SetupStep.ShortcutAlert in setupSteps(quick = true))
    }

    @Test
    fun watchPageFollowsTheWatchState() {
        assertEquals(SetupWatchKind.Checking, setupWatchKind(null))
        assertEquals(SetupWatchKind.None, setupWatchKind(WatchState.None))
        assertEquals(SetupWatchKind.Paired, setupWatchKind(WatchState.NoApp))
        assertEquals(SetupWatchKind.Ready, setupWatchKind(WatchState.Connected))
    }

    @Test
    fun previewTapStepsThroughAChoiceAndRoundAgain() {
        assertEquals(1, cycleIndex(0, 3))
        assertEquals(2, cycleIndex(1, 3))
        assertEquals(0, cycleIndex(2, 3))
        assertEquals(1, cycleIndex(0, 2))
        assertEquals(0, cycleIndex(1, 2))
    }

    @Test
    fun previewTapFromAnOwnValueStartsWithTheFirst() {
        assertEquals(0, cycleIndex(-1, 3))
        assertEquals(0, cycleIndex(7, 3))
        assertEquals(0, cycleIndex(0, 0))
    }
}
