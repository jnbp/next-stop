package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** #148: the counter shows the clock time from an hour on, without flipping at the boundary. */
class HeroCountTest {
    @Test
    fun clockFromAnHourOn() {
        assertTrue(heroShowsClock(179, showingClock = false))
        assertTrue(heroShowsClock(60, showingClock = false))
        assertFalse(heroShowsClock(59, showingClock = false))
        assertFalse(heroShowsClock(45, showingClock = false))
    }

    @Test
    fun noFlickerAtTheBoundary() {
        // Shown as a clock at 60: a live time one minute earlier keeps the clock ...
        assertTrue(heroShowsClock(59, showingClock = true))
        // ... and only a second minute turns it into the countdown.
        assertFalse(heroShowsClock(58, showingClock = true))
        // Counting down at 59: one minute later in the live times does not bring the clock back yet at 59.
        assertFalse(heroShowsClock(59, showingClock = false))
    }

    @Test
    fun arrivalOrDeparture() {
        assertFalse(heroClockIsArrival(StepKind.Board))
        assertTrue(heroClockIsArrival(StepKind.GetOff))
        assertTrue(heroClockIsArrival(StepKind.WalkToEnd))
    }

    // #182: "3′" stood right of the pill's centre: the digit at the end of a two-digit slot and the prime after it.
    private fun middleOfDigits(p: HeroCountPlacement, numberWidth: Int) = p.number + numberWidth / 2.0

    @Test
    fun oneDigitSitsInTheMiddleWithThePrimeHangingRight() {
        val p = heroCountPlacement(leadWidth = 0, numberWidth = 18, unitWidth = 10, slotWidth = 36)
        assertEquals(p.width / 2.0, middleOfDigits(p, 18), 0.5)
        // The prime right after the digit, not after the empty half of the slot.
        assertEquals(p.number + 18, p.unit)
        // The width of two digits stays, so the pill does not jump from 10′ to 9′.
        assertEquals(heroCountPlacement(0, 36, 10, 36).width, p.width)
    }

    @Test
    fun twoAndThreeDigitsAreCentredToo() {
        for (digits in listOf(36, 54)) {
            val p = heroCountPlacement(0, digits, 10, 36)
            assertEquals(p.width / 2.0, middleOfDigits(p, digits), 0.5)
            assertEquals(p.number + digits, p.unit)
        }
    }

    @Test
    fun anEstimateKeepsTheDigitsInTheMiddle() {
        // "ca." on the left is wider than the prime on the right: both sides take its width.
        val p = heroCountPlacement(leadWidth = 24, numberWidth = 18, unitWidth = 10, slotWidth = 36)
        assertEquals(p.width / 2.0, middleOfDigits(p, 18), 0.5)
        assertEquals(p.number - 24, p.lead)
        assertTrue(p.lead >= 0)
        assertTrue(p.unit + 10 <= p.width)
    }
}
