package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * A change from the U-Bahn to a bus outside the station with 4 minutes from arrival to
 * departure and a short walk up to the street: normal, not "knapp". Before, it was red three
 * times over: the U-Bahn was measured from the S-Bahn platform of the same track number, a
 * change from a platform to a street stop needed at least 3 minutes with 2 more to spare for
 * normal, and a journey found only by the search without change time was risky wherever up to
 * 4 minutes were left beyond the need. Made-up stops and positions.
 */
class SubwayToBusChangeTest {
    @After
    fun reset() {
        ridePlatforms = { _, _ -> emptyList() }
        transferWalkSeconds = { _, _ -> null }
        walkSpeedFactor = 1.0
    }

    private val start: OffsetDateTime = OffsetDateTime.parse("2026-01-01T20:00:00+01:00")
    private fun at(minutes: Long) = start.plusMinutes(minutes)

    // One metre north is about 1 / 111320 degrees.
    private fun north(meters: Double) = 52.0 + meters / 111_320.0

    private val elsewhere = Stop("900000091", "Somewhere", 52.1, 13.1, null)
    private val station = Stop("900000092", "S+U Somewhere Town Hall", 52.0, 13.0, null, setOf("suburban", "subway", "bus"))
    private val busStop = Stop("900000093", "S+U Somewhere Town Hall", 52.0, 13.0, null, setOf("bus"))

    // The U-Bahn ends on track 2 where its way ends; the bus leaves 100 m north of that.
    private val subway = Leg(
        "U9", "subway", false, elsewhere.name, station.name, at(-10), at(-10), at(0), at(0), null, "2", null, false,
        path = listOf(52.01 to 13.0, 52.0 to 13.0), fromPlace = elsewhere, toPlace = station, tripId = "u9",
    )

    private fun busIn(minutes: Long, meters: Double = 100.0) = Leg(
        "M99", "bus", false, busStop.name, elsewhere.name, at(minutes), at(minutes), at(30), at(30), null, null, null, false,
        fromPlace = busStop, toPlace = elsewhere, tripId = "m99",
        boardingSpot = WalkTarget(WalkTargetKind.StopPosition, "2", north(meters), 13.0),
    )

    // The provider's walk fills the whole wait and gives no length.
    private fun walkOf(minutes: Long) = Leg(
        null, "walk", true, station.name, busStop.name, at(0), at(0), at(minutes), at(minutes), null, null, null, false,
        fromPlace = station, toPlace = busStop,
    )

    private fun journey(minutes: Long, found: TransferComfort = TransferComfort.Risky, meters: Double = 100.0) =
        Journey(listOf(subway, walkOf(minutes), busIn(minutes, meters)), foundLevel = found)

    // OpenStreetMap knows only the S-Bahn platform "1;2" of the complex, 250 m south.
    private val sBahnPlatform = OsmPlatform(north(-250.0) to 13.0, north(-400.0) to 13.0, "1;2", "1", listOf("S1"))

    @Test
    fun aUBahnIsNotMeasuredFromTheSBahnPlatformOfItsNumber() {
        assertEquals(emptyList<OsmPlatform>(), platformsOfLine("U9", "2", listOf(sBahnPlatform)))
        assertEquals(listOf(sBahnPlatform), platformsOfLine("S1", "2", listOf(sBahnPlatform)))
        assertEquals(listOf(sBahnPlatform), platformsOfLine("S2", "1", listOf(sBahnPlatform)))
        // A platform without lines counts by its number for any kind.
        val plain = sBahnPlatform.copy(lines = emptyList())
        assertEquals(listOf(plain), platformsOfLine("U9", "2", listOf(plain)))
    }

    @Test
    fun theNeedFromThePlatformToTheStop() {
        // 100 m (130 on foot, 104 s) and a minute for getting off and the stairs: 3 minutes.
        assertEquals(3L, platformStopMinutes(100.0))
        // 50 m: 2 minutes, no longer 3.
        assertEquals(2L, platformStopMinutes(50.0))
        // The stop next to the exit: 2 at least.
        assertEquals(2L, platformStopMinutes(0.0))
        // "Langsamer unterwegs" makes the walk longer.
        assertEquals(4L, platformStopMinutes(100.0, SLOWER_WALK_FACTOR))
    }

    @Test
    fun withoutItsPlatformTheUBahnCountsFromWhereItsWayEnds() {
        assertEquals(3L, platformStopNeed(subway, busIn(4)) { _, _ -> emptyList() })
        assertEquals(2L, platformStopNeed(subway, busIn(4, meters = 50.0)) { _, _ -> emptyList() })
        // The other way, from the bus to the U-Bahn.
        val bus = busIn(0).copy(alightingSpot = WalkTarget(WalkTargetKind.StopPosition, "2", north(100.0), 13.0))
        val boarding = subway.copy(path = listOf(52.0 to 13.0, 52.01 to 13.0), fromPlace = station)
        assertEquals(3L, platformStopNeed(bus, boarding) { _, _ -> emptyList() })
        // No way and no platform: nothing to measure.
        assertEquals(null, platformStopNeed(subway.copy(path = emptyList()), busIn(4)) { _, _ -> emptyList() })
        // A known platform comes first.
        val own = OsmPlatform(north(70.0) to 13.0, north(-60.0) to 13.0, "2", "-1", listOf("U9"))
        assertEquals(2L, platformStopNeed(subway, busIn(4)) { _, _ -> listOf(own) })
    }

    @Test
    fun fourMinutesFromTheUBahnToTheBusAreNormal() {
        // Found only by the search without change time, as the provider's walk is long.
        val four = journey(4)
        assertEquals(listOf(Tightness.Tight), journeyChangeRatings(four))
        assertEquals(Tightness.Tight, journeyTightness(four))
        val change = journeyChangesOf(four).single()
        assertEquals(4L, change.time.minutes)
        assertEquals(3L, change.time.need)
        assertNotEquals(WaitTone.Tight, change.time.tone)
        // Found by the normal search as well: the same.
        assertEquals(listOf(Tightness.Tight), journeyChangeRatings(journey(4, TransferComfort.Normal)))
        // The stop right at the exit: 2 needed, normal with 3 minutes by the rule, whichever
        // search found the journey (the same change rates the same in every journey).
        assertEquals(listOf(Tightness.Tight), journeyChangeRatings(journey(3, TransferComfort.Normal, meters = 30.0)))
        assertEquals(listOf(Tightness.Tight), journeyChangeRatings(journey(3, meters = 30.0)))
    }

    @Test
    fun tighterOrFartherStaysKnapp() {
        // 3 minutes leave nothing beyond the 3 needed: knapp.
        assertEquals(listOf(Tightness.Risky), journeyChangeRatings(journey(3, TransferComfort.Normal)))
        // A stop 250 m away needs 5: 4 minutes are knapp at best.
        val far = journeyChangeRatings(journey(4, TransferComfort.Normal, meters = 250.0)).single()
        assertEquals(true, far.ordinal >= Tightness.Risky.ordinal)
    }

    @Test
    fun theSBahnPlatformOfTheSameNumberNoLongerDecides() {
        // Before, the U9 on track 2 was measured from the S-Bahn platform "1;2" 250 m away.
        ridePlatforms = { ride, _ -> platformsOfLine(ride.line, ride.arrivalPlatform ?: ride.departurePlatform, listOf(sBahnPlatform)) }
        assertEquals(3L, changeNeed(subway, busIn(4), walkMinutes = 4))
        assertEquals(listOf(Tightness.Tight), journeyChangeRatings(journey(4)))
    }
}
