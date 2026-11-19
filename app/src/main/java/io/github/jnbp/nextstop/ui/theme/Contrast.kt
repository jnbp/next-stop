package io.github.jnbp.nextstop.ui.theme

import kotlin.math.pow

/*
 * The contrast of two colours as WCAG 2 defines it, on plain ARGB ints, so it can be checked
 * without Compose. Theme.kt builds contrastOf and readableOn on it; alpha is ignored (both
 * colours count as opaque).
 */

/** Text on its ground reads at least at this contrast (WCAG AA for normal text). */
const val TEXT_CONTRAST = 4.5f

/** Large text, icons and marks: at least this contrast (WCAG AA for large text and graphics). */
const val MARK_CONTRAST = 3f

// The near-black the app moves dark ink towards (the ink of the Next Stop scheme, a bit deeper).
private const val NEAR_BLACK = 0xFF101114.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()

private fun channel(value: Int): Double {
    val c = value / 255.0
    return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
}

/** The relative luminance of [argb] (0 black to 1 white). */
fun relativeLuminance(argb: Int): Double =
    0.2126 * channel((argb shr 16) and 0xFF) + 0.7152 * channel((argb shr 8) and 0xFF) + 0.0722 * channel(argb and 0xFF)

/** The contrast ratio of [a] and [b], 1 (the same) to 21 (black on white); the order does not matter. */
fun contrastRatio(a: Int, b: Int): Float {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    return ((maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)).toFloat()
}

/** [a] mixed with [b] by [fraction] (0 all [a], 1 all [b]), opaque. */
internal fun mixArgb(a: Int, b: Int, fraction: Float): Int {
    val f = fraction.coerceIn(0f, 1f)
    fun mix(shift: Int): Int {
        val x = (a shr shift) and 0xFF
        val y = (b shr shift) and 0xFF
        return (x + (y - x) * f + 0.5f).toInt().coerceIn(0, 255)
    }
    return (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
}

/**
 * [ink] where it reads on [ground] (at least [minimum]:1); else moved step by step towards
 * near-black or white, whichever stands out more from the ground, until it does. The hue is
 * kept as long as it can be, so a tinted text stays tinted. Returns [ink] itself (its alpha
 * too) when it already reads.
 */
fun readableArgb(ground: Int, ink: Int, minimum: Float = TEXT_CONTRAST): Int {
    if (contrastRatio(ground, ink) >= minimum) return ink
    val target = if (contrastRatio(ground, NEAR_BLACK) >= contrastRatio(ground, WHITE)) NEAR_BLACK else WHITE
    for (step in 1..10) {
        val moved = mixArgb(ink, target, step / 10f)
        if (contrastRatio(ground, moved) >= minimum) return moved
    }
    return target
}

/** [ink] readable on every one of [grounds] at [minimum], moved as little as it takes (see [readableArgb]). */
fun readableOnAll(grounds: IntArray, ink: Int, minimum: Float = TEXT_CONTRAST): Int {
    var shown = ink
    // Twice over, so a step for a later ground cannot undo an earlier one unnoticed.
    repeat(2) { for (ground in grounds) shown = readableArgb(ground, shown, minimum) }
    return shown
}
