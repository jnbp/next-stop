package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.Stop
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceSearchListTest {
    private fun stop(id: String, name: String) = Stop(id, name, 52.5, 13.4, null)

    private val alex = stop("1", "S+U Alexanderplatz")
    private val alt = stop("2", "Alt-Mariendorf")
    private val zoo = stop("3", "S+U Zoologischer Garten")

    @Test
    fun earlierResultsThatStillFitStayWhileTyping() {
        assertEquals(listOf(alex, alt), keptWhileTyping(listOf(alex, alt, zoo), "Al"))
        assertEquals(listOf(alex), keptWhileTyping(listOf(alex, alt, zoo), "Alex"))
    }

    @Test
    fun tooShortKeepsNothing() {
        assertEquals(emptyList<Stop>(), keptWhileTyping(listOf(alex), "A"))
    }

    @Test
    fun historyAndFavouritesFirstEachOnce() {
        val here = stop("current-location", "Mein Standort")
        val listing = searchListing(listOf(here), listOf(alex), listOf(zoo, alex), listOf(alt, zoo, alex))
        assertEquals(listOf(here, alex, zoo, alt), listing)
    }
}
