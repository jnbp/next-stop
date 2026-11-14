package io.github.jnbp.nextstop.data

import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sqrt

private typealias Point = Pair<Double, Double>

/**
 * Joins of ways on the map without spikes. A way (latitude, longitude) that is led on to a
 * point beside it, a stop position or the end of another way, must not run past that point
 * and come back: the way is cut where the point lies beside it (its projection), and the
 * point is added only where the way does not turn back sharply to reach it.
 */
object PathJoin {
    /** A join that turns by this much or more (0 straight on, 180 straight back) is a spike. */
    const val MAX_TURN_DEGREES = 100.0

    /** A stretch at an end of a way this short that is walked back again is left out. */
    const val ZIGZAG_METERS = 15.0

    /** Only so far along a way from its end is looked for where a point lies beside it. */
    const val END_WINDOW_METERS = 250.0

    // Points closer than this are the same point.
    private const val SAME_METERS = 0.5

    /** Where a point lies beside a way: on its stretch [index] to [index] + 1 at [share], at [point]. */
    class Hit(val index: Int, val share: Double, val point: Point, val meters: Double)

    /** The distance between [a] and [b] in metres (flat earth, fine for a city). */
    fun meters(a: Point, b: Point): Double {
        val north = (b.first - a.first) * 111_320.0
        val east = (b.second - a.second) * 111_320.0 * cos(Math.toRadians((a.first + b.first) / 2))
        return sqrt(north * north + east * east)
    }

    /**
     * How sharply the way [a] - [b] - [c] turns at [b] in degrees: 0 straight on, 180 straight
     * back. 0 where a stretch has no length.
     */
    fun turnDegrees(a: Point, b: Point, c: Point): Double {
        val scale = cos(Math.toRadians(b.first))
        val x1 = (b.second - a.second) * scale
        val y1 = b.first - a.first
        val x2 = (c.second - b.second) * scale
        val y2 = c.first - b.first
        val l1 = sqrt(x1 * x1 + y1 * y1)
        val l2 = sqrt(x2 * x2 + y2 * y2)
        if (l1 <= 0.0 || l2 <= 0.0) return 0.0
        val cosine = ((x1 * x2 + y1 * y2) / (l1 * l2)).coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(cosine))
    }

    /** The point of the stretches [from] .. [to] (point indices) of [path] nearest to [point]. */
    fun nearest(path: List<Point>, point: Point, from: Int = 0, to: Int = path.lastIndex): Hit {
        require(path.isNotEmpty())
        if (path.size == 1 || to <= from) {
            val only = path[from.coerceIn(0, path.lastIndex)]
            return Hit(from.coerceIn(0, path.lastIndex), 0.0, only, meters(only, point))
        }
        val scale = cos(Math.toRadians(point.first))
        var best: Hit? = null
        for (index in from until to) {
            val a = path[index]
            val b = path[index + 1]
            val ax = (a.second - point.second) * scale
            val ay = a.first - point.first
            val dx = (b.second - a.second) * scale
            val dy = b.first - a.first
            val length = dx * dx + dy * dy
            val share = if (length <= 0.0) 0.0 else (-(ax * dx + ay * dy) / length).coerceIn(0.0, 1.0)
            val at = (a.first + (b.first - a.first) * share) to (a.second + (b.second - a.second) * share)
            val distance = meters(at, point)
            if (best == null || distance < best.meters - 1e-9) best = Hit(index, share, at, distance)
        }
        return best!!
    }

    /** The index of the last point of [path] within [window] metres along it from its start (at least 1). */
    private fun windowEnd(path: List<Point>, window: Double): Int {
        var length = 0.0
        for (index in 1 until path.size) {
            length += meters(path[index - 1], path[index])
            if (length >= window) return index
        }
        return path.lastIndex
    }

    /** [path] without points that repeat the one before them. */
    fun deduped(path: List<Point>): List<Point> {
        if (path.size < 2) return path
        val out = ArrayList<Point>(path.size)
        for (p in path) if (out.isEmpty() || meters(out.last(), p) > SAME_METERS) out.add(p)
        return out
    }

    /**
     * [path] led from [spot]: cut where [spot] lies beside its first [END_WINDOW_METERS], and
     * [spot] put in front of it where the way does not turn back by [maxTurn] or more to go on
     * from it ([always]: in any case, for a point the way must start at).
     */
    fun attachStart(path: List<Point>, spot: Point, maxTurn: Double = MAX_TURN_DEGREES, always: Boolean = false): List<Point> {
        if (path.isEmpty()) return listOf(spot)
        if (path.size == 1) return if (meters(path[0], spot) <= SAME_METERS) listOf(spot) else listOf(spot, path[0])
        val hit = nearest(path, spot, 0, windowEnd(path, END_WINDOW_METERS))
        val cut = deduped(listOf(hit.point) + path.subList(hit.index + 1, path.size))
        if (meters(spot, cut.first()) <= SAME_METERS) return listOf(spot) + cut.drop(1)
        val second = cut.getOrNull(1)
        return if (always || second == null || turnDegrees(spot, cut[0], second) < maxTurn) listOf(spot) + cut else cut
    }

    /** [path] led on to [spot], as [attachStart] at its other end. */
    fun attachEnd(path: List<Point>, spot: Point, maxTurn: Double = MAX_TURN_DEGREES, always: Boolean = false): List<Point> =
        attachStart(path.asReversed(), spot, maxTurn, always).asReversed().toList()

    /**
     * [path] without the short back-and-forth at its ends: a point next to an end that is
     * less than [maxMeters] from it and where the way turns back by [maxTurn] or more is left
     * out, again and again. Both ends themselves stay.
     */
    fun smoothEnds(path: List<Point>, maxMeters: Double = ZIGZAG_METERS, maxTurn: Double = MAX_TURN_DEGREES): List<Point> {
        val p = deduped(path).toMutableList()
        while (p.size >= 3 && meters(p[0], p[1]) < maxMeters && turnDegrees(p[0], p[1], p[2]) >= maxTurn) p.removeAt(1)
        while (p.size >= 3 && meters(p[p.lastIndex], p[p.lastIndex - 1]) < maxMeters &&
            turnDegrees(p[p.lastIndex], p[p.lastIndex - 1], p[p.lastIndex - 2]) >= maxTurn
        ) {
            p.removeAt(p.lastIndex - 1)
        }
        return p
    }

    /**
     * The way of a ride [path] from where its stop [stop] lies beside it: a provider's way often
     * starts a little before the stop (at the last point of the line's shape before it), so the
     * ride seemed to begin past the platform and run back through it, a V with the walk that
     * leads to it. The way is cut at the point beside [stop] within its first
     * [END_WINDOW_METERS]; of a way that runs past the stop and back, on its way back (the last
     * stretch that comes as close as the nearest, give or take [CLIP_TIE_METERS]). Unchanged
     * where the stop lies more than [maxMeters] from the way or the way already starts beside it.
     */
    fun clipStartAtStop(
        path: List<Point>,
        stop: Point,
        maxMeters: Double = CLIP_STOP_METERS,
        windowMeters: Double = END_WINDOW_METERS,
    ): List<Point> {
        if (path.size < 2) return path
        val window = windowEnd(path, windowMeters)
        val hits = (0 until window).map { nearest(path, stop, it, it + 1) }
        val closest = hits.minOf { it.meters }
        if (closest > maxMeters) return path
        val hit = hits.last { it.meters <= closest + CLIP_TIE_METERS }
        if (hit.index == 0 && meters(hit.point, path[0]) <= SAME_METERS) return path
        val cut = deduped(listOf(hit.point) + path.subList(hit.index + 1, path.size))
        return if (cut.size >= 2) cut else path
    }

    /** [path] up to where its stop [stop] lies beside it, as [clipStartAtStop] at its other end. */
    fun clipEndAtStop(
        path: List<Point>,
        stop: Point,
        maxMeters: Double = CLIP_STOP_METERS,
        windowMeters: Double = END_WINDOW_METERS,
    ): List<Point> = clipStartAtStop(path.asReversed(), stop, maxMeters, windowMeters).asReversed().toList()

    /**
     * A ride of the U-Bahn, S-Bahn or a train is cut at its own platform (see
     * OsmStations.refine): the middle of the platform lies this close to the track at most, and
     * is looked for this far along the way, which may start at the coordinate of the whole
     * station (the S-Bahn's for the U8 at Karl-Bonhoeffer-Nervenklinik, 150 m off its track)
     * and run past the platform before it comes back.
     */
    const val PLATFORM_CLIP_METERS = 60.0
    const val PLATFORM_WINDOW_METERS = 700.0

    /** A stop farther than this from the way of its ride is not where the way is cut. */
    const val CLIP_STOP_METERS = 150.0

    /** Stretches this much farther from a stop than the nearest one count as just as near. */
    const val CLIP_TIE_METERS = 5.0

    /**
     * The walk [way] between two rides as drawn: from [from] (where the arriving ride ends) to
     * the point of [way] nearest to it, along [way] to its point nearest to [to] (where the next
     * ride starts) and on to [to]. A missing end stays where [way] has it. Without a way long
     * enough, or where [to] lies before [from] along it, the two ends joined straight.
     */
    fun clipWalk(way: List<Point>, from: Point?, to: Point?): List<Point> {
        if (way.size < 2) return smoothEnds(listOfNotNull(from) + way + listOfNotNull(to))
        val a = if (from != null) nearest(way, from) else Hit(0, 0.0, way.first(), 0.0)
        val b = if (to != null) nearest(way, to, a.index, way.lastIndex) else Hit(way.lastIndex - 1, 1.0, way.last(), 0.0)
        val forward = b.index > a.index || (b.index == a.index && b.share >= a.share)
        val middle = if (forward) listOf(a.point) + way.subList(a.index + 1, b.index + 1) + b.point else listOf(a.point)
        val start = from ?: way.first()
        val end = to ?: way.last()
        val joined = if (forward) listOf(start) + middle + end else listOf(start, end)
        return smoothEnds(joined)
    }
}
