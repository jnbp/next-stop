package io.github.jnbp.nextstop.ui

import androidx.compose.ui.unit.dp
import io.github.jnbp.nextstop.data.ChartTotalTime
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.Leg
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** The total time of a journey in the connections chart: door to door, said as people say it. Made-up stops. */
class ChartTotalTimeTest {
    private val start: OffsetDateTime = OffsetDateTime.parse("2026-01-01T08:00:00+01:00")
    private fun at(minutes: Long) = start.plusMinutes(minutes)

    private fun leg(walking: Boolean, leaves: Long, arrives: Long, late: Long = 0) = Leg(
        if (walking) null else "X1", if (walking) "walking" else "bus", walking, "A", "B",
        at(leaves + late), at(leaves), at(arrives + late), at(arrives), null, null, null, false,
    )

    // The German strings of the app: "%d′", "%d:00 h", "%d:%02d h" (#106).
    private fun german(minutes: Long) = totalTimeText(
        minutes,
        minutesOnly = { "$it′" },
        wholeHours = { "%d:00 h".format(it) },
        hoursAndMinutes = { hours, rest -> "%d:%02d h".format(hours, rest) },
    )

    @Test
    fun countsTheWalksAtStartAndEnd() {
        // 7 minutes on foot, a ride of 2:04 h, 12 minutes on foot again.
        val journey = Journey(listOf(leg(true, 0, 7), leg(false, 7, 131), leg(true, 131, 143)))
        assertEquals(143L, journeyTotalMinutes(journey))
        assertEquals("2:23 h", german(journeyTotalMinutes(journey)))
    }

    @Test
    fun usesTheLiveTimes() {
        // The ride arrives 5 minutes late: the journey takes 5 minutes longer.
        val journey = Journey(listOf(leg(false, 0, 40, late = 0), leg(true, 40, 50).copy(arrival = at(55))))
        assertEquals(55L, journeyTotalMinutes(journey))
    }

    @Test
    fun saysShortWholeAndMixedTimes() {
        assertEquals("48′", german(48))
        assertEquals("0′", german(0))
        assertEquals("59′", german(59))
        assertEquals("1:00 h", german(60))
        assertEquals("2:00 h", german(120))
        assertEquals("1:01 h", german(61))
        assertEquals("1:05 h", german(65))
        assertEquals("5:20 h", german(320))
        // Never below zero, even when live data puts the arrival before the departure.
        assertEquals("0′", german(-5))
    }

    @Test
    fun keepsRoomOnlyWhereTheTotalStands() {
        assertEquals(0.dp, chartRoomOf(ChartTotalTime.Off, top = true))
        assertEquals(0.dp, chartRoomOf(ChartTotalTime.Off, top = false))
        assertEquals(chartRoomOf(ChartTotalTime.Above, top = true), chartRoomOf(ChartTotalTime.Below, top = false))
        assertEquals(0.dp, chartRoomOf(ChartTotalTime.Above, top = false))
        assertEquals(0.dp, chartRoomOf(ChartTotalTime.Below, top = true))
        assert(chartRoomOf(ChartTotalTime.Above, top = true) > 0.dp)
        // Pinned totals leave the columns as they are.
        for (pinned in listOf(ChartTotalTime.PinnedTop, ChartTotalTime.PinnedBottom)) {
            assertEquals(0.dp, chartRoomOf(pinned, top = true))
            assertEquals(0.dp, chartRoomOf(pinned, top = false))
        }
    }

    @Test
    fun pinsABandOnlyAtTheChosenEdge() {
        assertEquals(PINNED_TOTAL_BAND, pinnedBandOf(ChartTotalTime.PinnedTop, top = true))
        assertEquals(0.dp, pinnedBandOf(ChartTotalTime.PinnedTop, top = false))
        assertEquals(PINNED_TOTAL_BAND, pinnedBandOf(ChartTotalTime.PinnedBottom, top = false))
        assertEquals(0.dp, pinnedBandOf(ChartTotalTime.PinnedBottom, top = true))
        for (moving in listOf(ChartTotalTime.Off, ChartTotalTime.Above, ChartTotalTime.Below)) {
            assertEquals(0.dp, pinnedBandOf(moving, top = true))
            assertEquals(0.dp, pinnedBandOf(moving, top = false))
        }
        assert(PINNED_TOTAL_BAND > 0.dp)
    }

    @Test
    fun readsTheStoredSettingOfBefore() {
        // "Top" and "Bottom" of the three choices of before are the lines that move with the column.
        assertEquals(ChartTotalTime.Above, ChartTotalTime.fromStored("Top"))
        assertEquals(ChartTotalTime.Below, ChartTotalTime.fromStored("Bottom"))
        for (setting in ChartTotalTime.entries) assertEquals(setting, ChartTotalTime.fromStored(setting.name))
        // Nothing stored, or something unknown: below the column, as before.
        assertEquals(ChartTotalTime.Below, ChartTotalTime.fromStored(null))
        assertEquals(ChartTotalTime.Below, ChartTotalTime.fromStored("Sideways"))
        assertEquals(5, ChartTotalTime.entries.size)
    }
}
