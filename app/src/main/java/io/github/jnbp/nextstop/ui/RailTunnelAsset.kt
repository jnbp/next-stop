package io.github.jnbp.nextstop.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import io.github.jnbp.nextstop.data.DiagLog
import io.github.jnbp.nextstop.data.RAIL_TUNNELS_ASSET
import io.github.jnbp.nextstop.data.RailTunnels
import io.github.jnbp.nextstop.data.parseTunnelGrid
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/*
 * The rail tunnels of Germany (#219, see RailTunnels) for the hint of the sunny side: the asset
 * RAIL_TUNNELS_ASSET (about 135 kB) is read once per process, always off the main thread,
 * started when the app comes to the front (MainActivity) or the first hint asks for it. Until it
 * is read no hint shows on rails inside its box (see sunWaitsForTunnels), and [ready] tells the
 * ride cards to work their hint out again. Without the asset every ride counts as above ground.
 */
object RailTunnelAsset {
    /** True once the tunnels are read (or reading failed); observable, so a ride card recomposes. */
    var ready by mutableStateOf(RailTunnels.read)
        private set

    private val started = AtomicBoolean(false)
    private val lock = Mutex()

    /** Starts reading the asset in the background, once; cheap to call again. */
    fun warm(context: Context) {
        if (RailTunnels.read || !started.compareAndSet(false, true)) return
        val app = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { ensure(app) }
    }

    /** Reads the asset on an IO thread if that is not done yet, and returns when it is (for the watch's state). */
    suspend fun ensure(context: Context) {
        if (!RailTunnels.read) {
            withContext(Dispatchers.IO) {
                lock.withLock {
                    if (!RailTunnels.read) {
                        RailTunnels.grid = readGrid(context.applicationContext)
                        RailTunnels.read = true
                    }
                }
            }
        }
        if (!ready) Snapshot.withMutableSnapshot { ready = true }
    }

    private fun readGrid(context: Context) = try {
        context.assets.open(RAIL_TUNNELS_ASSET).bufferedReader().use { parseTunnelGrid(it.readText()) }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        // Unreadable or unexpected: every ride counts as above ground, as without the asset.
        DiagLog.w("SunSide", e) { "rail tunnels" }
        null
    }
}
