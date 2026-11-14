package io.github.jnbp.nextstop.ui

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import io.github.jnbp.nextstop.data.Departure
import io.github.jnbp.nextstop.data.OsmLine
import io.github.jnbp.nextstop.data.lineKey
import io.github.jnbp.nextstop.data.platformNumber

/*
 * The rules that keep the map readable close up at big stations (Berlin Hbf, Hamburg Hbf):
 * which station details show from which zoom, which one wins where they collide, near-identical
 * symbols merged into one, and the stop position pills of the marked stop kept apart. Plain
 * functions without Android, so they are tested on their own (MapDeclutterTest).
 */

/** The kinds of station details on the map. */
enum class DetailKind(val key: String) {
    Entrance("entrance"),
    Lift("lift"),
    Escalator("escalator"),
}

/**
 * The setting "Ein- und Ausgänge anzeigen": [Auto] by zoom (entrances from 16, lifts from 17,
 * escalators from 18; the marked station a step earlier), [Always] every entrance, lift and
 * escalator from zoom 16, [MarkedOnly] only those of the marked station.
 */
enum class EntranceMode(val key: String) {
    Auto("auto"),
    Always("always"),
    MarkedOnly("marked"),
    ;

    companion object {
        fun of(key: String?): EntranceMode = entries.firstOrNull { it.key == key } ?: Auto
    }
}

/**
 * From which zoom a detail of [kind] shows with the setting [mode]: in [EntranceMode.Auto]
 * entrances from 16, lifts from 17, escalators from 18 everywhere, and for the [marked] station
 * a step earlier each (entrances already with its stop positions), so the station one looks at
 * shows what it has. [EntranceMode.Always]: all of them from 16 (the marked station's
 * entrances still with its positions). [EntranceMode.MarkedOnly]: the marked station as in
 * Auto; the others never (null).
 */
fun detailMinZoom(kind: DetailKind, marked: Boolean, mode: EntranceMode = EntranceMode.Auto): Double? {
    val auto = when (kind) {
        DetailKind.Entrance -> if (marked) SELECTED_POSITION_ZOOM else STATION_DETAIL_ZOOM
        DetailKind.Lift -> if (marked) 16.5 else 17.0
        DetailKind.Escalator -> if (marked) 17.0 else 18.0
    }
    return when (mode) {
        EntranceMode.Auto -> auto
        EntranceMode.Always -> minOf(auto, STATION_DETAIL_ZOOM)
        EntranceMode.MarkedOnly -> if (marked) auto else null
    }
}

/**
 * The sort key of a detail where symbols collide (lower is placed first and wins): entrances
 * with a name or letter before plain entrances, those before lifts, lifts before escalators.
 */
fun detailSortKey(kind: DetailKind, named: Boolean): Float = when (kind) {
    DetailKind.Entrance -> if (named) 0f else 1f
    DetailKind.Lift -> 2f
    DetailKind.Escalator -> 3f
}

/** One symbol of a station detail as the merging sees it: [icon] tells which look alike. */
data class DetailSymbol(
    val kind: DetailKind,
    val icon: String,
    val latitude: Double,
    val longitude: Double,
    val named: Boolean = false,
)

/** Near-identical symbols lie at most this far apart: one of them stands for all. */
const val DETAIL_MERGE_METERS = 8.0

/**
 * The indices of [symbols] to draw: of symbols with the same icon within [meters] of each
 * other only one is kept, a named one before an unnamed one, else the first. The order of
 * the kept ones stays as given.
 */
fun mergeNearSymbols(symbols: List<DetailSymbol>, meters: Double = DETAIL_MERGE_METERS): List<Int> {
    if (symbols.size < 2) return symbols.indices.toList()
    // Named ones are looked at first, so they are the ones kept.
    val order = symbols.indices.sortedBy { if (symbols[it].named) 0 else 1 }
    val cellDegrees = meters / METERS_PER_DEGREE
    val kept = HashMap<Long, MutableList<Int>>()
    val keep = BooleanArray(symbols.size)
    for (index in order) {
        val symbol = symbols[index]
        val row = floor(symbol.latitude / cellDegrees).toLong()
        val column = floor(symbol.longitude / cellDegrees).toLong()
        var near = false
        search@ for (dy in -1L..1L) for (dx in -2L..2L) {
            // Longitude degrees are shorter than latitude ones: two columns either side cover the room.
            for (other in kept[cellKey(row + dy, column + dx)].orEmpty()) {
                val candidate = symbols[other]
                if (candidate.icon == symbol.icon && metersBetween(candidate, symbol) <= meters) {
                    near = true
                    break@search
                }
            }
        }
        if (near) continue
        keep[index] = true
        kept.getOrPut(cellKey(row, column)) { ArrayList() }.add(index)
    }
    return symbols.indices.filter { keep[it] }
}

private const val METERS_PER_DEGREE = 111_320.0

private fun cellKey(row: Long, column: Long): Long = (row shl 32) + (column and 0xFFFFFFFFL)

private fun metersBetween(a: DetailSymbol, b: DetailSymbol): Double {
    val north = (b.latitude - a.latitude) * METERS_PER_DEGREE
    val east = (b.longitude - a.longitude) * METERS_PER_DEGREE * cos((a.latitude + b.latitude) / 2 * PI / 180)
    return sqrt(north * north + east * east)
}

/**
 * A stop position pill of the marked stop as the declutter sees it: where its pin stands, how
 * big its picture is on the screen in dp ([widthDp] by [heightDp], the pin at the bottom
 * middle) and how much it matters ([priority], higher wins).
 */
data class PillBox(
    val latitude: Double,
    val longitude: Double,
    val widthDp: Float,
    val heightDp: Float,
    val priority: Int,
    /** The width of the pill alone (without its pin and shadow), for the pill moved aside; 0: as [widthDp]. */
    val pillWidthDp: Float = 0f,
)

/**
 * Where the pill of a stop position stands to its position: [Above] on its pin as always,
 * moved aside to the [Right], [Left] or [Below] with a thin leader line to the position's dot
 * where it would cover a station sign, or as its [Pin] alone where no side is free. [code]
 * goes into the name of the picture.
 */
enum class PillSide(val code: String) {
    Above("n"),
    Right("r"),
    Left("l"),
    Below("b"),
    Pin("p"),
}

// The sizes of a pill picture in dp (see selectedPositionBitmap and shiftedPositionBitmap):
// the pill's height, the leader from the position's dot to a pill moved aside, the dot's
// radius, and the room for the shadow around the pill.
const val PILL_HEIGHT_DP = 18f
const val PILL_LEADER_DP = 14f
const val PILL_DOT_DP = 2.5f
const val PILL_MARGIN_DP = 4f

/**
 * A station sign as the pills see it: a box of [halfWidthDp] by [halfHeightDp] around its
 * point, moved down by [downDp] (the name below the marked stop's sign) and right by
 * [rightDp] (a column of vehicles beside a sign, see groupColumnBox). Pills keep clear of it.
 */
data class SignBox(
    val latitude: Double,
    val longitude: Double,
    val halfWidthDp: Float,
    val halfHeightDp: Float,
    val downDp: Float = 0f,
    val rightDp: Float = 0f,
)

/** The width and height in dp of the picture of a pill [pillWidthDp] wide moved to [side] (see shiftedPositionBitmap). */
fun shiftedPillSize(pillWidthDp: Float, side: PillSide): Pair<Float, Float> = when (side) {
    PillSide.Right, PillSide.Left ->
        (2 * PILL_DOT_DP + PILL_LEADER_DP + pillWidthDp + PILL_MARGIN_DP) to (PILL_HEIGHT_DP + 2 * PILL_MARGIN_DP)
    PillSide.Below -> (pillWidthDp + 2 * PILL_MARGIN_DP) to (2 * PILL_DOT_DP + PILL_LEADER_DP + PILL_HEIGHT_DP + PILL_MARGIN_DP)
    else -> 0f to 0f
}

/**
 * Where each of [pills] stands at [zoom] with the map turned by [bearing]: station signs
 * ([signs], each grown by [signPaddingDp]) always win. The most important pill first (by
 * priority, then the order given): on its pin ([PillSide.Above]) where that box overlaps no
 * sign and no pill kept before; where it would cover a sign, moved to the first free side of
 * right, left and below (with a leader line to its position); otherwise its pin alone. A pill
 * that covers only another pill tries those sides as well where its [PillBox.pillWidthDp] is
 * known (a bare position shows as its pin).
 */
fun placePills(
    pills: List<PillBox>,
    signs: List<SignBox>,
    zoom: Double,
    bearing: Double = 0.0,
    paddingDp: Float = 2f,
    signPaddingDp: Float = 3f,
): List<PillSide> {
    if (pills.isEmpty()) return emptyList()
    val latitude = pills.sumOf { it.latitude } / pills.size
    val perDp = metersPerDp(zoom, latitude)
    val turn = bearing * PI / 180
    val origin = pills[0]
    // Screen positions in dp, y downwards, relative to the first pill.
    fun screen(lat: Double, lon: Double): Pair<Double, Double> {
        val east = (lon - origin.longitude) * METERS_PER_DEGREE * cos(latitude * PI / 180) / perDp
        val north = (lat - origin.latitude) * METERS_PER_DEGREE / perDp
        val x = east * cos(turn) - north * sin(turn)
        val y = -(east * sin(turn) + north * cos(turn))
        return x to y
    }
    // Boxes as left, top, right, bottom.
    val signRects = signs.map { sign ->
        val (x, y) = screen(sign.latitude, sign.longitude)
        val cx = x + sign.rightDp
        val cy = y + sign.downDp
        doubleArrayOf(
            cx - sign.halfWidthDp - signPaddingDp, cy - sign.halfHeightDp - signPaddingDp,
            cx + sign.halfWidthDp + signPaddingDp, cy + sign.halfHeightDp + signPaddingDp,
        )
    }
    fun overlaps(a: DoubleArray, b: DoubleArray, pad: Float): Boolean =
        a[0] < b[2] + pad && b[0] < a[2] + pad && a[1] < b[3] + pad && b[1] < a[3] + pad
    val sides = MutableList(pills.size) { PillSide.Pin }
    val kept = ArrayList<DoubleArray>()
    val order = pills.indices.sortedWith(compareByDescending<Int> { pills[it].priority }.thenBy { it })
    for (index in order) {
        val pill = pills[index]
        val (x, y) = screen(pill.latitude, pill.longitude)
        val above = doubleArrayOf(x - pill.widthDp / 2, y - pill.heightDp, x + pill.widthDp / 2, y)
        val onSign = signRects.any { overlaps(above, it, 0f) }
        val onPill = kept.any { overlaps(above, it, paddingDp) }
        if (!onSign && !onPill) {
            sides[index] = PillSide.Above
            kept.add(above)
            continue
        }
        // A pill that only covers another pill moves aside too, so the trams at Berlin
        // Hauptbahnhof (Pos. 4 and 5, beside the busier bus Pos. 3) still show their lines;
        // a bare position (no number, no lines) keeps to its pin.
        if (!onSign && pill.pillWidthDp <= 0f) continue
        val width = if (pill.pillWidthDp > 0f) pill.pillWidthDp else pill.widthDp
        for (side in SIDE_CHOICES) {
            val (w, h) = shiftedPillSize(width, side)
            val box = when (side) {
                PillSide.Right -> doubleArrayOf(x, y - h / 2, x + w, y + h / 2)
                PillSide.Left -> doubleArrayOf(x - w, y - h / 2, x, y + h / 2)
                else -> doubleArrayOf(x - w / 2, y, x + w / 2, y + h)
            }
            if (signRects.none { overlaps(box, it, 0f) } && kept.none { overlaps(box, it, paddingDp) }) {
                sides[index] = side
                kept.add(box)
                break
            }
        }
    }
    return sides
}

// The sides a pill that would cover a station sign tries, in this order.
private val SIDE_CHOICES = listOf(PillSide.Right, PillSide.Left, PillSide.Below)

/** Metres per dp of the map at [zoom] and [latitude] (MapLibre's world is 512 dp wide at zoom 0). */
fun metersPerDp(zoom: Double, latitude: Double): Double =
    40_075_016.686 * cos(latitude * PI / 180) / (512.0 * 2.0.pow(zoom))

/**
 * The indices of [pills] drawn as whole pills at [zoom] with the map turned by [bearing]
 * degrees; the others would cover one that matters more and show as their pin alone. The
 * most important first (by priority, then the order given); a pill is kept where its box,
 * grown by [paddingDp], overlaps none kept before.
 */
fun declutterPills(pills: List<PillBox>, zoom: Double, bearing: Double = 0.0, paddingDp: Float = 2f): Set<Int> {
    if (pills.size < 2) return pills.indices.toSet()
    val latitude = pills.sumOf { it.latitude } / pills.size
    val perDp = metersPerDp(zoom, latitude)
    val turn = bearing * PI / 180
    val origin = pills[0]
    // Screen positions in dp, y downwards, relative to the first pill.
    val points = pills.map { pill ->
        val east = (pill.longitude - origin.longitude) * METERS_PER_DEGREE * cos(latitude * PI / 180) / perDp
        val north = (pill.latitude - origin.latitude) * METERS_PER_DEGREE / perDp
        // A map turned by the bearing turns what is on it the other way.
        val x = east * cos(turn) - north * sin(turn)
        val y = -(east * sin(turn) + north * cos(turn))
        x to y
    }
    val order = pills.indices.sortedWith(compareByDescending<Int> { pills[it].priority }.thenBy { it })
    val kept = ArrayList<Int>()
    for (index in order) {
        val (x, y) = points[index]
        val pill = pills[index]
        val clear = kept.none { other ->
            val (ox, oy) = points[other]
            val o = pills[other]
            // Boxes stand centred above their pins: horizontally around x, vertically from y - height to y.
            val overlapX = abs(x - ox) < (pill.widthDp + o.widthDp) / 2 + paddingDp
            val top = y - pill.heightDp
            val otherTop = oy - o.heightDp
            val overlapY = top < oy + paddingDp && otherTop < y + paddingDp
            overlapX && overlapY
        }
        if (clear) kept.add(index)
    }
    return kept.toSet()
}

/**
 * The departures of a stop that leave from one of its stop positions, "Pos. [pos]" with the
 * [lines] OpenStreetMap lists there: buses and trams whose platform has that number; where
 * the provider names no platform, those of a line that stops there. Trains never count, so
 * "Gleis 3" is not taken for "Pos. 3".
 */
fun positionDepartures(departures: List<Departure>, pos: String?, lines: List<OsmLine>): List<Departure> {
    val number = platformNumber(pos)
    val names = lines.map { lineKey(it.name) }.toSet()
    return departures.filter { departure ->
        if (departure.product != "bus" && departure.product != "tram") return@filter false
        val platform = platformNumber(departure.platform)
        when {
            number != null && platform != null -> platform == number
            names.isNotEmpty() -> lineKey(departure.line) in names
            else -> false
        }
    }
}
