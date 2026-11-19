package io.github.jnbp.nextstop.data

/*
 * Where rail lines run underground (#219), for the hint of the sunny side: a ride through a
 * tunnel gets no sun. The data is one grid of cells of about 50 m over Germany, built from the
 * rail ways of OpenStreetMap by tools/build_tunnel_asset.py into the asset RAIL_TUNNELS_ASSET:
 * the cells a way in a tunnel crosses and, near them (within two cells, for the portals), those
 * an open way crosses. Any other point (outside the box, far from a tunnel) counts as open.
 * The lists are sorted IntArrays (a few hundred kB in memory) searched by binary search.
 */

/** The asset of the grid (app/src/main/assets), read once off the main thread with [parseTunnelGrid]. */
const val RAIL_TUNNELS_ASSET = "rail-tunnels-germany.json"

/** The grid of the asset: [tunnel] and [open] are sorted cell indices (row * [cols] + col, row 0 in the south). */
class TunnelGrid(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
    val cellLat: Double,
    val cellLon: Double,
    val cols: Int,
    private val tunnel: IntArray,
    private val open: IntArray,
) {
    /** The cell at [latitude], [longitude]; null outside the box. */
    fun cellAt(latitude: Double, longitude: Double): Int? {
        if (latitude < south || latitude >= north || longitude < west || longitude >= east) return null
        val row = ((latitude - south) / cellLat).toInt()
        val col = ((longitude - west) / cellLon).toInt()
        return row * cols + col
    }

    /**
     * Whether the rail line at [latitude], [longitude] runs underground: true where only a
     * tunnel crosses its cell, false where only an open way does; where both or neither do,
     * the cell and its eight neighbours decide by majority. Null where that is even or nothing
     * is known (outside the box, far from any tunnel: the asset keeps open cells only near one).
     */
    fun undergroundAt(latitude: Double, longitude: Double): Boolean? {
        val cell = cellAt(latitude, longitude) ?: return null
        val inTunnel = tunnel.binarySearch(cell) >= 0
        val inOpen = open.binarySearch(cell) >= 0
        if (inTunnel != inOpen) return inTunnel
        val row = cell / cols
        val col = cell % cols
        var tunnels = 0
        var opens = 0
        for (dr in -1..1) {
            for (dc in -1..1) {
                val c = col + dc
                if (c < 0 || c >= cols || row + dr < 0) continue
                val near = (row + dr) * cols + c
                if (tunnel.binarySearch(near) >= 0) tunnels++
                if (open.binarySearch(near) >= 0) opens++
            }
        }
        return when {
            tunnels > opens -> true
            opens > tunnels -> false
            else -> null
        }
    }
}

/** The cell indices of the asset's lists: gaps to the previous index in base 36, joined by commas. */
fun decodeTunnelCells(text: String): IntArray {
    if (text.isBlank()) return IntArray(0)
    // One pass without split: the Germany lists hold some 100 000 cells.
    var count = 1
    for (ch in text) if (ch == ',') count++
    val out = IntArray(count)
    var index = 0
    var cell = 0L
    var gap = 0L
    var digits = 0
    fun flush() {
        if (digits == 0) throw NumberFormatException("empty gap")
        cell += gap
        if (cell > Int.MAX_VALUE) throw NumberFormatException("cell out of range")
        out[index++] = cell.toInt()
        gap = 0L
        digits = 0
    }
    for (ch in text) {
        when {
            ch == ',' -> flush()
            ch == ' ' -> Unit
            else -> {
                val digit = Character.digit(ch, 36)
                if (digit < 0) throw NumberFormatException("not base 36: $ch")
                gap = gap * 36 + digit
                if (gap > Int.MAX_VALUE) throw NumberFormatException("gap out of range")
                digits++
            }
        }
    }
    flush()
    out.sort()
    return out
}

/** The string value of [key] in the flat JSON [text], without regular expressions over the long lists. */
private fun tunnelStringValue(text: String, key: String): String? {
    val keyAt = text.indexOf("\"$key\"")
    if (keyAt < 0) return null
    val colon = text.indexOf(':', keyAt + key.length + 2)
    if (colon < 0) return null
    val open = text.indexOf('"', colon + 1)
    if (open < 0 || text.substring(colon + 1, open).isNotBlank()) return null
    val close = text.indexOf('"', open + 1)
    return if (close < 0) null else text.substring(open + 1, close)
}

/** The grid of the asset's JSON [text]; null where it cannot be read. */
fun parseTunnelGrid(text: String): TunnelGrid? = try {
    fun number(key: String): Double? =
        Regex("\"$key\"\\s*:\\s*(-?[0-9.eE+-]+)").find(text)?.groupValues?.get(1)?.toDoubleOrNull()
    fun cells(key: String): IntArray? = tunnelStringValue(text, key)?.let(::decodeTunnelCells)
    val box = Regex("\"box\"\\s*:\\s*\\[([^\\]]*)]").find(text)?.groupValues?.get(1)
        ?.split(',')?.mapNotNull { it.trim().toDoubleOrNull() }
    val cellLat = number("cellLat")
    val cellLon = number("cellLon")
    val cols = number("cols")?.toInt()
    val tunnel = cells("tunnel")
    val open = cells("open")
    if (box == null || box.size != 4 || cellLat == null || cellLon == null || cols == null || tunnel == null || open == null ||
        cellLat <= 0.0 || cellLon <= 0.0 || cols <= 0 ||
        // Every cell index of the box must fit an Int.
        Math.ceil((box[2] - box[0]) / cellLat) * cols >= Int.MAX_VALUE
    ) {
        null
    } else {
        TunnelGrid(box[0], box[1], box[2], box[3], cellLat, cellLon, cols, tunnel, open)
    }
} catch (e: NumberFormatException) {
    null
}

/** The grid of the app, read once from the asset off the main thread (see RailTunnelAsset); null until then or where it failed. */
object RailTunnels {
    @Volatile
    var grid: TunnelGrid? = null

    /** True once reading the asset is over, whether it worked or not. */
    @Volatile
    var read: Boolean = false
}

// The box the asset covers ("box" of RAIL_TUNNELS_ASSET, Germany), known before it is read.
private const val TUNNEL_BOX_SOUTH = 47.2
private const val TUNNEL_BOX_WEST = 5.8
private const val TUNNEL_BOX_NORTH = 55.1
private const val TUNNEL_BOX_EAST = 15.1

/**
 * True while the hint of the sunny side of a ride of [product] along [way] must wait for the
 * tunnels: they are not read yet ([tunnelsRead] false), the ride is on rails, and its way
 * touches the box of the asset. Until then no hint shows, so a ride through a tunnel never
 * flashes a sun chip that goes again a moment later. Once read (or failed) this is false.
 */
fun sunWaitsForTunnels(product: String, way: List<Pair<Double, Double>>, tunnelsRead: Boolean): Boolean {
    if (tunnelsRead || product in NOT_ON_RAILS) return false
    return way.any { (latitude, longitude) ->
        latitude >= TUNNEL_BOX_SOUTH && latitude < TUNNEL_BOX_NORTH &&
            longitude >= TUNNEL_BOX_WEST && longitude < TUNNEL_BOX_EAST
    }
}

// Vehicles on the road or water: never in a rail tunnel.
private val NOT_ON_RAILS = setOf("bus", "coach", "tram", "ferry", "taxi")

/**
 * Whether a point of a ride of [product] lies underground, by [grid]; null (nothing known: all
 * open) for buses, trams and ferries and without a grid. An unknown point counts as open.
 */
fun undergroundTest(product: String, grid: TunnelGrid?): ((Double, Double) -> Boolean)? {
    if (grid == null || product in NOT_ON_RAILS) return null
    return { latitude, longitude -> grid.undergroundAt(latitude, longitude) == true }
}
