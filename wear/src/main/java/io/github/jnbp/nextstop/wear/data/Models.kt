package io.github.jnbp.nextstop.wear.data

/** What a place is: a stop of the network, a point of interest, or a street address. */
enum class PlaceKind { Stop, Poi, Address }

/**
 * How tight changes may be, as on the phone (TransferComfort there): the least minutes a
 * search leaves between two rides. Very risky searches like risky (0 minutes); the phone
 * also rates delays, which the watch does not.
 */
enum class TransferComfort(val minutes: Int) {
    Relaxed(8),
    Normal(4),
    Risky(0),
    VeryRisky(0),
    ;

    /** The next level for the chip that cycles through them. */
    fun next(): TransferComfort = entries[(ordinal + 1) % entries.size]

    companion object {
        fun parse(name: String?): TransferComfort? = entries.firstOrNull { it.name == name }
    }
}

/** A line that serves a stop, as the badge shows it. */
data class StopLine(val name: String, val product: String)

/**
 * A place the watch can show departures for or search a journey to. [lines] and [products]
 * are only filled for stops found nearby (empty when the service sends none).
 */
data class Place(
    val id: String,
    val name: String,
    val latitude: Double?,
    val longitude: Double?,
    val kind: PlaceKind = PlaceKind.Stop,
    val distanceMeters: Int? = null,
    val lines: List<StopLine> = emptyList(),
    val products: List<String> = emptyList(),
    /**
     * The phone's station identity key (one station across feeds, see the phone's
     * StationIdentity); kept as received and sent back with the place, null where none came.
     */
    val key: String? = null,
)

/**
 * One departure of a line at a stop; times as epoch milliseconds, null when the provider sends
 * none. [time] is the live time, or the planned one without live data (a cancelled run).
 */
data class Departure(
    val line: String,
    val product: String,
    val direction: String,
    val time: Long?,
    val delayMinutes: Int?,
    val platform: String?,
    val cancelled: Boolean,
    /** The time in the timetable; null when the provider sends none. */
    val plannedTime: Long? = null,
    /** An extra run the provider announces as additional ("Zusatzfahrt"), not in the timetable. */
    val additional: Boolean = false,
    /** The line of a cancelled run this additional run stands in for (see mergeReplacements); null where none. */
    val replaces: String? = null,
    /** The short texts of the provider's warnings for this run (disruptions, construction work); empty where none are sent. */
    val warnings: List<String> = emptyList(),
    /** The colour of the run's line as the phone works it out (ARGB, "color"); null where none came (see [lineArgb]). */
    val color: Long? = null,
)

/** One section of a journey: a ride on a line or a walk. Times as epoch milliseconds. */
data class Leg(
    val line: String?,
    val product: String,
    val walking: Boolean,
    val from: String,
    val to: String,
    val departure: Long,
    val arrival: Long,
    val departureDelayMinutes: Int,
    val arrivalDelayMinutes: Int,
    val platform: String?,
    val direction: String?,
    val cancelled: Boolean,
    /** Planned or live arrival times of the stops of a ride after boarding, the last one being [to]. */
    val stopTimes: List<Long> = emptyList(),
    /** The platform where the ride arrives at [to]; [platform] is the one of departure. */
    val arrivalPlatform: String? = null,
    /** Where [from] and [to] lie, for the sketch of a walk; null when the provider names none. */
    val fromPosition: Pair<Double, Double>? = null,
    val toPosition: Pair<Double, Double>? = null,
    /** A walk of 0 m within a station (the phone's walkMeters): nothing to set off for. */
    val inStation: Boolean = false,
    /**
     * How tight the change to this ride is by the phone's rule ("rating", which knows the station,
     * its platforms and walks); null where the phone sent none (a search of the watch itself, an
     * older phone app): then the watch estimates it (see [ChangeRating.estimate]).
     */
    val changeRating: ChangeRating? = null,
    /**
     * Where to board this ride, as the phone works it out for the journeys it finds for the
     * watch ("boardHint", "boardPart", "boardEstimated"): its hint text ("Mitte einsteigen –
     * kurzer Weg zur U2") and the third of the train, 0 rear, 1 middle, 2 front, -1 unknown.
     * Null and -1 for a search of the watch itself, which knows no change positions.
     */
    val boardHint: String? = null,
    val boardPart: Int = -1,
    val boardEstimated: Boolean = false,
    /**
     * The colour of the ride's line as the phone works it out ("color": its provider's colour,
     * where it runs, see lineColor there); null for a walk and a search of the watch itself,
     * which falls back to Berlin's network map (see [lineArgb]).
     */
    val color: Long? = null,
    /**
     * The names of [from], [to] and [direction] as the phone's trip state shows them, where they
     * differ ("fromShown", "toShown", "dirShown": railway style on a long-distance ride, #93, the
     * railway's short words on a regional train, #168; on the last leg the destination as short
     * as the phone names it, #146); null else and for a search of the watch itself. Only for
     * showing: [from] and [to] stay the provider's names, which keys and matches use.
     */
    val fromShown: String? = null,
    val toShown: String? = null,
    val directionShown: String? = null,
    /**
     * For a walk of a change, the minutes it takes as the phone counts them ("changeWalk", its
     * changeWalkMinutes), where fewer than its times: a walk the provider stretched over the wait.
     * Null else, and for a search of the watch itself (then its times count).
     */
    val changeWalkMinutes: Int? = null,
    /** The provider sent live times for this ride ("realtime" from the phone), not only the timetable (#227b). */
    val realtime: Boolean = false,
)

/** A connection from A to B. [refreshToken] loads it again with live data, on the watch or the phone. */
data class Journey(val legs: List<Leg>, val refreshToken: String?) {
    val departure: Long get() = legs.first().departure
    val arrival: Long get() = legs.last().arrival
    val rides: List<Leg> get() = legs.filter { !it.walking }
    val transfers: Int get() = (rides.size - 1).coerceAtLeast(0)
}

/**
 * Within this time before the arrival at the stop to get off, "next stop: get off" is said
 * (and the watch buzzes); farther ahead, on a long hop of a night coach or an ICE, the time
 * left is told instead ("Get off in 2:58 h"). The twin of the phone's GET_OFF_SOON_SECONDS.
 */
const val GET_OFF_SOON_MILLIS = 10 * 60_000L

/** True when the arrival at [arrival] is at most [GET_OFF_SOON_MILLIS] after [now] (epoch milliseconds). */
fun getOffSoon(arrival: Long, now: Long): Boolean = arrival - now <= GET_OFF_SOON_MILLIS

/**
 * [minutes] split for a label: below an hour the minutes alone (hours null), from an hour on the
 * whole hours and the minutes left, those null for whole hours (shown "2:00 h").
 */
fun hoursAndMinutes(minutes: Long): Pair<Long?, Long?> {
    val safe = minutes.coerceAtLeast(0L)
    if (safe < 60L) return null to safe
    return safe / 60L to (safe % 60L).takeIf { it > 0L }
}
