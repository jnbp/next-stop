package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.PickerMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** B4: the detail page opens its connection pickers by the same setting as the Live-Fahrt; done parts only while followed. */
class BlueChangeRulesTest {
    @Test
    fun foldedOpensOnlyWhatWasTapped() {
        assertFalse(detailPickerOpen(PickerMode.Folded, keepLegs = 1, firstKeepLegs = 1, toggled = false))
        assertFalse(detailPickerOpen(PickerMode.Folded, keepLegs = 3, firstKeepLegs = 1, toggled = false))
        assertTrue(detailPickerOpen(PickerMode.Folded, keepLegs = 3, firstKeepLegs = 1, toggled = true))
    }

    @Test
    fun allOpenOpensEveryChangeAndCannotBeFolded() {
        assertTrue(detailPickerOpen(PickerMode.AllOpen, keepLegs = 1, firstKeepLegs = 1, toggled = false))
        // "Alle ausgeklappt" only starts open: a tap on the link folds it (r7plan).
        assertFalse(detailPickerOpen(PickerMode.AllOpen, keepLegs = 3, firstKeepLegs = 1, toggled = true))
    }

    @Test
    fun nextOpenOpensTheFirstChangeOnTheDetail() {
        assertTrue(detailPickerOpen(PickerMode.NextOpen, keepLegs = 1, firstKeepLegs = 1, toggled = false))
        assertFalse(detailPickerOpen(PickerMode.NextOpen, keepLegs = 3, firstKeepLegs = 1, toggled = false))
        // Folded by hand, and a later one opened by hand.
        assertFalse(detailPickerOpen(PickerMode.NextOpen, keepLegs = 1, firstKeepLegs = 1, toggled = true))
        assertTrue(detailPickerOpen(PickerMode.NextOpen, keepLegs = 3, firstKeepLegs = 1, toggled = true))
        // No change known: nothing opens by itself.
        assertFalse(detailPickerOpen(PickerMode.NextOpen, keepLegs = 1, firstKeepLegs = null, toggled = false))
    }

    @Test
    fun partsAreOverOnlyWhileFollowedAndOnceTheNextRideLeft() {
        assertFalse(detailPartOver(followed = false, nextDeparture = 1_000L, now = 5_000L))
        assertFalse(detailPartOver(followed = true, nextDeparture = 9_000L, now = 5_000L))
        assertTrue(detailPartOver(followed = true, nextDeparture = 5_000L, now = 5_000L))
        assertFalse(detailPartOver(followed = true, nextDeparture = null, now = 5_000L))
    }

    @Test
    fun theDetailWakesAtTheNextDepartureOnly() {
        assertEquals(7_000L, detailNextWake(listOf(1_000L, 9_000L, 7_000L), now = 5_000L))
        assertNull(detailNextWake(listOf(1_000L, 5_000L), now = 5_000L))
    }

    @Test
    fun threeChipsFitWhereTheirWidthsAndGapsDo() {
        assertTrue(blueActionsFit(listOf(120, 110, 120), gap = 8, room = 366))
        assertFalse(blueActionsFit(listOf(120, 110, 120), gap = 8, room = 365))
    }

    @Test
    fun aButtonNotShownTakesNoRoomAndNoGap() {
        assertTrue(blueActionsFit(listOf(0, 110, 120), gap = 8, room = 238))
        assertTrue(blueActionsFit(listOf(0, 0, 0), gap = 8, room = 0))
    }

    @Test
    fun theOpenMapLabelDecidesAndGoogleMapsNeverBreaksAWord() {
        // #206 on a phone: "Karte zeigen" 130, "Karte öffnen" 125, Google Maps 125 (its longest word 85), icon 40.
        assertEquals(BlueActionsLook.Chips, blueActionsLook(130, 125, 125, 85, 40, gap = 8, room = 400))
        // The map open: "Karte ausblenden" 165 no longer leaves room for three chips.
        assertEquals(BlueActionsLook.OpenAsIcon, blueActionsLook(165, 125, 125, 85, 40, gap = 8, room = 400))
        // Narrower still: Google Maps beside the two would break "Google"; it becomes an icon too.
        assertEquals(BlueActionsLook.IconsOnly, blueActionsLook(165, 125, 125, 85, 40, gap = 8, room = 290))
        assertEquals(BlueActionsLook.OpenAsIcon, blueActionsLook(165, 125, 125, 85, 40, gap = 8, room = 306))
    }

    @Test
    fun withoutGoogleMapsOnlyTheOpenButtonTurnsIntoAnIcon() {
        assertEquals(BlueActionsLook.OpenAsIcon, blueActionsLook(165, 125, 0, 0, 40, gap = 8, room = 250))
    }
}
