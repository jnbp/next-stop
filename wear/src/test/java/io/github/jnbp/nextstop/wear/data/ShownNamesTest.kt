package io.github.jnbp.nextstop.wear.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** The names the watch shows of places and of its own trips, as the phone shows them (#177). */
class ShownNamesTest {
    @Test
    fun aStopInTheTownOfTheWatchIsShownWithoutIt() {
        val stop = Place("1", "S+U Westhafen (Berlin)", 52.5, 13.3, PlaceKind.Stop)
        assertEquals("S+U Westhafen", shownPlaceName(stop, "Berlin"))
        // Another town stays, and so does an address as it came.
        assertEquals("S Golm (Potsdam)", shownPlaceName(stop.copy(name = "S Golm (Potsdam)"), "Berlin"))
        assertEquals("Hauptstr. 1 (Berlin)", shownPlaceName(stop.copy(name = "Hauptstr. 1 (Berlin)", kind = PlaceKind.Address), "Berlin"))
    }

    @Test
    fun theSublineIsLeftOutWhereItRepeatsTheLabel() {
        val stop = Place("1", "S+U Westhafen (Berlin)", 52.5, 13.3, PlaceKind.Stop)
        assertNull(placeSubline("S+U Westhafen", stop, "Berlin"))
        assertEquals("S+U Westhafen", placeSubline("Arbeit", stop, "Berlin"))
    }

    @Test
    fun aTripOfTheWatchShowsItsStopsWithoutTheTown() {
        val trip = TripSnapshot(
            active = true,
            fromPhone = false,
            place = "S+U Westhafen (Berlin)",
            destination = "S Golm (Potsdam)",
            direction = "S Spandau (Berlin)",
            rideTo = "S Westkreuz (Berlin)",
            alertKey = "S41|S+U Westhafen (Berlin)|1",
            stops = listOf(RideStop("S Beusselstr. (Berlin)", 1L)),
            next = NextRide("S7", "suburban", "S Potsdam Hbf (Potsdam)", "S Westkreuz (Berlin)", null, 2L, 3),
        )
        val shown = tripWithShownNames(trip, "Berlin")
        assertEquals("S+U Westhafen", shown.place)
        assertEquals("S Golm (Potsdam)", shown.destination)
        assertEquals("S Spandau", shown.direction)
        assertEquals("S Westkreuz", shown.rideTo)
        assertEquals("S Beusselstr.", shown.stops.single().name)
        assertEquals("S Westkreuz", shown.next?.stop)
        assertEquals("S Potsdam Hbf (Potsdam)", shown.next?.direction)
        // The key of the buzz stays the raw one, so a ride vibrates once only.
        assertEquals(trip.alertKey, shown.alertKey)
    }

    @Test
    fun aTripOfThePhoneKeepsTheNamesThePhoneSent() {
        val trip = TripSnapshot(active = true, fromPhone = true, place = "S Golm (Potsdam)")
        assertSame(trip, tripWithShownNames(trip, "Potsdam"))
    }
}
