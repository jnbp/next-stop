package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The wake-up alarm of long rides, on a made-up night train and a short bus after it. */
class WakeAlarmTest {
    private fun at(clock: String): OffsetDateTime = OffsetDateTime.parse("2026-01-01T${clock}:00+01:00")

    private fun ride(line: String, from: String, to: String, dep: String, arr: String) =
        Leg(line, "express", false, from, to, at(dep), at(dep), at(arr), at(arr), null, null, null, false, tripId = "$line@$dep")

    private val night = ride("NJ 1", "Example A", "Example B", "00:13", "03:11")
    private val bus = ride("100", "Example B", "Example C", "03:20", "03:35")
    private val journey = Journey(listOf(night, bus))

    @Test
    fun autoRingsOnlyOnTheLongRideTenMinutesBefore() {
        val targets = wakeTargets(journey, WakeAlarmMode.Auto, 10)
        assertEquals(1, targets.size)
        assertEquals(0, targets[0].legIndex)
        assertEquals(at("03:01"), targets[0].at)
    }

    @Test
    fun alwaysRingsOnEveryRideLongEnough() {
        assertEquals(2, wakeTargets(journey, WakeAlarmMode.Always, 10).size)
        // 15 min ride, 15 min lead: too short.
        assertEquals(1, wakeTargets(journey, WakeAlarmMode.Always, 15).size)
    }

    @Test
    fun theSwitchOfTheTripWins() {
        assertTrue(wakeTargets(journey, WakeAlarmMode.Auto, 10, forTrip = false).isEmpty())
        assertEquals(2, wakeTargets(journey, WakeAlarmMode.Off, 10, forTrip = true).size)
        assertTrue(wakeTargets(journey, WakeAlarmMode.Off, 10).isEmpty())
    }

    @Test
    fun ringsAtItsTimeAndAgainEveryMinuteUntilConfirmed() {
        val targets = wakeTargets(journey, WakeAlarmMode.Auto, 10)
        val key = targets[0].key
        assertEquals(WakeAction.None, wakeAction(targets, emptySet(), null, null, at("02:59")))
        val first = wakeAction(targets, emptySet(), null, null, at("03:01"))
        assertTrue(first is WakeAction.Ring && first.first)
        assertEquals(WakeAction.None, wakeAction(targets, emptySet(), key, at("03:01"), at("03:01").plusSeconds(30)))
        val again = wakeAction(targets, emptySet(), key, at("03:01"), at("03:02"))
        assertTrue(again is WakeAction.Ring && !again.first)
        assertEquals(WakeAction.Stop, wakeAction(targets, setOf(key), key, at("03:02"), at("03:03")))
        assertEquals(at("03:03"), nextWakeLook(targets, emptySet(), key, at("03:02"), at("03:02")))
    }

    @Test
    fun itStopsByItselfAfterTheArrivalAndWhenThePlanChanges() {
        val targets = wakeTargets(journey, WakeAlarmMode.Auto, 10)
        val key = targets[0].key
        assertEquals(WakeAction.Stop, wakeAction(targets, emptySet(), key, at("03:13"), at("03:15")))
        assertEquals(WakeAction.Stop, wakeAction(emptyList(), emptySet(), key, at("03:02"), at("03:02")))
    }

    @Test
    fun theNextLookIsTheNextTarget() {
        val targets = wakeTargets(journey, WakeAlarmMode.Auto, 10)
        assertEquals(at("03:01"), nextWakeLook(targets, emptySet(), null, null, at("01:00")))
        assertNull(nextWakeLook(targets, setOf(targets[0].key), null, null, at("01:00")))
    }

    @Test
    fun theTimeChosenForTheTripMovesTheRing() {
        // Minutes of its own (#64).
        assertEquals(at("02:56"), wakeTargets(journey, WakeAlarmMode.Auto, 10, lead = WakeLead.Minutes(15))[0].at)
        // An exact time within the ride.
        assertEquals(at("02:40"), wakeTargets(journey, WakeAlarmMode.Auto, 10, lead = WakeLead.At(at("02:40")))[0].at)
        // An exact time outside the ride: the minutes of the setting.
        assertEquals(at("03:01"), wakeRingAt(night, WakeLead.At(at("04:00")), 10))
        // Stops before: when the vehicle leaves the stop two before the end.
        val stops = listOf(
            Stopover("Example A", null, at("00:13")),
            Stopover("Example M1", at("01:00"), at("01:02")),
            Stopover("Example M2", at("02:30"), at("02:32")),
            Stopover("Example M3", at("02:50"), at("02:51")),
            Stopover("Example B", at("03:11"), null),
        )
        val withStops = night.copy(stopovers = stops)
        assertEquals(at("02:32"), wakeRingAt(withStops, WakeLead.Stops(2), 10))
        // Too few stops: the minutes of the setting.
        assertEquals(at("03:01"), wakeRingAt(night, WakeLead.Stops(2), 10))
        // Never before the departure.
        assertEquals(at("00:13"), wakeRingAt(night, WakeLead.Minutes(500), 10))
    }
}
