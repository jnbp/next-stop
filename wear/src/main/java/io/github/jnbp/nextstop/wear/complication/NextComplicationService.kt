package io.github.jnbp.nextstop.wear.complication

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import kotlin.coroutines.cancellation.CancellationException
import io.github.jnbp.nextstop.wear.R
import io.github.jnbp.nextstop.wear.data.BvgClient
import io.github.jnbp.nextstop.wear.data.DepartureCache
import io.github.jnbp.nextstop.wear.data.StepKind
import io.github.jnbp.nextstop.wear.data.WatchStore
import io.github.jnbp.nextstop.wear.data.minutesUntil
import io.github.jnbp.nextstop.wear.ui.MainActivity

/**
 * A complication for watch faces (SHORT_TEXT): minutes and line of the next step of the
 * trip, else of the next departure at the first favourite stop. The system asks every five
 * minutes (see the manifest); the phone and the watch's own trip ask more often while a trip runs.
 */
class NextComplicationService : SuspendingComplicationDataSourceService() {
    private val client = BvgClient()

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        if (type == ComplicationType.SHORT_TEXT) shortText("3'", "U7", "U7 in 3 min") else null

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        if (request.complicationType != ComplicationType.SHORT_TEXT) return null
        WatchStore.load(this)
        val trip = WatchStore.trip.value
        if (trip.active && trip.kind != StepKind.Arrived) {
            val minutes = minutesUntil(trip.time)
            val title = when (trip.kind) {
                // Before the walk to the first ride: leaving, not the ride.
                StepKind.Board -> if (trip.leave) "🚶" else trip.line ?: "–"
                StepKind.GetOff -> trip.line ?: "–"
                else -> "🚶"
            }
            return shortText("$minutes'", title, "$title $minutes min ${trip.place}")
        }
        val stop = WatchStore.tileStop() ?: return shortText("–", "–", getString(R.string.tile_empty))
        val loaded = try {
            client.departures(stop.id, results = 4).also { DepartureCache.put(this, stop.id, it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Offline: the departures loaded last, those not gone yet.
            DepartureCache.get(this, stop.id)?.departures
        }
        val next = loaded?.firstOrNull { !it.cancelled } ?: return shortText("–", stop.name.take(4), stop.name)
        val minutes = next.time?.let { minutesUntil(it) } ?: 0
        return shortText("$minutes'", next.line, "${next.line} ${next.direction} $minutes min")
    }

    private fun shortText(text: String, title: String, description: String): ShortTextComplicationData {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return ShortTextComplicationData.Builder(
            PlainComplicationText.Builder(text).build(),
            PlainComplicationText.Builder(description).build(),
        )
            .setTitle(PlainComplicationText.Builder(title).build())
            .setTapAction(open)
            .build()
    }

    companion object {
        /** Asks every watch face that shows this complication for fresh data. */
        fun requestUpdate(context: Context) {
            try {
                ComplicationDataSourceUpdateRequester
                    .create(context, ComponentName(context, NextComplicationService::class.java))
                    .requestUpdateAll()
            } catch (e: Exception) {
                // Nothing shows it: nothing to update.
            }
        }
    }
}
