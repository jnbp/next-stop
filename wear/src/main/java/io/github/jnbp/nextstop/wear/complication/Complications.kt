package io.github.jnbp.nextstop.wear.complication

import android.app.PendingIntent
import android.os.Build
import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import io.github.jnbp.nextstop.wear.ui.MainActivity
import io.github.jnbp.nextstop.wear.ui.OpenRequest

/*
 * The complications of Next Stop and when they are drawn anew. None of them polls: each asks
 * the system at most every UPDATE_PERIOD_SECONDS of its manifest entry (none for the quick
 * start), shows a timeline that moves on by itself (see ComplicationSlides.kt), and is pushed
 * by the events that change what it shows:
 * - a new state of the trip (the phone's data item, the watch's own trip): automatic, companion;
 * - new favourites or places of the phone: automatic, favourite stop, home, disruptions;
 * - another destination of "home" (the tile target setting): home;
 * - another nearest stop found by the start page (NearestStop.remember): automatic, nearby, disruptions;
 * - its fresh board of the same stop, at most every five minutes while the page is open: automatic, nearby.
 */

/** The complications, each with its service (the name in the manifest). */
enum class ComplicationKind(val service: Class<out ComplicationDataSourceService>) {
    /** Departures of the nearest stop, the companion while a trip runs (the first complication of the app). */
    Auto(NextComplicationService::class.java),

    /** The next departure at the favourite stop. */
    Favorite(FavoriteStopComplicationService::class.java),

    /** The next departure at the nearest stop. */
    Nearby(NearbyComplicationService::class.java),

    /** The ring of the running trip. */
    Trip(CompanionComplicationService::class.java),

    /** The next connection home. */
    Home(HomeComplicationService::class.java),

    /** The disruptions at the favourite (else the nearest) stop. */
    Disruptions(DisruptionsComplicationService::class.java),

    /** Opens the route search. */
    QuickStart(QuickStartComplicationService::class.java),
}

object Complications {
    /** Asks every watch face that shows one of [kinds] for fresh data; nothing happens for those not shown. */
    fun requestUpdate(context: Context, vararg kinds: ComplicationKind) {
        for (kind in kinds) {
            try {
                ComplicationDataSourceUpdateRequester
                    .create(context, ComponentName(context, kind.service))
                    .requestUpdateAll()
            } catch (e: Exception) {
                // No complication host on this watch: nothing to update.
            }
        }
    }

    /** The trip changed (started, a new step, ended). */
    fun onTripChanged(context: Context) = requestUpdate(context, ComplicationKind.Auto, ComplicationKind.Trip)

    /** The places of the phone changed: the favourite stop or home may be another. */
    fun onPlacesChanged(context: Context) = requestUpdate(
        context,
        ComplicationKind.Auto,
        ComplicationKind.Favorite,
        ComplicationKind.Home,
        ComplicationKind.Disruptions,
    )

    /** The destination of "home" was chosen anew. */
    fun onHomeChanged(context: Context) = requestUpdate(context, ComplicationKind.Home)

    /** The start page found another nearest stop (see NearestStop.remember). */
    fun onNearestStopChanged(context: Context) = requestUpdate(
        context,
        ComplicationKind.Auto,
        ComplicationKind.Nearby,
        ComplicationKind.Disruptions,
    )

    /**
     * The start page loaded the board of the same nearest stop again (see NearestStop.remember):
     * only the complications that take that board from DepartureCache, without a request of
     * their own. The disruptions would load anew each time; they follow a new stop only.
     */
    fun onNearestBoardLoaded(context: Context) = requestUpdate(context, ComplicationKind.Auto, ComplicationKind.Nearby)

    /**
     * The tap action of a complication of [kind] that opens [request] (the app's start page for
     * null). One request code per kind: a PendingIntent is told apart by its request code, not
     * by its extras, so two kinds never overwrite each other's target.
     */
    fun tap(context: Context, kind: ComplicationKind, request: OpenRequest?): PendingIntent {
        val intent = if (request != null) {
            OpenRequest.intent(context, request)
        } else {
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        // Since Android 15 a PendingIntent's creator no longer lets it start an activity from the
        // background by default; the tap on the face must open the app (#270), so it says so.
        val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ActivityOptions.makeBasic()
                .setPendingIntentCreatorBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                .toBundle()
        } else {
            null
        }
        return PendingIntent.getActivity(
            context,
            TAP_REQUEST_BASE + kind.ordinal,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            options,
        )
    }

    // Request codes of the taps, apart from those of the notifications (TripOngoing, CompanionAutoOpen).
    private const val TAP_REQUEST_BASE = 100
}
