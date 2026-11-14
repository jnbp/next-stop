package io.github.jnbp.nextstop.data

/*
 * Favourites of several stops at once: the stops picked together on the departures tab, kept
 * as one favourite with a name of its own. Opening one shows the board of exactly those stops.
 */

/**
 * A favourite of several [stops] shown together, the first one the selected stop of the board
 * (the others are added to it). [name] is the user's own; empty, the list names it by its stops.
 */
data class StopGroup(val stops: List<Stop>, val name: String = "") {
    /** The same for the same stops in any order: a board of these stops is this favourite. */
    val id: String get() = stopGroupId(stops.map { it.id })
}

/** The identity of a set of stops by their ids, in any order and each once. */
fun stopGroupId(ids: Collection<String>): String = ids.distinct().sorted().joinToString("\n")

/**
 * The favourite of [stops] (each once, without the distance of the moment) named [name], or
 * null for fewer than two stops: one stop is an ordinary favourite stop.
 */
fun stopGroupOf(stops: List<Stop>, name: String = ""): StopGroup? {
    val distinct = stops.distinctBy { it.id }.map { it.copy(distanceMeters = null) }
    return if (distinct.size < 2) null else StopGroup(distinct, name.trim())
}

/** Whether [group] is the favourite of exactly [stops], in whatever order. */
fun sameStops(group: StopGroup, stops: List<Stop>): Boolean = group.id == stopGroupId(stops.map { it.id })

/** The saved favourite of exactly [stops], or null (always for fewer than two stops). */
fun findStopGroup(groups: List<StopGroup>, stops: List<Stop>): StopGroup? {
    if (stops.distinctBy { it.id }.size < 2) return null
    return groups.firstOrNull { sameStops(it, stops) }
}

/** [groups] with [group] at the end, in place of an earlier favourite of the same stops. */
fun withStopGroup(groups: List<StopGroup>, group: StopGroup): List<StopGroup> = groups.filter { it.id != group.id } + group

/** [groups] without the favourite of [stops]. */
fun withoutStopGroup(groups: List<StopGroup>, stops: List<Stop>): List<StopGroup> = groups.filter { !sameStops(it, stops) }

/** [groups] with [group] back at [index] (an undone removal), unless it is there already. */
fun restoreStopGroup(groups: List<StopGroup>, group: StopGroup, index: Int): List<StopGroup> {
    if (groups.any { it.id == group.id }) return groups
    val at = index.coerceIn(0, groups.size)
    return groups.take(at) + group + groups.drop(at)
}

/**
 * The favourites as read back from storage: each of at least two stops (each once), every set
 * of stops once (the first wins), names trimmed. What was saved comes back as it was.
 */
fun storedStopGroups(stored: List<StopGroup>): List<StopGroup> =
    stored.mapNotNull { stopGroupOf(it.stops, it.name) }.distinctBy { it.id }
