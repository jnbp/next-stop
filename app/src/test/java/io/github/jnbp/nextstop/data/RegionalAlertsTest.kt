package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The notices noted from loaded answers and the regions asked outside Berlin (see RegionalAlerts.kt). Made-up stops. */
class RegionalAlertsTest {
    private val now: OffsetDateTime = OffsetDateTime.parse("2026-03-02T10:00:00+01:00")
    private val nowMillis = now.toInstant().toEpochMilli()
    private val station = Stop("tr:x1", "Example Hbf", 50.70, 7.10, null)

    private fun notice(summary: String, text: String, line: String, until: OffsetDateTime? = null, stop: Stop? = null) =
        disruptionsOfDepartures(
            listOf(
                Departure(
                    line, "regional", "Y", now, now, null, null, false,
                    warnings = listOf(Warning(summary, text, validUntil = until, stop = stop)),
                ),
            ),
        ).single()

    @Test
    fun aNoticeSeenAgainIsOneWithBothLinesAndTheLaterTime() {
        val text = "Zwischen Example Hbf und Example Nord fahren Busse statt Zügen."
        val first = harvestMerge(emptyList(), listOf(notice("Bauarbeiten", text, "RE5")), nowMillis - 60_000)
        val second = harvestMerge(first, listOf(notice("Bauarbeiten", text, "RB26")), nowMillis)
        assertEquals(1, second.size)
        assertEquals(setOf("RE5", "RB26"), second.single().item.lines)
        assertEquals(nowMillis, second.single().seenMillis)
    }

    @Test
    fun theNewestStayWhenThereAreTooMany() {
        val many = (1..5).map { notice("Störung $it", "Text der Störung Nummer $it auf der Strecke.", "RE$it") }
        var kept = emptyList<HarvestEntry>()
        many.forEachIndexed { index, item -> kept = harvestMerge(kept, listOf(item), nowMillis + index, max = 3) }
        assertEquals(listOf("Störung 5", "Störung 4", "Störung 3"), kept.map { it.item.summary })
    }

    @Test
    fun oldAndEndedNoticesGo() {
        val ended = HarvestEntry(notice("Vorbei", "Diese Störung ist schon vorbei gewesen.", "RE1", until = now.minusMinutes(5)), nowMillis)
        val old = HarvestEntry(notice("Alt", "Diese Störung wurde lange nicht gesehen.", "RE2"), nowMillis - HARVEST_KEEP_MILLIS - 1)
        val fresh = HarvestEntry(notice("Neu", "Diese Störung gilt noch eine Weile.", "RE3", until = now.plusHours(2)), nowMillis - 1_000)
        assertEquals(listOf("Neu"), harvestCurrent(listOf(ended, old, fresh), nowMillis, now).map { it.item.summary })
    }

    @Test
    fun aNoticeKeepsTheStopItWasFoundAt() {
        val item = notice("Aufzug defekt", "", "RE5", stop = station)
        val kept = harvestMerge(emptyList(), listOf(item), nowMillis).single().item
        assertEquals(listOf(station), kept.stops)
    }

    @Test
    fun onlyPlacesAwayFromBerlinAreAskedEachRegionOnce() {
        val berlin = 52.50 to 13.30
        val bonn = 50.73 to 7.10
        val nearBonn = 50.75 to 7.12
        val hamburg = 53.55 to 10.00
        assertEquals(listOf(bonn, hamburg), regionAnchorsToAsk(listOf(berlin, bonn, nearBonn, hamburg)))
        assertEquals(listOf(bonn), regionAnchorsToAsk(listOf(bonn, hamburg), max = 1))
        assertTrue(regionAnchorsToAsk(listOf(berlin)).isEmpty())
    }

    @Test
    fun theRegionPrefersItsRailStations() {
        val center = 50.73 to 7.10
        val bus = Stop("tr:b", "Example Busplatz", 50.731, 7.101, null, products = setOf("bus"))
        val rail = Stop("tr:r", "Example Hbf", 50.74, 7.11, null, products = setOf("regional", "bus"))
        val far = Stop("tr:f", "Faraway Hbf", 51.50, 7.10, null, products = setOf("express"))
        val hubs = bigStopsIn(listOf(bus, rail, far), regionBox(center), center, REGION_HUBS)
        assertEquals(listOf("tr:r", "tr:b"), hubs.map { it.id })
    }
}
