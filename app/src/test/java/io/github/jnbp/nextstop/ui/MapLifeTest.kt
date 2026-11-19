package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The life of the map view (#284): rest before detach and destroy, never started again once destroyed. */
class MapLifeTest {
    private fun steps(from: MapRunLevel, to: MapRunLevel): List<MapRunCall> {
        val calls = ArrayList<MapRunCall>()
        var at = from
        while (true) {
            val call = nextMapRunCall(at, to) ?: break
            calls.add(call)
            at = mapRunAfter(call)
            check(calls.size <= 4)
        }
        assertEquals(to, at)
        return calls
    }

    @Test
    fun upAndDownInTheOrderOfTheLifecycle() {
        assertEquals(listOf(MapRunCall.Start, MapRunCall.Resume), steps(MapRunLevel.Idle, MapRunLevel.Resumed))
        assertEquals(listOf(MapRunCall.Pause, MapRunCall.Stop), steps(MapRunLevel.Resumed, MapRunLevel.Idle))
        assertEquals(listOf(MapRunCall.Pause), steps(MapRunLevel.Resumed, MapRunLevel.Started))
        assertEquals(listOf(MapRunCall.Stop), steps(MapRunLevel.Started, MapRunLevel.Idle))
        assertNull(nextMapRunCall(MapRunLevel.Started, MapRunLevel.Started))
    }

    @Test
    fun aDestroyedOrHiddenMapRestsWhateverTheScreenDoes() {
        assertEquals(MapRunLevel.Idle, wantedMapRun(shown = true, destroyed = true, started = true, resumed = true))
        assertEquals(MapRunLevel.Idle, wantedMapRun(shown = false, destroyed = false, started = true, resumed = true))
        assertEquals(MapRunLevel.Idle, wantedMapRun(shown = true, destroyed = false, started = false, resumed = false))
        assertEquals(MapRunLevel.Started, wantedMapRun(shown = true, destroyed = false, started = true, resumed = false))
        assertEquals(MapRunLevel.Resumed, wantedMapRun(shown = true, destroyed = false, started = true, resumed = true))
    }

    @Test
    fun aReleasedMapIsNoLongerAlive() {
        val first = Any()
        val second = Any()
        assertFalse(MapAlive.alive(null))
        assertTrue(MapAlive.alive(first))
        MapAlive.release(first)
        assertFalse(MapAlive.alive(first))
        assertTrue(MapAlive.alive(second))
    }
}
