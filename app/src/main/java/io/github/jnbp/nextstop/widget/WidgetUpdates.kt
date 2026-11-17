package io.github.jnbp.nextstop.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import io.github.jnbp.nextstop.ui.TripState
import io.github.jnbp.nextstop.ui.minutesBetween
import io.github.jnbp.nextstop.ui.tripStep

// Every widget is loaded again at least this often, by WorkManager (its shortest period).
private const val PERIODIC_MINUTES = 15L

// While something leaves within this many minutes, the widgets are drawn again every minute.
private const val NEAR_MINUTES = 15L

// ... and their data is loaded again when it is older than this.
private const val NEAR_REFRESH_MILLIS = 4 * 60_000L

// Opening the app loads widgets whose data is older than this.
private const val FOREGROUND_REFRESH_MILLIS = 2 * 60_000L

// The minute redraw may come this much after the start of a minute.
private const val TICK_WINDOW_MILLIS = 20_000L

private const val WORK_PERIODIC = "widgets-periodic"
private const val WORK_SOON = "widgets-soon"
private const val KEY_MAX_AGE = "maxAge"
private const val ACTION_TICK = "io.github.jnbp.nextstop.WIDGET_TICK"

/**
 * Keeps the home screen widgets current.
 *
 * - Data: WorkManager loads all widgets every 15 minutes (with a network), and at once when the
 *   app comes to the front, when a widget is set up and when the data of a widget showing a
 *   departure soon is older than four minutes.
 * - Minutes: the widgets work out the minutes from the absolute times whenever they are drawn.
 *   While one of them shows something within 15 minutes (or a trip is followed without its
 *   service), an inexact, non-waking alarm (AlarmManager RTC, a 20 second window) draws them
 *   again at the start of every minute. It never wakes the device: with the screen off it
 *   waits, and comes as soon as the device is awake again, which is when the widgets can be
 *   seen. WorkManager is not used for this: it has no delays this short, and every job costs
 *   more than one alarm. The alarm ends as soon as nothing is near.
 * - A followed trip: TripService and the app push every change of the next step.
 */
object WidgetUpdates {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val drawing = Mutex()
    @Volatile private var tripSignature: String? = null
    @Volatile private var watching = false

    /** The widget of each kind; the receivers hand out the same objects. */
    fun widgetOf(kind: WidgetKind): GlanceAppWidget = when (kind) {
        WidgetKind.Departures -> DeparturesWidget()
        WidgetKind.Favorite -> FavoriteWidget()
        WidgetKind.Trip -> TripWidget()
        WidgetKind.Mini -> MiniWidget()
        WidgetKind.Appointment -> AppointmentWidget()
        WidgetKind.Auto -> AutoWidget()
    }

    internal fun receiverOf(kind: WidgetKind): Class<*> = when (kind) {
        WidgetKind.Departures -> DeparturesWidgetReceiver::class.java
        WidgetKind.Favorite -> FavoriteWidgetReceiver::class.java
        WidgetKind.Trip -> TripWidgetReceiver::class.java
        WidgetKind.Mini -> MiniWidgetReceiver::class.java
        WidgetKind.Appointment -> AppointmentWidgetReceiver::class.java
        WidgetKind.Auto -> AutoWidgetReceiver::class.java
    }

    /** The kind of the widget with this id of AppWidgetManager, by its receiver. */
    fun kindOf(context: Context, appWidgetId: Int): WidgetKind? {
        val provider = AppWidgetManager.getInstance(context).getAppWidgetInfo(appWidgetId)?.provider ?: return null
        return WidgetKind.entries.firstOrNull { receiverOf(it).name == provider.className }
    }

    /** True when at least one widget of the app is on a home screen; cheap, no coroutine needed. */
    fun anyPlaced(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        return WidgetKind.entries.any { kind ->
            manager.getAppWidgetIds(ComponentName(context, receiverOf(kind))).isNotEmpty()
        }
    }

    private suspend fun placed(context: Context): List<Pair<WidgetKind, GlanceId>> {
        val manager = GlanceAppWidgetManager(context)
        return WidgetKind.entries.flatMap { kind ->
            manager.getGlanceIds(widgetOf(kind).javaClass).map { kind to it }
        }
    }

    /** Loads all widgets every 15 minutes while any is placed. */
    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(PERIODIC_MINUTES, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Loads the widgets whose data is older than [maxAgeMillis], soon and once. */
    fun requestRefresh(context: Context, maxAgeMillis: Long = 0L) {
        val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf(KEY_MAX_AGE to maxAgeMillis))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_SOON, ExistingWorkPolicy.KEEP, request)
    }

    /**
     * The app came to the front: its position is kept for the widgets, and widgets with old
     * data are loaded again. Also starts the watch of a followed trip.
     */
    fun onAppForeground(context: Context) {
        val app = context.applicationContext
        watchTrip(app)
        // The pictures of the widget picker matter most before the first widget is placed.
        scope.launch { WidgetPreviews.publishOnce(app) }
        if (!anyPlaced(app)) return
        lastKnownLocation(app)?.let { recordWidgetPosition(app, it) }
        schedulePeriodic(app)
        requestRefresh(app, FOREGROUND_REFRESH_MILLIS)
    }

    /** Draws all widgets again at once, after a change of their colours for example. */
    fun redrawAll(context: Context) {
        val app = context.applicationContext
        scope.launch { redraw(app) { true } }
    }

    /** One widget was set up anew: it loads at once. */
    fun refreshWidget(context: Context, kind: WidgetKind, id: GlanceId) {
        val app = context.applicationContext
        schedulePeriodic(app)
        scope.launch {
            try {
                refreshOne(app, WidgetLoader(app), kind, id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The next round of WorkManager tries again.
            }
            evaluateTicks(app)
        }
    }

    /**
     * Follows the trip of the app in [TripState]: a new or ended trip redraws the trip and
     * automatic widgets. Started once per process.
     */
    private fun watchTrip(context: Context) {
        if (watching) return
        watching = true
        scope.launch {
            TripState.journey.map { it?.id }.distinctUntilChanged().collect { tripChanged(context) }
        }
    }

    /**
     * The followed trip may have changed: when what the widgets show of it changed (the
     * journey, the step, its minutes or delay), the trip and automatic widgets are drawn again.
     * Called by TripService on every round and by the watch of the trip.
     */
    fun tripChanged(context: Context) {
        val journey = TripState.journey.value
        val now = OffsetDateTime.now()
        val signature = journey?.let {
            val step = tripStep(it, now)
            "${it.id}/${step.kind}/${step.leave}/${minutesBetween(now, step.time)}/${step.leg?.departure}/${step.leg?.arrival}"
        }
        if (signature == tripSignature) return
        tripSignature = signature
        val app = context.applicationContext
        scope.launch {
            redraw(app) { it == WidgetKind.Trip || it == WidgetKind.Auto }
            evaluateTicks(app)
        }
    }

    /** One round of WorkManager: loads the widgets whose data is older than [maxAgeMillis]. */
    internal suspend fun refreshAll(context: Context, maxAgeMillis: Long) {
        val widgets = placed(context)
        if (widgets.isEmpty()) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_PERIODIC)
            cancelTick(context)
            return
        }
        val loader = WidgetLoader(context)
        val now = System.currentTimeMillis()
        for ((kind, id) in widgets) {
            val state = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
            val age = now - (state[WidgetKeys.FETCHED_AT] ?: 0L)
            if (maxAgeMillis > 0 && age < maxAgeMillis) continue
            try {
                refreshOne(context, loader, kind, id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The widget keeps its last data, which still counts down from its times.
                if (state[WidgetKeys.SNAPSHOT] == null) {
                    store(context, kind, id, WidgetSnapshot(problem = WidgetProblem.Offline))
                }
            }
        }
        evaluateTicks(context)
    }

    private suspend fun refreshOne(context: Context, loader: WidgetLoader, kind: WidgetKind, id: GlanceId) {
        val state = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val snapshot = loader.load(kind, readConfig(state))
        store(context, kind, id, snapshot)
    }

    private suspend fun store(context: Context, kind: WidgetKind, id: GlanceId, snapshot: WidgetSnapshot) {
        val now = System.currentTimeMillis()
        updateAppWidgetState(context, id) { state ->
            state[WidgetKeys.SNAPSHOT] = snapshotToText(snapshot)
            state[WidgetKeys.FETCHED_AT] = now
            state[WidgetKeys.TICK] = now
        }
        widgetOf(kind).update(context, id)
    }

    /** Draws the widgets of the kinds [which] picks again, with the minutes of now. */
    private suspend fun redraw(context: Context, which: (WidgetKind) -> Boolean) {
        drawing.withLock {
            val now = System.currentTimeMillis()
            for ((kind, id) in placed(context)) {
                if (!which(kind)) continue
                updateAppWidgetState(context, id) { it[WidgetKeys.TICK] = now }
                widgetOf(kind).update(context, id)
            }
        }
    }

    /** The redraw at the start of a minute; asks for new data where it got old. */
    internal suspend fun tick(context: Context) {
        val now = System.currentTimeMillis()
        var stale = false
        for ((kind, id) in placed(context)) {
            if (kind == WidgetKind.Trip && TripState.journey.value != null) continue
            val state = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
            val snapshot = snapshotFromText(state[WidgetKeys.SNAPSHOT])
            val age = now - (state[WidgetKeys.FETCHED_AT] ?: 0L)
            val next = snapshot?.nextTime(now)
            if (age > NEAR_REFRESH_MILLIS && (next == null || next - now < NEAR_MINUTES * 60_000)) stale = true
        }
        redraw(context) { true }
        if (stale) requestRefresh(context, NEAR_REFRESH_MILLIS)
        evaluateTicks(context)
    }

    /** Starts the redraw every minute while something near is shown, and ends it otherwise. */
    private suspend fun evaluateTicks(context: Context) {
        val now = System.currentTimeMillis()
        val trip = TripState.journey.value != null && !TripState.serviceRunning.value
        val near = trip || placed(context).any { (_, id) ->
            val state: Preferences = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
            val next = snapshotFromText(state[WidgetKeys.SNAPSHOT])?.nextTime(now)
            next != null && next - now <= NEAR_MINUTES * 60_000
        }
        if (near) scheduleTick(context) else cancelTick(context)
    }

    private fun tickIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, WidgetTickReceiver::class.java).setAction(ACTION_TICK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun scheduleTick(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val now = System.currentTimeMillis()
        // Just after the start of the next minute, so "3 min" turns into "2 min" in time.
        val at = (now / 60_000 + 1) * 60_000 + 500
        // RTC, not RTC_WAKEUP: the device is never woken for a widget.
        alarms.setWindow(AlarmManager.RTC, at, TICK_WINDOW_MILLIS, tickIntent(context))
    }

    private fun cancelTick(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(tickIntent(context))
    }

    internal fun runAsync(block: suspend () -> Unit) {
        scope.launch { block() }
    }
}

/** Loads the widgets in the background; see [WidgetUpdates]. */
class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        WidgetUpdates.refreshAll(applicationContext, inputData.getLong(KEY_MAX_AGE, 0L))
        return Result.success()
    }
}

/** The redraw at the start of a minute; see [WidgetUpdates]. */
class WidgetTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TICK) return
        val pending = goAsync()
        val app = context.applicationContext
        WidgetUpdates.runAsync {
            try {
                WidgetUpdates.tick(app)
            } finally {
                pending.finish()
            }
        }
    }
}

/** True on Android 15 and later, where the widget picker shows generated previews. */
internal val generatedPreviews: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM
