package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The sections of a search for places: a fixed order, and late answers only add to their own section (#208). */
class SearchSectionOrderTest {
    private val here = 52.5219 to 13.4132

    private fun stop(id: String, lat: Double, lon: Double, vararg products: String) =
        Stop(id, id, lat, lon, null, products.toSet(), PlaceKind.Stop)

    private fun address(id: String) = Stop(id, id, 52.50, 13.40, null, emptySet(), PlaceKind.Address)

    private val nearStop = stop("Alexanderplatz", 52.5215, 13.4110, "subway", "suburban", "tram")
    private val farStop = stop("Rathaus Spandau", 52.5355, 13.1995, "subway", "bus")
    private val mainStation = stop("Berlin Hbf", 52.5251, 13.3694, "express", "suburban")
    private val otherTown = stop("Hamburg Hbf", 53.5530, 10.0067, "express", "regional")
    private val poi = Stop("poi:tower", "Fernsehturm", 52.5208, 13.4094, null, emptySet(), PlaceKind.Poi)
    private val street = address("addr:street")

    @Test
    fun sectionsComeInTheirFixedOrder() {
        val sections = searchSections(
            instant = emptyList(),
            found = listOf(street, poi, farStop, mainStation, nearStop),
            far = listOf(otherTown),
            near = here,
        )
        assertEquals(listOf(nearStop, farStop), sections.local)
        assertEquals(listOf(mainStation, otherTown), sections.longDistance)
        assertEquals(listOf(street, poi), sections.places)
        assertEquals(listOf(nearStop, farStop, mainStation, otherTown, street, poi), sections.all)
    }

    @Test
    fun nearStationsComeFirstElseTheGivenOrderStays() {
        val a = stop("Spandau A", 52.53, 13.20, "bus")
        val b = stop("Spandau B", 52.54, 13.21, "bus")
        val sections = searchSections(emptyList(), listOf(a, b, nearStop), near = here)
        assertEquals(listOf(nearStop, a, b), sections.local)
        // Without a known position the order of the providers stays.
        assertEquals(listOf(a, b, nearStop), searchSections(emptyList(), listOf(a, b, nearStop)).local)
    }

    @Test
    fun instantRowsStayFirstAndAreNotRepeated() {
        val sections = searchSections(listOf(nearStop), listOf(nearStop, farStop, street), near = here)
        assertEquals(listOf(nearStop), sections.instant)
        assertEquals(listOf(farStop), sections.local)
        assertEquals(listOf(nearStop, farStop, street), sections.all)
    }

    @Test
    fun lateAnswersNeverMoveEarlierSections() {
        val instant = listOf(nearStop)
        // Stage 1: only what is known at once. Stage 2: the providers answer. Stage 3: the far towns.
        val stages = listOf(
            searchSections(instant, emptyList(), emptyList(), here),
            searchSections(instant, listOf(street, farStop, mainStation), emptyList(), here),
            searchSections(instant, listOf(street, farStop, mainStation), listOf(otherTown), here),
        )
        for (i in 1 until stages.size) {
            val before = stages[i - 1]
            val after = stages[i]
            assertEquals(before.instant, after.instant)
            // Each section only grows at its end, so every row shown before keeps its section.
            assertTrue(after.local.take(before.local.size) == before.local)
            assertTrue(after.longDistance.take(before.longDistance.size) == before.longDistance)
            assertTrue(after.places.take(before.places.size) == before.places)
        }
        assertEquals(listOf(nearStop, farStop, mainStation, otherTown, street), stages.last().all)
    }

    @Test
    fun theSameInputGivesTheSameList() {
        val found = listOf(poi, mainStation, farStop, street, nearStop)
        val first = searchSections(emptyList(), found, listOf(otherTown), here)
        repeat(3) { assertEquals(first, searchSections(emptyList(), found, listOf(otherTown), here)) }
    }

    @Test
    fun peopleComeRightAfterTheOwnPositionThenKnownThenStationsThenPlaces() {
        // Joel's order while typing (#208): contacts and appointments first, then stations, then places;
        // the own position stays the fixed first row, history and favourites follow the people.
        assertEquals(
            listOf(
                SearchSection.Lead,
                SearchSection.People,
                SearchSection.Known,
                SearchSection.Local,
                SearchSection.LongDistance,
                SearchSection.Places,
            ),
            SEARCH_SECTION_ORDER,
        )
    }

    @Test
    fun theOwnPositionIsTheLeadAndNotAmongTheKnownPlaces() {
        val position = Stop("current-location", "Aktueller Standort", 52.52, 13.41, null, emptySet(), PlaceKind.Address)
        val sections = searchSections(
            instant = listOf(position, farStop),
            found = listOf(street, nearStop),
            near = here,
            leadIds = setOf(position.id),
        )
        assertEquals(listOf(position), sections.lead)
        assertEquals(listOf(farStop), sections.instant)
        assertEquals(listOf(nearStop), sections.local)
        assertEquals(listOf(position, farStop, nearStop, street), sections.all)
    }

    @Test
    fun lateAnswersNeverMoveTheLeadOrTheKnownPlaces() {
        val position = Stop("current-location", "Aktueller Standort", 52.52, 13.41, null, emptySet(), PlaceKind.Address)
        val instant = listOf(position, farStop)
        val lead = setOf(position.id)
        val early = searchSections(instant, emptyList(), emptyList(), here, lead)
        val late = searchSections(instant, listOf(street, mainStation, nearStop), listOf(otherTown), here, lead)
        assertEquals(early.lead, late.lead)
        assertEquals(early.instant, late.instant)
    }

    @Test
    fun aRegionalOnlyStationIsLocalTransport() {
        val regional = stop("Erkner", 52.4207, 13.7522, "regional", "suburban")
        assertEquals(listOf(regional), searchSections(emptyList(), listOf(regional)).local)
        assertTrue(isLongDistanceStation(mainStation))
    }
}
