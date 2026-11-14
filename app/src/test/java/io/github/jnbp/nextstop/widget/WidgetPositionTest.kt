package io.github.jnbp.nextstop.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetPositionTest {
    private val minute = 60_000L
    private val now = 1_800_000_000_000L

    @Test
    fun newerPositionWins() {
        val old = TimedPosition(52.5, 13.4, now - 20 * minute)
        val fresh = TimedPosition(52.49, 13.35, now - minute)
        assertSame(fresh, newerPosition(old, fresh))
        assertSame(fresh, newerPosition(fresh, old))
        assertSame(old, newerPosition(old, null))
        assertSame(fresh, newerPosition(null, fresh))
        assertNull(newerPosition(null, null))
    }

    @Test
    fun positionWithUnknownTimeLosesAgainstTimedOne() {
        val unknown = TimedPosition(52.5, 13.4, 0L)
        val timed = TimedPosition(52.49, 13.35, now - 30 * minute)
        assertSame(timed, newerPosition(unknown, timed))
    }

    @Test
    fun freshPositionNeedsNoRequest() {
        assertTrue(needsFreshPosition(null, now))
        assertTrue(needsFreshPosition(TimedPosition(52.5, 13.4, 0L), now))
        assertTrue(needsFreshPosition(TimedPosition(52.5, 13.4, now - 3 * minute), now))
        assertFalse(needsFreshPosition(TimedPosition(52.5, 13.4, now - minute), now))
    }

    @Test
    fun ageIsOnlyMentionedWhenOld() {
        assertNull(positionAgeMinutes(null, now))
        assertNull(positionAgeMinutes(0L, now))
        assertNull(positionAgeMinutes(now - 9 * minute, now))
        assertEquals(10L, positionAgeMinutes(now - 10 * minute, now))
        assertEquals(125L, positionAgeMinutes(now - 125 * minute - 30_000L, now))
    }
}
