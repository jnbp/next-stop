package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** The mode filters of the departures header (#223). */
class DepartureModeFacesTest {
    private val order = listOf("suburban", "subway", "tram", "bus", "regional", "express", "coach", "ferry")

    @Test
    fun sAndUByTheirLetterTheOthersByAnIcon() {
        assertEquals(ModeFace(letter = "S"), departureModeFace("suburban"))
        assertEquals(ModeFace(letter = "U"), departureModeFace("subway"))
        assertEquals(ModeIcon.Bus, departureModeFace("bus").icon)
        assertEquals(ModeIcon.Tram, departureModeFace("tram").icon)
        assertEquals(ModeIcon.Ferry, departureModeFace("ferry").icon)
        // Two kinds of train, told apart beside the icon.
        assertEquals(ModeFace(icon = ModeIcon.Train, regional = true), departureModeFace("regional"))
        assertEquals(ModeFace(icon = ModeIcon.Train, longDistance = true), departureModeFace("express"))
    }

    @Test
    fun theKindsOfTheStopInThePageOrderOnlyWhenThereIsAChoice() {
        assertEquals(listOf("suburban", "subway", "bus"), departureModeFilters(order, setOf("bus", "subway", "suburban")))
        assertEquals(emptyList<String>(), departureModeFilters(order, setOf("bus")))
        assertEquals(emptyList<String>(), departureModeFilters(order, emptySet()))
    }
}
