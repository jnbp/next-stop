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
)

/** A connection from A to B. [refreshToken] loads it again with live data, on the watch or the phone. */
data class Journey(val legs: List<Leg>, val refreshToken: String?) {
    val departure: Long get() = legs.first().departure
    val arrival: Long get() = legs.last().arrival
    val rides: List<Leg> get() = legs.filter { !it.walking }
    val transfers: Int get() = (rides.size - 1).coerceAtLeast(0)
}
