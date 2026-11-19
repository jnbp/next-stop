package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StopTownsTest {
    @Test
    fun theHomeTownAfterACommaIsLeftOut() {
        assertEquals("Rathaus", stopNameParts("Rathaus, Potsdam", "Potsdam", language = "de").title)
        assertEquals("S Golm", stopNameParts("S Golm, Potsdam", "Potsdam", language = "de").title)
        // Another town after the comma stays, so do other words after one.
        assertEquals("Rathaus, Potsdam", stopNameParts("Rathaus, Potsdam", "Berlin", language = "de").title)
        assertEquals("Hauptstr., Ecke Lindenweg", stopNameParts("Hauptstr., Ecke Lindenweg", "Potsdam", language = "de").title)
        // The same stop with its town in brackets or after a comma, or without it, reads the same.
        val names = listOf("Rathaus (Potsdam)", "Rathaus, Potsdam", "Rathaus").map { stopNameParts(it, "Potsdam", language = "de").title }
        assertEquals(listOf("Rathaus", "Rathaus", "Rathaus"), names)
    }

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
        assertEquals("S Ostkreuz", townedStopName("S Ostkreuz Bhf (Berlin)", "Berlin"))
        // Without the town of the board, a town the name says is shown.
        assertEquals("Kirchstraße · Zagelsdorf", townedStopName("Zagelsdorf, Kirchstraße", null))
        // Long-distance stations of other towns as the regional providers name them read as the railway does.
        assertEquals("Leipzig Hbf", townedStopName("Leipzig, Hauptbahnhof", "Berlin"))
        assertEquals("Lutherstadt Wittenberg Hbf", townedStopName("Lutherstadt Wittenberg, Hauptbahnhof", "Berlin"))
        assertEquals("Bitterfeld", townedStopName("Bitterfeld, Bahnhof", "Berlin"))
        assertEquals("Frankfurt (Oder)", townedStopName("Frankfurt (Oder), Bahnhof", "Berlin"))
        assertEquals("Leipzig Hbf", townedStopName("Leipzig, Hauptbahnhof", "Leipzig"))
        assertEquals("Leipzig Hbf", townedStopName("Leipzig, Hbf", null))
        // In its own town a plain station stays what the local board says.
        assertEquals("Bahnhof", townedStopName("Bitterfeld, Bahnhof", "Bitterfeld"))
        // Names the railway already writes as it does stay.
        assertEquals("Leipzig Hbf", townedStopName("Leipzig Hbf", "Berlin"))
    }

    @Test
    fun displayNameWithoutTheHomeTownAndWithThePartBelow() {
        // The VBB's name of the bus stop in Karl-Liebknecht-Straße at Alexanderplatz.
        val alex = stopNameParts("S+U Alexanderplatz (Bln) [Bus K.-Liebknecht-Str]", "Berlin")
        assertEquals("S+U Alexanderplatz", alex.title)
        assertEquals("Bus K.-Liebknecht-Str.", alex.part)
        assertEquals("S Ostkreuz", stopNameParts("S Ostkreuz Bhf (Berlin)", "Berlin").title)
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

    @Test
    fun regionalTrainsUseTheRailwaysShortWords() {
        // #168: an RE3 to "Lutherstadt Wittenberg, Hauptbahnhof" fits one line.
        assertEquals("Lutherstadt Wittenberg Hbf", railShortName("Lutherstadt Wittenberg, Hauptbahnhof"))
        assertEquals("Frankfurt (Oder)", railShortName("Frankfurt (Oder), Bahnhof"))
        assertEquals("Cottbus Hbf", railShortName("Cottbus, Hauptbahnhof"))
        assertEquals("Cottbus Hbf", railShortName("Cottbus Hauptbahnhof"))
        // Only the word: it stays.
        assertEquals("Hauptbahnhof", railShortName("Hauptbahnhof"))
        val re3 = Leg(
            "RE3", "regional", false, "Example Start", "Lutherstadt Wittenberg, Hauptbahnhof",
            java.time.OffsetDateTime.parse("2026-11-18T15:00:00+01:00"), null,
            java.time.OffsetDateTime.parse("2026-11-18T16:00:00+01:00"), null, null, null, "Lutherstadt Wittenberg, Hauptbahnhof", false,
        )
        assertEquals("Lutherstadt Wittenberg Hbf", railwayDirection(re3, "Lutherstadt Wittenberg, Hauptbahnhof"))
        assertEquals("Lutherstadt Wittenberg Hbf", shownRideStop(re3, re3.to))
        // An S-Bahn keeps the local network's style, sign in front.
        val s1 = re3.copy(line = "S1", product = "suburban")
        assertEquals("S Wannsee Bhf".plainStopName(), shownRideStop(s1, "S Wannsee Bhf"))
    }

    @Test
    fun aBareStationWordKeepsItsTown() {
        // #255: dropping the home town never leaves a bare "Hauptbahnhof".
        assertEquals("Musterstadt Hbf", stopNameParts("Hauptbahnhof (Musterstadt)", "Musterstadt", language = "de").title)
        assertEquals("Musterstadt Hbf", stopNameParts("Hbf (Musterstadt)", "Musterstadt", language = "de").title)
        assertEquals("Musterstadt Hbf", stopNameParts("Hauptbahnhof, Musterstadt", "Musterstadt", language = "de").title)
        assertEquals("Musterstadt", stopNameParts("Bahnhof (Musterstadt)", "Musterstadt", language = "de").title)
        assertEquals("Musterstadt Busbahnhof", stopNameParts("Busbahnhof (Musterstadt)", "Musterstadt", language = "de").title)
        assertEquals("Musterstadt ZOB", stopNameParts("ZOB, Musterstadt", "Musterstadt", language = "de").title)
        // A stop with a name of its own still drops the home town, a sign in front is no bare word.
        assertEquals("Rathaus", stopNameParts("Rathaus (Musterstadt)", "Musterstadt", language = "de").title)
        assertEquals("S+U Hauptbahnhof", stopNameParts("S+U Hauptbahnhof (Musterstadt)", "Musterstadt", language = "de").title)
        assertTrue(isBareStationWord("Hauptbahnhof"))
        assertTrue(isBareStationWord(" Hbf "))
        assertFalse(isBareStationWord("Musterstadt Hbf"))
    }

    @Test
    fun aRideNeverEndsAtABareHauptbahnhofWhereTheNameSaysTheTown() {
        val at = java.time.OffsetDateTime.parse("2026-11-19T09:00:00+01:00")
        fun ride(product: String, to: String) = Leg(
            "ICE 1", product, false, "Beispielstadt Hbf", to,
            at, null, at.plusHours(1), null, null, null, "Musterstadt Hbf", false,
        )
        assertEquals("Musterstadt Hbf", shownRideStop(ride("express", "Hauptbahnhof (Musterstadt)"), "Hauptbahnhof (Musterstadt)"))
        // A regional train keeps the name as sent, with its town.
        assertEquals("Hauptbahnhof (Musterstadt)", shownRideStop(ride("regional", "Hauptbahnhof (Musterstadt)"), "Hauptbahnhof (Musterstadt)"))
        assertEquals("Musterstadt Hbf", shownRideStop(ride("express", "Musterstadt Hauptbahnhof"), "Musterstadt Hauptbahnhof"))
        // Nothing tells the town: the name stays as it came.
        assertEquals("Hauptbahnhof", shownRideStop(ride("express", "Hauptbahnhof"), "Hauptbahnhof"))
    }

    @Test
    fun aStreetInBracketsIsNoTownAndStays() {
        // #278: Dresden, tram 3: "Bahnhof Neustadt" and "Bf Neustadt (Hansastr.)" are two stops.
        val hansa = "Dresden Bf Neustadt (Hansastr.)"
        assertNull(splitStopTown(hansa).town)
        assertNull(townOf(hansa))
        assertEquals(hansa, townedStopName(hansa, "Dresden"))
        assertEquals(hansa, townedStopName(hansa, null))
        assertEquals("Dresden Bf Neustadt (Hansastr)", stopNameParts("Dresden Bf Neustadt (Hansastr)", "Dresden", language = "de").title)
        assertEquals(hansa, railwayStationName(hansa))
        assertEquals(hansa, railShortName(hansa))
        // Made-up streets and squares stay too, also where the town is said after a comma.
        assertEquals("Rathaus (Lindenweg)", townedStopName("Musterstadt, Rathaus (Lindenweg)", "Musterstadt"))
        assertEquals("Rathaus (Lindenweg) · Musterstadt", townedStopName("Musterstadt, Rathaus (Lindenweg)", "Beispielstadt"))
        assertEquals("Schule (Am Marktplatz)", stopNameParts("Schule (Am Marktplatz)", "Musterstadt", language = "de").title)
        assertEquals("Parkstr. (Hauptallee)", townedStopName("Parkstr. (Hauptallee)", "Musterstadt"))
        // A town in brackets that repeats the town still goes, another one still stays apart.
        assertEquals("Rathaus", townedStopName("Rathaus (Musterstadt)", "Musterstadt"))
        assertEquals("Rathaus · Beispielstadt", townedStopName("Rathaus (Beispielstadt)", "Musterstadt"))
        assertEquals("S Beispielhof", stopNameParts("S Beispielhof (Berlin)", "Berlin", language = "de").title)
    }

    @Test
    fun twoStopsOfOneListNeverReadAlike() {
        // Parts in square brackets left out: shown again where they tell the stops apart.
        assertEquals(
            listOf("Musterplatz · Bus", "Musterplatz · Tram", "Lindenweg"),
            distinctStopNames(listOf("Musterplatz [Bus]", "Musterplatz [Tram]", "Lindenweg"), listOf("Musterplatz", "Musterplatz", "Lindenweg")),
        )
        // Shown alike but sent differently: the names as sent.
        assertEquals(
            listOf("Dresden Bahnhof Neustadt", "Dresden Bf Neustadt (Hansastr.)"),
            distinctStopNames(listOf("Dresden Bahnhof Neustadt", "Dresden Bf Neustadt (Hansastr.)"), listOf("Dresden Bf Neustadt", "Dresden Bf Neustadt")),
        )
        // The same stop twice (a loop) and different names stay as they are.
        assertEquals(listOf("A-Dorf", "B-Weg", "A-Dorf"), distinctStopNames(listOf("A-Dorf", "B-Weg", "A-Dorf"), listOf("A-Dorf", "B-Weg", "A-Dorf")))
    }

    @Test
    fun theStopsOfARideNeverReadAlike() {
        val at = java.time.OffsetDateTime.parse("2026-11-19T10:00:00+01:00")
        fun stop(name: String) = Stopover(name, at, at)
        val leg = Leg(
            "3", "tram", false, "Dresden Bf Neustadt (Hansastr.)", "Dresden Musterplatz",
            at, null, at.plusMinutes(10), null, null, null, "Dresden Musterplatz", false,
            stopovers = listOf(stop("Dresden Bf Neustadt (Hansastr.)"), stop("Dresden Bahnhof Neustadt"), stop("Dresden Musterplatz")),
        )
        assertEquals(
            listOf("Dresden Bf Neustadt (Hansastr.)", "Dresden Bahnhof Neustadt", "Dresden Musterplatz"),
            shownRideStopNames(leg, leg.stopovers, leg.rideTown),
        )
    }
}
