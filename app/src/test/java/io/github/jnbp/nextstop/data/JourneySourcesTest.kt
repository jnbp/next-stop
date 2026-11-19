package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** #277b: where the data and the live times of a journey come from, for the footer. */
class JourneySourcesTest {
    private val at = OffsetDateTime.parse("2026-11-19T10:00:00+01:00")

    private fun ride(tripId: String?, realtime: Boolean = false, hafasLive: Boolean = false, estimated: Boolean = false) = Leg(
        "3", "tram", false, "Musterplatz", "Lindenweg",
        at, at, at.plusMinutes(10), at.plusMinutes(10), null, null, "Lindenweg", false,
        realtime = realtime, tripId = tripId, hafasLive = hafasLive, arrivalEstimated = estimated,
    )

    private fun walk() = Leg(null, "walking", true, "Lindenweg", "Ziel", at, null, at.plusMinutes(5), null, null, null, null, false)

    @Test
    fun theProviderComesFromTheHandles() {
        assertEquals(Backend.Transitous, journeySources(Journey(listOf(walk(), ride("${DataSource.Transitous.prefix}abc")))).provider)
        assertEquals(Backend.Bvg, journeySources(Journey(listOf(ride("1|2345|0")))).provider)
        assertEquals(Backend.Vbb, journeySources(Journey(listOf(ride("${Backend.Vbb.tag}1|2345|0")))).provider)
        assertEquals(null, journeySources(Journey(listOf(walk()))).provider)
    }

    @Test
    fun liveTimesSayWhereTheyComeFrom() {
        val tr = DataSource.Transitous.prefix
        assertEquals(emptyList<LiveOrigin>(), journeySources(Journey(listOf(ride("${tr}a")))).live)
        assertEquals(listOf(LiveOrigin.Provider), journeySources(Journey(listOf(ride("${tr}a", realtime = true)))).live)
        assertEquals(listOf(LiveOrigin.Hafas), journeySources(Journey(listOf(ride("${tr}a", realtime = true, hafasLive = true)))).live)
        assertEquals(
            listOf(LiveOrigin.Provider, LiveOrigin.Hafas, LiveOrigin.Estimate),
            journeySources(Journey(listOf(ride("${tr}a", realtime = true), ride("${tr}b", realtime = true, hafasLive = true), ride("${tr}c", estimated = true)))).live,
        )
    }
}
