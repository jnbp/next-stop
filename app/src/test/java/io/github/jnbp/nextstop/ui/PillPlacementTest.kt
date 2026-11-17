package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pills of the marked stop switch between their pin and a side without flicker. */
class PillPlacementTest {
    private val zoom = 18.0
    private val latitude = 52.5
    private val longitude = 13.4
    private val perDp = metersPerDp(zoom, latitude)

    // A pill 60 by 30 dp on its pin: its box spans x -30..30 and y -30..0 around the position.
    private val pill = PillBox(latitude, longitude, widthDp = 60f, heightDp = 30f, priority = 1)

    /** A sign 20 by 20 dp whose middle stands [dx] dp right of the position, level with the pill's middle. */
    private fun signAt(dx: Double): SignBox {
        val east = dx * perDp / (111_320.0 * Math.cos(Math.toRadians(latitude)))
        val north = 15.0 * perDp / 111_320.0
        return SignBox(latitude + north, longitude + east, halfWidthDp = 10f, halfHeightDp = 10f)
    }

    private fun side(dx: Double, previous: PillSide? = null): PillSide =
        placePills(listOf(pill), listOf(signAt(dx)), zoom, previous = previous?.let { listOf(it) }).single()

    @Test
    fun pillOnItsPinHoldsOnWhileASignJustTouchesIt() {
        // The sign (with its padding of 3 dp) reaches 2 dp into the pill's box.
        assertEquals(PillSide.Left, side(41.0))
        assertEquals(PillSide.Above, side(41.0, previous = PillSide.Above))
    }

    @Test
    fun pillAsideComesBackOnlyWhenItsPinIsClearlyFree() {
        // Clear by 2 dp: the pill aside stays there, a new one goes onto its pin.
        assertEquals(PillSide.Above, side(45.0))
        assertEquals(PillSide.Left, side(45.0, previous = PillSide.Left))
        // Clear by 7 dp: back onto its pin.
        assertEquals(PillSide.Above, side(50.0, previous = PillSide.Left))
    }

    @Test
    fun switchingIsNotSuppressedWhereItIsClearlyDue() {
        assertTrue(side(30.0, previous = PillSide.Above) != PillSide.Above)
        assertEquals(PillSide.Above, side(200.0, previous = PillSide.Left))
    }

    @Test
    fun switchedPillsAreTheOnesThatMoved() {
        assertEquals(setOf(1), switchedPills(listOf(PillSide.Above, PillSide.Above), listOf(PillSide.Above, PillSide.Right)))
        assertEquals(emptySet<Int>(), switchedPills(null, listOf(PillSide.Above)))
        assertEquals(emptySet<Int>(), switchedPills(listOf(PillSide.Above), listOf(PillSide.Above, PillSide.Left)))
    }
}
