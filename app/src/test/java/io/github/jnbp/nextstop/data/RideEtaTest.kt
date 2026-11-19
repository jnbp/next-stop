package io.github.jnbp.nextstop.data

import java.time.Duration
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The arrival on board from position and live times (RideState.kt), with the cases of the test
 * rides of 17.11.: the replacement bus 2 km before its end while its live time still says 21 min,
 * the same bus standing in a jam the live times had priced in, and a ride where both agree.
 */
class RideEtaTest {
    private val now = OffsetDateTime.parse("2026-01-01T09:45:00+01:00")

    private fun seconds(to: OffsetDateTime) = Duration.between(now, to).seconds

    @Test
    fun positionAheadOfTheLiveTimeWins() {
        // 2 km left at 15 m/s, the live time says 21 min (it implies 1.6 m/s): far off, set aside.
        val eta = blendedArrival(2_000.0, 15.0, now.plusMinutes(21), rideSpeed = 10.0, now = now)
        assertFalse(eta.standing)
        assertTrue(eta.realtimeDiscarded)
        assertTrue(seconds(eta.arrival) in 120..240)
    }

    @Test
    fun aStandingVehicleKeepsTheLiveTimeAndSaysItStands() {
        // In a jam: 1.5 km left, 0.4 m/s, the live time with the jam in it says 10 min.
        val eta = blendedArrival(1_500.0, 0.4, now.plusMinutes(10), rideSpeed = 8.0, now = now)
        assertTrue(eta.standing)
        assertTrue(eta.realtimeUsed)
        assertEquals(now.plusMinutes(10), eta.arrival)
    }

    @Test
    fun aStandingVehiclePastItsLiveTimeIsNotExtrapolatedAtZero() {
        // The live time is already over: the ride's own speed stands in, never 0 km/h.
        val eta = blendedArrival(800.0, 0.0, now.minusMinutes(1), rideSpeed = 8.0, now = now)
        assertTrue(eta.standing)
        assertEquals(100L, seconds(eta.arrival))
    }

    @Test
    fun crawlingInAJamTrustsTheLiveTime() {
        // 2 m/s, 1.5 km, live 12.5 min (2 m/s implied): plausible for this speed, blended.
        val eta = blendedArrival(1_500.0, 2.0, now.plusSeconds(750), rideSpeed = 10.0, now = now)
        assertTrue(eta.realtimeUsed)
        assertEquals(750L, seconds(eta.arrival))
    }

    @Test
    fun agreeingSpeedsBlend() {
        // GPS 10 m/s, the live time implies 8 m/s for 2.4 km: half each, 9 m/s.
        val eta = blendedArrival(2_400.0, 10.0, now.plusSeconds(300), rideSpeed = 9.0, now = now)
        assertTrue(eta.realtimeUsed)
        assertEquals(267L, seconds(eta.arrival))
    }

    @Test
    fun theShownArrivalEasesAndNeverJumps() {
        val shown = now.plusMinutes(21)
        val target = now.plusMinutes(3)
        val first = easeArrival(shown, target, 15.0)
        assertTrue(first.isBefore(shown) && first.isAfter(target))
        // Each step closes a good part of the gap, never all of it at once.
        val gap = Duration.between(target, shown).seconds
        val left = Duration.between(target, first).seconds
        assertTrue(left in gap / 4..gap * 3 / 4)
        // Close enough, it takes the target.
        assertEquals(target, easeArrival(target.plusSeconds(8), target, 15.0))
        assertEquals(target, easeArrival(null, target, 15.0))
    }

    @Test
    fun theSpeedIsSmoothed() {
        assertEquals(10.0, smoothSpeed(null, 10f, 0.0)!!, 1e-9)
        val next = smoothSpeed(10.0, 0f, 15.0)!!
        assertTrue(next in 5.0..9.0)
        assertEquals(10.0, smoothSpeed(10.0, null, 15.0)!!, 1e-9)
    }

    @Test
    fun theTripIsRefreshedOftenOnlyCloseToADeparture() {
        val leg = Leg(
            "66", "tram", false, "Example A", "Example B", now.plusMinutes(4), now.plusMinutes(4), now.plusMinutes(20), now.plusMinutes(20),
            null, null, null, false,
        )
        val journey = Journey(listOf(leg))
        assertEquals(CLOSE_REFRESH_MILLIS, tripRefreshMillis(journey, now, 30_000))
        assertEquals(30_000L, tripRefreshMillis(journey, now.minusMinutes(10), 30_000))
        // Waited for past its time, still often.
        assertEquals(CLOSE_REFRESH_MILLIS, tripRefreshMillis(journey, now.plusMinutes(6), 30_000))
    }
}
