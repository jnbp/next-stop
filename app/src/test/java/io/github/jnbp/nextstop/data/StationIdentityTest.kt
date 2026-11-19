package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The station-identity layer (docs/station-identity.md) against real answers of Transitous
 * (RealTransitousData.kt, 18 Nov 2026): one station from several feeds is one, close but
 * different stations stay two, the same train from two feeds is one departure.
 */
class StationIdentityTest {
    // A stop as the app keeps it: Transitous ids with their mark, the modes as products.
    private fun real(id: String): Stop {
        val raw = REAL_STOPS.first { it.id == id }
        return Stop("tr:" + raw.id, raw.name, raw.lat, raw.lon, null, raw.modes.mapNotNull { product(it) }.toSet())
    }

    private fun product(mode: String?): String? = when (mode) {
        "HIGHSPEED_RAIL", "LONG_DISTANCE", "NIGHT_RAIL" -> "express"
        "REGIONAL_RAIL", "REGIONAL_FAST_RAIL", "RAIL" -> "regional"
        "SUBURBAN" -> "suburban"
        "SUBWAY", "METRO" -> "subway"
        "TRAM" -> "tram"
        "BUS" -> "bus"
        "COACH" -> "coach"
        else -> null
    }

    private fun evidence(a: String, b: String) = StationIdentity.evidence(real(a), real(b))

    // The real stops used below.
    private val fraHbfRail = "at-Railway-Current-Reference-Data-2026_de:06412:10:16:16"
    private val fraHbfTief = "de-DELFI_de:06412:7010"
    private val francfort = "fr-horaires-sncf_FR::LMO:71ba90a0-3a69-11e9-8417-bb1d8a705241:"
    private val fraSued = "de-DELFI_de:06412:912_G"
    private val fraTram = "de-DELFI_de:06412:8:2:2"
    private val fraAirport = "de-DELFI_de:06412:11511"
    private val fraAirportAt = "at-Railway-Current-Reference-Data-2026_de:06412:11511:1:1"
    private val fraAirportNs = "nl-OpenOV_2992809"
    private val fraAirportRegional = "de-DELFI_de:06412:2930"
    private val muenchenHbf = "at-Railway-Current-Reference-Data-2026_de:09162:100:11:11"
    private val munich = "fr-horaires-sncf_FR::LMO:719d92c0-3a69-11e9-8417-bb1d8a705241:"
    private val karlsplatz = "de-DELFI_de:09162:1_G"
    private val saarbrueckenHbf = "de-DELFI_de:10041:8000323_G_G"
    private val sarrebruck = "fr-horaires-sncf_FR::LMU:71d62ef0-3a69-11e9-8417-bb1d8a705241:"
    private val saarbrueckenNord = "de-DELFI_de:10041:10647::1064701"
    private val baselSbb = "ch-opentransportdataswiss26_Parentch:1:sloid:10"
    private val baselSbbAmarillo = "de-amarillo-bw_ch:1:sloid:10"
    private val baselBad = "de-DELFI_ch:1:sloid:90"
    private val aachenSncb = "be-sncb_8015345"
    private val aachenDelfi = "de-DELFI_de:05334:1008"
    private val strasbourgDelfi = "de-DELFI_fr:24067:1284"
    private val strasbourgSncf = "fr-horaires-sncf_FR::LMU:6a30e1a0-cb0a-11e8-8bfa-f784c1c7c611:"
    private val koelnHbf = "at-Railway-Current-Reference-Data-2027_de:05315:11201:7:72"
    private val koelnEurostar = "fr-eurostar-gtfs-plan-de-transport-et-temps-reel_koln_hbf"
    private val koelnDeutz = "at-Railway-Current-Reference-Data-2026_de:05315:11901:7:81"
    private val salzburgParent = "at-Railway-Current-Reference-Data-2026_Pat:45:50002"
    private val salzburgPlatform = "at-PTA-Salzburg-Flex-2026_at:45:50002:0:20"
    private val berlinVbb = "de-VBB_de:11000:900003201:3:55"
    private val berlinAt = "at-Railway-Current-Reference-Data-2026_de:11000:900003200:2:52"

    @Test
    fun feedsAndHardIdsAsTheServerWritesThem() {
        assertEquals("de-DELFI", StationIdentity.feedOf("tr:de-DELFI_de:06412:10_G"))
        assertEquals("fr-horaires-sncf", StationIdentity.feedOf("tr:$francfort"))
        assertEquals("at-Railway-Current-Reference-Data-2026", StationIdentity.feedOf(salzburgParent))
        assertNull(StationIdentity.feedOf("900003201"))
        assertEquals("ifopt:de:06412:10", StationIdentity.stationKey(real(fraHbfRail)))
        assertEquals("ifopt:at:45:50002", StationIdentity.stationKey(real(salzburgParent)))
        assertEquals("ifopt:ch:1:sloid:10", StationIdentity.stationKey(real(baselSbb)))
        assertEquals("ifopt:de:10041:8000323", StationIdentity.stationKey(real(saarbrueckenHbf)))
        assertEquals("uic:8015345", StationIdentity.stationKey(real(aachenSncb)))
        // The SNCF's ids hold no code of the station: no key, the stop's own id stays.
        assertNull(StationIdentity.stationKey(real(francfort)))
        // Seven digits of a feed that are no UIC code (country 29) are no key either.
        assertNull(StationIdentity.stationKey(real(fraAirportNs)))
    }

    @Test
    fun oneStationOfManyFeeds() {
        // Frankfurt (Main) Hbf: the SNCF's French name 117 m away, the S-Bahn station "tief" below.
        assertEquals(StationEvidence.Exonym, evidence(fraHbfRail, francfort))
        assertEquals(StationEvidence.Name, evidence(fraHbfRail, fraHbfTief))
        assertTrue(StationIdentity.same(real(francfort), real(fraHbfTief)))
        // Frankfurt Flughafen Fernbahnhof: the same DHID in two feeds, NS's abbreviation.
        assertEquals(StationEvidence.HardId, evidence(fraAirport, fraAirportAt))
        assertEquals(StationEvidence.Name, evidence(fraAirport, fraAirportNs))
        assertEquals(StationEvidence.Exonym, evidence(muenchenHbf, munich))
        // "Hauptbahnhof, Saarbrücken" (town behind) and the SNCF's "Sarrebruck".
        assertEquals(StationEvidence.Exonym, evidence(saarbrueckenHbf, sarrebruck))
        assertEquals(StationEvidence.HardId, evidence(baselSbb, baselSbbAmarillo))
        assertEquals(StationEvidence.Name, evidence(aachenSncb, aachenDelfi))
        assertEquals(StationEvidence.Name, evidence(strasbourgDelfi, strasbourgSncf))
        assertEquals(StationEvidence.Name, evidence(koelnHbf, koelnEurostar))
        assertEquals(StationEvidence.HardId, evidence(salzburgParent, salzburgPlatform))
        assertEquals(StationEvidence.Name, evidence(berlinVbb, berlinAt))
    }

    @Test
    fun brusselsAmsterdamAndViennaAsRecorded() {
        // Brussel-Zuid / Bruxelles-Midi in four feeds: the SNCB's "S8814001", the European
        // Sleeper's "8814001" (one UIC code), NS's Dutch name 138 m away, DELFI's padded code.
        assertEquals(StationEvidence.HardId, evidence("be-sncb_S8814001", "eu-european-sleeper_8814001"))
        assertEquals(StationEvidence.Exonym, evidence("be-sncb_S8814001", "nl-OpenOV_stoparea:482359"))
        assertEquals("uic:8400058", StationIdentity.stationKey(real("de-DELFI_000008400058")))
        assertEquals(StationEvidence.Name, evidence("de-DELFI_000008400058", "nl-OpenOV_2992194"))
        assertNull(evidence("be-sncb_S8814001", "be-sncb_S8813003"))
        assertNull(evidence("nl-OpenOV_stoparea:482359", "be-sncb_S8813003"))
        assertNull(evidence("be-sncb_S8814001", "be-sncb_S8812005"))
        // Bus stops named after the station are stops of the street, not the rail station.
        assertNull(evidence("be-sncb_S8814001", "be-delijn_gs:delijn:307250"))
        assertNull(evidence("de-DELFI_000008400058", "eu-flixbus_dcbc54e2-9603-11e6-9066-549f350fcb0c"))
        // Wien Hbf: the tram stop "Ost" and the car-train terminal 1.4 km off are not it.
        assertNull(evidence("at-Railway-Current-Reference-Data-2026_Pat:49:1349", "at-PTA-Eastern-Region-Flex-2026_Pat:49:905"))
        assertNull(evidence("at-Railway-Current-Reference-Data-2026_Pat:49:1349", "at-Railway-Current-Reference-Data-2026_Pat:49:1350"))
        // Berlin: Friedrichstraße (DELFI) is not Berlin Hbf; the bus stop at Washingtonplatz is no rail stop.
        assertNull(evidence("de-DELFI_de:11000:900100001", berlinAt))
        assertNull(evidence(berlinAt, "de-VBB_de:11000:900003205::4"))
    }

    @Test
    fun berlinStopsOfVbbDelfiAndOthersAreNeverConfused() {
        // One station: VBB and DELFI by the VBB number, at-Railway's parent "Pde:", Polish Trains by name.
        assertEquals(StationEvidence.HardId, evidence("de-DELFI_de:11000:900100001", "de-VBB_de:11000:900100001:3:55"))
        assertEquals(StationEvidence.HardId, evidence("at-Railway-Current-Reference-Data-2026_de:11000:900029101:2:52", "de-DELFI_de:11000:900029101:3:55"))
        assertEquals("ifopt:de:11000:900058101", StationIdentity.stationKey(real("at-Railway-Current-Reference-Data-2026_Pde:11000:900058101")))
        assertEquals(StationEvidence.Name, evidence("de-DELFI_de:11000:900120003", "pl-Polish-Trains_140168"))
        assertEquals(StationEvidence.Name, evidence("de-DELFI_de:11000:900023201", "pl-Polish-Trains_140106"))
        // Never one: the stops around a station and the stations of a district.
        assertNull(evidence("at-Railway-Current-Reference-Data-2026_de:11000:900029101:2:52", "de-VBB_de:11000:900029302"))
        assertNull(evidence("de-DELFI_de:11000:900100003", "de-VBB_de:11000:900100024::1"))
        assertNull(evidence("de-DELFI_de:11000:900100003", "de-VBB_de:11000:900100005::1"))
        assertNull(evidence("de-DELFI_de:11000:900100003", "eu-flixbus_dcbc32c3-9603-11e6-9066-549f350fcb0c"))
        assertNull(evidence("at-Railway-Current-Reference-Data-2026_Pde:11000:900058101", "de-VBB_de:11000:900058100::1"))
        assertNull(evidence("at-Railway-Current-Reference-Data-2026_Pde:11000:900058101", "eu-flixbus_dcbb994f-9603-11e6-9066-549f350fcb0c"))
        assertNull(evidence("de-DELFI_de:11000:900100001", "de-DELFI_de:11000:900100003"))
        // A bus stop of a ride-sharing feed named after the station and its street.
        assertNull(evidence("de-amarillo-bw_de:06412:60767", "de-DELFI_de:06412:10:6:31"))
        // The platforms of one VBB station and the change between them stay one stop place.
        assertTrue(StationIdentity.sameStopPlace(real("de-VBB_de:11000:900100001:3:55"), real("de-VBB_de:11000:900100001::1")))
        assertFalse(StationIdentity.sameStopPlace(real("de-VBB_de:11000:900100001:3:55"), real("de-DELFI_de:11000:900100003")))
    }

    @Test
    fun pragueCopenhagenLeipzigByCodesAndNames() {
        // Praha hl.n.: the IFOPT "cz:0:5457076" holds the UIC code that the SBB writes as "Parent5457076".
        assertEquals(StationEvidence.HardId, evidence("at-Railway-Current-Reference-Data-2026_cz:0:5457076:0:1", "ch-opentransportdataswiss27_Parent5457076"))
        // Rejseplanen's copy lies 2.5 km off: the same code still joins it.
        assertEquals(StationEvidence.HardId, evidence("ch-opentransportdataswiss27_Parent5457076", "dk-rejseplanen_000005457076"))
        assertEquals(StationEvidence.Name, evidence("de-DELFI_000008601309", "nl-OpenOV_4177268"))
        assertEquals(StationEvidence.Name, evidence("at-Railway-Current-Reference-Data-2026_de:14713:8010205:4:13", "nl-OpenOV_4177320"))
        assertNull(evidence("de-DELFI_000008601309", "dk-rejseplanen_000008698626"))
        assertNull(evidence("at-Railway-Current-Reference-Data-2026_de:14612:28:10:Gleis3", "de-DELFI_de:14612:32:1:1"))
    }

    @Test
    fun closeButDifferentStationsStayApart() {
        assertNull(evidence(koelnHbf, koelnDeutz))
        assertNull(evidence(muenchenHbf, karlsplatz))
        assertNull(evidence(munich, karlsplatz))
        assertNull(evidence(baselSbb, baselBad))
        assertNull(evidence(fraHbfRail, fraSued))
        assertNull(evidence(francfort, fraSued))
        // The tram stop in front of the station is a stop of the street, no part of the rail station.
        assertNull(evidence(francfort, fraTram))
        // Fernbahnhof and Regionalbahnhof of the airport: two stations.
        assertNull(evidence(fraAirport, fraAirportRegional))
        assertNull(evidence(saarbrueckenHbf, saarbrueckenNord))
        // Synthetic, public positions: Berlin Friedrichstraße and Berlin Hbf, about 1.3 km.
        val friedrichstrasse = Stop("tr:de-DELFI_de:11000:900100001", "S+U Berlin Friedrichstraße", 52.52027, 13.38700, null, setOf("suburban", "regional"))
        assertFalse(StationIdentity.same(friedrichstrasse, real(berlinAt)))
        // A foreign feed's stop of another town or of a side of town right beside a main station.
        val offenbachOst = Stop("tr:fr-horaires-sncf_X", "Offenbach Ost", 50.1073, 8.6624, null, setOf("express"))
        assertFalse(StationIdentity.same(offenbachOst, real(fraHbfRail)))
        val bruxellesCentral = Stop("tr:fr-horaires-sncf_C", "Bruxelles-Central", 50.8455, 4.3571, null, setOf("express"))
        val brusselZuid = Stop("tr:be-sncb_8814001", "Brussel-Zuid", 50.8357, 4.3363, null, setOf("express", "regional"))
        assertFalse(StationIdentity.same(bruxellesCentral, brusselZuid))
    }

    @Test
    fun exonymsOfOtherCountriesByPlaceNotByAList() {
        // Synthetic, as the SNCF and the SNCB would write them, a few metres apart.
        val zuid = Stop("tr:be-sncb_8814001", "Brussel-Zuid", 50.83570, 4.33630, null, setOf("express", "regional"))
        val midi = Stop("tr:fr-horaires-sncf_FR::LMU:x:", "Bruxelles-Midi", 50.83590, 4.33620, null, setOf("express"))
        assertEquals(StationEvidence.Exonym, StationIdentity.evidence(zuid, midi))
        val wien = Stop("tr:at-Railway-Current-Reference-Data-2026_Pat:49:1349", "Wien Hauptbahnhof", 48.18520, 16.37660, null, setOf("express", "regional", "suburban"))
        val vienne = Stop("tr:fr-horaires-sncf_FR::LMO:y:", "Vienne", 48.18530, 16.37600, null, setOf("express"))
        assertEquals(StationEvidence.Exonym, StationIdentity.evidence(wien, vienne))
        val amsterdamNs = Stop("tr:nl-OpenOV_2422", "Amsterdam Centraal", 52.37890, 4.90060, null, setOf("express", "regional"))
        val amsterdamDb = Stop("tr:de-DELFI_nl:x", "Amsterdam Centraal", 52.37900, 4.90000, null, setOf("express"))
        assertEquals(StationEvidence.Name, StationIdentity.evidence(amsterdamNs, amsterdamDb))
        // Within one feed the town in another language never joins: that is no exonym.
        val sameFeed = Stop("tr:fr-horaires-sncf_Z", "Bruxelles-Midi", 50.83590, 4.33620, null, setOf("express"))
        assertFalse(StationIdentity.otherFeedTwins(midi, sameFeed.copy(id = midi.id)))
        // Farther than 150 m: no exonym, however plain the names.
        assertNull(StationIdentity.evidence(wien, vienne.copy(latitude = 48.1875)))
    }

    @Test
    fun theStopOfTheStationsCountryStandsForIt() {
        val stops = listOf(real(francfort), real(fraHbfRail))
        assertEquals(1, StationIdentity.preferredIndex(stops))
        assertEquals(0, StationIdentity.preferredIndex(listOf(real(saarbrueckenHbf), real(sarrebruck))))
        assertTrue(localStationFirst(real(francfort), real(fraHbfRail)))
        assertFalse(localStationFirst(real(fraHbfRail), real(francfort)))
    }

    @Test
    fun aPlatformNumberAloneIsNoStopPlace() {
        val a = Stop("tr:de-DELFI_de:06412:10:3:4", "Frankfurt (Main) Hauptbahnhof", 50.1071, 8.6633, null)
        val b = Stop("tr:de-DELFI_de:08111:6115:3:4", "Stuttgart Hauptbahnhof", 48.7840, 9.1820, null)
        assertEquals(normalStationId(a.id), normalStationId(b.id))
        assertFalse(StationIdentity.sameStopPlace(a, b))
        assertTrue(StationIdentity.sameStopPlace(a, a.copy(id = "tr:de-DELFI_de:06412:10:5:8")))
        assertTrue(StationIdentity.sameStopPlace(Stop("900003201", "x", null, null, null), Stop("de:11000:900003201::2", "y", null, null, null)))
        // By ids alone (a feeder run's first stop, the carriage hint): the platform's last number never decides.
        assertFalse(StationIdentity.sameStopPlaceId("tr:de-DELFI_de:06412:10:3:4", "tr:de-DELFI_de:08111:6115:3:4"))
        assertTrue(StationIdentity.sameStopPlaceId("tr:de-DELFI_de:06412:10:3:4", "tr:de-DELFI_de:06412:10_G"))
        assertTrue(StationIdentity.sameStopPlaceId("8011160", "8011160"))
    }

    // A departure as TransitousSource makes it of a stop time.
    private fun departure(row: RealStopTime): Departure {
        val line = rapidLineName(composeLineName(row.mode, row.route, row.number, row.display, null, null, null), product(row.mode) ?: "") ?: "?"
        val planned = row.planned?.let { OffsetDateTime.parse(it) }
        return Departure(
            line = line,
            product = classifyProduct(product(row.mode) ?: "", line, null),
            direction = fullHeadsign(row.headsign, row.tripTo) ?: "",
            time = planned,
            plannedTime = planned,
            delaySeconds = null,
            platform = row.track,
            cancelled = false,
            tripId = row.tripId,
        )
    }

    @Test
    fun theSameTrainFromTwoFeedsIsOneDeparture() {
        // Strasbourg (one board of MOTIS with the SNCF's and DELFI's stops): ICE 9573, 9583 and
        // 9591 come twice, once towards "Frankfurt (Main) Hauptbahnhof", once "Francfort sur le Main".
        val board = REAL_STOP_TIMES.filter { it.board == "strasbourg_0" }.map { departure(it) }
        val ices = { list: List<Departure> -> list.filter { it.line.endsWith("9573") || it.line.endsWith("9583") || it.line.endsWith("9591") } }
        assertEquals(6, ices(board).size)
        val shown = withoutDoubleRides(board)
        assertEquals(3, ices(shown).size)
        // Other trains stay: only those twins went.
        assertEquals(board.size - 3, shown.size)
        // Basel SBB: the TGV 9218 of the SBB and the SNCF's TGV Lyria 9218 ("Paris Gare de Lyon Hall 1 - 2").
        val basel = REAL_STOP_TIMES.filter { it.board == "basel_0" }.map { departure(it) }
        val tgv = { list: List<Departure> -> list.count { it.line.endsWith("9218") } }
        assertEquals(2, tgv(basel))
        assertEquals(1, tgv(withoutDoubleRides(basel)))
    }

    @Test
    fun boardOfFrankfurtWithItsFrenchTwin() {
        // The SNCF's board of "Francfort sur le Main" and an ICE 9552 as DELFI sends it at the same minute.
        val french = REAL_STOP_TIMES.filter { it.board == "frankfurt-hbf_2" }.map { departure(it) }
        val first = french.first { it.line.endsWith("9552") }
        val german = first.copy(line = "ICE 9552", direction = "Paris Est", platform = "19", tripId = "de-DELFI_1")
        assertTrue(sameTrainRun(first, german))
        assertEquals(1, withoutDoubleRides(listOf(german, first)).size)
        // Another train number, or the same number an hour later, is another train.
        assertFalse(sameTrainRun(first, german.copy(line = "ICE 9553")))
        assertFalse(sameTrainRun(first, german.copy(plannedTime = german.plannedTime!!.plusHours(1), time = german.time!!.plusHours(1))))
        // A regional line number shared by many runs is never taken for a train number.
        val re = german.copy(line = "RE 55", product = "regional")
        assertFalse(sameTrainRun(re, re.copy(direction = "Würzburg")))
    }

    @Test
    fun aChangeBetweenTwinsIsNoWalkToAnotherStation() {
        assertTrue(sameStation(real(francfort), "Francfort sur le Main", real(fraHbfRail), "Frankfurt (Main) Hbf"))
        assertTrue(sameStation(real(saarbrueckenHbf), "", real(sarrebruck), ""))
        assertFalse(sameStation(real(koelnHbf), "Köln Hbf", real(koelnDeutz), "Köln Messe/Deutz"))
    }

    @Test
    fun aStoredStopOfARetiredFeedGoesByItsStationKey() {
        // Saved with the at-Railway feed of 2026; this session only answers with the 2027 feed.
        val saved = "tr:at-Railway-Current-Reference-Data-2026_de:05315:11201:7:72"
        val now = Stop("tr:at-Railway-Current-Reference-Data-2027_de:05315:11201:7:72", "Köln Hbf", 50.94313, 6.95809, null, setOf("express"))
        StationIdentity.noteStored(saved, StationIdentity.stationKey(real(koelnHbf)))
        assertEquals(saved, StationIdentity.resolveId(saved))
        StationIdentity.noteSeen(now)
        assertEquals(now.id, StationIdentity.resolveId(saved))
        // A feed that still answers keeps its own id; the local provider's ids are never touched.
        StationIdentity.noteSeen(Stop("tr:at-Railway-Current-Reference-Data-2026_de:06412:10:16:16", "Frankfurt (Main) Hbf", 50.10629, 8.66196, null, setOf("express")))
        assertEquals(saved, StationIdentity.resolveId(saved))
        StationIdentity.noteStored("900003201", "vbb:900003201")
        assertEquals("900003201", StationIdentity.resolveId("900003201"))
        // An id stored without a key stays as it is.
        assertEquals("tr:x-Gone_1", StationIdentity.resolveId("tr:x-Gone_1"))
    }
}
