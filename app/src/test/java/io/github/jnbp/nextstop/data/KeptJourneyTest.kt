package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The open journey page kept on the device comes back whole, and only while it is of use. */
class KeptJourneyTest {
    private val start = OffsetDateTime.parse("2026-11-16T22:10:00+01:00")
    private val from = Stop("hafas:1", "Example Square", 52.521, 13.411, null, setOf("subway"), areas = listOf("Berlin"))
    private val to = Stop("hafas:2", "Alexanderplatz", 52.521, 13.411, 120, setOf("subway", "suburban"))

    private val ride = Leg(
        line = "U8",
        product = "subway",
        walking = false,
        from = from.name,
        to = "Hackescher Markt",
        departure = start.plusMinutes(2),
        plannedDeparture = start,
        arrival = start.plusMinutes(6),
        plannedArrival = start.plusMinutes(5),
        departurePlatform = "1",
        arrivalPlatform = null,
        direction = "Hackescher Markt",
        cancelled = false,
        realtime = true,
        path = listOf(52.521 to 13.411, 52.5225 to 13.4024),
        fromPlace = from,
        warnings = listOf(
            Warning("Lift", "Out of order", id = "w1", validFrom = start, links = listOf(NoticeLink(NoticeLinkKind.More, "https://example.org"))),
        ),
        stopovers = listOf(Stopover("Rosenthaler Platz", start.plusMinutes(4), start.plusMinutes(4), delaySeconds = 60, estimated = true)),
        tripId = "trip-1",
        boardingSpot = WalkTarget(WalkTargetKind.StopPosition, "2", 52.5211, 13.4111, listOf(OsmLine("M46", "bus")), level = "-1", climbBy = PassageKind.Lift, climb = 1),
        throughRuns = listOf(ThroughRun("trip-2", "U8", "Ostkreuz", null, start.plusMinutes(3), lineChanges = false)),
        providerColor = -16776961,
        // #264: the small chips of the ride card come back too.
        occupancy = Occupancy.Medium,
        features = setOf(RideFeature.BikeCarriage, RideFeature.StepFree),
        bikeNote = "Fahrradmitnahme begrenzt möglich",
    )
    private val walk = Leg(
        line = null, product = "walking", walking = true, from = "Hackescher Markt", to = to.name,
        departure = start.plusMinutes(6), plannedDeparture = null, arrival = start.plusMinutes(30), plannedArrival = null,
        departurePlatform = null, arrivalPlatform = null, direction = null, cancelled = false, toPlace = to, walkMeters = 900,
    )
    private val journey = Journey(
        legs = listOf(ride, walk),
        refreshToken = "token-1",
        split = JourneySplit(1, "head", "tail", "cut"),
        via = JourneyVia(to, 5, 1),
        foundLevel = TransferComfort.Normal,
    )
    private val other = Journey(legs = listOf(walk.copy(departure = start.plusMinutes(40), arrival = start.plusMinutes(60))))

    private val page = KeptPage(
        savedAt = 1_000_000L,
        tab = "Route",
        tripOpen = true,
        followed = journey,
        origin = from,
        destination = to,
        via = Via(to, 3),
        time = start,
        timeArrival = true,
        journeys = listOf(other, journey),
        selectedId = journey.id,
        detailPage = true,
        planning = false,
    )

    @Test
    fun pageComesBackWhole() {
        val back = keptPageOf(keptPageJson(page))
        assertEquals(page, back)
        assertEquals(journey.id, back!!.openJourney!!.id)
        assertEquals("token-1", back.followed!!.refreshToken)
    }

    @Test
    fun brokenOrForeignFileGivesNothing() {
        assertNull(keptPageOf("not json"))
        assertNull(keptPageOf("{\"v\":7,\"savedAt\":1}"))
    }

    @Test
    fun pageIsDroppedWhenOldOrArrived() {
        val now = start.plusMinutes(10)
        assertTrue(keptPageUsable(page, page.savedAt + 60_000L, now))
        // Older than about twelve hours.
        assertFalse(keptPageUsable(page, page.savedAt + KEPT_PAGE_MAX_AGE_MILLIS + 1, now))
        // The open journey has arrived.
        assertFalse(keptPageUsable(page, page.savedAt + 60_000L, start.plusMinutes(31)))
        // Without an open journey: as long as one of the connections has not arrived.
        val list = page.copy(followed = null, selectedId = null)
        assertTrue(keptPageUsable(list, page.savedAt + 60_000L, start.plusMinutes(31)))
        assertFalse(keptPageUsable(list, page.savedAt + 60_000L, start.plusMinutes(61)))
        // Nothing open.
        assertFalse(keptPageUsable(list.copy(journeys = null, destination = null), page.savedAt, now))
    }

    @Test
    fun aStartRestoredFromAnEarlierRunIsDroppedWhenItsSearchCloses() {
        // The restored page's start: the next search starts where the user is.
        assertNull(startAfterClose(from, restored = from))
    }

    @Test
    fun aStartPickedInThisRunStaysAfterItsSearchCloses() {
        // Picked by hand now (another object, even for the same stop): it stays.
        assertEquals(from, startAfterClose(from.copy(), restored = from))
        assertEquals(to, startAfterClose(to, restored = from))
        assertEquals(to, startAfterClose(to, restored = null))
        assertNull(startAfterClose(null, restored = from))
    }
}
