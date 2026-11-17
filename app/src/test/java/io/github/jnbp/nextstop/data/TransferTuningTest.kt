package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The tunable buffers of the transfer rating (see TransferTuning.kt) and the facts the app explains it with. */
class TransferTuningTest {
    private val start = OffsetDateTime.parse("2026-11-14T10:00:00+01:00")

    private fun ride(from: String, to: String, leaves: Long, arrives: Long) = Leg(
        "U0", "subway", false, from, to, start.plusMinutes(leaves), start.plusMinutes(leaves),
        start.plusMinutes(arrives), start.plusMinutes(arrives), null, null, null, false,
    )

    private fun withTuning(tuning: TransferTuning, block: () -> Unit) {
        val before = currentTransferTuning()
        walkSpeedFactor = 1.0
        setTransferTuning(tuning)
        try {
            block()
        } finally {
            setTransferTuning(before)
        }
    }

    @Test
    fun theDefaultsAreTheFormerConstants() = withTuning(TransferTuning()) {
        assertEquals(TransferTuning(NORMAL_SPARE, RELAXED_SPARE, EXACT_WALK_EXTRA_SECONDS), currentTransferTuning())
        assertEquals(Tightness.Comfortable, spareTightness(5))
        assertEquals(Tightness.Tight, spareTightness(4))
        assertEquals(Tightness.Tight, spareTightness(1))
        assertEquals(Tightness.Risky, spareTightness(0))
        assertEquals(Tightness.VeryRisky, spareTightness(-1))
        // A known walk of 60 s and 30 s buffer: 2 minutes.
        val a = ride("Stop A", "Hub", 0, 10)
        val b = ride("Hub", "Stop B", 14, 20)
        assertEquals(2L, exactWalkNeed(a, b) { _, _ -> 60 })
        // Platform to street: 100 m (104 s) and 60 s: 3 minutes, the stop at the exit 2.
        assertEquals(3L, platformStopMinutes(100.0))
        assertEquals(2L, platformStopMinutes(0.0))
    }

    @Test
    fun changedBuffersChangeTheRating() = withTuning(TransferTuning(normalSpare = 2, relaxedSpare = 8, changeBufferSeconds = 90)) {
        assertEquals(Tightness.Tight, spareTightness(5))
        assertEquals(Tightness.Tight, spareTightness(2))
        assertEquals(Tightness.Risky, spareTightness(1))
        assertEquals(Tightness.Comfortable, spareTightness(8))
        assertEquals(Tightness.Tight, paceTightness(gap = 10, need = 3, briskNeed = 2))
        assertEquals(Tightness.Comfortable, paceTightness(gap = 11, need = 3, briskNeed = 2))
        // 60 s and 90 s: 3 minutes.
        assertEquals(3L, exactWalkNeed(ride("Stop A", "Hub", 0, 10), ride("Hub", "Stop B", 14, 20)) { _, _ -> 60 })
        // 104 s, 90 s and 30 s for the stairs: 4 minutes.
        assertEquals(4L, platformStopMinutes(100.0))
    }

    @Test
    fun storedValuesStayWithinTheirChoices() {
        assertEquals(TransferTuning(3, 3, 30), TransferTuning(9, 2, 20).bounded())
        assertEquals(TransferTuning(0, 10, 120), TransferTuning(-4, 30, 500).bounded())
        assertEquals(60L, TransferTuning(changeBufferSeconds = 55).bounded().changeBufferSeconds)
        withTuning(TransferTuning(9, 2, 20)) {
            assertEquals(TransferTuning(3, 3, 30), currentTransferTuning())
        }
    }

    @Test
    fun theExplanationNamesTheNumbersInUse() {
        val defaults = transferRatingFacts(TransferTuning())
        assertEquals(30L, defaults.changeBufferSeconds)
        assertEquals(60L, defaults.platformStreetSeconds)
        assertEquals(2L, defaults.minExactMinutes)
        assertEquals(listOf(1L, 3L, 5L, 1L), listOf(defaults.samePlatform, defaults.sameStation, defaults.railStation, defaults.walkExtra))
        assertEquals(5L, defaults.relaxedSpare)
        assertEquals(1L, defaults.normalSpare)
        assertEquals(2L, defaults.minKnappMinutes)
        assertEquals(80, defaults.briskPercent)
        val tuned = transferRatingFacts(TransferTuning(normalSpare = 2, relaxedSpare = 7, changeBufferSeconds = 0))
        assertEquals(0L, tuned.changeBufferSeconds)
        assertEquals(30L, tuned.platformStreetSeconds)
        assertEquals(7L, tuned.relaxedSpare)
        assertEquals(2L, tuned.normalSpare)
        // By default the facts follow the tuning in use.
        withTuning(TransferTuning(normalSpare = 3, relaxedSpare = 9, changeBufferSeconds = 120)) {
            assertEquals(150L, transferRatingFacts().platformStreetSeconds)
            assertEquals(9L, transferRatingFacts().relaxedSpare)
        }
    }

    @Test
    fun theNearbyRadiusMovesInStepsAndKeepsTheFormerChoices() {
        assertEquals(listOf(300, 500, 800), listOf(300, 500, 800).map { nearbyRadiusOf(it) })
        assertEquals(NEARBY_RADIUS_MIN, nearbyRadiusOf(0))
        assertEquals(NEARBY_RADIUS_MAX, nearbyRadiusOf(5_000))
        assertEquals(350, nearbyRadiusOf(333))
        assertEquals(300, nearbyRadiusOf(324))
        assertEquals(1_250, nearbyRadiusOf(1_250))
    }

    @Test
    fun everyChangeOfTheRatingSettingsGivesANewVersion() = withTuning(TransferTuning()) {
        val start = ratingSettingsVersion
        // The same values again: nothing to rate afresh.
        setTransferTuning(TransferTuning())
        walkSpeedFactor = 1.0
        assertEquals(start, ratingSettingsVersion)
        setTransferTuning(TransferTuning(normalSpare = 2))
        val tuned = ratingSettingsVersion
        assertTrue(tuned > start)
        walkSpeedFactor = walkFactor(slower = true)
        val slower = ratingSettingsVersion
        assertTrue(slower > tuned)
        walkSpeedFactor = 1.0
        assertTrue(ratingSettingsVersion > slower)
    }
}
