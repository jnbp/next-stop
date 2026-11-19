package io.github.jnbp.nextstop.ui

/*
 * #238: what scrolls beneath a pinned ride head of the detail page fades out before it is behind
 * the head, so nothing shows behind its rounded corners or through gaps: fully opaque a little
 * below the head's bottom edge, fully transparent at the head's vertical middle. Not an
 * animation but a mask, so it is the same with animations off. The next ride's own head, which
 * pushes the pinned one up, is never faded.
 */

/**
 * The opacity of what lies at [y] beneath a head pinned from [headTop] to [headBottom] (all in
 * the coordinates of the list, y downwards): 0 at the head's vertical middle and above, 1 from
 * [clear] below its bottom on, linear in between. A head of no height fades nothing.
 */
fun underHeadAlpha(y: Float, headTop: Float, headBottom: Float, clear: Float): Float {
    if (headBottom <= headTop) return 1f
    val zero = underHeadZero(headTop, headBottom)
    val one = underHeadOne(headBottom, clear)
    return ((y - zero) / (one - zero)).coerceIn(0f, 1f)
}

/** Where what lies beneath a head from [headTop] to [headBottom] is fully transparent: its vertical middle (see underHeadAlpha). */
fun underHeadZero(headTop: Float, headBottom: Float): Float = (headTop + headBottom) / 2f

/** Where what lies beneath a head ending at [headBottom] is fully opaque again: [clear] below it (see underHeadAlpha). */
fun underHeadOne(headBottom: Float, clear: Float): Float = headBottom + clear.coerceAtLeast(0f)

/**
 * #245: whether the content of a scrolled column fades beneath a line pinned over its top (the
 * status line of the Live-Fahrt sheet, the sticky blue box of the calm view) that ends
 * [pinBottom] below the column's top: only while the line is there and the content is scrolled
 * by [scrolled] pixels; at rest nothing fades.
 */
fun pinCoversContent(pinBottom: Float, scrolled: Int): Boolean = pinBottom > 0f && scrolled > 0

/**
 * #258: above which line (pixels below the column's top) nothing of a column is drawn while a
 * line lies pinned over its top from [pinTop] to [pinBottom]: the line's vertical middle, where
 * the fade beneath it is fully transparent, scrolled or not, so no strip of the content ever shows
 * between what stands above the column (the sheet's header) and the line, not even while the
 * room left for the line catches up with it (it slides in, its height changes). Null with no
 * line: the column draws as it is.
 */
fun pinClipTop(pinTop: Float, pinBottom: Float): Float? =
    if (pinBottom > 0f && pinBottom > pinTop) underHeadZero(pinTop, pinBottom) else null

/**
 * Whether the head of the item [headIndex], placed at [headOffset] in the list, covers content:
 * it is pinned at the top (at or above the top edge) and something scrolled beneath it, which is
 * so once the first item in view ([firstIndex], scrolled by [firstScrolled] pixels) is another
 * item or the head itself is partly scrolled away. A head resting at the very top covers nothing.
 */
fun headCoversContent(headIndex: Int, headOffset: Int, firstIndex: Int, firstScrolled: Int): Boolean =
    headOffset <= 0 && (firstIndex != headIndex || firstScrolled > 0)

/*
 * #251 (variant A): the next ride's head pushes the pinned one up. Over the last [ramp] of its
 * approach the pinned head's bottom corners round from square to the card's radius, and the
 * pinned head is pushed up keeping [gap] between them, so a rounded top never meets a square
 * bottom. With nothing approaching the pinned head keeps its look.
 */

/**
 * The radius of the pinned head's bottom corners with the next head [distance] below its bottom
 * (as the list lays them out, without the lift): 0 from [gap] + [ramp] away on, [radius] from
 * [gap] on (and while pushed), linear in between.
 */
fun pushCornerRadius(distance: Float, gap: Float, ramp: Float, radius: Float): Float {
    if (radius <= 0f) return 0f
    if (ramp <= 0f) return if (distance <= gap) radius else 0f
    return radius * ((gap + ramp - distance) / ramp).coerceIn(0f, 1f)
}

/**
 * How far the pinned head is lifted above where the list puts it, with the next head [distance]
 * below its bottom: nothing while the next head is [gap] or more away, then as much as it came
 * closer, so the gap stays; [gap] while the list pushes the head itself (distance 0).
 */
fun pushLift(distance: Float, gap: Float): Float = (gap - distance).coerceIn(0f, gap.coerceAtLeast(0f))
