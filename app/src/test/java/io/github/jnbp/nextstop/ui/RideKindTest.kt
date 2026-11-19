package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** #212: texts about a ride name its kind, and "Fahrt" where it is not known. */
class RideKindTest {
    @Test
    fun productsOfTheSourcesMapToTheirKind() {
        assertEquals(RideKind.Bus, rideKindOf("bus"))
        assertEquals(RideKind.Bus, rideKindOf("coach"))
        assertEquals(RideKind.Tram, rideKindOf("tram"))
        assertEquals(RideKind.UBahn, rideKindOf("subway"))
        assertEquals(RideKind.SBahn, rideKindOf("suburban"))
        assertEquals(RideKind.Ferry, rideKindOf("ferry"))
        for (train in listOf("express", "regional", "national", "nationalExpress", "regionalExpress")) {
            assertEquals(train, RideKind.Train, rideKindOf(train))
        }
    }

    @Test
    fun noProductOrAnUnknownOneIsNeutral() {
        assertEquals(RideKind.Unknown, rideKindOf(null))
        assertEquals(RideKind.Unknown, rideKindOf(""))
        assertEquals(RideKind.Unknown, rideKindOf("taxi"))
    }
}
