package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LongDistanceStationsTest {
    private val region = 52.52 to 13.40

    private fun station(id: String, name: String, at: Pair<Double, Double>, vararg products: String) =
        Stop(id = id, name = name, latitude = at.first, longitude = at.second, distanceMeters = null, products = products.toSet())

    private val hbf = station("t:1", "Hamburg Hbf", 53.553 to 10.006, "express", "regional", "suburban")
    private val altona = station("t:2", "Hamburg-Altona", 53.552 to 9.935, "express", "regional")
    private val harburg = station("t:3", "Hamburg-Harburg", 53.456 to 9.991, "express", "regional")
    private val bergedorf = station("t:4", "Hamburg-Bergedorf", 53.490 to 10.206, "regional")
    private val localStreet = station("v:1", "Hamburger Str.", 52.53 to 13.41, "bus")
    private val localStation = station("v:2", "S Hamburger Bahnhof", 52.528 to 13.372, "suburban")

    @Test
    fun stationsOfTheTownRankLongDistanceAndMainStationFirst() {
        val found = longDistanceStations("Hamburg", listOf(bergedorf, altona, localStreet, hbf, localStation, harburg), emptyList(), region)
        assertEquals(listOf("Hamburg Hbf", "Hamburg-Altona", "Hamburg-Harburg", "Hamburg-Bergedorf"), found.map { it.name })
    }

    @Test
    fun localHitsAndTwinsAreLeftOut() {
        val twin = station("v:9", "Hamburg Hbf", 53.5531 to 10.0061, "express")
        val found = longDistanceStations("hamburg", listOf(hbf, twin, altona), local = listOf(altona), region = region)
        assertEquals(listOf("Hamburg Hbf"), found.map { it.name })
    }

    @Test
    fun stationsInTheRegionOrWithoutTrainsAreNoBlock() {
        val near = station("v:3", "Hamburg Testbahnhof", 52.50 to 13.30, "regional")
        val busOnly = station("t:5", "Hamburg Musterplatz", 53.56 to 10.0, "bus")
        assertEquals(emptyList<Stop>(), longDistanceStations("Hamburg", listOf(near, busOnly, localStreet), emptyList(), region))
    }

    @Test
    fun theTownMustBeAWholeWord() {
        val other = station("t:6", "Hamburgerstadt Bf", 50.0 to 9.0, "regional")
        assertEquals(emptyList<Stop>(), longDistanceStations("Hamburg", listOf(other), emptyList(), region))
    }

    @Test
    fun aFarStationAmongTheLocalHitsMovesIntoTheBlock() {
        val testHbf = station("t:7", "Teststadt Hbf", 48.78 to 9.18, "express", "regional")
        val localStop = station("v:7", "Teststadter Str.", 52.51 to 13.39, "bus")
        val town = Stop("o:1", "Teststadt", 48.77, 9.17, null, kind = PlaceKind.Poi, detail = "Teststadt")
        val blocks = longDistanceBlocks("Teststadt", listOf(localStop, town, testHbf), far = emptyList(), region = region)
        assertEquals(listOf("Teststadter Str.", "Teststadt"), blocks.local.map { it.name })
        assertEquals(listOf("Teststadt Hbf"), blocks.far.map { it.name })
        // Also found without the bias: once, in the block.
        val twice = longDistanceBlocks("Teststadt", listOf(localStop, testHbf), far = listOf(testHbf.copy(id = "x:7")), region = region)
        assertEquals(listOf("Teststadt Hbf"), twice.far.map { it.name })
        assertEquals(listOf("Teststadter Str."), twice.local.map { it.name })
    }

    @Test
    fun historyStaysAndANearStationStaysLocal() {
        val testHbf = station("t:7", "Teststadt Hbf", 48.78 to 9.18, "express")
        val fixed = longDistanceBlocks("Teststadt", listOf(testHbf), emptyList(), region, fixed = setOf("t:7"))
        assertEquals(listOf("Teststadt Hbf"), fixed.local.map { it.name })
        assertEquals(emptyList<Stop>(), fixed.far)
        val nearHbf = station("v:8", "Teststadt Hbf", 52.5 to 13.4, "express")
        assertEquals(emptyList<Stop>(), longDistanceBlocks("Teststadt", listOf(nearHbf), emptyList(), region).far)
        // Without a known region nothing moves.
        assertEquals(listOf(testHbf), longDistanceBlocks("Teststadt", listOf(testHbf), emptyList(), null).local)
    }

    @Test
    fun aTownFromTwoSourcesShowsOnce() {
        val one = Stop("o:1", "Teststadt", 48.77, 9.17, null, kind = PlaceKind.Poi, detail = "Teststadt")
        val two = Stop("p:1", "Teststadt", 48.78, 9.18, null, kind = PlaceKind.Address, areas = listOf("Teststadt", "Land"))
        val street = Stop("p:2", "Hauptweg", 48.78, 9.18, null, kind = PlaceKind.Address, detail = "Teststadt")
        val street2 = Stop("p:3", "Hauptweg", 48.70, 9.10, null, kind = PlaceKind.Address, detail = "Teststadt")
        assertEquals(listOf(one, street, street2), withoutTwinTowns(listOf(one, two, street, street2)))
    }
}
