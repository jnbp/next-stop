package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The age of live data in the freshness indicator (see freshLabelKey). */
class FreshnessTextTest {
    private val stale = 100L

    @Test
    fun firstSecondsAreJustNow() {
        assertEquals(0L, freshLabelKey(0, stale))
        assertEquals(0L, freshLabelKey(4, stale))
        // A clock that went back is not a negative age.
        assertEquals(0L, freshLabelKey(-30, stale))
    }

    @Test
    fun secondsTickUntilAMinute() {
        assertEquals(5L, freshLabelKey(5, stale))
        assertEquals(55L, freshLabelKey(59, stale))
        assertFalse(freshKeyInMinutes(freshLabelKey(59, stale)))
        assertEquals(55L, freshKeyValue(freshLabelKey(59, stale)))
    }

    @Test
    fun minutesChangeOnlyOnceAMinute() {
        assertEquals(freshLabelKey(60, stale), freshLabelKey(99, stale))
        assertTrue(freshKeyInMinutes(freshLabelKey(60, stale)))
        assertEquals(1L, freshKeyValue(freshLabelKey(61, 1_000)))
        assertEquals(3L, freshKeyValue(freshLabelKey(200, 1_000)))
    }

    @Test
    fun oldDataIsStale() {
        val key = freshLabelKey(130, stale)
        assertTrue(freshKeyStale(key))
        assertTrue(freshKeyInMinutes(key))
        assertEquals(2L, freshKeyValue(key))
        assertFalse(freshKeyStale(freshLabelKey(99, stale)))
        // Stale within its first minute still says how old it is: 0 minutes.
        assertEquals(0L, freshKeyValue(freshLabelKey(30, 20)))
    }

    @Test
    fun secondsCountInStepsOfFive() {
        val stale = 100L
        assertEquals(5L, freshLabelKey(5, stale))
        assertEquals(5L, freshLabelKey(9, stale))
        assertEquals(10L, freshLabelKey(10, stale))
        assertEquals(15L, freshLabelKey(17, stale))
        assertEquals(55L, freshLabelKey(55, stale))
        // Then "vor 1′".
        assertTrue(freshKeyInMinutes(freshLabelKey(60, stale)))
        assertEquals(1L, freshKeyValue(freshLabelKey(60, stale)))
        // Every key in seconds is a step of five.
        for (age in 5L until 60L) assertEquals(0L, freshLabelKey(age, stale) % FRESH_SECONDS_STEP)
    }

    @Test
    fun theNumberStandsBetweenItsWords() {
        assertEquals("Live · vor " to " s", aroundNumber("Live · vor %1\$d s"))
        assertEquals("Live · vor " to "\u2032", aroundNumber("Live · vor %1\$d\u2032"))
        assertEquals("" to " min ago", aroundNumber("%d min ago"))
        assertEquals(null, aroundNumber("Live · gerade eben"))
    }

    @Test
    fun theLabelChangesAtTheseAges() {
        val stale = 100L
        assertEquals(5L, freshNextChangeAge(0, stale))
        assertEquals(5L, freshNextChangeAge(-3, stale))
        assertEquals(10L, freshNextChangeAge(5, stale))
        assertEquals(10L, freshNextChangeAge(9, stale))
        assertEquals(60L, freshNextChangeAge(55, stale))
        // In minutes, then stale at 100 s, then stale minutes.
        assertEquals(100L, freshNextChangeAge(60, stale))
        assertEquals(120L, freshNextChangeAge(100, stale))
        assertEquals(20L, freshNextChangeAge(15, 20))
        // Between two of these ages the label never changes, and at each it does.
        for (age in 0L until 400L) {
            val next = freshNextChangeAge(age, stale)
            assertTrue(next > age)
            for (between in age until next) assertEquals(freshLabelKey(age, stale), freshLabelKey(between, stale))
            assertTrue(freshLabelKey(next, stale) != freshLabelKey(next - 1, stale))
        }
    }

    @Test
    fun theClockWakesOnlyWhenALabelChanges() {
        val marked = 1_000_000_400L
        // Marked at .400 of a second: its whole-second age turns 5 at second 1_000_005.
        assertEquals(1_000_005_000L, freshNextWakeMillis(1_000_000_900L, marked, 100))
        assertEquals(1_000_010_000L, freshNextWakeMillis(1_000_005_000L, marked, 100))
        // Freshness chips alone: every 5 s up to the first minute (12 wakes), not every second.
        var now = marked
        var wakes = 0
        while (now < 1_000_060_000L) {
            now = tickerNextWake(now, listOf(freshNextWakeMillis(now, marked, 100)))
            wakes++
        }
        assertEquals(12, wakes)
        // A consumer showing seconds makes it wake each second.
        assertEquals(1_000_001_000L, tickerNextWake(1_000_000_500L, listOf(freshNextWakeMillis(1_000_000_500L, marked, 100), nextWholeSecondMillis(1_000_000_500L))))
        // Nobody: a long sleep; a moment already past: the next millisecond.
        assertEquals(5L + TICKER_IDLE_MILLIS, tickerNextWake(5L, emptyList()))
        assertEquals(6L, tickerNextWake(5L, listOf(3L)))
    }
}
