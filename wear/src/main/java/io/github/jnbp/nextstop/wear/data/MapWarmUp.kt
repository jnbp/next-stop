package io.github.jnbp.nextstop.wear.data

import android.content.Context
import android.os.SystemClock
import android.util.Log
import kotlin.coroutines.cancellation.CancellationException
import org.json.JSONObject

/*
 * The warm-up of the phone's map pictures: when the watch app comes to the front it tells the
 * phone ([Protocol.MSG_WARM_UP]) where the watch is and in which style and pixel ratio it draws
 * maps. The phone then sets up MapLibre, loads the style and the tiles around and keeps its
 * snapshotter warm, so the first map picture takes about a second instead of a cold start.
 * At most once per [MIN_GAP_MILLIS] (a return to the app every few seconds must not keep the
 * phone busy); the phone limits it again on its side. An older phone app ignores the message.
 */
object MapWarmUp {
    // Elapsed realtime of the last warm-up sent (0: none yet in this process).
    @Volatile
    private var last = 0L

    /**
     * Whether a warm-up is due at [now] after the last one at [last] (both elapsed realtime, 0:
     * never). A clock that went back counts as long ago.
     */
    fun due(now: Long, last: Long): Boolean = last <= 0L || now < last || now - last >= MIN_GAP_MILLIS

    /** Sends the warm-up when it is due; quietly does nothing without a phone. */
    suspend fun maybeSend(context: Context) {
        val app = context.applicationContext
        val now = SystemClock.elapsedRealtime()
        if (!due(now, last)) return
        last = now
        try {
            // The last known place only: no fix is waited for, the phone's map is not worth a GPS start.
            val position = currentPosition(app, waitForFix = false)
            val payload = JSONObject()
                .put("dark", WatchStore.mapStyle.value.dark())
                .put("ratio", app.resources.displayMetrics.density.toDouble().coerceIn(1.0, 3.0))
            if (position != null) payload.put("lat", position.latitude).put("lon", position.longitude)
            val sent = WatchStore.sendToPhone(app, Protocol.MSG_WARM_UP, payload.toString().toByteArray(), single = true)
            Log.i(MapImages.TAG, "watch: warm-up ${if (sent) "sent" else "not sent (no phone)"} position=${if (position != null) "known" else "none"}")
            // Without a phone the next return to the app tries again.
            if (!sent) last = 0L
        } catch (e: CancellationException) {
            last = 0L
            throw e
        } catch (e: Exception) {
            Log.w(MapImages.TAG, "watch: warm-up failed: $e")
        }
    }

    // A warm-up at most this often (milliseconds): longer than the phone keeps its snapshotter
    // warm (3 min), shorter than a typical gap between two looks at a map.
    const val MIN_GAP_MILLIS = 5 * 60_000L
}
