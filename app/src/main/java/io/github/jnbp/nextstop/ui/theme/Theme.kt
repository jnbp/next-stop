package io.github.jnbp.nextstop.ui.theme

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import io.github.jnbp.nextstop.data.AppPrefs

/** Where the colours of the app come from. The names are stored, so they must stay as they are. */
enum class ColorChoice { NextStop, System, Custom }

/** Light, dark, or as the system is set. The names are stored, so they must stay as they are. */
enum class DarkChoice { Light, Dark, System }

/**
 * The look of the map: calm and light, colourful (blue water, green parks), dark, or
 * following the light or dark look of the app. The names are stored, so they must stay as they are.
 */
enum class MapStyleChoice { Light, Colorful, Dark, Auto }

/**
 * Whether the vehicle badges on the map show the way they head. The names are stored, so they
 * must stay as they are. How the direction is drawn is the map's matter (see DirectionStyle).
 */
enum class VehicleDirectionChoice { Off, On }

/**
 * Which live vehicles the map shows: [All] everywhere, also far zoomed out; [Dynamic] only
 * zoomed in close (from DYNAMIC_VEHICLE_MIN_ZOOM), fewer and smaller requests, which spares
 * data and battery; [Off] none (recommended for less powerful phones). The names are stored,
 * so they must stay as they are.
 */
enum class MapVehiclesChoice { All, Dynamic, Off }

/**
 * The stored choice of the map vehicles: by its name [stored]; before there was one, the old
 * switch [legacyOn] where it was stored (on: [MapVehiclesChoice.All], off: [MapVehiclesChoice.Off]);
 * else [MapVehiclesChoice.Dynamic], the default: vehicles only zoomed in close.
 */
fun mapVehiclesChoice(stored: String?, legacyOn: Boolean?): MapVehiclesChoice =
    MapVehiclesChoice.entries.firstOrNull { it.name == stored } ?: when (legacyOn) {
        true -> MapVehiclesChoice.All
        false -> MapVehiclesChoice.Off
        null -> MapVehiclesChoice.Dynamic
    }

/** How a followed trip shows in the notifications. The names are stored, so they must stay as they are. */
enum class NotificationLook { LiveUpdate, AppLook }

/** The accent colours the user can pick for an own colour scheme. */
val ACCENT_PRESETS: List<Int> = listOf(
    0xFF4F46E5.toInt(), // indigo
    0xFF7C3AED.toInt(), // violet
    0xFFC026D3.toInt(), // magenta
    0xFFE11D48.toInt(), // rose
    0xFFDC2626.toInt(), // red
    0xFFEA580C.toInt(), // orange
    0xFFB7791F.toInt(), // amber
    0xFF16A34A.toInt(), // green
    0xFF0D9488.toInt(), // teal
    0xFF0284C7.toInt(), // sky
)

/**
 * All colours the interface uses, for one scheme in light or in dark. [blue] is the
 * main colour of the scheme and [lime] its bright highlight; the names come from the
 * colours of the Next Stop scheme. [card] is the ground of rows and cards (white in light).
 */
@Immutable
data class AppPalette(
    val dark: Boolean,
    val blue: Color,
    val onBlue: Color,
    val blueContainer: Color,
    val onBlueContainer: Color,
    val lime: Color,
    val onLime: Color,
    val ink: Color,
    val onInk: Color,
    val muted: Color,
    val chip: Color,
    val grid: Color,
    val card: Color,
    val ground: Color,
    val plainGround: Color,
    val delayRed: Color,
    val onDelayRed: Color,
    val early: Color,
    val tight: Color,
    val delayContainer: Color,
    val delayInk: Color,
    val calmContainer: Color,
    val calmInk: Color,
    val amberContainer: Color,
    val amberInk: Color,
    val warning: Color,
    val warningContainer: Color,
    val rail: Color,
    val divider: Color,
    val glass: Color,
    val glassTint: Color,
    val glassBorder: Color,
    val glassHighlight: Color,
    val glassShadow: Color,
    val fog: Color,
    val fogTint: Color,
    /**
     * Cards inside a glass sheet (tiles, the appointment card, chips, the cards of the
     * connection detail and the companion) are a second, brighter layer of the same frosted
     * glass: [innerCard] is laid over the glass of the sheet, so they stand out by light, not
     * by lines. [innerBorder] is at most a hairline of light along their top edge, and
     * [innerShadow] a soft, wide shadow without an outline.
     */
    val innerCard: Color,
    val innerBorder: Color,
    val innerShadow: Color,
)

private val NextStopLight = AppPalette(
    dark = false,
    blue = Color(0xFF1B4FD8),
    onBlue = Color.White,
    blueContainer = Color(0xFFDBE3FF),
    onBlueContainer = Color(0xFF00174D),
    lime = Color(0xFFC8F16B),
    onLime = Color(0xFF1A2600),
    ink = Color(0xFF171A21),
    onInk = Color.White,
    muted = Color(0xFF454A57),
    chip = Color(0xFFECEEF4),
    grid = Color(0xFFDFE3EC),
    card = Color.White,
    ground = Color(0xFFF8F9FC),
    plainGround = Color(0xFFEEF0F5),
    delayRed = Color(0xFFA3141F),
    onDelayRed = Color.White,
    early = Color(0xFF1B6B3A),
    tight = Color(0xFF9A5B00),
    delayContainer = Color(0xFFFCE9E6),
    delayInk = Color(0xFF5C0A0E),
    calmContainer = Color(0xFFDDF3E4),
    calmInk = Color(0xFF0C3D22),
    amberContainer = Color(0xFFFFE3A3),
    amberInk = Color(0xFF3A2700),
    warning = Color(0xFFEE7B00),
    warningContainer = Color(0xFFFFF1DC),
    rail = Color(0xFFC9CED9),
    divider = Color(0x1F171A21),
    // All glass of the app is set here: GlassSurface, the glass chips and buttons and the
    // tab bar read these values through glassGround.
    // Glass without a picture of the map behind it: nearly opaque, so text stays readable.
    glass = Color(0xF2FFFFFF),
    // Glass over the blurred map: 66 %, a touch cool, so the brighter inner cards stand out.
    // The map behind is blurred and light, so dark ink keeps a contrast far above 4.5:1.
    glassTint = Color(0xA8F4F7FC),
    // A thin light rim, and a soft light at the top edge inside, as on real glass.
    glassBorder = Color(0xCCFFFFFF),
    glassHighlight = Color(0x59FFFFFF),
    glassShadow = Color(0x3D1E325A),
    fog = Color(0xB8F0F3F8),
    fogTint = Color(0xA8F0F3F8),
    // White at 58 % over the glass of the sheet: about 86 % white in all, brighter than the sheet.
    innerCard = Color(0x94FFFFFF),
    innerBorder = Color(0x80FFFFFF),
    innerShadow = Color(0x1A1E325A),
)

// Dark: light ink on dark grounds; the status colours lighter, so they stand out on the dark.
private val NextStopDark = AppPalette(
    dark = true,
    blue = Color(0xFF3D6AF2),
    onBlue = Color.White,
    blueContainer = Color(0xFF223A7E),
    onBlueContainer = Color(0xFFDCE3FF),
    lime = Color(0xFFC8F16B),
    onLime = Color(0xFF1A2600),
    ink = Color(0xFFE7E9F0),
    onInk = Color(0xFF171A21),
    muted = Color(0xFFAAB0BE),
    chip = Color(0xFF2B303B),
    grid = Color(0xFF343A47),
    card = Color(0xFF1C2029),
    ground = Color(0xFF111318),
    plainGround = Color(0xFF15181E),
    delayRed = Color(0xFFFF6F66),
    onDelayRed = Color(0xFF3B0004),
    early = Color(0xFF5BD68A),
    tight = Color(0xFFF0B550),
    delayContainer = Color(0xFF4A1C1A),
    delayInk = Color(0xFFFFDAD6),
    calmContainer = Color(0xFF16382A),
    calmInk = Color(0xFFC2EFD2),
    amberContainer = Color(0xFF4A3800),
    amberInk = Color(0xFFFFE08A),
    warning = Color(0xFFFF9A3D),
    warningContainer = Color(0xFF3D2A12),
    rail = Color(0xFF4A5162),
    divider = Color(0x24FFFFFF),
    glass = Color(0xF21C2029),
    // 62 % over the blurred dark map, a touch cool; light ink keeps a contrast far above 4.5:1.
    glassTint = Color(0x9E181C26),
    glassBorder = Color(0x33FFFFFF),
    glassHighlight = Color(0x14FFFFFF),
    glassShadow = Color(0x66000000),
    fog = Color(0xC0111318),
    fogTint = Color(0xB0111318),
    // White at 14 % over the dark glass of the sheet: a lighter layer of the same glass.
    innerCard = Color(0x24FFFFFF),
    innerBorder = Color(0x1AFFFFFF),
    innerShadow = Color(0x40000000),
)

/** A colour of [hue] (degrees), [saturation] and [lightness] (0 to 1). */
private fun hsl(hue: Float, saturation: Float, lightness: Float): Color {
    val h = ((hue % 360f) + 360f) % 360f
    return Color(ColorUtils.HSLToColor(floatArrayOf(h, saturation.coerceIn(0f, 1f), lightness.coerceIn(0f, 1f))))
}

/** The WCAG contrast ratio of two opaque colours (1 to 21). */
fun contrastOf(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

// Text on its ground reads at least at this contrast (WCAG AA), large marks and icons at the second.
private const val TEXT_CONTRAST = 4.5f
private const val MARK_CONTRAST = 3f

/**
 * [ink] where it reads on [ground] (at least [minimum]:1); else moved step by step towards
 * near-black or white, whichever stands out more from the ground, until it does. The hue is
 * kept as long as it can be, so a tinted text stays tinted.
 */
fun readableOn(ground: Color, ink: Color, minimum: Float = TEXT_CONTRAST): Color {
    if (contrastOf(ground, ink) >= minimum) return ink
    val darkEnd = Color(0xFF101114)
    val target = if (contrastOf(ground, darkEnd) >= contrastOf(ground, Color.White)) darkEnd else Color.White
    for (step in 1..10) {
        val moved = lerp(ink, target, step / 10f)
        if (contrastOf(ground, moved) >= minimum) return moved
    }
    return target
}

/**
 * The palette with every pair of a colour and the colour drawn on it readable: text on the
 * grounds (cards, sheets, chips), on the main colour, its container and the highlight. The
 * colours of the wallpaper (Material You) and an own accent can come out in any lightness,
 * so a pair that reads in one scheme may not in another; this moves only those that fall short.
 */
private fun AppPalette.readable(): AppPalette {
    val grounds = listOf(card, ground, chip, plainGround)
    fun onGrounds(color: Color, minimum: Float): Color = grounds.fold(color) { ink, under -> readableOn(under, ink, minimum) }
    val shownInk = onGrounds(ink, TEXT_CONTRAST)
    // The main colour is also the colour of links, icons and selected marks on the grounds.
    val shownBlue = onGrounds(blue, MARK_CONTRAST)
    return copy(
        ink = shownInk,
        onInk = readableOn(shownInk, onInk),
        muted = onGrounds(muted, TEXT_CONTRAST),
        blue = shownBlue,
        onBlue = readableOn(shownBlue, onBlue),
        onBlueContainer = readableOn(blueContainer, onBlueContainer),
        onLime = readableOn(lime, onLime),
    )
}

/** Dark ink on a light ground, white on a dark one. */
private fun inkOn(ground: Color, dark: Color = Color(0xFF16130A)): Color =
    if (ground.luminance() > 0.3f) dark else Color.White

/**
 * A scheme from one [accent] colour: its hue for the main colour and its containers, and a
 * bright highlight on the far side of the colour wheel, as lime lies to blue in the
 * Next Stop scheme.
 */
private fun accentPalette(accent: Int, dark: Boolean): AppPalette {
    val values = FloatArray(3)
    ColorUtils.colorToHSL(accent, values)
    val hue = values[0]
    val saturation = values[1].coerceIn(0.45f, 0.85f)
    val highlightHue = hue + 214f
    val base = if (dark) NextStopDark else NextStopLight
    val main = if (dark) hsl(hue, saturation, 0.56f) else hsl(hue, saturation, 0.42f)
    return base.copy(
        blue = main,
        onBlue = inkOn(main),
        blueContainer = if (dark) hsl(hue, saturation * 0.6f, 0.26f) else hsl(hue, saturation, 0.92f),
        onBlueContainer = if (dark) hsl(hue, 0.9f, 0.9f) else hsl(hue, 0.9f, 0.15f),
        lime = hsl(highlightHue, 0.8f, 0.68f),
        onLime = hsl(highlightHue, 0.9f, 0.12f),
    ).readable()
}

/** The colours of the wallpaper (Material You), with the status colours of the app. */
private fun dynamicPalette(scheme: ColorScheme, dark: Boolean): AppPalette {
    val base = if (dark) NextStopDark else NextStopLight
    val card = if (dark) scheme.surfaceContainerHigh else scheme.surfaceContainerLowest
    return base.copy(
        blue = scheme.primary,
        onBlue = scheme.onPrimary,
        blueContainer = scheme.primaryContainer,
        onBlueContainer = scheme.onPrimaryContainer,
        lime = scheme.tertiaryContainer,
        onLime = scheme.onTertiaryContainer,
        ink = scheme.onSurface,
        onInk = scheme.surface,
        muted = scheme.onSurfaceVariant,
        chip = scheme.surfaceContainerHighest,
        grid = scheme.outlineVariant,
        card = card,
        ground = scheme.surface,
        plainGround = scheme.surfaceContainer,
        rail = scheme.outlineVariant,
        divider = scheme.onSurface.copy(alpha = 0.12f),
        // The strengths of the glass are those of the Next Stop palette, in the colour of the card.
        glass = card.copy(alpha = base.glass.alpha),
        glassTint = card.copy(alpha = base.glassTint.alpha),
        glassBorder = if (dark) base.glassBorder else card.copy(alpha = base.glassBorder.alpha),
        fog = scheme.surface.copy(alpha = if (dark) 0.75f else 0.72f),
        fogTint = scheme.surface.copy(alpha = if (dark) 0.69f else 0.66f),
        innerCard = Color.White.copy(alpha = if (dark) 0.14f else 0.58f),
        innerBorder = Color.White.copy(alpha = if (dark) 0.10f else 0.5f),
    ).readable()
}

/** The palette and the Material colours for one choice. Not composable: the notification uses it too. */
fun appPalette(context: Context, choice: ColorChoice, accent: Int, dark: Boolean): AppPalette = when (choice) {
    ColorChoice.NextStop -> if (dark) NextStopDark else NextStopLight
    ColorChoice.Custom -> accentPalette(accent, dark)
    ColorChoice.System -> dynamicPalette(
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context),
        dark,
    )
}

/** True when the system is set to dark. */
fun systemDark(context: Context): Boolean =
    (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

/** The palette as the stored settings ask for it, for places outside the interface such as the notification. */
fun storedPalette(context: Context): AppPalette {
    val prefs = AppPrefs(context)
    val dark = when (prefs.darkChoice) {
        DarkChoice.Light -> false
        DarkChoice.Dark -> true
        DarkChoice.System -> systemDark(context)
    }
    return appPalette(context, prefs.colorChoice, prefs.accentColor, dark)
}

/**
 * The appearance settings while the app runs. They are snapshot state, so a change is
 * drawn at once everywhere; every change is stored as well.
 */
object Appearance {
    var colorChoice by mutableStateOf(ColorChoice.NextStop)
        private set
    var darkChoice by mutableStateOf(DarkChoice.System)
        private set
    var accent by mutableIntStateOf(ACCENT_PRESETS.first())
        private set
    var mapStyle by mutableStateOf(MapStyleChoice.Auto)
        private set
    var vehicleDirection by mutableStateOf(VehicleDirectionChoice.Off)
        private set
    var notificationLook by mutableStateOf(NotificationLook.AppLook)
        private set

    /** Which live vehicles the map shows (see AppPrefs.mapVehicles). */
    var mapVehicles by mutableStateOf(MapVehiclesChoice.Dynamic)
        private set
    private var loaded = false

    /** Reads the stored settings once per process. */
    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val prefs = AppPrefs(context)
        colorChoice = prefs.colorChoice
        darkChoice = prefs.darkChoice
        accent = prefs.accentColor
        mapStyle = prefs.mapStyle
        vehicleDirection = prefs.vehicleDirection
        notificationLook = prefs.notificationLook
        mapVehicles = prefs.mapVehicles
    }

    fun changeColorChoice(context: Context, value: ColorChoice) {
        colorChoice = value
        AppPrefs(context).colorChoice = value
    }

    fun changeDarkChoice(context: Context, value: DarkChoice) {
        darkChoice = value
        AppPrefs(context).darkChoice = value
    }

    fun changeMapStyle(context: Context, value: MapStyleChoice) {
        mapStyle = value
        AppPrefs(context).mapStyle = value
    }

    fun changeVehicleDirection(context: Context, value: VehicleDirectionChoice) {
        vehicleDirection = value
        AppPrefs(context).vehicleDirection = value
    }

    fun changeAccent(context: Context, value: Int) {
        accent = value
        AppPrefs(context).accentColor = value
    }

    fun changeMapVehicles(context: Context, value: MapVehiclesChoice) {
        mapVehicles = value
        AppPrefs(context).mapVehicles = value
    }

    fun changeNotificationLook(context: Context, value: NotificationLook) {
        notificationLook = value
        AppPrefs(context).notificationLook = value
    }
}

/**
 * The colours of the app by name. They follow the chosen scheme and light or dark: every
 * read goes to snapshot state, so composition, layout and drawing that read a colour are
 * done again when the scheme changes. Line colours are not part of it; they stay the
 * official ones in every scheme.
 */
object AppColors {
    /** Set by [NextStopTheme]; read through the names below. */
    var palette by mutableStateOf(NextStopLight)

    val Blue: Color get() = palette.blue
    val OnBlue: Color get() = palette.onBlue
    val BlueContainer: Color get() = palette.blueContainer
    val OnBlueContainer: Color get() = palette.onBlueContainer
    val Lime: Color get() = palette.lime
    val OnLime: Color get() = palette.onLime
    val Ink: Color get() = palette.ink
    val OnInk: Color get() = palette.onInk
    val Muted: Color get() = palette.muted
    val Chip: Color get() = palette.chip
    val Grid: Color get() = palette.grid
    val Card: Color get() = palette.card
    val Ground: Color get() = palette.ground
    val PlainGround: Color get() = palette.plainGround
    val DelayRed: Color get() = palette.delayRed
    val OnDelayRed: Color get() = palette.onDelayRed
    val Early: Color get() = palette.early
    val Tight: Color get() = palette.tight
    val DelayContainer: Color get() = palette.delayContainer
    val DelayInk: Color get() = palette.delayInk
    val CalmContainer: Color get() = palette.calmContainer
    val CalmInk: Color get() = palette.calmInk
    val AmberContainer: Color get() = palette.amberContainer
    val AmberInk: Color get() = palette.amberInk

    /** A change that only works by delays: a deep red turning purple, beyond the red of a risk. */
    val VeryRisky: Color get() = if (palette.dark) Color(0xFFF07AC8) else Color(0xFF7D0F4F)
    val VeryRiskyContainer: Color get() = if (palette.dark) Color(0xFF4A1236) else Color(0xFFF5DCEA)
    val VeryRiskyInk: Color get() = if (palette.dark) Color(0xFFFFD8EF) else Color(0xFF46062C)
    /**
     * A long wait on the platform: a calm, warm sand, neither the green of a good change nor
     * the red, magenta or orange of a problem, and warmer than the grey of a plain one.
     */
    val Sand: Color get() = if (palette.dark) Color(0xFFCDBA95) else Color(0xFF7D6A47)
    val SandContainer: Color get() = if (palette.dark) Color(0xFF3A3428) else Color(0xFFF3EEE3)
    val SandInk: Color get() = if (palette.dark) Color(0xFFE9DEC6) else Color(0xFF6B5B3E)
    val Warning: Color get() = palette.warning
    val WarningContainer: Color get() = palette.warningContainer
    val Rail: Color get() = palette.rail
    val Divider: Color get() = palette.divider
    val Glass: Color get() = palette.glass
    val GlassTint: Color get() = palette.glassTint
    val GlassBorder: Color get() = palette.glassBorder
    val GlassHighlight: Color get() = palette.glassHighlight
    val GlassShadow: Color get() = palette.glassShadow
    val Fog: Color get() = palette.fog
    val FogTint: Color get() = palette.fogTint
    val InnerCard: Color get() = palette.innerCard
    val InnerBorder: Color get() = palette.innerBorder
    val InnerShadow: Color get() = palette.innerShadow
}

/** The Material colours of a palette, for the standard components (switches, dialogs, pickers). */
private fun materialScheme(palette: AppPalette): ColorScheme {
    val base = if (palette.dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = palette.blue,
        onPrimary = palette.onBlue,
        primaryContainer = palette.blueContainer,
        onPrimaryContainer = palette.onBlueContainer,
        secondaryContainer = palette.blueContainer,
        onSecondaryContainer = palette.onBlueContainer,
        tertiaryContainer = palette.lime,
        onTertiaryContainer = palette.onLime,
        background = palette.ground,
        onBackground = palette.ink,
        surface = palette.ground,
        onSurface = palette.ink,
        surfaceVariant = palette.chip,
        onSurfaceVariant = palette.muted,
        outlineVariant = palette.grid,
        error = palette.delayRed,
        onError = palette.onDelayRed,
        // Dialogs and pickers lie on these.
        surfaceContainerLowest = palette.card,
        surfaceContainerLow = if (palette.dark) palette.card else base.surfaceContainerLow,
        surfaceContainer = if (palette.dark) palette.card else base.surfaceContainer,
        surfaceContainerHigh = if (palette.dark) palette.chip else base.surfaceContainerHigh,
        surfaceContainerHighest = palette.chip,
    )
}

/** True when the app is drawn dark: by the setting, or as the system is set. */
@Composable
fun appInDark(): Boolean = when (Appearance.darkChoice) {
    DarkChoice.Light -> false
    DarkChoice.Dark -> true
    DarkChoice.System -> isSystemInDarkTheme()
}

@Composable
fun NextStopTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = appInDark()
    val choice = Appearance.colorChoice
    val accent = Appearance.accent
    val palette = remember(choice, accent, dark) { appPalette(context, choice, accent, dark) }
    // Written before the content is composed, so the first frame already has the new colours.
    if (AppColors.palette != palette) AppColors.palette = palette
    val scheme = remember(palette) {
        if (choice == ColorChoice.System) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            materialScheme(palette)
        }
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography,
    ) {
        CompositionLocalProvider(LocalContentColor provides palette.ink, content = content)
    }
}
