package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.Disruption
import io.github.jnbp.nextstop.data.NoticeLine
import io.github.jnbp.nextstop.data.Stop
import org.junit.Assert.assertEquals
import org.junit.Test

/** The search field of "All disruptions" (see DisruptionSearch.kt). */
class DisruptionSearchTest {
    private val u9 = Disruption(
        id = "a",
        summary = "Bauarbeiten",
        text = "U9: Zwischen Osloer Straße und Leopoldplatz Ersatzverkehr mit Bussen.",
        lines = setOf("U9"),
        products = setOf("subway"),
        carriers = listOf(NoticeLine("U9", "subway")),
    )
    private val re1 = Disruption(
        id = "b",
        summary = "Verspätungen",
        text = "RE 1: Verspätungen wegen einer Weichenstörung.",
        lines = setOf("RE 1"),
        products = setOf("regional"),
    )
    private val re10 = Disruption(
        id = "c",
        summary = "Zugausfall",
        text = "Einzelne Fahrten fallen aus.",
        lines = setOf("RE10"),
        products = setOf("regional"),
    )
    private val lift = Disruption(
        id = "d",
        summary = "Aufzug außer Betrieb",
        text = "Der Aufzug zum Bahnsteig ist gestört.",
        lines = emptySet(),
        products = emptySet(),
        stops = listOf(Stop("1", "S+U Zoologischer Garten", null, null, null)),
    )
    private val all = indexDisruptions(listOf(u9, re1, re10, lift))

    private fun ids(query: String) = filterDisruptions(all, query).map { it.id }

    @Test
    fun anEmptyQueryKeepsAll() {
        assertEquals(listOf("a", "b", "c", "d"), ids(""))
        assertEquals(listOf("a", "b", "c", "d"), ids("   "))
    }

    @Test
    fun linesInAnyCaseAndSpacing() {
        assertEquals(listOf("a"), ids("u9"))
        assertEquals(listOf("a"), ids("U 9"))
        assertEquals(listOf("b"), ids("RE1"))
        assertEquals(listOf("b"), ids("re 1"))
        // A line token is a whole line: RE1 does not find RE10.
        assertEquals(listOf("c"), ids("RE10"))
    }

    @Test
    fun stationNamesAndTextWithoutDiacritics() {
        assertEquals(listOf("d"), ids("zoolog"))
        assertEquals(listOf("d"), ids("AUFZUG ausser"))
        assertEquals(listOf("a"), ids("osloer strasse"))
        assertEquals(listOf("a"), ids("Osloer Str."))
        assertEquals(listOf("b"), ids("weichenstörung"))
        assertEquals(listOf("b"), ids("Weichenstoerung"))
    }

    @Test
    fun everyWordMustFit() {
        assertEquals(listOf("a"), ids("u9 bus"))
        assertEquals(emptyList<String>(), ids("u9 aufzug"))
        assertEquals(emptyList<String>(), ids("xyz"))
    }
}
