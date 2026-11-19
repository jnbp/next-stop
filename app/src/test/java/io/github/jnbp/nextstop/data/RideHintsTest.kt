package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** How full a ride is expected to be, and the hints shown as icons. */
class RideHintsTest {
    @Test
    fun loadFactors() {
        assertEquals(Occupancy.Low, Occupancy.fromLoadFactor("low-to-medium"))
        assertEquals(Occupancy.Medium, Occupancy.fromLoadFactor("high"))
        assertEquals(Occupancy.High, Occupancy.fromLoadFactor("very-high"))
        assertEquals(Occupancy.Full, Occupancy.fromLoadFactor("exceptionally-high"))
        assertNull(Occupancy.fromLoadFactor(null))
        assertNull(Occupancy.fromLoadFactor("unknown"))
        assertEquals(3, Occupancy.Full.segments)
    }

    @Test
    fun featuresByCodeAndWords() {
        assertEquals(setOf(RideFeature.BikeCarriage), rideFeatures(listOf("FB" to "Fahrradmitnahme begrenzt möglich")))
        assertEquals(setOf(RideFeature.StepFree), rideFeatures(listOf("bf" to null)))
        assertEquals(
            setOf(RideFeature.BikeCarriage, RideFeature.StepFree),
            rideFeatures(listOf(null to "Bicycle conveyance", "RG" to "Behindertengerechtes Fahrzeug")),
        )
    }

    @Test
    fun negationsAndNothing() {
        assertEquals(emptySet<RideFeature>(), rideFeatures(listOf(null to "Fahrradmitnahme nicht möglich")))
        assertEquals(emptySet<RideFeature>(), rideFeatures(emptyList()))
        assertEquals(emptySet<RideFeature>(), rideFeatures(listOf("XY" to "Komfort-Check-in möglich")))
    }

    @Test
    fun theBikeChipShowsTheProvidersOwnWords() {
        // #248: the bicycle hint among others, by its HAFAS code or by its words.
        assertEquals(
            "Fahrradmitnahme begrenzt möglich",
            bikeRemarkText(listOf("bf" to "barrierefrei", "FB" to "Fahrradmitnahme begrenzt möglich", "WV" to "WLAN verfügbar")),
        )
        assertEquals("Bicycles allowed (limited space)", bikeRemarkText(listOf(null to "Bicycles allowed (limited space)")))
        assertEquals("mit Reservierung", bikeRemarkText(listOf("FR" to "  mit Reservierung  ")))
    }

    @Test
    fun aHintThatAllowsBikesComesBeforeOneThatDoesNot() {
        assertEquals(
            "Fahrradmitnahme möglich",
            bikeRemarkText(listOf(null to "Fahrradmitnahme nicht möglich", "FK" to "Fahrradmitnahme möglich")),
        )
        // Only the opposite: still the provider's words.
        assertEquals("Fahrradmitnahme nicht möglich", bikeRemarkText(listOf(null to "Fahrradmitnahme nicht möglich")))
    }

    @Test
    fun withoutAWordedBikeHintTheGenericTextStandsAlone() {
        assertNull(bikeRemarkText(emptyList()))
        assertNull(bikeRemarkText(listOf("FB" to null, "FK" to "  ")))
        assertNull(bikeRemarkText(listOf("bf" to "barrierefrei", "WV" to "WLAN verfügbar")))
    }

    @Test
    fun theVbbApisAttributeNotesGiveTheBikeChip() {
        // #264: the official VBB API sends bicycles as an attribute note ("A"), not a HAFAS ReST remark.
        val notes = listOf(
            Triple("A", "FB", "Fahrradmitnahme begrenzt möglich"),
            Triple("A", "EH", "Fahrzeuggebundene Einstiegshilfe vorhanden"),
            Triple("R", null, "Bauarbeiten: Fahrrad nicht barrierefrei"),
        )
        val hints = vbbNoteHints(notes)
        assertEquals(listOf("FB" to "Fahrradmitnahme begrenzt möglich", "EH" to "Fahrzeuggebundene Einstiegshilfe vorhanden"), hints)
        assertEquals(setOf(RideFeature.BikeCarriage, RideFeature.StepFree), rideFeatures(hints))
        assertEquals("Fahrradmitnahme begrenzt möglich", bikeRemarkText(hints))
    }

    @Test
    fun aVbbNoteWithoutTypeCountsAsAnAttribute() {
        assertEquals(setOf(RideFeature.BikeCarriage), rideFeatures(vbbNoteHints(listOf(Triple(null, "FK", null)))))
        assertEquals(emptyList<Pair<String?, String?>>(), vbbNoteHints(listOf(Triple("M", "FB", "Fahrrad"))))
    }
}
