package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * An additional run that stands in for a cancelled run of its line is shown once, as the
 * replacement; line names given only by their number get the letter of their product.
 * As seen at S Julius-Leber-Brücke on 13 November 2026: S1 to Wannsee cancelled, an extra
 * "1" from platform 1 at the same minute.
 */
class ReplacementRunTest {
    private val planned: OffsetDateTime = OffsetDateTime.parse("2026-11-13T13:06:00+01:00")

    private fun departure(
        line: String,
        minutes: Long = 0,
        cancelled: Boolean = false,
        additional: Boolean = false,
        platform: String? = "1",
        direction: String = "S Wannsee",
    ) = Departure(
        line = line,
        product = "suburban",
        direction = direction,
        time = planned.plusMinutes(minutes),
        plannedTime = planned.plusMinutes(minutes),
        delaySeconds = 0,
        platform = platform,
        cancelled = cancelled,
        additional = additional,
    )

    @Test
    fun numberOnlyRapidLinesGetTheirLetter() {
        assertEquals("S1", rapidLineName("1", "suburban"))
        assertEquals("U9", rapidLineName("9", "subway"))
        assertEquals("S41", rapidLineName("S41", "suburban"))
        assertEquals("186", rapidLineName("186", "bus"))
        assertEquals("RE 1", rapidLineName("RE 1", "regional"))
        assertNull(rapidLineName(null, "suburban"))
    }

    @Test
    fun theWordOfTheProductInFrontGoes() {
        assertEquals("50", rapidLineName("Tram 50", "tram"))
        assertEquals("50", rapidLineName("STR 50", "tram"))
        assertEquals("585", rapidLineName("Bus 585", "bus"))
        assertEquals("M48", rapidLineName("Bus M48", "bus"))
        assertEquals("F10", rapidLineName("Fähre F10", "ferry"))
        // Names whose letters are part of the line stay as they are.
        assertEquals("S1", rapidLineName("S1", "suburban"))
        assertEquals("RE1", rapidLineName("RE1", "regional"))
        assertEquals("ICE 27", rapidLineName("ICE 27", "express"))
        assertEquals("FEX", rapidLineName("FEX", "regional"))
        assertEquals("X7", rapidLineName("X7", "bus"))
        assertEquals("Bus", rapidLineName("Bus", "bus"))
        assertEquals("Bus Ost", rapidLineName("Bus Ost", "bus"))
    }

    @Test
    fun replacementTakesThePlaceOfTheCancelledRun() {
        val cancelled = departure("S1", cancelled = true)
        val extra = departure(rapidLineName("1", "suburban")!!, minutes = 1, additional = true)
        val later = departure("S1", minutes = 10)
        val board = mergeReplacements(listOf(cancelled, extra, later))
        assertEquals(2, board.size)
        assertEquals("S1", board[0].replaces)
        assertEquals(false, board[0].cancelled)
        assertEquals(later, board[1])
    }

    @Test
    fun otherPlatformTimeOrDirectionIsNoReplacement() {
        val cancelled = departure("S1", cancelled = true)
        listOf(
            departure("S1", additional = true, platform = "2"),
            departure("S1", minutes = 5, additional = true),
            departure("S1", additional = true, direction = "S Oranienburg"),
            departure("S2", additional = true),
        ).forEach { extra ->
            val board = mergeReplacements(listOf(cancelled, extra))
            assertEquals(2, board.size)
            assertNull(board.first { it.additional }.replaces)
        }
    }

    @Test
    fun additionalRunDoublingARegularRunThatGoesShowsOnce() {
        // As on the watch at an S-Bahn station: "S1 S Oranienburg 13 min" and "1 S Oranienburg 13 min".
        val regular = departure("S1", direction = "S Oranienburg")
        val extra = departure(rapidLineName("1", "suburban")!!, additional = true, direction = "S Oranienburg")
        val other = departure("S1", direction = "S Zehlendorf", platform = "2")
        assertEquals(listOf(regular, other), mergeReplacements(listOf(regular, extra, other)))
        assertEquals(listOf(regular), mergeReplacements(listOf(extra, regular)))
    }

    @Test
    fun additionalRunAMinuteApartOrElsewhereStays() {
        val regular = departure("S1", direction = "S Oranienburg")
        listOf(
            departure("S1", minutes = 2, additional = true, direction = "S Oranienburg"),
            departure("S1", additional = true, direction = "S Oranienburg", platform = "2"),
            departure("S1", additional = true, direction = "S Wannsee"),
            departure("S2", additional = true, direction = "S Oranienburg"),
        ).forEach { extra -> assertEquals(2, mergeReplacements(listOf(regular, extra)).size) }
    }

    @Test
    fun replacementOfACancelledRunIsNotTakenForADouble() {
        val cancelled = departure("S1", cancelled = true)
        val extra = departure("S1", additional = true)
        val board = mergeReplacements(listOf(cancelled, extra))
        assertEquals(1, board.size)
        assertEquals("S1", board[0].replaces)
    }

    private fun stop(id: String) = Stop("9000$id", "Stop $id", 52.0, 13.0, null)
    private val juliusLeber = stop("57104")
    private val steglitz = stop("62202")
    private val target = stop("62999")

    private fun ride(line: String, product: String, from: Stop, to: Stop, leaves: Long, arrives: Long, cancelled: Boolean = false) = Leg(
        line, product, false, from.name, to.name, planned.plusMinutes(leaves), planned.plusMinutes(leaves),
        planned.plusMinutes(arrives), planned.plusMinutes(arrives), "1", null, null, cancelled,
        fromPlace = from, toPlace = to,
    )

    @Test
    fun journeyWithTheReplacementHidesTheCancelledOne() {
        val bus = ride("186", "bus", steglitz, target, 20, 30)
        val cancelled = Journey(listOf(ride("S1", "suburban", juliusLeber, steglitz, 0, 8, cancelled = true), bus))
        val replacement = Journey(listOf(ride("S1", "suburban", juliusLeber, steglitz, 1, 9), bus))
        val other = Journey(listOf(ride("S1", "suburban", juliusLeber, steglitz, 10, 18), ride("186", "bus", steglitz, target, 30, 40)))
        assertEquals(listOf(replacement, other), withoutReplacedJourneys(listOf(cancelled, replacement, other)))
        // Without the replacement the cancelled journey stays, so the user sees it is gone.
        assertEquals(listOf(cancelled, other), withoutReplacedJourneys(listOf(cancelled, other)))
        // Another bus after the replacement is another journey.
        val otherBus = Journey(listOf(ride("S1", "suburban", juliusLeber, steglitz, 1, 9), ride("186", "bus", steglitz, target, 25, 35)))
        assertEquals(2, withoutReplacedJourneys(listOf(cancelled, otherBus)).size)
    }

    @Test
    fun replacementAtAnotherStopOfTheSameStationMeetsTheCancelledRun() {
        val onePlatform = Stop("900062202", "S+U Rathaus Steglitz", 52.456164, 13.321717, null)
        val otherPlatform = Stop("de:11000:900062202::2", "S+U Rathaus Steglitz", 52.456200, 13.321800, null)
        val nearSameName = Stop("900099999", "Rathaus Steglitz", 52.456500, 13.321900, null)
        val farSameName = Stop("900099998", "Rathaus Steglitz", 52.470000, 13.340000, null)
        val cancelled = departure("S1", cancelled = true).copy(stop = onePlatform)
        listOf(onePlatform, otherPlatform, nearSameName).forEach { place ->
            val extra = departure("S1", minutes = 1, additional = true).copy(stop = place)
            val board = mergeDepartures(listOf(listOf(cancelled), listOf(extra)))
            assertEquals(1, board.size)
            assertEquals("S1", board[0].replaces)
        }
        // A stop far away with the same name is another place.
        val far = departure("S1", minutes = 1, additional = true).copy(stop = farSameName)
        assertEquals(2, mergeDepartures(listOf(listOf(cancelled), listOf(far))).size)
    }
}
