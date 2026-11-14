package io.github.jnbp.nextstop.data

import java.io.IOException
import java.time.OffsetDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The fallback between data backends: order, timeouts, resting after failures, the hint. */
class SourceChainTest {
    private var now = 1_000_000L
    private val health = SourceHealth(clock = { now }, failuresToTrip = 2, backoffMillis = 120_000L)
    private val chain = SourceChain(health)
    private val family = DataSource.Bvg
    private val order = listOf(Backend.Vbb, Backend.Bvg, Backend.Pte)

    private fun ask(answers: Map<Backend, () -> String>, calls: MutableList<Backend> = mutableListOf()): String = runBlocking {
        chain.first(family, order, timeoutMillis = 200) { backend ->
            calls.add(backend)
            (answers[backend] ?: { throw IOException("down") }).invoke()
        }
    }

    @Test
    fun firstChoiceAnswersWithoutHint() {
        val calls = mutableListOf<Backend>()
        assertEquals("vbb", ask(mapOf(Backend.Vbb to { "vbb" }), calls))
        assertEquals(listOf(Backend.Vbb), calls)
        assertNull(health.answered.value)
    }

    @Test
    fun failureFallsBackToTheNextAndShowsIt() {
        assertEquals("bvg", ask(mapOf(Backend.Bvg to { "bvg" })))
        assertEquals(Backend.Bvg, health.answered.value)
    }

    @Test
    fun serverErrorFallsBackButNothingFoundDoesNot() {
        assertEquals("bvg", ask(mapOf(Backend.Vbb to { throw HttpStatusException(503) }, Backend.Bvg to { "bvg" })))
        val calls = mutableListOf<Backend>()
        try {
            ask(mapOf(Backend.Vbb to { throw HttpStatusException(404) }, Backend.Bvg to { "bvg" }), calls)
            fail("404 must reach the caller")
        } catch (e: HttpStatusException) {
            assertEquals(404, e.code)
        }
        assertEquals(listOf(Backend.Vbb), calls)
    }

    @Test
    fun unreadableAnswerFallsBack() {
        assertEquals("bvg", ask(mapOf(Backend.Vbb to { throw IllegalStateException("bad json") }, Backend.Bvg to { "bvg" })))
    }

    @Test
    fun notSupportedIsSkippedWithoutCountingAsFailure() {
        val calls = mutableListOf<Backend>()
        repeat(3) { ask(mapOf(Backend.Vbb to { throw NotSupportedException("radar") }, Backend.Bvg to { "bvg" }), calls) }
        // Never rests: asked every time.
        assertEquals(3, calls.count { it == Backend.Vbb })
    }

    @Test
    fun slowBackendIsCutOffAfterItsTime() {
        val answer = runBlocking {
            chain.first(family, order, timeoutMillis = 100) { backend ->
                if (backend == Backend.Vbb) {
                    delay(5_000)
                    "late"
                } else {
                    "bvg"
                }
            }
        }
        assertEquals("bvg", answer)
    }

    @Test
    fun lastBackendIsNotCutOffUnlessAsked() {
        val answer = runBlocking {
            chain.first(family, listOf(Backend.Bvg), timeoutMillis = 50) {
                delay(200)
                "slow but there"
            }
        }
        assertEquals("slow but there", answer)
        try {
            runBlocking {
                chain.first(family, listOf(Backend.Bvg), timeoutMillis = 50, cutLast = true) {
                    delay(2_000)
                    "too late"
                }
            }
            fail("must time out")
        } catch (e: SourceUnreachableException) {
            assertEquals(listOf(Backend.Bvg), e.tried)
        }
    }

    @Test
    fun repeatedFailuresLetABackendRestThenItIsTriedAgain() {
        val calls = mutableListOf<Backend>()
        val bvgOnly = mapOf<Backend, () -> String>(Backend.Bvg to { "bvg" })
        ask(bvgOnly, calls)
        ask(bvgOnly, calls)
        assertTrue(health.resting(Backend.Vbb))
        calls.clear()
        ask(bvgOnly, calls)
        assertEquals(listOf(Backend.Bvg), calls)
        assertEquals(Backend.Bvg, health.answered.value)
        // Two minutes later the first choice is asked again; once it answers the hint goes away.
        now += 120_001L
        calls.clear()
        assertEquals("vbb", ask(mapOf(Backend.Vbb to { "vbb" }, Backend.Bvg to { "bvg" }), calls))
        assertEquals(listOf(Backend.Vbb), calls)
        assertNull(health.answered.value)
    }

    @Test
    fun allFailingNamesEveryBackendTried() {
        try {
            ask(emptyMap())
            fail("must fail")
        } catch (e: SourceUnreachableException) {
            assertEquals(order, e.tried)
            assertEquals(DataSource.Bvg, e.source)
        }
    }

    @Test
    fun allRestingAreAskedAnyway() {
        repeat(2) { runCatching { ask(emptyMap()) } }
        assertTrue(order.all { health.resting(it) })
        assertEquals("pte", ask(mapOf(Backend.Pte to { "pte" })))
    }

    @Test
    fun vbbProductMaskHasAClassPerProduct() {
        assertEquals(1 + 2, productMask(setOf("suburban", "subway")))
        assertEquals(64, productMask(setOf("regional")))
        assertEquals(32 + 64, productMask(setOf("regional", "express")))
        // No class for coaches: they come as buses.
        assertEquals(8, productMask(setOf("coach")))
        assertEquals(setOf("bus", "tram"), productsOfMask(12))
        assertEquals("express", productOfClass(32))
    }

    @Test
    fun familyTagsTripsAndSendsThemBackToTheirBackend() = runBlocking {
        val vbb = FakeSource("v")
        val bvg = FakeSource("b")
        val source = FamilySource(DataSource.Bvg, { listOf(Backend.Vbb to vbb, Backend.Bvg to bvg) }, chain)
        val departures = source.departures("900100003")
        assertEquals("vbb~v-trip", departures.single().tripId)
        assertEquals("vbb~v-trip", source.tripCourse("vbb~v-trip").tripId)
        assertEquals("v-trip", vbb.lastTrip)
        // BVG has no tag, as before.
        vbb.down = true
        assertEquals("b-trip", source.departures("900100003").single().tripId)
        source.tripCourse("b-trip")
        assertEquals("b-trip", bvg.lastTrip)
    }

    @Test
    fun pointOfInterestOfAnotherBackendGoesByPosition() = runBlocking {
        val vbb = FakeSource("v")
        val bvg = FakeSource("b")
        val source = FamilySource(DataSource.Bvg, { listOf(Backend.Vbb to vbb, Backend.Bvg to bvg) }, chain)
        val poi = Stop("pte~123", "Museum", 52.5, 13.4, null, kind = PlaceKind.Poi)
        val stop = Stop("900100003", "Alexanderplatz", 52.52, 13.41, null)
        source.journeys(poi, stop, SearchOptions())
        assertEquals(PlaceKind.Address, vbb.lastFrom?.kind)
        assertEquals("900100003", vbb.lastTo?.id)
        val own = poi.copy(id = "vbb~123")
        source.journeys(own, stop, SearchOptions())
        assertEquals("123", vbb.lastFrom?.id)
        assertEquals(PlaceKind.Poi, vbb.lastFrom?.kind)
    }

    @Test
    fun backendsWithoutKeyAreSkippedUntilOneIsEntered() {
        val choice = SourceChoice()
        assertEquals(listOf(Backend.Bvg, Backend.Transitous), choice.inUse)
        val withKeys = choice.copy(vbbAccessId = "key", pteAuthorization = "{}")
        // public-transport-enabler only in builds that include it (nextstop.pte).
        val expected = listOf(Backend.Vbb, Backend.Bvg, Backend.Pte, Backend.Transitous)
            .filter { it != Backend.Pte || PteSource.INCLUDED }
        assertEquals(expected, withKeys.inUse)
        // The keys never show in a log line.
        assertTrue("key" !in withKeys.toString().substringAfter("vbbKey="))
    }

    @Test
    fun userOrderDecidesTheFamilyAndUnknownBackendsGoLast() {
        val choice = SourceChoice(order = listOf(Backend.Transitous, Backend.Bvg))
        assertEquals(listOf(Backend.Transitous, Backend.Bvg, Backend.Vbb, Backend.Pte), choice.ordered)
        assertEquals(listOf(DataSource.Transitous, DataSource.Bvg), choice.active)
        val berlinOnly = SourceChoice(enabled = setOf(Backend.Bvg))
        assertEquals(listOf(DataSource.Bvg), berlinOnly.active)
        // Nothing usable: Transitous, so the app is never left without a source.
        assertEquals(listOf(DataSource.Transitous), SourceChoice(enabled = setOf(Backend.Vbb)).active)
    }

    @Test
    fun taggedValuesFindTheirBackend() {
        assertEquals(Backend.Vbb, backendOf(DataSource.Bvg, "vbb~1|2"))
        assertEquals(Backend.Pte, backendOf(DataSource.Bvg, "pte~{}"))
        assertEquals(Backend.Bvg, backendOf(DataSource.Bvg, "1|62785|0|86|13112026"))
        assertEquals(Backend.Transitous, backendOf(DataSource.Transitous, "abc"))
    }

    @Test
    fun familyFallsBackForDeparturesAndRecordsWhoAnswered() = runBlocking {
        val vbb = FakeSource("v").apply { down = true }
        val bvg = FakeSource("b")
        val source = FamilySource(DataSource.Bvg, { listOf(Backend.Vbb to vbb, Backend.Bvg to bvg) }, chain)
        assertEquals("b-trip", source.departures("900100003").single().tripId)
        assertEquals(Backend.Bvg, health.answered.value)
        vbb.down = false
        assertEquals("vbb~v-trip", source.departures("900100003").single().tripId)
        assertNull(health.answered.value)
    }

    @Test
    fun noRadarAnywhereMeansNoVehiclesNotAnError() = runBlocking {
        val noRadar = object : TransitSource by FakeSource("v") {
            override suspend fun vehicles(
                north: Double,
                west: Double,
                south: Double,
                east: Double,
                results: Int,
                products: Set<String>?,
            ): List<Vehicle> = throw NotSupportedException("radar")
        }
        val source = FamilySource(DataSource.Bvg, { listOf(Backend.Vbb to noRadar) }, chain)
        assertTrue(source.vehicles(52.6, 13.2, 52.4, 13.6).isEmpty())
    }

    /** A backend with one departure and one journey, or none at all while [down]. */
    private class FakeSource(private val name: String) : TransitSource {
        var down = false
        var lastTrip: String? = null
        var lastFrom: Stop? = null
        var lastTo: Stop? = null

        private fun check() {
            if (down) throw IOException("$name down")
        }

        override suspend fun departures(stopId: String, results: Int, products: Set<String>, time: OffsetDateTime?): List<Departure> {
            check()
            return listOf(Departure("U2", "subway", "Pankow", null, null, null, null, false, tripId = "$name-trip"))
        }

        override suspend fun tripCourse(tripId: String): TripCourse {
            check()
            lastTrip = tripId
            return TripCourse(tripId, "U2", "subway", "Pankow", emptyList(), emptyList())
        }

        override suspend fun journeys(
            from: Stop,
            to: Stop,
            options: SearchOptions,
            results: Int,
            departure: OffsetDateTime?,
            arrival: OffsetDateTime?,
        ): JourneyPage {
            check()
            lastFrom = from
            lastTo = to
            return JourneyPage(emptyList(), null, null)
        }

        override suspend fun nearbyStops(latitude: Double, longitude: Double, results: Int): List<Stop> = TODO()
        override suspend fun nearestPlace(latitude: Double, longitude: Double): Stop? = TODO()
        override suspend fun searchStops(query: String, results: Int): List<Stop> = TODO()
        override suspend fun vehicles(
            north: Double,
            west: Double,
            south: Double,
            east: Double,
            results: Int,
            products: Set<String>?,
        ): List<Vehicle> = TODO()
        override suspend fun tripPath(tripId: String): List<Pair<Double, Double>> = TODO()
        override suspend fun tripStopovers(tripId: String): List<Stopover> = TODO()
        override suspend fun arrivals(stopId: String, products: Set<String>, time: OffsetDateTime, durationMinutes: Int): List<Arrival> = TODO()
        override suspend fun refreshJourney(refreshToken: String, stopovers: Boolean, polylines: Boolean): Journey? = TODO()
        override suspend fun earlierJourneys(from: Stop, to: Stop, options: SearchOptions, earlierRef: String, results: Int): JourneyPage = TODO()
        override suspend fun laterJourneys(from: Stop, to: Stop, options: SearchOptions, laterRef: String, results: Int): JourneyPage = TODO()
    }
}
