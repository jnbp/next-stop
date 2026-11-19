package io.github.jnbp.nextstop.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.PlannedTrip

/**
 * What the cards of planned trips (on the start page and at the top of "My routes") ask of the
 * app: open the trip's journey on its own page, and "Undo" for a trip just cancelled. A plain
 * holder of two requests that AppRoot takes (see PlannedTripRequests there), so the cards need
 * no callbacks handed through the pages.
 */
object PlannedTripActions {
    /** The journey of a planned trip to open on its own page; null once AppRoot took it. */
    var toOpen by mutableStateOf<Journey?>(null)

    /** A planned trip just cancelled from a card, for "Undo"; null once AppRoot took it. */
    var cancelled by mutableStateOf<PlannedTrip?>(null)

    /** Opens [trip]: its journey on its own page, with its details. */
    fun open(trip: PlannedTrip) {
        toOpen = trip.journey
    }

    /** Cancels [trip] with its alarms and notification; AppRoot offers "Undo" for a few seconds. */
    fun cancel(context: Context, trip: PlannedTrip) {
        PlannedTripAlarms.cancel(context, trip.id)
        cancelled = trip
    }

    /** Takes the open request, if any. */
    fun takeOpen(): Journey? = toOpen?.also { toOpen = null }

    /** Takes the cancelled trip, if any. */
    fun takeCancelled(): PlannedTrip? = cancelled?.also { cancelled = null }
}
