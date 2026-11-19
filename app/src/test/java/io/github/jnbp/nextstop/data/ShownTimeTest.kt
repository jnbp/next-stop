package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** #227: one time as the setting "Verspätungen" shows it, for live on time, late, early and no live data. */
class ShownTimeTest {
    private val planned: OffsetDateTime = OffsetDateTime.parse("2026-01-01T21:51:00+01:00")
    private val late = planned.plusMinutes(3)
    private val early = planned.minusMinutes(1)

    private fun shown(display: DelayDisplay, actual: OffsetDateTime, realtime: Boolean, delay: Long) =
        shownTime(display, actual, planned, realtime, delay)

    @Test
    fun newTimeAndDelay() {
        val d = DelayDisplay.RealtimeAndDelay
        assertEquals(ShownTime(planned, TimeTone.OnTime, null, null), shown(d, planned, true, 0))
        assertEquals(ShownTime(late, TimeTone.Late, null, 3L), shown(d, late, true, 3))
        assertEquals(ShownTime(early, TimeTone.OnTime, null, -1L), shown(d, early, true, -1))
        assertEquals(ShownTime(planned, TimeTone.Scheduled, null, null), shown(d, planned, false, 0))
    }

    @Test
    fun plannedTimeAndDelay() {
        val d = DelayDisplay.PlannedAndDelay
        assertEquals(ShownTime(planned, TimeTone.Scheduled, null, null), shown(d, planned, true, 0))
        assertEquals(ShownTime(planned, TimeTone.Scheduled, null, 3L), shown(d, late, true, 3))
        assertEquals(ShownTime(planned, TimeTone.Scheduled, null, -1L), shown(d, early, true, -1))
        assertEquals(ShownTime(planned, TimeTone.Scheduled, null, null), shown(d, planned, false, 0))
        // Without a timetable time the expected one stands.
        assertEquals(late, shownTime(d, late, null, true, 3).clock)
    }

    @Test
    fun newTimeOnly() {
        val d = DelayDisplay.RealtimeOnly
        assertEquals(ShownTime(planned, TimeTone.OnTime, null, null), shown(d, planned, true, 0))
        assertEquals(ShownTime(late, TimeTone.Late, null, null), shown(d, late, true, 3))
        assertEquals(ShownTime(early, TimeTone.OnTime, null, null), shown(d, early, true, -1))
        assertEquals(ShownTime(planned, TimeTone.Scheduled, null, null), shown(d, planned, false, 0))
    }

    @Test
    fun plannedStruckAndNewTime() {
        val d = DelayDisplay.StruckPlanned
        assertEquals(ShownTime(planned, TimeTone.OnTime, null, null), shown(d, planned, true, 0))
        assertEquals(ShownTime(late, TimeTone.Late, planned, null), shown(d, late, true, 3))
        assertEquals(ShownTime(early, TimeTone.OnTime, planned, null), shown(d, early, true, -1))
        assertEquals(ShownTime(planned, TimeTone.Scheduled, null, null), shown(d, planned, false, 0))
        // Nothing to strike without a timetable time.
        assertEquals(null, shownTime(d, late, null, true, 3).struck)
    }

    @Test
    fun plainTextFollowsTheSetting() {
        val clock: (OffsetDateTime) -> String = { "%02d:%02d".format(it.hour, it.minute) }
        val delay: (Long) -> String = { "%+d".format(it) }
        assertEquals("21:54 +3", shownTimeText(shown(DelayDisplay.RealtimeAndDelay, late, true, 3), clock, delay))
        assertEquals("21:51 +3", shownTimeText(shown(DelayDisplay.PlannedAndDelay, late, true, 3), clock, delay))
        assertEquals("21:54", shownTimeText(shown(DelayDisplay.RealtimeOnly, late, true, 3), clock, delay))
        assertEquals("21:54", shownTimeText(shown(DelayDisplay.StruckPlanned, late, true, 3), clock, delay))
        assertEquals("21:51", shownTimeText(shown(DelayDisplay.RealtimeAndDelay, planned, false, 0), clock, delay))
    }

    @Test
    fun estimatedFromPositionIsBlueAloneWhateverTheSetting() {
        // #277: planned = plain, live on time = green, live late = red, the app's estimate = blue
        // with "ca."; the estimate shows its own time alone, nothing struck, no delay.
        DelayDisplay.entries.forEach { d ->
            assertEquals(ShownTime(late, TimeTone.Estimated, null, null), shownTime(d, late, planned, true, 3, estimated = true))
            assertEquals(ShownTime(late, TimeTone.Estimated, null, null), shownTime(d, late, planned, false, 0, estimated = true))
            assertEquals(TimeTone.Estimated, shownTone(d, realtime = true, delayMinutes = 3, estimated = true))
        }
        // A live time that is not the app's estimate keeps its live colour (live data wins).
        val d = DelayDisplay.RealtimeOnly
        assertEquals(TimeTone.Late, shownTime(d, late, planned, true, 3, estimated = false).tone)
        assertEquals(TimeTone.OnTime, shownTime(d, planned, planned, true, 0).tone)
        assertEquals(TimeTone.Scheduled, shownTime(d, planned, planned, false, 0).tone)
    }
}
