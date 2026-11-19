package io.github.jnbp.nextstop.wear.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The level of change time of the watch's search also limits what it offers: at "knapp" no
 * journey with a sprint change (W2). The case seen: a U-Bahn change of 0 minutes (U3 into the
 * U2 at U Mittelplatz), which a search at "knapp" (0 minutes asked of the provider) returns
 * and the phone's rule rates a sprint, as every change under 2 minutes.
 */
class ComfortFilterTest {
    private val minute = 60_000L

    private fun ride(line: String, from: String, to: String, dep: Long, arr: Long, platform: String? = null, rating: ChangeRating? = null) =
        Leg(line, "subway", false, from, to, dep * minute, arr * minute, 0, 0, platform, "Towards", false, changeRating = rating)

    private fun walk(from: String, to: String, dep: Long, arr: Long) =
        Leg(null, "walk", true, from, to, dep * minute, arr * minute, 0, 0, null, null, false)

    // 18:33 to 18:36 and on at 18:36: a change of 0 minutes.
    private fun sprint(rating: ChangeRating? = null) = Journey(
        listOf(
            walk("Somewhere", "U Stop A", 1101, 1113),
            ride("U3", "U Stop A", "U Mittelplatz", 1113, 1116, platform = "2"),
            ride("U2", "U Mittelplatz", "S+U Alexanderplatz", 1116, 1134, platform = "2", rating = rating),
        ),
        "sprint",
    )

    // The same lines with 4 minutes at the change: normal by the phone's rule.
    private val normal = Journey(
        listOf(
            ride("U3", "U Stop A", "U Mittelplatz", 1123, 1126),
            ride("U2", "U Mittelplatz", "S+U Alexanderplatz", 1130, 1148),
        ),
        "normal",
    )

    private val direct = Journey(listOf(ride("U2", "U Stop B", "S+U Alexanderplatz", 1120, 1140)), "direct")

    @Test
    fun aChangeOfNoMinuteIsASprintAsOnThePhone() {
        val plan = journeyPlan(sprint())
        assertEquals(0, plan.rides[1].gap)
        assertEquals(ChangeRating.VeryRisky, plan.rides[1].rating)
    }

    @Test
    fun knappLeavesOutTheSprintChange() {
        val found = listOf(sprint(), normal, direct)
        assertEquals(listOf("normal", "direct"), journeysWithin(found, TransferComfort.Risky).map { it.refreshToken })
        // At "Sprint" every journey stays.
        assertEquals(listOf("sprint", "normal", "direct"), journeysWithin(found, TransferComfort.VeryRisky).map { it.refreshToken })
    }

    @Test
    fun normalKeepsAChangeOfFourMinutes() {
        // What a search at "normal" leaves (4 minutes) is normal, as on the phone, not "knapp".
        assertEquals(ChangeRating.Tight, journeyPlan(normal).rides[1].rating)
        assertEquals(listOf("normal", "direct"), journeysWithin(listOf(sprint(), normal, direct), TransferComfort.Normal).map { it.refreshToken })
        assertEquals(listOf("direct"), journeysWithin(listOf(normal, direct), TransferComfort.Relaxed).map { it.refreshToken })
    }

    @Test
    fun thePhonesRatingWins() {
        // The phone knows the station: where it rates the change "knapp", the watch shows and keeps it so.
        val rated = sprint(ChangeRating.Risky)
        assertEquals(ChangeRating.Risky, journeyPlan(rated).rides[1].rating)
        assertEquals(listOf("sprint"), journeysWithin(listOf(rated), TransferComfort.Risky).map { it.refreshToken })
        // The upcoming ride of the trip page rates the change the same.
        val step = snapshotOf(rated, 1114 * minute)
        assertEquals(ChangeRating.Risky, step.next?.rating)
    }
}
