package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The transfer levels by walking pace (see WalkPace.kt), with a made-up station of three
 * platforms (numbers as a real one in transfer-walks-berlin.json has them): platform 0 is the
 * S2, 1 the U7, 2 the S1; U7 to S1 184 s, S2 to S1 292 s.
 */
class WalkPaceTest {
    // The pace and the walk times are global: each test that sets them puts them back itself
    // (see withPace and rated), so nothing leaks into the next test or another test class even
    // where a runner skips @After; this stays as a second net.
    @After
    fun usualPace() {
        walkSpeedFactor = 1.0
        transferWalkSeconds = { _, _ -> null }
    }

    private val s2 = doubleArrayOf(52.520456, 13.412009, 52.522134, 13.412136)
    private val u7 = doubleArrayOf(52.522973, 13.41006, 52.522993, 13.411589)
    private val s1 = doubleArrayOf(52.521611, 13.407295, 52.522829, 13.40831)
    private val junction = TransferWalkStation(
        "900000001", "Example Junction", listOf(s2, u7, s1), emptyList(),
        mapOf(
            TransferWalkStation.pairKey(0, 1) to TransferWalk(160, null, false),
            TransferWalkStation.pairKey(0, 2) to TransferWalk(292, null, false),
            TransferWalkStation.pairKey(1, 2) to TransferWalk(184, 341, false),
        ),
    )

    private fun end(axis: DoubleArray) = WalkEnd.Platform(axis[0] to axis[1], axis[2] to axis[3])

    private val at = OffsetDateTime.parse("2026-11-14T05:00:00+01:00")
    private val sUJunction = Stop("900000001", "S+U Example Junction", 52.5225, 13.4102, null)
    private val sJunctionS1 = Stop("900000002", "S Example Junction (south)", 52.5222, 13.4078, null)
    private val elsewhere = Stop("900000009", "Elsewhere", 52.55, 13.45, null)
    private val arriving = Leg(
        "U7", "subway", false, elsewhere.name, sUJunction.name, at.minusMinutes(10), null, at, null, null, null, null, false,
        fromPlace = elsewhere, toPlace = sUJunction, tripId = "u7",
    )

    /** The S1 leaving [minutes] after the arrival. */
    private fun s1In(minutes: Long) = Leg(
        "S1", "suburban", false, sJunctionS1.name, elsewhere.name, at.plusMinutes(minutes), null, at.plusMinutes(30), null, "2", null, null, false,
        fromPlace = sJunctionS1, toPlace = elsewhere, tripId = "s1",
    )

    /** The category of a change of [minutes] with a walk of [seconds] measured at the usual pace. */
    private fun rated(seconds: Int, minutes: Long): Tightness {
        val before = transferWalkSeconds
        transferWalkSeconds = { _, _ -> seconds }
        try {
            return changeTime(arriving, s1In(minutes)).tightness
        } finally {
            transferWalkSeconds = before
        }
    }

    /** Runs [block] at the pace [factor], then puts the pace back as it was. */
    private fun withPace(factor: Double, block: () -> Unit) {
        val before = walkSpeedFactor
        walkSpeedFactor = factor
        try {
            block()
        } finally {
            walkSpeedFactor = before
        }
    }

    @Test
    fun theWalksOfTheComplex() {
        assertEquals(184, changeWalkSeconds(junction, listOf(end(u7)), listOf(end(s1)), stepFree = false))
        assertEquals(292, changeWalkSeconds(junction, listOf(end(s2)), listOf(end(s1)), stepFree = false))
    }

    @Test
    fun theNeedsOfAWalkOf292Seconds() {
        // Usual pace: 292 s and half a minute, 6 minutes. Brisk: 292 x 0.8 = 234 s, 4 minutes.
        assertEquals(6L, exactWalkNeed(arriving, s1In(5)) { _, _ -> 292 })
        assertEquals(4L, exactBriskNeed(arriving, s1In(5)) { _, _ -> 292 })
    }

    @Test
    fun levelBoundariesWith292Seconds() {
        // 5 minutes: 1 short at the usual pace, enough at a brisk one: knapp.
        assertEquals(Tightness.Risky, rated(292, 5))
        assertEquals(Tightness.Risky, rated(292, 4))
        // 3 minutes do not work even at a brisk pace: Sprint.
        assertEquals(Tightness.VeryRisky, rated(292, 3))
        // Nothing to spare beyond the 6 needed: knapp; 1: normal; 5: relaxed.
        assertEquals(Tightness.Risky, rated(292, 6))
        assertEquals(Tightness.Tight, rated(292, 7))
        assertEquals(Tightness.Tight, rated(292, 8))
        assertEquals(Tightness.Comfortable, rated(292, 11))
    }

    @Test
    fun levelBoundariesWithTheU7ToTheS1() {
        // 184 s: 4 minutes at the usual pace, 3 at a brisk one.
        assertEquals(Tightness.Risky, rated(184, 4))
        assertEquals(Tightness.Tight, rated(184, 5))
        assertEquals(Tightness.Risky, rated(184, 3))
        assertEquals(Tightness.VeryRisky, rated(184, 2))
    }

    @Test
    fun noChangeOfOneMinuteIsKnapp() {
        // The same platform: 1 minute needed, but 0 or 1 minute is a sprint; 2 leave 1: normal.
        assertEquals(Tightness.VeryRisky, rated(0, 1))
        assertEquals(Tightness.VeryRisky, rated(0, 0))
        assertEquals(Tightness.Tight, rated(0, 2))
        assertEquals(Tightness.Tight, rated(0, 3))
    }

    @Test
    fun slowerOnTheMoveMakesEveryWalkLonger() {
        assertEquals(1.3, walkFactor(slower = true), 1e-9)
        assertEquals(1.0, walkFactor(slower = false), 1e-9)
        withPace(walkFactor(slower = true)) {
            // 292 s x 1.3 = 380 s: 7 minutes at the usual pace, 380 x 0.8 = 304 s: 6 at a brisk one.
            assertEquals(7L, exactWalkNeed(arriving, s1In(5)) { _, _ -> 292 })
            assertEquals(6L, exactBriskNeed(arriving, s1In(5)) { _, _ -> 292 })
            assertEquals(Tightness.VeryRisky, rated(292, 5))
            assertEquals(Tightness.Risky, rated(292, 6))
            assertEquals(Tightness.Tight, rated(292, 9))
        }
        assertEquals(1.0, walkSpeedFactor, 1e-9)
    }

    @Test
    fun secondsAreScaledAndRoundedUp() {
        assertEquals(184, pacedSeconds(184, 1.0))
        assertEquals(240, pacedSeconds(184, 1.3))
        assertEquals(0, pacedSeconds(0, 1.3))
        assertEquals(3L, briskMinutes(184, 1.0))
        assertEquals(2L, briskMinutes(60, 1.0))
    }

    @Test
    fun walksBetweenStopPositionsFollowThePace() {
        // 150 m apart: 195 m on foot, 3 minutes at 75 m a minute; 4 when slower.
        assertEquals(3L, spotWalkMinutes(150.0, 1.0))
        assertEquals(4L, spotWalkMinutes(150.0, 1.3))
        assertEquals(0L, spotWalkMinutes(5.0, 1.3))
    }

    @Test
    fun theProvidersAreAskedForThePace() {
        assertEquals(WalkingSpeed.Normal, SearchOptions().walkingSpeed)
        assertEquals(WalkingSpeed.Slow, SearchOptions(slower = true).walkingSpeed)
        assertEquals(WalkingSpeed.Fast, SearchOptions(brisk = true).walkingSpeed)
        assertEquals(WalkingSpeed.Slow, SearchOptions(slower = true, brisk = true).walkingSpeed)
    }
}
