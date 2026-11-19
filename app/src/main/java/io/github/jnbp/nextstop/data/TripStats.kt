package io.github.jnbp.nextstop.data

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import io.github.jnbp.nextstop.ui.shortLine

/*
 * The user's own travel statistics: every trip followed with the companion to its end is kept
 * on the device (nothing leaves it), in a small preferences file as JSON. What "My routes"
 * shows of it (trips, kilometres, punctuality, trips per week, most used lines...) is worked
 * out by the pure functions below.
 */

/** One ride of a recorded trip: its line, kind of transport, times (epoch millis) and length. */
data class StatLeg(
    val line: String,
    val product: String,
    val plannedDeparture: Long?,
    val departure: Long,
    val plannedArrival: Long?,
    val arrival: Long,
    val meters: Double,
)

/** A finished trip: the day it began ([day], ISO date), its rides, and how late it arrived. */
data class TripRecord(
    val id: String,
    val day: LocalDate,
    val startMillis: Long,
    val endMillis: Long,
    val legs: List<StatLeg>,
    /** Minutes late at the end of the last ride (negative: early); null where not known. */
    val arrivalDelayMinutes: Int?,
) {
    val meters: Double get() = legs.sumOf { it.meters }
    val minutes: Long get() = (endMillis - startMillis) / 60_000L
    /** On time at the end: less than [PUNCTUAL_MINUTES] late; null where not known. */
    val punctual: Boolean? get() = arrivalDelayMinutes?.let { it < PUNCTUAL_MINUTES }
}

/** What the strip of "My routes" shows: trips, kilometres and the share of punctual arrivals (null: none known). */
data class TripSummary(val trips: Int, val km: Int, val punctualPercent: Int?)

/** A line among the most used ones, with how many recorded trips rode it. */
data class LineUse(val line: String, val product: String, val trips: Int)

/** Arrivals count as punctual below this many minutes late (as the networks count a little lateness). */
const val PUNCTUAL_MINUTES = 3

/** The trips kept at most; the oldest go first. */
const val MAX_TRIP_RECORDS = 600

/** The days the strip of "My routes" sums up. */
const val SUMMARY_DAYS = 30L

/**
 * The record of [journey], followed to [now]; null when it was not ridden to its end: no ride,
 * or ended more than [ARRIVAL_SLACK_MINUTES] before the last ride arrives (a trip given up or
 * replaced on the way does not count).
 */
fun tripRecordOf(journey: Journey, now: OffsetDateTime, zone: ZoneId = ZoneId.systemDefault()): TripRecord? {
    val rides = journey.rides.filter { !it.cancelled }
    if (rides.isEmpty()) return null
    val last = rides.last()
    if (now.isBefore(last.arrival.minusMinutes(ARRIVAL_SLACK_MINUTES))) return null
    val legs = rides.map { leg ->
        StatLeg(
            line = shortLine(leg.line) ?: leg.product,
            product = leg.product,
            plannedDeparture = leg.plannedDeparture?.toInstant()?.toEpochMilli(),
            departure = leg.departure.toInstant().toEpochMilli(),
            plannedArrival = leg.plannedArrival?.toInstant()?.toEpochMilli(),
            arrival = leg.arrival.toInstant().toEpochMilli(),
            meters = legMeters(leg),
        )
    }
    val delay = last.plannedArrival?.let { Duration.between(it, last.arrival).toMinutes().toInt() }
    val start = rides.first().departure
    return TripRecord(
        id = journey.id,
        day = start.atZoneSameInstant(zone).toLocalDate(),
        startMillis = start.toInstant().toEpochMilli(),
        endMillis = last.arrival.toInstant().toEpochMilli(),
        legs = legs,
        arrivalDelayMinutes = delay,
    )
}

/** How long a ride was: along its path where there is one, else in a straight line between its ends. */
internal fun legMeters(leg: Leg): Double {
    if (leg.path.size >= 2) return leg.path.zipWithNext { a, b -> spotMeters(a, b) }.sum()
    val from = leg.fromPlace?.position ?: return 0.0
    val to = leg.toPlace?.position ?: return 0.0
    return spotMeters(from, to)
}

/** The trips of the last [days] days up to [today], summed up. */
fun tripSummary(records: List<TripRecord>, today: LocalDate, days: Long = SUMMARY_DAYS): TripSummary {
    val first = today.minusDays(days - 1)
    val shown = records.filter { !it.day.isBefore(first) && !it.day.isAfter(today) }
    val known = shown.mapNotNull { it.punctual }
    return TripSummary(
        trips = shown.size,
        km = Math.round(shown.sumOf { it.meters } / 1000.0).toInt(),
        punctualPercent = if (known.isEmpty()) null else Math.round(known.count { it } * 100.0 / known.size).toInt(),
    )
}

/** Trips per week (Monday to Sunday) for the last [weeks] weeks up to [today], the oldest first. */
fun tripsPerWeek(records: List<TripRecord>, today: LocalDate, weeks: Int = 8): IntArray {
    val counts = IntArray(weeks)
    val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    records.forEach { record ->
        if (record.day.isAfter(today)) return@forEach
        val monday = record.day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val back = ChronoUnit.WEEKS.between(monday, thisMonday).toInt()
        if (back in 0 until weeks) counts[weeks - 1 - back]++
    }
    return counts
}

/** The [count] lines ridden on the most trips (a line twice on one trip counts once), the most used first. */
fun mostUsedLines(records: List<TripRecord>, count: Int = 3): List<LineUse> {
    val uses = HashMap<String, LineUse>()
    records.forEach { record ->
        record.legs.distinctBy { it.line }.forEach { leg ->
            val known = uses[leg.line]
            uses[leg.line] = LineUse(leg.line, known?.product ?: leg.product, (known?.trips ?: 0) + 1)
        }
    }
    return uses.values.sortedWith(compareByDescending<LineUse> { it.trips }.thenBy { it.line }).take(count)
}

/** The mean delay at arrival in minutes (early arrivals as on time); null without a known one. */
fun averageDelayMinutes(records: List<TripRecord>): Double? {
    val delays = records.mapNotNull { it.arrivalDelayMinutes }
    if (delays.isEmpty()) return null
    return delays.sumOf { it.coerceAtLeast(0) }.toDouble() / delays.size
}

/** The longest trip by time from the first departure to the last arrival; null without trips. */
fun longestTrip(records: List<TripRecord>): TripRecord? = records.maxByOrNull { it.endMillis - it.startMillis }

/** [records] with [record] added: once per journey (the newest copy wins), at most [MAX_TRIP_RECORDS]. */
fun withRecord(records: List<TripRecord>, record: TripRecord): List<TripRecord> =
    (records.filter { it.id != record.id } + record).sortedBy { it.startMillis }.takeLast(MAX_TRIP_RECORDS)

/**
 * Where the recorded trips are kept: one JSON text (the app keeps it in a small preferences
 * file of its own, see TripStats); [write] with null forgets it.
 */
interface TripRecordText {
    fun read(): String?
    fun write(text: String?)
}

/**
 * The recorded trips held in [state] over a [TripRecordText]: read once ([load]), a trip added
 * ([add]), all forgotten ([clear], "Statistik zurücksetzen", #195). [state] follows every change,
 * so the statistics shown follow at once. Only the recorded trips are touched, nothing else.
 */
class TripRecordBook(private val state: MutableStateFlow<List<TripRecord>?>) {
    /** Reads the records once (cheap: one small JSON string). */
    fun load(text: TripRecordText): List<TripRecord> {
        state.value?.let { return it }
        val read = tripRecordsOf(text.read())
        state.compareAndSet(null, read)
        return state.value ?: read
    }

    /** Adds [record] (see [withRecord]) and stores all. */
    fun add(text: TripRecordText, record: TripRecord): List<TripRecord> {
        val all = withRecord(load(text), record)
        state.value = all
        text.write(tripRecordsJson(all))
        return all
    }

    /** Forgets every recorded trip: the statistics start at zero again. */
    fun clear(text: TripRecordText) {
        state.value = emptyList()
        text.write(null)
    }
}

internal fun tripRecordsJson(records: List<TripRecord>): String = JSONArray().apply {
    records.forEach { record ->
        put(
            JSONObject()
                .put("id", record.id)
                .put("day", record.day.toString())
                .put("start", record.startMillis)
                .put("end", record.endMillis)
                .put("delay", record.arrivalDelayMinutes ?: JSONObject.NULL)
                .put(
                    "legs",
                    JSONArray().apply {
                        record.legs.forEach { leg ->
                            put(
                                JSONObject()
                                    .put("line", leg.line)
                                    .put("product", leg.product)
                                    .put("pDep", leg.plannedDeparture ?: JSONObject.NULL)
                                    .put("dep", leg.departure)
                                    .put("pArr", leg.plannedArrival ?: JSONObject.NULL)
                                    .put("arr", leg.arrival)
                                    .put("m", leg.meters),
                            )
                        }
                    },
                ),
        )
    }
}.toString()

internal fun tripRecordsOf(text: String?): List<TripRecord> {
    if (text.isNullOrEmpty()) return emptyList()
    val array = try {
        JSONArray(text)
    } catch (e: Exception) {
        return emptyList()
    }
    return (0 until array.length()).mapNotNull { at ->
        try {
            val item = array.getJSONObject(at)
            val legs = item.getJSONArray("legs")
            TripRecord(
                id = item.getString("id"),
                day = LocalDate.parse(item.getString("day")),
                startMillis = item.getLong("start"),
                endMillis = item.getLong("end"),
                arrivalDelayMinutes = if (item.isNull("delay")) null else item.getInt("delay"),
                legs = (0 until legs.length()).map { index ->
                    val leg = legs.getJSONObject(index)
                    StatLeg(
                        line = leg.getString("line"),
                        product = leg.getString("product"),
                        plannedDeparture = if (leg.isNull("pDep")) null else leg.getLong("pDep"),
                        departure = leg.getLong("dep"),
                        plannedArrival = if (leg.isNull("pArr")) null else leg.getLong("pArr"),
                        arrival = leg.getLong("arr"),
                        meters = leg.getDouble("m"),
                    )
                },
            )
        } catch (e: Exception) {
            null
        }
    }
}

/** Epoch [millis] as a local date-time, for the details. */
fun statTime(millis: Long, zone: ZoneId = ZoneId.systemDefault()): OffsetDateTime =
    OffsetDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)

// A trip ended up to this long before its last ride arrives still counts as ridden to its end.
private const val ARRIVAL_SLACK_MINUTES = 3L
