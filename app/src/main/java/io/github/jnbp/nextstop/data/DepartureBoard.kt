package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime

/**
 * The time a departure takes its place in a board by: the live time of a run that goes, the
 * planned time of a cancelled one. BVG HAFAS sends a cancelled run without a live time, and
 * its answer sorts such runs by an empty time, so they would come first.
 */
fun departureOrderTime(departure: Departure): OffsetDateTime? =
    if (departure.cancelled) departure.plannedTime ?: departure.time else departure.time ?: departure.plannedTime

/** [departures] in the order they leave (see [departureOrderTime]); those without any time at the end. */
fun inDepartureOrder(departures: List<Departure>): List<Departure> =
    departures.sortedWith(compareBy(nullsLast()) { departure: Departure -> departureOrderTime(departure)?.toInstant() })

/**
 * Where the next page of a board starts: at the planned time of its last departure, so a
 * page overlaps the one before by that minute and no run between them is lost (the overlap
 * is merged away, see mergeDepartures). Null for an empty board.
 */
fun nextDeparturesStart(board: List<Departure>): OffsetDateTime? {
    val last = board.lastOrNull() ?: return null
    return last.plannedTime ?: last.time
}

/** The ring lines of the Berlin S-Bahn and the way they run round. */
enum class RingWay { Clockwise, Counterclockwise }

// The arrows the providers put behind the ring as its direction ("Ring S41 ⟳").
private val CLOCKWISE_ARROWS = listOf("⟳", "↻", "\uD83D\uDD03")
private val COUNTERCLOCKWISE_ARROWS = listOf("⟲", "↺", "\uD83D\uDD04")
private val RING_WORD = Regex("\\bring(bahn)?\\b", RegexOption.IGNORE_CASE)

/**
 * The way round of a ride of the Berlin ring, from its [line] (S41 runs clockwise, S42
 * counterclockwise) or, for another line, from the arrow in its [direction]; null for a
 * ride that does not go round the ring. The providers name the direction "Ring S41 ⟳":
 * the line twice in one row, and an arrow few can read. The rule by the name holds only for
 * an S-Bahn ([product]) in the area of the VBB ([at], see [LineColors.inHomeArea]): an S41
 * elsewhere is another line, which goes by its arrows alone.
 */
fun ringWayOf(line: String, direction: String, product: String = "suburban", at: Pair<Double, Double>? = null): RingWay? {
    val clockwise = CLOCKWISE_ARROWS.any { direction.contains(it) }
    val counterclockwise = COUNTERCLOCKWISE_ARROWS.any { direction.contains(it) }
    if (!clockwise && !counterclockwise && !RING_WORD.containsMatchIn(direction)) return null
    val berlinRing = product == "suburban" && LineColors.inHomeArea(at)
    return when (if (berlinRing) normalLine(line) else null) {
        "S41" -> RingWay.Clockwise
        "S42" -> RingWay.Counterclockwise
        else -> when {
            clockwise && !counterclockwise -> RingWay.Clockwise
            counterclockwise && !clockwise -> RingWay.Counterclockwise
            else -> null
        }
    }
}

/**
 * [departures] with each ride once: two runs of the same line (as [normalLine] gives it, after
 * the provider's name was completed, see rapidLineName) towards the same place (as
 * [stationNameKey] gives it) at the same planned minute from the same platform, or where one
 * of them names no platform, are one ride. BVG HAFAS can send an S1 and an extra "1" that way,
 * also without the remark that marks the extra run. The run kept is the one that goes, then
 * the regular one (not additional), then the one with live data; it keeps its own place, the
 * others are left out. Works on one board as it is, in any order.
 */
fun withoutDoubleRides(departures: List<Departure>): List<Departure> {
    val dropped = mutableSetOf<Int>()
    departures.indices.forEach { index ->
        if (index in dropped) return@forEach
        val group = departures.indices.filter { other -> other !in dropped && (other == index || oneRide(departures[index], departures[other])) }
        if (group.size < 2) return@forEach
        val keep = group.minWith(compareBy<Int>({ rideRank(departures[it]) }, { it }))
        dropped += group - keep
    }
    return departures.filterIndexed { index, _ -> index !in dropped }
}

/** Lower is better: a run that goes, then a regular one, then one with live data. */
private fun rideRank(departure: Departure): Int =
    (if (departure.cancelled) 4 else 0) + (if (departure.additional) 2 else 0) +
        (if (departure.time != null && departure.delaySeconds != null) 0 else 1)

/** True when two runs of one board are one ride (see [withoutDoubleRides]). */
internal fun oneRide(a: Departure, b: Departure): Boolean {
    if (sameTrainRun(a, b)) return true
    if (normalLine(a.line) != normalLine(b.line)) return false
    val one = stationNameKey(a.direction)
    if (one.isEmpty() || one != stationNameKey(b.direction)) return false
    val first = (a.plannedTime ?: a.time)?.toEpochSecond()?.div(60) ?: return false
    val second = (b.plannedTime ?: b.time)?.toEpochSecond()?.div(60) ?: return false
    if (first != second) return false
    val x = a.platform?.trim().orEmpty()
    val y = b.platform?.trim().orEmpty()
    return x.isEmpty() || y.isEmpty() || x.equals(y, ignoreCase = true)
}

// Two departures of one train number this far apart as planned are one train sent by two feeds.
private const val SAME_TRAIN_MINUTES = 2L

// The train number at the end of a long-distance train's name: "ICE 9552", "TGV INOUI 9898", "EC 65".
private val TRAIN_NUMBER = Regex("(?:^|[^0-9])([0-9]{2,6})$")

/**
 * True when [a] and [b] are one long-distance train that two feeds both send (Transitous,
 * 18 Nov 2026: the ICE 9591 at Strasbourg from DELFI towards "Frankfurt (Main) Hauptbahnhof"
 * and from the SNCF towards "Francfort sur le Main"; the TGV 9218 at Basel SBB from the SBB
 * and the SNCF): both long-distance, the same train number, planned within
 * [SAME_TRAIN_MINUTES], and directions that may name one place (see
 * StationIdentity.directionsMayMatch). A cancelled run and an extra one are never one train.
 */
internal fun sameTrainRun(a: Departure, b: Departure): Boolean {
    if (a.product != "express" || b.product != "express") return false
    if (a.cancelled != b.cancelled && (a.additional || b.additional)) return false
    val number = TRAIN_NUMBER.find(a.line.trim())?.groupValues?.get(1)?.trimStart('0') ?: return false
    if (number.isEmpty() || number != TRAIN_NUMBER.find(b.line.trim())?.groupValues?.get(1)?.trimStart('0')) return false
    val first = (a.plannedTime ?: a.time)?.toEpochSecond() ?: return false
    val second = (b.plannedTime ?: b.time)?.toEpochSecond() ?: return false
    if (kotlin.math.abs(first - second) > SAME_TRAIN_MINUTES * 60) return false
    return StationIdentity.directionsMayMatch(a.direction, b.direction)
}
