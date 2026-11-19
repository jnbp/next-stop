package io.github.jnbp.nextstop.ui

import kotlin.math.abs
import kotlin.math.sign

/*
 * Loading earlier and later journeys at the edges of the chart: when an edge counts as
 * reached, when reaching or pulling at it loads a page (once, not for every frame), and what
 * the edge says when a page brought nothing to show. Pure, so it is tested on its own.
 */

/** How the last request for a further page at one edge ended. */
enum class PageOutcome {
    /** New journeys came. */
    Added,

    /** The page only had journeys that are shown already; the next page may have more. */
    Nothing,

    /** No level could be asked; the same page is asked for again by the next pull. */
    Failed,

    /** No further page: none to ask for, or asking again would bring the same one. */
    End,
}

/** One finished request at an edge, numbered so the same outcome twice still counts as new. */
data class EdgeResult(val outcome: PageOutcome, val seq: Int)

/** What the chart shows at an edge: whether a page is on its way, and how the last one ended. */
data class EdgeStatus(
    val loadingEarlier: Boolean = false,
    val loadingLater: Boolean = false,
    val earlier: EdgeResult? = null,
    val later: EdgeResult? = null,
)

/** The short note at an edge after a page that showed nothing new. */
enum class EdgeNote { Nothing, Hidden, Failed, End }

/**
 * The end of the row is reached when its last column in view ([lastVisibleKey]) is one of
 * the last two journeys ([lastId], [beforeLastId]), so the next page is on its way before the
 * very end. Keys, not indices: indices are stale for a frame after a page came in. Nor whether
 * the row can scroll on: for that frame it still tells of the row as it was before the page,
 * which would count a page put in front as the start reached once more.
 */
fun chartAtEnd(lastVisibleKey: Any?, lastId: String?, beforeLastId: String?): Boolean {
    if (lastId == null || lastVisibleKey == null) return false
    return lastVisibleKey == lastId || (beforeLastId != null && lastVisibleKey == beforeLastId)
}

/** The start is reached when the first journey ([firstId]) is the first column in view (see [chartAtEnd]). */
fun chartAtStart(firstVisibleKey: Any?, firstId: String?): Boolean =
    firstId != null && firstVisibleKey == firstId

/**
 * When one edge of the chart loads a page. Reaching it loads once, by a drag or a fling alike;
 * the frames spent there after it load nothing more. A new edge (a page came in and its last
 * journey is in view at once) counts as reached again. A pull at the edge (see [EdgePull])
 * loads the next page, once per gesture, also when reaching the edge already did in it.
 * With [onlyWhileScrolling] (earlier journeys) the edge loads up front once and then only
 * while the user scrolls, so the chart never walks into the past by itself.
 */
class EdgeTrigger(private val onlyWhileScrolling: Boolean) {
    private var wasAt = false
    private var lastEdge: String? = null
    private var gestureFired = false

    /** The edge as it is now; true when that loads a page. */
    fun edge(at: Boolean, scrolling: Boolean, edgeId: String?, primed: Boolean = true): Boolean {
        val reached = at && (!wasAt || edgeId != lastEdge)
        wasAt = at
        lastEdge = edgeId
        if (!scrolling) gestureFired = false
        val fire = reached && (!onlyWhileScrolling || scrolling || !primed) && !(scrolling && gestureFired)
        if (fire && scrolling) gestureFired = true
        return fire
    }

    /** A pull at the edge (see [EdgePull]); true when that loads a page. */
    fun pull(scrolling: Boolean): Boolean {
        if (gestureFired) return false
        if (scrolling) gestureFired = true
        return true
    }
}

/**
 * Counts a pull at an edge of the row: the part of a drag the row could not take (it is at an
 * edge already). Once it adds up to [threshold] (pixels) it counts as one pull, once per
 * gesture: [drag] gives 1 for a pull at the start (a positive amount, towards the start), -1
 * at the end, and 0 otherwise. [release] at the end of the gesture.
 */
class EdgePull(private val threshold: Float) {
    private var sum = 0f
    private var fired = false

    fun drag(unconsumed: Float): Int {
        if (unconsumed == 0f || unconsumed.isNaN()) return 0
        if (sum != 0f && sign(sum) != sign(unconsumed)) release()
        sum += unconsumed
        if (fired || abs(sum) < threshold) return 0
        fired = true
        return if (sum > 0f) 1 else -1
    }

    fun release() {
        sum = 0f
        fired = false
    }
}

/**
 * How a request for a further page ended: [found] levels answered, [failed] failed (an empty
 * answer is no failure), [added] journeys were new, and the references to the next pages were
 * [before] the request and are [after] it.
 */
fun pageOutcome(found: Int, failed: Int, added: Int, before: Map<*, String>, after: Map<*, String>): PageOutcome = when {
    added > 0 -> PageOutcome.Added
    found == 0 && failed > 0 -> PageOutcome.Failed
    after.isEmpty() || after == before -> PageOutcome.End
    else -> PageOutcome.Nothing
}

/** How many of the journeys [after] a page (by id) were not there [before] it. */
fun addedCount(before: Collection<String>, after: Collection<String>): Int {
    val known = before.toHashSet()
    return after.count { it !in known }
}

/**
 * The note at an edge after a page ended with [outcome]: none when new journeys show there
 * ([edgeBefore], the journey at the edge when it was asked, is no longer at the edge
 * ([edgeNow])), [EdgeNote.Hidden] when new ones came but the filters of the view hide them all.
 */
fun edgeNoteOf(outcome: PageOutcome, edgeBefore: String?, edgeNow: String?): EdgeNote? = when (outcome) {
    PageOutcome.Added -> if (edgeBefore != null && edgeBefore == edgeNow) EdgeNote.Hidden else null
    PageOutcome.Nothing -> EdgeNote.Nothing
    PageOutcome.Failed -> EdgeNote.Failed
    PageOutcome.End -> EdgeNote.End
}
