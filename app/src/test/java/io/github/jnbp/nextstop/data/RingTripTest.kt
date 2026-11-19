package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A ring line whose run covers more than one lap calls at the same stations twice: the ride
 * has to be found on the lap of its own times, and its way on the pass of the way of that lap
 * (see TripMatch). A made-up circle with Ringbahn names, about 1.4 laps.
 */
class RingTripTest {
    private val names = listOf("Westkreuz", "Halensee", "Heidelberger Platz", "Südkreuz", "Ostkreuz", "Gesundbrunnen", "Beusselstraße")
    private val start: OffsetDateTime = OffsetDateTime.parse("2026-03-02T12:00:00+01:00")
    private val centre = 52.50 to 13.40
    private val radius = 0.05
    private val lonScale = 1.0 / cos(Math.toRadians(centre.first))
    private val pointsPerHop = 24
    private val hops = 10

    private fun angle(station: Int) = station * 2 * PI / names.size

    private fun onCircle(angle: Double, factor: Double) =
        (centre.first + radius * factor * sin(angle)) to (centre.second + radius * factor * lonScale * cos(angle))

    // The stations lie a little inside the tracks; the second lap's way runs a few metres nearer
    // to them (as a thinned way may), so the nearest point of all lies a lap ahead.
    private fun station(index: Int) = onCircle(angle(index % names.size), 0.995)

    private fun stopAt(index: Int, delayMinutes: Long = 0): Stopover {
        val name = names[index % names.size]
        val planned = start.plusMinutes(8L * index)
        val position = station(index)
        return Stopover(
            name,
            planned.plusMinutes(delayMinutes),
            planned.plusMinutes(delayMinutes),
            Stop("id$name", name, position.first, position.second, null),
            (delayMinutes * 60).toInt(),
            plannedArrival = planned,
            plannedDeparture = planned,
        )
    }

    private val stops = (0..hops).map { stopAt(it, delayMinutes = 3) }

    private val path: List<Pair<Double, Double>> = buildList {
        for (hop in 0 until hops) {
            val factor = if (hop < names.size) 1.0 else 0.9985
            for (step in 0 until pointsPerHop) add(onCircle(angle(hop) + (angle(1) * step / pointsPerHop), factor))
        }
        add(onCircle(angle(hops), 0.9985))
    }

    private val course = TripCourse("ring-1", "S42", "suburban", "Ring", stops, path)

    // Boards at the second call at Halensee (stop 8), 3 minutes late, and gets off at Südkreuz (stop 10).
    private val leg = Leg(
        "S42", "suburban", false, "Halensee", "Südkreuz",
        start.plusMinutes(64 + 3), start.plusMinutes(64), start.plusMinutes(80 + 3), start.plusMinutes(80),
        null, null, null, false, realtime = true,
        fromPlace = stops[8].place, toPlace = stops[10].place, tripId = "ring-1",
    )

    private fun metres(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val north = (a.first - b.first) * 111_320.0
        val east = (a.second - b.second) * 111_320.0 * cos(Math.toRadians(a.first))
        return sqrt(north * north + east * east)
    }

    @Test
    fun fixtureHasTheTrap() {
        // The same name twice, and the plain nearest point of the way to the second stop lies on the second lap.
        assertEquals(1, stops.indexOfFirst { it.name == "Halensee" })
        val nearest = path.indices.minByOrNull { metres(path[it], station(1)) }!!
        assertTrue("nearest point anywhere is on lap 2: $nearest", nearest >= names.size * pointsPerHop)
    }

    @Test
    fun boardsAtTheCallOfItsOwnLap() {
        assertEquals(8, TripMatch.boardIndex(stops, leg))
        // Without live times, by the timetable alone.
        val planned = leg.copy(departure = start.plusMinutes(64), plannedDeparture = null)
        assertEquals(8, TripMatch.boardIndex(stops, planned))
        // A ride on the first lap still boards there.
        val early = leg.copy(departure = start.plusMinutes(8 + 3), plannedDeparture = start.plusMinutes(8))
        assertEquals(1, TripMatch.boardIndex(stops, early))
    }

    @Test
    fun alightsAfterBoardingOnTheSameLap() {
        assertEquals(10, TripMatch.alightIndex(stops, 8, leg))
        // A ride on the first lap gets off on the first lap, not one lap later.
        val early = leg.copy(arrival = start.plusMinutes(24 + 3), plannedArrival = start.plusMinutes(24))
        assertEquals(3, TripMatch.alightIndex(stops, 1, early))
        // Never at or before the boarding stop.
        assertEquals(-1, TripMatch.alightIndex(stops, 10, leg))
    }

    @Test
    fun stopsLieOnTheirOwnLapOfTheWay() {
        val at = TripMatch.stopPathIndices(path, stops.map { it.place?.position })
        for (index in stops.indices) {
            assertEquals("stop $index", index * pointsPerHop, at[index])
        }
    }

    @Test
    fun cutCoversOnlyTheSouthWestArcToSuedkreuz() {
        val cut = TripMatch.cutBetween(path, stops.map { it.place?.position }, 8, 10)!!
        assertEquals(2 * pointsPerHop + 1, cut.size)
        assertTrue(metres(cut.first(), station(1)) < 60)
        assertTrue(metres(cut.last(), station(3)) < 60)
        // Every point between the two, on the arc past Heidelberger Platz; none near the far side of the ring.
        val beussel = station(6)
        val ost = station(4)
        cut.forEach { point ->
            assertTrue(metres(point, beussel) > 3_000)
            assertTrue(metres(point, ost) > 2_000)
        }
        assertTrue(cut.any { metres(it, station(2)) < 60 })
    }

    @Test
    fun vehicleEstimateLandsOnTheRightLap() {
        // As TripTrack places it: between the points of the two stops it is between by its times.
        val at = TripMatch.stopPathIndices(path, stops.map { it.place?.position })
        // 13:11 with 3 minutes delay: half way from Halensee (13:07) to Heidelberger Platz (13:15).
        val halfway = path[(at[8] + at[9]) / 2]
        val expected = onCircle((angle(1) + angle(2)) / 2, 0.9985)
        assertTrue("${metres(halfway, expected)}", metres(halfway, expected) < 50)
        assertTrue((at[8] + at[9]) / 2 >= names.size * pointsPerHop)
        assertTrue(metres(halfway, station(6)) > 3_000)
    }

    @Test
    fun retargetedRideKeepsItsLap() {
        val ahead = vehicleStops(leg, course)
        assertEquals(stops.size - 8, ahead.size)
        assertEquals("Halensee", ahead.first().name)
        assertEquals(start.plusMinutes(64 + 3), ahead.first().departure)
        val ridden = rideUntil(leg, ahead, 2, course)
        assertEquals("Südkreuz", ridden.to)
        assertEquals(2 * pointsPerHop + 1, ridden.path.size)
        ridden.path.forEach { assertTrue(metres(it, station(6)) > 3_000) }
    }

    @Test
    fun pathIndexKeepsToTheFirstPass() {
        assertEquals(pointsPerHop, TripMatch.pathIndex(path, station(1), 0))
        assertEquals(8 * pointsPerHop, TripMatch.pathIndex(path, station(1), 2 * pointsPerHop))
    }
}
