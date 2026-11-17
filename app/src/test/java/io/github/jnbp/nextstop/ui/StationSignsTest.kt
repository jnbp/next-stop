package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.Stop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The parts of a big station stand on the map as one sign. */
class StationSignsTest {
    private val rail = setOf("suburban", "subway", "regional", "express")

    // Berlin Hauptbahnhof as the VBB lists it: the station, its deep platforms 150 m south
    // (900003200, "[Gleis 1-8]") and, from the offline data, the bus and tram stop area in the
    // street to the north (150 m from the station's sign: a sign of its own).
    private val hbf = Stop("900003201", "S+U Berlin Hauptbahnhof", 52.525847, 13.368924, 20, rail + setOf("bus", "tram"))
    private val deep = Stop("900003200", "S+U Berlin Hauptbahnhof [Gleis 1-8]", 52.524500, 13.369400, 10, setOf("regional", "express"))
    private val street = Stop("osm:a:900003201:hauptbahnhof", "S+U Hauptbahnhof", 52.527200, 13.369297, null, setOf("bus", "tram"))

    @Test
    fun hauptbahnhofIsOneRailSignAndTheStreetStopStays() {
        // The deep platforms are listed first (nearer to the user): the sign still is the station's.
        val groups = stationGroups(listOf(deep, hbf, street))
        assertEquals(2, groups.size)
        val station = groups[0]
        assertEquals("900003201", station.main.id)
        assertEquals(listOf("900003200"), station.members.map { it.id })
        assertTrue(station.sign.products.containsAll(rail))
        assertEquals(hbf.position, station.sign.position)
        assertEquals(street, groups[1].main)
    }

    @Test
    fun noSecondSignBesideTheMarkedStation() {
        // Marked: the deep platforms (as a search may pick them). The station's other sign goes,
        // the "H" in the street stays.
        val signs = stationSigns(listOf(hbf, street), deep)
        assertEquals(listOf(street.id), signs.map { it.id })
        // Marked: the station, its deep platforms in the list.
        assertEquals(listOf(street.id), stationSigns(listOf(deep, street), hbf).map { it.id })
        // Nothing marked: one rail sign and the "H".
        assertEquals(listOf(hbf.id, street.id), stationSigns(listOf(hbf, deep, street), null).map { it.id })
    }

    @Test
    fun partsByNameIdAndComplex() {
        val alex = Stop("900100003", "S+U Alexanderplatz Bhf (Berlin)", 52.5219, 13.4115, null, setOf("suburban", "subway", "regional"))
        val alexU2 = Stop("900100703", "S+U Alexanderplatz Bhf (Berlin) [U2]", 52.5225, 13.4136, null, setOf("subway"))
        val alexU8 = Stop("900100705", "S+U Alexanderplatz Bhf (Berlin) [U8]", 52.5212, 13.4127, null, setOf("subway"))
        assertEquals(1, stationGroups(listOf(alexU2, alex, alexU8)).size)
        assertEquals("900100003", stationGroups(listOf(alexU2, alex, alexU8)).single().main.id)

        // München Hbf and its deep S-Bahn station, by DHID and by name.
        val munich = Stop("de-DELFI_de:09162:100:1:1", "München Hbf", 48.1402, 11.5600, null, setOf("regional", "express"))
        val munichDeep = Stop("de:09162:100:7:9", "München Hbf (tief)", 48.1410, 11.5585, null, setOf("suburban"))
        assertEquals(1, stationGroups(listOf(munich, munichDeep)).size)
        val frankfurt = Stop("x1", "Frankfurt (Main) Hbf", 50.1070, 8.6630, null, setOf("express"))
        val frankfurtDeep = Stop("x2", "Frankfurt (Main) Hbf (tief)", 50.1066, 8.6640, null, setOf("suburban"))
        assertEquals(1, stationGroups(listOf(frankfurt, frankfurtDeep)).size)

        // Hamburg Hbf and the U-Bahn station "Hauptbahnhof Süd": names apart, one complex of OpenStreetMap.
        val hamburg = Stop("de:02000:10950", "Hamburg Hbf", 53.5530, 10.0067, null, setOf("suburban", "regional", "express"))
        val sued = Stop("de:02000:10951", "Hauptbahnhof Süd", 53.5524, 10.0080, null, setOf("subway"))
        assertEquals(2, stationGroups(listOf(hamburg, sued)).size)
        assertEquals(1, stationGroups(listOf(hamburg, sued)) { "53.5528,10.0067" }.size)
    }

    @Test
    fun offlineStationsOfOneComplex() {
        val tunnel = Stop("osm:s:52.526621,13.369585", "Berlin Hauptbahnhof Tunnel (S-Bahn)", 52.526621, 13.369585, null, setOf("suburban"))
        val main = Stop("osm:s:900003201", "Hauptbahnhof", 52.525015, 13.369745, null, setOf("suburban", "subway", "regional"))
        val groups = stationGroups(listOf(tunnel, main))
        assertEquals(1, groups.size)
        assertEquals(main.id, groups.single().main.id)
    }

    @Test
    fun otherStationsStayApart() {
        // Another station of a name that starts the same.
        val herne = Stop("a", "Herne", 51.5440, 7.2190, null, setOf("regional"))
        val herneMitte = Stop("b", "Herne Mitte", 51.5470, 7.2200, null, setOf("subway"))
        assertEquals(2, stationGroups(listOf(herne, herneMitte)).size)
        // One name, too far apart.
        val near = Stop("c", "S Bahnhofstr.", 52.50, 13.40, null, setOf("suburban"))
        val far = Stop("d", "S Bahnhofstr.", 52.51, 13.40, null, setOf("suburban"))
        assertEquals(2, stationGroups(listOf(near, far)).size)
        // Bus stops of one name far apart: each "H" has its own street.
        val busA = Stop("e", "S+U Hauptbahnhof", 52.5265, 13.3693, null, setOf("bus"))
        val busB = Stop("f", "S+U Hauptbahnhof", 52.5233, 13.3702, null, setOf("bus"))
        assertEquals(2, stationGroups(listOf(busA, busB)).size)
        // OpenStreetMap stations of one id (a tagging slip) but no word in common stay apart.
        val hbfSued = Stop("osm:s:de:02000:10950", "Hauptbahnhof Süd", 53.5524, 10.0080, null, setOf("subway"))
        val stein = Stop("osm:s:de:02000:10950", "Steinstraße", 53.5500, 10.0040, null, setOf("subway"))
        assertEquals(2, stationGroups(listOf(hbfSued, stein)).size)
    }

    @Test
    fun baseKeys() {
        assertEquals("hbf", stationBaseKey("S+U Berlin Hauptbahnhof [Gleis 1-8]"))
        assertEquals("hbf", stationBaseKey("S+U Berlin Hauptbahnhof"))
        assertEquals("alexanderplatz", stationBaseKey("S+U Alexanderplatz Bhf (Berlin) [U2]"))
        assertEquals("münchenhbf", stationBaseKey("München Hbf Gleis 27-36, Starnberger Bahnhof"))
        assertEquals("münchenhbf", stationBaseKey("München Hauptbahnhof"))
        assertEquals("münchenhbf", stationBaseKey("München Hbf (tief)"))
        assertEquals("gleisdreieck", stationBaseKey("U Gleisdreieck (Berlin)"))
        assertEquals("ostkreuz", stationBaseKey("S Ostkreuz Bhf (Berlin)"))
        assertTrue(baseKeysFit("hbf", "hbftunnel"))
        assertFalse(baseKeysFit("herne", "hernemitte"))
    }

    @Test
    fun vehiclesOfAStationSignAreItsTrains() {
        assertEquals(setOf("suburban", "subway"), anchorProducts(setOf("suburban", "subway", "bus", "tram")))
        assertEquals(setOf("bus", "tram"), anchorProducts(setOf("bus", "tram")))
        assertEquals(setOf("bus", "tram"), anchorProducts(emptySet()))
    }

    // The bus stop of Alexanderplatz in Karl-Liebknecht-Straße, 230 m from the station's sign:
    // a sign ("H") of its own, whatever products the provider lists for it.
    private val alexStation = Stop("900100003", "S+U Alexanderplatz (Bln)", 52.521536, 13.412089, null, setOf("suburban", "subway", "regional", "bus", "tram"))
    private val alexBus = Stop("900100031", "S+U Alexanderplatz (Bln) [Bus K.-Liebknecht-Str]", 52.521817, 13.408531, null, setOf("bus"))

    @Test
    fun streetStopOfABigStationKeepsItsSign() {
        assertEquals(2, stationGroups(listOf(alexStation, alexBus)).size)
        // Even where the provider lists the station's trains for it.
        val listedWithTrains = alexBus.copy(products = setOf("bus", "subway"))
        assertEquals(2, stationGroups(listOf(alexStation, listedWithTrains)).size)
        // The rail parts of the station still join it ("[Gleis 1-8]" type).
        assertEquals(listOf("900003201"), stationGroups(listOf(deep, hbf)).map { it.main.id })
    }

    @Test
    fun offlineStreetStopFarFromTheStationSignStays() {
        // The stop area of OpenStreetMap for that bus stop, the provider listing only the station.
        val osmBus = Stop("osm:a:900100031:alexanderplatzmemhardstr", "S+U Alexanderplatz/Memhardstraße", 52.521817, 13.408531, null, setOf("bus"))
        assertEquals(listOf(alexStation.id, osmBus.id), mergeMapStops(listOf(alexStation), listOf(osmBus), null).map { it.id })
        // The trams of Dircksenstraße, 20 m from the station's sign: the station's sign stands for them.
        val osmTram = Stop("osm:a:900100024:alexanderplatzdircksenstr", "S+U Alexanderplatz/Dircksenstraße", 52.521468, 13.412011 + 0.0002, null, setOf("tram"))
        assertEquals(listOf(alexStation.id), mergeMapStops(listOf(alexStation), listOf(osmTram), null).map { it.id })
        // The provider's own stop of that stop area stands for it, by its number.
        assertEquals(listOf(alexStation.id, alexBus.id), mergeMapStops(listOf(alexStation, alexBus), listOf(osmBus.copy(latitude = 52.5223)), null).map { it.id })
    }

    @Test
    fun stopPlaceOfAnOfflineStop() {
        assertTrue(sameStopPlace("osm:a:900100031:alexanderplatzmemhardstr", "900100031"))
        assertTrue(sameStopPlace("osm:a:de:11000:900100031:x", "de:11000:900100031::3"))
        assertFalse(sameStopPlace("osm:a:900100024:x", "900100031"))
        assertFalse(sameStopPlace("osm:a:52.5,13.4:x", "900100031"))
    }

    @Test
    fun streetStopRightAtTheStationJoinsItsSign() {
        // The bus and tram stop area of Hauptbahnhof 85 m from the station's sign, of its number.
        val close = street.copy(latitude = 52.526564)
        val groups = stationGroups(listOf(hbf, close))
        assertEquals(1, groups.size)
        assertEquals(hbf.id, groups.single().main.id)
        assertEquals(listOf(close.id), groups.single().members.map { it.id })
        assertTrue(groups.single().sign.products.containsAll(setOf("bus", "tram")))
    }

    @Test
    fun nightBusStopOfAUBahnStationIsNoSecondSign() {
        // The provider lists the U-Bahn station and the stop of the night buses in front of it
        // under two numbers, 60 m apart: one sign.
        val station = Stop("900100023", "U Rosenthaler Platz (Berlin)", 52.529700, 13.401400, null, setOf("subway"))
        val nightBus = Stop("900100523", "U Rosenthaler Platz (Berlin)", 52.530200, 13.401700, null, setOf("bus"))
        val groups = stationGroups(listOf(nightBus, station))
        assertEquals(1, groups.size)
        assertEquals(station.id, groups.single().main.id)
        assertEquals(setOf("subway", "bus"), groups.single().sign.products)
        // The same from the offline data: the stop area beside the station's sign is dropped
        // already, and what is left is one sign.
        assertEquals(1, stationSigns(mergeMapStops(listOf(station), listOf(nightBus.copy(id = "osm:a:900100523:rosenthalerplatz")), null), null).size)
    }

    @Test
    fun tramAndBusStopOfOneSquareAreOneSign() {
        // Two stop areas of one name, 145 m apart, with numbers of their own.
        val tram = Stop("900120816", "Bersarinplatz (Berlin)", 52.518943, 13.454404, null, setOf("tram"))
        val other = Stop("900120516", "Bersarinplatz (Berlin)", 52.519417, 13.452368, null, setOf("tram", "bus"))
        val groups = stationGroups(listOf(tram, other))
        assertEquals(1, groups.size)
        assertEquals(tram.id, groups.single().main.id)
        assertEquals(listOf(other.id), groups.single().members.map { it.id })
        // Not on from stop to stop: a third one of the name 290 m from the first stays apart.
        val third = Stop("x3", "Bersarinplatz (Berlin)", 52.519417, 13.450200, null, setOf("bus"))
        assertEquals(2, stationGroups(listOf(tram, other, third)).size)
    }

    @Test
    fun longDistanceAndLocalStopOfOneStationAreOneSign() {
        val local = Stop("900058101", "S Südkreuz Bhf (Berlin)", 52.475465, 13.365575, null, setOf("suburban", "bus"))
        // Another provider's stop of the station, under the DB number and another name.
        val far = Stop("8011113", "Berlin Südkreuz", 52.475800, 13.365100, null, setOf("regional", "express"))
        assertEquals(1, stationGroups(listOf(local, far)).size)
        // A provider that names no products for it.
        val bare = Stop("de-DELFI_de:11000:900058101:3:51", "Berlin Südkreuz", 52.476100, 13.365600, null)
        val groups = stationGroups(listOf(far, bare, local))
        assertEquals(1, groups.size)
        assertEquals(setOf("suburban", "bus", "regional", "express"), groups.single().sign.products)
    }

    @Test
    fun busStationInFrontOfAMainStationJoinsIt() {
        // The station and, from the offline data, the stop areas in front of it: its bus
        // station under a number of its own, the long-distance buses and the coach stop.
        val station = Stop("de-DELFI_de:03159:33817:1:1", "Göttingen", 51.536783, 9.925897, null, setOf("regional", "express"))
        val buses = Stop("osm:a:de:03152:33853:bahnhof", "Bahnhof", 51.535013, 9.927103, null, setOf("bus"))
        val coaches = Stop("osm:a::göttingenfernbusse", "Göttingen Fernbusse", 51.534898, 9.926553, null, setOf("bus"))
        val tourist = Stop("osm:a::zob", "ZOB (Touristenbusse)", 51.536019, 9.927593, null, setOf("bus"))
        // A stop of another name 270 m off: a stop of its own.
        val court = Stop("osm:a::landgerichtbahnhof", "Landgericht/Bahnhof", 51.537850, 9.929463, null, setOf("bus"))
        val groups = stationGroups(listOf(buses, coaches, station, tourist, court))
        assertEquals(2, groups.size)
        assertEquals(station.id, groups[0].main.id)
        assertEquals(setOf(buses.id, coaches.id, tourist.id), groups[0].members.map { it.id }.toSet())
        assertEquals(court.id, groups[1].main.id)
        assertTrue(isForecourtOf("göttingenfernbusse", "göttingen"))
        assertFalse(isForecourtOf("landgerichtbahnhof", "göttingen"))
    }
}
