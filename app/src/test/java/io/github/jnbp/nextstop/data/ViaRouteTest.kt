package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Journeys through a place on the way, put together from two searches. Made-up stops only. */
class ViaRouteTest {
    private val start: OffsetDateTime = OffsetDateTime.parse("2026-01-01T10:00:00+01:00")
    private fun at(minutes: Long) = start.plusMinutes(minutes)
    private fun stop(name: String) = Stop("id$name", "Stop $name", 52.0 + name.length * 0.001, 13.0, null)

    private fun ride(line: String, from: String, to: String, leaves: Long, arrives: Long) = Journey(
        listOf(
            Leg(
                line, "subway", false, "Stop $from", "Stop $to", at(leaves), at(leaves), at(arrives), at(arrives),
                null, null, null, false, fromPlace = stop(from), toPlace = stop(to), tripId = line,
            ),
        ),
        refreshToken = line,
    )

    private val via = Via(stop("V"), 10)
    private val heads = listOf(ride("H1", "A", "V", 0, 10), ride("H2", "A", "V", 10, 20))
    private val tails = listOf(15L, 25L, 35L).map { ride("T$it", "V", "Z", it, it + 10) }

    private fun lines(journeys: List<Journey>) = journeys.map { journey -> journey.legs.map { it.line } }

    @Test
    fun leavingAtATime() = runBlocking {
        val found = composeVia(stop("A"), stop("Z"), via, 10, at(0), null) { _, end, leaves, _ ->
            if (end.name == "Stop V") heads else tails.filter { leaves == null || !it.departure.isBefore(leaves) }
        }
        // Each way to the place gets the first way on after the stay of ten minutes.
        assertEquals(listOf(listOf("H1", "T25"), listOf("H2", "T35")), lines(found))
        assertEquals(1, found[0].via?.afterLegs)
        assertEquals("H1", found[0].split?.headToken)
        assertEquals("T25", found[0].split?.tailToken)
    }

    @Test
    fun arrivingByATime() = runBlocking {
        val found = composeVia(stop("A"), stop("Z"), via, 10, null, at(46)) { _, end, _, arrives ->
            if (end.name == "Stop V") heads.filter { !it.arrival.isAfter(arrives) } else tails.filter { !it.arrival.isAfter(arrives) }
        }
        assertEquals(listOf(listOf("H1", "T25"), listOf("H2", "T35")), lines(found))
    }

    @Test
    fun placeOnTheWayAhead() = runBlocking {
        val journey = composeVia(stop("A"), stop("Z"), via, 10, at(0), null) { _, end, _, _ ->
            if (end.name == "Stop V") heads.take(1) else tails
        }.first()
        assertEquals(via, viaAhead(journey, emptyList()))
        assertNull(viaAhead(journey, journey.legs.take(1)))
        // Swapped from the start on, a way that passes the place itself brings its own.
        assertEquals(1, spliceJourney(journey, 0, journey).via?.afterLegs)
        assertNull(spliceJourney(journey, 0, tails[0]).via)
    }
}
