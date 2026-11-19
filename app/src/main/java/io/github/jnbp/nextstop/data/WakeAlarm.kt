package io.github.jnbp.nextstop.data

import java.time.Duration
import java.time.OffsetDateTime

/**
 * When the wake-up alarm ("Aufwach-Alarm") rings on a followed trip: never, by itself on rides
 * of at least [WAKE_LONG_MINUTES] ([Auto], the default), or on every ride long enough for it.
 */
enum class WakeAlarmMode { Off, Auto, Always }

/** From this long a ride gets the wake-up alarm by itself ([WakeAlarmMode.Auto]). */
const val WAKE_LONG_MINUTES = 60L

/** Minutes before getting off the alarm rings, unless set otherwise. */
const val WAKE_DEFAULT_LEAD_MINUTES = 10

/** The choices of the lead in the settings. */
val WAKE_LEAD_CHOICES = listOf(5, 10, 15, 20, 30)

/** The alarm rings again this often until it is confirmed. */
const val WAKE_REPEAT_SECONDS = 60L

// A ride this much longer than the lead at least gets the alarm when it is wanted on every ride.
private const val WAKE_MIN_EXTRA_MINUTES = 5L

// The alarm stops by itself this long after the arrival it was for.
const val WAKE_AFTER_ARRIVAL_SECONDS = 180L

/** One ring of the wake-up alarm: before getting off the ride [legIndex] at [arrival], at [at]. */
data class WakeTarget(val key: String, val legIndex: Int, val at: OffsetDateTime, val arrival: OffsetDateTime, val stop: String)

/** True when [leg] is long enough for the alarm by [mode] and the lead [leadMinutes]. */
fun wakeWanted(leg: Leg, mode: WakeAlarmMode, leadMinutes: Int): Boolean {
    if (leg.walking || leg.cancelled) return false
    val minutes = Duration.between(leg.departure, leg.arrival).toMinutes()
    return when (mode) {
        WakeAlarmMode.Off -> false
        WakeAlarmMode.Auto -> minutes >= WAKE_LONG_MINUTES
        WakeAlarmMode.Always -> minutes >= leadMinutes + WAKE_MIN_EXTRA_MINUTES
    }
}

/**
 * The rings of [journey]: one per ride that gets the alarm by [mode], [leadMinutes] before its
 * plausible arrival (see [Journey.plausible]). [forTrip] is the switch of this trip: null
 * follows [mode], true rings on every ride long enough for it, false never. The key stays the
 * same while live data move the time. A ride where the traveller stays seated for the next
 * one rings before the end of the last of them only. With [timesShown] the times of [journey]
 * are taken as they are (the ride state, see RideState: the arrival on board by position).
 */
fun wakeTargets(
    journey: Journey,
    mode: WakeAlarmMode,
    leadMinutes: Int,
    forTrip: Boolean? = null,
    timesShown: Boolean = false,
    lead: WakeLead? = null,
): List<WakeTarget> {
    // [timesShown]: the times of [journey] are already the ones to show (plausible, or by the ride state).
    val shown = (if (timesShown) journey else journey.plausible()).legs
    val chosen = when (forTrip) {
        null -> mode
        true -> WakeAlarmMode.Always
        false -> WakeAlarmMode.Off
    }
    if (chosen == WakeAlarmMode.Off) return emptyList()
    val out = mutableListOf<WakeTarget>()
    shown.forEachIndexed { index, leg ->
        if (!wakeWanted(leg, chosen, leadMinutes)) return@forEachIndexed
        val next = shown.drop(index + 1).firstOrNull { !it.walking }
        if (next != null && leg.tripId != null && leg.tripId == next.tripId) return@forEachIndexed
        val at = wakeRingAt(leg, lead, leadMinutes)
        val key = "wake|${leg.tripId ?: leg.line ?: leg.product}|${leg.to}|${leg.plannedArrival ?: leg.arrival}"
        out.add(WakeTarget(key, index, at, leg.arrival, leg.to))
    }
    return out
}

/**
 * When the wake-up alarm of one trip rings, chosen in the companion for this trip only (#64):
 * [Minutes] before getting off, [Stops] stops before the stop to get off at (when the vehicle
 * leaves that stop), or at an exact time [At]. Null everywhere means the lead of the settings.
 */
sealed class WakeLead {
    data class Minutes(val minutes: Int) : WakeLead()
    data class Stops(val stops: Int) : WakeLead()
    data class At(val time: OffsetDateTime) : WakeLead()
}

/** The choices of the lead offered for one trip, in the order shown. */
val WAKE_TRIP_LEADS: List<WakeLead> = listOf(WakeLead.Stops(2), WakeLead.Minutes(5), WakeLead.Minutes(10), WakeLead.Minutes(15))

/**
 * When the alarm rings before getting off [leg]: by [lead] where given, else [leadMinutes] before
 * the arrival. Never before the departure nor after the arrival. [WakeLead.Stops] counts back from
 * the last stop among the stops of the ride and falls back on the minutes where the ride has too
 * few stops or no time there; [WakeLead.At] a time outside the ride falls back the same way.
 */
fun wakeRingAt(leg: Leg, lead: WakeLead?, leadMinutes: Int): OffsetDateTime {
    val byMinutes = { minutes: Int -> leg.arrival.minusMinutes(minutes.toLong()) }
    val at = when (lead) {
        null -> byMinutes(leadMinutes)
        is WakeLead.Minutes -> byMinutes(lead.minutes)
        is WakeLead.Stops -> {
            val stops = leg.stopovers
            val index = stops.lastIndex - lead.stops
            val stop = stops.getOrNull(index)?.takeIf { index > 0 }
            (stop?.departure ?: stop?.arrival) ?: byMinutes(leadMinutes)
        }
        is WakeLead.At -> lead.time.takeIf { !it.isBefore(leg.departure) && !it.isAfter(leg.arrival) } ?: byMinutes(leadMinutes)
    }
    return when {
        at.isBefore(leg.departure) -> leg.departure
        at.isAfter(leg.arrival) -> leg.arrival
        else -> at
    }
}

/** What the alarm does at a look: nothing, ring (a new ring or the next repeat), or stop ringing. */
sealed class WakeAction {
    object None : WakeAction()
    data class Ring(val target: WakeTarget, val first: Boolean) : WakeAction()
    object Stop : WakeAction()
}

/**
 * What to do at [now] with [targets]: [confirmed] are the keys confirmed ("Ich bin wach"),
 * [ringing] the key that rings, if any, since its last ring at [lastRing]. A target rings from
 * its time until [WAKE_AFTER_ARRIVAL_SECONDS] after its arrival, again every
 * [WAKE_REPEAT_SECONDS]; one that is gone (another plan, the end of the trip) stops.
 */
fun wakeAction(
    targets: List<WakeTarget>,
    confirmed: Set<String>,
    ringing: String?,
    lastRing: OffsetDateTime?,
    now: OffsetDateTime,
): WakeAction {
    val due = targets.filter {
        it.key !in confirmed && !it.at.isAfter(now) && now.isBefore(it.arrival.plusSeconds(WAKE_AFTER_ARRIVAL_SECONDS))
    }.maxByOrNull { it.at }
    if (due == null) return if (ringing != null) WakeAction.Stop else WakeAction.None
    if (due.key != ringing || lastRing == null) return WakeAction.Ring(due, first = due.key != ringing)
    return if (!now.isBefore(lastRing.plusSeconds(WAKE_REPEAT_SECONDS))) WakeAction.Ring(due, first = false) else WakeAction.None
}

/** When to look again: the next ring of what rings, else the next target's time; null when nothing is left. */
fun nextWakeLook(targets: List<WakeTarget>, confirmed: Set<String>, ringing: String?, lastRing: OffsetDateTime?, now: OffsetDateTime): OffsetDateTime? {
    if (ringing != null && lastRing != null) return lastRing.plusSeconds(WAKE_REPEAT_SECONDS)
    return targets.filter { it.key !in confirmed && it.at.isAfter(now) }.minOfOrNull { it.at }
}

/** The keys of rings started by hand ("Aufwach-Alarm testen" in the developer options) begin with this. */
const val WAKE_TEST_PREFIX = "wake|test|"

/** A ring to test the alarm at [now]: to [stop], arriving [WAKE_DEFAULT_LEAD_MINUTES] from now. */
fun wakeTestTarget(now: OffsetDateTime, stop: String): WakeTarget =
    WakeTarget(WAKE_TEST_PREFIX + now.toInstant().toEpochMilli(), -1, now, now.plusMinutes(WAKE_DEFAULT_LEAD_MINUTES.toLong()), stop)
