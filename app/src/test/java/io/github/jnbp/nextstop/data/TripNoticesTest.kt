package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** When a notification about the followed trip is taken back (#172), with made-up stations. */
class TripNoticesTest {
    private fun at(clock: String): OffsetDateTime = OffsetDateTime.parse("2026-01-01T${clock}:00+01:00")

    private fun ride(line: String, from: String, to: String, dep: String, arr: String, platform: String? = null, planned: String? = null) = Leg(
        line, "regional", false, from, to, at(dep), at(dep), at(arr), at(arr),
        platform, null, null, false, tripId = line.lowercase(), plannedDeparturePlatform = planned,
    )

    private fun walk(from: String, to: String, dep: String, arr: String) = Leg(
        null, "walking", true, from, to, at(dep), at(dep), at(arr), at(arr), null, null, null, false,
    )

    private val first = ride("RE1", "Example A", "Example B", "10:00", "10:30", platform = "5", planned = "3")
    private val second = ride("RE2", "Example B", "Example C", "10:40", "11:10")
    private val journey = Journey(listOf(first, walk("Example B", "Example B", "10:30", "10:35"), second))
    private val shifts = platformShifts(journey, emptyMap())

    @Test
    fun tagsRoundTrip() {
        val notices = listOf(
            TripNotice.Platform("re1|d|Example A", true),
            TripNotice.Platform("re1|a|Example B", false),
            TripNotice.Rescue("abc", 2),
            TripNotice.Alight("abc", 0),
        )
        notices.forEach { assertEquals(it, TripNotice.parse(it.tag)) }
        assertNull(TripNotice.parse(null))
        assertNull(TripNotice.parse("disruption|x|y"))
        assertNull(TripNotice.parse("trip.rescue|x|y"))
    }

    @Test
    fun platformChangeStaysUntilTheRideLeaves() {
        val notice = platformNotice(journey, shifts.single())!!
        assertTrue(keepTripNotice(notice, journey, at("09:50"), shifts, null))
        assertFalse(keepTripNotice(notice, journey, at("10:01"), shifts, null))
    }

    @Test
    fun platformChangeGoesWhenTheRideIsNoLongerPartOfTheTrip() {
        val notice = platformNotice(journey, shifts.single())!!
        val other = Journey(listOf(ride("RE8", "Example A", "Example C", "10:05", "11:00")))
        assertFalse(keepTripNotice(notice, other, at("09:50"), platformShifts(other, emptyMap()), null))
    }

    @Test
    fun platformChangeGoesWhenThePlatformIsBack() {
        val notice = platformNotice(journey, shifts.single())!!
        val back = Journey(listOf(first.copy(departurePlatform = "3"), journey.legs[1], second))
        assertFalse(keepTripNotice(notice, back, at("09:50"), platformShifts(back, emptyMap()), null))
    }

    @Test
    fun everythingGoesAtTheEndOfTheTrip() {
        val hash = noticeJourneyHash(journey)
        listOf(platformNotice(journey, shifts.single())!!, TripNotice.Rescue(hash, 2), TripNotice.Alight(hash, 0))
            .forEach { assertFalse(keepTripNotice(it, null, at("09:50"), shifts, TripNotice.Rescue(hash, 2))) }
    }

    @Test
    fun rescueStaysWhileStillSuggestedAndBeforeTheNextRide() {
        val notice = TripNotice.Rescue(noticeJourneyHash(journey), 2)
        assertTrue(keepTripNotice(notice, journey, at("10:20"), shifts, notice))
        // Works again (no suggestion), another change, passed, or another journey followed.
        assertFalse(keepTripNotice(notice, journey, at("10:20"), shifts, null))
        assertFalse(keepTripNotice(notice, journey, at("10:20"), shifts, TripNotice.Rescue(notice.journeyHash, 1)))
        assertFalse(keepTripNotice(notice, journey, at("10:41"), shifts, notice))
        val switched = Journey(listOf(first, ride("RE8", "Example B", "Example C", "10:45", "11:20")))
        assertFalse(keepTripNotice(notice, switched, at("10:20"), shifts, notice))
    }

    @Test
    fun alightGoesAfterTheArrival() {
        val notice = TripNotice.Alight(noticeJourneyHash(journey), 0)
        assertTrue(keepTripNotice(notice, journey, at("10:29"), shifts, null))
        assertFalse(keepTripNotice(notice, journey, at("10:31"), shifts, null))
    }

    @Test
    fun plannedReminderGoesWhenStartedOrCancelled() {
        assertEquals("p1", plannedNoticeId(PLANNED_NOTICE_PREFIX + "p1"))
        assertNull(plannedNoticeId("trip.alight|0|x"))
        assertTrue(keepPlannedNotice("j1", null))
        assertTrue(keepPlannedNotice("j1", "j2"))
        assertFalse(keepPlannedNotice(null, null))
        assertFalse(keepPlannedNotice("j1", "j1"))
    }

    @Test
    fun sectionsOfOneTrackAreNoPlatformChange() {
        assertTrue(platformShifts(Journey(listOf(ride("RE8", "Example A", "Example C", "10:05", "11:00", platform = "1 D - G", planned = "1"))), emptyMap()).isEmpty())
        assertTrue(platformShifts(Journey(listOf(ride("RE8", "Example A", "Example C", "10:05", "11:00", platform = "11 A-C", planned = "11"))), emptyMap()).isEmpty())
        assertEquals(1, platformShifts(Journey(listOf(ride("RE8", "Example A", "Example C", "10:05", "11:00", platform = "2 D - G", planned = "1"))), emptyMap()).size)
        assertEquals(1, platformShifts(Journey(listOf(ride("RE8", "Example A", "Example C", "10:05", "11:00", platform = "4b", planned = "4a"))), emptyMap()).size)
    }
}
