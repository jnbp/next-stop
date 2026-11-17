package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** The colour of a change by its wait: sprint, knapp, comfortable, neutral or a long wait. */
class WaitToneTest {
    private fun tone(minutes: Long, need: Long, tightness: Tightness = spareTightness(minutes - need)) =
        waitTone(tightness, minutes, need)

    @Test
    fun sprintAndKnappFollowTheTightness() {
        assertEquals(WaitTone.Sprint, tone(4, 6))
        assertEquals(WaitTone.Tight, tone(3, 3))
        // The journey's own rating wins over the minutes for these two.
        assertEquals(WaitTone.Sprint, waitTone(Tightness.VeryRisky, 30, 3))
        assertEquals(WaitTone.Tight, waitTone(Tightness.Risky, 30, 3))
    }

    @Test
    fun aShortChangeWithRoomIsComfortable() {
        // Need 3: from 6 minutes (3 to spare) up to 12.
        assertEquals(WaitTone.Comfortable, tone(6, 3))
        assertEquals(WaitTone.Comfortable, tone(11, 3))
        assertEquals(WaitTone.Comfortable, tone(TONE_COMFORT_MAX_GAP, 3))
        // Same platform: 2 to spare are enough.
        assertEquals(WaitTone.Comfortable, tone(3, 1))
    }

    @Test
    fun aLongWalkMayTakeLongerAndStillBeComfortable() {
        // Need 6: up to need + 8 = 14 minutes.
        assertEquals(WaitTone.Comfortable, tone(12, 6))
        assertEquals(WaitTone.Comfortable, tone(6 + TONE_COMFORT_MAX_OVER_NEED, 6))
        assertEquals(WaitTone.Neutral, tone(6 + TONE_COMFORT_MAX_OVER_NEED + 1, 6))
    }

    @Test
    fun littleRoomOrAGrowingWaitIsNeutral() {
        // 2 to spare but the change needs 3: not knapp, not comfortable either.
        assertEquals(WaitTone.Neutral, tone(5, 3))
        assertEquals(WaitTone.Neutral, tone(TONE_COMFORT_MAX_GAP + 1, 3))
        assertEquals(WaitTone.Neutral, tone(3 + TONE_LONG_WAIT, 3))
    }

    @Test
    fun aLongWaitIsSandNotGreen() {
        assertEquals(WaitTone.LongWait, tone(3 + TONE_LONG_WAIT + 1, 3))
        assertEquals(WaitTone.LongWait, tone(40, 3))
        assertEquals(WaitTone.LongWait, tone(40, 1))
    }

    @Test
    fun theChangeTimeCarriesItsTone() {
        assertEquals(WaitTone.LongWait, ChangeTime(40, 3, Tightness.Comfortable).tone)
        assertEquals(WaitTone.Comfortable, ChangeTime(11, 3, Tightness.Comfortable).tone)
        assertEquals(WaitTone.Sprint, ChangeTime(4, 6, Tightness.VeryRisky).tone)
    }
}
