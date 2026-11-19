package io.github.jnbp.nextstop.wear.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** #183: the alert to change goes once the change is done or the train has left. */
class AlertLifetimeTest {
    private val key = "s7-run|Example Change"
    private val approaching = TripSnapshot(active = true, kind = StepKind.GetOff, place = "Example Change", alert = true, alertKey = key, change = true)

    @Test
    fun staysWhileGettingOffThatRide() {
        assertFalse(alertOutlived(key, approaching))
        // Standing at the end of it, about to get off: still the same ride.
        assertFalse(alertOutlived(key, approaching.copy(alert = false)))
    }

    @Test
    fun goesOnceTheChangeIsDone() {
        // Off the train, waiting for the next ride: the step is boarding now.
        assertTrue(alertOutlived(key, approaching.copy(kind = StepKind.Board, alertKey = "s1-run|Example End", alert = false)))
        // On the next ride already.
        assertTrue(alertOutlived(key, approaching.copy(alertKey = "s1-run|Example End", alert = false)))
    }

    @Test
    fun goesWhenTheTripEnds() {
        assertTrue(alertOutlived(key, TripSnapshot.NONE))
        assertTrue(alertOutlived(key, approaching.copy(active = false)))
    }

    @Test
    fun nothingShownNothingToDo() {
        assertFalse(alertOutlived(null, TripSnapshot.NONE))
        assertFalse(alertOutlived("", approaching))
    }
}
