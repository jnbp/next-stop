package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Where a tap on a notification of the disruption watch leads on "My routes" (see DisruptionTapTarget.kt). */
class DisruptionTapTargetTest {
    private fun notice(id: String, vararg providerIds: String) = Disruption(
        id = id,
        summary = "Umleitung",
        text = "Tram M27: Umleitung zwischen A und B.",
        lines = sortedSetOf("M27"),
        products = setOf("tram"),
        providerIds = providerIds.toSet(),
    )

    private val present = listOf(notice("a", "p1"), notice("b"), notice("c", "p3"))

    @Test
    fun oneReportedNoticeStillCurrentOpensIt() {
        assertEquals(DisruptionTapTarget.One("b"), disruptionTapTarget(DisruptionTapRequest(listOf("b")), present))
    }

    @Test
    fun aNoticeMergedUnderAnotherCopyIsFoundByTheProviderId() {
        val target = disruptionTapTarget(DisruptionTapRequest(listOf("x"), listOf("p3")), present)
        assertEquals(DisruptionTapTarget.One("c"), target)
    }

    @Test
    fun aResolvedNoticeIsGone() {
        assertEquals(DisruptionTapTarget.Gone, disruptionTapTarget(DisruptionTapRequest(listOf("x"), listOf("p9")), present))
        assertEquals(DisruptionTapTarget.Gone, disruptionTapTarget(DisruptionTapRequest(listOf("a")), emptyList()))
    }

    @Test
    fun severalInOneNotificationOpenTheSectionWithThoseStillCurrent() {
        val target = disruptionTapTarget(DisruptionTapRequest(listOf("a", "x", "c")), present)
        assertEquals(DisruptionTapTarget.Several(listOf("a", "c")), target)
    }

    @Test
    fun severalAllResolvedAreGone() {
        assertEquals(DisruptionTapTarget.Gone, disruptionTapTarget(DisruptionTapRequest(listOf("x", "y")), present))
    }

    @Test
    fun aNotificationWithoutNoticesOpensTheSection() {
        assertEquals(DisruptionTapTarget.Section, disruptionTapTarget(DisruptionTapRequest(emptyList()), present))
    }

    @Test
    fun theSectionComesAfterTitlePlannedMapAndStrip() {
        assertEquals(2, waysDisruptionsIndex(planned = 0, hasMap = false))
        assertEquals(5, waysDisruptionsIndex(planned = 2, hasMap = true))
    }
}
