package io.github.jnbp.nextstop.ui

/**
 * The share of a square large icon a line badge may take: as wide as 84 % and as high as
 * 42 % of it. The badge's diagonal then stays inside the circle of the square (0.94 of its
 * side), so whatever mask the system lays over the large icon (square, rounded square or
 * circle, as on some builds of Live Updates and on watches) cuts nothing of the badge.
 */
const val ICON_BADGE_MAX_WIDTH = 0.84f
const val ICON_BADGE_MAX_HEIGHT = 0.42f

/** A badge placed in a square icon: drawn at [scale], its top left corner at [left], [top]. */
data class IconFit(val scale: Float, val left: Float, val top: Float)

/**
 * Where a line badge of [badgeWidth] x [badgeHeight] goes in a transparent square icon of
 * [size]: centred, scaled down (never up) to fit [ICON_BADGE_MAX_WIDTH] x [ICON_BADGE_MAX_HEIGHT].
 */
fun fitBadgeInIcon(size: Float, badgeWidth: Float, badgeHeight: Float): IconFit {
    if (size <= 0f || badgeWidth <= 0f || badgeHeight <= 0f) return IconFit(1f, 0f, 0f)
    val scale = minOf(1f, size * ICON_BADGE_MAX_WIDTH / badgeWidth, size * ICON_BADGE_MAX_HEIGHT / badgeHeight)
    return IconFit(scale, (size - badgeWidth * scale) / 2f, (size - badgeHeight * scale) / 2f)
}
