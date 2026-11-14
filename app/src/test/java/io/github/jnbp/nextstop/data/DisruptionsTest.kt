package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** Collecting, grouping and reporting disruption notices once (see Disruptions.kt). */
class DisruptionsTest {
    private val start: OffsetDateTime = OffsetDateTime.parse("2026-01-01T10:00:00+01:00")
    private val works = Warning("Bauarbeiten", "Zwischen A und B Ersatzverkehr mit Bussen.")
    private val lift = Warning("Aufzug defekt", "")

    private fun ride(line: String, product: String, vararg warnings: Warning) = Leg(
        line, product, false, "A", "B", start, start, start.plusMinutes(10), start.plusMinutes(10),
        null, null, null, false, warnings = warnings.toList(),
    )

    private fun departure(line: String, product: String, vararg warnings: Warning) =
        Departure(line, product, "X", start, start, null, null, false, warnings = warnings.toList())

    @Test
    fun oneNoticeWithAllItsLines() {
        val journeys = listOf(
            Journey(listOf(ride("S41", "suburban", works))),
            Journey(listOf(ride("S42", "suburban", works), ride("U8", "subway", lift))),
        )
        val found = disruptionsOf(journeys).associateBy { it.summary }
        assertEquals(setOf("S41", "S42"), found.getValue("Bauarbeiten").lines)
        // A lift is a station's notice, whatever lines carry it.
        assertEquals(DisruptionGroup.Stations, found.getValue("Aufzug defekt").group)
    }

    @Test
    fun theSameTextKeepsItsId() {
        assertEquals(disruptionId(works), disruptionId(Warning(" Bauarbeiten", "Zwischen A und B Ersatzverkehr mit Bussen. ")))
    }

    @Test
    fun copiesOfOneNoticeAreOne() {
        val moved = Warning("Haltestelle verlegt", "Bus M45, 245: Verlegung der Haltestelle S+U Zoologischer Garten/Jebensstraße Richtung Johannesstift.", id = "294758")
        // The same notice with other spaces and a placeholder, and once under the provider's id only.
        val spaced = Warning("Haltestelle  verlegt", "Bus M45, 245:  Verlegung der Haltestelle S+U Zoologischer Garten/Jebensstraße Richtung Johannesstift. [MEHR/MORE]")
        val sameId = Warning("Haltestelle verlegt", "Verlegung der Haltestelle", id = "294758")
        val all = mergeDisruptions(
            listOf(
                disruptionsOfDepartures(listOf(departure("M45", "bus", moved))),
                disruptionsOfDepartures(listOf(departure("245", "bus", spaced))),
                disruptionsOfDepartures(listOf(departure("245", "bus", sameId))),
            ),
        )
        assertEquals(1, all.size)
        assertEquals(setOf("M45", "245"), all.single().lines)
    }

    @Test
    fun twoDiversionsOfOneLineStayTwo() {
        // Two notices of bus 140 at U Mehringdamm with the same headline but other ways.
        val one = Warning("Umleitung", "Bus 140: Umleitung aufgrund von Bauarbeiten zwischen Hornstraße und Wilmsstraße via Blücherstraße.")
        val two = Warning("Umleitung", "Bus 140: Umleitung zwischen Wilmsstraße und U Mehringdamm via Gneisenaustraße.")
        assertEquals(2, disruptionsOfDepartures(listOf(departure("140", "bus", one, two))).size)
    }

    @Test
    fun onlyNewOnesAreReported() {
        val all = disruptionsOfDepartures(listOf(departure("M10", "tram", works), departure("200", "bus", lift)))
        val seen = setOf(disruptionId(works))
        assertEquals(listOf("Aufzug defekt"), newDisruptions(all, seen).map { it.summary })
    }

    @Test
    fun groupedWithMyLinesFirst() {
        val all = mergeDisruptions(
            listOf(
                disruptionsOfDepartures(listOf(departure("U2", "subway", works))),
                disruptionsOfDepartures(listOf(departure("U8", "subway", lift), departure("S1", "suburban", lift))),
            ),
        )
        val grouped = groupDisruptions(all, myLines = setOf("U8"))
        // The lift notice was seen on S1 and U8: filed under the stations, not under a line.
        assertEquals(listOf(DisruptionGroup.Subway, DisruptionGroup.Stations), grouped.keys.toList())
        assertEquals(listOf("Bauarbeiten"), grouped.getValue(DisruptionGroup.Subway).map { it.summary })
    }

    // As v6.bvg.transport.rest sends them at Am Kupfergraben and U Mehringdamm.
    private val tram12 = Warning("Ersatzverkehr", "Tram 12: Ersatzverkehr aufgrund von Bauarbeiten zwischen Am Kupfergraben und U Eberswalder Straße.")
    private val mehringdammLift = Warning(
        "Aufzug außer Betrieb",
        "Der Aufzug im Bahnhof U Mehringdamm (Zwischenebene <> Bahnsteig U6/U7 Richtung Alt-Tegel/Rathaus Spandau) wird so schnell wie möglich repariert.",
    )
    private val movedStop = Warning("Haltestelle verlegt", "Bus M45, 245: Verlegung der Haltestelle S+U Zoologischer Garten/Jebensstraße")

    @Test
    fun aReplacementIsFiledUnderTheLineItReplaces() {
        // BVG runs the replacement of tram 12 as bus "12" and puts the notice on those buses.
        val found = disruptionsOfDepartures(listOf(departure("12", "bus", tram12))).single()
        assertEquals(listOf(NoticeLine("12", "tram")), found.about)
        assertEquals(DisruptionGroup.Tram, found.group)
        assertEquals(listOf(NoticeLine("12", "tram")), found.badges)
    }

    @Test
    fun aLiftIsAStationNoticeWithoutTheBusesOfTheStation() {
        val found = disruptionsOfDepartures(
            listOf("248" to "bus", "M19" to "bus", "140" to "bus", "U6" to "subway", "U7" to "subway")
                .map { (line, product) -> departure(line, product, mehringdammLift) },
        ).single()
        assertEquals(DisruptionGroup.Stations, found.group)
        assertEquals(emptyList<NoticeLine>(), found.badges)
    }

    @Test
    fun aMovedStopKeepsTheLinesItNames() {
        val found = disruptionsOfDepartures(listOf(departure("245", "bus", movedStop))).single()
        assertEquals(DisruptionGroup.Stations, found.group)
        assertEquals(listOf(NoticeLine("M45", "bus"), NoticeLine("245", "bus")), found.badges)
    }

    @Test
    fun linesAtTheStartOfANotice() {
        assertEquals(listOf(NoticeLine("U6", "subway")), linesNamedIn("U6: Pendelverkehr", emptyList()))
        assertEquals(listOf(NoticeLine("M10", "tram")), linesNamedIn("Tram M10: Replacement service due to construction works", emptyList()))
        // A time is no line.
        assertEquals(emptyList<NoticeLine>(), linesNamedIn("12:30 Uhr: Sperrung", listOf(NoticeLine("12", "bus"))))
    }
}
