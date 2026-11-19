package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class BadgeIconFitTest {
    private fun check(size: Float, w: Float, h: Float): IconFit {
        val fit = fitBadgeInIcon(size, w, h)
        val bw = w * fit.scale
        val bh = h * fit.scale
        // Centred, inside the square, inside the circle of the square (no mask cuts it).
        assertEquals(fit.left, size - fit.left - bw, 0.01f)
        assertEquals(fit.top, size - fit.top - bh, 0.01f)
        assertTrue(fit.left > 0f && fit.top > 0f)
        assertTrue(hypot(bw, bh) < size * 0.95f)
        assertTrue(fit.scale <= 1f)
        return fit
    }

    @Test
    fun wideBadgeLikeIce374Fits() {
        // A badge as wide as the badge allows (twice its height), drawn at the share's height.
        check(size = 168f, w = 141f, h = 70.5f)
    }

    @Test
    fun oversizedBadgeIsScaledDown() {
        val fit = check(size = 168f, w = 400f, h = 100f)
        assertEquals(168f * ICON_BADGE_MAX_WIDTH / 400f, fit.scale, 0.0001f)
    }

    @Test
    fun smallBadgeIsNotScaledUp() {
        val fit = check(size = 168f, w = 70f, h = 60f)
        assertEquals(1f, fit.scale, 0f)
    }

    @Test
    fun emptyInputsAreHarmless() {
        assertEquals(IconFit(1f, 0f, 0f), fitBadgeInIcon(0f, 10f, 10f))
    }
}
