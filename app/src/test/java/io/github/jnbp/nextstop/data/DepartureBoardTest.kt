package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The departures board: cancelled runs at their planned time, pages that overlap merged into
 * one list, rides of one complex shown once, and the way round of the ring in words.
 * As seen at S Südkreuz (S41, S42, S2, S25, S26) in November 2026.
 */
class DepartureBoardTest {
    private val base: OffsetDateTime = OffsetDateTime.parse("2026-11-13T16:00:00+01:00")

    private fun departure(
        line: String,
        planned: Long,
        delay: Long? = 0,
        cancelled: Boolean = false,
        platform: String? = "11",
        direction: String = "Ring S41 ⟳",
        tripId: String? = null,
    ) = Departure(
        line = line,
        product = "suburban",
        direction = direction,
        // BVG HAFAS sends a cancelled run without a live time.
        time = if (cancelled || delay == null) null else base.plusMinutes(planned + delay),
        plannedTime = base.plusMinutes(planned),
        delaySeconds = if (cancelled) null else delay?.times(60)?.toInt(),
        platform = platform,
        cancelled = cancelled,
        tripId = tripId,
    )

    @Test
    fun cancelledRunsStandAtTheirPlannedTime() {
        // HAFAS order: the cancelled run first, as its empty live time sorts before all others.
        val answer = listOf(
            departure("S2", 9, cancelled = true, platform = "2", direction = "S Blankenfelde"),
            departure("S41", 3),
            departure("S42", 5, platform = "12", direction = "Ring S42 ⟲"),
            departure("S41", 13, delay = 2),
        )
        val board = inDepartureOrder(answer)
        assertEquals(listOf(3L, 5L, 9L, 13L), board.map { it.plannedTime!!.minute.toLong() })
        assertEquals(true, board[2].cancelled)
    }

    @Test
    fun aDelayedRunGoesByItsLiveTime() {
        val board = inDepartureOrder(listOf(departure("S41", 3, delay = 6), departure("S42", 5, platform = "12")))
        assertEquals("S42", board[0].line)
    }

    @Test
    fun departuresWithoutAnyTimeGoLast() {
        val none = departure("S1", 0).copy(time = null, plannedTime = null)
        assertEquals(none, inDepartureOrder(listOf(none, departure("S41", 3))).last())
    }

    @Test
    fun overlappingPagesShowEachRideOnce() {
        val first = listOf(departure("S41", 3, tripId = "a"), departure("S42", 5, tripId = "b"), departure("S41", 13, tripId = "c"))
        val start = nextDeparturesStart(first)
        assertEquals(base.plusMinutes(13), start)
        val second = listOf(departure("S41", 13, tripId = "c"), departure("S42", 15, tripId = "d"))
        val board = mergeDepartures(listOf(first, second))
        assertEquals(listOf("a", "b", "c", "d"), board.map { it.tripId })
    }

    @Test
    fun oneRingTripPassingTwiceIsTwoDepartures() {
        // A ring trip goes round once and on: it leaves here again an hour later.
        val now = departure("S41", 3, tripId = "ring")
        val later = departure("S41", 63, tripId = "ring")
        assertEquals(2, mergeDepartures(listOf(listOf(now), listOf(later))).size)
        assertEquals(1, mergeDepartures(listOf(listOf(now), listOf(now))).size)
    }

    @Test
    fun theSameRideFromTwoProvidersShowsOnceByLineMinuteAndPlatform() {
        val hafas = departure("S41", 3, tripId = "1|123")
        val transitous = departure("S41", 3, direction = "S Südkreuz", tripId = "tr:abc")
        assertEquals(1, mergeDepartures(listOf(listOf(hafas), listOf(transitous))).size)
        // The other ring line at the same minute is another ride.
        val other = departure("S42", 3, platform = "12", direction = "Ring S42 ⟲", tripId = "1|456")
        assertEquals(2, mergeDepartures(listOf(listOf(hafas), listOf(other))).size)
    }

    @Test
    fun emptyBoardHasNoNextPage() {
        assertNull(nextDeparturesStart(emptyList()))
    }

    @Test
    fun ringWayFromLineOrArrow() {
        assertEquals(RingWay.Clockwise, ringWayOf("S41", "Ring S41 ⟳"))
        assertEquals(RingWay.Counterclockwise, ringWayOf("S42", "Ring S42 ⟲"))
        assertEquals(RingWay.Clockwise, ringWayOf("S 41", "S41 Ring ↻"))
        assertEquals(RingWay.Counterclockwise, ringWayOf("S42", "Ringbahn"))
        // A ring line turned short of the ring goes to a station like any other.
        assertNull(ringWayOf("S41", "S Westend"))
        assertNull(ringWayOf("S2", "S Blankenfelde"))
        assertNull(ringWayOf("Bus 106", "Ringstr."))
        assertEquals(RingWay.Counterclockwise, ringWayOf("S45", "Ring ↺"))
    }

    @Test
    fun anS1AndAnExtraOneWithoutRemarkAreOneRide() {
        // A rider's watch at an S-Bahn station: "S1 S Oranienburg 13 min" and "1 S Oranienburg 13 min",
        // the "1" without the remark of an additional run; named "S1" by rapidLineName first.
        val regular = departure("S1", 13, platform = "1", direction = "S Oranienburg", tripId = "1|a")
        val extra = departure(rapidLineName("1", "suburban")!!, 13, platform = "1", direction = "S Oranienburg", tripId = "1|b")
        val south = departure("S1", 13, delay = 3, platform = "2", direction = "S Zehlendorf", tripId = "1|c")
        assertEquals(listOf(regular, south), inDepartureOrder(withoutDoubleRides(listOf(south, regular, extra))))
        // Through the board of a station complex as well.
        assertEquals(2, mergeDepartures(listOf(listOf(south, regular, extra))).size)
    }

    @Test
    fun theRegularRunWithLiveDataIsKept() {
        val regular = departure("S1", 13, platform = "1", direction = "S Oranienburg", tripId = "1|a")
        val extra = departure("S1", 13, platform = "1", direction = "S Oranienburg", tripId = "1|b").copy(additional = true)
        assertEquals(listOf(regular), withoutDoubleRides(listOf(extra, regular)))
        val noLive = regular.copy(time = null, delaySeconds = null)
        val live = departure("S1", 13, delay = 1, platform = null, direction = "S Oranienburg", tripId = "1|b")
        assertEquals(listOf(live), withoutDoubleRides(listOf(noLive, live)))
        // A cancelled S1 and a "1" that goes without the remark: the one that goes stays.
        val cancelled = departure("S1", 13, cancelled = true, platform = "1", direction = "S Oranienburg", tripId = "1|a")
        assertEquals(listOf(live), withoutDoubleRides(listOf(cancelled, live)))
    }

    @Test
    fun anotherMinutePlatformOrDirectionIsAnotherRide() {
        val regular = departure("S1", 13, platform = "1", direction = "S Oranienburg", tripId = "1|a")
        listOf(
            departure("S1", 14, platform = "1", direction = "S Oranienburg", tripId = "1|b"),
            departure("S1", 13, platform = "2", direction = "S Oranienburg", tripId = "1|b"),
            departure("S1", 13, platform = "1", direction = "S Wannsee", tripId = "1|b"),
            departure("S2", 13, platform = "1", direction = "S Oranienburg", tripId = "1|b"),
        ).forEach { other -> assertEquals(2, withoutDoubleRides(listOf(regular, other)).size) }
    }
}
