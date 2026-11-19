package io.github.jnbp.nextstop.wear.data

import android.util.Log
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import io.github.jnbp.nextstop.wear.complication.Complications
import io.github.jnbp.nextstop.wear.complication.NextComplicationService
import io.github.jnbp.nextstop.wear.tile.FavoriteTileService
import io.github.jnbp.nextstop.wear.tile.NextTileService
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Receives what the phone writes, also while the app on the watch is closed: stores it,
 * updates the tiles and the complications, and vibrates once per ride when the phone says the
 * next stop is the one to get off.
 *
 * The settings of a batch are taken first, so a trip that comes with "open the companion"
 * switched on in the same batch already sees the setting. Each item is taken on its own: one
 * that fails does not take the others (or the process) with it.
 */
class WatchDataService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        // The map pictures go to the open map only (MapImages listens for them); nothing to store.
        if (events.all { it.dataItem.uri.path?.startsWith(Protocol.PATH_MAP_IMAGE) == true }) return
        WatchStore.load(this)
        // Copied out of the buffer, which is released after this call: path, node and text.
        val items = events.mapNotNull { event ->
            if (event.type != DataEvent.TYPE_CHANGED) return@mapNotNull null
            val item = event.dataItem
            val path = item.uri.path ?: return@mapNotNull null
            if (path.startsWith(Protocol.PATH_MAP_IMAGE)) return@mapNotNull null
            val json = try {
                DataMapItem.fromDataItem(item).dataMap.getString(Protocol.KEY_JSON)
            } catch (e: RuntimeException) {
                null
            } ?: return@mapNotNull null
            Triple(path, item.uri.host, json)
        }.sortedBy { (path, _, _) -> if (path == Protocol.PATH_WATCH_SETTINGS) 0 else 1 }
        var changed = false
        for ((path, node, json) in items) {
            try {
                if (take(path, node, json)) changed = true
            } catch (e: RuntimeException) {
                Log.w(LOG_TAG, "data item $path not taken: ${e.javaClass.simpleName}")
            }
        }
        if (changed) {
            NextTileService.requestUpdate(this)
            NextComplicationService.requestUpdate(this)
        }
    }

    // Takes one item of the phone; true when the trip or the places changed.
    private fun take(path: String, node: String?, json: String): Boolean {
        when (path) {
            Protocol.PATH_PLACES -> {
                WatchStore.savePlaces(this, json)
                // Favourite stop and home may be others now.
                Complications.onPlacesChanged(this)
                return true
            }
            Protocol.PATH_WATCH_SETTINGS -> {
                // The phone edited the watch's settings: the tile "To a favourite" may have a new destination.
                if (WatchStore.takePhoneSettings(this, json)) {
                    FavoriteTileService.requestUpdate(this)
                    Complications.onHomeChanged(this)
                }
            }
            // The wake-up alarm of the phone rings (again) or stopped.
            Protocol.PATH_WAKE -> WakeRing.take(this, json)
            Protocol.PATH_TRIP -> {
                val snapshot = TripSnapshot.parse(json).copy(node = node)
                val before = WatchStore.trip.value
                val changed = WatchStore.takePhoneTrip(this, snapshot)
                // Only for a trip of the phone the watch took (not a stale or an ended one).
                val taken = WatchStore.trip.value == snapshot
                // A trip just started on the phone: the companion opens by itself, if so chosen.
                if (taken) CompanionAutoOpen.onPhoneTrip(this, before, snapshot)
                if (taken && snapshot.alert && WatchStore.takeAlert(this, snapshot.alertKey)) {
                    alertGetOff(this, snapshot.place, snapshot.change, snapshot.alertKey)
                }
                // The alert of a ride the trip has moved on from goes (#183).
                clearOutlivedAlert(this, WatchStore.trip.value)
                // The map pictures of its walk and next change, before their card is shown.
                if (taken) TripMapPrefetch.onTrip(this, snapshot)
                return changed
            }
        }
        return false
    }

    /** The test of the phone's developer options: open the companion now (see [Protocol.MSG_COMPANION_TEST]). */
    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != Protocol.MSG_COMPANION_TEST) return
        WatchStore.load(this)
        CompanionAutoOpen.test(this)
    }

    /**
     * The phone with Next Stop came back in reach (or went). Only while a trip of the phone is
     * shown: it is asked to write the trip again at once, so the watch need not wait for the
     * next state (and does not take the trip as stale meanwhile). Without a trip nothing is
     * sent: a trip started meanwhile comes with the Data Layer by itself.
     */
    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        if (capabilityInfo.name != Protocol.CAPABILITY_PHONE || capabilityInfo.nodes.isEmpty()) return
        WatchStore.load(this)
        val shown = WatchStore.trip.value
        if (!shown.active || !shown.fromPhone) return
        runBlocking {
            withTimeoutOrNull(SYNC_TIMEOUT_MILLIS) {
                WatchStore.sendToPhone(applicationContext, Protocol.MSG_SYNC, ByteArray(0))
            }
        }
    }

    private companion object {
        const val SYNC_TIMEOUT_MILLIS = 10_000L
    }
}
