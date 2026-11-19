package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The shortcut alert speaks up only for what changed since the choice. Made-up stops only. */
class ShortcutNewsTest {
    private val start: OffsetDateTime = OffsetDateTime.parse("2026-01-01T10:00:00+01:00")
    private fun at(minutes: Long) = start.plusMinutes(minutes)
    private fun stop(name: String) = Stop("id$name", "Stop $name", 52.0 + name.length * 0.001, 13.0, null)
    private fun stopover(name: String, minutes: Long) = Stopover("Stop $name", at(minutes), at(minutes), stop(name), 60)

    private fun ride(line: String, from: String, to: String, leaves: Long, arrives: Long, trip: String, stops: List<Stopover> = emptyList()) =
        Leg(
            line, "subway", false, "Stop $from", "Stop $to", at(leaves), at(leaves), at(arrives), at(arrives),
            null, null, null, false, fromPlace = stop(from), toPlace = stop(to), tripId = trip, stopovers = stops,
        )

    /** [journey] with its last ride arriving [minutes] later than planned (live delay). */
    private fun delayed(journey: Journey, minutes: Long): Journey {
        val legs = journey.legs.toMutableList()
        val last = legs.last()
        legs[legs.lastIndex] = last.copy(arrival = last.arrival.plusMinutes(minutes))
        return journey.copy(legs = legs)
    }

    // The chosen journey: the U1 from A at 10:00, at Z 10:30; the traveller waits at A.
    private val chosen = Journey(listOf(ride("U1", "A", "Z", 0, 30, "t1")))
    private val now = at(-2)
    private val point = replanPoint(chosen, now)!!

    // An alternative shown in the list the trip was picked from: 5 minutes faster.
    private val skipped = Journey(listOf(ride("S1", "A", "Z", 1, 25, "t2")))
    private val known = knownWays(chosen, listOf(chosen, skipped))

    // The threshold at one minute: these tests are about news, not about the threshold (see ShortcutTruthTest).
    private val alertOnly = ShortcutRules(everyMinute = false, userLevel = TransferComfort.Normal, thresholdMinutes = 1)
    private val everyMinute = ShortcutRules(everyMinute = true, userLevel = TransferComfort.Normal)
    private val longAgo = at(-60)

    private fun offer(
        current: Journey,
        options: List<Journey>,
        rules: ShortcutRules = alertOnly,
        startedAt: OffsetDateTime = longAgo,
        known: KnownWays = this.known,
    ) = shortcutOffer(current, replanPoint(current, now)!!, options, emptyMap(), known, rules, startedAt, now)

    @Test
    fun knownAndAsFastAtTheChoiceIsNoNews() {
        // A rider's case: the faster way was in the list, they chose the U1 anyway.
        assertNull(offer(chosen, listOf(skipped)))
        // "Every minute counts" stays quiet about it as well.
        assertNull(offer(chosen, listOf(skipped), everyMinute))
        // Found again with a new handle and other live data of no weight: still the same way.
        assertNull(offer(chosen, listOf(Journey(listOf(ride("S 1", "A", "Z", 1, 25, "t9")), refreshToken = "x"))))
    }

    @Test
    fun aNewWayIsNews() {
        val fresh = Journey(listOf(ride("S2", "A", "Z", 2, 26, "t3")))
        val found = offer(chosen, listOf(skipped, fresh))
        assertSame(fresh, found?.first)
        assertEquals(4 * 60L, found?.second)
        assertSame(fresh, offer(chosen, listOf(fresh), everyMinute)?.first)
    }

    @Test
    fun theChosenJourneyGotWorse() {
        // Three minutes late: the known way now wins 8 minutes instead of 5.
        val late = delayed(chosen, 3)
        assertEquals(chosen.id, late.id)
        val found = offer(late, listOf(skipped))
        assertSame(skipped, found?.first)
        assertEquals(8 * 60L, found?.second)
        // Half a minute is noise.
        assertNull(offer(chosen.copy(legs = listOf(chosen.legs[0].copy(arrival = at(30).plusSeconds(30)))), listOf(skipped)))
    }

    @Test
    fun theKnownWayGotFaster() {
        val sooner = Journey(listOf(ride("S1", "A", "Z", 1, 25, "t2").copy(arrival = at(23))))
        assertSame(sooner, offer(chosen, listOf(sooner))?.first)
    }

    @Test
    fun aLostChangeLetsKnownWaysThrough() {
        val twoRides = Journey(listOf(ride("U1", "A", "B", 0, 10, "t1"), ride("S5", "B", "Z", 12, 30, "t5")))
        val knownThen = knownWays(twoRides, listOf(twoRides, skipped))
        assertNull(offer(twoRides, listOf(skipped), known = knownThen))
        // The U1 is late at B after the S5 has left: the plan no longer holds.
        val lost = twoRides.copy(legs = listOf(twoRides.legs[0].copy(arrival = at(13)), twoRides.legs[1]))
        assertTrue(brokenAhead(lost, replanPoint(lost, now)!!))
        assertFalse(brokenAhead(twoRides, replanPoint(twoRides, now)!!))
        assertSame(skipped, offer(lost, listOf(skipped), known = knownThen)?.first)
    }

    @Test
    fun aCancelledWayRunningAgainIsNews() {
        val cancelled = Journey(listOf(ride("S1", "A", "Z", 1, 25, "t2").copy(cancelled = true)))
        val knownThen = knownWays(chosen, listOf(chosen, cancelled))
        assertSame(skipped, offer(chosen, listOf(skipped), known = knownThen)?.first)
    }

    @Test
    fun takenOfferIsTheNewChoice() {
        val fresh = Journey(listOf(ride("S2", "A", "Z", 2, 26, "t3")))
        val rebased = known.rebased(fresh, listOf(skipped, fresh))
        assertEquals(fresh.id, rebased.chosenId)
        // The skipped way still wins only one minute against the new choice: known, as fast as then.
        assertFalse(rebased.isNews(skipped, 60))
        assertTrue(rebased.isNews(skipped, 120))
    }

    @Test
    fun quietAfterTheStart() {
        val quiet = alertOnly.copy(quietStart = true)
        val twoMinutes = Journey(listOf(ride("S2", "A", "Z", 2, 28, "t3")))
        val fourMinutes = Journey(listOf(ride("S3", "A", "Z", 2, 26, "t4")))
        val justStarted = at(-3)
        assertNull(offer(chosen, listOf(twoMinutes), quiet, justStarted))
        assertSame(fourMinutes, offer(chosen, listOf(twoMinutes, fourMinutes), quiet, justStarted)?.first)
        // After 3 minutes, or with the option off, or with "Every minute counts": every minute.
        assertSame(twoMinutes, offer(chosen, listOf(twoMinutes), quiet, at(-6))?.first)
        assertSame(twoMinutes, offer(chosen, listOf(twoMinutes), alertOnly, justStarted)?.first)
        assertSame(twoMinutes, offer(chosen, listOf(twoMinutes), everyMinute.copy(quietStart = true), justStarted)?.first)
    }

    @Test
    fun onlyAsComfortable() {
        val same = alertOnly.copy(sameComfort = true)
        val withChange = Journey(listOf(ride("S3", "A", "B", 1, 6, "t6"), ride("U2", "B", "Z", 14, 22, "t7")))
        assertSame(withChange, offer(chosen, listOf(withChange))?.first)
        assertNull(offer(chosen, listOf(withChange), same))
        val direct = Journey(listOf(ride("S2", "A", "Z", 2, 26, "t3")))
        assertSame(direct, offer(chosen, listOf(withChange, direct), same)?.first)
        // "Every minute counts" takes every way.
        assertSame(withChange, offer(chosen, listOf(withChange), everyMinute.copy(sameComfort = true))?.first)
    }

    @Test
    fun stayingOnBoardIsNoChange() {
        val stops = listOf(stopover("A", 0), stopover("B", 5), stopover("C", 10), stopover("D", 20))
        val twoRides = Journey(listOf(ride("U1", "A", "D", 0, 20, "t1", stops), ride("S5", "D", "Z", 22, 30, "t5")))
        val onBoard = replanPoint(twoRides, at(2))!!
        assertTrue(onBoard.cut)
        // On with the U1 beyond D, then one other ride: one boarding, as the chosen way.
        val stay = Journey(listOf(ride("U1", "B", "E", 5, 24, "t1"), ride("S7", "E", "Z", 25, 28, "t8")))
        assertEquals(1, optionBoardings(onBoard, stay))
        val leave = Journey(listOf(ride("S7", "B", "Z", 6, 27, "t8")))
        assertEquals(1, optionBoardings(onBoard, leave))
        // Waiting at A, the first ride counts.
        assertEquals(1, optionBoardings(point, skipped))
    }
}
