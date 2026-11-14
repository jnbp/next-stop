package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Where to board, estimated from the platform the train arrives at and the exit towards the
 * next ride (see [BoardingGeometry.platformEstimate]). Real coordinates from the assets
 * osm-berlin.json (© OpenStreetMap contributors, ODbL 1.0): platforms, entrances and the stop
 * positions of trams and buses as mapped there.
 */
class BoardingPlatformTest {
    // U Osloer Straße: the U9 and the U8 platform, the stop position of the trams 50 and M13 in
    // the street, and the exits the app picks towards it (OsmStations.exitToward).
    private val osloerU9 = (52.55674 to 13.372856) to (52.557615 to 13.373602)
    private val osloerU8 = (52.557509 to 13.372749) to (52.556738 to 13.373768)
    private val osloerStation = 52.55716 to 13.373271
    private val osloerTram = 52.556695 to 13.373262
    private val osloerExitU9 = 52.5568 to 13.372941
    private val osloerExitU8 = 52.556763 to 13.373359

    // The stations next to it, as points behind the arriving train.
    private val nauenerPlatz = 52.551406 to 13.36717
    private val franzNeumannPlatz = 52.564539 to 13.363924
    private val pankstrasse = 52.552262 to 13.381458

    // S Landsberger Allee: the platform of the Ring, the trams M5, M6, M8 and 18 and the bus 156
    // in the street, and the exit towards them; the stations before on either side.
    private val landsbergerRing = (52.530218 to 13.454007) to (52.528898 to 13.455408)
    private val landsbergerTram = 52.528532 to 13.455783
    private val landsbergerBus = 52.529866 to 13.456788
    private val landsbergerExit = 52.52881 to 13.455725
    private val greifswalderStrasse = 52.540246 to 13.438989
    private val storkowerStrasse = 52.523743 to 13.464889

    private fun part(
        platform: Pair<Pair<Double, Double>, Pair<Double, Double>>,
        previous: Pair<Double, Double>,
        exit: Pair<Double, Double>,
        product: String,
    ): Int? = BoardingGeometry.platformEstimate(platform.first, platform.second, previous, exit, product).estimate?.part

    @Test
    fun u9ToTramAtOsloerStrasse() {
        // The U9 from Nauener Platz runs north; the trams stop at the south end: the rear.
        assertEquals(0, part(osloerU9, nauenerPlatz, osloerExitU9, "subway"))
        // Without a known exit, the stop position of the tram itself says the same.
        assertEquals(0, part(osloerU9, nauenerPlatz, osloerTram, "subway"))
        // The ends of the platform as mapped in the other order give the same answer.
        assertEquals(0, part(osloerU9.second to osloerU9.first, nauenerPlatz, osloerExitU9, "subway"))
    }

    @Test
    fun u8ToTramAtOsloerStrasseBothWays() {
        // From Franz-Neumann-Platz the U8 runs south-east, towards the end at the trams.
        assertEquals(2, part(osloerU8, franzNeumannPlatz, osloerExitU8, "subway"))
        // From Pankstraße it runs north-west: the trams are behind.
        assertEquals(0, part(osloerU8, pankstrasse, osloerExitU8, "subway"))
    }

    @Test
    fun sameStationPointGaveNoEstimate() {
        // The cause of the missing hint: the source has one stop for the U-Bahn and the trams
        // in front of it, so the old estimate saw no way from the train to the tram at all.
        assertNull(BoardingGeometry.estimate(nauenerPlatz, osloerStation, osloerStation, "subway").estimate)
        assertNotNull(BoardingGeometry.platformEstimate(osloerU9.first, osloerU9.second, nauenerPlatz, osloerExitU9, "subway").estimate)
    }

    @Test
    fun ringToTramAndBusAtLandsbergerAllee() {
        // The S41 from Greifswalder Straße runs south-east, the trams stop beyond that end.
        assertEquals(2, part(landsbergerRing, greifswalderStrasse, landsbergerExit, "suburban"))
        // The S42 from Storkower Straße runs north-west: the rear.
        assertEquals(0, part(landsbergerRing, storkowerStrasse, landsbergerExit, "suburban"))
        assertEquals(0, part(landsbergerRing, storkowerStrasse, landsbergerTram, "suburban"))
        // The bus 156 stops beside the middle of the station, but its way leaves by the exit at
        // the south-east end: the exit decides, not the stop position alone.
        assertEquals(1, part(landsbergerRing, greifswalderStrasse, landsbergerBus, "suburban"))
        assertEquals(2, part(landsbergerRing, greifswalderStrasse, landsbergerExit, "suburban"))
    }

    @Test
    fun alongIsMeasuredFromTheMiddleOfTheTrain() {
        val estimate = BoardingGeometry.platformEstimate(osloerU9.first, osloerU9.second, nauenerPlatz, osloerExitU9, "subway").estimate
        assertNotNull(estimate)
        estimate!!
        // About 50 m behind the middle of the train, beyond the threshold (30 m).
        assertEquals(true, estimate.along < -estimate.threshold)
        assertEquals(30.0, estimate.threshold, 0.01)
        // Running about north-east.
        assertEquals(true, estimate.bearing in 0.0..90.0)
    }

    @Test
    fun noEstimate() {
        // A direction across the platform tells nothing: from straight east of Osloer Straße.
        val east = BoardingGeometry.moved(osloerStation, 0.0, 400.0)
        assertNull(part(osloerU9, east, osloerExitU9, "subway"))
        // A point behind the train too close to the platform gives no direction.
        assertNull(part(osloerU9, BoardingGeometry.moved(osloerStation, -20.0, 0.0), osloerExitU9, "subway"))
        // An exit far away is not reached from one end of the platform.
        assertNull(part(osloerU9, nauenerPlatz, BoardingGeometry.moved(osloerStation, 0.0, 900.0), "subway"))
        // A platform of a few metres is a mapping error.
        assertNull(part(osloerU9.first to BoardingGeometry.moved(osloerU9.first, 5.0, 0.0), nauenerPlatz, osloerExitU9, "subway"))
    }
}
