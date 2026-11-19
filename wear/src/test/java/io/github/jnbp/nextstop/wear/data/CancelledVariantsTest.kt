package io.github.jnbp.nextstop.wear.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** #233 on the watch: the same train shown once, running, not beside a cancelled way to it. Made-up stations only. */
class CancelledVariantsTest {
    private val minute = 60_000L
    private val day = 1_000_000 * minute
    private fun at(minutes: Long) = day + minutes * minute

    private fun leg(line: String?, product: String, from: String, to: String, leaves: Long, arrives: Long, cancelled: Boolean = false) =
        Leg(line, product, line == null, from, to, at(leaves), at(arrives), 0, 0, null, null, cancelled)

    private fun ice(from: String, leaves: Long, cancelled: Boolean = false) =
        leg("ICE 277", "nationalExpress", from, "Zielstadt Bahnhof", leaves, 480, cancelled)

    private fun walk(from: String, to: String, leaves: Long, arrives: Long) = leg(null, "walking", from, to, leaves, arrives)

    private val viaHbf = Journey(
        listOf(
            walk("Example Straße", "Example Straße", 17, 20),
            leg("N9", "bus", "Example Straße", "Example Hbf", 20, 35),
            ice("Example Hbf", 40),
            walk("Zielstadt Bahnhof", "Zielstadt Markt", 480, 481),
        ),
        null,
    )

    private val viaSuedSkipped = Journey(
        listOf(
            walk("Example Straße", "Example Straße", 23, 26),
            leg("N7", "bus", "Example Straße", "Example Süd", 26, 40),
            ice("Example Süd", 48, cancelled = true),
            walk("Zielstadt Bahnhof", "Zielstadt Markt", 480, 481),
        ),
        null,
    )

    @Test
    fun aVariantCancelledOnlyAtItsBoardingStopGoesBesideTheRunningOne() {
        assertEquals(listOf(viaHbf), withoutCancelledVariants(listOf(viaHbf, viaSuedSkipped)))
        assertEquals(listOf(viaHbf), withoutCancelledVariants(listOf(viaSuedSkipped, viaHbf)))
    }

    @Test
    fun aVariantWhoseFeederIsCancelledGoesToo() {
        val feederCancelled = Journey(
            listOf(
                walk("Example Straße", "Example Straße", 23, 26),
                leg("N7", "bus", "Example Straße", "Example Süd", 26, 40, cancelled = true),
                ice("Example Süd", 48),
                walk("Zielstadt Bahnhof", "Zielstadt Markt", 480, 481),
            ),
            null,
        )
        assertEquals(listOf(viaHbf), withoutCancelledVariants(listOf(viaHbf, feederCancelled)))
    }

    @Test
    fun aDelayedRunningTrainIsStillTheSameTrainByItsPlannedArrival() {
        // Live: the running ICE is 6 minutes late; planned it arrives at the same minute.
        val late = Journey(viaHbf.legs.map { if (it.line == "ICE 277") it.copy(arrival = it.arrival + 6 * minute, arrivalDelayMinutes = 6) else it }, null)
        val skipped = Journey(viaSuedSkipped.legs.dropLast(1) + walk("Zielstadt Bahnhof", "Zielstadt Markt", 486, 487), null)
        assertEquals(listOf(late), withoutCancelledVariants(listOf(late, skipped)))
    }

    @Test
    fun aWholeCancelledTrainStaysVisible() {
        val hbfCancelled = Journey(viaHbf.legs.map { if (it.line == "ICE 277") it.copy(cancelled = true) else it }, null)
        val both = listOf(hbfCancelled, viaSuedSkipped)
        assertEquals(both, withoutCancelledVariants(both))
    }

    @Test
    fun aCancelledVariantThatArrivedEarlierStays() {
        val laterRunning = Journey(viaHbf.legs.dropLast(1) + walk("Zielstadt Bahnhof", "Zielstadt Markt", 480, 495), null)
        val both = listOf(laterRunning, viaSuedSkipped)
        assertEquals(both, withoutCancelledVariants(both))
    }

    @Test
    fun anotherTrainNeverStandsIn() {
        val otherTrain = Journey(
            listOf(
                walk("Example Straße", "Example Straße", 17, 20),
                leg("N9", "bus", "Example Straße", "Example Hbf", 20, 35),
                leg("ICE 593", "nationalExpress", "Example Hbf", "Zielstadt Bahnhof", 38, 470),
                walk("Zielstadt Bahnhof", "Zielstadt Markt", 470, 471),
            ),
            null,
        )
        val both = listOf(otherTrain, viaSuedSkipped)
        assertEquals(both, withoutCancelledVariants(both))
    }

    @Test
    fun aRunningVariantLeavingTheTrainElsewhereIsNoStandIn() {
        val shortRide = Journey(
            listOf(
                walk("Example Straße", "Example Straße", 17, 20),
                leg("N9", "bus", "Example Straße", "Example Hbf", 20, 35),
                leg("ICE 277", "nationalExpress", "Example Hbf", "Zwischenstadt", 40, 300),
                leg("RE 5", "regional", "Zwischenstadt", "Zielstadt Bahnhof", 310, 470),
                walk("Zielstadt Bahnhof", "Zielstadt Markt", 470, 471),
            ),
            null,
        )
        val both = listOf(shortRide, viaSuedSkipped)
        assertEquals(both, withoutCancelledVariants(both))
    }
}
