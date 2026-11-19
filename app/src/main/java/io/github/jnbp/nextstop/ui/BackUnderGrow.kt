package io.github.jnbp.nextstop.ui

// The content a nested back gesture returns to (the overview under the page of one journey)
// comes in with the gesture instead of lying there whole: it grows from BACK_UNDER_START_SCALE
// to its full size and fades in from BACK_UNDER_FADE_FROM on, so it never shows as a ring of
// the same size around the page that shrinks in front of it. Pure math, see NestedBack.kt.
internal const val BACK_UNDER_START_SCALE = 0.94f
internal const val BACK_UNDER_FADE_FROM = 0.15f

/**
 * How far the content underneath came (0 to 1): the gesture's [progress] while the finger is
 * on it and when it springs back (called off, [fade] stays 1); once let go the rest of the way
 * with the fade of the page in front ([fade] from 1 to 0), so it is whole when that page is gone.
 */
internal fun backUnderShown(progress: Float, fade: Float): Float {
    val held = progress.coerceIn(0f, 1f)
    return (held + (1f - held) * (1f - fade.coerceIn(0f, 1f))).coerceIn(0f, 1f)
}

/** The scale of the content underneath at [shown] (see [backUnderShown]). */
internal fun backUnderScale(shown: Float): Float =
    BACK_UNDER_START_SCALE + (1f - BACK_UNDER_START_SCALE) * shown.coerceIn(0f, 1f)

/** The opacity of the content underneath at [shown]: none before BACK_UNDER_FADE_FROM, then a smoothstep to 1. */
internal fun backUnderAlpha(shown: Float): Float {
    val x = ((shown - BACK_UNDER_FADE_FROM) / (1f - BACK_UNDER_FADE_FROM)).coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

/**
 * The vertical pivot (a fraction of its own height, may lie outside 0 to 1) of a part above
 * the card underneath, [partHeight] high, for it to grow about the card's centre, which lies
 * [cardCentre] px below the part's top.
 */
internal fun backUnderPivot(cardCentre: Float, partHeight: Float): Float =
    if (partHeight <= 0f) 0.5f else cardCentre / partHeight

/**
 * Whether the page in front has been under a back gesture that led away from it: true while
 * the gesture or its fade runs ([active]), and after that for as long as the page is [closed]
 * (back was let go, the page is on its way out). A gesture called off leaves the page open,
 * so it forgets the gesture as soon as that is over.
 */
internal fun backLeftSeen(active: Boolean, closed: Boolean, seenBefore: Boolean): Boolean =
    active || (seenBefore && closed)

/**
 * The page in front is hidden: the fade of its back gesture is over ([active] false) but the
 * transition has not taken it away yet ([seen], see [backLeftSeen]). It was faded out already,
 * so it must not stand there whole again for those last frames.
 */
internal fun backLeftHidden(active: Boolean, seen: Boolean): Boolean = !active && seen
