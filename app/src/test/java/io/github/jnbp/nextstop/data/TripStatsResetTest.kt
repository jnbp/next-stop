package io.github.jnbp.nextstop.data

import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Statistik zurücksetzen" (#195): forgetting the recorded trips, and only them. */
class TripStatsResetTest {
    /** A store in memory, as the preferences file of the statistics. */
    private class Memory(var stored: String?) : TripRecordText {
        var writes = 0
        override fun read(): String? = stored
        override fun write(text: String?) {
            writes++
            stored = text
        }
    }

    private fun record(day: LocalDate, line: String) = TripRecord(
        id = "$day$line",
        day = day,
        startMillis = day.toEpochDay() * 86_400_000L,
        endMillis = day.toEpochDay() * 86_400_000L + 20 * 60_000L,
        legs = listOf(StatLeg(line, "subway", null, 0L, null, 0L, 1000.0)),
        arrivalDelayMinutes = 0,
    )

    private val today = LocalDate.of(2026, 11, 16)

    @Test
    fun clearForgetsEveryTripAndTheStoredText() {
        val first = record(today.minusDays(2), "U7")
        val second = record(today.minusDays(1), "M27")
        val memory = Memory(tripRecordsJson(listOf(first, second)))
        val state = MutableStateFlow<List<TripRecord>?>(null)
        val book = TripRecordBook(state)
        assertEquals(2, book.load(memory).size)
        assertEquals(2, tripSummary(state.value!!, today).trips)

        book.clear(memory)

        assertEquals(emptyList<TripRecord>(), state.value)
        assertNull(memory.stored)
        assertEquals(0, tripSummary(state.value!!, today).trips)
        assertTrue(mostUsedLines(state.value!!).isEmpty())
        assertNull(averageDelayMinutes(state.value!!))
    }

    @Test
    fun aFreshStartAfterTheResetReadsNoTrip() {
        val memory = Memory(tripRecordsJson(listOf(record(today, "U7"))))
        TripRecordBook(MutableStateFlow(null)).apply {
            load(memory)
            clear(memory)
        }
        // The next start of the app reads the store anew.
        assertEquals(emptyList<TripRecord>(), TripRecordBook(MutableStateFlow(null)).load(memory))
    }

    @Test
    fun clearWithoutLoadingFirstStillEmpties() {
        val memory = Memory(tripRecordsJson(listOf(record(today, "U7"))))
        val state = MutableStateFlow<List<TripRecord>?>(null)
        TripRecordBook(state).clear(memory)
        assertEquals(emptyList<TripRecord>(), state.value)
        assertNull(memory.stored)
    }

    @Test
    fun tripsAfterTheResetAreRecordedAgain() {
        val memory = Memory(null)
        val state = MutableStateFlow<List<TripRecord>?>(null)
        val book = TripRecordBook(state)
        book.add(memory, record(today.minusDays(1), "U7"))
        book.clear(memory)
        book.add(memory, record(today, "S1"))
        assertEquals(listOf("S1"), state.value!!.flatMap { record -> record.legs.map { it.line } })
        assertEquals(1, tripRecordsOf(memory.stored).size)
    }
}
