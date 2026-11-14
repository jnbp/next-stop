package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Vehicles on their way at the same place line up beside a dot instead of hiding each other. */
class VehicleClustersTest {
    private val latitude = 52.5
    private val longitude = 13.4

    // A point [eastDp] and [southDp] dp of the screen from the reference point at [zoom].
    private fun at(key: String, label: String, eastDp: Double, southDp: Double, zoom: Double): FreeVehicle {
        val perDp = metersPerDp(zoom, latitude)
        val north = -southDp * perDp / 111_320.0
        val east = eastDp * perDp / (111_320.0 * Math.cos(Math.toRadians(latitude)))
        return FreeVehicle(key, latitude + north, longitude + east, label)
    }

    @Test
    fun metersPerDpHalvesWithEveryZoomLevel() {
        val at15 = metersPerDp(15.0, latitude)
        assertEquals(at15 / 2, metersPerDp(16.0, latitude), 1e-9)
        // About 1.45 m per dp at zoom 15 in Berlin.
        assertTrue(at15 in 1.3..1.6)
    }

    @Test
    fun overlapCountsWidthAndHeight() {
        val half = badgeHalfWidthDp("M41")
        assertTrue(badgesOverlap(0.0, 0.0, half, half))
        assertTrue(badgesOverlap(2 * half - 1.0, 0.0, half, half))
        assertFalse(badgesOverlap(2 * half + CLUSTER_GAP_DP + 1.0, 0.0, half, half))
        assertFalse(badgesOverlap(0.0, BADGE_HEIGHT_DP + CLUSTER_GAP_DP + 1.0, half, half))
        // Within the slack they still count.
        assertTrue(badgesOverlap(0.0, BADGE_HEIGHT_DP + CLUSTER_GAP_DP + 1.0, half, half, slack = CLUSTER_KEEP_DP))
    }

    @Test
    fun twoBusesOnTheSameSpotFormOneCluster() {
        val vehicles = listOf(
            at("a", "M41", 0.0, 0.0, 16.0),
            at("b", "171", 1.0, 0.5, 16.0),
            at("far", "M29", 200.0, 0.0, 16.0),
        )
        val clusters = findClusters(vehicles, 16.0)
        assertEquals(1, clusters.size)
        val cluster = clusters[0]
        assertEquals(listOf("a", "b"), cluster.keys)
        assertEquals(clusterId("a"), cluster.id)
        // The middle lies between the two.
        assertTrue(cluster.latitude in minOf(vehicles[0].latitude, vehicles[1].latitude)..maxOf(vehicles[0].latitude, vehicles[1].latitude))
        assertTrue(cluster.longitude in vehicles[0].longitude..vehicles[1].longitude)
    }

    @Test
    fun theThresholdFollowsTheZoom() {
        // 40 m apart: overlapping when zoomed out, apart when zoomed in.
        val perDpFar = metersPerDp(14.0, latitude)
        val perDpNear = metersPerDp(18.0, latitude)
        val meters = 40.0
        val zoomedOut = listOf(at("a", "100", 0.0, 0.0, 14.0), at("b", "200", meters / perDpFar, 0.0, 14.0))
        assertEquals(1, findClusters(zoomedOut, 14.0).size)
        val zoomedIn = listOf(at("a", "100", 0.0, 0.0, 18.0), at("b", "200", meters / perDpNear, 0.0, 18.0))
        assertTrue(findClusters(zoomedIn, 18.0).isEmpty())
    }

    @Test
    fun noClustersWhereNoBadgesAreDrawn() {
        val vehicles = listOf(at("a", "S1", 0.0, 0.0, 12.0), at("b", "S2", 0.0, 0.0, 12.0))
        assertTrue(findClusters(vehicles, 12.0).isEmpty())
        assertTrue(findClusters(vehicles.take(1), 16.0).isEmpty())
    }

    @Test
    fun overlapChainsJoinOneCluster() {
        val half = badgeHalfWidthDp("100")
        val step = 2 * half - 2.0
        val vehicles = listOf(
            at("a", "100", 0.0, 0.0, 16.0),
            at("b", "100", step, 0.0, 16.0),
            at("c", "100", 2 * step, 0.0, 16.0),
        )
        val clusters = findClusters(vehicles, 16.0)
        assertEquals(1, clusters.size)
        assertEquals(listOf("a", "b", "c"), clusters[0].keys)
    }

    @Test
    fun aPairHoldsTogetherALittleLongerThanItForms() {
        // Just apart: no cluster of its own ...
        val gap = BADGE_HEIGHT_DP + CLUSTER_GAP_DP + CLUSTER_KEEP_DP / 2
        val vehicles = listOf(at("a", "M41", 0.0, 0.0, 16.0), at("b", "M41", 0.0, gap, 16.0))
        assertTrue(findClusters(vehicles, 16.0).isEmpty())
        // ... but a pair that was together in the last frame stays together.
        val previous = mapOf("a" to clusterId("a"), "b" to clusterId("a"))
        assertEquals(listOf("a", "b"), findClusters(vehicles, 16.0, previous).single().keys)
        // Vehicles that were in different clusters do not hold together.
        val apart = mapOf("a" to clusterId("a"), "b" to clusterId("b"))
        assertTrue(findClusters(vehicles, 16.0, apart).isEmpty())
        // Further apart than the slack, the pair splits.
        val farther = listOf(at("a", "M41", 0.0, 0.0, 16.0), at("b", "M41", 0.0, gap + CLUSTER_KEEP_DP, 16.0))
        assertTrue(findClusters(farther, 16.0, previous).isEmpty())
    }

    @Test
    fun manyVehiclesFindTheSameClustersAsComparingAll() {
        val random = java.util.Random(7)
        val vehicles = (0 until 300).map { i ->
            at("v$i", listOf("1", "M41", "S41", "RE20", "X7").random(kotlin.random.Random(i)), random.nextDouble() * 2000, random.nextDouble() * 2000, 16.0)
        }
        val clusters = findClusters(vehicles, 16.0)
        // Every pair whose badges overlap is in the same cluster.
        val clusterOf = HashMap<String, String>()
        clusters.forEach { cluster -> cluster.keys.forEach { clusterOf[it] = cluster.id } }
        val perDp = metersPerDp(16.0, latitude)
        for (i in vehicles.indices) for (j in i + 1 until vehicles.size) {
            val a = vehicles[i]
            val b = vehicles[j]
            val dx = (b.longitude - a.longitude) * 111_320.0 * Math.cos(Math.toRadians(latitude)) / perDp
            val dy = (b.latitude - a.latitude) * 111_320.0 / perDp
            // A hair of margin: the projection here and in findClusters differ in the last digits.
            if (badgesOverlap(dx, dy, badgeHalfWidthDp(a.label), badgeHalfWidthDp(b.label), slack = -0.5f)) {
                assertTrue(clusterOf[a.key] != null && clusterOf[a.key] == clusterOf[b.key])
            }
        }
    }

    @Test
    fun aClusterIsLaidOutLikeAStationOrderedByProductLineAndDirection() {
        val layout = layoutGroup(
            CLUSTER_DOT_RADIUS_DP,
            listOf(
                GroupMember("bus2", rank = 3, label = "M41", direction = "Sonnenallee"),
                GroupMember("bus1", rank = 3, label = "M41", direction = "Hauptbahnhof"),
                GroupMember("s", rank = 0, label = "S41", direction = "Ring"),
            ),
        )
        val order = layout.slots.entries.sortedBy { it.value.dy }.map { it.key }
        assertEquals(listOf("s", "bus1", "bus2"), order)
        // Right of the dot, never on it.
        for (slot in layout.slots.values) assertTrue(slot.dx > CLUSTER_DOT_RADIUS_DP)
    }

    @Test
    fun theColumnBoxCoversEveryRowBesideTheAnchor() {
        val anchor = GroupAnchor("hbf", latitude, longitude, signHalfWidthDp(1))
        val members = listOf(
            GroupMember("a", rank = 3, label = "142"),
            GroupMember("b", rank = 3, label = "142"),
            GroupMember("c", rank = 3, label = "147"),
        )
        val layout = layoutGroup(anchor.halfWidthDp, members)
        val box = groupColumnBox(anchor, layout, members)!!
        // Right of the sign, as high as three rows.
        assertEquals(anchor.halfWidthDp + GROUP_SIGN_GAP_DP, box.rightDp - box.halfWidthDp, 1e-3f)
        assertEquals(badgeHalfWidthDp("142"), box.halfWidthDp, 1e-3f)
        assertEquals(0f, box.downDp, 1e-3f)
        assertTrue(2 * box.halfHeightDp >= 3 * BADGE_HEIGHT_DP.toFloat())
        assertEquals(null, groupColumnBox(anchor, layoutGroup(anchor.halfWidthDp, emptyList()), emptyList()))
    }

    @Test
    fun aPillOnAColumnOfVehiclesMovesAsideTheVehiclesKeepTheirPlace() {
        // The owner's case at Hauptbahnhof: buses 142, 142, 147 beside the bus stop's sign, and
        // the pill "Pos. 3" of the marked stop right where the column is.
        val zoom = 17.0
        val anchor = GroupAnchor("hbf", latitude, longitude, signHalfWidthDp(1))
        val members = listOf(
            GroupMember("a", rank = 3, label = "142"),
            GroupMember("b", rank = 3, label = "142"),
            GroupMember("c", rank = 3, label = "147"),
        )
        val column = groupColumnBox(anchor, layoutGroup(anchor.halfWidthDp, members), members)!!
        val pin = at("pos3", "", 30.0, 12.0, zoom)
        val pill = PillBox(pin.latitude, pin.longitude, widthDp = 110f, heightDp = 30f, priority = 1, pillWidthDp = 100f)
        assertEquals(PillSide.Above, placePills(listOf(pill), emptyList(), zoom).single())
        assertTrue(placePills(listOf(pill), listOf(column), zoom).single() != PillSide.Above)
    }
}
