package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Notices about lifts and step-free access raise the warning sign only for step-free travel. Made-up texts. */
class NoticeKindsTest {
    private fun notice(summary: String, text: String = "", code: String? = null) = Warning(summary, text, code = code)

    @Test
    fun liftsAndEscalatorsAreAccessibility() {
        listOf(
            notice("Aufzug außer Betrieb", "Der Aufzug zwischen Bahnsteig und Straße ist gestört."),
            notice("Störung", "Bahnsteigaufzug am Ausgang Nord defekt"),
            notice("Fahrstuhl gesperrt"),
            notice("Rolltreppe zum Bahnsteig außer Betrieb"),
            notice("Fahrtreppe defekt"),
            notice("Ausfall des Aufzugs"),
            notice("Die Station ist derzeit nicht barrierefrei erreichbar."),
            notice("Elevator out of service"),
            notice("Escalators under repair"),
            notice("Lift to platform 2 not available"),
            notice("Hinweis", code = "bf"),
        ).forEach { assertEquals(it.summary, NoticeKind.Accessibility, noticeKind(it)) }
    }

    @Test
    fun realDisruptionsStayDisruptions() {
        listOf(
            notice("Ersatzverkehr mit Bussen", "Bitte nutzen Sie den Aufzug am Ausgang Süd."),
            notice("Umleitung", "Die Linie wird umgeleitet."),
            notice("Bauarbeiten", "Einzelne Fahrten fallen aus."),
            notice("Zugausfall"),
            notice("Kein Halt an der Haltestelle"),
            notice("Haltestelle verlegt"),
            notice("Polizeieinsatz", "Verspätungen möglich"),
            notice("Detour", "Buses are diverted"),
            notice("Trains cancelled between two stations"),
            notice("Elevator closed, station closed", code = "bf"),
            notice("Bauarbeiten", "Gleis 2 gesperrt"),
            notice("Geänderte Abfahrtszeiten"),
        ).forEach { assertEquals(it.summary, NoticeKind.Disruption, noticeKind(it)) }
    }

    @Test
    fun wordsInsideOtherWordsDoNotCount() {
        // "lift" only as a word; "SEV" only as a word.
        assertEquals(NoticeKind.Disruption, noticeKind(notice("Shift of the timetable")))
        assertEquals(NoticeKind.Disruption, noticeKind(notice("Liftoff")))
        assertEquals(NoticeKind.Disruption, noticeKind(notice("SEV", "zwischen zwei Stationen")))
        assertEquals(NoticeKind.Disruption, noticeKind(notice("Fahrplanänderung", code = "XY")))
    }

    @Test
    fun onlyStepFreeTravelRaisesTheSignForLifts() {
        val lift = notice("Aufzug außer Betrieb")
        val detour = notice("Umleitung")
        assertFalse(warningAlerts(lift, stepFree = false))
        assertTrue(warningAlerts(lift, stepFree = true))
        assertTrue(warningAlerts(detour, stepFree = false))
        assertEquals(listOf(detour), alertingWarnings(listOf(lift, detour), stepFree = false))
        assertEquals(listOf(lift), quietWarnings(listOf(lift, detour), stepFree = false))
        assertEquals(listOf(lift, detour), alertingWarnings(listOf(lift, detour), stepFree = true))
        assertEquals(emptyList<Warning>(), quietWarnings(listOf(lift, detour), stepFree = true))
    }
}
