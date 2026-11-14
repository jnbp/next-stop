package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** The stations of a journey where lifts matter (see LiftStatus.kt). */
class LiftPlacesTest {
    private val start: OffsetDateTime = OffsetDateTime.parse("2026-01-01T10:00:00+01:00")
    private fun stop(id: String) = Stop(id, "Stop $id", 52.5, 13.4, null)

    private fun leg(from: String, to: String, walking: Boolean = false) = Leg(
        if (walking) null else "U$from", "subway", walking, "Stop $from", "Stop $to", start, start, start, start,
        null, null, null, false, fromPlace = stop(from), toPlace = stop(to),
    )

    @Test
    fun startChangesAndEnd() {
        val journey = Journey(listOf(leg("900000001", "900000002"), leg("900000002", "900000003", walking = true), leg("900000003", "900000004")))
        assertEquals(
            listOf("900000001", "900000002", "900000003", "900000004"),
            liftPlaces(journey).map { it.id },
        )
    }

    @Test
    fun addressesAreLeftOut() {
        val address = Stop("address:1", "Somewhere 1", 52.5, 13.4, null, kind = PlaceKind.Address)
        val ride = leg("900000001", "900000002").copy(fromPlace = address)
        assertEquals(listOf("900000002"), liftPlaces(Journey(listOf(ride))).map { it.id })
    }
}
