package io.github.jnbp.nextstop.widget

import io.github.jnbp.nextstop.data.DiagLog
import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import java.time.OffsetDateTime
import kotlin.reflect.KClass
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.Leg
import io.github.jnbp.nextstop.data.PlaceIcon
import io.github.jnbp.nextstop.data.Stop
import io.github.jnbp.nextstop.ui.BatterySaver
import io.github.jnbp.nextstop.ui.TripState

// Data older than this is loaded again when a widget is drawn by the system.
private const val STALE_MILLIS = 15 * 60_000L

/**
 * The common part of all widgets of the app: their state (settings and the last data, see
 * [WidgetKeys]), drawn at the size they have. The minutes are worked out at every drawing from
 * the absolute times, so a redraw alone keeps them right.
 */
abstract class NextStopWidget(private val kind: WidgetKind) : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    // Drawn once per size step (see [responsiveSizes]); the launcher picks the step that fits.
    override val sizeMode: SizeMode = SizeMode.Responsive(responsiveSizes(kind))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        if (kind == WidgetKind.Trip || kind == WidgetKind.Auto) loadBoardingData(context)
        val state: Preferences = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val fetched = state[WidgetKeys.FETCHED_AT] ?: 0L
        // Drawn by the system with old data (after a restart, an update of the app, a new
        // launcher): loaded again, but only where it can be seen and not while the battery saver
        // is active (then only a tap or the app opening loads; battery rule).
        if (System.currentTimeMillis() - fetched > STALE_MILLIS && WidgetUpdates.screenOn(context) && !BatterySaver.active) {
            WidgetUpdates.requestRefresh(context, STALE_MILLIS)
        }
        provideContent {
            val current = currentState<Preferences>()
            // Read so that every redraw composes again, with the minutes of now.
            current[WidgetKeys.TICK]
            GlanceTheme {
                WidgetContent(
                    kind = kind,
                    config = readConfig(current),
                    snapshot = snapshotFromText(current[WidgetKeys.SNAPSHOT]),
                    now = System.currentTimeMillis(),
                    trip = TripState.journey.value,
                )
            }
        }
    }

    /**
     * The picture in the widget picker of Android 15 and later: the widget with sample data,
     * without taps. Only an addition to the static previewLayout, which every picker can show:
     * see [WidgetPreviews.publishOnce].
     */
    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val now = System.currentTimeMillis()
        val snapshot = WidgetPreviews.sample(kind, now)
        val trip = if (kind == WidgetKind.Trip) WidgetPreviews.sampleTrip() else null
        provideContent {
            CompositionLocalProvider(LocalWidgetPreview provides true) {
                GlanceTheme {
                    WidgetContent(
                        kind = kind,
                        config = WidgetConfig(scheme = WidgetScheme.NextStop),
                        snapshot = snapshot,
                        now = now,
                        trip = trip,
                    )
                }
            }
        }
    }
}

/**
 * The size steps a widget is drawn in. Glance draws the content once per step and the
 * launcher shows the largest step that fits, so these are lower bounds: 40 dp high is a
 * single row of cells, about 100 one of two, and so on. Kept under 16 per widget, the most
 * one RemoteViews can carry.
 */
internal fun responsiveSizes(kind: WidgetKind): Set<DpSize> = when (kind) {
    // Mini: the smallest form fits a cell of 40 dp; from 64 dp high the delay gets a line of its own.
    WidgetKind.Mini -> setOf(DpSize(40.dp, 40.dp), DpSize(40.dp, 64.dp), DpSize(100.dp, 40.dp), DpSize(100.dp, 64.dp))
    WidgetKind.Departures, WidgetKind.Auto -> buildSet {
        for (width in listOf(100.dp, WIDE_WIDGET)) {
            for (height in listOf(40.dp, 100.dp, 130.dp, 160.dp, 220.dp, 300.dp)) add(DpSize(width, height))
        }
    }
    WidgetKind.Favorite, WidgetKind.Trip, WidgetKind.Appointment -> buildSet {
        for (width in listOf(100.dp, WIDE_WIDGET)) {
            for (height in listOf(40.dp, 100.dp, 130.dp, TALL_WIDGET)) add(DpSize(width, height))
        }
    }
}

/** What every widget draws: the content of its kind on its card. */
@Composable
internal fun WidgetContent(kind: WidgetKind, config: WidgetConfig, snapshot: WidgetSnapshot?, now: Long, trip: Journey?) {
    val context = LocalContext.current
    val colors = widgetColors(config)
    val layout = widgetLayout(LocalSize.current.width.value, LocalSize.current.height.value)
    val padding = if (layout.compact) 8.dp else 12.dp
    when (kind) {
        WidgetKind.Departures -> WidgetCard(colors, openStopAction(context, snapshot?.board), padding) {
            DeparturesBody(
                snapshot?.board,
                snapshot?.problem,
                colors,
                now,
                layout.rows,
                layout.wide,
                showHeader = !layout.compact,
                compact = layout.compact,
                positionAt = snapshot?.positionAt,
            )
        }
        WidgetKind.Mini -> WidgetCard(colors, openStopAction(context, snapshot?.board), padding = 4.dp) {
            // From two cells wide on, two departures side by side; from 64 dp high the roomier form.
            MiniBody(
                snapshot?.board,
                snapshot?.problem,
                colors,
                now,
                count = if (LocalSize.current.width.value >= 100f) 2 else 1,
                roomy = LocalSize.current.height.value >= 64f,
            )
        }
        WidgetKind.Favorite -> WidgetCard(colors, openAppAction(context), padding) {
            ConnectionBody(snapshot, colors, now, layout)
        }
        WidgetKind.Trip -> if (trip != null) {
            WidgetCard(colors, openAppAction(context, trip = true), padding) {
                TripBody(trip, colors, OffsetDateTime.now(), layout)
            }
        } else {
            WidgetCard(colors, openAppAction(context), padding) { ConnectionBody(snapshot, colors, now, layout) }
        }
        WidgetKind.Appointment -> WidgetCard(colors, searchAppointmentAction(context, snapshot?.appointment), padding) {
            AppointmentBody(snapshot, colors, now, layout = layout)
        }
        WidgetKind.Auto -> AutoContent(snapshot, colors, now, trip, layout, padding)
    }
}

/**
 * The automatic widget: the trip while one is followed, else what the last load picked (see
 * WidgetLoader.auto), always with a small caption why.
 */
@Composable
private fun AutoContent(snapshot: WidgetSnapshot?, colors: WidgetColors, now: Long, trip: Journey?, layout: WidgetLayout, padding: Dp) {
    val context = LocalContext.current
    // A trip that ended since the last load leaves the departures nearby that came with it.
    val reason = if (trip != null) {
        AutoReason.Trip
    } else {
        snapshot?.reason?.takeIf { it != AutoReason.Trip } ?: AutoReason.Nearby
    }
    val action = when (reason) {
        AutoReason.Trip -> openAppAction(context, trip = true)
        AutoReason.Nearby -> openStopAction(context, snapshot?.board)
        AutoReason.Appointment -> searchAppointmentAction(context, snapshot?.appointment)
        else -> openAppAction(context)
    }
    WidgetCard(colors, action, padding) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            // A single row has no room for the caption: the content alone says enough there.
            if (!layout.compact) {
                // An appointment with its way gets the same "Start" as the favourite widget,
                // next to the caption where it always has room; the way to work or home
                // brings its own in ConnectionBody.
                val startable = if (trip == null && reason == AutoReason.Appointment) snapshot?.appointment?.connection else null
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    ReasonCaption(reason, colors)
                    if (startable != null) {
                        Spacer(modifier = GlanceModifier.defaultWeight())
                        StartButton(startable, colors, small = true)
                    } else if (trip == null) {
                        // Load anew by a tap, without opening the app (see WidgetRefreshAction).
                        Spacer(modifier = GlanceModifier.defaultWeight())
                        RefreshMark(colors)
                    }
                }
                Spacer(modifier = GlanceModifier.height(6.dp))
            }
            // The caption takes about one row of departures.
            val inner = if (layout.compact) layout else layout.copy(rows = (layout.rows - 1).coerceAtLeast(1))
            when {
                trip != null -> TripBody(trip, colors, OffsetDateTime.now(), inner)
                reason == AutoReason.Appointment -> AppointmentBody(snapshot, colors, now, layout = inner)
                reason == AutoReason.ToWork || reason == AutoReason.ToHome -> ConnectionBody(snapshot, colors, now, inner)
                else -> DeparturesBody(
                    snapshot?.board,
                    snapshot?.problem,
                    colors,
                    now,
                    inner.rows,
                    inner.wide,
                    showHeader = !inner.compact,
                    compact = inner.compact,
                    positionAt = snapshot?.positionAt,
                )
            }
        }
    }
}

class DeparturesWidget : NextStopWidget(WidgetKind.Departures)
class FavoriteWidget : NextStopWidget(WidgetKind.Favorite)
class TripWidget : NextStopWidget(WidgetKind.Trip)
class MiniWidget : NextStopWidget(WidgetKind.Mini)
class AppointmentWidget : NextStopWidget(WidgetKind.Appointment)
class AutoWidget : NextStopWidget(WidgetKind.Auto)

/** The common part of the receivers: the first widget loads at once (no regular loading, see WidgetUpdates). */
abstract class NextStopWidgetReceiver(kind: WidgetKind) : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WidgetUpdates.widgetOf(kind)

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdates.requestRefresh(context)
    }
}

class DeparturesWidgetReceiver : NextStopWidgetReceiver(WidgetKind.Departures)
class FavoriteWidgetReceiver : NextStopWidgetReceiver(WidgetKind.Favorite)
class TripWidgetReceiver : NextStopWidgetReceiver(WidgetKind.Trip)
class MiniWidgetReceiver : NextStopWidgetReceiver(WidgetKind.Mini)
class AppointmentWidgetReceiver : NextStopWidgetReceiver(WidgetKind.Appointment)
class AutoWidgetReceiver : NextStopWidgetReceiver(WidgetKind.Auto)

/** Sample data for the pictures in the widget picker, and their publication. */
internal object WidgetPreviews {
    private const val KEY_PUBLISHED = "previewsPublished"
    // Raise to publish the pictures again after a change of their look.
    private const val PREVIEW_VERSION = 2

    /**
     * Publishes the generated previews once per [PREVIEW_VERSION] on Android 15 and later.
     * Android allows only about two calls an hour, so they are not renewed with the data.
     * They are only an addition: every widget has a static previewLayout that the picker shows
     * where no generated preview is published, so a preview that fails is skipped, not fatal.
     */
    suspend fun publishOnce(context: Context) {
        if (!generatedPreviews) return
        val prefs = context.getSharedPreferences("next-stop-widgets", Context.MODE_PRIVATE)
        val key = "$KEY_PUBLISHED-$PREVIEW_VERSION"
        val done = prefs.getStringSet(key, emptySet()).orEmpty().toMutableSet()
        val manager = GlanceAppWidgetManager(context)
        for (kind in WidgetKind.entries) {
            if (kind.name in done) continue
            val result = try {
                manager.setWidgetPreviews(receiverClass(kind))
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                // This preview cannot be drawn: the picker keeps the static one, and it is not tried again.
                DiagLog.w("NextStopWidgets", e) { "Generated preview of $kind failed" }
                done.add(kind.name)
                continue
            }
            // Over the limit: the rest follows at a later start of the app.
            if (result != GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_SUCCESS) break
            done.add(kind.name)
        }
        prefs.edit().putStringSet(key, done).apply()
    }

    private fun receiverClass(kind: WidgetKind): KClass<out GlanceAppWidgetReceiver> = when (kind) {
        WidgetKind.Departures -> DeparturesWidgetReceiver::class
        WidgetKind.Favorite -> FavoriteWidgetReceiver::class
        WidgetKind.Trip -> TripWidgetReceiver::class
        WidgetKind.Mini -> MiniWidgetReceiver::class
        WidgetKind.Appointment -> AppointmentWidgetReceiver::class
        WidgetKind.Auto -> AutoWidgetReceiver::class
    }

    private fun stop(name: String) = Stop(id = "preview", name = name, latitude = null, longitude = null, distanceMeters = null)

    fun sample(kind: WidgetKind, now: Long): WidgetSnapshot {
        val minute = 60_000L
        val board = StopBoard(
            stop("S+U Alexanderplatz"),
            listOf(
                WidgetDeparture("U8", "subway", "Hermannstraße", now + 2 * minute, now + 2 * minute, 0, false, "2"),
                WidgetDeparture("U2", "subway", "Pankow", now + 4 * minute, now + 3 * minute, 1, false, "1"),
                WidgetDeparture("S5", "suburban", "Strausberg Nord", now + 7 * minute, now + 4 * minute, 3, false, "3"),
                WidgetDeparture("M4", "tram", "Hackescher Markt", now + 9 * minute, now + 9 * minute, 0, false, null),
                WidgetDeparture("200", "bus", "Michelangelostraße", now + 12 * minute, now + 12 * minute, 0, false, null),
            ),
        )
        val connection = WidgetConnection(
            from = stop("S+U Alexanderplatz"),
            to = stop("Home"),
            departure = now + 6 * minute,
            plannedDeparture = now + 6 * minute,
            arrival = now + 31 * minute,
            firstRide = now + 8 * minute,
            rides = listOf(WidgetRide("U8", "subway", 5), WidgetRide("S5", "suburban", 3)),
            key = "preview",
        )
        return when (kind) {
            WidgetKind.Departures -> WidgetSnapshot(board = board)
            WidgetKind.Mini -> WidgetSnapshot(board = board.copy(departures = board.departures.take(1)))
            WidgetKind.Favorite, WidgetKind.Trip -> WidgetSnapshot(connection = connection, icon = PlaceIcon.Home, label = "")
            WidgetKind.Appointment -> WidgetSnapshot(
                appointment = WidgetAppointment("Team meeting", now + 55 * minute, "Office", connection.copy(departure = now + 18 * minute)),
            )
            WidgetKind.Auto -> WidgetSnapshot(board = board, reason = AutoReason.Nearby)
        }
    }

    /** A trip a few minutes before boarding the U8, for the picture of the trip widget. */
    fun sampleTrip(): Journey {
        val now = OffsetDateTime.now()
        fun ride(line: String, product: String, from: String, to: String, start: Long, end: Long) = Leg(
            line = line,
            product = product,
            walking = false,
            from = from,
            to = to,
            departure = now.plusMinutes(start),
            plannedDeparture = now.plusMinutes(start),
            arrival = now.plusMinutes(end),
            plannedArrival = now.plusMinutes(end),
            departurePlatform = "2",
            arrivalPlatform = "1",
            direction = null,
            cancelled = false,
        )
        return Journey(
            legs = listOf(
                ride("U8", "subway", "S+U Alexanderplatz", "S+U Jannowitzbrücke", -2, 3),
                ride("S5", "suburban", "S+U Jannowitzbrücke", "S Ostkreuz", 7, 15),
            ),
        )
    }
}

/** The widget behind an id of AppWidgetManager, for the settings page. */
internal fun glanceIdOf(context: Context, appWidgetId: Int): GlanceId? = try {
    GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
} catch (e: IllegalArgumentException) {
    null
}

internal fun invalidWidgetId(id: Int): Boolean = id == AppWidgetManager.INVALID_APPWIDGET_ID
