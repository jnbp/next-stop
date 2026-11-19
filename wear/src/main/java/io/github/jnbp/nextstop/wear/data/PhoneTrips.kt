package io.github.jnbp.nextstop.wear.data

/*
 * Which state of the phone's trip the watch takes, when the Data Layer holds more than one
 * (every node that ever wrote "/nextstop/trip" keeps its own item: a second phone with the
 * app, an old pairing). Free of Android classes, tested in PhoneTripsTest.
 */

/**
 * The state to take from [states], the trip items of all phones at [now]: the running trip
 * (active and not stale) written last; without one, the newest state at all (so a trip that
 * ended there ends here). Null for an empty list.
 */
fun choosePhoneTrip(states: List<TripSnapshot>, now: Long): TripSnapshot? {
    val running = states.filter { it.active && !it.stale(now) }
    if (running.isNotEmpty()) return running.maxByOrNull { it.updated }
    return states.maxByOrNull { it.updated }
}

/**
 * True when [incoming], a state of a phone that is not a running trip (no trip, or a stale
 * one), ends the trip of the phone [shown] here. A state of another phone ([TripSnapshot.node])
 * never does, unless the trip shown is stale itself; where a node is unknown (older stored
 * states) it does, as before.
 */
fun endsShownTrip(shown: TripSnapshot, incoming: TripSnapshot, now: Long): Boolean {
    if (!shown.active || !shown.fromPhone) return false
    if (shown.stale(now)) return true
    val from = shown.node ?: return true
    val other = incoming.node ?: return true
    return from == other
}
