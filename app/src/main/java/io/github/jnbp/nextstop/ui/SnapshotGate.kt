package io.github.jnbp.nextstop.ui

import kotlinx.coroutines.sync.Mutex

/*
 * One still map picture (MapLibre's MapSnapshotter) at a time in the whole app: the small maps
 * of walks and disruptions (takeSnapshot in WalkMiniMap.kt), the maps of ways (WaysMap.kt) and
 * the pictures for the watch (wear/WatchMapImages.kt). Each snapshotter has a render thread and
 * a GL context of its own and shares the one file source (network and tile cache) with the live
 * map; several at once only compete for it. A picture waits for the one before, which ends
 * within its own time limit, so nothing waits for long.
 */
internal object SnapshotGate {
    /** Held while a snapshotter loads and renders; take it outside any time limit of the picture. */
    val lock = Mutex()
}
