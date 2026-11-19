package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The short text of the compact chip of transport left out. */
class SearchLimitChipsTest {
    private val more: (String, Int) -> String = { first, count -> "$first +$count" }

    @Test
    fun oneKindIsNamed() {
        assertEquals("Fernbus", productsOffShort(listOf("Fernbus"), more))
    }

    @Test
    fun moreKindsShowTheFirstAndHowManyMore() {
        assertEquals("Fernverkehr +1", productsOffShort(listOf("Fernverkehr", "Fernbus"), more))
        assertEquals("Bus +2", productsOffShort(listOf("Bus", "Fähre", "Fernbus"), more))
    }

    @Test
    fun noneIsNothing() {
        assertNull(productsOffShort(emptyList(), more))
    }
}
