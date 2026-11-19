package io.github.jnbp.nextstop.wear.complication

import io.github.jnbp.nextstop.wear.data.StepKind
import io.github.jnbp.nextstop.wear.data.TripSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OngoingStatusTest {
    private fun text(trip: TripSnapshot, count: String) = statusText(
        statusLead(tripState(trip), trip.kind, trip.leave, trip.line), trip.line, count, "Aussteigen", "Umsteigen", "Los",
    )

    @Test
    fun theStatusIsShort() {
        val waiting = TripSnapshot(active = true, kind = StepKind.Board, line = "U3", place = "Example Square")
        assertEquals("U3 · 6'", text(waiting, "6'"))
        val riding = TripSnapshot(active = true, kind = StepKind.GetOff, line = "U3", place = "Sample Street", stopsLeft = 4)
        assertEquals("U3 · 9'", text(riding, "9'"))
        assertEquals("Aussteigen 2'", text(riding.copy(stopsLeft = 1), "2'"))
        assertEquals("Umsteigen 2'", text(riding.copy(stopsLeft = 1, change = true), "2'"))
        assertEquals("Los · 5'", text(waiting.copy(leave = true), "5'"))
        assertEquals("4'", text(TripSnapshot(active = true, kind = StepKind.WalkToEnd, place = "Test Park"), "4'"))
        // Waiting for the next ride after a change: its line, the icon tells the change.
        assertEquals("S7 · 3'", text(waiting.copy(line = "S7", planIndex = 1), "3'"))
        assertEquals("3'", text(waiting.copy(line = null), "3'"))
    }

    @Test
    fun theStatusUsesTheIconOfTheComplication() {
        val riding = TripSnapshot(active = true, kind = StepKind.GetOff, line = "M4", product = "tram", stopsLeft = 3)
        assertEquals(TripGlyph.Tram, tripGlyph(tripState(riding), riding.product))
        assertEquals(TripGlyph.Exit, tripGlyph(tripState(riding.copy(stopsLeft = 1)), riding.product))
    }

    @Test
    fun aSilentTripOfThePhoneEndsWhenItTurnsStale() {
        val t0 = 1_800_000_000_000L
        val trip = TripSnapshot(active = true, kind = StepKind.GetOff, arrival = t0 + 20 * 60_000L, updated = t0, fromPhone = true)
        val at = trip.staleAt()
        assertTrue(at > t0)
        assertFalse(trip.stale(at - 1))
        assertTrue(trip.stale(at))
        assertEquals(0L, trip.copy(fromPhone = false).staleAt())
        assertEquals(0L, trip.copy(active = false).staleAt())
        assertEquals(0L, trip.copy(arrival = 0, updated = 0).staleAt())
        // Near the arrival the arrival decides.
        val late = trip.copy(updated = trip.arrival + 4 * 60_000L)
        assertFalse(late.stale(late.staleAt() - 1))
        assertTrue(late.stale(late.staleAt()))
    }
}
