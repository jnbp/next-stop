package io.github.jnbp.nextstop.wear.data

import kotlin.math.ceil
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

    /**
     * The width left for the label of the "Details" button of a journey card in the results on
     * a round screen of [diameter] dp, where it shares the row half and half with the icon-only
     * start button (#283): the list keeps 5.2 % of the width on each side (Wear's content
     * padding, rounded up), the card [CARD_PADDING] on each side, the gap [CARD_BUTTON_GAP]
     * between the buttons, and the Details button [BUTTON_PADDING] on each side of its label.
     */
    fun journeyCardLabelRoom(diameter: Float): Float {
        val list = ceil(diameter * LIST_PADDING_PERCENT / 100f)
        val row = diameter - 2 * list - 2 * CARD_PADDING
        return (row - CARD_BUTTON_GAP) / 2 - 2 * BUTTON_PADDING
    }

    // The sizes of Wear Material 3 and the card of the results, in dp.
    private const val LIST_PADDING_PERCENT = 5.2f
    private const val CARD_PADDING = 12f
    const val ICON_BUTTON_WIDTH = 52f
    private const val CARD_BUTTON_GAP = 4f
    private const val BUTTON_PADDING = 12f
}
