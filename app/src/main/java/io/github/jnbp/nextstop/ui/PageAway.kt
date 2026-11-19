package io.github.jnbp.nextstop.ui

/*
 * #194: while a walk is on the big map, the page in front slides down out of view and keeps its
 * state. It was moved by its own height only. The pages lie inside the system bars, so the page's
 * top then stopped at the top of the navigation bar: in the strip of the navigation bar (and with
 * the shadow of its top edge) it still showed, a sliver of the page at the bottom of the map, the
 * one that slides back in on the way back.
 */

/**
 * How far down the page in front is moved while a walk is on the big map: [away] (0 in place,
 * 1 out of view) of its [pageHeight], the room below the pages to the bottom of the window
 * ([belowPages], the navigation bar) and [shadow] for what is drawn above its top edge, all in
 * pixels. Fully away nothing of it is left in the window.
 */
fun pageAwayOffset(pageHeight: Float, belowPages: Float, shadow: Float, away: Float): Float =
    away * (pageHeight + belowPages.coerceAtLeast(0f) + shadow.coerceAtLeast(0f))
