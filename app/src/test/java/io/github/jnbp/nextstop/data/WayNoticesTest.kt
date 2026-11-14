package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The notices of the cards of "My routes": each once, counted once, and each card's own (see WayNotices.kt). */
class WayNoticesTest {
    private fun notice(id: String, text: String, vararg runs: Pair<String, String>, summary: String = "Umleitung") = Disruption(
        id = id,
        summary = summary,
        text = text,
        lines = runs.map { it.first }.toSortedSet(),
        products = runs.map { it.second }.toSet(),
        carriers = runs.map { NoticeLine(it.first, it.second) },
    )

    private val tram = notice("a", "Tram M27: Umleitung zwischen A und B wegen Bauarbeiten am Gleis.", "M27" to "tram")
    private val bus = notice("b", "Bus 100: Umleitung zwischen C und D wegen einer Veranstaltung im Park.", "100" to "bus")
    private val subway = notice("c", "U2: Kein Zugverkehr zwischen E und F, Ersatzverkehr mit Bussen.", "U2" to "subway")

    @Test
    fun aNoticeOnTwoCardsCountsOnce() {
        val found = wayNotices(
            listOf(
                "route:1" to listOf(tram, bus),
                "route:2" to listOf(tram.copy(lines = sortedSetOf("M27", "M10"))),
                "place:0" to listOf(subway),
            ),
        )
        assertEquals(listOf("a", "b", "c"), found.all.map { it.id })
        assertEquals(listOf("a", "b"), found.of("route:1").map { it.id })
        assertEquals(listOf("a"), found.of("route:2").map { it.id })
        // The card holds the merged notice, with the lines of both copies.
        assertEquals(setOf("M10", "M27"), found.of("route:2").single().lines)
        assertEquals(listOf("c"), found.of("place:0").map { it.id })
    }

    @Test
    fun copiesOnOneCardAreOne() {
        // The same notice found twice, once under the provider's id only.
        val copy = notice("x", "Umleitung", "M27" to "tram").copy(providerIds = setOf("p1"))
        val found = wayNotices(listOf("route:1" to listOf(tram.copy(providerIds = setOf("p1")), copy)))
        assertEquals(1, found.all.size)
        assertEquals(1, found.of("route:1").size)
    }

    @Test
    fun aCardWithoutNoticesHasNone() {
        val found = wayNotices(listOf("route:1" to emptyList(), "saved:x" to listOf(bus)))
        assertTrue(found.of("route:1").isEmpty())
        assertEquals(setOf("saved:x"), found.byCard.keys)
        assertEquals(WayNotices.NONE, wayNotices(emptyList()))
    }

    @Test
    fun aConnectionHasTheNoticesOfItsLines() {
        val items = listOf(tram, bus, subway)
        assertEquals(listOf("a", "c"), noticesOnLines(items, listOf("Tram M27", "U2")).map { it.id })
        assertTrue(noticesOnLines(items, listOf("S41")).isEmpty())
        assertTrue(noticesOnLines(items, emptyList()).isEmpty())
    }

    @Test
    fun aConnectionHasTheNoticesAboutItsLinesFoundOnOthers() {
        // A notice of tram 12, found on its replacement buses (run as bus "12E").
        val replaced = notice("r", "Tram 12: Ersatzverkehr mit Bussen zwischen G und H.", "12E" to "bus")
        assertEquals(listOf("r"), noticesOnLines(listOf(replaced), listOf("12")).map { it.id })
    }

    @Test
    fun thePillSettlesWhenEveryCheckFailed() {
        assertEquals(WaysPill(loading = true, count = 0), waysPill(checkable = true, known = false, done = false, count = 0))
        assertEquals(WaysPill(loading = false, count = 2), waysPill(checkable = true, known = true, done = false, count = 2))
        assertTrue(waysPill(checkable = true, known = false, done = true, count = 0).unknown)
        // Nothing to ask: "All running" at once.
        assertEquals(WaysPill(loading = false, count = 0), waysPill(checkable = false, known = false, done = false, count = 0))
    }
}
