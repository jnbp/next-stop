package io.github.jnbp.nextstop.ui

import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sqrt

/*
 * Parts of one journey that run on the same way: a walk to the main station along the tracks a
 * train then takes, a train that goes into the city and back out on the same line. Drawn as
 * they are they lie on top of each other and the way in and the way back are one line. Each way
 * that shares much of its course with another way of the journey is drawn a little to its right
 * (as traffic keeps right), so a way in and a way back stand side by side, each with its casing.
 * Pure logic, no Android.
 */

/** How far, in dp, a way that shares its course with another is moved to its right. */
const val OVERLAP_SHIFT_DP = 3.5f

// The size of the cells two ways are compared in, in metres: ways closer than this share a cell.
private const val OVERLAP_CELL_METRES = 30.0

// A way is moved where at least this share of its cells is also on another way...
private const val OVERLAP_SHARE = 0.2

// ...and at least this many: a crossing or a shared station is no shared course.
private const val OVERLAP_MIN_CELLS = 6

/**
 * For each of [ways] (latitude and longitude points) whether it shares much of its course with
 * another of them: at least OVERLAP_SHARE and OVERLAP_MIN_CELLS of the cells it passes through.
 * Works on sorted arrays of primitive cells: a journey across the country costs a few hundred
 * kilobytes of longs and a sort, no boxed numbers.
 */
fun overlappingWays(ways: List<List<Pair<Double, Double>>>): BooleanArray {
    val result = BooleanArray(ways.size)
    if (ways.size < 2) return result
    val latitude = ways.firstOrNull { it.isNotEmpty() }?.first()?.first ?: return result
    val cellsOf = ways.map { wayCells(it, latitude) }
    // Every cell of every way, each way's cells once: a cell found twice is on two ways.
    val all = LongArray(cellsOf.sumOf { it.size })
    var at = 0
    for (cells in cellsOf) {
        cells.copyInto(all, at)
        at += cells.size
    }
    all.sort()
    for (index in ways.indices) {
        val cells = cellsOf[index]
        if (cells.isEmpty()) continue
        var shared = 0
        for (cell in cells) if (onTwoWays(all, cell)) shared++
        result[index] = shared >= OVERLAP_MIN_CELLS && shared >= OVERLAP_SHARE * cells.size
    }
    return result
}

/** Whether [cell] is at least twice in the sorted [all]. */
private fun onTwoWays(all: LongArray, cell: Long): Boolean {
    val found = all.binarySearch(cell)
    if (found < 0) return false
    return (found > 0 && all[found - 1] == cell) || (found < all.size - 1 && all[found + 1] == cell)
}

// At most this many cells are made for one way: a way across the country is filled in more coarsely.
private const val MAX_WAY_CELLS = 20_000

/**
 * The cells [points] pass through, sorted and each once, filled in along long stretches;
 * [latitude] for the scale of the east.
 */
private fun wayCells(points: List<Pair<Double, Double>>, latitude: Double): LongArray {
    if (points.isEmpty()) return LongArray(0)
    val north = 111_320.0
    val east = 111_320.0 * cos(Math.toRadians(latitude))
    var metres = 0.0
    for (index in 1 until points.size) {
        val dy = (points[index].first - points[index - 1].first) * north
        val dx = (points[index].second - points[index - 1].second) * east
        metres += sqrt(dx * dx + dy * dy)
    }
    // Half a cell between filled in points, or coarser where the way would make too many.
    val spacing = maxOf(OVERLAP_CELL_METRES / 2, metres / (MAX_WAY_CELLS - points.size).coerceAtLeast(1))
    var cells = LongArray(minOf(MAX_WAY_CELLS, points.size + (metres / spacing).toInt() + 1).coerceAtLeast(points.size))
    var count = 0
    fun add(lat: Double, lon: Double) {
        if (count == cells.size) cells = cells.copyOf(cells.size * 2)
        val row = floor(lat * north / OVERLAP_CELL_METRES).toLong()
        val column = floor(lon * east / OVERLAP_CELL_METRES).toLong()
        cells[count++] = row * 4_000_000L + column
    }
    for (index in points.indices) {
        val (lat, lon) = points[index]
        add(lat, lon)
        if (index == 0) continue
        val (fromLat, fromLon) = points[index - 1]
        val dy = (lat - fromLat) * north
        val dx = (lon - fromLon) * east
        val steps = (sqrt(dx * dx + dy * dy) / spacing).toInt()
        for (step in 1 until steps) {
            val share = step.toDouble() / steps
            add(fromLat + (lat - fromLat) * share, fromLon + (lon - fromLon) * share)
        }
    }
    val sorted = cells.copyOf(count)
    sorted.sort()
    var unique = 0
    for (index in sorted.indices) {
        if (index == 0 || sorted[index] != sorted[index - 1]) sorted[unique++] = sorted[index]
    }
    return sorted.copyOf(unique)
}

/**
 * The line through [places] (x and y in turn on the screen, [count] points, y down) moved by
 * [distance] pixels to its right, into [out]: each point along the mean of the normals of the
 * stretches beside it, lengthened at bends so the stretches keep their distance (at most twice).
 * Makes nothing.
 */
fun shiftRight(places: FloatArray, count: Int, distance: Float, out: FloatArray) {
    if (count < 2) {
        for (index in 0 until count * 2) out[index] = places[index]
        return
    }
    // The normal of the last stretch that has a length.
    var lastNx = 0f
    var lastNy = 0f
    for (index in 0 until count) {
        var nx = 0f
        var ny = 0f
        var beforeX = 0f
        var beforeY = 0f
        var has = 0
        if (index > 0) {
            val dx = places[index * 2] - places[index * 2 - 2]
            val dy = places[index * 2 + 1] - places[index * 2 - 1]
            val length = sqrt(dx * dx + dy * dy)
            if (length > 0f) {
                beforeX = -dy / length
                beforeY = dx / length
                nx += beforeX
                ny += beforeY
                has++
            }
        }
        if (index < count - 1) {
            val dx = places[index * 2 + 2] - places[index * 2]
            val dy = places[index * 2 + 3] - places[index * 2 + 1]
            val length = sqrt(dx * dx + dy * dy)
            if (length > 0f) {
                val ax = -dy / length
                val ay = dx / length
                if (has == 0) {
                    beforeX = ax
                    beforeY = ay
                }
                nx += ax
                ny += ay
                has++
            }
        }
        var length = sqrt(nx * nx + ny * ny)
        if (has == 0 || length < 1e-3f) {
            // No stretch with a length here (or a turn back on itself): as the last one.
            nx = lastNx
            ny = lastNy
            beforeX = nx
            beforeY = ny
            length = 1f
        } else {
            nx /= length
            ny /= length
            lastNx = nx
            lastNy = ny
        }
        // At a bend the point goes further out, so both stretches keep the distance.
        val cosine = (nx * beforeX + ny * beforeY).coerceAtLeast(0.5f)
        val reach = distance / cosine
        out[index * 2] = places[index * 2] + nx * reach
        out[index * 2 + 1] = places[index * 2 + 1] + ny * reach
    }
}
