package io.github.jnbp.nextstop.wear.data

import kotlin.math.sqrt

/** Lengths on a round screen, for things at its edge that must stay inside the circle. */
object RoundScreen {
    /** The width of a round screen of [diameter] at the height [fromBottom] above its lowest point (0 outside). */
    fun chord(diameter: Float, fromBottom: Float): Float {
        val radius = diameter / 2
        val fromCentre = radius - fromBottom.coerceIn(0f, diameter)
        val square = radius * radius - fromCentre * fromCentre
        return if (square <= 0f) 0f else 2 * sqrt(square)
    }

    /** The least height above the lowest point of a round screen of [diameter] where it is [width] wide. */
    fun bottomFor(diameter: Float, width: Float): Float {
        val radius = diameter / 2
        val half = (width / 2).coerceAtMost(radius)
        return radius - sqrt(radius * radius - half * half)
    }
}
