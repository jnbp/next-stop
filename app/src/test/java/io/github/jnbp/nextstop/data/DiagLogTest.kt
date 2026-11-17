package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagLogTest {
    @Test
    fun ringKeepsTheLastEventsOldestFirst() {
        val ring = DiagRing(3)
        for (i in 1..5) ring.add(1_000L * i, 'I', "T", "event $i")
        val lines = ring.lines()
        assertEquals(3, lines.size)
        assertTrue(lines[0].endsWith("I/T: event 3"))
        assertTrue(lines[2].endsWith("I/T: event 5"))
        assertEquals(5L, ring.written)
    }

    @Test
    fun ringBelowCapacity() {
        val ring = DiagRing(4)
        ring.add(0L, 'W', "Tag", "one")
        ring.add(0L, 'E', "Tag", "two")
        assertEquals(listOf("W/Tag: one", "E/Tag: two"), ring.lines().map { it.substringAfter(' ').substringAfter(' ') })
        ring.clear()
        assertEquals(0, ring.lines().size)
    }

    @Test
    fun debugEventsAreNotBuiltWhenOff() {
        DiagLog.clear()
        DiagLog.verbose = false
        DiagLog.debugToLogcat = false
        var built = 0
        DiagLog.d("T") { built++; "debug" }
        assertEquals(0, built)
        assertEquals(0, DiagLog.size)
        DiagLog.verbose = true
        DiagLog.d("T") { built++; "debug" }
        assertEquals(1, built)
        assertEquals(1, DiagLog.size)
        DiagLog.verbose = false
        DiagLog.i("T") { "info" }
        DiagLog.w("T", IllegalStateException("boom")) { "warn" }
        val lines = DiagLog.lines()
        assertEquals(3, lines.size)
        assertTrue(lines[2].endsWith("W/T: warn: java.lang.IllegalStateException: boom"))
        DiagLog.clear()
    }

    @Test
    fun debugToLogcatAloneKeepsNothing() {
        DiagLog.clear()
        DiagLog.verbose = false
        DiagLog.debugToLogcat = true
        var built = 0
        DiagLog.d("T") { built++; "debug" }
        assertEquals(1, built)
        assertEquals(0, DiagLog.size)
        DiagLog.debugToLogcat = false
    }

    @Test
    fun multiLineMessagesAreIndented() {
        val ring = DiagRing(2)
        ring.add(0L, 'E', "Crash", "first\nsecond")
        assertTrue(ring.lines()[0].endsWith("E/Crash: first\n    second"))
    }
}
