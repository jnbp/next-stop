package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** One train found twice under different names (see withoutSameRunTwins). */
class SameRunTwinsTest {
    private val start: OffsetDateTime = OffsetDateTime.parse("2026-12-03T16:56:00+01:00")
    private fun at(minutes: Long) = start.plusMinutes(minutes)

    private fun ride(
        line: String,
        leaves: Long,
        arrives: Long,
        product: String = "express",
        from: String = "Dresden-Neustadt",
        to: String = "Berlin Südkreuz",
        realtime: Boolean = false,
    ) = Leg(
        line, product, false, from, to, at(leaves), at(leaves), at(arrives), at(arrives), null, null,
        "Hamburg Hbf", false, realtime = realtime,
    )

    @Test
    fun railjetWithAndWithoutItsKindIsOneJourney() {
        val named = Journey(listOf(ride("RJ 174", 0, 117)))
        val bare = Journey(listOf(ride("174", 4, 144)))
        assertTrue(sameRunTwins(named, bare))
        assertEquals(listOf(named), withoutSameRunTwins(listOf(named, bare)))
        // The named one wins wherever it stands.
        assertEquals(listOf(named), withoutSameRunTwins(listOf(bare, named)))
    }

    @Test
    fun liveTimesWinOverAName() {
        val named = Journey(listOf(ride("RJ 174", 0, 117)))
        val live = Journey(listOf(ride("174", 4, 144, realtime = true)))
        val kept = withoutSameRunTwins(listOf(named, live))
        assertEquals(1, kept.size)
        // The live copy stays, with the kind of the train its twin named: "RJ 174", not "174".
        assertTrue(kept[0].rides[0].realtime)
        assertEquals(at(4), kept[0].rides[0].departure)
        assertEquals("RJ 174", kept[0].rides[0].line)
        assertEquals("RJ 174", withoutSameRunTwins(listOf(live, named))[0].rides[0].line)
    }

    @Test
    fun twinNamesFillOnlyRidesWithoutAKind() {
        val named = Journey(listOf(ride("RJ 174", 0, 117)))
        assertSame(named, withTwinNames(named, Journey(listOf(ride("174", 4, 144)))))
    }

    @Test
    fun regionalLinesAreNotRuns() {
        // Many runs share a line number: RE 1 and RE 10, or two RE 1 a few minutes apart, stay.
        assertFalse(sameRunTwins(Journey(listOf(ride("RE 1", 0, 60, "regional"))), Journey(listOf(ride("RE 10", 0, 60, "regional")))))
        assertFalse(sameRunTwins(Journey(listOf(ride("RE 1", 0, 60, "regional"))), Journey(listOf(ride("RE 1", 10, 70, "regional")))))
        assertNull(trainRunOf(ride("RE 1", 0, 60, "regional")))
        assertNull(trainRunOf(ride("100", 0, 60, "bus")))
        assertNull(trainRunOf(ride("17", 0, 60, "express")))
    }

    @Test
    fun otherKindOrNumberIsAnotherTrain() {
        val rj = Journey(listOf(ride("RJ 174", 0, 117)))
        assertFalse(sameRunTwins(rj, Journey(listOf(ride("ICE 174", 0, 117)))))
        assertFalse(sameRunTwins(rj, Journey(listOf(ride("RJ 176", 0, 117)))))
        assertFalse(sameRunTwins(rj, Journey(listOf(ride("1740", 0, 117)))))
    }

    @Test
    fun sameNumberOnAnotherRouteOrDayIsAnotherTrain() {
        val rj = Journey(listOf(ride("RJ 174", 0, 117)))
        assertFalse(sameRunTwins(rj, Journey(listOf(ride("174", 0, 117, from = "Leipzig Hbf")))))
        assertFalse(sameRunTwins(rj, Journey(listOf(ride("174", 0, 117, to = "Berlin Hbf")))))
        assertFalse(sameRunTwins(rj, Journey(listOf(ride("174", 30, 147)))))
        assertFalse(sameRunTwins(rj, Journey(listOf(ride("174", 24 * 60, 24 * 60 + 117)))))
    }

    @Test
    fun stationNamesWrittenDifferentlyMatch() {
        val a = Journey(listOf(ride("RJ 174", 0, 117)))
        val b = Journey(listOf(ride("174", 0, 117, from = "Dresden Neustadt", to = "S+U Berlin Südkreuz")))
        assertTrue(sameRunTwins(a, b))
    }

    @Test
    fun mergedPagesKeepOneCopy() {
        val named = Journey(listOf(ride("RJ 174", 0, 117)))
        val bare = Journey(listOf(ride("174", 4, 144)))
        val other = Journey(listOf(ride("EC 176", 120, 237)))
        assertEquals(listOf(named, other), mergeSearchPages(listOf(named), listOf(bare, other)))
    }

    @Test
    fun bareNumbersOfUnknownProductStayApart() {
        // A line number of an unknown mode repeats all day: two rides 10 minutes apart stay.
        val a = Journey(listOf(ride("174", 0, 30, product = "")))
        val b = Journey(listOf(ride("174", 10, 40, product = "")))
        assertFalse(sameRunTwins(a, b))
    }

    /**
     * #280: Transitous found the Railjet Berlin - Hamburg twice, as DELFI's "ICE 384" and as
     * Rejseplanen's "RJ 384" (no way of its own, foreign names), each after the same U-Bahn. The
     * journeys are one, and the German feed's copy stays.
     */
    @Test
    fun aForeignFeedsCopyOfAGermanTrainGivesWayToTheGermanOne() {
        fun trip(line: String, leaves: Long, arrives: Long, tripId: String, product: String = "express", from: String = "Berlin Hbf", to: String = "Hamburg Hbf") =
            ride(line, leaves, arrives, product = product, from = from, to = to).copy(tripId = tripId)
        val subway = trip("U5", -20, -8, "tr:20261203_16:36_de-VBB_5", product = "subway", from = "Alexanderplatz", to = "Berlin Hbf")
        val onward = trip("ICE 201", 120, 200, "tr:20261203_18:56_de-DELFI_201", from = "Hamburg Hbf", to = "Bremen Hbf")
        val danish = Journey(listOf(subway, trip("RJ 384", 0, 110, "tr:20261203_16:56_dk-rejseplanen_384"), onward))
        val german = Journey(listOf(subway, trip("ICE 384", 0, 110, "tr:20261203_16:56_de-DELFI_384"), onward))
        assertTrue(isForeignFeedRide(danish.rides[1]))
        assertFalse(isForeignFeedRide(german.rides[1]))
        assertFalse(isForeignFeedRide(ride("RJ 384", 0, 110)))
        assertTrue(sameRunTwins(danish, german))
        assertEquals(listOf(german), withoutSameRunTwins(listOf(danish, german)))
        assertEquals(listOf(german), withoutSameRunTwins(listOf(german, danish)))
        // Another planned minute: other trains, as two kinds of one number elsewhere.
        val later = Journey(listOf(subway, trip("RJ 384", 1, 110, "tr:20261203_16:57_dk-rejseplanen_384"), onward))
        assertFalse(sameRunTwins(later, german))
        // Another U-Bahn trip: another journey.
        val otherSubway = Journey(listOf(subway.copy(tripId = "tr:20261203_16:26_de-VBB_5"), german.rides[1], onward))
        assertFalse(sameRunTwins(otherSubway, danish))
    }
}
