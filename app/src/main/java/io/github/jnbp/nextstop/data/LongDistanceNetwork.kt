package io.github.jnbp.nextstop.data

import android.content.Context
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

/**
 * The long-distance network for the map far out, where the tiles carry no railways (below zoom
 * 8): the ways of ICE, IC and EC trains, kept in the app's files and merged like the S- and
 * U-Bahn network (see mergeNetwork: a union, never smaller; a way no sample has found for 30
 * days drops out). It grows from two sources, both cheap:
 * - once a day at most, one request to the map of Transitous for the long-distance trains of all
 *   of Germany during the next WINDOW_MINUTES ([TransitousSource.longDistanceHops]): every hop of
 *   every ICE and IC running then, from stop to stop with its shape. A few samples on different
 *   days cover the main axes;
 * - the tracks of long-distance trains the app looks up anyway, for a ride whose provider only
 *   knows air lines (see RoutingSource.trackOf, [noteTrack]): no request of its own.
 * Data: Transitous, from the feeds of the operators (see TransitousSource.SOURCES_URL).
 */
object LongDistanceNetwork {
    private const val CACHE_FILE = "long-distance-1.json"

    // At most one sample of the whole country per day.
    private const val SAMPLE_MILLIS = 24L * 60 * 60 * 1000

    // The trains of this many minutes: enough for one hop of most lines, a small answer.
    private const val WINDOW_MINUTES = 20L

    // Germany with the edges of its neighbours.
    private const val NORTH = 55.1
    private const val SOUTH = 47.2
    private const val WEST = 5.8
    private const val EAST = 15.1

    // The zoom the sample asks at: that of the whole country, where MOTIS sends long-distance trains only.
    private const val SAMPLE_ZOOM = 6.0

    // Ways are simplified to this many metres: plenty for a map of the country or a state.
    private const val SIMPLIFY_METRES = 60.0

    private const val LOG_TAG = "LongDistance"

    // How often a running app looks whether a sample is due (and allowed).
    private const val SAMPLE_CHECK_MILLIS = 60L * 60 * 1000

    private val noted = ConcurrentLinkedQueue<NetworkLine>()
    private val notes = MutableStateFlow(0)

    /**
     * Notes the [track] of a train named [line] for the network, if it is a long-distance one
     * (see longDistanceTrackRank); it is merged in and kept by the running [load].
     */
    fun noteTrack(line: String?, track: List<Pair<Double, Double>>) {
        val rank = longDistanceTrackRank(line) ?: return
        // A way drawn from station to station (a feed without shapes, #280) is no railway to show.
        if (!lendsWay(track)) return
        noted.add(NetworkLine(longDistanceKey(rank, "t", track), simplify(track, SIMPLIFY_METRES)))
        notes.value = notes.value + 1
    }

    /**
     * Hands the kept network to [publish] at once (only the ways to draw, see longDistanceShown),
     * then, while it runs, takes a new sample whenever the last is a day old and [sampleAllowed]
     * (not while the battery saver is on), looking again every SAMPLE_CHECK_MILLIS, and merges in
     * the tracks noted while the app runs, handing the network over again after each change.
     * Runs until cancelled.
     */
    suspend fun load(
        context: Context,
        source: TransitousSource,
        sampleAllowed: () -> Boolean,
        publish: (List<NetworkLine>) -> Unit,
    ) {
        val file = File(context.filesDir, CACHE_FILE)
        // The sample and the noted tracks change the kept network one at a time.
        val lock = Mutex()
        var cache = withContext(Dispatchers.IO) { readCache(file) }
        cache?.lines?.takeIf { it.isNotEmpty() }?.let { publish(longDistanceShown(it)) }
        coroutineScope {
            launch {
                while (true) {
                    val now = System.currentTimeMillis()
                    val last = lock.withLock { cache }
                    // Asked again at every look: the battery saver may have come or gone meanwhile.
                    if (sampleAllowed() && (last == null || now - last.attempted > SAMPLE_MILLIS)) {
                        val fresh = sample(source)
                        lock.withLock {
                            val record = mergedCache(cache, fresh, stale = true, now = now)
                            cache = record
                            withContext(Dispatchers.IO) { writeCache(file, record) }
                            if (fresh.isNotEmpty()) publish(longDistanceShown(record.lines))
                        }
                    }
                    delay(SAMPLE_CHECK_MILLIS)
                }
            }
            notes.collect {
                val tracks = ArrayList<NetworkLine>()
                while (true) tracks.add(noted.poll() ?: break)
                if (tracks.isEmpty()) return@collect
                lock.withLock {
                    // Taken as found, without counting as a sample (the next one stays due as before).
                    val before = cache
                    val merged = mergeNetwork(before?.kept.orEmpty(), tracks, System.currentTimeMillis())
                    val record = NetworkCache(built = before?.built ?: 0L, attempted = before?.attempted ?: 0L, kept = merged)
                    cache = record
                    withContext(Dispatchers.IO) { writeCache(file, record) }
                    publish(longDistanceShown(record.lines))
                }
            }
        }
    }

    /** One sample of the long-distance trains of the whole country as lines; empty when it fails. */
    private suspend fun sample(source: TransitousSource): List<NetworkLine> {
        val fresh = try {
            val hops = source.longDistanceHops(NORTH, WEST, SOUTH, EAST, SAMPLE_ZOOM, WINDOW_MINUTES)
            withContext(Dispatchers.Default) { longDistanceLines(hops) { simplify(it, SIMPLIFY_METRES) } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DiagLog.w(LOG_TAG) { "sample: ${e.javaClass.simpleName}" }
            emptyList()
        }
        DiagLog.i(LOG_TAG) { "sample: ${fresh.size} ways" }
        return fresh
    }
}
