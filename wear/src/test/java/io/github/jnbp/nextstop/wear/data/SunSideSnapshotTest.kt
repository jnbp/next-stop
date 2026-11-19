package io.github.jnbp.nextstop.wear.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The sunny side the phone sends for the ride to board (#176). */
class SunSideSnapshotTest {
    @Test
    fun theSideIsReadAndKept() {
        val left = TripSnapshot.parse("""{"active":true,"kind":"Board","sunSide":"Left"}""")
        assertEquals(true, left.sunLeft)
        assertEquals(true, TripSnapshot.parse(left.toJson()).sunLeft)
        val right = TripSnapshot.parse("""{"active":true,"kind":"Board","sunSide":"Right"}""")
        assertEquals(false, right.sunLeft)
        assertEquals(false, TripSnapshot.parse(right.toJson()).sunLeft)
    }

    @Test
    fun noOrAnUnknownSideShowsNothing() {
        assertNull(TripSnapshot.parse("""{"active":true,"kind":"Board"}""").sunLeft)
        assertNull(TripSnapshot.parse("""{"active":true,"kind":"Board","sunSide":"Ahead"}""").sunLeft)
        assertNull(TripSnapshot.parse("""{"active":true,"kind":"Board","sunSide":null}""").sunLeft)
        assertNull(TripSnapshot.parse(TripSnapshot(active = true).toJson()).sunLeft)
    }
}
