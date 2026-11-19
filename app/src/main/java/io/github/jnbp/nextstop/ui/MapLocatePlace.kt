package io.github.jnbp.nextstop.ui

/*
 * Where the floating "centre on me" button of the map stands (#165): at the bottom right, just
 * above whatever lies over the bottom of the map (a sheet, a card, the bars), and gone where
 * that reaches up to what covers the top (a sheet pulled fully up). Pure sums, no Android.
 */

/**
 * The top of the button (pixels of the screen) above [bottomEdge], the top edge of what covers
 * the bottom of the map ([height] where nothing does or it is unknown), [gap] clear of it, the
 * button [button] tall; null where there is no room left below [topCover] (what covers the top,
 * NaN for nothing) plus [gap]: the button fades out then.
 */
fun locateButtonTop(bottomEdge: Float, topCover: Float, height: Float, button: Float, gap: Float): Float? {
    val edge = if (bottomEdge.isFinite() && bottomEdge > 0f && bottomEdge <= height) bottomEdge else height
    val top = edge - gap - button
    val ceiling = (if (topCover.isFinite() && topCover > 0f) topCover else 0f) + gap
    return if (top < ceiling) null else top
}

/**
 * True while the map is still under its loading ground (#165): the map is to be seen
 * ([visible]) but has not begun to come in ([entering], see MapLoading), or comes back from a
 * rest under its plain ground ([resting], [waking]). The floating button waits for the map
 * meanwhile, so it never stands on the grey logo.
 */
fun mapStillCovered(visible: Boolean, entering: Boolean, resting: Boolean, waking: Boolean): Boolean =
    visible && (!entering || resting || waking)
