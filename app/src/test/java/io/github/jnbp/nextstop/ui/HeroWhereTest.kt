package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.Leg
import io.github.jnbp.nextstop.data.Stop
import io.github.jnbp.nextstop.data.WalkTarget
import io.github.jnbp.nextstop.data.WalkTargetKind
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * #211: waiting for the bus, the hero said "Warte auf X1 · kommt ca. 21:34 · ab Gleis 2 · 5′
 * Umstieg": track 2 and the 5 minutes were those of the change after the bus, to the underground
 * line U99 at "Example Change". The sub-line named the change after the ride shown whether or not
 * the traveller was on it. Before boarding it names where the ride waited for is boarded.
 */
class HeroWhereTest {
    private fun at(clock: String): OffsetDateTime = OffsetDateTime.parse("2026-11-18T$clock:00+01:00")

    private val start = Stop("Example Start", "Example Start", 50.00, 10.0, null, setOf("bus"))
    private val change = Stop("Example Change", "Example Change", 50.06, 10.0, null, setOf("bus", "subway"))
    private val end = Stop("Example End", "Example End", 50.10, 10.0, null, setOf("subway"))

    private val spot = WalkTarget(WalkTargetKind.StopPosition, "3", 50.0001, 10.0001)
    private val bus = Leg(
        "X1", "bus", false, start.name, change.name, at("21:34"), at("21:34"), at("21:50"), at("21:50"), null, null, null, false,
        fromPlace = start, toPlace = change, boardingSpot = spot,
    )
    private val subway = Leg(
        "U99", "subway", false, change.name, end.name, at("21:56"), at("21:56"), at("22:06"), at("22:06"), "2", null, null, false,
        fromPlace = change, toPlace = end,
    )
    private val journey = Journey(listOf(bus, subway))

    @Test
    fun waitingForTheBusNamesItsStopPositionNotTheNextPlatform() {
        val step = TripStep(StepKind.Board, at("21:34"), bus, bus.from, waiting = true)
        assertEquals(HeroWhere.Board, heroWhere(step, onBoard = false))
        assertEquals(BoardPlace("3", null), boardPlace(step.leg!!))
    }

    @Test
    fun aBusWithoutAKnownStopPositionNamesNothing() {
        val plain = bus.copy(boardingSpot = null)
        assertNull(boardPlace(plain))
        // A stop point id from the provider is no platform either.
        assertNull(boardPlace(plain.copy(departurePlatform = "de:11000:900062202:3:78")))
    }

    @Test
    fun waitingForATrainNamesItsOwnPlatform() {
        val step = TripStep(StepKind.Board, at("21:56"), subway, subway.from, waiting = true)
        assertEquals(HeroWhere.Board, heroWhere(step, onBoard = false))
        assertEquals(BoardPlace(null, "2"), boardPlace(subway))
    }

    @Test
    fun onBoardTheChangeAfterItIsNamed() {
        val step = TripStep(StepKind.GetOff, at("21:50"), bus, bus.to)
        assertEquals(HeroWhere.Change, heroWhere(step, onBoard = true))
        // Getting off by the times alone, the ride not shown as on board: nothing of the change.
        assertEquals(HeroWhere.None, heroWhere(step, onBoard = false))
    }

    @Test
    fun aMissedRideNamesNoPlace() {
        val step = TripStep(StepKind.Board, at("21:34"), bus, bus.from, waiting = true, missed = true)
        assertEquals(HeroWhere.None, heroWhere(step, onBoard = false))
        assertEquals(HeroWhere.None, heroWhere(TripStep(StepKind.Arrived, at("22:06"), null, end.name), onBoard = false))
        assertEquals(2, journey.legs.size)
    }
}
