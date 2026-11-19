package io.github.jnbp.nextstop.wear.ui

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import io.github.jnbp.nextstop.wear.data.Place
import io.github.jnbp.nextstop.wear.data.parsePlaceJson
import io.github.jnbp.nextstop.wear.data.placeJson
import org.json.JSONObject

/** A page another part of the watch (the ongoing activity, a tile, a complication) asks the app to open. */
sealed interface OpenRequest {
    /** The followed trip. */
    data object Trip : OpenRequest

    /** The connections from here to [destination]. */
    data class Results(val destination: Place) : OpenRequest

    /** The page to choose the destination of the tile "To a favourite". */
    data object TileTarget : OpenRequest

    /** The departures of [stop] (a complication of departures). */
    data class Departures(val stop: Place) : OpenRequest

    /** The list of stops nearby (a complication that found no stop yet). */
    data object Nearby : OpenRequest

    /** The route search, to type or say a destination (the quick start complication). */
    data object Search : OpenRequest

    companion object {
        /** The request of the last intent, taken (set to null) by [WearApp] once it navigated. */
        val pending = MutableStateFlow<OpenRequest?>(null)

        private const val EXTRA_OPEN = "io.github.jnbp.nextstop.wear.OPEN"
        private const val EXTRA_PLACE = "io.github.jnbp.nextstop.wear.PLACE"

        /** The extras of an intent that opens [request] (also for the launch action of a tile). */
        fun extras(request: OpenRequest): Map<String, String> = when (request) {
            Trip -> mapOf(EXTRA_OPEN to "trip")
            TileTarget -> mapOf(EXTRA_OPEN to "tile-target")
            is Results -> mapOf(EXTRA_OPEN to "results", EXTRA_PLACE to placeJson(request.destination).toString())
            is Departures -> mapOf(EXTRA_OPEN to "departures", EXTRA_PLACE to placeJson(request.stop).toString())
            Nearby -> mapOf(EXTRA_OPEN to "nearby")
            Search -> mapOf(EXTRA_OPEN to "search")
        }

        /** An intent of [MainActivity] that opens [request]. */
        fun intent(context: Context, request: OpenRequest): Intent =
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .apply { extras(request).forEach { (key, value) -> putExtra(key, value) } }

        /** Reads the request of [intent] into [pending]; nothing for a plain start. */
        fun take(intent: Intent?) {
            if (intent == null) return
            val request = when (intent.getStringExtra(EXTRA_OPEN)) {
                "trip" -> Trip
                "tile-target" -> TileTarget
                "results" -> intent.getStringExtra(EXTRA_PLACE)
                    ?.let { runCatching { parsePlaceJson(JSONObject(it)) }.getOrNull() }
                    ?.let { Results(it) }
                "departures" -> intent.getStringExtra(EXTRA_PLACE)
                    ?.let { runCatching { parsePlaceJson(JSONObject(it)) }.getOrNull() }
                    ?.let { Departures(it) }
                "nearby" -> Nearby
                "search" -> Search
                else -> null
            } ?: return
            pending.value = request
        }
    }
}
