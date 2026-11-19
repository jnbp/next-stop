package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** #96 in the place search and on a board opened from it: the SNCF's "Francfort sur le Main" is Frankfurt Hbf. */
class ForeignTwinsTest {
    private val german = Stop(
        "de-DELFI_de:06412:10", "Frankfurt (Main) Hauptbahnhof", 50.10710, 8.66330, null,
        setOf("suburban", "subway", "regional", "express"),
    )
    private val french = Stop("fr-sncf_StopArea:OCE80110007", "Francfort sur le Main", 50.10670, 8.66210, null, setOf("express"))
    private val tram = Stop("de:06412:11", "Frankfurt (Main) Hauptbahnhof/Münchener Straße", 50.10740, 8.66500, null, setOf("tram"))

    @Test
    fun theSearchShowsTheLocalName() {
        // The French one came first: the German one takes its place.
        assertTrue(localStationFirst(french, german))
        assertFalse(localStationFirst(german, french))
        assertFalse(localStationFirst(german, tram))
    }

    @Test
    fun aBoardFindsTheTwinAround() {
        assertEquals(listOf(french.id), foreignTwinsOf(german, listOf(german, tram, french)).map { it.id })
        assertEquals(listOf(german.id), foreignTwinsOf(french, listOf(german, tram)).map { it.id })
    }
}
