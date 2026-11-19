package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The course camera rests while the vehicle stays in the inner part of the free room. */
class CourseFollowPanTest {
    // A free room 0..400 × 100..500 dp: 60 % of it is 240 dp, so 80 dp either way of the anchor.
    @Test
    fun aVehicleNearItsAnchorLeavesTheCameraAtRest() {
        assertFalse(followPanDue(200.0, 300.0, 200.0, 300.0, 0.0, 100.0, 400.0, 500.0))
        assertFalse(followPanDue(279.0, 379.0, 200.0, 300.0, 0.0, 100.0, 400.0, 500.0))
    }

    @Test
    fun leavingTheInnerShareInEitherDirectionPans() {
        assertTrue(followPanDue(281.0, 300.0, 200.0, 300.0, 0.0, 100.0, 400.0, 500.0))
        assertTrue(followPanDue(200.0, 219.0, 200.0, 300.0, 0.0, 100.0, 400.0, 500.0))
    }

    @Test
    fun theBoxIsAroundTheAnchorNotTheMiddle() {
        // Framed with its next stops, the vehicle stands near the edge it comes from: no pan right after.
        assertFalse(followPanDue(30.0, 300.0, 30.0, 300.0, 0.0, 100.0, 400.0, 500.0))
        assertTrue(followPanDue(111.0, 300.0, 30.0, 300.0, 0.0, 100.0, 400.0, 500.0))
    }

    @Test
    fun outsideTheFreeRoomAlwaysPansWithoutAnchorNever() {
        assertTrue(followPanDue(-1.0, 300.0, Double.NaN, Double.NaN, 0.0, 100.0, 400.0, 500.0))
        assertTrue(followPanDue(200.0, 501.0, 200.0, 300.0, 0.0, 100.0, 400.0, 500.0))
        assertFalse(followPanDue(390.0, 300.0, Double.NaN, Double.NaN, 0.0, 100.0, 400.0, 500.0))
        assertFalse(followPanDue(Double.NaN, 300.0, 200.0, 300.0, 0.0, 100.0, 400.0, 500.0))
    }
}
