package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Google Maps on the last way asks or does what was remembered; other ways just open it (#143). */
class MapsHandoffTest {
    @Test
    fun onlyTheLastWayAsksOrEnds() {
        assertEquals(MapsHandoff.Ask, mapsHandoffStep(lastLeg = true, remembered = MapsHandoff.Ask))
        assertEquals(MapsHandoff.OpenAndEnd, mapsHandoffStep(lastLeg = true, remembered = MapsHandoff.OpenAndEnd))
        assertEquals(MapsHandoff.Open, mapsHandoffStep(lastLeg = true, remembered = MapsHandoff.Open))
        for (remembered in MapsHandoff.entries) {
            assertEquals(MapsHandoff.Open, mapsHandoffStep(lastLeg = false, remembered = remembered))
        }
    }

    @Test
    fun anUnknownStoredChoiceAsks() {
        assertEquals(MapsHandoff.Ask, mapsHandoffOf(null))
        assertEquals(MapsHandoff.Ask, mapsHandoffOf("something"))
        assertEquals(MapsHandoff.OpenAndEnd, mapsHandoffOf("OpenAndEnd"))
    }

    // #189: "Google Maps" in the trip notification decides by the situation, never asks.
    @Test
    fun theNotificationEndsTheTripOnTheLastWalk() {
        assertEquals(MapsHandoff.OpenAndEnd, notificationMapsHandoff(lastWalk = true, walkToChange = false, secondsToArrival = 600, remembered = MapsHandoff.Ask))
        assertEquals(MapsHandoff.OpenAndEnd, notificationMapsHandoff(lastWalk = true, walkToChange = false, secondsToArrival = 600, remembered = MapsHandoff.OpenAndEnd))
        // Who chose "only open" in the app gets that here too.
        assertEquals(MapsHandoff.Open, notificationMapsHandoff(lastWalk = true, walkToChange = false, secondsToArrival = 600, remembered = MapsHandoff.Open))
    }

    @Test
    fun theNotificationEndsTheTripCloseToTheArrival() {
        assertEquals(MapsHandoff.OpenAndEnd, notificationMapsHandoff(lastWalk = false, walkToChange = false, secondsToArrival = 110, remembered = MapsHandoff.Ask))
        assertEquals(MapsHandoff.OpenAndEnd, notificationMapsHandoff(lastWalk = false, walkToChange = false, secondsToArrival = -30, remembered = MapsHandoff.Ask))
        // Three minutes before, on the last ride: no Maps yet.
        assertEquals(null, notificationMapsHandoff(lastWalk = false, walkToChange = false, secondsToArrival = 180, remembered = MapsHandoff.Ask))
    }

    @Test
    fun midTripTheNotificationOnlyOpensMaps() {
        assertEquals(MapsHandoff.Open, notificationMapsHandoff(lastWalk = false, walkToChange = true, secondsToArrival = 1_800, remembered = MapsHandoff.OpenAndEnd))
        assertEquals(null, notificationMapsHandoff(lastWalk = false, walkToChange = false, secondsToArrival = 1_800, remembered = MapsHandoff.OpenAndEnd))
    }
}
