package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Vehicles at a stop line up beside its sign instead of piling up on it. */
class VehicleGroupsTest {
    // Potsdamer Platz and Anhalter Bahnhof, about 650 m apart.
    private val potsdamer = GroupAnchor("pp", 52.5096, 13.3760, signHalfWidthDp(3))
    private val anhalter = GroupAnchor("ab", 52.5053, 13.3820, signHalfWidthDp(1))
    private val index = AnchorIndex(listOf(potsdamer, anhalter))

    @Test
    fun distanceIsAboutRight() {
        // 0.001 degrees of latitude are about 111 m.
        assertEquals(111.3, metersBetween(52.5, 13.4, 52.501, 13.4), 0.5)
        assertTrue(metersBetween(52.5096, 13.3760, 52.5053, 13.3820) in 550.0..700.0)
    }

    @Test
    fun nearestAnchorWithinTheRadius() {
        assertEquals("pp", index.nearest(52.5100, 13.3762)?.id)
        assertEquals("ab", index.nearest(52.5055, 13.3818)?.id)
        // Halfway between: too far from both.
        assertNull(index.nearest(52.5075, 13.3790))
    }

    @Test
    fun candidateFromAnyPointOfTheWay() {
        // Arrives at Potsdamer Platz at the end of its way.
        val arriving = listOf(52.5130 to 13.3720, 52.5110 to 13.3745, 52.5098 to 13.3758)
        assertEquals("pp", candidateAnchor(index, arriving)?.id)
        val elsewhere = listOf(52.5200 to 13.4000, 52.5210 to 13.4010)
        assertNull(candidateAnchor(index, elsewhere))
        assertTrue(isAt(potsdamer, 52.5098, 13.3758))
        assertFalse(isAt(potsdamer, 52.5130, 13.3720))
    }

    @Test
    fun columnBesideTheSignTrainsFirst() {
        val layout = layoutGroup(
            potsdamer.halfWidthDp,
            listOf(
                GroupMember("bus", rank = 3, label = "200"),
                GroupMember("re", rank = 1, label = "RE20"),
                GroupMember("s1", rank = 0, label = "S1"),
            ),
        )
        val order = layout.slots.entries.sortedBy { it.value.dy }.map { it.key }
        assertEquals(listOf("s1", "re", "bus"), order)
        // Right of the sign, left edges aligned, never on the sign.
        for ((key, slot) in layout.slots) {
            val label = mapOf("bus" to "200", "re" to "RE20", "s1" to "S1").getValue(key)
            assertEquals(potsdamer.halfWidthDp + GROUP_SIGN_GAP_DP, slot.dx - badgeHalfWidthDp(label), 1e-3f)
        }
        // Centred on the sign, rows apart by more than a badge is high.
        val ys = layout.slots.values.map { it.dy }.sorted()
        assertEquals(0f, ys.sum(), 1e-3f)
        assertTrue(ys[1] - ys[0] > BADGE_HEIGHT_DP.toFloat())
        assertNull(layout.chip)
        assertTrue(layout.hidden.isEmpty())
    }

    @Test
    fun oneVehicleSitsLevelWithTheSign() {
        val layout = layoutGroup(10f, listOf(GroupMember("a", 0, "U2")))
        assertEquals(0f, layout.slots.getValue("a").dy, 1e-6f)
    }

    @Test
    fun orderHoldsWhateverTheInputOrder() {
        val members = listOf(GroupMember("b", 0, "S1"), GroupMember("a", 0, "S1"), GroupMember("c", 0, "S2"))
        assertEquals(layoutGroup(10f, members).slots, layoutGroup(10f, members.reversed()).slots)
    }

    @Test
    fun tooManyShowAChip() {
        val members = (1..7).map { GroupMember("t$it", rank = it, label = "S$it") }
        val layout = layoutGroup(10f, members)
        assertEquals(GROUP_MAX_SLOTS - 1, layout.slots.size)
        assertEquals("+4", layout.chipLabel)
        assertEquals(setOf("t4", "t5", "t6", "t7"), layout.hidden)
        val chip = layout.chip!!
        // The chip takes the last row.
        assertTrue(layout.slots.values.all { it.dy < chip.dy })
    }

    @Test
    fun signWidthGrowsWithItsSymbols() {
        assertTrue(signHalfWidthDp(2) > signHalfWidthDp(1))
        assertTrue(signHalfWidthDp(1, ring = true) > signHalfWidthDp(1))
    }

    @Test
    fun aTrainLinesUpOnlyBesideAStationOfItsProduct() {
        // S Nordbahnhof: the bus "H" (S Nordbahnhof/Gartenstr.) lies on the way of the S1 and
        // S2, the station's "S" about 220 m on. The train waits beside the "S", never the "H".
        val gartenstr = GroupAnchor("h", 52.5300, 13.3850, signHalfWidthDp(1), setOf("bus", "tram"))
        val nordbahnhof = GroupAnchor("s", 52.5320, 13.3850, signHalfWidthDp(1), setOf("suburban"))
        val both = AnchorIndex(listOf(gartenstr, nordbahnhof))
        val sBahn = listOf(52.5303 to 13.3850, 52.5313 to 13.3850)
        assertEquals("h", candidateAnchor(both, sBahn)?.id) // by distance alone, as before
        assertEquals("s", candidateAnchor(both, sBahn, "suburban")?.id)
        // With no station of its own near, the train keeps to its way.
        assertNull(candidateAnchor(both, listOf(52.5303 to 13.3850), "suburban"))
        assertNull(both.nearest(52.5301, 13.3850, product = "suburban"))
        assertEquals("h", both.nearest(52.5301, 13.3850, product = "bus")?.id)
    }

    @Test
    fun trainsAndBusesAtBernauerStrasseEachBesideTheirOwnSign() {
        // U Bernauer Str.: the U8 lined up beside the bus "H" (U Bernauer Str./Schönholzer Str.)
        // and the M10 replacement bus beside the U-Bahn sign, swapped.
        val station = GroupAnchor("u", 52.5378, 13.3965, signHalfWidthDp(1), anchorProducts(setOf("subway", "bus")))
        val street = GroupAnchor("h", 52.5385, 13.3975, signHalfWidthDp(1), anchorProducts(setOf("bus")))
        val index = AnchorIndex(listOf(station, street))
        val u8 = listOf(52.5386 to 13.3976, 52.5380 to 13.3965)
        val replacementBus = listOf(52.5378 to 13.3966, 52.5384 to 13.3974)
        assertEquals("h", candidateAnchor(index, u8)?.id) // the old mistake
        assertEquals("u", candidateAnchor(index, u8, "subway")?.id)
        assertEquals("u", candidateAnchor(index, replacementBus)?.id) // the old mistake
        assertEquals("h", candidateAnchor(index, replacementBus, "bus")?.id)
        // A tram finds no sign with trams here.
        assertNull(candidateAnchor(index, replacementBus, "tram"))
    }

    @Test
    fun anchorWithoutProductsTakesEveryVehicle() {
        val cluster = GroupAnchor("c", 52.5, 13.4, 4f)
        assertTrue(cluster.serves("bus"))
        assertTrue(cluster.serves("suburban"))
        assertTrue(GroupAnchor("s", 52.5, 13.4, 4f, setOf("suburban")).serves(null))
        assertFalse(GroupAnchor("s", 52.5, 13.4, 4f, setOf("suburban")).serves("bus"))
    }

    @Test
    fun belowANameTheColumnIsCentredUnderIt() {
        val members = listOf(GroupMember("a", 1, "M29"), GroupMember("b", 1, "100"))
        val half = stopNameHalfHeightDp("Rathaus")
        val layout = layoutGroup(signHalfWidthDp(1), members, belowHalfHeightDp = half)
        val first = layout.slots.getValue("b")
        val second = layout.slots.getValue("a")
        assertEquals(0f, first.dx, 0.001f)
        assertEquals(0f, second.dx, 0.001f)
        assertTrue(first.dy > half)
        assertTrue(second.dy > first.dy)
        // Without a name: to the right of the sign.
        assertTrue(layoutGroup(signHalfWidthDp(1), members).slots.getValue("a").dx > signHalfWidthDp(1))
    }
}
