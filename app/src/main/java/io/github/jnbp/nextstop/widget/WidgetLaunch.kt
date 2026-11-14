package io.github.jnbp.nextstop.widget

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import io.github.jnbp.nextstop.data.Stop

/** "Start" on a widget: the journey from [from] to [to] with [key] that leaves at [departure]. */
data class WidgetTripRequest(val from: Stop, val to: Stop, val key: String, val departure: Long)

/**
 * A tap on an appointment: the journey search to [to] (or to what [location] resolves to
 * when it was not found yet), to arrive by [arrival].
 */
data class WidgetSearchRequest(val to: Stop?, val location: String, val arrival: Long)

/**
 * What a tap on a widget asks of the app, handed from the activity to the page. Each is
 * cleared once the page took it.
 */
object WidgetLaunch {
    /** Open the departures of this stop. */
    val openStop = MutableStateFlow<Stop?>(null)

    /** Start the trip companion for this connection. */
    val startTrip = MutableStateFlow<WidgetTripRequest?>(null)

    /** Open the journey search to an appointment. */
    val searchTo = MutableStateFlow<WidgetSearchRequest?>(null)

    /** Takes what [intent] of a widget asks for; false when it came from elsewhere. */
    fun follow(intent: Intent?): Boolean {
        when (intent?.action) {
            ACTION_WIDGET_STOP -> {
                val stop = intent.getStringExtra(EXTRA_STOP)?.let { placeFromText(it) } ?: return false
                openStop.value = stop
                return true
            }
            ACTION_WIDGET_START -> {
                val from = intent.getStringExtra(EXTRA_FROM)?.let { placeFromText(it) } ?: return false
                val to = intent.getStringExtra(EXTRA_TO)?.let { placeFromText(it) } ?: return false
                startTrip.value = WidgetTripRequest(
                    from = from,
                    to = to,
                    key = intent.getStringExtra(EXTRA_KEY).orEmpty(),
                    departure = intent.getLongExtra(EXTRA_DEPARTURE, 0L),
                )
                return true
            }
            ACTION_WIDGET_SEARCH -> {
                val arrival = intent.getLongExtra(EXTRA_ARRIVAL, 0L)
                val to = intent.getStringExtra(EXTRA_TO)?.let { placeFromText(it) }
                val location = intent.getStringExtra(EXTRA_LOCATION).orEmpty()
                if (arrival <= 0L || (to == null && location.isBlank())) return false
                searchTo.value = WidgetSearchRequest(to = to, location = location, arrival = arrival)
                return true
            }
        }
        return false
    }
}
