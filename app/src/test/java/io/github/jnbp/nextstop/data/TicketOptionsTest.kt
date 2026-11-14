package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Deutschlandticket and bicycle options of a search (see TicketOptions.kt). */
class TicketOptionsTest {
    private val start: OffsetDateTime = OffsetDateTime.parse("2026-01-01T10:00:00+01:00")

    private fun ride(product: String, line: String = "X") = Leg(
        line, product, false, "A", "B", start, start, start.plusMinutes(10), start.plusMinutes(10),
        null, null, null, false,
    )

    private val regional = Journey(listOf(ride("regional"), ride("subway")))
    private val ice = Journey(listOf(ride("express")))
    private val flixBus = Journey(listOf(ride("coach")))
    private val localOnly = SearchOptions().withLocalOnly(true)

    @Test
    fun deutschlandticketDropsLongDistance() {
        assertTrue(localOnly.localOnly)
        assertTrue(ticketFits(regional, localOnly))
        assertFalse(ticketFits(ice, localOnly))
        assertFalse(ticketFits(flixBus, localOnly))
        val page = JourneyPage(listOf(regional, ice, flixBus), null, null).fitting(localOnly)
        assertEquals(listOf(regional), page.journeys)
    }

    @Test
    fun withoutTheOptionEverythingFits() {
        assertTrue(ticketFits(ice, SearchOptions()))
        assertEquals(3, JourneyPage(listOf(regional, ice, flixBus), null, null).fitting(SearchOptions()).journeys.size)
    }

    @Test
    fun deutschlandticketIsTheLongDistanceProductsOff() {
        val all = SearchOptions()
        assertFalse(all.localOnly)
        assertEquals(PRODUCT_KEYS.toSet() - setOf("express", "coach"), localOnly.products)
        // Off again: both back on, the rest as it was.
        val noBus = all.copy(products = all.products - "bus")
        assertEquals(noBus.products, noBus.withLocalOnly(true).withLocalOnly(false).products)
        // Both switched off by hand: the switch shows on.
        assertTrue(all.copy(products = all.products - "express" - "coach").localOnly)
        // Only one of them off: not the Deutschlandticket.
        assertFalse(all.copy(products = all.products - "express").localOnly)
        assertFalse(all.copy(products = all.products - "coach").localOnly)
    }

    @Test
    fun deutschlandticketNeverLeavesNothing() {
        val onlyIce = SearchOptions(products = setOf("express"))
        val switched = onlyIce.withLocalOnly(true)
        assertTrue(switched.localOnly)
        assertEquals(PRODUCT_KEYS.toSet() - LONG_DISTANCE_PRODUCTS, switched.products)
    }

    @Test
    fun productsOffAreDroppedAfterwards() {
        val noCoach = SearchOptions(products = PRODUCT_KEYS.toSet() - "coach")
        assertFalse(ticketFits(flixBus, noCoach))
        assertTrue(ticketFits(ice, noCoach))
        // A ride of no known product passes.
        assertTrue(ticketFits(Journey(listOf(ride(""))), noCoach))
    }

    @Test
    fun chipsAndSwitchingOff() {
        val options = localOnly.copy(bike = true)
        assertEquals(listOf(TicketFilter.LocalOnly, TicketFilter.Bike), ticketFilters(options))
        val off = withoutTicketFilter(options, TicketFilter.LocalOnly)
        assertEquals(listOf(TicketFilter.Bike), ticketFilters(off))
        assertEquals(PRODUCT_KEYS.toSet(), off.products)
        assertEquals(emptyList<TicketFilter>(), ticketFilters(SearchOptions()))
        // The bike combo carries the bicycle anyway: one chip for both.
        val combo = SearchOptions(bike = true, bikeCombo = true)
        assertEquals(listOf(TicketFilter.BikeCombo), ticketFilters(combo))
        assertTrue(combo.carriesBike)
        assertEquals(listOf(TicketFilter.Bike), ticketFilters(withoutTicketFilter(combo, TicketFilter.BikeCombo)))
    }
}
