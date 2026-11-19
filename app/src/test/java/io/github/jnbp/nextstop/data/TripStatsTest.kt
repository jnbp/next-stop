package io.github.jnbp.nextstop.data

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The travel statistics: what is recorded of a trip, and what the strip and the details show. */
class TripStatsTest {
    private val zone = ZoneOffset.UTC
    private val base = OffsetDateTime.of(2026, 11, 16, 8, 0, 0, 0, zone)
    private fun at(minutes: Long) = base.plusMinutes(minutes)
    private val a = Stop("a", "Musterstraße", 52.50, 13.30, null)
    private val b = Stop("b", "Example Platz", 52.51, 13.30, null)
    private val c = Stop("c", "Example Hof", 52.51, 13.32, null)

    private fun ride(line: String, product: String, from: Stop, to: Stop, leaves: Long, arrives: Long, late: Long = 0) = Leg(
        line, product, false, from.name, to.name, at(leaves + late), at(leaves), at(arrives + late), at(arrives),
        null, null, null, false, fromPlace = from, toPlace = to,
    )

    private val journey = Journey(listOf(ride("U7", "subway", a, b, 0, 10), ride("M27", "tram", b, c, 14, 22, late = 4)))

    private fun record(day: LocalDate, delay: Int?, meters: Double = 1000.0, lines: List<String> = listOf("U7"), minutes: Long = 20) =
        TripRecord(
            id = "$day$delay$lines$minutes",
            day = day,
            startMillis = day.toEpochDay() * 86_400_000L,
            endMillis = day.toEpochDay() * 86_400_000L + minutes * 60_000L,
            legs = lines.map { StatLeg(it, "subway", null, 0L, null, 0L, meters / lines.size) },
            arrivalDelayMinutes = delay,
        )

    @Test
    fun aTripRiddenToItsEndIsRecorded() {
        val record = tripRecordOf(journey, at(26), zone)
        assertNotNull(record)
        record!!
        assertEquals(listOf("U7", "M27"), record.legs.map { it.line })
        assertEquals(4, record.arrivalDelayMinutes)
        assertEquals(false, record.punctual)
        assertEquals(LocalDate.of(2026, 11, 16), record.day)
        // About 1.1 km north, then about 1.4 km east: in a straight line without a path.
        assertTrue(record.meters in 2400.0..2700.0)
    }

    @Test
    fun aTripGivenUpOnTheWayIsNotRecorded() {
        assertNull(tripRecordOf(journey, at(12), zone))
        // Ended just before the arrival: counts.
        assertNotNull(tripRecordOf(journey, at(24), zone))
    }

    @Test
    fun theSameJourneyIsKeptOnce() {
        val record = tripRecordOf(journey, at(26), zone)!!
        val twice = withRecord(withRecord(emptyList(), record), record.copy(arrivalDelayMinutes = 2))
        assertEquals(1, twice.size)
        assertEquals(2, twice.single().arrivalDelayMinutes)
    }

    @Test
    fun theSummaryCountsTheLastThirtyDays() {
        val today = LocalDate.of(2026, 11, 17)
        val records = listOf(
            record(today, 0, 2000.0),
            record(today.minusDays(5), 5, 3000.0),
            record(today.minusDays(29), 1, 1600.0),
            record(today.minusDays(30), 0, 9000.0),
            record(today.minusDays(2), null, 400.0),
        )
        val summary = tripSummary(records, today)
        assertEquals(4, summary.trips)
        assertEquals(7, summary.km)
        // Two of three known arrivals were punctual.
        assertEquals(67, summary.punctualPercent)
        assertEquals(TripSummary(0, 0, null), tripSummary(emptyList(), today))
    }

    @Test
    fun tripsPerWeekGoMondayToSundayOldestFirst() {
        // 2026-11-17 is a Tuesday; the week began on Monday the 16th.
        val today = LocalDate.of(2026, 11, 17)
        val records = listOf(
            record(LocalDate.of(2026, 11, 16), 0),
            record(LocalDate.of(2026, 11, 17), 0),
            record(LocalDate.of(2026, 11, 15), 0),
            record(LocalDate.of(2026, 11, 9), 0),
            record(LocalDate.of(2026, 9, 1), 0),
        )
        val weeks = tripsPerWeek(records, today, weeks = 4)
        assertEquals(listOf(0, 0, 2, 2), weeks.toList())
    }

    @Test
    fun mostUsedLinesCountEachTripOnce() {
        val day = LocalDate.of(2026, 11, 10)
        val records = listOf(
            record(day, 0, lines = listOf("U7", "U7")),
            record(day.plusDays(1), 0, lines = listOf("U7", "M27")),
            record(day.plusDays(2), 0, lines = listOf("M27")),
            record(day.plusDays(3), 0, lines = listOf("S41")),
        )
        val top = mostUsedLines(records, count = 2)
        assertEquals(listOf("M27" to 2, "U7" to 2), top.map { it.line to it.trips })
    }

    @Test
    fun averageDelayTakesEarlyAsOnTime() {
        val day = LocalDate.of(2026, 11, 10)
        assertNull(averageDelayMinutes(listOf(record(day, null))))
        assertEquals(2.0, averageDelayMinutes(listOf(record(day, -3), record(day, 4), record(day, 2), record(day, null)))!!, 1e-9)
    }

    @Test
    fun longestTripByTime() {
        val day = LocalDate.of(2026, 11, 10)
        val long = record(day, 0, minutes = 95)
        assertEquals(long, longestTrip(listOf(record(day, 0, minutes = 20), long, record(day, 0, minutes = 40))))
        assertNull(longestTrip(emptyList()))
    }

    @Test
    fun recordsSurviveTheirJson() {
        val record = tripRecordOf(journey, at(26), zone)!!
        val back = tripRecordsOf(tripRecordsJson(listOf(record, record(LocalDate.of(2026, 1, 2), null))))
        assertEquals(2, back.size)
        assertEquals(record, back[0])
        assertNull(back[1].arrivalDelayMinutes)
        assertEquals(emptyList<TripRecord>(), tripRecordsOf("not json"))
    }
}
