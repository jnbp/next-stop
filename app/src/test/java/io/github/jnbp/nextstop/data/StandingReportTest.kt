package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "steht" only for a real stand (see reportsStanding): a bus that stops at each station is
 * not said to stand there; one that stands a minute away from its stops, or longer than a
 * normal dwell at one, is. Made-up places: a bus runs east from Example A over Example M
 * (half way, ~1.76 km) to Example B.
 */
class StandingReportTest {
    private fun at(clock: String): OffsetDateTime = OffsetDateTime.parse("2026-01-01T${clock}:00+01:00")
    private val lat = 50.7
    private fun stop(name: String, east: Double) = Stop(name, name, lat, 7.1 + east, null)
    private val a = stop("Example A", 0.0)
    private val m = stop("Example M", 0.025)
    private val b = stop("Example B", 0.05)

    private val bus = Leg(
        "100", "bus", false, a.name, b.name, at("09:30"), at("09:30"), at("10:00"), at("09:58"), null, null, null, false,
        realtime = true,
        path = listOf(a.position!!, m.position!!, b.position!!),
        stopovers = listOf(
            Stopover(a.name, null, at("09:30"), a),
            Stopover(m.name, at("09:44"), at("09:44"), m),
            Stopover(b.name, at("10:00"), null, b),
        ),
        fromPlace = a, toPlace = b, tripId = "bus-100",
    )

    private val now = at("09:45")
    private val total = wayMeters(bus.path)
    private val atM = alongPath(bus.path, m.position!!).first

    // The fixes at [along] metres every [step] seconds up to [until], at [speed]: the last RideEta.
    private fun stood(along: Double, nextStop: Int, until: Long, step: Long = 10, speed: Float = 0.2f): RideEta {
        var eta: RideEta? = null
        var t = 0L
        while (t <= until) {
            val fix = PositionFix("j", 0, FixKind.OnRide, along, total - along, nextStop, t * 1000, speed)
            eta = nextRideEta(eta, bus, fix, now.plusSeconds(t))
            t += step
        }
        return eta!!
    }

    @Test
    fun aNormalStopAtAStationIsNotStanding() {
        // At Example M (the next stop ahead is B, index 2): 30 s, 70 s, no "steht".
        assertFalse(stood(atM, 2, 30).standing)
        assertFalse(stood(atM, 2, 70).standing)
        // 20 m short of it, with M still ahead, the same.
        assertFalse(stood(atM - 20, 1, 80).standing)
        // Longer than a normal dwell: it stands.
        assertTrue(stood(atM, 2, 100).standing)
    }

    @Test
    fun aMinuteAwayFromTheStopsIsStanding() {
        // 900 m after Example A, far from every stop: not at once, after a minute.
        assertFalse(stood(900.0, 1, 0).standing)
        assertFalse(stood(900.0, 1, 50).standing)
        assertTrue(stood(900.0, 1, 60).standing)
    }

    @Test
    fun movingAgainEndsTheStand() {
        val standing = stood(900.0, 1, 70)
        assertTrue(standing.standing)
        val fix = PositionFix("j", 0, FixKind.OnRide, 950.0, total - 950.0, 1, 80_000, 9f)
        val moving = nextRideEta(standing, bus, fix, now.plusSeconds(80))
        assertFalse(moving.standing)
        assertTrue(moving.stillSinceMillis == null)
    }
}
