package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Generic English endings of stop names in the German app (see localStopTerms). */
class StopNameTermsTest {
    private fun de(name: String) = localStopTerms(name, "de")

    @Test
    fun trainStationsNamedByMoreThanATownDropTheWords() {
        assertEquals("Berlin Südkreuz", de("Berlin Südkreuz train station"))
        assertEquals("Leipzig Hbf", de("Leipzig Hbf railway station"))
        assertEquals("Hamburg-Altona Bahnhof", de("Hamburg-Altona train station"))
    }

    @Test
    fun aTownAloneSaysBahnhof() {
        assertEquals("Göttingen Bahnhof", de("Göttingen train station"))
    }

    @Test
    fun busStationsAndCentralStations() {
        assertEquals("Göttingen ZOB", de("Göttingen central bus station"))
        assertEquals("Hamburg ZOB", de("Hamburg bus station"))
        assertEquals("Kassel ZOB", de("Kassel, coach station"))
        assertEquals("Göttingen Hbf", de("Göttingen central station"))
        assertEquals("Erfurt Hbf", de("Erfurt Main Station"))
        // A name that says it already keeps it once.
        assertEquals("Erfurt Hauptbahnhof", de("Erfurt Hauptbahnhof central station"))
    }

    @Test
    fun hamburgCentralBusStationIsZob() {
        assertEquals("Hamburg ZOB", de("Hamburg central bus station"))
        assertEquals("Hamburg ZOB", de("Hamburg Central Bus Station"))
        assertEquals("Hamburg ZOB", de("Hamburg, Central Bus Station"))
        assertEquals("Hamburg ZOB", de("Central Bus Station, Hamburg"))
        assertEquals("Hamburg ZOB", de("Hamburg central bus station (ZOB)"))
        assertEquals("Hamburg ZOB", de("Hamburg ZOB central bus station"))
        assertEquals("Hamburg ZOB", de("Hamburg (central bus station)"))
        // The words alone, the town said apart.
        assertEquals("ZOB", de("central bus station"))
        assertEquals("ZOB", de("Bus Station"))
        assertEquals("Hbf", de("main station"))
        assertEquals("Hbf", de("Central Station"))
        // Another addition in brackets stays.
        assertEquals("Hamburg ZOB (Steig 3)", de("Hamburg central bus station (Steig 3)"))
        assertEquals("Hamburg Central Bus Station", localStopTerms("Hamburg Central Bus Station", "en"))
    }

    @Test
    fun properNamesStay() {
        for (name in listOf("S+U Alexanderplatz", "Stationsweg", "Bahnhofstr.", "U Osloer Str.", "Train Station", "Hauptbahnhof", "Flughafen BER")) {
            assertEquals(name, de(name))
        }
    }

    @Test
    fun anEndingInBrackets() {
        assertEquals("Frankfurt (Main) Hbf", de("Frankfurt (Main) Hbf (main station)"))
        assertEquals("Erfurt Hbf", de("Erfurt (central station)"))
    }

    @Test
    fun airportsOfATownOrACode() {
        assertEquals("Flughafen BER Terminal 1", de("Airport BER Terminal 1"))
        assertEquals("Flughafen BER Terminal 2", de("BER airport terminal 2"))
        assertEquals("Flughafen Leipzig/Halle", de("Leipzig/Halle Airport"))
        // A longer name is a proper name.
        assertEquals("Berlin Brandenburg Airport", de("Berlin Brandenburg Airport"))
        assertEquals("Airport BER Terminal 1", localStopTerms("Airport BER Terminal 1", "en"))
    }

    @Test
    fun twiceIsOnce() {
        for (name in listOf("Göttingen central bus station", "Göttingen train station", "Erfurt Main Station", "Airport BER Terminal 1", "Hamburg bus station", "Central Bus Station, Hamburg", "central bus station", "Hamburg central bus station (Steig 3)")) {
            assertEquals(de(name), de(de(name)))
        }
    }

    @Test
    fun englishStaysEnglish() {
        assertEquals("Göttingen central bus station", localStopTerms("Göttingen central bus station", "en"))
    }

    @Test
    fun theShownNameTakesTheNameAsItCameIn() {
        // Said in German where the provider's answer is read (with the position), not again when shown.
        assertEquals("Göttingen ZOB", stopNameParts("Göttingen ZOB", home = "Berlin", language = "de").title)
        assertEquals("London Victoria Coach Station", stopNameParts("London Victoria Coach Station", home = "Berlin", language = "de").title)
    }

    // Made-up spots: one in Germany, others across the borders.
    private val inGermany = 51.0 to 10.0
    private fun deAt(name: String, at: Pair<Double, Double>) = localStopTerms(name, "de", at.first, at.second)

    @Test
    fun germanyByItsOutline() {
        assertTrue(insideGermany(52.52, 13.40))
        assertTrue(insideGermany(53.55, 9.99))
        assertTrue(insideGermany(48.14, 11.58))
        assertTrue(insideGermany(48.57, 7.82)) // Kehl
        assertTrue(insideGermany(47.59, 7.63)) // Weil am Rhein
        assertFalse(insideGermany(52.37, 4.90)) // Amsterdam
        assertFalse(insideGermany(51.50, -0.14)) // London
        assertFalse(insideGermany(48.58, 7.75)) // Strasbourg
        assertFalse(insideGermany(47.56, 7.59)) // Basel
        assertFalse(insideGermany(47.80, 13.04)) // Salzburg
        assertFalse(insideGermany(50.08, 14.43)) // Prague
        assertFalse(insideGermany(53.43, 14.55)) // Szczecin
        assertFalse(insideGermany(51.37, 6.17)) // Venlo
        assertFalse(insideGermany(55.68, 12.57)) // Copenhagen
    }

    @Test
    fun furtherWordsOnlyInGermany() {
        assertEquals("Flughafen Berlin Brandenburg", deAt("Berlin Brandenburg Airport", inGermany))
        assertEquals("Flughafen Frankfurt Terminal 1", deAt("Frankfurt Airport Terminal 1", inGermany))
        assertEquals("Musterstadt Hbf", deAt("Musterstadt Main Station", inGermany))
        assertEquals("Musterstadt Hbf", deAt("Musterstadt Central Station (Hbf)", inGermany))
        assertEquals("Musterstadt ZOB", deAt("Musterstadt Coach Station", inGermany))
        assertEquals("Musterstadt Bahnhof", deAt("Musterstadt station", inGermany))
        assertEquals("Musterstadt Hafen", deAt("Musterstadt Harbour", inGermany))
        assertEquals("Musterstadt Hafen", deAt("Musterstadt Port", inGermany))
        assertEquals("Musterstadt Messe", deAt("Musterstadt Trade Fair", inGermany))
        assertEquals("Musterstadt Messe", deAt("Musterstadt Exhibition Centre", inGermany))
        assertEquals("Musterstadt Universität", deAt("Musterstadt University", inGermany))
        assertEquals("Musterstadt Altstadt", deAt("Musterstadt Old Town", inGermany))
        assertEquals("Musterstadt Dom", deAt("Musterstadt Cathedral", inGermany))
        assertEquals("Musterstadt Zoo", deAt("Musterstadt Zoo", inGermany))
        // Twice is once.
        for (name in listOf("Berlin Brandenburg Airport", "Musterstadt station", "Musterstadt Harbour", "Musterstadt Old Town")) {
            assertEquals(deAt(name, inGermany), deAt(deAt(name, inGermany), inGermany))
        }
        // Words alone or a longer name in front of "station" stay.
        assertEquals("Train Station", deAt("Train Station", inGermany))
        assertEquals("University", deAt("University", inGermany))
        assertEquals("Musterstadt Nord station", deAt("Musterstadt Nord station", inGermany))
        assertEquals("Musterstadt Harbour", localStopTerms("Musterstadt Harbour", "en", inGermany.first, inGermany.second))
    }

    @Test
    fun nothingIsTranslatedOutsideGermany() {
        assertEquals("Amsterdam Centraal", deAt("Amsterdam Centraal", 52.379 to 4.900))
        assertEquals("London Victoria Coach Station", deAt("London Victoria Coach Station", 51.493 to -0.148))
        assertEquals("Strasbourg Central Station", deAt("Strasbourg Central Station", 48.585 to 7.734))
        assertEquals("Basel Airport", deAt("Basel Airport", 47.59 to 7.53))
        assertEquals("Salzburg Old Town", deAt("Salzburg Old Town", 47.80 to 13.04))
        // Without a position only the first words, as before; the further ones need Germany.
        assertEquals("Musterstadt ZOB", de("Musterstadt coach station"))
        assertEquals("Musterstadt Harbour", de("Musterstadt Harbour"))
        assertEquals("Musterstadt station", de("Musterstadt station"))
    }
}
