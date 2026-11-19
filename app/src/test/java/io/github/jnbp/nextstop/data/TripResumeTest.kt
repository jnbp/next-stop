package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** #117: a followed trip comes back after an update of the app, an ended one does not. */
class TripResumeTest {
    private fun at(clock: String): OffsetDateTime = OffsetDateTime.parse("2026-11-18T${clock}:00+01:00")

    private fun ride(line: String, departure: String, arrival: String) = Leg(
        line, "regional", false, "Example Start", "Example End", at(departure), at(departure), at(arrival), at(arrival),
        null, null, null, false, tripId = "$line-$departure",
    )

    private val trip = Journey(listOf(ride("RE1", "14:02", "14:40"), ride("S5", "14:48", "15:10")))
    private val saved = at("14:00").toInstant().toEpochMilli()

    private fun millis(clock: String) = at(clock).toInstant().toEpochMilli()

    @Test
    fun aTripUnderWayComesBack() {
        assertEquals(trip, resumableTrip(trip, saved, millis("14:20"), at("14:20")))
        // Just arrived: still within the few minutes the companion stays.
        assertNotNull(resumableTrip(trip, saved, millis("15:13"), at("15:13")))
    }

    @Test
    fun anEndedOrStaleTripDoesNot() {
        assertNull(resumableTrip(trip, saved, millis("15:16"), at("15:16")))
        assertNull(resumableTrip(null, saved, millis("14:20"), at("14:20")))
        // A mark from the future (clock changed) or older than the limit is not trusted.
        assertNull(resumableTrip(trip, millis("14:30"), millis("14:20"), at("14:20")))
        assertNull(resumableTrip(trip, saved - RESUME_MAX_AGE_MILLIS - 1, millis("14:20"), at("14:20")))
    }

    @Test
    fun theMarkKeepsTheTrip() {
        val text = tripMarkJson(trip, saved)
        val read = tripMarkOf(text)
        assertNotNull(read)
        assertEquals(saved, read!!.first)
        assertEquals(trip.legs.map { it.line }, read.second.legs.map { it.line })
        assertEquals(trip.arrival, read.second.arrival)
        assertNull(tripMarkOf("not json"))
        assertNull(tripMarkOf("{\"v\":2}"))
    }

    @Test
    fun theKeyChangesOnlyWithTheWay() {
        val later = trip.copy(legs = listOf(trip.legs[0], trip.legs[1].copy(arrival = at("15:14"))))
        assertEquals(tripMarkKey(trip), tripMarkKey(later))
        val other = Journey(listOf(ride("RE1", "14:02", "14:40"), ride("S7", "14:52", "15:12")))
        assertNotEquals(tripMarkKey(trip), tripMarkKey(other))
        assertNull(tripMarkKey(null))
    }
}
