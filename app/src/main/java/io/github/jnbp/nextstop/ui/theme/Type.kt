package io.github.jnbp.nextstop.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import io.github.jnbp.nextstop.R

/** Google Sans Flex, bundled with the app (SIL Open Font License, see docs/licenses). */
val AppFont = FontFamily(
    Font(R.font.google_sans_flex_400, FontWeight.Normal),
    Font(R.font.google_sans_flex_600, FontWeight.SemiBold),
    Font(R.font.google_sans_flex_700, FontWeight.Bold),
    Font(R.font.google_sans_flex_800, FontWeight.ExtraBold),
)

private val Defaults = Typography()

// The Material type scale, with the app font in every style.
val Typography = Typography(
    displayLarge = Defaults.displayLarge.copy(fontFamily = AppFont),
    displayMedium = Defaults.displayMedium.copy(fontFamily = AppFont),
    displaySmall = Defaults.displaySmall.copy(fontFamily = AppFont),
    headlineLarge = Defaults.headlineLarge.copy(fontFamily = AppFont),
    headlineMedium = Defaults.headlineMedium.copy(fontFamily = AppFont),
    headlineSmall = Defaults.headlineSmall.copy(fontFamily = AppFont),
    titleLarge = Defaults.titleLarge.copy(fontFamily = AppFont),
    titleMedium = Defaults.titleMedium.copy(fontFamily = AppFont),
    titleSmall = Defaults.titleSmall.copy(fontFamily = AppFont),
    bodyLarge = Defaults.bodyLarge.copy(fontFamily = AppFont, letterSpacing = Defaults.bodyMedium.letterSpacing),
    bodyMedium = Defaults.bodyMedium.copy(fontFamily = AppFont),
    bodySmall = Defaults.bodySmall.copy(fontFamily = AppFont),
    labelLarge = Defaults.labelLarge.copy(fontFamily = AppFont),
    labelMedium = Defaults.labelMedium.copy(fontFamily = AppFont),
    labelSmall = Defaults.labelSmall.copy(fontFamily = AppFont),
)
