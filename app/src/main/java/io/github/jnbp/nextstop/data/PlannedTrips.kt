package io.github.jnbp.nextstop.data

import android.content.Context
import java.time.OffsetDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The trips planned ahead, kept on the device, as a flow for every page that shows them (the
 * start page, "Meine Wege"). The alarms that remind and start them live in ui/PlannedTripAlarms.kt;
 * whoever changes the list here calls [io.github.jnbp.nextstop.ui.PlannedTripAlarms.schedule].
 */
object PlannedTrips {
    private const val PREFS = "planned_trips"
    private const val KEY = "trips"
    private val state = MutableStateFlow<List<PlannedTrip>>(emptyList())
    @Volatile private var loaded = false

    /** The planned trips, soonest first. Empty until [load]. */
    val trips: StateFlow<List<PlannedTrip>> get() = state

    /** Reads the stored trips once per process, leaving out those that are over. */
    // Read, change and store as one: callers run on several threads (app start, receivers, the cards).
    @Synchronized
    fun load(context: Context): List<PlannedTrip> {
        if (!loaded) {
            synchronized(this) {
                if (!loaded) {
                    val text = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
                    val now = OffsetDateTime.now()
                    state.value = plannedTripsOf(text).filter { keep(it, now) }.sortedBy { it.leaveAt }
                    loaded = true
                }
            }
        }
        return state.value
    }

    /** True when [journey] is planned already. */
    fun isPlanned(journey: Journey): Boolean = state.value.any { it.journey.id == journey.id }

    /** Plans [journey]; a journey planned already keeps its entry. Returns the planned trip. */
    // Read, change and store as one: callers run on several threads (app start, receivers, the cards).
    @Synchronized
    fun add(context: Context, journey: Journey, fromName: String, toName: String, leadMinutes: Int): PlannedTrip {
        load(context)
        state.value.firstOrNull { it.journey.id == journey.id }?.let { return it }
        val trip = PlannedTrip(
            id = "p" + System.currentTimeMillis().toString(36) + (journey.id.hashCode() and 0xffff).toString(36),
            journey = journey,
            fromName = fromName,
            toName = toName,
            leaveAt = plannedLeaveTime(journey),
            leadMinutes = leadMinutes,
        )
        store(context, (state.value + trip).sortedBy { it.leaveAt })
        return trip
    }

    /** The trip with [id], if it is still planned. */
    fun find(context: Context, id: String): PlannedTrip? = load(context).firstOrNull { it.id == id }

    /** Takes the trip with [id] off the list (cancelled, or started). */
    // Read, change and store as one: callers run on several threads (app start, receivers, the cards).
    @Synchronized
    fun remove(context: Context, id: String) {
        load(context)
        store(context, state.value.filter { it.id != id })
    }

    /** Puts [trip] back as it was (the "Undo" of a cancel); one of the same journey planned meanwhile stays. */
    // Read, change and store as one: callers run on several threads (app start, receivers, the cards).
    @Synchronized
    fun restore(context: Context, trip: PlannedTrip) {
        load(context)
        val back = withRestored(state.value, trip)
        if (back != state.value) store(context, back)
    }

    /** Marks the reminder of [id] as given. */
    // Read, change and store as one: callers run on several threads (app start, receivers, the cards).
    @Synchronized
    fun markReminded(context: Context, id: String) {
        load(context)
        store(context, state.value.map { if (it.id == id) it.copy(reminded = true) else it })
    }

    /** Marks the start of [id] as offered by a notification: no alarm for it any more. */
    // Read, change and store as one: callers run on several threads (app start, receivers, the cards).
    @Synchronized
    fun markOffered(context: Context, id: String) {
        load(context)
        store(context, state.value.map { if (it.id == id) it.copy(offered = true) else it })
    }

    /** Leaves out the trips with nothing left to do at [now]. */
    // Read, change and store as one: callers run on several threads (app start, receivers, the cards).
    @Synchronized
    fun prune(context: Context, now: OffsetDateTime = OffsetDateTime.now()) {
        load(context)
        val kept = state.value.filter { keep(it, now) }
        if (kept.size != state.value.size) store(context, kept)
    }

    // An offered start stays on the list (its notification starts it) until its arrival.
    private fun keep(trip: PlannedTrip, now: OffsetDateTime): Boolean =
        plannedNext(trip, now) != null || (trip.offered && trip.journey.arrival.isAfter(now))

    private fun store(context: Context, trips: List<PlannedTrip>) {
        state.value = trips
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, plannedTripsJson(trips)).apply()
    }
}
