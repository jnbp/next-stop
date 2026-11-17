package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AddressOrderTest {
    @Test
    fun numberInFrontMovesBehindTheStreet() {
        assertEquals("Musterstraße 32", germanStreetOrder("32 Musterstraße"))
        assertEquals("Musterstraße 10b", germanStreetOrder("10b Musterstraße"))
        assertEquals("Beispielweg 4A", germanStreetOrder("4 A Beispielweg"))
    }

    @Test
    fun wholeAddressKeepsPostcodeAndTown() {
        assertEquals("Musterstraße 32, 12345 Musterstadt", germanStreetOrder("32 Musterstraße, 12345 Musterstadt"))
        assertEquals("Café X, Am Beispielplatz 7", germanStreetOrder("Café X, 7 Am Beispielplatz"))
    }

    @Test
    fun germanOrderAndOtherNamesStay() {
        assertEquals("Musterstraße 32", germanStreetOrder("Musterstraße 32"))
        assertEquals("12345 Musterstadt", germanStreetOrder("12345 Musterstadt"))
        assertEquals("3 Linden", germanStreetOrder("3 Linden"))
        assertEquals("S Musterbahnhof", germanStreetOrder("S Musterbahnhof"))
        assertEquals("", germanStreetOrder(""))
    }

    @Test
    fun endingsOfTownsAndNamesAreNoStreets() {
        assertEquals("2 Nürnberg", germanStreetOrder("2 Nürnberg"))
        assertEquals("4 Ernst", germanStreetOrder("4 Ernst"))
        assertEquals("1 Bahnhof", germanStreetOrder("1 Bahnhof"))
        assertEquals("Ludwig 3", germanStreetOrder("Ludwig 3"))
        // As whole words they are streets.
        assertEquals("Am Berg 5", germanStreetOrder("5 Am Berg"))
        assertEquals("Main St. 12", germanStreetOrder("12 Main St."))
        assertEquals("Sample Rd 7", germanStreetOrder("7 Sample Rd"))
        assertEquals("Example Way 9", germanStreetOrder("9 Example Way"))
        assertEquals("Musterstr. 8", germanStreetOrder("8 Musterstr."))
    }

    @Test
    fun shownNamesTakeTheGermanOrder() {
        assertEquals("Musterstraße 32", stopNameParts("32 Musterstraße", home = null).title)
    }

    @Test
    fun fieldsMakeTheStreetFirst() {
        assertEquals("Musterstraße 32", streetWithNumber("Musterstraße", "32"))
        assertEquals("Musterstraße", streetWithNumber("Musterstraße", null))
        assertEquals(null, streetWithNumber(null, null))
    }

    @Test
    fun streetsNamedByTheirFirstWordMoveToo() {
        assertEquals("Am Dachsbau 58", germanStreetOrder("58 Am Dachsbau"))
        assertEquals("An der Mühle 3a, 12345 Musterstadt", germanStreetOrder("3a An der Mühle, 12345 Musterstadt"))
        assertEquals("Zur Linde 7", germanStreetOrder("7 Zur Linde"))
        assertEquals("Unter den Eichen 12", germanStreetOrder("12 Unter den Eichen"))
        assertEquals("Alt-Musterdorf 4", germanStreetOrder("4 Alt-Musterdorf"))
        assertEquals("Musterstadt Hbf", germanStreetOrder("Musterstadt Hbf"))
        assertEquals("3 Linden", germanStreetOrder("3 Linden"))
    }

    @Test
    fun anAddressFromItsSourceTakesAnyName() {
        assertEquals("Lindenhain 9, 12345 Musterstadt", germanStreetOrder("9 Lindenhain, 12345 Musterstadt", address = true))
        assertEquals("Hinter den Gärten 2", germanStreetOrder("2 Hinter den Gärten", address = true))
        assertEquals("12345 Musterstadt", germanStreetOrder("12345 Musterstadt", address = true))
        assertEquals("Musterstraße 1", germanStreetOrder("Musterstraße 1", address = true))
    }
}
