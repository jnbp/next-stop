package io.github.jnbp.nextstop.wear.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotLiveTest {
    @Test
    fun liveFlagsAndTimeAreReadAndKept() {
        val text = """{"active":true,"kind":"GetOff","realtime":true,"liveAt":1795000000000,
            "plan":[{"line":"S7","product":"suburban","from":"A","to":"B","dep":1,"arr":2,"live":true},
                    {"line":"100","product":"bus","from":"B","to":"C","dep":3,"arr":4}]}"""
        val trip = TripSnapshot.parse(text)
        assertTrue(trip.realtime)
        assertEquals(1795000000000L, trip.liveAt)
        assertTrue(trip.plan[0].live)
        assertFalse(trip.plan[1].live)
        val again = TripSnapshot.parse(trip.toJson())
        assertTrue(again.realtime)
        assertEquals(trip.liveAt, again.liveAt)
        assertEquals(listOf(true, false), again.plan.map { it.live })
    }

    @Test
    fun anOlderPhoneSendsNone() {
        val trip = TripSnapshot.parse("""{"active":true,"kind":"Board"}""")
        assertFalse(trip.realtime)
        assertEquals(0L, trip.liveAt)
    }

    @Test
    fun anEstimatedTimeIsReadAndKept() {
        // #277: the phone's estimate from the position comes as "estimated"; older phones send none.
        val trip = TripSnapshot.parse("""{"active":true,"kind":"GetOff","time":5,"estimated":true}""")
        assertTrue(trip.estimated)
        assertTrue(TripSnapshot.parse(trip.toJson()).estimated)
        assertFalse(TripSnapshot.parse("""{"active":true,"kind":"GetOff"}""").estimated)
    }
}
