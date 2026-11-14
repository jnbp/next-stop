package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.Stop
import io.github.jnbp.nextstop.data.placeScore
import io.github.jnbp.nextstop.data.searchTokens

/** Characters typed before the providers are asked; history and favourites match from the first. */
const val SEARCH_MIN_CHARS = 2

/** Milliseconds of quiet typing before the providers are asked. */
const val SEARCH_DEBOUNCE_MS = 300L

/** True where [place] fits every word of [text] (see placeScore). */
fun placeMatches(place: Stop, text: String): Boolean = placeScore(searchTokens(text), place, null) >= 0.0

/**
 * What stays in the list while the providers are asked for [text]: the results of an earlier
 * query that still fit what is typed now, in their order, so the list does not empty and
 * refill on every key.
 */
fun keptWhileTyping(previous: List<Stop>, text: String): List<Stop> =
    if (text.trim().length < SEARCH_MIN_CHARS) emptyList() else previous.filter { placeMatches(it, text) }

/**
 * The list of a search, in order and each place once (by id): the own position first,
 * then the places picked before ([history]) and the favourite or saved places ([pinned]),
 * both shown at once without asking anyone, and then the [found] places.
 */
fun searchListing(lead: List<Stop>, history: List<Stop>, pinned: List<Stop>, found: List<Stop>): List<Stop> =
    (lead + history + pinned + found).distinctBy { it.id }
