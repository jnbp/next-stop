package io.github.jnbp.nextstop.wear.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The departures board of the watch follows the rules of the phone (ReplacementRunTest and
 * DepartureBoardTest there): line names given by their number alone, a replacement run in
 * place of the cancelled one, an additional run that doubles a regular one shown once, and
 * cancelled runs at their planned time. As seen at S-Bahn stations on 13 November 2026.
 */
class DepartureBoardTest {
    // A planned time on a whole minute, as epoch milliseconds.
    private val planned = 1_763_035_560_000L

    private fun departure(
        line: String,
        minutes: Long = 0,
        cancelled: Boolean = false,
        additional: Boolean = false,
        platform: String? = "1",
        direction: String = "S Wannsee",
        delay: Long = 0,
    ) = Departure(
        line = line,
        product = "suburban",
        direction = direction,
        // BVG HAFAS sends a cancelled run without a live time; the watch then keeps the planned one.
        time = planned + (minutes + if (cancelled) 0 else delay) * 60_000,
        delayMinutes = if (cancelled) null else delay.toInt(),
        platform = platform,
        cancelled = cancelled,
        plannedTime = planned + minutes * 60_000,
        additional = additional,
    )

    @Test
    fun numberOnlyRapidLinesGetTheirLetter() {
        assertEquals("S1", rapidLineName("1", "suburban"))
        assertEquals("U9", rapidLineName("9", "subway"))
        assertEquals("S41", rapidLineName("S41", "suburban"))
        assertEquals("186", rapidLineName("186", "bus"))
        assertEquals("RE 1", rapidLineName("RE 1", "regional"))
        assertEquals("S1", rapidLineName(" 1 ", "suburban"))
        assertNull(rapidLineName(null, "suburban"))
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
        // The watch at an S-Bahn station: "S1 S Oranienburg 13 min" and "1 S Oranienburg 13 min".
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

    @Test
    fun withoutAdditionalRunsTheBoardStaysAsItIs() {
        val list = listOf(departure("S1"), departure("S1", cancelled = true, minutes = 10))
        assertEquals(list, mergeReplacements(list))
    }

    @Test
    fun cancelledRunsStandAtTheirPlannedTime() {
        // HAFAS order: the cancelled run first, as its empty live time sorts before all others.
        val answer = listOf(
            departure("S2", 9, cancelled = true, platform = "2", direction = "S Blankenfelde").copy(time = null),
            departure("S41", 3),
            departure("S42", 5, platform = "12", direction = "Ring S42"),
            departure("S41", 13, delay = 2),
        )
        val board = departureBoard(answer)
        assertEquals(listOf(3L, 5L, 9L, 13L), board.map { (it.plannedTime!! - planned) / 60_000 })
        assertEquals(true, board[2].cancelled)
    }

    @Test
    fun aDelayedRunGoesByItsLiveTime() {
        val board = inDepartureOrder(listOf(departure("S41", 3, delay = 6), departure("S42", 5, platform = "12")))
        assertEquals("S42", board[0].line)
    }

    @Test
    fun departuresWithoutAnyTimeGoLast() {
        val none = departure("S1").copy(time = null, plannedTime = null)
        assertEquals(none, inDepartureOrder(listOf(none, departure("S41", 3))).last())
    }

    @Test
    fun theReportedBoardShowsEachTrainOnce() {
        // S1 to Zehlendorf late, S1 to Oranienburg and its double "1", all at 13 minutes.
        val board = departureBoard(
            listOf(
                departure("S1", 10, direction = "S Zehlendorf", platform = "2", delay = 3),
                departure("S1", 13, direction = "S Oranienburg"),
                departure("S1", 13, direction = "S Oranienburg", additional = true),
            ),
        )
        assertEquals(listOf("S Zehlendorf", "S Oranienburg"), board.map { it.direction })
        assertEquals(false, board.any { it.additional })
    }

    @Test
    fun anS1AndAnExtraOneWithoutRemarkAreOneRide() {
        // A rider's watch: "S1 S Oranienburg 13 min" and "1 S Oranienburg 13 min", the "1" without
        // the remark of an additional run; named "S1" by rapidLineName before the board rules.
        val regular = departure("S1", 13, platform = "1", direction = "S Oranienburg")
        val extra = departure(rapidLineName("1", "suburban")!!, 13, platform = "1", direction = "S Oranienburg")
        val south = departure("S1", 10, delay = 3, platform = "2", direction = "S Zehlendorf")
        assertEquals(listOf(south, regular), departureBoard(listOf(south, regular, extra)))
        assertEquals(listOf(extra), departureBoard(listOf(extra, regular)))
    }

    @Test
    fun theRegularRunWithLiveDataIsKept() {
        val regular = departure("S1", 13, direction = "S Oranienburg")
        val extra = departure("S1", 13, direction = "S Oranienburg", additional = true)
        assertEquals(listOf(regular), withoutDoubleRides(listOf(extra, regular)))
        val noLive = regular.copy(delayMinutes = null)
        val live = departure("S1", 13, delay = 1, platform = null, direction = "S Oranienburg")
        assertEquals(listOf(live), withoutDoubleRides(listOf(noLive, live)))
        // A cancelled S1 and a "1" that goes without the remark: the one that goes stays.
        val cancelled = departure("S1", 13, cancelled = true, direction = "S Oranienburg")
        assertEquals(listOf(live), withoutDoubleRides(listOf(cancelled, live)))
    }

    @Test
    fun anotherMinutePlatformOrDirectionIsAnotherRide() {
        val regular = departure("S1", 13, platform = "1", direction = "S Oranienburg")
        listOf(
            departure("S1", 14, platform = "1", direction = "S Oranienburg"),
            departure("S1", 13, platform = "2", direction = "S Oranienburg"),
            departure("S1", 13, platform = "1", direction = "S Wannsee"),
            departure("S2", 13, platform = "1", direction = "S Oranienburg"),
        ).forEach { other -> assertEquals(2, withoutDoubleRides(listOf(regular, other)).size) }
    }

    @Test
    fun namesInOneForm() {
        assertEquals("S1", normalLine("S 1"))
        assertEquals("M41", normalLine("Bus M41"))
        assertEquals(stationNameKey("S Oranienburg"), stationNameKey("S+U Oranienburg (Berlin)"))
    }
}
