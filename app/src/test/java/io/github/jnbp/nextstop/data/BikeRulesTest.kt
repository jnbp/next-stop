package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Where a bicycle may come along, for the bike combo (see BikeRules). */
class BikeRulesTest {
    private val berlin = 52.5219 to 13.4132
    private val hamburg = 53.5530 to 10.0069
    private val munich = 48.1402 to 11.5600
    private val leipzig = 51.3455 to 12.3821

    // Wednesday 14 Jan 2026 and Saturday 17 Jan 2026.
    private fun wednesday(time: String) = OffsetDateTime.parse("2026-01-14T$time:00+01:00")
    private fun saturday(time: String) = OffsetDateTime.parse("2026-01-17T$time:00+01:00")

    @Test
    fun trainsCarryBikes() {
        listOf("suburban", "subway", "tram", "ferry", "regional").forEach { product ->
            assertEquals(product, BikeCarriage.Allowed, BikeRules.carriage(product, null, berlin, wednesday("08:00")))
            assertEquals(product, BikeCarriage.Allowed, BikeRules.carriage(product, null, leipzig, wednesday("08:00")))
        }
        assertEquals(BikeCarriage.Reservation, BikeRules.carriage("express", "ICE 1005", berlin, wednesday("08:00")))
    }

    @Test
    fun busesAndCoachesDoNotByDefault() {
        assertEquals(BikeCarriage.NotAllowed, BikeRules.carriage("bus", "M41", berlin, wednesday("12:00")))
        assertEquals(BikeCarriage.NotAllowed, BikeRules.carriage("bus", "N10", berlin, wednesday("02:00")))
        assertEquals(BikeCarriage.NotAllowed, BikeRules.carriage("coach", "FlixBus 040", berlin, wednesday("12:00")))
        // What the app has no product for: no rule known (#263).
        assertEquals(BikeCarriage.Unknown, BikeRules.carriage("", null, berlin, wednesday("12:00")))
    }

    @Test
    fun bvgNightLinesForTheUBahnDo() {
        assertEquals(BikeCarriage.Allowed, BikeRules.carriage("bus", "N7", berlin, wednesday("01:30")))
        assertEquals(BikeCarriage.Allowed, BikeRules.carriage("bus", "N1", berlin, wednesday("03:00")))
        // Not an N line of another city: there no rule is known (#263).
        assertEquals(BikeCarriage.Unknown, BikeRules.carriage("bus", "N7", leipzig, wednesday("01:30")))
    }

    @Test
    fun rushHoursOfHamburgAndMunich() {
        assertEquals(BikeCarriage.NotAllowed, BikeRules.carriage("subway", "U3", hamburg, wednesday("07:30")))
        assertEquals(BikeCarriage.NotAllowed, BikeRules.carriage("suburban", "S1", hamburg, wednesday("17:00")))
        assertEquals(BikeCarriage.Allowed, BikeRules.carriage("subway", "U3", hamburg, wednesday("12:00")))
        assertEquals(BikeCarriage.Allowed, BikeRules.carriage("subway", "U3", hamburg, saturday("07:30")))
        // Regional trains are not banned there.
        assertEquals(BikeCarriage.Allowed, BikeRules.carriage("regional", "RE 7", hamburg, wednesday("07:30")))
        // Munich: S-Bahn mornings only, U-Bahn mornings and evenings.
        assertEquals(BikeCarriage.NotAllowed, BikeRules.carriage("suburban", "S8", munich, wednesday("06:30")))
        assertEquals(BikeCarriage.Allowed, BikeRules.carriage("suburban", "S8", munich, wednesday("17:00")))
        assertEquals(BikeCarriage.NotAllowed, BikeRules.carriage("subway", "U6", munich, wednesday("17:00")))
        // 9:00 sharp is free again.
        assertEquals(BikeCarriage.Allowed, BikeRules.carriage("subway", "U6", munich, wednesday("09:00")))
        // Berlin has no rush-hour ban.
        assertEquals(BikeCarriage.Allowed, BikeRules.carriage("subway", "U2", berlin, wednesday("07:30")))
    }

    @Test
    fun journeysWithABusAreDropped() {
        fun ride(product: String, line: String, at: OffsetDateTime) = Leg(
            line, product, false, "A", "B", at, at, at.plusMinutes(10), at.plusMinutes(10), null, null, null, false,
            fromPlace = Stop("1", "A", berlin.first, berlin.second, null),
        )
        val at = wednesday("12:00")
        assertTrue(BikeRules.journeyCarriesBike(Journey(listOf(ride("suburban", "S5", at), ride("regional", "RE1", at)))))
        assertFalse(BikeRules.journeyCarriesBike(Journey(listOf(ride("suburban", "S5", at), ride("bus", "M41", at)))))
        val combo = SearchOptions(bikeCombo = true)
        assertFalse(ticketFits(Journey(listOf(ride("bus", "M41", at))), combo))
        assertTrue(ticketFits(Journey(listOf(ride("bus", "N2", wednesday("02:00")))), combo))
    }

    @Test
    fun comboAsksForBusesOnlyAtNightAroundBerlin() {
        assertTrue(BikeRules.busesWorthAsking(berlin, wednesday("01:00")))
        assertFalse(BikeRules.busesWorthAsking(berlin, wednesday("12:00")))
        assertFalse(BikeRules.busesWorthAsking(leipzig, wednesday("01:00")))
        val all = PRODUCT_KEYS.toSet()
        assertEquals(all - "bus" - "coach", BikeRules.comboProducts(all, buses = false))
        assertEquals(all - "coach", BikeRules.comboProducts(all, buses = true))
        assertEquals(setOf("suburban", "subway", "tram", "regional"), BikeRules.comboProducts(setOf("bus", "coach"), buses = false))
    }

    private fun ride(product: String, line: String, at: OffsetDateTime, where: Pair<Double, Double> = berlin, remark: String? = null) = Leg(
        line, product, false, "A", "B", at, at, at.plusMinutes(10), at.plusMinutes(10), null, null, null, false,
        fromPlace = Stop("1", "A", where.first, where.second, null),
        bikeNote = remark,
    )

    @Test
    fun bikeAlongKeepsRidesWhoseProviderSaysNothingOrNo() {
        // #263: "Fahrrad dabei" is no provider filter; a remark "no bicycles" on the Berlin S-Bahn changes nothing.
        val bike = SearchOptions(bike = true)
        val at = wednesday("08:00")
        assertTrue(ticketFits(Journey(listOf(ride("suburban", "S1", at, remark = "Fahrradmitnahme nicht möglich"))), bike))
        assertTrue(ticketFits(Journey(listOf(ride("subway", "U2", at), ride("tram", "M10", at))), bike))
        assertTrue(ticketFits(Journey(listOf(ride("regional", "RE 1", at, where = leipzig))), bike))
        // A bus outside Berlin: no rule known, it stays.
        assertTrue(ticketFits(Journey(listOf(ride("bus", "60", at, where = leipzig))), bike))
        // The page keeps them too.
        val page = JourneyPage(listOf(Journey(listOf(ride("suburban", "S1", at)))), null, null).fitting(bike)
        assertEquals(1, page.journeys.size)
    }

    @Test
    fun bikeAlongDropsOnlyRidesKnownNotToTakeABicycle() {
        val bike = SearchOptions(bike = true)
        // BVG day bus, hvv U-Bahn in the morning rush hour, a coach.
        assertFalse(ticketFits(Journey(listOf(ride("suburban", "S1", wednesday("12:00")), ride("bus", "M41", wednesday("12:00")))), bike))
        assertFalse(ticketFits(Journey(listOf(ride("subway", "U3", wednesday("07:30"), where = hamburg))), bike))
        assertFalse(ticketFits(Journey(listOf(ride("coach", "FlixBus 040", wednesday("12:00")))), bike))
        // A BVG night line for the U-Bahn and an ICE (with a reservation) do.
        assertTrue(ticketFits(Journey(listOf(ride("bus", "N7", wednesday("01:30")))), bike))
        assertTrue(ticketFits(Journey(listOf(ride("express", "ICE 1005", wednesday("12:00")))), bike))
    }

    @Test
    fun berlinRulesAreExplicit() {
        listOf("suburban", "subway", "tram", "ferry").forEach { product ->
            assertEquals(product, BikeCarriage.Allowed, BikeRules.carriage(product, null, berlin, wednesday("07:45")))
        }
        assertEquals(BikeCarriage.NotAllowed, BikeRules.carriage("bus", "M29", berlin, wednesday("17:00")))
        assertEquals(BikeCarriage.Allowed, BikeRules.carriage("bus", "N9", berlin, wednesday("02:00")))
    }

    @Test
    fun theBikeComboStillNeedsEveryRideKnownToTakeTheBicycle() {
        val combo = SearchOptions(bikeCombo = true)
        val at = wednesday("12:00")
        assertFalse(ticketFits(Journey(listOf(ride("bus", "60", at, where = leipzig))), combo))
        assertTrue(ticketFits(Journey(listOf(ride("regional", "RE 1", at, where = leipzig))), combo))
        assertFalse(BikeRules.journeyCarriesBike(Journey(listOf(ride("", "X", at)))))
        assertTrue(BikeRules.journeyMayCarryBike(Journey(listOf(ride("", "X", at)))))
    }
}
