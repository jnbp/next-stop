package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The shortcut alert offers only ways the traveller can really take from where they are: none
 * that leaves before the chosen one, none with a change tighter than the chosen level (the
 * change at the point included), and never one that cannot be made. Made-up stops only.
 */
class ShortcutFilterTest {
    private val start: OffsetDateTime = OffsetDateTime.parse("2026-01-01T10:00:00+01:00")
    private fun at(minutes: Long) = start.plusMinutes(minutes)
    private fun stop(name: String) = Stop("id$name", "Stop $name", 52.0, 13.0 + (name[0] - 'A') * 0.01, null)
    private fun stopover(name: String, minutes: Long) = Stopover("Stop $name", at(minutes), at(minutes), stop(name), 60)

    private fun ride(
        line: String,
        from: String,
        to: String,
        leaves: Long,
        arrives: Long,
        trip: String,
        plannedLeaves: Long = leaves,
        stops: List<Stopover> = emptyList(),
    ) = Leg(
        line, "subway", false, "Stop $from", "Stop $to", at(leaves), at(plannedLeaves), at(arrives), at(arrives),
        null, null, null, false, fromPlace = stop(from), toPlace = stop(to), tripId = trip, stopovers = stops,
    )

    private fun walk(from: String, to: String, leaves: Long, arrives: Long) = Leg(
        null, "", true, "Stop $from", "Stop $to", at(leaves), null, at(arrives), null,
        null, null, null, false, fromPlace = stop(from), toPlace = stop(to),
    )

    private val nobodyKnown = KnownWays("none", at(0), emptyMap())
    private val normal = ShortcutRules(everyMinute = false, userLevel = TransferComfort.Normal)
    private val tight = ShortcutRules(everyMinute = false, userLevel = TransferComfort.Risky)
    private val everyMinute = ShortcutRules(everyMinute = true, userLevel = TransferComfort.Normal)

    private fun offer(current: Journey, now: OffsetDateTime, options: List<Journey>, rules: ShortcutRules = normal) =
        shortcutOffer(current, replanPoint(current, now)!!, options, emptyMap(), nobodyKnown, rules, at(-60), now)

    // Before the trip: waiting at A for the U1 at 10:05, at Z 10:40.
    private val waiting = Journey(listOf(ride("U1", "A", "Z", 5, 40, "t1")))

    @Test
    fun neverEarlierThanTheChosenDeparture() {
        val earlier = Journey(listOf(ride("S1", "A", "Z", 3, 30, "t2")))
        assertNull(offer(waiting, at(0), listOf(earlier)))
        assertNull(offer(waiting, at(0), listOf(earlier), everyMinute))
        val later = Journey(listOf(ride("S2", "A", "Z", 6, 30, "t3")))
        assertSame(later, offer(waiting, at(0), listOf(earlier, later))?.first)
    }

    @Test
    fun aLateRidePlannedEarlierIsNoAlternative() {
        // Planned at 10:02, three minutes before the chosen one, now leaving 10:07 late.
        val late = Journey(listOf(ride("S1", "A", "Z", 7, 30, "t2", plannedLeaves = 2)))
        assertNull(offer(waiting, at(0), listOf(late)))
        // Planned and live no earlier than the chosen one: taken.
        val onTime = Journey(listOf(ride("S1", "A", "Z", 7, 30, "t2", plannedLeaves = 5)))
        assertSame(onTime, offer(waiting, at(0), listOf(onTime))?.first)
    }

    // On board of the U1 from A to D (10:00-10:20), then the S5 at 10:22, at Z 10:40.
    private val stops = listOf(stopover("A", 0), stopover("B", 5), stopover("C", 10), stopover("D", 20))
    private val onBoard = Journey(listOf(ride("U1", "A", "D", 0, 20, "t1", stops = stops), ride("S5", "D", "Z", 22, 40, "t5")))

    @Test
    fun theChangeAtThePointCounts() {
        // The traveller can get off at B at 10:05. An S7 from there at 10:05 leaves no time to
        // change: a Sprint at best, never offered at the normal or tight level.
        val now = at(2)
        assertEquals("Stop B", replanPoint(onBoard, now)!!.place.name)
        val gone = Journey(listOf(ride("S7", "B", "Z", 5, 25, "t7")))
        assertNull(offer(onBoard, now, listOf(gone)))
        assertNull(offer(onBoard, now, listOf(gone), tight))
        // One that leaves B at 10:10 leaves time to change: offered at the normal level.
        val easy = Journey(listOf(ride("S7", "B", "Z", 10, 30, "t8")))
        assertSame(easy, offer(onBoard, now, listOf(gone, easy))?.first)
    }

    @Test
    fun theWalkAtThePointCounts() {
        // From B a walk of 6 minutes to E, then a bus from E at 10:09: there is no getting there in time.
        val now = at(2)
        val rushed = Journey(listOf(walk("B", "E", 5, 11), ride("X1", "E", "Z", 9, 25, "t9")))
        assertNull(offer(onBoard, now, listOf(rushed)))
        assertNull(offer(onBoard, now, listOf(rushed), everyMinute))
        // With the bus at 10:15 the walk plus a minute fits with room to spare.
        val fits = Journey(listOf(walk("B", "E", 5, 11), ride("X1", "E", "Z", 15, 30, "t9")))
        assertSame(fits, offer(onBoard, now, listOf(fits))?.first)
    }

    @Test
    fun aTightChangeOnlyAtATightLevel() {
        // Getting off at B 10:05 and on the S7 at 10:07 on the same stop: one or two minutes, no more.
        val now = at(2)
        val close = Journey(listOf(ride("S7", "B", "Z", 7, 25, "t7")))
        val rating = journeyComfort(joinedAtPoint(replanPoint(onBoard, now)!!, close))
        assertEquals(true, rating.ordinal > TransferComfort.Normal.ordinal)
        assertNull(offer(onBoard, now, listOf(close)))
        if (rating == TransferComfort.Risky) {
            assertSame(close, offer(onBoard, now, listOf(close), tight)?.first)
        } else {
            assertNull(offer(onBoard, now, listOf(close), tight))
        }
        assertSame(close, offer(onBoard, now, listOf(close), everyMinute)?.first)
    }
}
