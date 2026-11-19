package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #94, #97, #98: the choice of connections at a change of the followed trip (ICE into Berlin Hbf,
 * then the FEX at 15:22). The search from the change found S-Bahn ways but not the FEX itself:
 * the current choice was missing, and with every option out of reach the row was empty.
 */
class ConnectionPickTest {
    private fun at(clock: String): OffsetDateTime = OffsetDateTime.parse("2026-11-18T${clock}:00+01:00")

    private fun ride(line: String, departure: String, arrival: String) = Leg(
        line, "regional", false, "S+U Berlin Hauptbahnhof", "Example End", at(departure), at(departure), at(arrival), at(arrival),
        null, null, null, false, tripId = "$line-$departure",
    )

    private val fex = Journey(listOf(ride("FEX", "15:22", "15:40")))
    private val s7 = Journey(listOf(ride("S7", "15:26", "15:50")))
    private val s5 = Journey(listOf(ride("S5", "15:31", "15:55")))

    @Test
    fun theCurrentChoiceIsAlwaysOffered() {
        val options = withCurrentChoice(listOf(s7, s5), fex, fex.legs[0])
        assertEquals(listOf("FEX", "S7", "S5"), options.map { it.legs[0].line })
        // Found by the search as well: once.
        assertEquals(3, withCurrentChoice(listOf(s7, fex, s5), fex, fex.legs[0]).size)
        // Nothing found: the current one alone, never an empty row.
        assertEquals(1, withCurrentChoice(emptyList(), fex, fex.legs[0]).size)
    }

    @Test
    fun theEarliestReachableAndASprintHint() {
        val departures = listOf(at("15:22"), at("15:26"), at("15:31"))
        val times = listOf(
            ChangeTime(-3, 4, Tightness.VeryRisky, 0), // the FEX: gone
            ChangeTime(1, 4, Tightness.VeryRisky, 0), // the S7: only at a run
            ChangeTime(6, 4, Tightness.Comfortable, 0), // the S5: fine
        )
        val marks = choiceMarks(times, departures)
        assertEquals(2, marks.earliest)
        assertEquals(1, marks.sprintEarlier)
        // Nothing tighter: no hint.
        assertNull(choiceMarks(times.take(1) + times.drop(2), departures.take(1) + departures.drop(2)).sprintEarlier)
    }

    @Test
    fun aChoiceOutOfReachOffersTheBestOneThatWorks() {
        val times = listOf(
            ChangeTime(-3, 4, Tightness.VeryRisky, 0),
            ChangeTime(1, 4, Tightness.VeryRisky, 0),
            ChangeTime(6, 4, Tightness.Comfortable, 0),
        )
        assertEquals(2, rescueChoice(chosen = 0, times = times))
        // The chosen one still works: nothing to offer.
        assertNull(rescueChoice(chosen = 2, times = times))
        // Only a sprint left: that one, rather than nothing.
        assertEquals(1, rescueChoice(chosen = 0, times = times.take(2)))
        assertTrue(rescueChoice(chosen = 0, times = times.take(1)) == null)
    }

    @Test
    fun theTilesGoByTheirDepartureTheChosenOneAtItsPlace() {
        val at = { minute: Int -> OffsetDateTime.parse("2026-11-18T12:00:00+01:00").plusMinutes(minute.toLong()) }
        // In the order of the options (by arrival); index 0 is the chosen one, now "zu spät".
        val departures = listOf(at(10), at(14), at(12), at(20), at(12))
        assertEquals(listOf(0, 2, 4, 1, 3), tileOrder(departures))
        // Compared as instants, whatever the offset; equal departures (2 and 4 above) keep their order.
        assertEquals(listOf(1, 0), tileOrder(listOf(at(5), at(5).withOffsetSameInstant(java.time.ZoneOffset.UTC).minusMinutes(1))))
        assertEquals(emptyList<Int>(), tileOrder(emptyList()))
    }

    @Test
    fun theSameTramFromTwoSourcesIsOneTile() {
        // #275: the tram of the followed journey and the same run found by the search, written
        // with another offset, another name and a few seconds apart.
        val followed = Leg(
            "3", "tram", false, "Example Station", "Example End",
            at("09:19"), at("09:19"), at("09:27"), at("09:27"), null, null, null, false,
        )
        val found = followed.copy(
            line = "STR 3",
            departure = OffsetDateTime.parse("2026-11-18T08:19:30Z"),
            plannedDeparture = OffsetDateTime.parse("2026-11-18T08:19:00Z"),
            arrival = OffsetDateTime.parse("2026-11-18T08:27:00Z"),
            plannedArrival = OffsetDateTime.parse("2026-11-18T08:27:00Z"),
        )
        assertTrue(sameChoiceRide(followed, found))
        val current = Journey(listOf(followed))
        val options = withCurrentChoice(listOf(Journey(listOf(found))), current, followed)
        assertEquals(1, options.size)
        // The next run of the line is another choice.
        val later = followed.copy(departure = at("09:29"), plannedDeparture = at("09:29"))
        assertTrue(!sameChoiceRide(followed, later))
        // Without a timetable on one side: the time it leaves stands for it.
        assertTrue(sameChoiceRide(followed.copy(plannedDeparture = null, departure = at("09:21")), found.copy(departure = at("09:21"))))
        // Twice in the offers: once.
        assertEquals(2, distinctChoices(listOf(Journey(listOf(found)), current, Journey(listOf(later)))).size)
    }
}
