package io.github.jnbp.nextstop.data

/** What the menu of a stop offers (see StopMenu): the same everywhere a stop is tapped (#139). */
enum class StopAction { From, To, Departures, Map, Pin, Favorite }

/**
 * The entries of the menu of a stop, in their order: "Von hier", "Hierhin", "Abfahrten hier",
 * "Auf der Karte zeigen", "Auf den Startbildschirm" where the launcher takes pinned shortcuts
 * ([pinnable]) and "Als Lieblingshaltestelle" (or taking it back) where favourites can be kept
 * ([favorites]); without what the place it opens from offers on its own ([omit]).
 */
fun stopMenuEntries(pinnable: Boolean, favorites: Boolean, omit: Set<StopAction> = emptySet()): List<StopAction> =
    listOfNotNull(
        StopAction.From,
        StopAction.To,
        StopAction.Departures,
        StopAction.Map,
        StopAction.Pin.takeIf { pinnable },
        StopAction.Favorite.takeIf { favorites },
    ).filter { it !in omit }
