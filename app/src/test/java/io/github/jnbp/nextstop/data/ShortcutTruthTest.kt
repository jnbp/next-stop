package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shortcut alert tells the truth: only a real alternative (another action than the plan),
 * compared on the own vehicle's current realtime, reachable from where the own vehicle really
 * is, and at least the threshold faster. The rides of an evening RB 48 from Bonn Hbf towards
 * Köln and of a morning S 19 from the airport, with made-up times.
 */
class ShortcutTruthTest {
    private val base: OffsetDateTime = OffsetDateTime.parse("2026-11-17T21:00:00+01:00")
    private fun at(minutes: Long) = base.plusMinutes(minutes)
    private fun stop(name: String) = Stop("id-$name", name, 50.8 + name.length * 0.01, 7.0, null)
    private fun call(name: String, live: Long, planned: Long = live) =
        Stopover(name, at(live), at(live), stop(name), ((live - planned) * 60).toInt(), at(planned), at(planned))

    private fun ride(
        line: String,
        from: String,
        to: String,
        leaves: Long,
        arrives: Long,
        trip: String,
        plannedLeaves: Long = leaves,
        plannedArrives: Long = arrives,
        stops: List<Stopover> = emptyList(),
        product: String = "regional",
    ) = Leg(
        line, product, false, from, to, at(leaves), at(plannedLeaves), at(arrives), at(plannedArrives),
        null, null, null, false, fromPlace = stop(from), toPlace = stop(to), tripId = trip, stopovers = stops,
    )

    private val nobodyKnown = KnownWays("none", at(-120), emptyMap())
    private val alertOnly = ShortcutRules(everyMinute = false, userLevel = TransferComfort.Normal)
    private val everyMinute = ShortcutRules(everyMinute = true, userLevel = TransferComfort.Normal)

    private fun pick(
        current: Journey,
        now: OffsetDateTime,
        options: List<Journey>,
        rules: ShortcutRules = alertOnly,
        ignored: Map<String, Long> = emptyMap(),
    ) = shortcutPick(current, replanPoint(current, now)!!, options, ignored, nobodyKnown, rules, at(-120), now)

    // On board of the RB 48 that left Bonn Hbf at 21:03 instead of 20:54 (+9, the live time of
    // the followed journey), to Köln Hbf at 21:33, then the S 11 at 21:41, at Köln Messe/Deutz 21:47.
    private val rb48Calls = listOf(
        call("Bonn Hbf", 3, -6),
        call("Bornheim-Roisdorf", 10, 1),
        call("Köln West", 28, 19),
        call("Köln Hbf", 33, 24),
    )
    private val rb48 = ride("RB 48", "Bonn Hbf", "Köln Hbf", 3, 33, "rb48", plannedLeaves = -6, plannedArrives = 24, stops = rb48Calls)
    private val followed = Journey(listOf(rb48, ride("S 11", "Köln Hbf", "Köln Messe/Deutz", 41, 47, "s11", product = "suburban")))
    private val now = at(5)

    // The search lags behind: it still has the RB 48 at +2, at Köln West at 21:21.
    private fun staleRb48ToWest() = ride(
        "RB 48", "Bornheim-Roisdorf", "Köln West", 10, 21, "rb48", plannedLeaves = 1, plannedArrives = 19,
        stops = listOf(call("Bornheim-Roisdorf", 10, 1), call("Köln West", 21, 19)),
    )

    @Test
    fun theNextStopIsWhereTheOwnTrainReallyIs() {
        val point = replanPoint(followed, now)!!
        assertEquals("Bornheim-Roisdorf", point.place.name)
        assertEquals(at(10), point.time)
        assertTrue(point.onBoard && point.cut)
    }

    @Test
    fun aChangeTheOwnTrainCannotReachIsNeverOffered() {
        // "S 12 at 21:26 from Köln West": by the stale search a change of 5 minutes, but the own
        // RB 48 is at Köln West only at 21:28. Not offered, not even with "Every minute counts".
        val gone = Journey(listOf(staleRb48ToWest(), ride("S 12", "Köln West", "Köln Messe/Deutz", 26, 44, "s12a", product = "suburban")))
        assertNull(pick(followed, now, listOf(gone)))
        assertNull(pick(followed, now, listOf(gone), everyMinute))
    }

    @Test
    fun aReachableChangeSaysWhatToDo() {
        val later = Journey(listOf(staleRb48ToWest(), ride("S 12", "Köln West", "Köln Messe/Deutz", 36, 43, "s12b", product = "suburban")))
        val found = pick(followed, now, listOf(later))
        assertNotNull(found)
        assertSame(later, found!!.option)
        assertEquals(4 * 60L, found.gainSeconds)
        assertEquals(at(43), found.arrival)
        val action = found.action
        assertEquals(ShortcutActionKind.GetOffAndTake, action.kind)
        assertEquals("Köln West", action.at)
        assertEquals("S 12", action.line)
        assertEquals(at(36), action.time)
    }

    @Test
    fun theSameTrainUnderAnotherLabelIsNoShortcut() {
        // The way of the plan found again: the same RB 48 and S 11, the S 11 a minute sooner in this search.
        val same = Journey(
            listOf(
                ride("RB 48", "Bornheim-Roisdorf", "Köln Hbf", 10, 26, "rb48", plannedLeaves = 1, plannedArrives = 24),
                ride("S 11", "Köln Hbf", "Köln Messe/Deutz", 41, 46, "s11-new-handle", product = "suburban"),
            ),
        )
        assertNull(shortcutAction(followed.plausible(), replanPoint(followed, now)!!, same))
        assertNull(pick(followed, now, listOf(same), everyMinute))
    }

    @Test
    fun stayingSeatedCountsOnTheOwnTrainsRealtime() {
        // The plan: S 11 at 21:36, at Köln Messe/Deutz 21:40. The search has the RB 48 going on to
        // Messe/Deutz at 21:31: 9 minutes sooner by its stale times, 2 by the own train's (+7).
        val plan = Journey(listOf(rb48, ride("S 11", "Köln Hbf", "Köln Messe/Deutz", 36, 40, "s11", product = "suburban")))
        val stay = Journey(
            listOf(
                ride(
                    "RB 48", "Bornheim-Roisdorf", "Köln Messe/Deutz", 10, 31, "rb48", plannedLeaves = 1, plannedArrives = 29,
                    stops = listOf(call("Bornheim-Roisdorf", 10, 1), call("Köln West", 21, 19), call("Köln Hbf", 26, 24), call("Köln Messe/Deutz", 31, 29)),
                ),
            ),
        )
        assertNull(pick(plan, now, listOf(stay)))
        val found = pick(plan, now, listOf(stay), everyMinute)
        assertEquals(2 * 60L, found?.gainSeconds)
        assertEquals(ShortcutActionKind.StaySeated, found?.action?.kind)
        assertEquals("Köln Messe/Deutz", found?.action?.until)
    }

    @Test
    fun aDismissedShortcutComesBackOnlyClearlyBetter() {
        val later = Journey(listOf(staleRb48ToWest(), ride("S 12", "Köln West", "Köln Messe/Deutz", 36, 43, "s12b", product = "suburban")))
        val first = pick(followed, now, listOf(later))!!
        val ignored = mapOf(first.action.key to first.gainSeconds)
        assertNull(pick(followed, now, listOf(later), ignored = ignored))
        // A minute better is not enough...
        val bitBetter = Journey(listOf(staleRb48ToWest(), ride("S 12", "Köln West", "Köln Messe/Deutz", 36, 42, "s12b", plannedArrives = 43, product = "suburban")))
        assertNull(pick(followed, now, listOf(bitBetter), ignored = ignored))
        // ...two minutes are.
        val better = Journey(listOf(staleRb48ToWest(), ride("S 12", "Köln West", "Köln Messe/Deutz", 36, 41, "s12b", plannedArrives = 43, product = "suburban")))
        assertEquals(first.action.key, pick(followed, now, listOf(better), ignored = ignored)?.action?.key)
    }

    // Waiting at the airport for the S 19 at 08:37 to Troisdorf, then the SEV of the RE 8 to
    // Bonn-Ramersdorf and the 66, at Bonn Hbf 09:31.
    private val morning = base.minusHours(13)
    private fun am(minutes: Long) = morning.plusMinutes(minutes)
    private fun amRide(line: String, from: String, to: String, leaves: Long, arrives: Long, trip: String, product: String) = Leg(
        line, product, false, from, to, am(leaves), am(leaves), am(arrives), am(arrives),
        null, null, null, false, fromPlace = stop(from), toPlace = stop(to), tripId = trip,
    )
    private val airport = Journey(
        listOf(
            amRide("S 19", "Köln/Bonn Flughafen", "Troisdorf", 37, 46, "s19", "suburban"),
            amRide("SEV RE 8", "Troisdorf", "Bonn-Ramersdorf", 55, 75, "sev", "bus"),
            amRide("66", "Bonn-Ramersdorf", "Bonn Hbf", 80, 91, "t66", "tram"),
        ),
    )

    @Test
    fun anotherChangeAfterTheSameTrainNamesTheChange() {
        // The same S 19 (found under another handle), then the RE 8 at 09:05 from Troisdorf: one minute sooner.
        val re8 = Journey(
            listOf(
                amRide("S 19", "Köln/Bonn Flughafen", "Troisdorf", 37, 46, "s19-other", "suburban"),
                amRide("RE 8", "Troisdorf", "Bonn Hbf", 65, 90, "re8", "regional"),
            ),
        )
        val waiting = am(30)
        // One minute is below the threshold of 3 minutes.
        assertNull(pick(airport, waiting, listOf(re8)))
        // With "Every minute counts" it is offered, and it says what changes: at Troisdorf the RE 8, not the S 19.
        val found = pick(airport, waiting, listOf(re8), everyMinute)
        assertEquals(60L, found?.gainSeconds)
        val action = found!!.action
        assertEquals(ShortcutActionKind.ChangeInstead, action.kind)
        assertEquals("Troisdorf", action.at)
        assertEquals("RE 8", action.line)
        assertEquals("SEV RE 8", action.instead)
        assertEquals(am(65), action.time)
    }

    @Test
    fun theSameRidesOneMinuteSoonerAreNoShortcut() {
        val same = Journey(
            listOf(
                amRide("S 19", "Köln/Bonn Flughafen", "Troisdorf", 37, 46, "a", "suburban"),
                amRide("SEV RE 8", "Troisdorf", "Bonn-Ramersdorf", 55, 75, "b", "bus"),
                amRide("66", "Bonn-Ramersdorf", "Bonn Hbf", 80, 91, "c", "tram").copy(arrival = am(90)),
            ),
        )
        assertNull(pick(airport, am(30), listOf(same), everyMinute))
    }

    @Test
    fun sameVehicleByLineAndPlannedTimes() {
        val own = ride("S 12", "Köln West", "Köln Hbf", 36, 41, "x", product = "suburban")
        assertTrue(sameVehicle(own, ride("S12", "Köln West", "Köln Hbf", 38, 43, "y", plannedLeaves = 36, plannedArrives = 41, product = "suburban")))
        // The other direction at the same minute is another train.
        assertFalse(sameVehicle(own, ride("S 12", "Köln Hbf", "Köln West", 36, 41, "z", product = "suburban")))
        assertFalse(sameVehicle(own, ride("S 13", "Köln West", "Köln Hbf", 36, 41, "w", product = "suburban")))
    }

    @Test
    fun oneControlThreeStates() {
        assertEquals(ShortcutMode.Off, shortcutMode(everyMinute = false, alert = false))
        assertEquals(ShortcutMode.FromThreshold, shortcutMode(everyMinute = false, alert = true))
        assertEquals(ShortcutMode.EveryMinute, shortcutMode(everyMinute = true, alert = false))
        assertEquals(ShortcutMode.EveryMinute, shortcutMode(everyMinute = true, alert = true))
        assertFalse(ShortcutMode.Off.alert)
        assertTrue(ShortcutMode.FromThreshold.alert && !ShortcutMode.FromThreshold.everyMinute)
        assertTrue(ShortcutMode.EveryMinute.alert && ShortcutMode.EveryMinute.everyMinute)
    }

    @Test
    fun thresholds() {
        assertEquals(180L, shortcutThresholdSeconds(everyMinute = false, minutes = DEFAULT_SHORTCUT_MINUTES))
        assertEquals(300L, shortcutThresholdSeconds(everyMinute = false, minutes = 5))
        assertEquals(60L, shortcutThresholdSeconds(everyMinute = true, minutes = 5))
    }
}
