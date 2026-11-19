package io.github.jnbp.nextstop.data

/*
 * The mode filters in the header of the departures (#223): "Alle · S · U · <bus> ...", one
 * compact chip per kind of transport that leaves at the stop, S-Bahn and U-Bahn by their
 * letter, the others by a small icon (tram, bus, ferry, train), long-distance and regional
 * trains with a short word beside the train, as they read on the platform.
 */

/** The small icon of a mode filter (drawn in DepartureHeader.kt). */
enum class ModeIcon { Tram, Bus, Ferry, Train }

/**
 * What a mode filter shows: a [letter], or an [icon]; beside the icon "RE" for a regional train
 * ([regional]) or the word for long distance ([longDistance], "Fern", a string of the app).
 */
data class ModeFace(
    val letter: String? = null,
    val icon: ModeIcon? = null,
    val regional: Boolean = false,
    val longDistance: Boolean = false,
)

/** The face of the mode filter of [product] (see DEPARTURE_PRODUCTS); unknown kinds show their name. */
fun departureModeFace(product: String): ModeFace = when (product) {
    "suburban" -> ModeFace(letter = "S")
    "subway" -> ModeFace(letter = "U")
    "tram" -> ModeFace(icon = ModeIcon.Tram)
    "bus" -> ModeFace(icon = ModeIcon.Bus)
    "coach" -> ModeFace(icon = ModeIcon.Bus, longDistance = true)
    "ferry" -> ModeFace(icon = ModeIcon.Ferry)
    "regional" -> ModeFace(icon = ModeIcon.Train, regional = true)
    "express" -> ModeFace(icon = ModeIcon.Train, longDistance = true)
    else -> ModeFace(letter = product.take(3).replaceFirstChar { it.uppercaseChar() })
}

/**
 * The mode filters shown at a stop: the kinds of [present] in the order of [order] (the order
 * of the page), each once; none where only one kind leaves (nothing to choose).
 */
fun departureModeFilters(order: List<String>, present: Collection<String>): List<String> {
    val kinds = order.filter { it in present }
    return if (kinds.size > 1) kinds else emptyList()
}
