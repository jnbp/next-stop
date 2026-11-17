package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One ride over the seam of a journey put together from two: the S25 from S Lichterfelde Ost
 * at 22:12 to S Südkreuz at 22:21 (Gleis 2) as the end of the first part, and the same S25 from
 * S Südkreuz at 22:21 (Gleis 2) to S+U Yorckstr. at 22:24 as the start of the second, then the
 * U7. The app showed "0 min · Sprint", "Umstiege: 2", the S25 twice in the chart and a picker
 * for connections at a change that is none.
 */
class ThroughRunSeamTest {
    private fun at(clock: String): OffsetDateTime = OffsetDateTime.parse("2026-11-16T$clock:00+01:00")
    private val lichterfelde = Stop("900068201", "S Lichterfelde Ost", 52.4297, 13.3283, null)
    private val suedkreuz = Stop("900058101", "S Südkreuz", 52.4757, 13.3654, null)
    private val yorck = Stop("900057102", "S+U Yorckstr.", 52.4920, 13.3700, null)
    private val end = Stop("900078101", "U Ziel", 52.48, 13.42, null)

    private fun ride(
        line: String,
        product: String,
        from: Stop,
        to: Stop,
        leaves: String,
        arrives: String,
        trip: String?,
        fromPlatform: String? = null,
        toPlatform: String? = null,
    ) = Leg(
        line, product, false, from.name, to.name, at(leaves), at(leaves), at(arrives), at(arrives),
        fromPlatform, toPlatform, "S Hennigsdorf", false, realtime = true,
        fromPlace = from, toPlace = to, tripId = trip,
        stopovers = listOf(
            Stopover(from.name, null, at(leaves), from),
            Stopover(to.name, at(arrives), null, to),
        ),
        path = listOf((from.latitude!! to from.longitude!!), (to.latitude!! to to.longitude!!)),
    )

    private val s25a = ride("S25", "suburban", lichterfelde, suedkreuz, "22:12", "22:21", "s25-1", "1", "2")
    private val s25b = ride("S25", "suburban", suedkreuz, yorck, "22:21", "22:24", "s25-1", "2", "1")
    private val u7 = ride("U7", "subway", yorck, end, "22:29", "22:37", "u7-1")

    @Test
    fun theSameS25OverThePlaceOnTheWayIsOneRide() {
        val head = Journey(listOf(s25a), refreshToken = "head")
        val tail = Journey(listOf(s25b, u7), refreshToken = "tail")
        val joined = joinedVia(head, tail, Via(suedkreuz))
        assertEquals(2, joined.legs.size)
        assertEquals(1, joined.transfers)
        val s25 = joined.legs[0]
        assertEquals(lichterfelde.name, s25.from)
        assertEquals(yorck.name, s25.to)
        assertEquals(at("22:12"), s25.departure)
        assertEquals(at("22:24"), s25.arrival)
        assertEquals("1", s25.departurePlatform)
        assertEquals("1", s25.arrivalPlatform)
        assertEquals(listOf(lichterfelde.name, suedkreuz.name, yorck.name), s25.stopovers.map { it.name })
        assertEquals(3, s25.path.size)
        assertTrue(s25.realtime)
        // The seam lies inside the joined ride; the place on the way stays after it.
        assertEquals(1, joined.split!!.headLegs)
        assertTrue(joined.split!!.tailJoined)
        assertEquals(1, joined.via!!.afterLegs)
        // No change at Südkreuz: the only change is to the U7.
        assertFalse(isThroughRun(joined.legs[0], joined.legs[1]))
    }

    @Test
    fun aSwappedRestThatGoesOnInTheSameS25IsOneRide() {
        val followed = Journey(listOf(s25a, ride("S2", "suburban", suedkreuz, yorck, "22:25", "22:28", "s2-1")), refreshToken = "old")
        val option = Journey(listOf(s25b, u7), refreshToken = "new")
        val spliced = spliceJourney(followed, 1, option)
        assertEquals(listOf("S25", "U7"), spliced.legs.map { it.line })
        assertEquals(1, spliced.transfers)
        assertTrue(spliced.split!!.tailJoined)
    }

    @Test
    fun aChangeToTheNextS25StaysAChange() {
        // Another run of the same line twenty minutes later, after a stop on the way.
        val later = ride("S25", "suburban", suedkreuz, yorck, "22:41", "22:44", "s25-2", "2", "1")
        val head = Journey(listOf(s25a), refreshToken = "head")
        val tail = Journey(listOf(later, u7.copy(departure = at("22:49"), arrival = at("22:57"))), refreshToken = "tail")
        val joined = joinedVia(head, tail, Via(suedkreuz, 20))
        assertEquals(3, joined.legs.size)
        assertEquals(2, joined.transfers)
        assertFalse(joined.split!!.tailJoined)
        assertEquals(1, joined.split!!.headLegs)
        assertFalse(isThroughRun(s25a, later))
    }

    @Test
    fun anotherRunOfTheLineOnAnotherPlatformStaysAChange() {
        val other = ride("S25", "suburban", suedkreuz, yorck, "22:22", "22:25", "s25-3", "4", "1")
        assertFalse(isThroughRun(s25a, other))
        assertEquals(3, mergeThroughRuns(Journey(listOf(s25a, other, u7))).legs.size)
    }

    @Test
    fun aKeptJoinedRideIsCutBackAtTheSeamOnRefresh() {
        // The first part loaded again names no cut stop: its last leg stays the joined S25 as known.
        val known = joinedVia(Journey(listOf(s25a)), Journey(listOf(s25b, u7)), Via(suedkreuz)).legs[0]
        val freshTail = listOf(s25b.copy(arrival = at("22:25")), u7)
        val legs = withoutSeamOverlap(listOf(known), freshTail)
        assertEquals(suedkreuz.name, legs.single().to)
        assertTrue(legs.single().throughRuns.isEmpty())
        val again = mergeThroughRuns(Journey(legs + freshTail, split = JourneySplit(1, null, "tail")))
        assertEquals(listOf("S25", "U7"), again.legs.map { it.line })
        val s25 = again.legs[0]
        // The S25 once: no stop twice, the fresh arrival of the second part.
        assertEquals(listOf(lichterfelde.name, suedkreuz.name, yorck.name), s25.stopovers.map { it.name })
        assertEquals(at("22:25"), s25.arrival)
        assertEquals(listOf("s25-1"), s25.tripIds)
        assertTrue(again.split!!.tailJoined)
    }

    @Test
    fun withoutTheSeamAmongItsStopsTheKeptRideIsCutByTime() {
        val other = s25b.copy(tripId = "s25-1b")
        val known = mergeThroughRuns(Journey(listOf(s25a.copy(stopovers = emptyList()), other.copy(stopovers = emptyList())))).legs.single()
        val legs = withoutSeamOverlap(listOf(known), listOf(other, u7))
        assertEquals(suedkreuz.name, legs.single().to)
        assertEquals(at("22:21"), legs.single().arrival)
        val again = mergeThroughRuns(Journey(legs + listOf(other, u7)))
        assertEquals(listOf("S25", "U7"), again.legs.map { it.line })
        assertEquals(at("22:24"), again.legs[0].arrival)
    }

    @Test
    fun aSwapOnBoardIsRefreshedWithTheSecondPartAlone() = runBlocking {
        // On board the S25, an option that goes on in the same vehicle: the kept part has no handle.
        val followed = Journey(listOf(s25a, ride("S2", "suburban", suedkreuz, yorck, "22:25", "22:28", "s2-1")))
        val swapped = spliceJourney(followed, 1, Journey(listOf(s25b, u7), refreshToken = "tail"))
        assertTrue(swapped.split!!.tailJoined)
        assertNull(swapped.split!!.headToken)
        val freshTail = Journey(listOf(s25b.copy(arrival = at("22:25")), u7.copy(arrival = at("22:39"))), refreshToken = "tail")
        val unused = java.lang.reflect.Proxy.newProxyInstance(
            TransitSource::class.java.classLoader,
            arrayOf(TransitSource::class.java),
        ) { _, method, _ -> error("unexpected ${method.name}") } as TransitSource
        val source = object : TransitSource by unused {
            override suspend fun refreshJourney(refreshToken: String, stopovers: Boolean, polylines: Boolean): Journey? =
                freshTail.takeIf { refreshToken == "tail" }
        }
        val again = source.refreshed(swapped, stopovers = true, polylines = false)!!
        // The live second part, the S25 as one ride: no stop twice, no change at Südkreuz.
        assertEquals(listOf("S25", "U7"), again.legs.map { it.line })
        val s25 = again.legs[0]
        assertEquals(lichterfelde.name, s25.from)
        assertEquals(listOf(lichterfelde.name, suedkreuz.name, yorck.name), s25.stopovers.map { it.name })
        assertEquals(at("22:25"), s25.arrival)
        assertEquals(at("22:39"), again.legs[1].arrival)
        assertTrue(again.split!!.tailJoined)
    }

    @Test
    fun aHeadThatEndsAtTheSeamStaysAsItIs() {
        val head = listOf(s25a)
        assertEquals(head, withoutSeamOverlap(head, listOf(s25b, u7)))
    }
}
