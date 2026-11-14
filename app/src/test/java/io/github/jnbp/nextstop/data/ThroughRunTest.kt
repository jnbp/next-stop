package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One ride where the provider sends two: a rider's trip with the U9 from U Osloer Str. at 18:16
 * to U Schloßstr. at 18:23 (Gleis 1) and on from U Schloßstr. at 18:23 to S+U Rathaus
 * Steglitz at 18:24 (Gleis 1), under a new handle. The app showed "Umstieg, 0 min · knapp",
 * one change too many, the U9 twice in the chart and the M48 offered at a change that is none.
 */
class ThroughRunTest {
    private fun at(clock: String): OffsetDateTime = OffsetDateTime.parse("2026-11-13T$clock:00+01:00")
    private val start = Stop("900009202", "U Pankstr.", 52.5521, 13.3815, null)
    private val osloer = Stop("900009102", "U Osloer Str.", 52.5571, 13.3733, null)
    private val schloss = Stop("900061102", "U Schloßstr.", 52.4610, 13.3240, null)
    private val rathaus = Stop("900062202", "S+U Rathaus Steglitz", 52.4566, 13.3216, null)
    private val end = Stop("900062782", "Ziel", 52.45, 13.33, null)

    private fun ride(
        line: String,
        product: String,
        from: Stop,
        to: Stop,
        leaves: String,
        arrives: String,
        trip: String,
        fromPlatform: String? = null,
        toPlatform: String? = null,
        stops: List<Stopover> = emptyList(),
    ) = Leg(
        line, product, false, from.name, to.name, at(leaves), at(leaves), at(arrives), at(arrives),
        fromPlatform, toPlatform, "S+U Rathaus Steglitz", false, realtime = true,
        fromPlace = from, toPlace = to, tripId = trip, stopovers = stops,
    )

    private fun stopover(stop: Stop, arrives: String?, leaves: String?) =
        Stopover(stop.name, arrives?.let { at(it) }, leaves?.let { at(it) }, stop)

    private val feeder = ride("U8", "subway", start, osloer, "18:10", "18:13", "u7")
    private val u9a = ride(
        "U9", "subway", osloer, schloss, "18:16", "18:23", "u9-a", "1", "1",
        listOf(stopover(osloer, null, "18:16"), stopover(schloss, "18:23", null)),
    )
    private val u9b = ride(
        "U9", "subway", schloss, rathaus, "18:23", "18:24", "u9-b", "1", "1",
        listOf(stopover(schloss, null, "18:23"), stopover(rathaus, "18:24", null)),
    )
    private val m48 = ride("M48", "bus", rathaus, end, "18:28", "18:35", "m48")

    private val riderTrip = Journey(listOf(feeder, u9a, u9b, m48))

    @Test
    fun aRidersU9IsOneRide() {
        assertEquals(3, riderTrip.transfers)
        val merged = mergeThroughRuns(riderTrip)
        assertEquals(2, merged.transfers)
        val u9 = merged.rides[1]
        assertEquals("U9", u9.line)
        assertEquals(osloer.name, u9.from)
        assertEquals(rathaus.name, u9.to)
        assertEquals(at("18:16"), u9.departure)
        assertEquals(at("18:24"), u9.arrival)
        assertEquals(rathaus, u9.toPlace)
        // Both handles stay: the course follows the later run after the junction.
        assertEquals("u9-a", u9.tripId)
        assertEquals(listOf("u9-a", "u9-b"), u9.tripIds)
        assertEquals("u9-a", u9.tripIdAt(at("18:20")))
        assertEquals("u9-b", u9.tripIdAt(at("18:23")))
        // The junction once among the stops, arriving and leaving.
        assertEquals(listOf(osloer.name, schloss.name, rathaus.name), u9.stopovers.map { it.name })
        assertEquals(at("18:23"), u9.stopovers[1].arrival)
        assertEquals(at("18:23"), u9.stopovers[1].departure)
        assertNull(u9.continuesAs)
    }

    @Test
    fun noFakeChangeAfterTheMerge() {
        val merged = mergeThroughRuns(riderTrip)
        val changes = journeyChangesOf(merged)
        // Only U8 → U9 at Osloer Str. and U9 → M48 at Rathaus Steglitz; none at Schloßstr.
        assertEquals(listOf(osloer.name, rathaus.name), changes.map { it.arriving.to })
        assertEquals(2, journeyChangeRatings(merged).size)
    }

    @Test
    fun sameVehicleUnderAnotherLineIsOneRideWithANote() {
        val on = ride("U3", "subway", schloss, rathaus, "18:24", "18:26", "u9-a")
        val merged = mergeThroughRuns(Journey(listOf(u9a, on)))
        assertEquals(1, merged.rides.size)
        assertEquals("U3", merged.rides.single().continuesAs)
        val seated = ride("U3", "subway", schloss, rathaus, "18:24", "18:26", "other").copy(stayOnBoard = true)
        assertEquals("U3", mergeThroughRuns(Journey(listOf(u9a, seated))).rides.single().continuesAs)
    }

    @Test
    fun realChangesStay() {
        // Another line, no sign of the same vehicle.
        assertFalse(isThroughRun(u9a, ride("U3", "subway", schloss, rathaus, "18:24", "18:26", "u3")))
        // The same line, but more than 2 minutes later: the next train.
        assertFalse(isThroughRun(u9a, u9b.copy(departure = at("18:27"), plannedDeparture = at("18:27"))))
        // Another platform.
        assertFalse(isThroughRun(u9a, u9b.copy(departurePlatform = "2")))
        // Another station.
        assertFalse(isThroughRun(u9a, u9b.copy(from = rathaus.name, fromPlace = rathaus)))
        // Cancelled.
        assertFalse(isThroughRun(u9a, u9b.copy(cancelled = true)))
        // A platform missing on one side still joins.
        assertTrue(isThroughRun(u9a, u9b.copy(departurePlatform = null)))
        // "9" with product subway is the U9.
        assertTrue(isThroughRun(u9a, u9b.copy(line = "9")))
    }

    @Test
    fun aWalkBetweenKeepsTwoRides() {
        val walk = Leg(
            null, "walking", true, schloss.name, schloss.name, at("18:23"), at("18:23"), at("18:23"), at("18:23"),
            null, null, null, false,
        )
        assertEquals(2, mergeThroughRuns(Journey(listOf(u9a, walk, u9b))).rides.size)
    }
}
