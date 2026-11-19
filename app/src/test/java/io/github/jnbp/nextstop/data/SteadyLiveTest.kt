package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * #60, 18.11. at Koeln Messe/Deutz: ICE 621 planned 09:46, the board said 09:49; the companion
 * said 09:51, then 09:48, then 09:51 again, as refreshes from different backends alternated. A
 * small step back of a departure still ahead is only shown once a second refresh confirms it.
 */
class SteadyLiveTest {
    private fun at(clock: String): OffsetDateTime = OffsetDateTime.parse("2026-11-18T${clock}:00+01:00")

    private fun journey(departure: String) = Journey(
        listOf(
            Leg(
                "ICE 621", "express", false, "Köln Messe/Deutz Bf", "Frankfurt (Main) Hauptbahnhof",
                at(departure), at("09:46"), at("10:53"), at("10:51"), "11", "8", null, false,
                realtime = true, tripId = "ice621",
            ),
        ),
    )

    @Test
    fun aSmallStepBackIsHeldUntilConfirmed() {
        val steady = SteadyLive()
        val now = at("09:40")
        val shown = steady.steady(journey("09:51"), journey("09:51"), now)
        // One refresh says 09:48: held at 09:51.
        val second = steady.steady(journey("09:48"), shown, now)
        assertEquals(at("09:51"), second.legs[0].departure)
        // Back at 09:51: no jump at all.
        val third = steady.steady(journey("09:51"), second, now)
        assertEquals(at("09:51"), third.legs[0].departure)
        // 09:48 twice in a row: taken.
        val fourth = steady.steady(journey("09:48"), third, now)
        assertEquals(at("09:51"), fourth.legs[0].departure)
        val fifth = steady.steady(journey("09:48"), fourth, now)
        assertEquals(at("09:48"), fifth.legs[0].departure)
    }

    @Test
    fun laterAndBigChangesComeAtOnce() {
        val steady = SteadyLive()
        val now = at("09:40")
        // Later: at once (the train will not come before it).
        assertEquals(at("09:54"), steady.steady(journey("09:54"), journey("09:51"), now).legs[0].departure)
        // Earlier by more than 3 minutes: a real change, at once.
        assertEquals(at("09:46"), steady.steady(journey("09:46"), journey("09:51"), now).legs[0].departure)
        // A departure already past is not held either.
        assertEquals(at("09:48"), steady.steady(journey("09:48"), journey("09:51"), at("09:52")).legs[0].departure)
    }
}
