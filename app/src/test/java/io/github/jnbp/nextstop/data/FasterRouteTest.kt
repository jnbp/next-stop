package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Where a trip can be planned anew on the way, and which faster way counts. Made-up stops only. */
class FasterRouteTest {
    private val start: OffsetDateTime = OffsetDateTime.parse("2026-01-01T10:00:00+01:00")
    private fun at(minutes: Long) = start.plusMinutes(minutes)
    private fun stop(name: String) = Stop("id$name", "Stop $name", 52.0 + name.length * 0.001, 13.0, null)
    private fun stopover(name: String, minutes: Long) = Stopover("Stop $name", at(minutes), at(minutes), stop(name), 60)

    private fun ride(line: String, from: String, to: String, leaves: Long, arrives: Long, trip: String, stops: List<Stopover> = emptyList()) =
        Leg(
            line, "subway", false, "Stop $from", "Stop $to", at(leaves), at(leaves), at(arrives), at(arrives),
            null, null, null, false, fromPlace = stop(from), toPlace = stop(to), tripId = trip, stopovers = stops,
        )

    private fun walk(from: String, to: String, leaves: Long, arrives: Long) =
        Leg(
            null, "walking", true, "Stop $from", "Stop $to", at(leaves), null, at(arrives), null,
            null, null, null, false, fromPlace = stop(from), toPlace = stop(to),
        )

    private val journey = Journey(
        listOf(
            walk("H", "A", -5, 0),
            ride("U1", "A", "D", 0, 10, "t1", listOf(stopover("A", 0), stopover("B", 3), stopover("C", 6), stopover("D", 10))),
            ride("S1", "D", "Z", 15, 30, "t2"),
        ),
        refreshToken = "token",
    )

    @Test
    fun pointsOnTheWay() {
        val walking = replanPoint(journey, at(-3))!!
        assertEquals("Stop H", walking.place.name)
        assertTrue(walking.kept.isEmpty())
        // A stop reached within half a minute is passed: the next one counts.
        val onBoard = replanPoint(journey, at(2).plusSeconds(45))!!
        assertTrue(onBoard.cut && onBoard.onBoard)
        assertEquals("Stop C", onBoard.place.name)
        assertEquals(at(6), onBoard.kept.last().arrival)
        assertEquals(at(6).minusSeconds(60), onBoard.kept.last().plannedArrival)
        assertEquals("Stop D", replanPoint(journey, at(7))!!.place.name)
        val waiting = replanPoint(journey, at(12))!!
        assertEquals("wait2", waiting.stage)
        assertNull(replanPoint(journey, at(31)))
    }

    @Test
    fun fasterWays() {
        val point = replanPoint(journey, at(1))!!
        val faster = Journey(listOf(ride("S41", "B", "Z", 4, 29, "t3")), refreshToken = "other")
        val same = Journey(listOf(ride("S42", "B", "Z", 4, 30, "t4")))
        val noise = Journey(listOf(ride("S43", "B", "Z", 4, 29, "t5").copy(arrival = at(29).plusSeconds(30))))
        val best = fasterOption(journey, point, listOf(same, faster, noise), emptyMap())
        assertSame(faster, best?.first)
        assertEquals(60L, best?.second)
        // Put aside, it comes back only with another minute.
        assertNull(fasterOption(journey, point, listOf(faster), mapOf(journeyKey(faster) to 60L)))
        val sooner = Journey(listOf(ride("S41", "B", "Z", 4, 28, "t3")))
        assertNotNull(fasterOption(journey, point, listOf(sooner), mapOf(journeyKey(sooner) to 60L)))
        // A way that leaves before the traveller is there does not count.
        assertNull(fasterOption(journey, point, listOf(Journey(listOf(ride("S41", "B", "Z", 0, 20, "t6")))), emptyMap()))
        val taken = spliceAt(journey, point, faster)
        assertEquals("Stop B", taken.legs[1].to)
        assertEquals("Stop B", taken.split?.headCut)
        assertEquals("token", taken.split?.headToken)
        assertEquals(at(29), taken.arrival)
        assertEquals("S41", changeRide(point.kept, point.cut, faster)?.line)
        assertEquals("Stop B", spliceJourney(taken, 2, faster).split?.headCut)
        assertNull(spliceJourney(taken, 1, faster).split?.headCut)
    }
}
