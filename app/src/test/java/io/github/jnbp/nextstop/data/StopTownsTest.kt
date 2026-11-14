package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StopTownsTest {
    @Test
    fun townInFrontWithComma() {
        assertEquals(StopTown("Kirchstraße", "Zagelsdorf"), splitStopTown("Zagelsdorf, Kirchstraße"))
        assertEquals(StopTown("Bahnhof", "Frankfurt (Oder)"), splitStopTown("Frankfurt (Oder), Bahnhof"))
    }

    @Test
    fun townInBrackets() {
        assertEquals(StopTown("S Ostkreuz Bhf", "Berlin"), splitStopTown("S Ostkreuz Bhf (Berlin)"))
        // Brackets that belong to the name of a town are no town of their own.
        assertEquals(StopTown("Frankfurt (Oder)", null), splitStopTown("Frankfurt (Oder)"))
        assertEquals(StopTown("Dahme (Mark)", null), splitStopTown("Dahme (Mark)"))
    }

    @Test
    fun noTown() {
        assertEquals(StopTown("Kirchstraße", null), splitStopTown("Kirchstraße"))
        // A street with a number before the comma is no town.
        assertNull(splitStopTown("Hauptstr. 5, Zossen").town)
        // A station before the comma is no town either.
        assertNull(splitStopTown("S Wünsdorf-Waldstadt, Bus").town)
    }

    @Test
    fun sameTownHoweverWritten() {
        assertTrue(sameTown("Dahme/Mark", "Dahme (Mark)"))
        assertTrue(sameTown("Berlin", "berlin"))
        assertFalse(sameTown("Zagelsdorf", "Dahme (Mark)"))
        assertFalse(sameTown("Zagelsdorf", null))
    }

    @Test
    fun shownOnlyInAnotherTown() {
        assertEquals("Kirchstraße · Zagelsdorf", townedStopName("Zagelsdorf, Kirchstraße", "Dahme (Mark)"))
        assertEquals("Kirchstraße", townedStopName("Zagelsdorf, Kirchstraße", "Zagelsdorf"))
        assertEquals("Markt", townedStopName("Markt", "Zagelsdorf"))
        assertEquals("S Ostkreuz Bhf", townedStopName("S Ostkreuz Bhf (Berlin)", "Berlin"))
        // Without the town of the board, a town the name says is shown.
        assertEquals("Kirchstraße · Zagelsdorf", townedStopName("Zagelsdorf, Kirchstraße", null))
    }

    @Test
    fun displayNameWithoutTheHomeTownAndWithThePartBelow() {
        // The VBB's name of the bus stop in Karl-Liebknecht-Straße at Alexanderplatz.
        val alex = stopNameParts("S+U Alexanderplatz (Bln) [Bus K.-Liebknecht-Str]", "Berlin")
        assertEquals("S+U Alexanderplatz", alex.title)
        assertEquals("Bus K.-Liebknecht-Str.", alex.part)
        assertEquals("S Ostkreuz Bhf", stopNameParts("S Ostkreuz Bhf (Berlin)", "Berlin").title)
        assertNull(stopNameParts("S Ostkreuz Bhf (Berlin)", "Berlin").part)
        assertEquals(StopNameParts("S+U Berlin Hauptbahnhof", "Gleis 1-8"), stopNameParts("S+U Berlin Hauptbahnhof [Gleis 1-8]", "Berlin"))
        // Another town keeps its name; so do brackets that belong to a town's name or say a level.
        assertEquals("S Golm (Potsdam)", stopNameParts("S Golm (Potsdam)", "Berlin").title)
        assertEquals("Potsdam Hbf", stopNameParts("Potsdam Hbf (Potsdam)", "Potsdam").title)
        assertEquals("S+U Alexanderplatz (Bln)", stopNameParts("S+U Alexanderplatz (Bln) [U2]", "Potsdam").title)
        assertEquals("Frankfurt (Oder)", stopNameParts("Frankfurt (Oder)", "Frankfurt (Oder)").title)
        assertEquals("München Hbf (tief)", stopNameParts("München Hbf (tief)", "München").title)
        assertEquals(StopNameParts("Leipzig Hbf", null), stopNameParts("Leipzig Hbf", "Leipzig"))
        // Without a town to leave out, the name stays whole.
        assertEquals("Markt (Zossen)", stopNameParts("Markt (Zossen)", null).title)
    }

    @Test
    fun townAbbreviations() {
        assertTrue(sameTown("Bln", "Berlin"))
        assertTrue(isTownAbbreviation("Ffm", "Frankfurt am Main"))
        assertFalse(isTownAbbreviation("Bln", "Potsdam"))
        assertFalse(isTownAbbreviation("Berlin", "Berlin"))
        assertFalse(sameTown("Bln", "Bonn"))
    }

    @Test
    fun theTownOfTheStopsAround() {
        val names = listOf("S+U Alexanderplatz (Bln) [U2]", "S+U Alexanderplatz (Bln)", "S Hackescher Markt (Bln)", "Spandauer Str. (Bln)", "Memhardstr.", "S Golm (Potsdam)")
        assertEquals("Bln", dominantTown(names))
        assertNull(dominantTown(listOf("Memhardstr.", "Frankfurt (Oder)")))
        // Two stops of one place in brackets name no town; neither do parts of a town.
        assertNull(dominantTown(listOf("U Freie Universität (Thielplatz)", "Freie Universität (Thielplatz)", "Habelschwerdter Allee")))
        assertNull(dominantTown(listOf("Lahr (Nord)", "Markt (Nord)", "Schule (Nord)")))
    }
}
