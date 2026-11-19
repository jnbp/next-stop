package io.github.jnbp.nextstop.wear.complication

import io.github.jnbp.nextstop.wear.data.Departure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The decisions of the complications: the automatic mode, the timeline of departures, short texts and disruptions. */
class ComplicationLogicTest {
    private val minute = 60_000L

    private fun departure(line: String, at: Long?, cancelled: Boolean = false, warnings: List<String> = emptyList()) =
        Departure(line, "subway", "Pankow", at, 0, null, cancelled, warnings = warnings)

    @Test
    fun automaticShowsTheCompanionOnlyWhileATripRuns() {
        assertEquals(AutoMode.Trip, autoMode(tripActive = true, arrived = false, stale = false))
        assertEquals(AutoMode.Departures, autoMode(tripActive = false, arrived = false, stale = false))
        assertEquals(AutoMode.Departures, autoMode(tripActive = true, arrived = true, stale = false))
        // A trip of the phone that stopped writing is not shown frozen.
        assertEquals(AutoMode.Departures, autoMode(tripActive = true, arrived = false, stale = true))
    }

    @Test
    fun theTimelineSkipsCancelledAndGoneRunsAndKeepsTheOrder() {
        val now = 100 * minute
        val list = listOf(
            departure("U2", now + 7 * minute),
            departure("U5", now - 5 * minute),
            departure("U8", now + 2 * minute, cancelled = true),
            departure("S5", null),
            departure("U1", now + 3 * minute),
            // Leaving this very moment: still on the face for a few seconds.
            departure("U6", now - 10_000L),
        )
        assertEquals(listOf("U6", "U1", "U2"), timelineDepartures(list, now).map { it.line })
        assertEquals(listOf("U6"), timelineDepartures(list, now, max = 1).map { it.line })
    }

    @Test
    fun eachSlideStartsWhenTheOneBeforeItLeaves() {
        assertEquals(listOf(Long.MIN_VALUE, 10L, 20L), slideStarts(listOf(10L, 20L, 30L)))
        assertEquals(emptyList<Long>(), slideStarts(emptyList()))
    }

    @Test
    fun shortLabelsDropWordsThenCut() {
        assertEquals("U2", shortLabel("U2"))
        assertEquals("Bus M41", shortLabel("Bus M41"))
        assertEquals("S Hbf", shortLabel("S Hbf Berlin"))
        assertEquals("Alexan…", shortLabel("Alexanderplatz"))
        assertEquals("S Hbf", shortLabel("  S   Hbf "))
        assertEquals("Haupt…", shortLabel("Hauptbahnhof", max = 6))
        assertEquals("", shortLabel("   "))
    }

    @Test
    fun lineChainsNameAtMostTwoLines() {
        assertEquals("U2›S41", lineChain(listOf("U2", "S41", "M10")))
        assertEquals("U2", lineChain(listOf("", "U2")))
        assertEquals("", lineChain(emptyList()))
    }

    @Test
    fun theRingFillsTowardsTheDeparture() {
        assertEquals(1f, departureRingValue(10 * minute, 10 * minute), 0.001f)
        assertEquals(0.5f, departureRingValue(25 * minute, 10 * minute), 0.001f)
        assertEquals(0f, departureRingValue(100 * minute, 10 * minute), 0.001f)
    }

    @Test
    fun disruptionsCountEachNoticeOnceAndTheCancelledRuns() {
        val summary = disruptionSummary(
            listOf(
                departure("U2", 1L, warnings = listOf("Construction work", "Lift out of service")),
                departure("U2", 2L, warnings = listOf("Construction work")),
                departure("U5", 3L, cancelled = true),
            ),
        )
        assertEquals(listOf("Construction work", "Lift out of service"), summary.notices)
        assertEquals(1, summary.cancelled)
        assertEquals(3, summary.count)
        assertTrue(disruptionSummary(listOf(departure("U2", 1L))).clear)
    }

    @Test
    fun aCountdownStepsOncePerMinuteAsTheAppCountsThem() {
        val target = 1_000 * minute
        val from = target - 3 * minute - 30_000L
        val segments = countdownSegments(from, target + 10 * minute, target)
        assertEquals(listOf(3L, 2L, 1L, 0L), segments.map { it.minutes })
        // Contiguous, from the start to the end asked for.
        assertEquals(from, segments.first().start)
        assertEquals(target + 10 * minute, segments.last().end)
        segments.zipWithNext().forEach { (a, b) -> assertEquals(a.end, b.start) }
        // Each change where the full minutes left change (just after the full minute).
        assertEquals(target - 3 * minute + 1, segments[0].end)
        assertEquals(target - minute + 1, segments[2].end)
        // Each stretch shows what the app shows at any moment inside it.
        segments.forEach { segment ->
            listOf(segment.start, segment.end - 1).forEach { at ->
                assertEquals(watchMinutesLeftForTest(target - at), segment.minutes)
            }
        }
    }

    @Test
    fun aCountdownShowsTheClockFromAnHourOn() {
        val target = 1_000 * minute
        val segments = countdownSegments(target - 62 * minute, target - 58 * minute + 1, target)
        assertEquals(listOf(null, 59L, 58L), segments.map { it.minutes })
        assertEquals(target - 60 * minute + 1, segments[0].end)
    }

    @Test
    fun aCountdownEndsWhereTheNextSlideStartsAndKeepsToItsLimit() {
        val target = 1_000 * minute
        // The next slide starts at the departure: "now" for the last minute, as in the app.
        val toDeparture = countdownSegments(target - 2 * minute - 30_000L, target, target)
        assertEquals(listOf(2L, 1L, 0L), toDeparture.map { it.minutes })
        assertEquals(target - minute + 1, toDeparture.last().start)
        val capped = countdownSegments(target - 50 * minute, target + minute, target, max = 3)
        assertEquals(3, capped.size)
        assertEquals(target + minute, capped.last().end)
        assertTrue(countdownSegments(target, target, target).isEmpty())
        // Already gone: "now" until the end.
        assertEquals(listOf(0L), countdownSegments(target + minute, target + 9 * minute, target).map { it.minutes })
    }

    @Test
    fun countdownLabelsFitAShortSlotWithoutAUnitThatGetsCut() {
        assertEquals("12'", countdownLabel(12, "14:32", "jetzt", "%d'"))
        assertEquals("Jetzt", countdownLabel(0, "14:32", "jetzt", "%d'"))
        assertEquals("14:32", countdownLabel(null, "14:32", "jetzt", "%d'"))
        (0L..59L).forEach { m -> assertTrue(countdownLabel(m, "14:32", "jetzt", "%d'").length <= SHORT_LABEL_MAX) }
    }

    @Test
    fun aShortSlotNeverGetsAnIconAndATitleTogether() {
        val app = 1
        val home = 2
        // A telling icon wins over the title.
        assertEquals(home to null, textSlotExtras(home, "U2›S41", app))
        // The app's own icon gives way to the title (the line), and stands alone without one.
        assertEquals(null to "U2", textSlotExtras(app, "U2", app))
        assertEquals(app to null, textSlotExtras(app, null, app))
        assertEquals(null to "M10", textSlotExtras(null, "M10", app))
        assertEquals(null to null, textSlotExtras<Int>(null, null, app))
    }

    @Test
    fun theTileAndThePreviewsCountTheFullMinutesAsTheApp() {
        val target = 1_000 * minute
        assertEquals(12L, countdownMinutes(target, target - 12 * minute))
        assertEquals(11L, countdownMinutes(target, target - 11 * minute - 31_000L))
        assertEquals(0L, countdownMinutes(target, target - 59_000L))
        // Gone: "now", never a negative number.
        assertEquals(0L, countdownMinutes(target, target + 5 * minute))
        assertEquals(59L, countdownMinutes(target, target - 59 * minute))
        assertEquals(null, countdownMinutes(target, target - 60 * minute))
    }

    private fun watchMinutesLeftForTest(left: Long): Long? =
        io.github.jnbp.nextstop.wear.data.watchMinutesLeft(left).takeIf { it < 60 }

    @Test
    fun theLastDepartureLeavesTheFaceOnceItHasGone() {
        val target = 1_000 * minute
        val start = target - 8 * minute
        // #235: with a slide for after it, the countdown ends with the run, not hours later as "now".
        assertEquals(target + GONE_GRACE_MILLIS, lastSlideEnd(start, target, expired = true))
        val segments = countdownSegments(start, lastSlideEnd(start, target, expired = true), target)
        assertEquals(0L, segments.last().minutes)
        assertEquals(target + GONE_GRACE_MILLIS, segments.last().end)
        // Already gone when the slide would start: it is left out (end not after start).
        assertEquals(target + 5 * minute, lastSlideEnd(target + 5 * minute, target, expired = true))
        // Without a slide for after it, or without a countdown, the last slide stays as before.
        assertEquals(start + LAST_SLIDE_MILLIS, lastSlideEnd(start, target, expired = false))
        assertEquals(start + LAST_SLIDE_MILLIS, lastSlideEnd(start, null, expired = true))
    }

    @Test
    fun aComplicationTakesTheBoardTheAppJustLoaded() {
        val now = 1_000 * minute
        assertTrue(keptIsFresh(now - 20_000L, now))
        assertTrue(!keptIsFresh(now - KEPT_FRESH_MILLIS, now))
        assertTrue(!keptIsFresh(now - 10 * minute, now))
        // Kept "in the future" (the clock was set back): not trusted.
        assertTrue(!keptIsFresh(now + minute, now))
    }

    @Test
    fun aShortSlotSaysNowAsZeroMinutesThatFitBesideTheLine() {
        // #235: "Jetzt" beside "204" was cut at the round edge; "0'" is as wide as the minutes.
        assertEquals("0'", slotCountdownLabel(0, "14:32", "%d'"))
        assertEquals("0'", slotCountdownLabel(-2, "14:32", "%d'"))
        assertEquals("7'", slotCountdownLabel(7, "14:32", "%d'"))
        assertEquals("14:32", slotCountdownLabel(null, "14:32", "%d'"))
        (0L..59L).forEach { m -> assertTrue(slotCountdownLabel(m, "14:32", "%d'").length <= SLOT_COUNTDOWN_MAX) }
        // The tile and the app keep the word.
        assertEquals("Jetzt", countdownLabel(0, "14:32", "jetzt", "%d'"))
    }

    @Test
    fun anEmptyBoardSaysWhyAndAFailureIsNeverNoDepartures() {
        assertEquals(EmptyBoard.NoneSoon, emptyBoard(loaded = true))
        // Only gone runs kept from earlier after a failed load: a failure, not "no departures".
        assertEquals(EmptyBoard.Failed, emptyBoard(loaded = false))
    }

    @Test
    fun theAutomaticComplicationTriesTheFavouriteWhereTheNearestHasNothing() {
        assertTrue(autoTriesFavorite(nearestHasRuns = false, nearestId = "900000001", favoriteId = "900000002"))
        assertTrue(!autoTriesFavorite(nearestHasRuns = true, nearestId = "900000001", favoriteId = "900000002"))
        // The favourite is the nearest stop, or there is none: nothing else to try.
        assertTrue(!autoTriesFavorite(nearestHasRuns = false, nearestId = "900000001", favoriteId = "900000001"))
        assertTrue(!autoTriesFavorite(nearestHasRuns = false, nearestId = "900000001", favoriteId = null))
    }

    @Test
    fun aStopNameInAShortSlotIsCompactNeverCutWithAnEllipsis() {
        // Fits as it is.
        assertEquals("Linde", slotStopName("Linde"))
        // The prefixes of a station and the street go first.
        assertEquals("Linde", slotStopName("S+U Linde"))
        assertEquals("Muster", slotStopName("Musterstraße"))
        assertEquals("Ulmen", slotStopName("U Ulmenstr."))
        assertEquals("Feld", slotStopName("Feld Str."))
        assertEquals("Eck Pl.", slotStopName("Eck Platz"))
        // A first word that fits as a whole keeps it, without a full stop.
        assertEquals("Eich", slotStopName("Eich Platz"))
        // Still too long: the first word cut to six letters and a full stop.
        assertEquals("Muster.", slotStopName("Musterweg"))
        assertEquals("Eichen.", slotStopName("U Eichenallee"))
        assertEquals("Linden.", slotStopName("Lindenplatz"))
        assertEquals("Beispi.", slotStopName("S Beispielbahnhof"))
        // A first word too short to tell the stop: no text, the icon alone.
        assertEquals("", slotStopName("Am Weiher Nord"))
        assertEquals("", slotStopName("   "))
        // Never longer than the slot, never an ellipsis.
        for (name in listOf("Musterweg", "S+U Beispielstadt Hauptbahnhof", "Am Weiher Nord", "Kleingartenkolonie Sonnenschein", "Bahnhof Waldheim", "Lindenplatz Ost")) {
            val shown = slotStopName(name)
            assertTrue("$name -> $shown", shown.length <= SHORT_LABEL_MAX && !shown.contains("…"))
        }
    }
}
