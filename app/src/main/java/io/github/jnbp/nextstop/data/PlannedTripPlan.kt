package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.json.JSONArray
import org.json.JSONObject

/**
 * A trip planned ahead ("Fahrt planen"): [journey] from [fromName] to [toName]. A reminder
 * ("Bald losgehen") comes [leadMinutes] before [leaveAt], and the trip companion starts by
 * itself at [leaveAt]. [reminded] once the reminder came; [offered] once its start was offered
 * by a notification instead (Android did not let the app start it by itself). Kept on the device until it starts,
 * is cancelled or its arrival has passed.
 */
data class PlannedTrip(
    val id: String,
    val journey: Journey,
    val fromName: String,
    val toName: String,
    val leaveAt: OffsetDateTime,
    val leadMinutes: Int,
    val reminded: Boolean = false,
    val offered: Boolean = false,
) {
    val remindAt: OffsetDateTime get() = leaveAt.minusMinutes(leadMinutes.toLong())

    /** The name of the trip for lists ("Meine Wege"): where it goes. */
    val title: String get() = toName

    /** The lines it rides, in order; [products] the product of each, at the same index. */
    val lines: List<String> get() = journey.rides.mapNotNull { it.line }
    val products: List<String> get() = journey.rides.filter { it.line != null }.map { it.product }
}

/** What a planned trip waits for next. */
enum class PlannedPhase { Remind, Start }

/** Minutes before leaving the reminder comes, unless set otherwise. */
const val PLANNED_DEFAULT_LEAD_MINUTES = 10

/** The choices of that lead in the settings. */
val PLANNED_LEAD_CHOICES = listOf(5, 10, 15, 20, 30)

// A planned trip whose start was missed (the device was off) still starts this long after its time.
const val PLANNED_START_GRACE_MINUTES = 15L

/** When to leave for [journey]: the start of its first leg (the walk to the first ride, where there is one). */
fun plannedLeaveTime(journey: Journey): OffsetDateTime = journey.plausible().legs.first().departure

/**
 * What [trip] does next at [now] and when: the reminder (not yet given, before leaving; its
 * time may have come already), then the start at the time to leave. Null when nothing is left:
 * its start is more than [PLANNED_START_GRACE_MINUTES] ago or its arrival has passed.
 */
fun plannedNext(trip: PlannedTrip, now: OffsetDateTime): Pair<PlannedPhase, OffsetDateTime>? {
    if (!trip.journey.arrival.isAfter(now)) return null
    if (trip.offered) return null
    if (now.isAfter(trip.leaveAt.plusMinutes(PLANNED_START_GRACE_MINUTES))) return null
    if (!trip.reminded && now.isBefore(trip.leaveAt) && trip.remindAt.isBefore(trip.leaveAt)) {
        return PlannedPhase.Remind to trip.remindAt
    }
    return PlannedPhase.Start to trip.leaveAt
}

/** The earliest of what [trips] do next at [now]: when an alarm has to wake the app; null with nothing left. */
fun plannedWakeUp(trips: List<PlannedTrip>, now: OffsetDateTime): OffsetDateTime? =
    trips.mapNotNull { plannedNext(it, now)?.second }.minOrNull()

/** [trips] as JSON text. */
fun plannedTripsJson(trips: List<PlannedTrip>): String = JSONArray().apply {
    trips.forEach { trip ->
        put(
            JSONObject()
                .put("id", trip.id)
                .put("journey", keptJourneyJson(trip.journey))
                .put("from", trip.fromName)
                .put("to", trip.toName)
                .put("leave", trip.leaveAt.toString())
                .put("lead", trip.leadMinutes)
                .put("reminded", trip.reminded)
                .put("offered", trip.offered),
        )
    }
}.toString()

/** The planned trips stored as [text]; broken entries are left out. */
fun plannedTripsOf(text: String?): List<PlannedTrip> {
    if (text.isNullOrEmpty()) return emptyList()
    return try {
        val list = JSONArray(text)
        (0 until list.length()).mapNotNull { index ->
            try {
                val json = list.getJSONObject(index)
                val journey = keptJourneyOf(json.getJSONObject("journey")) ?: return@mapNotNull null
                PlannedTrip(
                    id = json.getString("id"),
                    journey = journey,
                    fromName = json.optString("from"),
                    toName = json.optString("to"),
                    leaveAt = OffsetDateTime.parse(json.getString("leave")),
                    leadMinutes = json.optInt("lead", PLANNED_DEFAULT_LEAD_MINUTES),
                    reminded = json.optBoolean("reminded"),
                    offered = json.optBoolean("offered"),
                )
            } catch (e: Exception) {
                null
            }
        }
    } catch (e: Exception) {
        emptyList()
    }
}

/**
 * [trips] with [trip] back in its place by the time to leave (the "Undo" of a cancel); the
 * same list where it or another plan of the same journey is there already.
 */
fun withRestored(trips: List<PlannedTrip>, trip: PlannedTrip): List<PlannedTrip> =
    if (trips.any { it.id == trip.id || it.journey.id == trip.journey.id }) trips else (trips + trip).sortedBy { it.leaveAt }
