package io.github.jnbp.nextstop.data

import io.github.jnbp.nextstop.widget.storedWidgetProducts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Long-distance trains and coaches as kinds of transport of their own (see Products.kt). */
class ProductsTest {
    @Test
    fun keysInTheOrderShown() {
        assertEquals(listOf("suburban", "subway", "tram", "bus", "ferry", "regional", "express", "coach"), PRODUCT_KEYS)
    }

    @Test
    fun kindOfALine() {
        assertEquals("ICE", lineKind("ICE 1234"))
        assertEquals("ICE", lineKind("ICE1234"))
        assertEquals("RE", lineKind("RE 1"))
        assertEquals("IC", lineKind("IC"))
        assertEquals("BUS", lineKind("Bus 100"))
        assertNull(lineKind("Flughafen"))
        assertEquals("S", lineKind("S41/S42"))
        assertNull(lineKind(null))
    }

    @Test
    fun iceUnderRegionalIsLongDistance() {
        // The bug: a feed (GTFS route type 2, "RAIL") or provider files an ICE under regional trains.
        assertEquals("express", classifyProduct("regional", "ICE 1005"))
        assertEquals("express", classifyProduct("regional", "IC 2213"))
        assertEquals("express", classifyProduct("regional", "EC 175"))
        assertEquals("express", classifyProduct("regional", "RJX 61"))
        assertEquals("express", classifyProduct("regional", "NJ 40490"))
        assertEquals("express", classifyProduct("regional", "FLX 30"))
        assertEquals("express", classifyProduct("", "ICE 29"))
        // Regional trains stay regional.
        assertEquals("regional", classifyProduct("regional", "RE 1"))
        assertEquals("regional", classifyProduct("regional", "RB 23"))
        assertEquals("regional", classifyProduct("regional", "IRE 3"))
        assertEquals("regional", classifyProduct("regional", "FEX"))
        assertEquals("regional", classifyProduct("regional", null))
        // An S-Bahn or a long-distance train keep what the provider says.
        assertEquals("suburban", classifyProduct("suburban", "S1"))
        assertEquals("express", classifyProduct("express", "ICE 1005"))
        // A lone "D" says too little without the provider naming a train.
        assertEquals("", classifyProduct("", "D 1"))
    }

    @Test
    fun flixBusIsACoach() {
        assertEquals("coach", classifyProduct("bus", "FlixBus N040"))
        assertEquals("coach", classifyProduct("bus", "040", "FlixBus DACH GmbH"))
        assertEquals("coach", classifyProduct("bus", "FLX 040"))
        assertEquals("coach", classifyProduct("bus", "BlaBlaCar Bus 12"))
        assertEquals("bus", classifyProduct("bus", "M41", "BVG"))
        assertEquals("bus", classifyProduct("bus", "N9"))
        assertTrue(isCoachName("ICB 18", null))
        assertFalse(isCoachName("X10", "Berliner Verkehrsbetriebe"))
    }

    @Test
    fun hafasQuerySwitchesOffWhatIsOff() {
        assertEquals("", hafasProductQuery(PRODUCT_KEYS.toSet()))
        assertEquals("&express=false", hafasProductQuery(PRODUCT_KEYS.toSet() - "express"))
        // HAFAS knows no coaches: buses stay on while coaches are.
        assertEquals("", hafasProductQuery(PRODUCT_KEYS.toSet() - "bus"))
        assertEquals("", hafasProductQuery(PRODUCT_KEYS.toSet() - "coach"))
        assertEquals("&bus=false", hafasProductQuery(PRODUCT_KEYS.toSet() - "bus" - "coach"))
        assertEquals("&express=false", hafasProductQuery(SearchOptions().withLocalOnly(true).products))
    }

    @Test
    fun widgetListsOfOldGetTheNewKeys() {
        // "regional" brought long-distance trains along, buses the coaches.
        assertEquals(setOf("regional", "express", "bus", "coach"), storedWidgetProducts(null, "regional,bus"))
        assertEquals(setOf("subway"), storedWidgetProducts(null, "subway"))
        // A list stored since stays as it is.
        assertEquals(setOf("regional", "bus"), storedWidgetProducts("regional,bus", "subway"))
        assertEquals(PRODUCT_KEYS.toSet(), storedWidgetProducts(null, null))
    }
}
