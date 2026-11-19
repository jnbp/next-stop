package io.github.jnbp.nextstop.ui

import java.util.Collections
import java.util.WeakHashMap

/*
 * The life of the map view and of its map (#284). Pure logic, no Android.
 *
 * MapLibre's surface view resets the native renderer on the main thread as it is detached from
 * the window, and only then stops its render thread. A render thread that still draws then
 * renders with a renderer that is gone (SIGSEGV in MapRenderer::render). The map view is
 * therefore always brought to rest (onPause, onStop: the render thread pauses and waits) before
 * it can be detached or destroyed, and never started again once destroyed. A map that is
 * released is no longer read either (projection, camera): see [MapAlive].
 */

/**
 * Where a mark over the map is placed (x and y, in pixels) once its map is released: off the
 * screen, for the frame until it is gone too.
 */
const val MAP_GONE_OFFSET = -100_000

/** How far a map view runs: started (onStart) and resumed (onResume), or neither. */
enum class MapRunLevel { Idle, Started, Resumed }

/** One lifecycle call of a map view. */
enum class MapRunCall { Start, Resume, Pause, Stop }

/** The one call that takes a map view from [from] a step towards [to]; null once it is there. */
fun nextMapRunCall(from: MapRunLevel, to: MapRunLevel): MapRunCall? = when {
    from == to -> null
    from < to -> if (from == MapRunLevel.Idle) MapRunCall.Start else MapRunCall.Resume
    else -> if (from == MapRunLevel.Resumed) MapRunCall.Pause else MapRunCall.Stop
}

/** Where a map view stands after [call]. */
fun mapRunAfter(call: MapRunCall): MapRunLevel = when (call) {
    MapRunCall.Start, MapRunCall.Pause -> MapRunLevel.Started
    MapRunCall.Resume -> MapRunLevel.Resumed
    MapRunCall.Stop -> MapRunLevel.Idle
}

/**
 * How far the map view should run: not at all while it is hidden ([shown] false), destroyed or
 * its screen is not started; resumed with a resumed screen.
 */
fun wantedMapRun(shown: Boolean, destroyed: Boolean, started: Boolean, resumed: Boolean): MapRunLevel = when {
    destroyed || !shown || !started -> MapRunLevel.Idle
    resumed -> MapRunLevel.Resumed
    else -> MapRunLevel.Started
}

/**
 * The maps that were released: their view is destroyed or about to be. Everything that holds a
 * map from before (an overlay drawn in the same frame, a loop, a camera listener) asks [alive]
 * before it reads the projection or the camera of it or changes its style: a destroyed map
 * answers with empty values, and its sources and layers are gone with it.
 */
object MapAlive {
    private val released: MutableSet<Any> = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<Any, Boolean>()))

    /** Marks [map] as released; from now on [alive] is false for it. */
    fun release(map: Any) {
        released.add(map)
    }

    /** True for a map that is there and was not released. */
    fun alive(map: Any?): Boolean = map != null && !released.contains(map)
}
