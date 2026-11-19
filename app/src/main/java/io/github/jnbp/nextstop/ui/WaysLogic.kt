package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.Disruption
import io.github.jnbp.nextstop.data.colourPlace
import io.github.jnbp.nextstop.data.lineKey
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sqrt

/*
 * The pure logic of "My routes": which notices are the user's, which lie in the region, where
 * the arrow to a place outside the map points, and where a dragged tile of the grid lands.
 * No Android.
 */

/** Notices count for a favourite place or a stop with a bell within this distance. */
const val WAYS_NEAR_METERS = 500

/**
 * True when [item] lies in the region: one of its stops within [radiusMeters] of one of
 * [anchors] (home, the user's position). A notice without any stop that has a position (one
 * of a journey, of the whole network) cannot be placed: it counts, as the network asked is
 * the local one. Without anchors everything counts. [always] are notices on the user's own
 * ways with a bell: they count wherever they are.
 */
fun inRegion(
    item: Disruption,
    anchors: List<Pair<Double, Double>>,
    radiusMeters: Double = WAYS_REGION_METERS,
    always: Set<String> = emptySet(),
): Boolean {
    if (item.id in always || anchors.isEmpty()) return true
    val placed = item.stops.mapNotNull { it.position }
    if (placed.isEmpty()) return true
    return placed.any { at -> anchors.any { wayMeters(at, it) <= radiusMeters } }
}

/**
 * The notices that concern the user's ways: on a line of [lines] (the saved routes' and
 * connections' lines, and the lines of stops with a bell), or at a stop within
 * [nearMeters] of one of [spots] (favourite places, stops with a bell). The order of [items] stays.
 */
fun relevantDisruptions(
    items: List<Disruption>,
    lines: Set<String>,
    spots: List<Pair<Double, Double>>,
    nearMeters: Int = WAYS_NEAR_METERS,
): List<Disruption> {
    if (items.isEmpty()) return items
    val keys = lines.mapTo(HashSet()) { lineKey(it) }
    return items.filter { item ->
        val onLine = keys.isNotEmpty() &&
            (item.lines.any { lineKey(it) in keys } || item.about.any { lineKey(it.line) in keys })
        onLine || item.stops.any { stop ->
            val at = stop.position ?: return@any false
            spots.any { wayMeters(at, it) <= nearMeters }
        }
    }
}

/** An arrow at the edge of the map: where it sits (dp from the top left) and where it points (degrees, 0 = right, clockwise). */
data class EdgeArrow(val x: Double, val y: Double, val degrees: Double)

/**
 * The arrow towards [target] (dp on a picture [width] × [height], possibly far outside it):
 * on the line from the middle of the picture to the target, where it leaves the picture
 * [inset] dp inside its edge. Null when the target is on the picture already.
 */
fun edgeArrow(target: Pair<Double, Double>, width: Double, height: Double, inset: Double): EdgeArrow? {
    val (x, y) = target
    if (x in 0.0..width && y in 0.0..height) return null
    val cx = width / 2.0
    val cy = height / 2.0
    val dx = x - cx
    val dy = y - cy
    if (abs(dx) < 1e-9 && abs(dy) < 1e-9) return null
    val halfW = (cx - inset).coerceAtLeast(0.0)
    val halfH = (cy - inset).coerceAtLeast(0.0)
    // How far along the line the inner frame is reached: the nearer of its sides.
    val scale = minOf(
        if (abs(dx) < 1e-9) Double.MAX_VALUE else halfW / abs(dx),
        if (abs(dy) < 1e-9) Double.MAX_VALUE else halfH / abs(dy),
    )
    val degrees = (atan2(dy, dx) * 180.0 / PI).let { if (it < 0) it + 360.0 else it }
    return EdgeArrow(cx + dx * scale, cy + dy * scale, degrees)
}

/**
 * Where a tile of a grid of [columns] columns lands when dragged by ([dx], [dy]) from cell
 * [from], cells [cellWidth] × [cellHeight] with [gap] between them, [count] cells in all:
 * the cell nearest to the dragged tile's centre.
 */
fun gridDropSlot(
    from: Int,
    dx: Float,
    dy: Float,
    cellWidth: Float,
    cellHeight: Float,
    gap: Float,
    count: Int,
    columns: Int = 2,
): Int {
    if (count <= 1) return 0
    val stepX = cellWidth + gap
    val stepY = cellHeight + gap
    val column = (from % columns + Math.round(dx / stepX)).coerceIn(0, columns - 1)
    val rows = (count + columns - 1) / columns
    val row = (from / columns + Math.round(dy / stepY)).coerceIn(0, rows - 1)
    return (row * columns + column).coerceIn(0, count - 1)
}

/** Where cell [index] of a grid of [columns] columns sits, as (column, row). */
fun gridCell(index: Int, columns: Int = 2): Pair<Int, Int> = (index % columns) to (index / columns)

private fun wayMeters(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
    val north = (b.first - a.first) * 111_320.0
    val east = (b.second - a.second) * 111_320.0 * cos((a.first + b.first) / 2 * PI / 180)
    return sqrt(north * north + east * east)
}

/** Whether "Disruptions on your ways" starts folded to one line: with more than two notices. */
fun waysNoticesFoldedByDefault(count: Int): Boolean = count > 2

/**
 * Whether "Disruptions on your ways" has its folding line (badges, count, chevron) at all: only
 * with more than one notice. One notice is its own row alone; a line above it that said the same
 * again (its badge, "1 Meldung") looked doubled (#134).
 */
fun waysNoticesHaveFoldRow(count: Int): Boolean = count > 1

/**
 * The line badges of the folded "Disruptions on your ways": each line once, in the order of
 * the notices, at most [max]; and how many lines are left out ("+n").
 */
fun foldedNoticeBadges(items: List<Disruption>, max: Int): Pair<List<io.github.jnbp.nextstop.data.NoticeLine>, Int> {
    val (placed, more) = foldedNoticeBadgesPlaced(items, max)
    return placed.map { it.line } to more
}

/** A badge of the folded notices with the place it is coloured at (its notice's, see colourPlace). */
data class PlacedNoticeBadge(val line: io.github.jnbp.nextstop.data.NoticeLine, val place: Pair<Double, Double>)

/**
 * As [foldedNoticeBadges], each badge with the colour place of its notice, the same place its
 * row uses, so the folded line and the open rows never differ. The same line name inside and
 * outside the VBB area (Berlin's U9, Stuttgart's U9) are two badges.
 */
fun foldedNoticeBadgesPlaced(items: List<Disruption>, max: Int): Pair<List<PlacedNoticeBadge>, Int> {
    val seen = LinkedHashMap<String, PlacedNoticeBadge>()
    items.forEach { item ->
        val place = item.colourPlace
        val home = io.github.jnbp.nextstop.data.inVbbArea(place.first, place.second)
        item.badges.forEach { badge ->
            val name = badge.line.filter { !it.isWhitespace() }.uppercase()
            val key = if (home) name else "$name@${place.first},${place.second}"
            if (name.isNotEmpty() && key !in seen) seen[key] = PlacedNoticeBadge(badge, place)
        }
    }
    val all = seen.values.toList()
    return all.take(max) to (all.size - max).coerceAtLeast(0)
}

/**
 * Where a tile of a two-column grid with rows of different heights lands when dragged by
 * ([dx], [dy]) from cell [from]: the column as in [gridDropSlot], the row whose middle is
 * nearest to the middle of the dragged tile. [rowTops] and [rowHeights] are per row (pixels).
 */
fun gridDropSlotRows(
    from: Int,
    dx: Float,
    dy: Float,
    cellWidth: Float,
    rowTops: FloatArray,
    rowHeights: FloatArray,
    gap: Float,
    count: Int,
    columns: Int = 2,
): Int {
    if (count <= 1) return 0
    val rows = minOf((count + columns - 1) / columns, rowTops.size, rowHeights.size)
    if (rows <= 0) return from.coerceIn(0, count - 1)
    val column = (from % columns + Math.round(dx / (cellWidth + gap))).coerceIn(0, columns - 1)
    val fromRow = (from / columns).coerceIn(0, rows - 1)
    val middle = rowTops[fromRow] + rowHeights[fromRow] / 2f + dy
    var row = 0
    var best = Float.MAX_VALUE
    for (r in 0 until rows) {
        val distance = abs(rowTops[r] + rowHeights[r] / 2f - middle)
        if (distance < best) {
            best = distance
            row = r
        }
    }
    return (row * columns + column).coerceIn(0, count - 1)
}

/** The tops of rows of [heights] with [gap] between them. */
fun rowTopsOf(heights: FloatArray, gap: Float): FloatArray {
    val tops = FloatArray(heights.size)
    var y = 0f
    for (i in heights.indices) {
        tops[i] = y
        y += heights[i] + gap
    }
    return tops
}

/**
 * Whether the header of a place tile puts its travel time under the title rather than beside
 * it (#99): the title has priority. Beside it only where the whole title ([titleWidth], at
 * its natural width), the [gap] and the time ([timeWidth]) fit into [maxWidth] (all pixels,
 * the title's width counted after its glyph); no time (0) never moves anything.
 */
fun tileTimeBelowTitle(titleWidth: Int, timeWidth: Int, gap: Int, maxWidth: Int): Boolean =
    timeWidth > 0 && titleWidth + gap + timeWidth > maxWidth
