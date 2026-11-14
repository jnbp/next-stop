package io.github.jnbp.nextstop.wear.data

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import io.github.jnbp.nextstop.wear.complication.NextComplicationService
import io.github.jnbp.nextstop.wear.tile.FavoriteTileService
import io.github.jnbp.nextstop.wear.tile.NextTileService

/**
 * Receives what the phone writes, also while the app on the watch is closed: stores it,
 * updates the tile and the complication, and vibrates once per ride when the phone says the
 * next stop is the one to get off.
 */
class WatchDataService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        // The map pictures go to the open map only (MapImages listens for them); nothing to store.
        if (events.all { it.dataItem.uri.path?.startsWith(Protocol.PATH_MAP_IMAGE) == true }) return
        WatchStore.load(this)
        var changed = false
        for (event in events) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            val item = event.dataItem
            val json = DataMapItem.fromDataItem(item).dataMap.getString(Protocol.KEY_JSON) ?: continue
            when (item.uri.path) {
                Protocol.PATH_PLACES -> {
                    WatchStore.savePlaces(this, json)
                    changed = true
                }
                Protocol.PATH_WATCH_SETTINGS -> {
                    // The phone edited the watch's settings: the tile "To a favourite" may have a new destination.
                    if (WatchStore.takePhoneSettings(this, json)) {
                        FavoriteTileService.requestUpdate(this)
                    }
                }
                Protocol.PATH_TRIP -> {
                    val snapshot = TripSnapshot.parse(json)
                    if (WatchStore.takePhoneTrip(this, snapshot)) changed = true
                    // Only for a trip of the phone the watch took (not a stale or an ended one).
                    val taken = WatchStore.trip.value == snapshot
                    if (taken && snapshot.alert && WatchStore.takeAlert(this, snapshot.alertKey)) {
                        alertGetOff(this, snapshot.place, snapshot.change)
                    }
                    // The map pictures of its walk and next change, before their card is shown.
                    if (taken) TripMapPrefetch.onTrip(this, snapshot)
                }
            }
        }
        if (changed) {
            NextTileService.requestUpdate(this)
            NextComplicationService.requestUpdate(this)
        }
    }
}
