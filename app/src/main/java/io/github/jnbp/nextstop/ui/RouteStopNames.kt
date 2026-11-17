package io.github.jnbp.nextstop.ui

/*
 * Which stops of a journey on the map carry their names. Start, changes and destination always
 * do; a stop on the way only where the map has room around it: zoomed out the route shows its
 * important stations alone, zoomed in the stops between come in one by one. Pure logic, no
 * Android.
 */

/** A stop on the way is named only where its dot is at least this far, in dp, from every named one. */
const val WAY_STOP_NAME_SPACING_DP = 56f

/**
 * For the dots of a journey at [x], [y] on the screen (pixels), of which [key] are start,
 * changes and destination: true for those whose names show. Every key; of the stops on the
 * way, in their order, each whose dot is at least [spacing] pixels from every key dot and from
 * every stop named before it.
 */
fun namedRouteStops(x: FloatArray, y: FloatArray, key: BooleanArray, spacing: Float): BooleanArray {
    val count = key.size
    val named = BooleanArray(count) { key[it] }
    val limit = spacing * spacing
    for (index in 0 until count) {
        if (key[index]) continue
        var free = true
        for (other in 0 until count) {
            if (other == index || !named[other]) continue
            val dx = x[index] - x[other]
            val dy = y[index] - y[other]
            if (dx * dx + dy * dy < limit) {
                free = false
                break
            }
        }
        named[index] = free
    }
    return named
}
