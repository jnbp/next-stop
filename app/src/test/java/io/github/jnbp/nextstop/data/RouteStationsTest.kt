package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** The stations of a journey on its map, and the one a finger hits. Made-up places. */
class RouteStationsTest {
    private fun at(clock: String): OffsetDateTime = OffsetDateTime.parse("2026-01-01T${clock}:00+01:00")
    private fun stop(id: String, north: Double?) = Stop(id, "Example $id", north?.let { 52.4 + it }, 13.2, null)

    private val a = stop("A", 0.0)
    private val b = stop("B", 0.01)
    private val c = stop("C", 0.02)
    private val nowhere = stop("X", null)
    private val street = Stop("addr", "Example Street 1", 52.5, 13.2, null, kind = PlaceKind.Address)

    private fun leg(from: Stop, to: Stop, walking: Boolean = false, stops: List<Stopover> = emptyList()) = Leg(
        if (walking) null else "S1", if (walking) "" else "suburban", walking, from.name, to.name,
        at("10:00"), at("10:00"), at("10:10"), at("10:10"), null, null, null, false,
        fromPlace = from, toPlace = to, stopovers = stops,
    )

    @Test
    fun ridesGiveTheirStationsOnceWithAPlace() {
        val journey = Journey(
            listOf(
                leg(street, a, walking = true),
                leg(a, b, stops = listOf(Stopover(a.name, null, at("10:00"), a), Stopover(nowhere.name, null, null, nowhere), Stopover(b.name, at("10:10"), null, b))),
                leg(b, c),
            ),
        )
        assertEquals(listOf("A", "B", "C"), journeyStations(journey).map { it.id })
        assertEquals(listOf("A", "B"), courseStations(listOf(Stopover(a.name, null, null, a), Stopover(b.name, null, null, b), Stopover(a.name, null, null, a))).map { it.id })
    }

    @Test
    fun theNearestStationWithinReach() {
        val xs = floatArrayOf(0f, 100f, 110f)
        val ys = floatArrayOf(0f, 0f, 0f)
        assertEquals(2, nearestStationIndex(xs, ys, 108f, 5f, 20f))
        assertEquals(1, nearestStationIndex(xs, ys, 102f, 0f, 20f))
        assertEquals(-1, nearestStationIndex(xs, ys, 50f, 0f, 20f))
        assertEquals(-1, nearestStationIndex(FloatArray(0), FloatArray(0), 0f, 0f, 20f))
    }
}
