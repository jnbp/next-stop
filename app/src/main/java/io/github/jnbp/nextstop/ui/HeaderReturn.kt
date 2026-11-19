package io.github.jnbp.nextstop.ui

// The chips above the overview of the connections fold away while the page of a journey grows
// (see StackedHeader in Connections.kt) and come back while it shrinks into its column again.
// Pure math, see HeaderReturnTest.

/**
 * How far below the top row the overview (and the page of a journey above it) is placed, with
 * the chips [headerRoom] px high (gap included) and [out] of them gone (0 to 1). While they
 * fold away ([collapsed], a journey opens) the overview slides up with them under the growing
 * page. Coming back it is in its place at once and stays there: it slid down with the chips
 * before, and the chart under the shrinking page seemed to jump up and down on the way back
 * (#249); the page keeps its way down by itself (see [headerReturnLift]).
 */
internal fun headerRestOffset(headerRoom: Int, out: Float, collapsed: Boolean): Int =
    if (collapsed) kotlin.math.round(headerRoom * (1f - out.coerceIn(0f, 1f))).toInt() else headerRoom

/**
 * How far (px, up is negative) the page of a journey that closes is drawn above its place while
 * the chips come back ([out] of them still gone, [headerRoom] as in [headerRestOffset]): it
 * starts where it stood and comes down with the chips, as it did when the overview still slid
 * with them, while the overview below it stands still.
 */
internal fun headerReturnLift(headerRoom: Int, out: Float, closing: Boolean): Float =
    if (closing) -headerRoom * out.coerceIn(0f, 1f) else 0f
