package io.github.jnbp.nextstop.wear

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.tasks.await

/** Whether a Wear OS watch is connected to this phone, and whether Next Stop is on it. */
enum class WatchState {
    /** No watch connected, or no Wear OS / Google Play services on this phone. */
    None,

    /** A watch is connected, but it does not declare Next Stop (not installed, or an older version). */
    NoApp,

    /** A watch with Next Stop is connected. */
    Connected,
}

/** The [state] of the watch and, with one connected, its node id and name. */
data class WatchStatus(val state: WatchState, val nodeId: String? = null, val name: String? = null)

/**
 * The watch as the setup and the settings show it. The app on the watch declares the
 * capability [CAPABILITY_WATCH] (wear/src/main/res/values/wear.xml). Every call tolerates a
 * phone without Google Play services: it then counts as "no watch".
 */
object WatchLink {
    const val CAPABILITY_WATCH = "nextstop_watch"
    private const val TAG = "NextStopWatchLink"

    suspend fun check(context: Context): WatchStatus {
        val nodes = try {
            Wearable.getNodeClient(context).connectedNodes.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "no connected nodes: $e")
            emptyList()
        }
        if (nodes.isEmpty()) return WatchStatus(WatchState.None)
        val capable = try {
            Wearable.getCapabilityClient(context)
                .getCapability(CAPABILITY_WATCH, CapabilityClient.FILTER_REACHABLE)
                .await()
                .nodes
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "capability check failed: $e")
            emptySet()
        }
        capable.firstOrNull()?.let { return WatchStatus(WatchState.Connected, it.id, it.displayName) }
        val node = nodes.first()
        return WatchStatus(WatchState.NoApp, node.id, node.displayName)
    }

    /**
     * Opens the Play Store page of Next Stop on the watch [nodeId] (all watches with null).
     * False when that could not even be sent. The listing does not exist yet: until then
     * the store shows that the app is not found.
     */
    fun installOnWatch(context: Context, nodeId: String?): Boolean {
        val intent = Intent(Intent.ACTION_VIEW)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setData(Uri.parse("market://details?id=${context.packageName}"))
        return try {
            RemoteActivityHelper(context).startRemoteActivity(intent, nodeId)
            true
        } catch (e: Exception) {
            Log.d(TAG, "cannot open the store on the watch: $e")
            false
        }
    }
}
