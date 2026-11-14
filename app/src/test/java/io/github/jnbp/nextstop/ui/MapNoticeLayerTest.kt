package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.Stop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The sign of disruptions sits on the upper right corner of its stop's sign, whatever its size. */
class MapNoticeLayerTest {
    @Test
    fun offsetFollowsTheSizeOfTheSign() {
        val h = noticeOffsetDp(signHalfWidthDp(1), 16f)
        val sur = noticeOffsetDp(signHalfWidthDp(3), 16f)
        val ringed = noticeOffsetDp(signHalfWidthDp(3, ring = true), 20.5f)
        // Right of the middle and above it, inside the sign's box so both overlap a little.
        assertTrue(h.first > 0f && h.first < signHalfWidthDp(1))
        assertTrue(h.second < 0f && h.second > -16f)
        // A wide station sign moves it right by the extra width; the ring moves it out and up.
        assertEquals(signHalfWidthDp(3) - signHalfWidthDp(1), sur.first - h.first, 0.01f)
        assertTrue(ringed.first > sur.first && ringed.second < sur.second)
        // At the end of a stop's green name.
        val name = noticeNameOffsetDp(40f, 7.5f)
        assertTrue(name.first > 30f && name.second < 0f)
        // The vehicles beside the sign start below it.
        assertTrue(noticeColumnTopDp(h.second) > h.second - 2f)
    }

    @Test
    fun theSignOfItsStopOrOfItsStation() {
        val station = NoticeSignSpot("900100003", "S+U Alexanderplatz (Bln)", 52.521536, 13.412089, 45f, 16f)
        val bus = NoticeSignSpot("900100031", "S+U Alexanderplatz (Bln) [Bus K.-Liebknecht-Str]", 52.521817, 13.408531, 16f, 16f)
        val spots = listOf(station, bus)
        // Its own sign by id.
        assertEquals(bus, noticeSpotFor(Stop("900100031", "x", 52.0, 13.0, null, setOf("bus")), spots))
        // A part of the station ("[U2]", 70 m off) on the station's sign; the bus stop is farther.
        val u2 = Stop("900100703", "S+U Alexanderplatz (Bln) [U2]", 52.5221, 13.4130, null, setOf("subway"))
        assertEquals(station, noticeSpotFor(u2, spots))
        // Nothing drawn near: none.
        assertNull(noticeSpotFor(Stop("1", "Elsewhere", 52.40, 13.20, null, setOf("bus")), spots))
    }
}
