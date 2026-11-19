package io.github.jnbp.nextstop.data

import java.time.Duration
import java.time.OffsetDateTime
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #88, on board: ICE Frankfurt -> Berlin near Huenfeld at 12:48, moving through the curves north
 * of Fulda at 15 m/s. The live arrival at Berlin Hbf is 15:17; the companion said "ca. 3 Std 45 min,
 * um 16:33": it ran the whole way left (some 390 km) at a speed half made of the GPS speed of the
 * moment. A long ride's position may only tell how far the train lags behind its live times.
 */
class LongRideOnBoardTest {
    private fun at(clock: String): OffsetDateTime = OffsetDateTime.parse("2026-11-18T${clock}:00+01:00")

    private fun stop(name: String, lat: Double, lon: Double) = Stop(name, name, lat, lon, null, setOf("express"))

    private val frankfurt = stop("Frankfurt (Main) Hauptbahnhof", 50.1071, 8.6633)
    private val hanau = stop("Hanau Hauptbahnhof", 50.1214, 8.9297)
    private val fulda = stop("Fulda", 50.5546, 9.6838)
    private val erfurt = stop("Erfurt Hbf", 50.9727, 11.0379)
    private val halle = stop("Halle (Saale) Hbf", 51.4775, 11.9870)
    private val berlin = stop("Berlin Hbf", 52.5251, 13.3694)

    // The way of the train through the places it passes (Huenfeld, Bad Hersfeld, Eisenach, Gotha, Bitterfeld).
    private val way = listOf(
        50.1071 to 8.6633, 50.1214 to 8.9297, 50.3500 to 9.3500, 50.5546 to 9.6838, 50.6200 to 9.7300,
        50.6720 to 9.7660, 50.8681 to 9.7070, 50.9768 to 10.3317, 50.9395 to 10.7128, 50.9727 to 11.0379,
        51.4775 to 11.9870, 51.6227 to 12.3165, 52.5251 to 13.3694,
    )

    private fun stopover(stop: Stop, arrival: String?, departure: String?) =
        Stopover(stop.name, arrival?.let { at(it) }, departure?.let { at(it) }, place = stop)

    // Live times: about 15 min late from Fulda on, which brings the train past Huenfeld at about 12:48.
    private val ice = Leg(
        "ICE 1092", "express", false, frankfurt.name, berlin.name, at("11:21"), at("11:21"), at("15:17"), at("15:02"),
        "7", "1", berlin.name, false, realtime = true, path = way, fromPlace = frankfurt, toPlace = berlin, tripId = "ice",
        stopovers = listOf(
            stopover(frankfurt, null, "11:21"), stopover(hanau, "11:36", "11:38"), stopover(fulda, "12:38", "12:40"),
            stopover(erfurt, "13:56", "13:58"), stopover(halle, "14:28", "14:30"), stopover(berlin, "15:17", null),
        ),
    )

    private fun fixAt(lat: Double, lon: Double, speed: Float, clock: String): PositionFix {
        val journey = Journey(listOf(ice))
        val sample = PositionSample(lat, lon, 15f, speed, at(clock).toInstant().toEpochMilli())
        val first = matchPosition(journey, sample.copy(atMillis = sample.atMillis - 5_000), at(clock), null)
        return matchPosition(journey, sample, at(clock), first)
    }

    private fun minutesOff(arrival: OffsetDateTime, expected: OffsetDateTime) = Duration.between(expected, arrival).toMinutes()

    @Test
    fun onTimeNearHuenfeldTheArrivalIsTheLiveOne() {
        val fix = fixAt(50.6720, 9.7660, 15f, "12:48")
        assertTrue(fix.kind == FixKind.OnRide)
        val eta = nextRideEta(null, ice, fix, at("12:48"))
        assertTrue("was ${eta.arrival}", kotlin.math.abs(minutesOff(eta.arrival, at("15:17"))) <= 5)
        val state = rideState(Journey(listOf(ice)), at("12:48"), fix)
        assertTrue("was ${state.time}", kotlin.math.abs(minutesOff(state.time!!, at("15:17"))) <= 5)
    }

    @Test
    fun aTrainBehindItsLiveTimesArrivesSomewhatLaterNotHoursLater() {
        // Still south of Huenfeld at 12:58, ten minutes after the live times put it there.
        val fix = fixAt(50.6400, 9.7438, 10f, "12:58")
        val eta = nextRideEta(null, ice, fix, at("12:58"))
        val late = minutesOff(eta.arrival, at("15:17"))
        assertTrue("was $late min", late in 1..12)
    }
}
