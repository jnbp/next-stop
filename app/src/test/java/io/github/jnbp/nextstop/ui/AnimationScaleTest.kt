package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The system's animator duration scale as arithmetic (see AnimationScale.kt). */
class AnimationScaleTest {
    @Test
    fun durationsScale() {
        assertEquals(300L, scaledMillis(300L, 1f))
        assertEquals(150L, scaledMillis(300L, 0.5f))
        assertEquals(3_000L, scaledMillis(300L, 10f))
        assertEquals(3_000, scaledMillis(300, 10f))
        assertEquals(4_000.0, scaledMillis(400.0, 10f), 1e-9)
    }

    @Test
    fun noAnimationsIsNoTime() {
        assertEquals(0L, scaledMillis(300L, 0f))
        assertEquals(0, scaledMillis(300, 0f))
        assertEquals(0.0, scaledMillis(400.0, 0f), 1e-9)
        assertEquals(0L, scaledMillis(0L, 10f))
        // A tiny scale still waits a little rather than not at all.
        assertEquals(1L, scaledMillis(1L, 0.1f))
    }

    @Test
    fun nonsenseIsNormal() {
        assertEquals(1f, sanitizeAnimatorScale(Float.NaN), 0f)
        assertEquals(1f, sanitizeAnimatorScale(-2f), 0f)
        assertEquals(0f, sanitizeAnimatorScale(0f), 0f)
        assertEquals(10f, sanitizeAnimatorScale(10f), 0f)
        assertEquals(MAX_ANIMATOR_SCALE, sanitizeAnimatorScale(1_000f), 0f)
    }

    @Test
    fun playedTimeIsElapsedOverScale() {
        assertEquals(1_000L, playedMillis(1_000L, 1f))
        assertEquals(100L, playedMillis(1_000L, 10f))
        assertEquals(2_000L, playedMillis(1_000L, 0.5f))
        assertEquals(ANIMATION_DONE_MILLIS, playedMillis(1L, 0f))
    }

    @Test
    fun followIsSlowerWithALargerScaleAndAtOnceWithout() {
        val normal = followShare(250L, 250.0, 1f)
        assertEquals(1.0 - Math.exp(-1.0), normal, 1e-9)
        assertEquals(normal, followShare(2_500L, 250.0, 10f), 1e-9)
        assertTrue(followShare(250L, 250.0, 10f) < normal)
        assertEquals(1.0, followShare(0L, 250.0, 0f), 0.0)
    }

    @Test
    fun theClockRunsAtOneOverScale() {
        val clock = AnimationClock()
        assertEquals(10_000L, clock.at(10_000L, 1f))
        assertEquals(11_000L, clock.at(11_000L, 1f))
        // Ten times slower from here on, without a jump.
        assertEquals(11_000L, clock.at(11_000L, 10f))
        assertEquals(11_100L, clock.at(12_000L, 10f))
        // Off: it stands still.
        assertEquals(11_200L, clock.at(13_000L, 0f))
        assertEquals(11_200L, clock.at(20_000L, 0f))
        // On again at normal pace from where it stood.
        assertEquals(11_200L, clock.at(20_000L, 1f))
        assertEquals(11_700L, clock.at(20_500L, 1f))
    }
}
