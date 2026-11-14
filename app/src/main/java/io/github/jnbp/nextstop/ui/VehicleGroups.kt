package io.github.jnbp.nextstop.ui

import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sqrt

/*
 * Vehicles at a station: trains that stand in a station or arrive there would all be drawn on
 * the same spot, on top of each other and of the station sign. Instead they line up in a
 * column beside the sign, ordered by product (trains first), each badge with its own row. The
 * work is split by cost: which station a vehicle may belong to is found once per refresh of
 * the vehicles (candidateAnchor); per frame only one distance per vehicle decides whether it
 * is there now, and the small groups are laid out (layoutGroup). Pure logic, no Android.
 */

/**
 * A stop sign on the map beside which the vehicles at that stop line up; [halfWidthDp] is half
 * the width of the sign. Only vehicles of [products] line up there (an S-Bahn beside the
 * station's "S", not beside the bus "H" in the street outside); empty: vehicles of any product.
 * [nameHalfHeightDp] is set for a stop the map shows by its green name close up (the setting
 * "stop names"): half the height of that name, under which the vehicles line up; 0 otherwise.
 */
data class GroupAnchor(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val halfWidthDp: Float,
    val products: Set<String> = emptySet(),
    val nameHalfHeightDp: Float = 0f,
) {
    /** Whether a vehicle of [product] may line up here; a vehicle of no known product may anywhere. */
    fun serves(product: String?): Boolean = products.isEmpty() || product.isNullOrEmpty() || product in products
}

/** A vehicle within this distance of a stop, in metres, stands or arrives there. */
const val GROUP_RADIUS_METERS = 100.0

/** At most this many rows beside a sign; more vehicles show the first ones and a chip "+N". */
const val GROUP_MAX_SLOTS = 4

// The gap between the sign and the column, and between two rows, in dp. A row is as high as a
// badge plus the gap; the gap leaves room for the live dot reaching above a badge.
internal const val GROUP_SIGN_GAP_DP = 3f
internal const val GROUP_ROW_GAP_DP = 3f

/** The distance between two points in metres; plain enough for the few hundred metres it is used for. */
fun metersBetween(latitude1: Double, longitude1: Double, latitude2: Double, longitude2: Double): Double {
    val north = (latitude2 - latitude1) * METERS_PER_DEGREE
    val east = (longitude2 - longitude1) * METERS_PER_DEGREE * cos(Math.toRadians((latitude1 + latitude2) / 2))
    return sqrt(north * north + east * east)
}

private const val METERS_PER_DEGREE = 111_320.0

/** The anchors in cells of about [GROUP_RADIUS_METERS], so the nearest one is found without looking at all of them. */
class AnchorIndex(anchors: List<GroupAnchor>) {
    private val cells = HashMap<Long, MutableList<GroupAnchor>>()

    init {
        for (anchor in anchors) cells.getOrPut(cellOf(anchor.latitude, anchor.longitude)) { ArrayList(2) }.add(anchor)
    }

    /** The anchor nearest to the point within [radius] metres that serves [product] (see GroupAnchor.serves), or null. */
    fun nearest(latitude: Double, longitude: Double, radius: Double = GROUP_RADIUS_METERS, product: String? = null): GroupAnchor? {
        val row = floor(latitude / CELL_DEGREES).toLong()
        val column = floor(longitude / CELL_DEGREES).toLong()
        var best: GroupAnchor? = null
        var bestDistance = radius
        for (r in row - 1..row + 1) {
            for (c in column - 1..column + 1) {
                for (anchor in cells[key(r, c)] ?: continue) {
                    if (!anchor.serves(product)) continue
                    val distance = metersBetween(latitude, longitude, anchor.latitude, anchor.longitude)
                    if (distance <= bestDistance) {
                        best = anchor
                        bestDistance = distance
                    }
                }
            }
        }
        return best
    }

    private fun cellOf(latitude: Double, longitude: Double): Long =
        key(floor(latitude / CELL_DEGREES).toLong(), floor(longitude / CELL_DEGREES).toLong())

    private fun key(row: Long, column: Long): Long = (row shl 32) xor (column and 0xFFFFFFFFL)

    private companion object {
        // About 220 m north-south and 150 m east-west in Berlin: wider than the radius either way.
        const val CELL_DEGREES = 0.002
    }
}

/**
 * The stop a vehicle on [path] (latitude, longitude) may stand at or arrive at during this
 * refresh: the anchor nearest to any point of its way, within [GROUP_RADIUS_METERS], of those
 * that serve its [product]; none where no such anchor is near (it keeps to its own way). Once
 * per refresh; whether the vehicle is there at a moment is then one distance (see isAt).
 */
fun candidateAnchor(index: AnchorIndex, path: List<Pair<Double, Double>>, product: String? = null): GroupAnchor? {
    var best: GroupAnchor? = null
    var bestDistance = Double.MAX_VALUE
    for ((latitude, longitude) in path) {
        val anchor = index.nearest(latitude, longitude, product = product) ?: continue
        val distance = metersBetween(latitude, longitude, anchor.latitude, anchor.longitude)
        if (distance < bestDistance) {
            best = anchor
            bestDistance = distance
        }
    }
    return best
}

/** Whether a vehicle at the point is at [anchor] now. */
fun isAt(anchor: GroupAnchor, latitude: Double, longitude: Double): Boolean =
    metersBetween(latitude, longitude, anchor.latitude, anchor.longitude) <= GROUP_RADIUS_METERS

/**
 * A vehicle at a stop (or lined up on its way, see VehicleClusters.kt): its [key] (the trip),
 * its [rank] of placement (lower first, trains first), its badge text and its [direction].
 */
data class GroupMember(val key: String, val rank: Int, val label: String, val direction: String = "")

/** Where a badge goes, from the middle of the sign, in dp, right and down positive. */
data class GroupSlot(val dx: Float, val dy: Float)

/** The layout of one group: the row of each vehicle shown, the keys of those hidden, and the chip "+N" for them. */
data class GroupLayout(val slots: Map<String, GroupSlot>, val hidden: Set<String>, val chipLabel: String?, val chip: GroupSlot?)

/** Half the width of a badge with [label] as the map draws it, in dp. */
fun badgeHalfWidthDp(label: String): Float = ((label.length.coerceAtLeast(1) * BADGE_CHAR_DP + BADGE_SIDE_DP) / 2).toFloat()

/**
 * Lays out [members] at a sign of half width [signHalfWidthDp]: a column right of the sign,
 * left edges aligned, centred on the sign, ordered by rank, then badge text, then direction,
 * then key (so the order holds from frame to frame). Beyond [GROUP_MAX_SLOTS] the last row is a chip "+N" for
 * the vehicles that do not fit. With [belowHalfHeightDp] above 0 the stop shows as a name of
 * that half height instead of a sign: the column stands below the name, each badge centred.
 */
fun layoutGroup(
    signHalfWidthDp: Float,
    members: List<GroupMember>,
    belowHalfHeightDp: Float = 0f,
    topDp: Float? = null,
): GroupLayout {
    val ordered = members.sortedWith(compareBy<GroupMember>({ it.rank }, { it.label }, { it.direction }, { it.key }))
    val overflow = ordered.size > GROUP_MAX_SLOTS
    val shown = if (overflow) ordered.take(GROUP_MAX_SLOTS - 1) else ordered
    val hidden = if (overflow) ordered.drop(GROUP_MAX_SLOTS - 1) else emptyList()
    val chipLabel = if (overflow) "+${hidden.size}" else null
    val rows = shown.size + if (overflow) 1 else 0
    val pitch = (BADGE_HEIGHT_DP + GROUP_ROW_GAP_DP).toFloat()
    val below = belowHalfHeightDp > 0f
    val left = signHalfWidthDp + GROUP_SIGN_GAP_DP
    // Beside the sign: centred on it. Below a name: the first row just under the name.
    // Beside a sign with a sign of disruptions on its corner: no higher than [topDp].
    val centredTop = -(rows - 1) / 2f * pitch - BADGE_HEIGHT_DP.toFloat() / 2
    val shift = if (!below && topDp != null && centredTop < topDp) topDp - centredTop else 0f
    fun rowY(row: Int): Float = if (below) {
        belowHalfHeightDp + GROUP_SIGN_GAP_DP + BADGE_HEIGHT_DP.toFloat() / 2 + row * pitch
    } else {
        (row - (rows - 1) / 2f) * pitch + shift
    }
    fun slotX(label: String): Float = if (below) 0f else left + badgeHalfWidthDp(label)
    val slots = LinkedHashMap<String, GroupSlot>()
    shown.forEachIndexed { row, member -> slots[member.key] = GroupSlot(slotX(member.label), rowY(row)) }
    val chip = chipLabel?.let { GroupSlot(slotX(it), rowY(rows - 1)) }
    return GroupLayout(slots, hidden.map { it.key }.toSet(), chipLabel, chip)
}

/**
 * The room the column of [layout] (for [members]) beside [anchor] takes on the screen, as a box
 * the pills of the marked stop keep clear of (see placePills): the vehicles keep their place,
 * a pill moves aside or shows as its pin. Null for an empty column.
 */
fun groupColumnBox(anchor: GroupAnchor, layout: GroupLayout, members: List<GroupMember>): SignBox? {
    var left = Float.MAX_VALUE
    var right = -Float.MAX_VALUE
    var top = Float.MAX_VALUE
    var bottom = -Float.MAX_VALUE
    fun take(slot: GroupSlot, label: String) {
        val half = badgeHalfWidthDp(label)
        left = minOf(left, slot.dx - half)
        right = maxOf(right, slot.dx + half)
        top = minOf(top, slot.dy - BADGE_HEIGHT_DP.toFloat() / 2)
        bottom = maxOf(bottom, slot.dy + BADGE_HEIGHT_DP.toFloat() / 2)
    }
    for (member in members) layout.slots[member.key]?.let { take(it, member.label) }
    val chip = layout.chip
    val chipLabel = layout.chipLabel
    if (chip != null && chipLabel != null) take(chip, chipLabel)
    if (left > right) return null
    return SignBox(
        anchor.latitude,
        anchor.longitude,
        halfWidthDp = (right - left) / 2,
        halfHeightDp = (bottom - top) / 2,
        downDp = (top + bottom) / 2,
        rightDp = (left + right) / 2,
    )
}

/**
 * Half the height of the green name of a stop, as the map writes it (12 sp, at most 8 em
 * wide, centred on the stop; rough widths per letter), for the column below it.
 */
/** Half the width of the green name of a stop shown by name, in dp (as [stopNameHalfHeightDp], at most 8 em wide). */
fun stopNameHalfWidthDp(name: String): Float = (name.length * 6.6f).coerceAtMost(96f) / 2

fun stopNameHalfHeightDp(name: String): Float {
    val lines = kotlin.math.ceil(name.length * 6.6f / 96f).coerceIn(1f, 3f)
    return lines * 15f / 2
}

/** Half the width of a stop sign with [symbols] symbols (one per letter), in dp; [ring] for the selected stop. */
fun signHalfWidthDp(symbols: Int, ring: Boolean = false): Float {
    val n = symbols.coerceAtLeast(1)
    val width = 2 * SIGN_PADDING + n * SIGN_SYMBOL + (n - 1) * SIGN_GAP
    return width / 2 + if (ring) SIGN_RING_EXTRA else 0f
}

// The sizes of a stop sign as drawn by the map (see stopSignBitmap), in dp.
private const val SIGN_SYMBOL = 22f
private const val SIGN_GAP = 3f
private const val SIGN_PADDING = 5f
private const val SIGN_RING_EXTRA = 4.5f

/** How a symbol layer of the map takes part in the placement of the symbols. */
data class SymbolCollision(val allowOverlap: Boolean, val ignorePlacement: Boolean)

/**
 * The placement of the vehicle badges, trains and the rest alike: drawn whatever was placed
 * before them (the stop signs and names above them), so a vehicle never vanishes, and taking
 * their room, so the stop position pills and details below them give way.
 */
val BADGE_COLLISION = SymbolCollision(allowOverlap = true, ignorePlacement = false)
