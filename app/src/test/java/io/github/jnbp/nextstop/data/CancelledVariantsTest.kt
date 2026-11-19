package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * #233: the same long-distance train shown twice, once running and once "Fällt aus" (two ways to
 * board it, one of them cancelled at its boarding stop or by its feeder). Made-up stations only.
 */
class CancelledVariantsTest {
    private val day: OffsetDateTime = OffsetDateTime.parse("2026-03-02T04:00:00+01:00")
    private fun at(minutes: Long) = day.plusMinutes(minutes)
    private fun stop(name: String) = Stop("x:" + name, name, 50.0, 10.0, null)

    private val home = stop("Example Straße")
    private val hbf = stop("Example Hbf")
    private val sued = stop("Example Süd")
    private val target = stop("Zielstadt Bahnhof")
    private val end = stop("Zielstadt Markt")

    private fun leg(
        line: String?,
        product: String,
        from: Stop,
        to: Stop,
        leaves: Long,
        arrives: Long,
        trip: String? = null,
        cancelled: Boolean = false,
    ) = Leg(
        line, product, line == null, from.name, to.name, at(leaves), at(leaves), at(arrives), at(arrives),
        null, null, null, cancelled, fromPlace = from, toPlace = to, tripId = trip,
    )

    // The ICE run: Example Hbf 04:40, Example Süd 04:48, Zielstadt Bahnhof 12:00 (planned).
    private fun ice(from: Stop, leaves: Long, cancelled: Boolean = false, trip: String? = "ice277") =
        leg("ICE 277", "express", from, target, leaves, 480, trip, cancelled)

    private fun walkTo(from: Stop, to: Stop, leaves: Long, arrives: Long) = leg(null, "walking", from, to, leaves, arrives)

    // Boards at Example Hbf after a bus, runs.
    private val viaHbf = Journey(
        listOf(
            walkTo(home, home, 17, 20),
            leg("N9", "bus", home, hbf, 20, 35, "bus1"),
            ice(hbf, 40),
            walkTo(target, end, 480, 481),
        ),
    )

    // Boards at Example Süd, where the ICE skips its stop today: the ride is cancelled there.
    private val viaSuedSkipped = Journey(
        listOf(
            walkTo(home, home, 23, 26),
            leg("N7", "bus", home, sued, 26, 40, "bus2"),
            ice(sued, 48, cancelled = true),
            walkTo(target, end, 480, 481),
        ),
    )

    @Test
    fun aVariantCancelledOnlyAtItsBoardingStopGoesBesideTheRunningOne() {
        assertEquals(listOf(viaHbf), withoutCancelledVariants(listOf(viaHbf, viaSuedSkipped)))
        // The order of the list does not matter.
        assertEquals(listOf(viaHbf), withoutCancelledVariants(listOf(viaSuedSkipped, viaHbf)))
    }

    @Test
    fun aVariantWhoseFeederIsCancelledGoesToo() {
        val feederCancelled = Journey(
            listOf(
                walkTo(home, home, 23, 26),
                leg("N7", "bus", home, sued, 26, 40, "bus2", cancelled = true),
                ice(sued, 48),
                walkTo(target, end, 480, 481),
            ),
        )
        assertEquals(listOf(viaHbf), withoutCancelledVariants(listOf(viaHbf, feederCancelled)))
    }

    @Test
    fun theSameTrainByLineAndArrivalWithoutAHandle() {
        val running = Journey(viaHbf.legs.map { if (it.line == "ICE 277") it.copy(tripId = null) else it })
        val skipped = Journey(viaSuedSkipped.legs.map { if (it.line == "ICE 277") it.copy(tripId = null) else it })
        assertEquals(listOf(running), withoutCancelledVariants(listOf(running, skipped)))
    }

    @Test
    fun aWholeCancelledTrainStaysVisible() {
        // The run is cancelled everywhere: both variants are cancelled, both stay (honest status).
        val hbfCancelled = Journey(viaHbf.legs.map { if (it.line == "ICE 277") it.copy(cancelled = true) else it })
        val both = listOf(hbfCancelled, viaSuedSkipped)
        assertEquals(both, withoutCancelledVariants(both))
    }

    @Test
    fun aCancelledVariantThatArrivedEarlierStays() {
        // The running way gets there later than the cancelled one planned to: it is no stand-in.
        val laterRunning = Journey(viaHbf.legs.dropLast(1) + walkTo(target, end, 480, 495))
        val both = listOf(laterRunning, viaSuedSkipped)
        assertEquals(both, withoutCancelledVariants(both))
    }

    @Test
    fun anotherTrainNeverStandsIn() {
        val otherTrain = Journey(
            listOf(
                walkTo(home, home, 17, 20),
                leg("N9", "bus", home, hbf, 20, 35, "bus1"),
                leg("ICE 593", "express", hbf, target, 38, 470, "ice593"),
                walkTo(target, end, 470, 471),
            ),
        )
        val both = listOf(otherTrain, viaSuedSkipped)
        assertEquals(both, withoutCancelledVariants(both))
    }

    @Test
    fun aRunningVariantLeavingTheTrainElsewhereIsNoStandIn() {
        val elsewhere = stop("Zwischenstadt")
        val shortRide = Journey(
            listOf(
                walkTo(home, home, 17, 20),
                leg("N9", "bus", home, hbf, 20, 35, "bus1"),
                leg("ICE 277", "express", hbf, elsewhere, 40, 300, "ice277"),
                leg("RE 5", "regional", elsewhere, target, 310, 470, "re5"),
                walkTo(target, end, 470, 471),
            ),
        )
        val both = listOf(shortRide, viaSuedSkipped)
        assertEquals(both, withoutCancelledVariants(both))
    }

    @Test
    fun aJourneyWithASwappedRestStays() {
        val swapped = viaSuedSkipped.copy(split = JourneySplit(headLegs = 2, headToken = null, tailToken = null))
        val both = listOf(viaHbf, swapped)
        assertEquals(both, withoutCancelledVariants(both))
    }

    @Test
    fun identicalRidesFoundTwiceShowOnceAndRunning() {
        // Case (a)/(c) of #233: the same rides twice, one copy cancelled (stale or partial data):
        // withoutDuplicateRides already keeps the running copy alone.
        val stale = Journey(viaHbf.legs.map { if (it.line == "ICE 277") it.copy(cancelled = true) else it })
        assertEquals(listOf(viaHbf), withoutDuplicateRides(listOf(stale, viaHbf)))
        assertEquals(listOf(viaHbf), withoutDuplicateRides(listOf(viaHbf, stale)))
    }
}
