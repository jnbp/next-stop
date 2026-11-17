package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** An ICE that the regional HAFAS draws as air lines gets the tracks of the same run elsewhere. */
class TrackShapesTest {
    // Berlin Südkreuz, Lutherstadt Wittenberg Hbf, Bitterfeld, Leipzig Hbf.
    private val stops = listOf(52.4752 to 13.3654, 51.8676 to 12.6624, 51.6230 to 12.3168, 51.3455 to 12.3821)

    // A track through every hop: a point every two kilometres or so along a bend.
    private fun track(points: List<Pair<Double, Double>>): List<Pair<Double, Double>> =
        points.zipWithNext().flatMap { (a, b) ->
            val steps = 40
            (0 until steps).map { step ->
                val f = step.toDouble() / steps
                // A small bend sideways, so the track is no straight line.
                val bend = 0.01 * kotlin.math.sin(f * Math.PI)
                (a.first + (b.first - a.first) * f + bend) to (a.second + (b.second - a.second) * f)
            }
        } + listOf(points.last())

    @Test
    fun airLinesBetweenStationsAreFound() {
        // As the regional HAFAS sends an ICE: the stations' own points.
        assertEquals(setOf(0, 1, 2), airHops(stops, stops))
        assertEquals(setOf(0, 1, 2), airHops(emptyList(), stops))
        assertTrue(airHops(track(stops), stops).isEmpty())
        // Its own tracks to Wittenberg, then air lines.
        val mixed = track(stops.take(2)) + stops.drop(2)
        assertEquals(setOf(1, 2), airHops(mixed, stops))
        // A stop without a position joins the hop it lies in.
        assertEquals(setOf(0, 1, 2), airHops(stops, listOf(stops[0], null, stops[2], stops[3])))
        // Short hops of an S-Bahn are no air lines, however few points they have.
        val near = listOf(52.50 to 13.40, 52.51 to 13.41, 52.52 to 13.43)
        assertTrue(airHops(near, near).isEmpty())
    }

    @Test
    fun airHopsTakeTheTracksOfTheSameRun() {
        val tracks = track(stops)
        val spliced = spliceTrack(stops, stops, tracks)
        assertTrue(spliced.size > stops.size * 10)
        assertTrue(airHops(spliced, stops).isEmpty())
        // Only the air hops are taken: an own way stays where there is one.
        val mixed = track(stops.take(2)) + stops.drop(2)
        val joined = spliceTrack(mixed, stops, tracks)
        assertTrue(airHops(joined, stops).isEmpty())
        assertEquals(mixed.subList(0, 41), joined.subList(0, 41))
        // Without a way of its own the stations stand in for it.
        assertTrue(airHops(spliceTrack(emptyList(), stops, tracks), stops).isEmpty())
    }

    @Test
    fun aTrackThatMissesTheStationsOrIsAnAirLineItselfIsNotTaken() {
        // Another line's track, kilometres beside the stations.
        val elsewhere = track(stops.map { (it.first + 0.1) to it.second })
        assertEquals(stops, spliceTrack(stops, stops, elsewhere))
        // The other source has only air lines too.
        assertEquals(stops, spliceTrack(stops, stops, stops))
        // A real way is left as it is.
        val real = track(stops)
        assertEquals(real, spliceTrack(real, stops, track(stops.map { (it.first + 0.001) to it.second })))
    }

    @Test
    fun aTrainPlacedOnTheAirLineIsToldApart() {
        val wittenberg = stops[1]
        val bitterfeld = stops[2]
        val middle = ((wittenberg.first + bitterfeld.first) / 2) to ((wittenberg.second + bitterfeld.second) / 2)
        // The regional HAFAS has only the stations: a position on their straight line is a guess.
        assertTrue(onAirLine(middle, wittenberg, bitterfeld, listOf(wittenberg, bitterfeld)))
        assertTrue(onAirLine(middle, wittenberg, bitterfeld, stops))
        assertTrue(onAirLine(middle, wittenberg, bitterfeld, emptyList()))
        // A real track bends away from the straight line: the position is on it.
        val real = track(listOf(wittenberg, bitterfeld))
        assertFalse(onAirLine(middle, wittenberg, bitterfeld, real))
        // Off the straight line: on some track.
        assertFalse(onAirLine((middle.first + 0.01) to middle.second, wittenberg, bitterfeld, emptyList()))
        // Without a shape, just out of a station: a track leaves it along the line at first.
        val justOut = (wittenberg.first + (bitterfeld.first - wittenberg.first) * 0.01) to
            (wittenberg.second + (bitterfeld.second - wittenberg.second) * 0.01)
        assertFalse(onAirLine(justOut, wittenberg, bitterfeld, emptyList()))
        // A short hop of an S-Bahn is no air line.
        assertFalse(onAirLine(52.505 to 13.405, 52.50 to 13.40, 52.51 to 13.41, emptyList()))
        // Without both stations nothing is known.
        assertFalse(onAirLine(middle, null, bitterfeld, emptyList()))
    }

    @Test
    fun theSameRunIsFoundByLineAndPlannedTime() {
        val planned = OffsetDateTime.parse("2026-11-16T10:04:00+01:00")
        fun departure(line: String, at: String, direction: String = "München Hbf") = Departure(
            line = line,
            product = "express",
            direction = direction,
            time = OffsetDateTime.parse(at),
            plannedTime = OffsetDateTime.parse(at),
            delaySeconds = null,
            platform = null,
            cancelled = false,
            tripId = "t",
        )
        assertTrue(sameRun(departure("ICE 603", "2026-11-16T10:04:00+01:00"), "ICE 603", planned))
        assertTrue(sameRun(departure("ICE603", "2026-11-16T10:05:00+01:00"), "ICE 603", planned))
        assertTrue(sameRun(departure("RE 1", "2026-11-16T10:04:00+01:00"), "RE1", planned))
        assertFalse(sameRun(departure("ICE 1603", "2026-11-16T10:04:00+01:00"), "ICE 603", planned))
        assertFalse(sameRun(departure("ICE 603", "2026-11-16T10:20:00+01:00"), "ICE 603", planned))
        assertFalse(sameRun(departure("ICE 603", "2026-11-16T10:04:00+01:00"), null, planned))
        // Long-distance and regional trains of every kind and operator, however the sources write them.
        assertTrue(sameRun(departure("IC2433", "2026-11-16T10:04:00+01:00"), "IC 2433", planned))
        assertTrue(sameRun(departure("EC 171", "2026-11-16T10:04:00+01:00"), "EC171", planned))
        assertTrue(sameRun(departure("ECE 3", "2026-11-16T10:04:00+01:00"), "ECE3", planned))
        assertTrue(sameRun(departure("NJ 40490", "2026-11-16T10:04:00+01:00"), "NJ40490", planned))
        assertTrue(sameRun(departure("RJX 63", "2026-11-16T10:04:00+01:00"), "RJX63", planned))
        assertTrue(sameRun(departure("FLX 1234", "2026-11-16T10:04:00+01:00"), "FLX1234", planned))
        assertTrue(sameRun(departure("WB 80", "2026-11-16T10:04:00+01:00"), "WB80", planned))
        assertTrue(sameRun(departure("RE1", "2026-11-16T10:04:00+01:00"), "ODEG RE 1", planned))
        assertTrue(sameRun(departure("RB 26", "2026-11-16T10:04:00+01:00"), "NEB RB26", planned))
        assertTrue(sameRun(departure("MEX 13", "2026-11-16T10:04:00+01:00"), "MEX13", planned))
        assertTrue(sameRun(departure("IRE 3", "2026-11-16T10:04:00+01:00"), "IRE3", planned))
        assertTrue(sameRun(departure("S 2", "2026-11-16T10:04:00+01:00"), "S2", planned))
        assertFalse(sameRun(departure("RE 10", "2026-11-16T10:04:00+01:00"), "RE1", planned))
        assertFalse(sameRun(departure("RB 1", "2026-11-16T10:04:00+01:00"), "RE1", planned))
        assertFalse(sameRun(departure("EC 171", "2026-11-16T10:04:00+01:00"), "IC 171", planned))
        // A line number for a train number: the same kind within a minute towards the same town.
        assertEquals(1, runScore(departure("FLX10", "2026-11-16T10:04:30+01:00"), "FLX 1234", planned, "München Hbf"))
        assertEquals(0, runScore(departure("FLX10", "2026-11-16T10:04:30+01:00"), "FLX 1234", planned, "Köln Hbf"))
        assertEquals(0, runScore(departure("FLX10", "2026-11-16T10:06:00+01:00"), "FLX 1234", planned, "München Hbf"))
        assertEquals(0, runScore(departure("IC 26", "2026-11-16T10:04:00+01:00"), "FLX 1234", planned, "München Hbf"))
        assertEquals(3, runScore(departure("ICE 603", "2026-11-16T10:04:00+01:00"), "ICE 603", planned, "Köln Hbf"))
        // Two line numbers that differ are other lines, even of one kind towards one town.
        assertEquals(0, runScore(departure("RE 10", "2026-11-16T10:04:00+01:00", "Frankfurt (Oder)"), "RE1", planned, "Frankfurt (Oder)"))
        assertEquals(0, runScore(departure("S42", "2026-11-16T10:04:00+01:00", "Ring"), "S41", planned, "Ring"))
        assertEquals(0, runScore(departure("S42", "2026-11-16T10:04:00+01:00", "Berlin, Ring"), "S41", planned, "Ring"))
        // A leading "Berlin" is not the town: Berlin Hbf and Berlin Südkreuz are other directions.
        assertEquals(0, runScore(departure("FLX10", "2026-11-16T10:04:30+01:00", "Berlin Hbf"), "FLX 1234", planned, "Berlin Südkreuz"))
        assertEquals(1, runScore(departure("FLX10", "2026-11-16T10:04:30+01:00", "Berlin Hbf"), "FLX 1234", planned, "Berlin Hbf (tief)"))
        assertEquals(1, runScore(departure("FLX 1234", "2026-11-16T10:04:30+01:00"), "FLX10", planned, "München Hbf"))
        assertEquals("RE1", trainKey("ODEG RE 1"))
        assertEquals("IC2433", trainKey("IC 2433"))
        assertEquals(null, trainKey("M41"))
        assertEquals(null, trainKey("Bus 100"))
        assertTrue(runsOnRails("express") && runsOnRails("regional") && runsOnRails("suburban"))
        assertTrue(runsOnRails("", "ICE 603") && runsOnRails("", "erixx RB 47"))
        assertFalse(runsOnRails("", "M41") || runsOnRails("bus", "Bus 100"))
        assertFalse(runsOnRails("bus") || runsOnRails("tram") || runsOnRails("subway"))
    }

    @Test
    fun aTripIsLookedUpOnceAndAMissIsAskedAgainLater() {
        TrackCache.clear()
        assertEquals(null, TrackCache.get("t1", now = 0L))
        TrackCache.put("t1", stops, now = 0L)
        assertEquals(stops, TrackCache.get("t1", now = 10L * 60 * 60 * 1000))
        TrackCache.put("t2", emptyList(), now = 0L)
        assertEquals(emptyList<Pair<Double, Double>>(), TrackCache.get("t2", now = 60_000L))
        assertEquals(null, TrackCache.get("t2", now = 31L * 60 * 1000))
        assertTrue(TrackCache.claim("t3"))
        assertFalse(TrackCache.claim("t3"))
        assertTrue(TrackCache.isClaimed("t3"))
        TrackCache.release("t3")
        assertFalse(TrackCache.isClaimed("t3"))
        assertTrue(TrackCache.claim("t3"))
        // A failed lookup backs off for three minutes only and never hides a known track.
        TrackCache.putFailure("t4", now = 0L)
        assertEquals(emptyList<Pair<Double, Double>>(), TrackCache.get("t4", now = 2L * 60 * 1000))
        assertEquals(null, TrackCache.get("t4", now = 4L * 60 * 1000))
        TrackCache.putFailure("t1", now = 0L)
        assertEquals(stops, TrackCache.get("t1", now = 60_000L))
        TrackCache.clear()
    }
}
