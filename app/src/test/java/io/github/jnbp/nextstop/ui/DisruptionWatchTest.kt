package io.github.jnbp.nextstop.ui

import java.time.Duration
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** The delay until a daily check of disruptions, and the stored check times. */
class DisruptionWatchTest {
    @Test
    fun laterToday() {
        assertEquals(Duration.ofMinutes(90), delayUntil(LocalDateTime.of(2026, 1, 1, 5, 30), 7 * 60))
    }

    @Test
    fun tomorrowOncePassed() {
        assertEquals(Duration.ofHours(23), delayUntil(LocalDateTime.of(2026, 1, 1, 8, 0), 7 * 60))
    }

    @Test
    fun checkTimesAreParsedSortedAndClean() {
        assertEquals(listOf(420, 1080), parseCheckTimes("1080,420,420"))
        assertEquals(listOf(390), parseCheckTimes("390, x, -5, 1440"))
        assertEquals(emptyList<Int>(), parseCheckTimes(""))
    }
}

/** Stored notices of a route as notices again, with the lines and colours of the list of all disruptions. */
class StoredDisruptionTest {
    @Test
    fun aWholeStoredNoticeHasTheLinesItIsAbout() {
        // Found on the replacement buses of tram M27; the text names the tram.
        val stored = StoredDisruption(
            id = "1",
            text = "Haltestelle verlegt",
            lines = listOf("M27"),
            product = "bus",
            summary = "Haltestelle verlegt",
            body = "Tram M27: Verlegung der Haltestelle Musterstraße.",
            carriers = listOf(io.github.jnbp.nextstop.data.NoticeLine("M27", "bus")),
        )
        val notice = stored.toDisruption()
        assertEquals(listOf(io.github.jnbp.nextstop.data.NoticeLine("M27", "tram")), notice.badges)
        assertEquals("Haltestelle verlegt", notice.summary)
    }

    @Test
    fun anOlderEntryStillLoads() {
        val stored = StoredDisruption(id = "2", text = "U2: Kein Zugverkehr", lines = listOf("U2"), product = "")
        val notice = stored.toDisruption()
        assertEquals("U2: Kein Zugverkehr", notice.summary)
        assertEquals("subway", notice.badges.single().product)
    }

    @Test
    fun aStatusKnowsEachNoticeOnce() {
        val item = StoredDisruption(id = "3", text = "Umleitung", lines = listOf("100"), product = "bus")
        val status = StoredRouteStatus(2, false, "Umleitung", listOf("100"), 0L, items = listOf(item, item))
        assertEquals(1, status.disruptions().size)
        // A status of a version before the items knows only its first notice.
        val older = StoredRouteStatus(3, false, "Umleitung", listOf("100"), 0L)
        assertEquals(listOf("Umleitung"), older.disruptions().map { it.summary })
        assertEquals(0, StoredRouteStatus(0, true, null, emptyList(), 0L).disruptions().size)
    }

    @Test
    fun theNoticesAroundARouteStayApartFromThoseOnItsRides() {
        val ride = StoredDisruption(id = "4", text = "Umleitung", lines = listOf("100"), product = "bus")
        val around = StoredDisruption(id = "5", text = "Aufzug außer Betrieb", lines = listOf("U2"), product = "subway", around = true)
        val status = StoredRouteStatus(2, false, "Umleitung", listOf("100"), 0L, items = listOf(ride, around), radiusMeters = 500)
        assertEquals(listOf("4"), status.rideDisruptions().map { it.id })
        assertEquals(listOf("5"), status.aroundDisruptions().map { it.id })
        assertEquals(setOf("4", "5"), status.disruptions().map { it.id }.toSet())
    }
}

/** When the disruption watch runs at all, and how often (see watchSchedule). */
class WatchScheduleTest {
    @Test
    fun nothingRunsWhileNothingIsWatched() {
        assertEquals(WatchSchedule.NONE, watchSchedule(0, checkPlaces = false, places = 3, periodMinutes = 30, times = listOf(420)))
        // Places on, but no place set: nothing to look at either.
        assertEquals(WatchSchedule.NONE, watchSchedule(0, checkPlaces = true, places = 0, periodMinutes = 30, times = listOf(420)))
        org.junit.Assert.assertFalse(WatchSchedule.NONE.runs)
    }

    @Test
    fun aBelledRouteRunsTheJobAndTheTimes() {
        assertEquals(WatchSchedule(30, listOf(420, 1080)), watchSchedule(1, false, 0, 30, listOf(1080, 420, 420)))
    }

    @Test
    fun placesAloneAreEnough() {
        assertEquals(WatchSchedule(15, listOf(420)), watchSchedule(0, true, 2, 15, listOf(420)))
    }

    @Test
    fun theJobCanBeOffAndTheTimesStay() {
        val plan = watchSchedule(2, false, 0, 0, listOf(420))
        assertEquals(WatchSchedule(null, listOf(420)), plan)
        org.junit.Assert.assertTrue(plan.runs)
    }

    @Test
    fun anUnknownIntervalIsTheDefault() {
        assertEquals(DEFAULT_PERIOD_MINUTES, watchPeriod(7))
        assertEquals(60, watchPeriod(60))
        assertEquals(WatchSchedule(DEFAULT_PERIOD_MINUTES, emptyList()), watchSchedule(1, false, 0, 45, emptyList()))
    }
}
