package io.github.jnbp.nextstop.wear.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuzzPatternsTest {
    private fun pulses(event: TripBuzz): List<Long> = buzzPattern(event).timings.filterIndexed { index, _ -> index % 2 == 1 }

    @Test
    fun everyPatternIsShortAndWellFormed() {
        TripBuzz.entries.forEach { event ->
            val pattern = buzzPattern(event)
            assertEquals(event.name, pattern.timings.size, pattern.amplitudes.size)
            assertEquals(event.name, 0L, pattern.timings[0])
            assertTrue(event.name, pattern.millis <= 1_000)
            pattern.amplitudes.forEachIndexed { index, amplitude ->
                if (index % 2 == 0) assertEquals(0, amplitude) else assertTrue(amplitude in 1..255)
            }
        }
    }

    @Test
    fun theRhythmsDiffer() {
        assertEquals(2, pulses(TripBuzz.Board).size)
        assertTrue(pulses(TripBuzz.Board).all { it < 150 })
        // One long, then one short.
        val getOff = pulses(TripBuzz.GetOffNext)
        assertEquals(2, getOff.size)
        assertTrue(getOff[0] >= 3 * getOff[1])
        assertEquals(3, pulses(TripBuzz.ChangeNow).size)
        assertTrue(pulses(TripBuzz.ChangeNow).all { it < 150 })
        // Long, short, long.
        val urgent = pulses(TripBuzz.Urgent)
        assertEquals(3, urgent.size)
        assertTrue(urgent[0] > 2 * urgent[1] && urgent[2] > 2 * urgent[1])
        // One soft pulse, softer than all others.
        assertEquals(1, pulses(TripBuzz.Arrival).size)
        val soft = buzzPattern(TripBuzz.Arrival).amplitudes.max()
        TripBuzz.entries.filter { it != TripBuzz.Arrival }.forEach { assertTrue(buzzPattern(it).amplitudes.max() > soft) }
        // No two events share a rhythm.
        assertEquals(TripBuzz.entries.size, TripBuzz.entries.map { buzzPattern(it).timings.toList() }.toSet().size)
    }

    @Test
    fun theAlertBeforeGettingOffTellsAChange() {
        assertEquals(TripBuzz.ChangeNow, alertBuzz(change = true))
        assertEquals(TripBuzz.GetOffNext, alertBuzz(change = false))
    }

    private val boarding = TripSnapshot(active = true, kind = StepKind.Board, line = "U9", place = "Example Square", platform = "1", tripKey = "t1", planIndex = 0)

    @Test
    fun badNewsOfTheSameTripIsUrgent() {
        assertTrue(urgentChange(boarding, boarding.copy(platform = "2")))
        assertTrue(urgentChange(boarding, boarding.copy(missed = true)))
        assertTrue(urgentChange(boarding, boarding.copy(rescue = RescueOffer("k1", "S7", "suburban", 1L, "3", "Sample Street"))))
        val plan = listOf(PlanRide("U9", "subway", null, "Example Square", "Sample Street", 1L, 2L), PlanRide("S7", "suburban", null, "Sample Street", "Test Park", 3L, 4L))
        assertTrue(urgentChange(boarding.copy(plan = plan), boarding.copy(plan = listOf(plan[0], plan[1].copy(cancelled = true)))))
    }

    @Test
    fun ordinaryProgressIsNotUrgent() {
        assertFalse(urgentChange(null, boarding))
        assertFalse(urgentChange(boarding, boarding))
        // The next step at another platform, a platform learnt late, another trip, the end.
        assertFalse(urgentChange(boarding, boarding.copy(kind = StepKind.GetOff, platform = "2")))
        assertFalse(urgentChange(boarding.copy(platform = null), boarding))
        assertFalse(urgentChange(boarding, boarding.copy(tripKey = "t2", platform = "2")))
        assertFalse(urgentChange(boarding, boarding.copy(active = false)))
        // The same rescue again is no news.
        val rescued = boarding.copy(rescue = RescueOffer("k1", "S7", "suburban", 1L, "3", "Sample Street"))
        assertFalse(urgentChange(rescued, rescued))
    }
}
