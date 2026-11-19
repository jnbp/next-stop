package io.github.jnbp.nextstop.wear.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** #227b: the phone's ShownTimeTest on the watch (epoch ms), and the setting as the phone syncs it. */
class ShownTimeTest {
    private val planned = 1_767_300_660_000L // 21:51 in Berlin
    private val late = planned + 3 * 60_000L
    private val early = planned - 60_000L

    private fun shown(display: DelayDisplay, actual: Long, realtime: Boolean, delay: Long) =
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
        assertEquals(null, shownTime(d, late, null, true, 3).struck)
    }

    @Test
    fun plainTextFollowsTheSetting() {
        val clock: (Long) -> String = { if (it == late) "21:54" else if (it == planned) "21:51" else "?" }
        val delay: (Long) -> String = { "%+d".format(it) }
        assertEquals("21:54 +3", shownTimeText(shown(DelayDisplay.RealtimeAndDelay, late, true, 3), clock, delay))
        assertEquals("21:51 +3", shownTimeText(shown(DelayDisplay.PlannedAndDelay, late, true, 3), clock, delay))
        assertEquals("21:54", shownTimeText(shown(DelayDisplay.RealtimeOnly, late, true, 3), clock, delay))
        assertEquals("21:54", shownTimeText(shown(DelayDisplay.StruckPlanned, late, true, 3), clock, delay))
        assertEquals("21:51", shownTimeText(shown(DelayDisplay.RealtimeAndDelay, planned, false, 0), clock, delay))
    }

    @Test
    fun theBoardCountsFromTheTimetableOnlyWithPlannedAndDelay() {
        assertEquals(planned, countedTime(DelayDisplay.PlannedAndDelay, late, planned))
        assertEquals(late, countedTime(DelayDisplay.RealtimeAndDelay, late, planned))
        assertEquals(late, countedTime(DelayDisplay.PlannedAndDelay, late, null))
        assertEquals(planned, countedTime(DelayDisplay.RealtimeOnly, null, planned))
    }

    @Test
    fun theSettingComesWithThePlaces() {
        assertEquals(DelayDisplay.StruckPlanned, SyncedPlaces.parse(JSONObject().put("delayDisplay", "StruckPlanned").toString()).phoneDelayDisplay)
        // An older phone app sends none, an unknown name is the default.
        assertEquals(DelayDisplay.RealtimeAndDelay, SyncedPlaces.parse(JSONObject().put("favorites", org.json.JSONArray()).toString()).phoneDelayDisplay)
        assertEquals(DelayDisplay.RealtimeAndDelay, DelayDisplay.parse("Sometime"))
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
