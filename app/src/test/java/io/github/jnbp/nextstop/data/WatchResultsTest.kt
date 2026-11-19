package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The journeys the phone finds for the watch keep to the level of change time the watch chose
 * (W2): a search at "knapp" asks for changes of 0 minutes, and a change of 0 minutes is a sprint
 * by the rule, even across one platform (getting off and on alone takes 2 minutes). The case
 * seen: the U3 into the U2 at U Mittelplatz in the same minute.
 */
class WatchResultsTest {
    private val start: OffsetDateTime = OffsetDateTime.parse("2026-11-18T18:00:00+01:00")
    private fun at(minutes: Long) = start.plusMinutes(minutes)
    private fun stop(id: String, name: String) = Stop("90000000$id", name, 52.5, 13.3, null, products = setOf("subway"))

    private val a = stop("1", "U Stop A")
    private val change = stop("2", "U Mittelplatz")
    private val end = stop("3", "S+U Alexanderplatz")

    private fun ride(line: String, from: Stop, to: Stop, leaves: Long, arrives: Long, platformFrom: String?, platformTo: String?) = Leg(
        line, "subway", false, from.name, to.name, at(leaves), at(leaves), at(arrives), at(arrives),
        platformFrom, platformTo, null, false, fromPlace = from, toPlace = to, tripId = "t$line$leaves",
    )

    // 18:33 to 18:36 and on at 18:36, across one platform by the numbers.
    private val sprint = Journey(listOf(ride("U3", a, change, 33, 36, "1", "2"), ride("U2", change, end, 36, 54, "2", "1")), refreshToken = "sprint")
    // Four minutes later on: normal.
    private val normal = Journey(listOf(ride("U3", a, change, 43, 46, "1", "2"), ride("U2", change, end, 50, 68, "2", "1")), refreshToken = "normal")
    private val direct = Journey(listOf(ride("U2", a, end, 40, 60, "1", "1")), refreshToken = "direct")

    @Test
    fun aChangeInTheSameMinuteIsASprintEvenAcrossOnePlatform() {
        assertEquals(mapOf(1 to Tightness.VeryRisky), changeRatingsByLeg(sprint))
        assertEquals(mapOf(1 to Tightness.Tight), changeRatingsByLeg(normal))
        assertEquals(emptyMap<Int, Tightness>(), changeRatingsByLeg(direct))
    }

    @Test
    fun knappOffersNoSprint() {
        val found = listOf(normal, sprint, direct)
        // By the time to set off; the sprint only at "Sprint".
        assertEquals(listOf("direct", "normal"), watchJourneys(found, TransferComfort.Risky, 3).map { it.refreshToken })
        assertEquals(listOf("sprint", "direct", "normal"), watchJourneys(found, TransferComfort.VeryRisky, 3).map { it.refreshToken })
        assertEquals(listOf("direct", "normal"), watchJourneys(found, TransferComfort.Normal, 3).map { it.refreshToken })
        assertEquals(listOf("direct"), watchJourneys(found, TransferComfort.Relaxed, 3).map { it.refreshToken })
        assertEquals(listOf("direct"), watchJourneys(found, TransferComfort.Risky, 1).map { it.refreshToken })
    }

    @Test
    fun asksForAFewMoreWhereSomeMayGo() {
        assertEquals(6, watchSearchCount(3, TransferComfort.Risky, 6))
        assertEquals(3, watchSearchCount(3, TransferComfort.VeryRisky, 6))
        assertEquals(6, watchSearchCount(6, TransferComfort.Normal, 6))
    }
}
