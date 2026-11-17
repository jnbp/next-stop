package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one access point a walk leaves its station by, and its way through it (see WalkAccess,
 * leavingBy). Places in metres east and north of 52.5° N, 13.4° E.
 */
class WalkAccessTest {
    private fun p(east: Double, north: Double) =
        (52.5 + north / 111_320.0) to (13.4 + east / (111_320.0 * Math.cos(Math.toRadians(52.5))))

    private fun door(label: String?, east: Double, north: Double) =
        WalkTarget(WalkTargetKind.Entrance, label, p(east, north).first, p(east, north).second)

    private fun lift(east: Double, north: Double, level: String? = null) =
        WalkTarget(WalkTargetKind.Elevator, null, p(east, north).first, p(east, north).second, level = level)

    // The provider's way: along the platform to an elevator at its east end, up, north along the
    // street and east to the bus stop.
    private val way = listOf(p(0.0, 0.0), p(60.0, 0.0), p(60.0, 80.0), p(150.0, 80.0))
    private val exitA = door("A", -5.0, 15.0)
    private val elevator = lift(60.0, 0.0)

    @Test
    fun theProvidersWayGoesOutByTheElevatorNotByTheNearestStairs() {
        val used = WalkAccess.usedBy(way, listOf(exitA, elevator))
        assertSame(elevator, used)
        // And the way stays through it: platform, elevator, street, stop.
        assertEquals(way, WalkAccess.pathOut(way, used!!))
    }

    @Test
    fun aLiftInsideFollowedByADoorLeavesByTheDoor() {
        val inner = lift(30.0, 0.0)
        val doorB = door("B", 60.0, 0.0)
        assertSame(doorB, WalkAccess.usedBy(way, listOf(inner, doorB)))
    }

    @Test
    fun aLiftKnownToStayBelowTheStreetIsNoWayOut() {
        assertNull(WalkAccess.usedBy(way, listOf(lift(60.0, 0.0, level = "-2;-1"))))
        val toStreet = lift(60.0, 0.0, level = "-1;0")
        assertSame(toStreet, WalkAccess.usedBy(way, listOf(toStreet)))
    }

    @Test
    fun aStraightWalkHasNoWayToTellAndStartsAtTheChosenExit() {
        val straight = listOf(p(0.0, 0.0), p(150.0, 80.0))
        assertNull(WalkAccess.usedBy(straight, listOf(exitA, elevator)))
        assertEquals(listOf(exitA.position, straight.last()), WalkAccess.pathOut(straight, exitA))
    }

    @Test
    fun aWayNotThroughTheExitIsLedFromIt() {
        val doorC = door("C", 70.0, 40.0)
        val led = WalkAccess.pathOut(way, doorC)
        assertEquals(doorC.position, led.first())
        assertEquals(way.last(), led.last())
        // The stretch inside, by the elevator, is no longer part of it.
        assertTrue(led.none { PathJoin.meters(it, elevator.position) < 5.0 })
    }

    @Test
    fun refreshedWalksKeepWayAndExitAsOne() {
        val raw = walk(way)
        val refined = walk(listOf(exitA.position, way.last()), exit = exitA)
        // Known with its exit: the fresh data does not bring another exit to the known way.
        val fresh = walk(listOf(elevator.position, way.last()), exit = elevator)
        val kept = WalkAccess.keptWay(fresh, refined)
        assertEquals(refined.path, kept.path)
        assertSame(exitA, kept.walkExit)
        // Known before the station data was there: the fresh walk as a whole.
        assertSame(fresh, WalkAccess.keptWay(fresh, raw))
        // Nothing to lead on either side: the known way.
        assertEquals(way, WalkAccess.keptWay(walk(listOf(way.first(), way.last())), raw).path)
    }

    @Test
    fun leavingByTheExitNamesNoOtherDoorAndNothingInTheStreet() {
        val inside = WalkPassage(PassageKind.Escalator, null, p(20.0, 0.0).first, p(20.0, 0.0).second, 0.1)
        val a = WalkPassage(PassageKind.Entrance, "A", exitA.latitude, exitA.longitude, 0.3, leaving = true)
        val street = WalkPassage(PassageKind.Lift, null, p(-3.0, 18.0).first, p(-3.0, 18.0).second, 0.35)
        val c = WalkPassage(PassageKind.Entrance, "C", p(70.0, 40.0).first, p(70.0, 40.0).second, 0.6, leaving = true)
        val all = listOf(inside, a, street, c)
        assertEquals(listOf(inside, a), leavingBy(all, exitA))
        assertEquals(all, leavingBy(all, null))
        // Out by the elevator: no door at all, the elevator, nothing after it.
        val up = WalkPassage(PassageKind.Lift, null, elevator.latitude, elevator.longitude, 0.2)
        assertEquals(listOf(inside, up), leavingBy(listOf(inside, up, a, street), elevator))
    }

    @Test
    fun theWayUpToAnExitIsTheEscalatorBesideItElseTheStairs() {
        val stairs = WalkAccess.withWayUp(exitA, -1.0, escalators = listOf(p(60.0, 0.0)))
        assertEquals(PassageKind.Stairs, stairs.climbBy)
        assertEquals(1, stairs.climb)
        val escalator = WalkAccess.withWayUp(exitA, -1.0, escalators = listOf(p(-5.0, 5.0)))
        assertEquals(PassageKind.Escalator, escalator.climbBy)
        // An elevated platform: down to the street.
        assertEquals(-1, WalkAccess.withWayUp(exitA, 1.0, emptyList()).climb)
        // Levels not known: nothing to say.
        assertNull(WalkAccess.withWayUp(exitA, null, emptyList()).climbBy)
        val up = WalkAccess.withWayUp(lift(60.0, 0.0, level = "-1;0"), -1.0, emptyList())
        assertEquals(PassageKind.Lift, up.climbBy)
        assertEquals(1, up.climb)
    }

    @Test
    fun stepFreeTravellersGetTheElevatorOnTheShortestWay() {
        val far = lift(-200.0, 0.0)
        val below = lift(140.0, 70.0, level = "-2;-1")
        val chosen = WalkAccess.nearestElevator(listOf(exitA, far, elevator, below), p(150.0, 80.0), listOf(p(-20.0, 0.0) to p(80.0, 0.0)), null)
        assertSame(elevator, chosen)
        assertNull(WalkAccess.nearestElevator(listOf(exitA), p(150.0, 80.0), emptyList(), null))
    }

    @Test
    fun theTextSaysTheStairsUpToTheExit() {
        val a = WalkPassage(PassageKind.Entrance, "A", exitA.latitude, exitA.longitude, 0.0, leaving = true)
        val exit = WalkAccess.withWayUp(exitA, -1.0, emptyList())
        val said = withClimbToExit(listOf(a), exit)
        assertEquals(listOf(PassageKind.Stairs, PassageKind.Entrance), said.map { it.kind })
        assertEquals(1, said.first().climb)
        // An escalator on the way before the door says it already.
        val inside = WalkPassage(PassageKind.Escalator, null, p(20.0, 0.0).first, p(20.0, 0.0).second, 0.0, climb = 1)
        assertEquals(listOf(inside, a), withClimbToExit(listOf(inside, a), exit))
        // Out by the elevator: it goes up.
        val lifted = WalkPassage(PassageKind.Lift, null, elevator.latitude, elevator.longitude, 0.2)
        val byLift = WalkAccess.withWayUp(lift(60.0, 0.0, level = "-1;0"), -1.0, emptyList())
        assertEquals(1, withClimbToExit(listOf(lifted), byLift).single().climb)
    }

    private val time: OffsetDateTime = OffsetDateTime.parse("2026-01-01T13:40:00+01:00")

    private fun walk(path: List<Pair<Double, Double>>, exit: WalkTarget? = null) = Leg(
        null, "walking", true, "U Station", "Bus stop", time, time, time.plusMinutes(3), time.plusMinutes(3),
        null, null, null, false, path = path, walkExit = exit,
    )
}
