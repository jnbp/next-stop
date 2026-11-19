package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** The menu of a stop offers the same entries everywhere, in one order (#139). */
class StopMenuEntriesTest {
    @Test
    fun allSixInTheirOrder() {
        assertEquals(
            listOf(StopAction.From, StopAction.To, StopAction.Departures, StopAction.Map, StopAction.Pin, StopAction.Favorite),
            stopMenuEntries(pinnable = true, favorites = true),
        )
    }

    @Test
    fun withoutPinningOrFavouritesTheyAreLeftOut() {
        assertEquals(
            listOf(StopAction.From, StopAction.To, StopAction.Departures, StopAction.Map),
            stopMenuEntries(pinnable = false, favorites = false),
        )
    }

    @Test
    fun whatThePlaceOffersItselfIsOmitted() {
        assertEquals(
            listOf(StopAction.From, StopAction.To, StopAction.Departures, StopAction.Pin, StopAction.Favorite),
            stopMenuEntries(pinnable = true, favorites = true, omit = setOf(StopAction.Map)),
        )
    }
}
