package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.Journey
import java.time.OffsetDateTime

/**
 * The journeys the list of connections shows (#150). It starts with the next departure; the
 * journeys that left already show once the row "Frühere Verbindungen" was tapped
 * ([earlierShown]), and always for a search at a picked time ([timed]). The picked journey
 * ([currentId]) stays even after it has left, so the pick is never lost.
 */
fun listedJourneys(
    journeys: List<Journey>,
    timed: Boolean,
    earlierShown: Boolean,
    now: OffsetDateTime,
    currentId: String?,
): List<Journey> =
    if (timed || earlierShown) journeys else journeys.filter { !it.departure.isBefore(now) || it.id == currentId }

/**
 * How far the list scrolls on, in pixels, so the row that was first ([anchorBefore], its top
 * before earlier rows came in above it) stays where it was ([anchorAfter], its top now).
 * Never back: rows that went away above it are not made up for.
 */
fun earlierScrollShift(anchorBefore: Int, anchorAfter: Int): Int = (anchorAfter - anchorBefore).coerceAtLeast(0)
