package io.github.jnbp.nextstop.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ButtonColors
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme

/*
 * The colours of Next Stop on the watch, in one place: the blue and lime of the app's icon
 * (ic_launcher_background, ic_launcher_foreground) and the shades of them that read well on
 * the black of a watch. Screens take their colours from here or from MaterialTheme, never a
 * literal of their own. Line colours are not brand colours: they come from data/LineColors.kt.
 */
object Brand {
    /** The blue of the icon: the filled main action of a page, with white on it (contrast about 6:1). */
    val Blue = Color(0xFF1B4FD8)

    /** The lime of the icon's dot. */
    val Lime = Color(0xFFC8F16B)

    /** A light blue for text and icons on black, where the full blue would be too dark to read (about 8:1). */
    val BlueOnDark = Color(0xFF9DB8FF)

    /** The container of secondary actions: a dark blue tint, white text on it (about 15:1). */
    val Tint = Color(0xFF172443)

    /** Text on [Blue]. */
    val OnBlue = Color.White

    /** Secondary text on [Blue]: still above 4.5:1. */
    val OnBlueSoft = Color(0xFFDCE5FF)
}

/** The main action of a page: filled in the brand blue. */
@Composable
fun brandButtonColors(): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = Brand.Blue,
    contentColor = Brand.OnBlue,
    secondaryContentColor = Brand.OnBlueSoft,
    iconColor = Brand.OnBlue,
)

/** The other actions of the start page: the brand tint, the icon in light blue. */
@Composable
fun brandTonalButtonColors(): ButtonColors = ButtonDefaults.filledTonalButtonColors(
    containerColor = Brand.Tint,
    contentColor = MaterialTheme.colorScheme.onSurface,
    secondaryContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    iconColor = Brand.BlueOnDark,
)
