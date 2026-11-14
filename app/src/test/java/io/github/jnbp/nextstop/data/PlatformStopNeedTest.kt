package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A change between a train and a bus counts from the platform to the stop position. The case
 * of S Lichterfelde Ost: S25 on platform 1/2, the bus 184 at its stop in the street, 5 minutes
 * to change. Positions from the asset osm-berlin.json (© OpenStreetMap contributors).
 */
class PlatformStopNeedTest {
    private val start: OffsetDateTime = OffsetDateTime.parse("2026-01-01T10:00:00+01:00")
    private fun at(minutes: Long) = start.plusMinutes(minutes)

    private val station = Stop("900064301", "S Lichterfelde Ost", 52.42975, 13.328452, null, setOf("suburban", "regional", "bus"))
    private val elsewhere = Stop("900000001", "Somewhere", 52.5, 13.4, null)
    private val platform = OsmPlatform(52.429445 to 13.327536, 52.430902 to 13.329573, "1;2", "1", listOf("S25", "S26"))
    private val busStop = WalkTarget(WalkTargetKind.StopPosition, null, 52.428718, 13.328268)

    private val s25 = Leg(
        "S25", "suburban", false, elsewhere.name, station.name, at(0), at(0), at(10), at(10), null, "1", null, false,
        fromPlace = elsewhere, toPlace = station, tripId = "s25",
    )
    private val bus = Leg(
        "184", "bus", false, station.name, elsewhere.name, at(15), at(15), at(30), at(30), null, null, null, false,
        fromPlace = station, toPlace = elsewhere, tripId = "184", boardingSpot = busStop,
    )

    // The provider's walk between the two: the whole wait, from the middle of the station to the stop.
    private val walk = Leg(
        null, "walk", true, station.name, station.name, at(10), at(10), at(15), at(15), null, null, null, false,
        path = listOf(station.position!!, busStop.position), fromPlace = station, toPlace = station,
    )

    private val platforms: (Leg, Boolean) -> List<OsmPlatform> = { ride, _ -> if (ride.product == "suburban") listOf(platform) else emptyList() }

    @Test
    fun theNumbers() {
        // About 95 m from the south end of the platform to the stop.
        val meters = segmentMeters(busStop.position, platform.a, platform.b)
        assertEquals(95.0, meters, 3.0)
        assertEquals(2L, spotWalkMinutes(meters))
        // 2 minutes on foot and one more is less than a change to another platform: 3.
        assertEquals(3L, platformStopNeed(s25, bus, platforms))
    }

    @Test
    fun beforeTheChangeWasRiskyByTheMiddleOfTheStation() {
        // What decided before: the walk of the provider by its way from the middle of the
        // station (116 m: 3 minutes on foot and 2 for the station, all of the 5 minutes),
        // and one minute more: 6 needed, 1 short; at a brisk pace (5 x 0.8: 4 minutes) knapp.
        val walkMinutes = changeWalkMinutes(walk, s25, bus)
        assertEquals(5L, walkMinutes)
        assertEquals(6L, neededMinutes(changeKind(s25, bus, walkMinutes), walkMinutes, largeStation(s25, bus)))
        assertEquals(Tightness.Risky, changeTightness(s25, bus.copy(boardingSpot = null), walkMinutes))
        // Without a walk the regional platforms of the station made it 5 as well; no more.
        assertFalse(largeStation(s25, bus))
    }

    @Test
    fun fiveMinutesToTheBusAreNormal() {
        val previous = ridePlatforms
        ridePlatforms = platforms
        try {
            val journey = Journey(listOf(s25, walk, bus), foundLevel = TransferComfort.Normal)
            assertEquals(listOf(Tightness.Tight), journeyChangeRatings(journey))
            assertEquals(listOf(2L), transferBuffers(journey))
            // 4 minutes leave 1: risky.
            val closer = Journey(listOf(s25, walk.copy(arrival = at(14), plannedArrival = at(14)), bus.copy(departure = at(14), plannedDeparture = at(14))), foundLevel = TransferComfort.Normal)
            assertEquals(listOf(Tightness.Risky), journeyChangeRatings(closer))
        } finally {
            ridePlatforms = previous
        }
    }

    @Test
    fun onlyWithBothPositions() {
        assertNull(platformStopNeed(s25, bus.copy(boardingSpot = null), platforms))
        assertNull(platformStopNeed(s25, bus) { _, _ -> emptyList() })
        // Bus to bus and train to train are not for this rule.
        assertNull(platformStopNeed(bus, bus, platforms))
        assertNull(platformStopNeed(s25, s25, platforms))
        // The other way, from the bus to the train.
        assertEquals(3L, platformStopNeed(bus.copy(alightingSpot = busStop), s25, platforms))
    }
}
