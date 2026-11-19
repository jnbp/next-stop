package io.github.jnbp.nextstop.data

import java.time.Duration
import java.time.OffsetDateTime

/*
 * #60: the live departure of a followed ride jumped 09:51 -> 09:48 -> 09:51 (the board said
 * 09:49), as refreshes of the trip came from backends whose live data were not equally fresh.
 * A departure still ahead that moves back a little is a promise to be early that nobody can
 * check; the traveller would hurry for nothing. Such a step back is shown only once a second
 * refresh in a row confirms it. Later times and big changes come at once.
 */

/** A departure may move back by at most this much (s) before a second refresh confirms it. */
const val STEADY_BACK_SECONDS = 180L

/**
 * The live departures of a followed trip, held steady over refreshes (see above). One per
 * followed trip; [steady] is called with each refreshed journey and the one shown before.
 */
class SteadyLive {
    // The earlier departure seen once per ride (run and planned departure), waiting for a second look.
    private val pending = HashMap<String, OffsetDateTime>()

    /**
     * [fresh] with every ride whose departure, still ahead at [now], moved back by at most
     * [STEADY_BACK_SECONDS] against [known] (the same ride, same planned time) keeping the known
     * departure, unless the refresh before already said the same earlier time.
     */
    fun steady(fresh: Journey, known: Journey, now: OffsetDateTime): Journey {
        if (fresh.legs.size != known.legs.size) return fresh
        var changed = false
        val legs = fresh.legs.mapIndexed { index, leg ->
            val before = known.legs[index]
            if (leg.walking || before.walking || leg.line != before.line) return@mapIndexed leg
            val planned = leg.plannedDeparture ?: return@mapIndexed leg
            if (planned != before.plannedDeparture) return@mapIndexed leg
            val key = "${leg.tripId ?: leg.line}|${planned.toInstant()}"
            val back = Duration.between(leg.departure, before.departure).seconds
            if (!before.departure.isAfter(now) || back <= 0 || back > STEADY_BACK_SECONDS) {
                pending.remove(key)
                return@mapIndexed leg
            }
            if (pending[key]?.isEqual(leg.departure) == true) {
                pending.remove(key)
                return@mapIndexed leg
            }
            pending[key] = leg.departure
            changed = true
            val stops = if (leg.stopovers.isNotEmpty() && before.stopovers.isNotEmpty() &&
                leg.stopovers.first().name == before.stopovers.first().name
            ) {
                listOf(before.stopovers.first()) + leg.stopovers.drop(1)
            } else {
                leg.stopovers
            }
            leg.copy(departure = before.departure, stopovers = stops)
        }
        return if (changed) fresh.copy(legs = legs) else fresh
    }
}

/** The steady live departures of the trip the companion follows (the trip service or the app, one at a time). */
val followedTripLive = SteadyLive()
