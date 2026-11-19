package io.github.jnbp.nextstop.ui.companion

/*
 * When the slim status line pinned at the top of the sheet of the map view shows (#225): once
 * the sheet pulled up covers most of the blue box, and gone again once the box is mostly free.
 * Two thresholds, so a sheet resting near the edge does not make the line blink.
 */

// The share of the blue box the sheet must cover before the status line shows, and below which it goes.
private const val PIN_SHOW_FROM = 0.6f
private const val PIN_HIDE_BELOW = 0.3f

/**
 * The share (0..1) of the blue box from [heroTop] to [heroBottom] that lies under a sheet whose
 * top edge is at [sheetTop] (all in the same coordinates, y downwards); 0 for a box not measured yet.
 */
fun heroCovered(heroTop: Float, heroBottom: Float, sheetTop: Float): Float {
    val height = heroBottom - heroTop
    if (height <= 0f) return 0f
    return ((heroBottom - sheetTop) / height).coerceIn(0f, 1f)
}

/** Whether the pinned status line shows with [covered] of the blue box under the sheet, given that it showed ([shown]) before. */
fun heroPinShown(covered: Float, shown: Boolean): Boolean = when {
    covered >= PIN_SHOW_FROM -> true
    covered <= PIN_HIDE_BELOW -> false
    else -> shown
}

/**
 * Hero corners (Joel 19.11.): where the blue box from [heroTop] stops being drawn, in its own coordinates (pixels
 * below its top), with the top edge of the sheet at [sheetTop] (both in the same coordinates
 * of the screen): at the sheet's top edge, so nothing of the box shimmers through the glass of
 * the sheet or peeks out beside its rounded top corners; null while the sheet is not placed
 * yet (nothing clipped).
 */
fun heroClipBottom(heroTop: Float, sheetTop: Float): Float? =
    if (sheetTop == Float.MAX_VALUE || sheetTop.isNaN()) null else (sheetTop - heroTop).coerceAtLeast(0f)
