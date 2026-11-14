package io.github.jnbp.nextstop.wear.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The same cases as StopTownsTest of the phone (the twin of StopNames.kt). */
class StopNamesTest {
    @Test
    fun displayNameWithoutTheHomeTownAndWithThePartBelow() {
        val alex = stopNameParts("S+U Alexanderplatz (Bln) [Bus K.-Liebknecht-Str]", "Berlin")
        assertEquals("S+U Alexanderplatz", alex.title)
        assertEquals("Bus K.-Liebknecht-Str.", alex.part)
        assertEquals("S Ostkreuz Bhf", stopNameParts("S Ostkreuz Bhf (Berlin)", "Berlin").title)
        assertNull(stopNameParts("S Ostkreuz Bhf (Berlin)", "Berlin").part)
        assertEquals(StopNameParts("S+U Berlin Hauptbahnhof", "Gleis 1-8"), stopNameParts("S+U Berlin Hauptbahnhof [Gleis 1-8]", "Berlin"))
        assertEquals("Zoologischer Garten", stopNameParts("Zoologischer Garten (Berlin)", "Berlin").title)
        // Another town keeps its name; so do brackets that belong to a town's name or say a level.
        assertEquals("S Golm (Potsdam)", stopNameParts("S Golm (Potsdam)", "Berlin").title)
        assertEquals("Potsdam Hbf", stopNameParts("Potsdam Hbf (Potsdam)", "Potsdam").title)
        assertEquals("S+U Alexanderplatz (Bln)", stopNameParts("S+U Alexanderplatz (Bln) [U2]", "Potsdam").title)
        assertEquals("Frankfurt (Oder)", stopNameParts("Frankfurt (Oder)", "Frankfurt (Oder)").title)
        assertEquals("München Hbf (tief)", stopNameParts("München Hbf (tief)", "München").title)
        assertEquals(StopNameParts("Leipzig Hbf", null), stopNameParts("Leipzig Hbf", "Leipzig"))
        assertEquals("Markt (Zossen)", stopNameParts("Markt (Zossen)", null).title)
    }

    @Test
    fun townsHoweverWritten() {
        assertTrue(sameTown("Dahme/Mark", "Dahme (Mark)"))
        assertTrue(sameTown("Berlin", "berlin"))
        assertTrue(sameTown("Bln", "Berlin"))
        assertFalse(sameTown("Bln", "Bonn"))
        assertFalse(sameTown("Zagelsdorf", null))
        assertTrue(isTownAbbreviation("Ffm", "Frankfurt am Main"))
        assertFalse(isTownAbbreviation("Bln", "Potsdam"))
        assertFalse(isTownAbbreviation("Berlin", "Berlin"))
    }

    @Test
    fun theTownOfTheStopsAround() {
        val names = listOf("S+U Alexanderplatz (Bln) [U2]", "S+U Alexanderplatz (Bln)", "S Hackescher Markt (Bln)", "Spandauer Str. (Bln)", "Memhardstr.", "S Golm (Potsdam)")
        assertEquals("Bln", dominantTown(names))
        assertNull(dominantTown(listOf("Memhardstr.", "Frankfurt (Oder)")))
        assertNull(dominantTown(listOf("U Freie Universität (Thielplatz)", "Freie Universität (Thielplatz)", "Habelschwerdter Allee")))
        assertNull(dominantTown(listOf("Lahr (Nord)", "Markt (Nord)", "Schule (Nord)")))
    }
}
