package io.github.jnbp.nextstop.data

import java.time.Duration
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #88, 18.11.: on ICE 621 from the airport into Frankfurt Hbf, the companion of the trip
 * Frankfurt Hbf 11:21 -> Berlin Hbf 15:17 (ICE) took the traveller for already on board the
 * Berlin train ("Naechster Halt: Hanau", "boarded earlier") and reckoned its arrival from the
 * whole way to Berlin at the speed of the train braking into Frankfurt: "ca. 6 Std 46 min,
 * um 17:40" while the train arrives 15:17. The change to the FEX at 15:22 then read "zu spaet".
 */
class LongRideEtaTest {
    private fun at(clock: String): OffsetDateTime = OffsetDateTime.parse("2026-11-18T${clock}:00+01:00")

    private val frankfurt = Stop("de:06412:10", "Frankfurt (Main) Hauptbahnhof", 50.1071, 8.6633, null, setOf("express"))
    private val berlin = Stop("de:11000:900003201", "S+U Berlin Hauptbahnhof", 52.5251, 13.3694, null, setOf("express"))
    private val way = listOf(
        50.1071 to 8.6633, 50.1210 to 8.9290, 50.5540 to 9.6840, 50.9720 to 11.0380, 51.4770 to 11.9870, 52.5251 to 13.3694,
    )

    private fun ice(departure: String) = Leg(
        "ICE 1092", "express", false, frankfurt.name, berlin.name, at(departure), at(departure), at("15:17"), at("15:17"),
        "7", "1", null, false, realtime = true, path = way, fromPlace = frankfurt, toPlace = berlin, tripId = "ice",
    )

    private fun sample(lat: Double, lon: Double, speed: Float, clock: String) =
        PositionSample(lat, lon, 15f, speed, at(clock).toInstant().toEpochMilli())

    @Test
    fun onAnotherTrainIntoTheBoardingStationLongBeforeTheDepartureIsNotOnBoard() {
        val journey = Journey(listOf(ice("11:21")))
        // On ICE 621 from the airport, 6 and then 4.5 km before Frankfurt Hbf, at 30 and 25 m/s.
        val first = matchPosition(journey, sample(50.0700, 8.5900, 30f, "10:52"), at("10:52"), null)
        val fix = matchPosition(journey, sample(50.0800, 8.6100, 25f, "10:53"), at("10:53"), first)
        // There at about 10:56, the ICE to Berlin leaves at 11:21: on another vehicle, not on this one.
        assertNotEquals(FixKind.OnBoardEarly, fix.kind)
        assertEquals(RideStage.Before, rideState(journey, at("10:53"), fix).stage)
    }

    @Test
    fun boardedEarlierTheTrainStillKeepsItsTimetable() {
        // Really on board early (the train is due at the boarding stop in a few minutes): its live
        // arrival holds, never the whole way to Berlin at the speed of the braking train.
        val journey = Journey(listOf(ice("10:58")))
        val first = matchPosition(journey, sample(50.0700, 8.5900, 30f, "10:52"), at("10:52"), null)
        val fix = matchPosition(journey, sample(50.0800, 8.6100, 12f, "10:53"), at("10:53"), first)
        assertEquals(FixKind.OnBoardEarly, fix.kind)
        val state = rideState(journey, at("10:53"), fix)
        assertEquals(at("15:17"), state.time)
        val eta = nextRideEta(null, journey.legs[0], fix, at("10:53"))
        assertEquals(at("15:17"), eta.arrival)
    }

    @Test
    fun aBlendedArrivalStaysNearTheLiveOne() {
        // Hours of way left at a slow GPS speed: the live time is far more telling than 10 m/s now.
        val eta = blendedArrival(550_000.0, 10.0, at("15:17"), rideSpeed = 39.0, now = at("10:53"))
        assertTrue(Duration.between(at("15:17"), eta.arrival).toMinutes() <= 60)
        assertTrue(Duration.between(eta.arrival, at("15:17")).toMinutes() <= 140)
    }
}
