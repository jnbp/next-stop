package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sqrt

class OwnMotionTest {
    private val baseLat = 52.40
    private val baseLon = 13.20
    private val perDegree = 111_320.0
    private val eastPerDegree = perDegree * cos(Math.toRadians(baseLat))

    // A place [east] and [north] metres from the base.
    private fun latOf(north: Double) = baseLat + north / perDegree
    private fun lonOf(east: Double) = baseLon + east / eastPerDegree
    private fun eastOf(motion: OwnMotion) = (motion.lon - baseLon) * eastPerDegree
    private fun northOf(motion: OwnMotion) = (motion.lat - baseLat) * perDegree

    private fun fixAt(motion: OwnMotion, east: Double, north: Double, at: Long, speed: Double = Double.NaN, bearing: Double = Double.NaN, accuracy: Double = 5.0) {
        motion.fix(latOf(north), lonOf(east), accuracy, speed, bearing, at, at)
    }

    @Test
    fun trainAtSpeedMovesEveryFrameAndNeverBack() {
        val motion = OwnMotion()
        val speed = 20.0
        var lastEast = Double.NaN
        var largestStep = 0.0
        var smallestStep = Double.MAX_VALUE
        var t = 0L
        var nextFix = 0L
        while (t <= 20_000L) {
            if (t >= nextFix) {
                fixAt(motion, speed * nextFix / 1000.0, 0.0, nextFix, speed, 90.0)
                nextFix += 1_000L
            }
            motion.evaluate(t)
            val east = eastOf(motion)
            if (!lastEast.isNaN() && t > 2_000L) {
                val step = east - lastEast
                largestStep = maxOf(largestStep, step)
                smallestStep = minOf(smallestStep, step)
            }
            lastEast = east
            t += 16L
        }
        // About 0.32 m a frame, with no stop and no leap.
        assertTrue("no frame stands still: $smallestStep", smallestStep > 0.2)
        assertTrue("no frame leaps: $largestStep", largestStep < 0.5)
        assertEquals(speed * 20.0, eastOf(motion), 2.0)
    }

    @Test
    fun speedFromTwoFixesWhenTheDeviceGivesNone() {
        val motion = OwnMotion()
        for (i in 0..5) fixAt(motion, 15.0 * i, 0.0, i * 1_000L)
        motion.evaluate(5_500L)
        // Moves on between the fixes instead of standing at the last one.
        assertTrue(eastOf(motion) > 75.0 + 4.0)
    }

    @Test
    fun predictionEndsAfterTheHorizon() {
        val motion = OwnMotion()
        fixAt(motion, 0.0, 0.0, 0L, 10.0, 0.0)
        fixAt(motion, 0.0, 10.0, 1_000L, 10.0, 0.0)
        motion.evaluate(4_000L)
        val at4 = northOf(motion)
        motion.evaluate(9_000L)
        assertEquals(at4, northOf(motion), 0.01)
        assertTrue(motion.resting(9_000L))
        // 1.5 times the interval of one second at most beyond the fix.
        assertTrue("stopped at $at4", at4 <= 10.0 + 10.0 * 1.5 + 0.5)
    }

    @Test
    fun jitterOfAStandingTravellerMovesNothing() {
        val motion = OwnMotion()
        fixAt(motion, 0.0, 0.0, 0L, 0.0, accuracy = 12.0)
        val wobble = doubleArrayOf(4.0, -6.0, 7.5, -3.0, 5.0, -8.0, 2.0)
        for ((i, w) in wobble.withIndex()) {
            val at = (i + 1) * 1_000L
            fixAt(motion, w, -w / 2, at, 0.3, accuracy = 12.0)
            motion.evaluate(at + 900L)
            assertTrue(sqrt(eastOf(motion) * eastOf(motion) + northOf(motion) * northOf(motion)) < 0.5)
        }
    }

    @Test
    fun aRealStepOfAStandingTravellerIsFollowed() {
        val motion = OwnMotion()
        fixAt(motion, 0.0, 0.0, 0L, 0.0, accuracy = 5.0)
        fixAt(motion, 40.0, 0.0, 1_000L, 0.0, accuracy = 5.0)
        motion.evaluate(3_000L)
        assertEquals(40.0, eastOf(motion), 0.2)
        assertTrue(motion.resting(3_000L))
    }

    @Test
    fun aLaggingFixDoesNotPullTheDotBack() {
        val motion = OwnMotion()
        for (i in 0..3) fixAt(motion, 20.0 * i, 0.0, i * 1_000L, 20.0, 90.0)
        motion.evaluate(4_400L)
        val before = eastOf(motion)
        // The next fix lags 15 m behind where the dot is.
        fixAt(motion, before - 15.0, 0.0, 4_400L, 20.0, 90.0)
        var last = before
        var t = 4_400L
        while (t < 7_000L) {
            t += 16L
            motion.evaluate(t)
            assertTrue("back at $t", eastOf(motion) >= last - 1e-6)
            last = eastOf(motion)
        }
    }

    @Test
    fun aFarFixIsAJump() {
        val motion = OwnMotion()
        fixAt(motion, 0.0, 0.0, 0L, 0.0)
        fixAt(motion, 5_000.0, 0.0, 1_000L, 0.0)
        motion.evaluate(1_016L)
        assertEquals(5_000.0, eastOf(motion), 0.01)
    }

    @Test
    fun withoutPredictionItEasesToEachFixAndRests() {
        val motion = OwnMotion()
        motion.extrapolate = false
        fixAt(motion, 0.0, 0.0, 0L, 20.0, 90.0)
        fixAt(motion, 60.0, 0.0, 3_000L, 20.0, 90.0)
        motion.evaluate(4_500L)
        assertEquals(60.0, eastOf(motion), 0.1)
        motion.evaluate(6_000L)
        assertEquals(60.0, eastOf(motion), 0.01)
        assertTrue(motion.resting(6_000L))
    }

    @Test
    fun withoutAnimationsTheDotIsAtTheFix() {
        val motion = OwnMotion()
        motion.smooth = false
        fixAt(motion, 0.0, 0.0, 0L, 20.0, 90.0)
        fixAt(motion, 30.0, 0.0, 1_000L, 20.0, 90.0)
        motion.evaluate(1_000L)
        assertEquals(30.0, eastOf(motion), 0.01)
        assertTrue(motion.resting(1_000L))
    }

    @Test
    fun theDotFollowsTheWayOfTheRideRoundACorner() {
        val motion = OwnMotion()
        // 600 m east, then 600 m north.
        val lats = doubleArrayOf(latOf(0.0), latOf(0.0), latOf(600.0))
        val lons = doubleArrayOf(lonOf(0.0), lonOf(600.0), lonOf(600.0))
        motion.setPath(lats, lons)
        // Fixes that cut the corner, 30 m inside of it.
        val fixes = listOf(400.0 to 0.0, 500.0 to 10.0, 570.0 to 30.0, 590.0 to 110.0, 600.0 to 200.0)
        var t = 0L
        fixes.forEachIndexed { i, (east, north) -> fixAt(motion, east, north, i * 2_000L, 15.0, Double.NaN, 8.0) }
        // Again, with frames in between: every shown place lies on the way and goes forward.
        val again = OwnMotion()
        again.setPath(lats, lons)
        var along = -1.0
        var index = 0
        while (t <= 9_000L) {
            if (index < fixes.size && t >= index * 2_000L) {
                val (east, north) = fixes[index]
                fixAt(again, east, north, t, 15.0, Double.NaN, 8.0)
                index++
            }
            again.evaluate(t)
            val east = eastOf(again)
            val north = northOf(again)
            val offWay = if (east < 599.9) kotlin.math.abs(north) else kotlin.math.abs(east - 600.0)
            assertTrue("off the way at $t: $east/$north", offWay < 0.3)
            val now = if (east < 599.9) east else 600.0 + north
            if (index > 1) assertTrue("back at $t", now >= along - 1e-6)
            along = now
            t += 16L
        }
        assertTrue(again.onPath)
        assertFalse(motion.lat.isNaN())
    }

    @Test
    fun aFixFarFromTheWayIsShownWhereItIs() {
        val motion = OwnMotion()
        motion.setPath(doubleArrayOf(latOf(0.0), latOf(0.0)), doubleArrayOf(lonOf(0.0), lonOf(1_000.0)))
        fixAt(motion, 100.0, 0.0, 0L, 0.0)
        fixAt(motion, 100.0, 400.0, 1_000L, 0.0)
        motion.evaluate(4_000L)
        assertFalse(motion.onPath)
        assertEquals(400.0, northOf(motion), 0.5)
    }

    @Test
    fun accuracyCircleEasesToANewRadius() {
        val motion = OwnMotion()
        fixAt(motion, 0.0, 0.0, 0L, 0.0, accuracy = 40.0)
        fixAt(motion, 1.0, 0.0, 1_000L, 0.0, accuracy = 8.0)
        motion.evaluate(1_100L)
        assertTrue(motion.accuracy in 9.0..39.0)
        motion.evaluate(4_000L)
        assertEquals(8.0, motion.accuracy, 0.5)
    }

    @Test
    fun aSmallChangeOfTheAccuracyIsTakenAtOnceWithoutFrames() {
        val motion = OwnMotion()
        fixAt(motion, 0.0, 0.0, 0L, 0.0, accuracy = 20.0)
        motion.evaluate(2_000L)
        // 20 % smaller: past the gate, under the snap share: the circle takes it at once and rests.
        fixAt(motion, 0.5, 0.0, 2_000L, 0.0, accuracy = 16.0)
        motion.evaluate(2_016L)
        assertEquals(16.0, motion.accuracy, 1e-9)
        assertTrue(motion.resting(2_016L))
    }

    @Test
    fun aGuessOfTheTimetableEasesThereAndRests() {
        val motion = OwnMotion()
        motion.ease(latOf(0.0), lonOf(0.0), 0L)
        motion.ease(latOf(0.0), lonOf(100.0), 1_000L)
        motion.evaluate(1_100L)
        assertTrue(eastOf(motion) in 1.0..99.0)
        motion.evaluate(3_000L)
        assertEquals(100.0, eastOf(motion), 0.1)
        assertTrue(motion.resting(3_000L))
        assertEquals(0.0, motion.accuracy, 0.01)
    }

    @Test
    fun aTrainThatBrakesToAStopNeverGoesBackAndStopsAtItsPlace() {
        val motion = OwnMotion()
        // 20 m/s braking to a stop over ten seconds; each fix half a second old when it comes in.
        var east = 0.0
        var last = Double.NaN
        var t = 0L
        var nextFix = 0L
        while (t <= 16_000L) {
            if (t >= nextFix) {
                val second = nextFix / 1000.0
                val speed = maxOf(0.0, 20.0 - 2.0 * second)
                east = if (second <= 10.0) 20.0 * second - second * second else 100.0
                motion.fix(latOf(0.0), lonOf(east), 5.0, speed, 90.0, nextFix - 500L, t)
                nextFix += 1_000L
            }
            motion.evaluate(t)
            if (!last.isNaN()) assertTrue("back at $t", eastOf(motion) >= last - 1e-6)
            last = eastOf(motion)
            t += 16L
        }
        assertEquals(100.0, eastOf(motion), 6.0)
        assertTrue(motion.resting(t))
    }

    @Test
    fun noisyFixesOfARideStillMoveSmoothlyForward() {
        val motion = OwnMotion()
        val random = java.util.Random(7)
        var last = Double.NaN
        var largestStep = 0.0
        var t = 0L
        var nextFix = 0L
        while (t <= 30_000L) {
            if (t >= nextFix) {
                val truth = 15.0 * nextFix / 1000.0
                val east = truth + random.nextGaussian() * 4.0
                val north = random.nextGaussian() * 4.0
                val speed = 15.0 + random.nextGaussian()
                motion.fix(latOf(north), lonOf(east), 8.0, speed, 90.0 + random.nextGaussian() * 3.0, nextFix - 300L, t)
                nextFix += 900L + random.nextInt(300)
            }
            motion.evaluate(t)
            if (!last.isNaN()) {
                assertTrue("back at $t", eastOf(motion) >= last - 1e-6)
                if (t > 2_000L) largestStep = maxOf(largestStep, eastOf(motion) - last)
            }
            last = eastOf(motion)
            t += 16L
        }
        // 0.24 m a frame on average; a correction adds little to a frame.
        assertTrue("leap $largestStep", largestStep < 0.8)
        assertEquals(15.0 * 30.0, eastOf(motion), 25.0)
    }

    @Test
    fun aStandingTravellerWithAWobblingAccuracyNeedsNoFrames() {
        val motion = OwnMotion()
        fixAt(motion, 0.0, 0.0, 0L, 0.0, accuracy = 12.0)
        motion.evaluate(2_000L)
        fixAt(motion, 1.0, 0.0, 2_000L, 0.0, accuracy = 13.0)
        motion.evaluate(2_016L)
        assertTrue(motion.resting(2_016L))
    }

    @Test
    fun theGlideToAGuessFollowsTheAnimatorDurationScale() {
        val normal = OwnMotion()
        val slow = OwnMotion()
        slow.durationScale = 10f
        for (motion in listOf(normal, slow)) {
            motion.ease(latOf(0.0), lonOf(0.0), 0L)
            motion.ease(latOf(0.0), lonOf(100.0), 1_000L)
        }
        normal.evaluate(1_100L)
        slow.evaluate(2_000L)
        // Ten times as long: after ten times the time it has come as far.
        assertEquals(eastOf(normal), eastOf(slow), 0.01)
        slow.evaluate(3_000L)
        assertFalse(slow.resting(3_000L))
        slow.evaluate(21_000L)
        assertEquals(100.0, eastOf(slow), 0.1)
        assertTrue(slow.resting(21_000L))
    }
}
