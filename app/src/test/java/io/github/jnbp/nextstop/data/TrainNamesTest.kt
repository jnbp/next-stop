package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Trains whose provider gives only their number get their kind in front (see withTrainKind). */
class TrainNamesTest {
    @Test
    fun railjetFromItsProductName() {
        assertEquals("RJ 174", withTrainKind("174", "express", "174", "RJ 174"))
        assertEquals("RJ 174", withTrainKind("174", "express", null, null, "RJ"))
        assertEquals("RJ 174", withTrainKind("174", "express", null, "Railjet"))
    }

    @Test
    fun aNameOfOnlyTheKindTakesTheTripNumber() {
        assertEquals("RJ 174", withTrainKind("RJ", "express", "174"))
        assertEquals("RJ 174", withTrainKind("Railjet", "express", "174"))
        assertEquals("EC 171", withTrainKind("EuroCity", "", "171"))
        assertEquals("RE 3912", withTrainKind("RE", "", "3912"))
        // Without a trip number the name stays as it is; a bus never takes one.
        assertEquals("Railjet", withTrainKind("Railjet", "express", null))
        assertEquals("Bus", withTrainKind("Bus", "bus", "174"))
    }

    @Test
    fun euroCityFromTripNumberAndCategory() {
        assertEquals("EC 174", withTrainKind(null, "express", "174", "EC"))
        assertEquals("EC 174", withTrainKind("", "express", "174", "EuroCity"))
        assertEquals("EC 174", withTrainKind("174", "regional", null, "ec"))
    }

    @Test
    fun noKindKnownKeepsTheBareNumber() {
        // Never a guessed kind: "Zug" or an operator's name tell none.
        assertEquals("174", withTrainKind("174", "express", null, "Zug", "České dráhy"))
        assertEquals("174", withTrainKind(null, "express", "174"))
        assertTrue(isBareTrainNumber("174", "express"))
        assertTrue(isBareTrainNumber("174", "regional"))
    }

    @Test
    fun namesThatAreMoreThanANumberStay() {
        assertEquals("RE1", withTrainKind("RE1", "regional", "3809", "RE"))
        assertEquals("ICE 29", withTrainKind("ICE 29", "express", null, "IC"))
        assertFalse(isBareTrainNumber("RJ 174", "express"))
    }

    @Test
    fun busesKeepTheirNumbers() {
        assertEquals("174", withTrainKind("174", "bus", null, "RJ"))
        assertFalse(isBareTrainNumber("174", "bus"))
        assertFalse(isBareTrainNumber("174", ""))
        // A ride of no named product: a kind given counts, a trip number alone does not.
        assertEquals("RJ 174", withTrainKind("174", "", null, "RJ"))
        assertNull(withTrainKind(null, "", "174"))
    }

    @Test
    fun flixTrainByItsOperator() {
        assertEquals("FLX 1234", withTrainKind("1234", "express", null, "FlixTrain"))
        assertEquals("FLX", trainKindOf("FlixTrain"))
        assertEquals("ICE", trainKindOf("Intercity-Express"))
        assertNull(trainKindOf("Bus"))
        assertNull(trainKindOf("Zug"))
    }

    @Test
    fun flixBusReadsFlixBusAndItsLine() {
        assertEquals("FlixBus 260", flixLineName("FLX 260", "coach"))
        assertEquals("FlixBus 260", flixLineName("Flix 260", "bus"))
        assertEquals("FlixBus N1334", flixLineName("FlixBus N1334", "coach"))
        assertEquals("FlixBus N1334", flixLineName("flixbus n1334", "coach"))
        assertEquals("FlixBus", flixLineName("Flixbus", "coach"))
        assertEquals("FlixBus 040", rapidLineName("FLX 040", "coach"))
    }

    @Test
    fun flixTrainReadsFlxAndItsNumber() {
        assertEquals("FLX 30", flixLineName("FLX30", "express"))
        assertEquals("FLX 1234", flixLineName("FlixTrain 1234", "express"))
        assertEquals("FlixTrain", flixLineName("FlixTrain", "express"))
        assertEquals("FLX 30", rapidLineName("flx 30", "express"))
        // Other names stay.
        assertEquals("ICE 29", flixLineName("ICE 29", "express"))
        assertEquals("Flixbahnhof", flixLineName("Flixbahnhof", "bus"))
    }
}
