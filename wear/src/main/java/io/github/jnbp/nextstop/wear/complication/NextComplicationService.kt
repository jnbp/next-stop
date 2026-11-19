package io.github.jnbp.nextstop.wear.complication

import android.content.Context

/**
 * "Automatic": the departures of the nearest stop, and the companion by itself while a trip
 * runs. Its class name is the one of the first complication of the app (0.1), so watch faces
 * that show it keep it.
 */
class NextComplicationService : TransitComplicationService() {
    override suspend fun slides(): Slides = ComplicationSources.auto(this)

    override fun preview(): Slide = departurePreview("U2", "Pankow", "Alexanderplatz")

    companion object {
        /** A new state of the trip: the complications that show it are drawn anew (kept for the callers of 0.1). */
        fun requestUpdate(context: Context) = Complications.onTripChanged(context)
    }
}
