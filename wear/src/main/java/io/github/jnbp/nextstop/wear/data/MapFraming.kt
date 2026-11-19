package io.github.jnbp.nextstop.wear.data

/** The middle of a map view in world units and its scale in pixels per unit. */
data class MapFraming(val x: Double, val y: Double, val pixelsPerUnit: Double)

/**
 * Frames an area of [spanX] x [spanY] world units around ([middleX], [middleY]) in a [width] x
 * [height] pixels view with [pad] pixels to spare at the edges, above a band of [bottomBand]
 * pixels at the foot that is kept free (for a caption, #149): the area is fitted into what is
 * left and the view's middle moved down by half the band, so the area sits above it. The band
 * shrinks where less than [MIN_FRAMED_PIXELS] would be left for the area.
 */
fun frameAboveBand(
    middleX: Double,
    middleY: Double,
    spanX: Double,
    spanY: Double,
    width: Int,
    height: Int,
    pad: Double,
    bottomBand: Double = 0.0,
): MapFraming {
    val band = bottomBand.coerceIn(0.0, maxOf(0.0, height - 2 * pad - MIN_FRAMED_PIXELS))
    val scale = minOf((width - 2 * pad) / spanX, (height - 2 * pad - band) / spanY).coerceAtLeast(1.0)
    return MapFraming(middleX, middleY + band / 2 / scale, scale)
}

// The least height in pixels left for the framed area above a bottom band.
const val MIN_FRAMED_PIXELS = 48.0
