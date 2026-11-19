package io.github.jnbp.nextstop.ui

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.animation.animateContentSize
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.IOException
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.isBareTrainNumber
import io.github.jnbp.nextstop.data.FailureKind
import io.github.jnbp.nextstop.data.failureKind
import io.github.jnbp.nextstop.data.DelayDisplay
import io.github.jnbp.nextstop.data.DelayTone
import io.github.jnbp.nextstop.data.MinutesTone
import io.github.jnbp.nextstop.data.TimeTone
import io.github.jnbp.nextstop.data.delayTone
import io.github.jnbp.nextstop.data.minutesTone
import io.github.jnbp.nextstop.data.timeTone
import io.github.jnbp.nextstop.data.StopTime
import io.github.jnbp.nextstop.data.shownTime
import io.github.jnbp.nextstop.data.shownTone
import io.github.jnbp.nextstop.data.Departure
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.Leg
import io.github.jnbp.nextstop.data.RailReplacement
import io.github.jnbp.nextstop.data.railReplacement
import io.github.jnbp.nextstop.data.townedStopName
import io.github.jnbp.nextstop.data.replacedProduct
import io.github.jnbp.nextstop.data.replacedProductOrGuess
import io.github.jnbp.nextstop.data.PRODUCT_KEYS
import io.github.jnbp.nextstop.data.rapidLineName
import io.github.jnbp.nextstop.data.Disruption
import io.github.jnbp.nextstop.data.LineColors
import io.github.jnbp.nextstop.data.TripCourse
import io.github.jnbp.nextstop.data.Vehicle
import io.github.jnbp.nextstop.data.ridePlace
import io.github.jnbp.nextstop.data.PlaceIcon
import io.github.jnbp.nextstop.data.PlaceKind
import io.github.jnbp.nextstop.data.RingWay
import io.github.jnbp.nextstop.data.ringWayOf
import io.github.jnbp.nextstop.data.NotSupportedException
import io.github.jnbp.nextstop.data.SourceUnreachableException
import io.github.jnbp.nextstop.data.Stop
import io.github.jnbp.nextstop.data.StopAction
import io.github.jnbp.nextstop.data.stopMenuEntries
import io.github.jnbp.nextstop.data.Tightness
import io.github.jnbp.nextstop.data.Warning
import io.github.jnbp.nextstop.data.shortStopName
import io.github.jnbp.nextstop.ui.theme.AppColors
import io.github.jnbp.nextstop.ui.theme.contrastOf
import io.github.jnbp.nextstop.ui.theme.readableOn

internal val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")
private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun OffsetDateTime.clock(): String = atZoneSameInstant(BERLIN).format(CLOCK)

fun minutesBetween(from: OffsetDateTime, to: OffsetDateTime): Long = Duration.between(from, to).toMinutes()

// From this many minutes on a wait is told in hours or days: a search for another day is far from "now".
private const val FAR_MINUTES = 120L
private const val DAY_MINUTES = 24 * 60L

/** A long stretch of time in hours and minutes, or in days and hours. */
@Composable
private fun spanLabel(minutes: Long): String =
    if (minutes < DAY_MINUTES) {
        stringResource(R.string.span_hours, minutes / 60, minutes % 60)
    } else {
        stringResource(R.string.span_days, minutes / DAY_MINUTES, minutes % DAY_MINUTES / 60)
    }

/** How long until something happens: in minutes while it is near, in hours or days when it is far. */
@Composable
fun waitLabel(minutes: Long): String =
    if (minutes < FAR_MINUTES) stringResource(R.string.in_minutes, minutes) else spanLabel(minutes)

/** The S- and U-Bahn lines the app knows the colour of: the lines of Berlin's network map. */
val RAPID_LINE_NAMES: Set<String> get() = LineColors.BERLIN_RAPID.keys

// Long-distance trains by the kind in front of their number: ICE red, FlixTrain green,
// the other long-distance trains dark grey.
private val ICE_RED = Color(0xFFEC0016)
private val FLX_GREEN = Color(0xFF97D700)
private val LONG_DISTANCE_GREY = Color(0xFF4A4F57)
// Coaches of other operators than FlixBus.
private val COACH_TEAL = Color(0xFF2F6F73)
private val LONG_DISTANCE_KINDS = setOf("IC", "EC", "ECE", "EN", "NJ", "RJ", "RJX", "TGV", "ES", "EST", "D")

/**
 * The colour of a ride of [line] of [product] that runs at [at] (see [ridePlace]), with the
 * colour its provider sent with it ([provided]) first: see [LineColors.rideStyle]. Where no
 * colour of the line is known, the colour of its kind (ICE red, FlixTrain green) or of its
 * product. Rides, departures, vehicles and courses have their own overloads that pass all of
 * it; a ride without a place falls back to where the app looks.
 */
fun lineColor(line: String?, product: String, at: Pair<Double, Double>? = null, provided: Int? = null): Color {
    LineColors.rideStyle(line, product, at, providedFor(line, product, provided))?.let { return Color(it.color) }
    // A line stored before names were completed may still be "1" for an S1 (see rapidLineName).
    val name = rapidLineName(line, product)
    val kind = name?.trim()?.uppercase()?.takeWhile { it.isLetter() }.orEmpty()
    return when {
        kind == "ICE" -> ICE_RED
        kind == "FLX" -> FLX_GREEN
        product == "coach" && name.orEmpty().contains("flix", ignoreCase = true) -> FLX_GREEN
        kind in LONG_DISTANCE_KINDS && product in setOf("express", "regional", "") -> LONG_DISTANCE_GREY
        else -> when (product) {
            "subway" -> Color(0xFF115D91)
            "suburban" -> Color(0xFF2D8B3C)
            "tram" -> Color(0xFFBE1414)
            "bus" -> Color(0xFF95276E)
            "ferry" -> Color(0xFF528DBA)
            "regional" -> Color(0xFFDA251D)
            "express" -> LONG_DISTANCE_GREY
            "coach" -> COACH_TEAL
            // A product the provider names none of (a Stadtbahn in some feeds): its name's
            // letter, where it has one, rather than grey.
            "" -> when {
                UNKNOWN_SUBWAY_NAME.matches(name.orEmpty()) -> Color(0xFF115D91)
                UNKNOWN_SUBURBAN_NAME.matches(name.orEmpty()) -> Color(0xFF2D8B3C)
                else -> Color(0xFF5A6072)
            }
            else -> Color(0xFF5A6072)
        }
    }
}

/**
 * The colour a provider sent with [line] of [product], where the badge takes it: not for a train
 * named by its bare number ("174", see isBareTrainNumber), which some feeds paint in the colours
 * of their operator (white with blue for a Czech train); it gets the style of its product, as an
 * ICE or IC does, so it does not read as a bus.
 */
private fun providedFor(line: String?, product: String, provided: Int?): Int? =
    provided?.takeUnless { isBareTrainNumber(line, product) }

// The names of U-Bahn or Stadtbahn ("U6") and S-Bahn lines ("S1") of a product not named.
private val UNKNOWN_SUBWAY_NAME = Regex("^U ?[0-9]{1,3}[A-Z]?$")
private val UNKNOWN_SUBURBAN_NAME = Regex("^S ?[0-9]{1,3}[A-Z]?$")

/** The colour of the line of the ride [leg], by the ride alone (see [LineColors.rideStyle]). */
fun lineColor(leg: Leg): Color = lineColor(leg.line, leg.product, leg.ridePlace, leg.providerColor)

/** The colour of the line of [departure], by the run alone (see [LineColors.rideStyle]). */
fun lineColor(departure: Departure): Color =
    lineColor(departure.line, departure.product, departure.ridePlace, departure.providerColor)

/** The colour of the line of [vehicle], by the vehicle alone (see [LineColors.rideStyle]). */
fun lineColor(vehicle: Vehicle): Color =
    lineColor(vehicle.line, vehicle.product, vehicle.ridePlace, vehicle.providerColor)

/** The colour of the line of [course], by the trip alone (see [LineColors.rideStyle]). */
fun lineColor(course: TripCourse): Color =
    lineColor(course.line, course.product, course.ridePlace, course.providerColor)

fun onLineColor(background: Color): Color =
    if (background.luminance() > 0.45f) Color(0xFF16130A) else Color.White

/**
 * The colour of the text on the badge of [line] of [product] at [at] whose ground is
 * [background]: the one the provider sent with the line's colour ([providedText] with
 * [provided], or one known near [at], see LineColors), else dark or white, whichever reads on it.
 */
fun lineInk(
    line: String?,
    product: String,
    background: Color,
    at: Pair<Double, Double>? = null,
    provided: Int? = null,
    providedText: Int? = null,
): Color {
    val known = LineColors.rideStyle(line, product, at, providedFor(line, product, provided), providedText)
        ?: return onLineColor(background)
    val text = known.text ?: return onLineColor(background)
    return if (known.color == background.toArgb()) Color(text) else onLineColor(background)
}

/** The colour of the text on the badge of the ride [leg] on [background] (see [lineInk]). */
fun lineInk(leg: Leg, background: Color): Color =
    lineInk(leg.line, leg.product, background, leg.ridePlace, leg.providerColor, leg.providerTextColor)

/** The colour of the text on the badge of [vehicle] on [background] (see [lineInk]). */
fun lineInk(vehicle: Vehicle, background: Color): Color =
    lineInk(vehicle.line, vehicle.product, background, vehicle.ridePlace, vehicle.providerColor, vehicle.providerTextColor)

/** Green for a change with room, dark grey for a normal one, red for a risky one. */
fun tightnessColor(tightness: Tightness): Color = when (tightness) {
    Tightness.Comfortable -> AppColors.Early
    Tightness.Tight -> AppColors.Muted
    Tightness.Risky -> AppColors.DelayRed
    Tightness.VeryRisky -> AppColors.VeryRisky
}

/** The ground of a tag or chip for a change of [tightness]. */
fun tightnessContainer(tightness: Tightness): Color = when (tightness) {
    Tightness.Comfortable -> AppColors.CalmContainer
    Tightness.Tight -> AppColors.Chip
    Tightness.Risky -> AppColors.DelayContainer
    Tightness.VeryRisky -> AppColors.VeryRiskyContainer
}

/** The ink on [tightnessContainer]. */
fun tightnessInk(tightness: Tightness): Color = when (tightness) {
    Tightness.Comfortable -> AppColors.CalmInk
    Tightness.Tight -> AppColors.Ink
    Tightness.Risky -> AppColors.DelayInk
    Tightness.VeryRisky -> AppColors.VeryRiskyInk
}

/**
 * The word for a change of [tightness]: relaxed, normal, tight (Risky) or sprint (VeryRisky). The same words
 * as the categories of journeys (Tightness.Tight is the category Normal), so a column tagged
 * "normal" does not call its change something else in its detail.
 */
fun tightnessName(tightness: Tightness): Int = when (tightness) {
    Tightness.Comfortable -> R.string.tightness_comfortable
    Tightness.Tight -> R.string.tightness_tight
    Tightness.Risky -> R.string.tightness_risky
    Tightness.VeryRisky -> R.string.tightness_very_risky
}

/** The gap between the floating tab bar and the lower edge of the screen. */
val NAV_BAR_BOTTOM = 12.dp

/** The visible gap between the lower edge of a sheet or card and the top of the floating tab bar. */
val SHEET_NAV_GAP = 12.dp

/**
 * The room a page leaves at its lower edge for the floating tab bar: the bar, the gap below
 * it and [SHEET_NAV_GAP] above it. A sheet that ends this far above the lower edge stands
 * clear of the bar. The bar is measured, so the room is still right with a larger font.
 */
val LocalNavInset = compositionLocalOf { 98.dp }

/**
 * Where the page covers the map, in pixels of the screen: [0] the lower edge of what lies
 * over its top, [1] the upper edge of what lies over its bottom, and where there is a third
 * entry, [2] the right edge of a panel over its left side (landscape); NaN while unknown. The
 * map frames lines and journeys into the room between them. Null where no map lies behind.
 */
val LocalMapCover = compositionLocalOf<FloatArray?> { null }

/** Reports this element as what covers the top of the map, by its lower edge. */
@Composable
fun Modifier.coversMapTop(): Modifier {
    val edges = LocalMapCover.current ?: return this
    return this.onGloballyPositioned { MapEdges.report(edges, 0, it.boundsInRoot().bottom) }
}

/** Reports this element as what covers the bottom of the map, by its upper edge. */
@Composable
fun Modifier.coversMapBottom(): Modifier {
    val edges = LocalMapCover.current ?: return this
    return this.onGloballyPositioned { MapEdges.report(edges, 1, it.boundsInRoot().top) }
}

/** Reports this element as a panel that covers the left side of the map, by its right edge. */
@Composable
fun Modifier.coversMapStart(): Modifier {
    val edges = LocalMapCover.current?.takeIf { it.size > 2 } ?: return this
    return this.onGloballyPositioned { edges[2] = it.boundsInRoot().right }
}

/**
 * Lets a list item fade in and rise from below with a spring when it first shows; items
 * further down follow about 80 ms later each (see staggerDelay). With animations switched
 * off in the system the item is simply there.
 */
@Composable
fun appearModifier(index: Int): Modifier {
    val on = rememberAnimationsOn()
    val progress = remember { Animatable(if (on) 0f else 1f) }
    LaunchedEffect(progress) {
        if (progress.value >= 1f) return@LaunchedEffect
        // One row after the other from below, each settling with a little bounce.
        animationDelay(staggerDelay(index))
        progress.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow))
    }
    return Modifier.graphicsLayer {
        alpha = progress.value.coerceIn(0f, 1f)
        translationY = (1f - progress.value) * 22.dp.toPx()
    }
}

/**
 * The badge of [line] of [product]: its name on the colour of the line. For a ride, a
 * departure or a vehicle, [at] (where it runs) and the colours its provider sent with it
 * ([provided], [providedText]) make its colour that of the very ride; the overloads for a
 * [Leg] and a [Departure] pass them.
 */
@Composable
fun LineBadge(
    line: String?,
    product: String,
    modifier: Modifier = Modifier,
    minWidth: Dp = 46.dp,
    at: Pair<Double, Double>? = null,
    provided: Int? = null,
    providedText: Int? = null,
    replacement: RailReplacement? = railReplacement(line, product),
) {
    if (replacement != null) {
        RailReplacementBadge(line, product, replacement, modifier, minWidth, at, provided)
        return
    }
    val color = lineColor(line, product, at, provided)
    val shape = RoundedCornerShape(LINE_BADGE_CORNER)
    val outline = badgeOutline(color)
    val mode = modeHint(line, product)
    val spoken = listOfNotNull(mode?.let { modeWord(it) }, line).joinToString(" ").ifEmpty { null }
    Box(
        modifier = modifier
            // Read out by its full name ("Bus 187"), with the kind of vehicle where the badge shows it.
            .then(if (spoken != null) Modifier.clearAndSetSemantics { contentDescription = spoken } else Modifier)
            .defaultMinSize(minWidth = minWidth)
            // A long name never grows the badge past its place (see badgeLine).
            .widthIn(max = BADGE_MAX_WIDTH + if (mode != null) MODE_MARK_SIZE + 3.dp else 0.dp)
            .clip(shape)
            .background(color)
            .then(if (outline != null) Modifier.border(1.dp, outline, shape) else Modifier)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        val ink = lineInk(line, product, color, at, provided, providedText)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            if (mode != null) ModeMark(mode, ink, MODE_MARK_SIZE)
            Text(
                text = badgeLine(line) ?: "?",
                color = ink,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The corner of a line badge. A row that starts with a badge and clips itself (for its ripple)
 * must not clip with a larger corner: it would cut the badge's left corners rounder than its
 * right ones (#121: the ICE badge of the calm companion's ride card).
 */
val LINE_BADGE_CORNER = 10.dp

/** The size of the mode icon in a line badge (see [ModeMark]). */
val MODE_MARK_SIZE = 13.dp

// The size of the mode icon in the labelled bar of a journey's rides.
private val BAR_MODE_MARK_SIZE = 11.dp

/** The word for [mode], read out before the name of a line whose badge shows its icon. */
@Composable
fun modeWord(mode: TravelMode): String =
    stringResource(if (mode == TravelMode.Bus) R.string.mode_bus else R.string.mode_train)

/**
 * A small icon of a bus or a train in [color], drawn beside the name of a line where the name
 * does not tell the kind of vehicle (see modeHint): a bus as a box with a window band and two
 * wheels, a train as a box with a rounded front, two lamps and the legs of its bogie.
 */
@Composable
fun ModeMark(mode: TravelMode, color: Color, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val line = w * 0.11f
        val stroke = Stroke(width = line, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (mode) {
            TravelMode.Bus -> {
                drawRoundRect(color, Offset(w * 0.16f, w * 0.08f), Size(w * 0.68f, w * 0.7f), CornerRadius(w * 0.12f), style = stroke)
                drawLine(color, Offset(w * 0.16f, w * 0.44f), Offset(w * 0.84f, w * 0.44f), strokeWidth = line)
                drawCircle(color, radius = w * 0.09f, center = Offset(w * 0.33f, w * 0.86f))
                drawCircle(color, radius = w * 0.09f, center = Offset(w * 0.67f, w * 0.86f))
            }
            TravelMode.Train -> {
                drawRoundRect(color, Offset(w * 0.2f, w * 0.06f), Size(w * 0.6f, w * 0.7f), CornerRadius(w * 0.22f), style = stroke)
                drawLine(color, Offset(w * 0.2f, w * 0.38f), Offset(w * 0.8f, w * 0.38f), strokeWidth = line)
                drawCircle(color, radius = w * 0.06f, center = Offset(w * 0.36f, w * 0.57f))
                drawCircle(color, radius = w * 0.06f, center = Offset(w * 0.64f, w * 0.57f))
                drawLine(color, Offset(w * 0.34f, w * 0.8f), Offset(w * 0.22f, w * 0.96f), strokeWidth = line, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.66f, w * 0.8f), Offset(w * 0.78f, w * 0.96f), strokeWidth = line, cap = StrokeCap.Round)
            }
        }
    }
}

/** The widest a line badge gets: [BADGE_MAX_CHARS] characters and its paddings. */
val BADGE_MAX_WIDTH = 124.dp

// A badge whose colour stands out less than this (contrast) from the ground or the accent
// colour of the scheme gets a thin outline, so its edge still shows.
private const val BADGE_OUTLINE_CONTRAST = 1.6f

/**
 * The outline of a line badge in [color], or null where none is needed: the colours of the
 * lines are fixed, the grounds and the accent follow the scheme (Material You, an own accent),
 * so a white, a black or an accent-like line can melt into the card it lies on, into the
 * accent, or into the container of the accent under a picked row.
 */
internal fun badgeOutline(color: Color): Color? {
    val close = contrastOf(color, AppColors.Card) < BADGE_OUTLINE_CONTRAST ||
        contrastOf(color, AppColors.Ground) < BADGE_OUTLINE_CONTRAST ||
        contrastOf(color, AppColors.Blue) < BADGE_OUTLINE_CONTRAST ||
        contrastOf(color, AppColors.BlueContainer) < BADGE_OUTLINE_CONTRAST
    return if (close) AppColors.Ink.copy(alpha = 0.38f) else null
}

/**
 * The badge of a rail replacement service: "SEV" and the line it stands in for, outlined and
 * striped in the colour of that line (of the bus itself where the line is not known), so it
 * never passes for the train.
 */
@Composable
private fun RailReplacementBadge(
    line: String?,
    product: String,
    replacement: RailReplacement,
    modifier: Modifier,
    minWidth: Dp,
    at: Pair<Double, Double>?,
    provided: Int?,
) {
    val replaced = replacement.replaced
    val color = if (replaced != null) lineColor(replaced, replacement.replacedProductOrGuess ?: replacedProduct(replaced), at) else lineColor(line, product, at, provided)
    val shape = RoundedCornerShape(10.dp)
    val label = stringResource(R.string.rail_replacement)
    val spoken = listOfNotNull(label, replaced ?: line?.takeIf { it.isNotBlank() }).joinToString(" ")
    Row(
        modifier = modifier
            .clearAndSetSemantics { contentDescription = spoken }
            .defaultMinSize(minWidth = minWidth)
            .clip(shape)
            .background(AppColors.Card)
            .drawBehind {
                // Thin diagonal stripes in the colour of the line.
                val step = 7.dp.toPx()
                val stroke = 2.dp.toPx()
                val stripe = color.copy(alpha = 0.22f)
                var x = -size.height
                while (x < size.width) {
                    drawLine(stripe, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = stroke)
                    x += step
                }
            }
            .border(2.dp, color, shape)
            .padding(horizontal = 7.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A replacement is a bus, whatever its name: the icon says so (see modeHint).
        ModeMark(TravelMode.Bus, AppColors.Ink, MODE_MARK_SIZE)
        Text(
            text = SEV_MARK,
            color = AppColors.Ink,
            fontWeight = FontWeight.ExtraBold,
            fontSize = if (replaced != null) 11.sp else 15.sp,
            maxLines = 1,
        )
        if (replaced != null) {
            Text(
                text = replaced,
                color = AppColors.Ink,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                maxLines = 1,
            )
        }
    }
}

// The mark of a rail replacement on its badge, the same in every language (as on the signs).
internal const val SEV_MARK = "SEV"

/**
 * A small tag "Ersatzverkehr" on a ride or departure that is a rail replacement service,
 * read out with the line it stands in for.
 */
@Composable
fun RailReplacementTag(replacement: RailReplacement, modifier: Modifier = Modifier) {
    val text = stringResource(R.string.rail_replacement)
    val note = replacement.replaced?.let { stringResource(R.string.rail_replacement_for, it) }
    Text(
        text = text,
        color = AppColors.Ink,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        maxLines = 1,
        softWrap = false,
        modifier = modifier
            .then(if (note != null) Modifier.semantics { contentDescription = "$text, $note" } else Modifier)
            .clip(RoundedCornerShape(6.dp))
            .background(AppColors.Chip)
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/** The badge of the line of the ride [leg] in its own colour (see [LineBadge]). */
@Composable
fun LineBadge(leg: Leg, modifier: Modifier = Modifier, minWidth: Dp = 46.dp) =
    LineBadge(leg.line, leg.product, modifier, minWidth, leg.ridePlace, leg.providerColor, leg.providerTextColor, leg.railReplacement)

/** The badge of the line of [departure] in its own colour (see [LineBadge]). */
@Composable
fun LineBadge(departure: Departure, modifier: Modifier = Modifier, minWidth: Dp = 46.dp) =
    LineBadge(
        departure.line,
        departure.product,
        modifier,
        minWidth,
        departure.ridePlace,
        departure.providerColor,
        departure.providerTextColor,
        departure.railReplacement,
    )


@Composable
fun MinutesPill(
    minutes: Long?,
    live: Boolean,
    cancelled: Boolean = false,
    modifier: Modifier = Modifier,
    // The time counted down to: in its last minute the pill counts seconds ("in 45 s") instead of "now".
    at: OffsetDateTime? = null,
) {
    val gone = minutes != null && minutes < 0
    val lastSeconds = rememberLastMinuteSeconds(at, near = !cancelled && minutes != null && minutes in 0L..1L)
    val background = when {
        cancelled -> AppColors.DelayRed
        live && !gone -> AppColors.Lime
        else -> AppColors.Chip
    }
    val foreground = when {
        cancelled -> AppColors.OnDelayRed
        gone -> AppColors.Muted
        live -> AppColors.OnLime
        else -> AppColors.Ink
    }
    val label = when {
        cancelled -> stringResource(R.string.cancelled)
        minutes == null -> "–"
        minutes <= -FAR_MINUTES -> stringResource(R.string.span_ago, spanLabel(-minutes))
        // The minutes of the page may lag a little behind the clock of the last minute: it goes first.
        lastSeconds != null -> stringResource(R.string.r9_in_seconds, lastSeconds)
        minutes < 0 -> stringResource(R.string.minutes_ago, -minutes)
        minutes <= 0 -> stringResource(R.string.now)
        else -> waitLabel(minutes)
    }
    Box(
        modifier = modifier
            .speaks(spokenTime(label))
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        // A new number rolls in with a spring: from below while it counts down, from above
        // when it grows (a delay that got longer).
        val animate = rememberAnimationsOn()
        AnimatedContent(
            targetState = (if (cancelled) null else minutes) to label,
            transitionSpec = { rollOrSnap(rollsUp(initialState.first, targetState.first), animate) },
            label = "minutes",
        ) { (_, text) ->
            Text(
                text = text,
                color = foreground,
                fontWeight = FontWeight.ExtraBold,
                fontSize = if (gone) 13.sp else 16.sp,
                maxLines = 1,
            )
        }
    }
}

/** Every departure row is this tall, so a list of them can be sized by its rows. */
val DEPARTURE_ROW_HEIGHT = 60.dp

/** The setting how delays are shown (see DelayDisplay), readable wherever a time is drawn. */
val LocalDelayDisplay = compositionLocalOf { DelayDisplay.RealtimeAndDelay }

/** Whether a "+2" stands beside a time with this setting. */
val DelayDisplay.showsDelay: Boolean get() = this != DelayDisplay.RealtimeOnly && this != DelayDisplay.StruckPlanned

/** Whether the timetable time stands struck through beside a changed expected time with this setting. */
val DelayDisplay.strikesPlanned: Boolean get() = this == DelayDisplay.StruckPlanned

/** The timetable time of a late or early run, struck through (see DelayDisplay.StruckPlanned). */
@Composable
fun StruckTime(planned: OffsetDateTime, fontSize: TextUnit = 11.sp, modifier: Modifier = Modifier) {
    Text(
        text = planned.clock(),
        color = AppColors.Muted,
        textDecoration = TextDecoration.LineThrough,
        fontWeight = FontWeight.Medium,
        fontSize = fontSize,
        maxLines = 1,
        modifier = modifier,
    )
}

/** Whether the time shown is the timetable time with this setting (when there is one). */
val DelayDisplay.showsPlanned: Boolean get() = this == DelayDisplay.PlannedAndDelay

/**
 * The clock time to show of a time [actual] (delay included) planned for [planned]: the
 * timetable time with PlannedAndDelay, else the expected one.
 */
fun DelayDisplay.clockOf(actual: OffsetDateTime, planned: OffsetDateTime?): OffsetDateTime =
    if (showsPlanned && planned != null) planned else actual

/**
 * The colour of a shown time: a timetable time and a time without live data stay [plain];
 * an expected time is red when late and green when on time.
 */
fun DelayDisplay.timeColor(realtime: Boolean, delayMinutes: Long, plain: Color): Color =
    timeToneColor(shownTone(this, realtime, delayMinutes), plain)

/** The colour of a clock time's mark (see timeTone, the same on the watch). */
fun timeToneColor(tone: TimeTone, plain: Color): Color = when (tone) {
    TimeTone.Scheduled -> plain
    TimeTone.Late -> AppColors.DelayRed
    TimeTone.OnTime -> AppColors.Early
    // #277: estimated from the position ("ca."): the blue of the own position on the map.
    TimeTone.Estimated -> AppColors.Blue
}

/**
 * #277: the colour of a time the app estimated from the position on the lime of the counters
 * (hero, pinned line): the blue of the own position, darkened where it would not read there;
 * [plain] for any other time.
 */
fun countInk(estimated: Boolean, plain: Color = AppColors.OnLime): Color =
    if (estimated) readableOn(AppColors.Lime, AppColors.Blue) else plain

/** #277: "ca." ([about]) and the clock time [time] the app estimated from the position, both blue. */
fun AnnotatedString.Builder.appendEstimatedTime(about: String, time: OffsetDateTime) {
    withStyle(SpanStyle(color = timeToneColor(TimeTone.Estimated, AppColors.Blue))) {
        append(about + " " + time.clock())
    }
}

/** The colour of a delay's mark (see delayTone, the same on the watch). */
fun delayToneColor(tone: DelayTone): Color = when (tone) {
    DelayTone.Late -> AppColors.DelayRed
    DelayTone.Early -> AppColors.Early
}

/**
 * Appends one time as the delay setting [display] says: the clock time in its colour ([plain]
 * without live data or as timetable time), then a small "+2" unless the coloured time stands alone.
 */
fun AnnotatedString.Builder.appendDelayedTime(
    display: DelayDisplay,
    actual: OffsetDateTime,
    planned: OffsetDateTime?,
    delayMinutes: Long,
    realtime: Boolean,
    plain: Color,
) {
    // One decision for every view (see shownTime): "Takt" style strikes the timetable time first.
    val shown = shownTime(display, actual, planned, realtime, delayMinutes)
    shown.struck?.let { struck ->
        withStyle(SpanStyle(color = AppColors.Muted, textDecoration = TextDecoration.LineThrough, fontSize = 0.8.em)) {
            append(struck.clock())
        }
        append("\u2009")
    }
    withStyle(SpanStyle(color = timeToneColor(shown.tone, plain))) {
        append(shown.clock.clock())
    }
    shown.delay?.let { delay ->
        withStyle(
            SpanStyle(
                color = delayToneColor(if (delay > 0) DelayTone.Late else DelayTone.Early),
                fontSize = 0.62.em,
                fontWeight = FontWeight.Bold,
            ),
        ) {
            append("\u2009" + "%+d".format(delay))
        }
    }
}

/**
 * One time of a ride ([time]) as the delay setting says (see shownTime), in one line: the
 * timetable time struck through, the clock time green, red or [plain], the small "+2". A time
 * the app estimated is blue with "ca." and without a delay (#277); [passed] stops are grey and plain.
 */
@Composable
fun DelayedTime(
    time: StopTime,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 14.sp,
    fontWeight: FontWeight = FontWeight.SemiBold,
    plain: Color = AppColors.Ink,
    passed: Boolean = false,
) {
    val display = LocalDelayDisplay.current
    Text(
        text = when {
            time.estimated && !passed -> buildAnnotatedString { appendEstimatedTime(stringResource(R.string.estimated_prefix), time.actual) }
            time.estimated -> AnnotatedString(stringResource(R.string.estimated_prefix) + " " + time.actual.clock())
            passed -> AnnotatedString(display.clockOf(time.actual, time.planned).clock())
            else -> buildAnnotatedString { appendDelayedTime(display, time.actual, time.planned, time.delayMinutes, time.realtime, plain) }
        },
        color = if (time.estimated || passed) AppColors.Muted else plain,
        fontWeight = fontWeight,
        fontSize = fontSize,
        maxLines = 1,
        modifier = modifier,
    )
}

/**
 * The string [pattern] ("ab %s", "an %s", "dann %s %s") with [time] put in as the delay setting
 * says (see DelayedTime); [args] are its arguments, null standing for the time. The time is
 * coloured, the rest of the text keeps the colour of its Text.
 */
@Composable
fun delayedTimeText(pattern: Int, time: StopTime, plain: Color, args: List<String?> = listOf(null)): AnnotatedString {
    val display = LocalDelayDisplay.current
    val template = stringResource(pattern, *args.map { it ?: "\u0000" }.toTypedArray())
    val about = stringResource(R.string.estimated_prefix)
    return buildAnnotatedString {
        template.forEach { char ->
            when {
                char != '\u0000' -> append(char)
                time.estimated -> appendEstimatedTime(about, time.actual)
                else -> appendDelayedTime(display, time.actual, time.planned, time.delayMinutes, time.realtime, plain)
            }
        }
    }
}

/** [parts] in one text, each apart by " · ", their styles kept. */
fun joinedText(parts: List<AnnotatedString>): AnnotatedString = buildAnnotatedString {
    parts.forEachIndexed { index, part ->
        if (index > 0) append(" · ")
        append(part)
    }
}

/**
 * The time range of [journey] ("12:09 +2 – 12:41") as the delay setting says, in the words of
 * [pattern] (a string with two %s, time_range or time_span). A cancelled journey shows plain times.
 */
@Composable
fun journeySpanText(journey: Journey, pattern: Int, plain: Color): AnnotatedString {
    val display = LocalDelayDisplay.current
    val template = stringResource(pattern, "\u0000", "\u0001")
    val first = journey.legs.first()
    val last = journey.legs.last()
    return buildAnnotatedString {
        template.forEach { char ->
            when {
                char == '\u0000' && journey.cancelled -> append(journey.departure.clock())
                char == '\u0001' && journey.cancelled -> append(journey.arrival.clock())
                char == '\u0000' -> appendDelayedTime(display, first.departure, first.plannedDeparture, first.departureDelayMinutes, first.realtime, plain)
                char == '\u0001' -> appendDelayedTime(display, last.arrival, last.plannedArrival, last.arrivalDelayMinutes, last.realtime, plain)
                else -> append(char)
            }
        }
    }
}

/** The setting "show the stops of changes in the chart"; off, the gaps show only their minutes. */
val LocalChartChangeStops = compositionLocalOf { false }

/** The setting where the chart shows the total time of each journey (see ChartTotalTime). */
val LocalChartTotalTime = compositionLocalOf { io.github.jnbp.nextstop.data.ChartTotalTime.Off }

@Composable
fun DepartureRow(
    departure: Departure,
    now: OffsetDateTime,
    modifier: Modifier = Modifier,
    boardTown: String? = null,
    // The row has a header that names its stop (see StopRowHeader): the line below leaves it out.
    stopInHeader: Boolean = false,
) {
    // With the timetable time shown, the countdown follows the timetable too.
    val display = LocalDelayDisplay.current
    val separate = display.showsPlanned && departure.plannedTime != null
    val time = if (separate) departure.plannedTime else (departure.time ?: departure.plannedTime)
    val rawMinutes = time?.let { minutesBetween(now, it) }
    // A late departure counted from the timetable has not left yet: never show it as gone.
    val minutes = if (separate) rawMinutes?.coerceAtLeast(0) else rawMinutes
    val delayMinutes = (departure.delaySeconds ?: 0) / 60
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(DEPARTURE_ROW_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LineBadge(departure)
        Column(modifier = Modifier.weight(1f)) {
            // The ring in words ("Ring, clockwise") instead of the provider's "Ring S41 ⟳".
            val ringWay = ringWayOf(departure.line, departure.direction, departure.product, departure.ridePlace)
            Text(
                text = when (ringWay) {
                    RingWay.Clockwise -> stringResource(R.string.ring_clockwise)
                    RingWay.Counterclockwise -> stringResource(R.string.ring_counterclockwise)
                    // A direction in another town than the board's says it: "Kirchstr. · Zagelsdorf".
                    null -> townedStopName(departure.direction, boardTown)
                },
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val platformText = platformLabel(departure.platform, departure.product)
            // Live times are coloured; a time without live data is marked as timetable.
            val realtime = departure.delaySeconds != null
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // "Takt" style: the timetable time struck through before the new one.
                val struck = departure.plannedTime
                if (display.strikesPlanned && realtime && delayMinutes != 0 && struck != null && time != null) {
                    StruckTime(struck, fontSize = 12.sp)
                }
                // The time first, then where it leaves: "16:03 · Platform 11".
                if (time != null) {
                    // A new expected time after a refresh rolls in as the minutes do: from
                    // above when it got later, from below when it got earlier.
                    RollingText(
                        value = time.toEpochSecond() / 60,
                        // A departure of another day says which day.
                        text = if (time.berlinDate() == now.berlinDate()) time.clock() else dateTimeLabel(time),
                        color = timeToneColor(timeTone(realtime && !separate, delayMinutes.toLong()), AppColors.Muted),
                        fontWeight = if (realtime) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 12.sp,
                    )
                }
                // "+2" beside the time, unless the setting shows the coloured expected time alone;
                // a changed delay rolls in too.
                val delayTone = delayTone(realtime, delayMinutes.toLong())
                if (display.showsDelay && delayTone != null) {
                    RollingText(
                        value = delayMinutes.toLong(),
                        text = stringResource(R.string.delay_minutes, delayMinutes),
                        color = delayToneColor(delayTone),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                    )
                }
                // How full it is expected to be, where the provider says so.
                departure.occupancy?.let { OccupancyGauge(it) }
                // A cancelled run has no live time either: the red pill says enough.
                if (!realtime && !departure.cancelled) {
                    Text(
                        text = stringResource(R.string.scheduled),
                        color = AppColors.Muted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                // In a list of several stops: where it leaves from, with its platform, unless
                // the header of the row names the stop already.
                val stopName = if (stopInHeader) null else departureStopLabel(departure, boardTown)
                val place = when {
                    stopName != null && platformText != null -> "$stopName · $platformText"
                    else -> stopName ?: platformText
                }
                if (place != null) {
                    Text(
                        text = if (time != null) "· $place" else place,
                        color = if (stopName != null) AppColors.Ink else AppColors.Muted,
                        fontWeight = if (stopName != null) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                // An extra run: one that stands in for a cancelled run of its line, or one on top.
                if (departure.additional && !departure.cancelled) {
                    ExtraRunTag(departure.replaces)
                }
                departure.railReplacement?.let { RailReplacementTag(it) }
            }
        }
        MinutesPill(
            minutes = minutes,
            // Highlighted from SOON_MINUTES on, "now" included (see minutesTone, the same on the watch).
            live = minutesTone(minutes, departure.cancelled) == MinutesTone.Soon,
            cancelled = departure.cancelled,
            at = time,
        )
    }
}

/**
 * A small tag on an extra run: "Ersatzfahrt" where it replaces the cancelled run of line
 * [replaces] (which the board then leaves out, see mergeReplacements), "Zusatzfahrt" else.
 */
@Composable
fun ExtraRunTag(replaces: String?, modifier: Modifier = Modifier) {
    val text = stringResource(if (replaces != null) R.string.replacement_run else R.string.additional_run)
    val note = replaces?.let { stringResource(R.string.replacement_for, shortLine(it) ?: it) }
    Text(
        text = text,
        color = AppColors.Ink,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        maxLines = 1,
        softWrap = false,
        modifier = modifier
            .then(if (note != null) Modifier.semantics { contentDescription = "$text, $note" } else Modifier)
            .clip(RoundedCornerShape(6.dp))
            .background(AppColors.Chip)
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/** What the menu of a departure offers to do with it. */
enum class DepartureAction { Course, ShowOnMap, FromHere, ToDestination, Favorite }

/**
 * A small visible ⋮ that opens the menu of a row. A long press on the row may open the same
 * menu, but only as a shortcut: every action stays reachable through a visible control.
 */
@Composable
fun MoreDots(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = AppColors.Muted,
    size: Dp = 40.dp,
) {
    // Three drawn dots, exactly in the middle of the button (#186): the character "⋮" sits on the
    // math axis of the font, below the middle of its line box, so a ⋮ centred as text stood low.
    Box(
        modifier = modifier
            .clip(CircleShape)
            .clickable(onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label }
            .size(size)
            .drawBehind {
                val radius = MORE_DOT_RADIUS.toPx()
                val gap = MORE_DOT_GAP.toPx()
                for (offset in -1..1) drawCircle(color, radius, center = Offset(this.size.width / 2f, this.size.height / 2f + offset * gap))
            },
    )
}

// The dots of MoreDots: their radius and the distance between their centres.
private val MORE_DOT_RADIUS = 2.dp
private val MORE_DOT_GAP = 5.5.dp

/**
 * The menu of a row without a ⋮ of its own (the stops between, #186): the row itself is the
 * visible control, a tap opens the menu and so does a long press (Joel's rule: nothing may be
 * reachable by a long press alone). A screen reader offers both, named [label].
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.rowMenu(label: String, onOpen: () -> Unit): Modifier =
    combinedClickable(
        onClickLabel = label,
        role = Role.Button,
        onLongClickLabel = label,
        onLongClick = onOpen,
        onClick = onOpen,
    )

/**
 * A departure that can be used: a tap shows the course of its line; the ⋮ at its end (or,
 * as a shortcut, a long press) opens a menu. [stopFavorite] says whether the stop it leaves from is a favourite already.
 * [modifier] shapes the row; [contentPadding] is the room inside it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DepartureItem(
    departure: Departure,
    now: OffsetDateTime,
    stopFavorite: Boolean,
    onAction: (DepartureAction) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    // The town of the board (see townOf): directions in other towns name theirs.
    boardTown: String? = null,
    // The row has a header that names its stop (see StopRowHeader).
    stopInHeader: Boolean = false,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val hasCourse = departure.tripId != null
    val destination = departure.destination
    val courseLabel = stringResource(R.string.line_course)
    val mapLabel = stringResource(R.string.departure_on_map)
    val fromLabel = stringResource(R.string.journeys_from_here)
    val toLabel = if (destination == null) null else stringResource(R.string.journeys_to, destination.name.shortStopName())
    val favoriteLabel = stringResource(if (stopFavorite) R.string.stop_unfavorite else R.string.stop_favorite)
    val menuLabel = stringResource(R.string.departure_menu)
    // Where the row is, so the course grows out of it.
    val bounds = remember { arrayOfNulls<Rect>(1) }
    Box(modifier = modifier.boundsInto(bounds)) {
        Row(
            modifier = Modifier
                .combinedClickable(
                    onClickLabel = if (hasCourse) courseLabel else null,
                    onLongClickLabel = menuLabel,
                    onLongClick = { menuOpen = true },
                    // Without a handle for its trip there is no course to show: the menu opens instead.
                    onClick = {
                        if (hasCourse) {
                            OpenOrigin.set(bounds[0])
                            onAction(DepartureAction.Course)
                        } else {
                            menuOpen = true
                        }
                    },
                )
                .padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DepartureRow(departure, now, modifier = Modifier.weight(1f), boardTown = boardTown, stopInHeader = stopInHeader)
            // The menu in plain sight: journeys from here or to its destination, the stop as a
            // favourite. It opens at the dots, on the right, not at the start of the row.
            Box {
                MoreDots(label = menuLabel, onClick = { menuOpen = true }, size = 36.dp)
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    val entries = listOfNotNull(
                        if (hasCourse) DepartureAction.Course to courseLabel else null,
                        // The vehicle live on the map tab, with the course of its line.
                        if (hasCourse) DepartureAction.ShowOnMap to mapLabel else null,
                        DepartureAction.FromHere to fromLabel,
                        if (toLabel == null) null else DepartureAction.ToDestination to toLabel,
                        DepartureAction.Favorite to favoriteLabel,
                    )
                    entries.forEach { (action, label) ->
                        DropdownMenuItem(
                            text = { Text(text = label) },
                            onClick = {
                                menuOpen = false
                                onAction(action)
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * A number that rolls to a new value instead of jumping there: from below when it gets
 * smaller, from above when it grows. It rolls inside a slot as wide as [slotDigits] digits
 * (wider for a longer number), so a unit beside it stays where it is (see [RollingText]).
 */
@Composable
fun CountingText(
    value: Int,
    color: Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.ExtraBold,
    slotDigits: Int = 2,
) {
    RollingText(
        value = value.toLong(),
        text = value.toString(),
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        modifier = modifier,
        slotDigits = slotDigits,
    )
}

// Pulling a sheet further than this puts it away or brings it back.
private val SHEET_PULL = 36.dp

/**
 * Content at the lower edge that can be put away: dragging it down, or a tap on its
 * handle, leaves only the handle, so what lies behind is free. Dragging the handle up, or
 * another tap, brings the content back.
 */
@Composable
fun CollapsibleBottom(
    collapsed: Boolean,
    onCollapsed: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // How far the finger has pulled, in pixels; the content follows at half the pace.
    var pull by remember { mutableFloatStateOf(0f) }
    val threshold = with(LocalDensity.current) { SHEET_PULL.toPx() }
    val handleLabel = stringResource(if (collapsed) R.string.sheet_show else R.string.sheet_hide)
    Column(
        modifier = modifier.draggable(
            state = rememberDraggableState { delta -> pull += delta },
            orientation = Orientation.Vertical,
            onDragStopped = {
                if (pull > threshold) onCollapsed(true) else if (pull < -threshold) onCollapsed(false)
                // The content settles back with a spring.
                animate(
                    initialValue = pull,
                    targetValue = 0f,
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
                ) { value, _ -> pull = value }
            },
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GlassSurface(shape = RoundedCornerShape(16.dp)) {
            Box(
                modifier = Modifier
                    .semantics { contentDescription = handleLabel }
                    .clickable { onCollapsed(!collapsed) }
                    .size(width = 96.dp, height = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(AppColors.Muted),
                )
            }
        }
        AnimatedVisibility(
            visible = !collapsed,
            enter = expandVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
            exit = shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .graphicsLayer {
                        val down = pull.coerceAtLeast(0f)
                        translationY = down * 0.5f
                        alpha = 1f - (down / (threshold * 3f)).coerceIn(0f, 0.6f)
                    },
            ) {
                content()
            }
        }
    }
}

/**
 * A chip button. Active chips are white and opaque, inactive ones are glass.
 * With [strong] the active chip is blue: for one choice out of several next to each other.
 */
@Composable
fun ChoiceChip(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    strong: Boolean = false,
    minHeight: Dp = 48.dp,
) {
    val shape = RoundedCornerShape(24.dp)
    // A pressed chip gives way and springs back, with a light click (see Haptics).
    val interactions = remember { MutableInteractionSource() }
    val ticks = rememberTicks()
    Box(
        modifier = modifier
            .semantics { if (description != null) contentDescription = description }
            .springPress(interactions)
            .clip(shape)
            .then(
                when {
                    active && strong -> Modifier.background(AppColors.Blue)
                    active -> Modifier.background(AppColors.Card)
                    else -> glassGround()
                },
            )
            .border(1.dp, if (active && strong) AppColors.Blue else glassRim(), shape)
            .clickable(interactionSource = interactions, indication = LocalIndication.current) {
                ticks.tick()
                onClick()
            }
            .defaultMinSize(minHeight = minHeight)
            .widthIn(max = 260.dp)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (active && strong) AppColors.OnBlue else AppColors.Ink,
            fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The sign of a disruption notice: an orange triangle with a white exclamation mark. */
@Composable
fun WarningMark(modifier: Modifier = Modifier, size: Dp = 16.dp) {
    val description = stringResource(R.string.warning_mark)
    Canvas(
        modifier = modifier
            .semantics { contentDescription = description }
            .size(size),
    ) {
        val w = this.size.width
        val triangle = Path().apply {
            moveTo(w * 0.5f, w * 0.14f)
            lineTo(w * 0.92f, w * 0.86f)
            lineTo(w * 0.08f, w * 0.86f)
            close()
        }
        drawPath(triangle, AppColors.Warning)
        // The same shape as an outline rounds the corners off.
        drawPath(triangle, AppColors.Warning, style = Stroke(width = w * 0.16f, join = StrokeJoin.Round))
        drawLine(
            Color.White,
            Offset(w * 0.5f, w * 0.38f),
            Offset(w * 0.5f, w * 0.58f),
            strokeWidth = w * 0.11f,
            cap = StrokeCap.Round,
        )
        drawCircle(Color.White, radius = w * 0.065f, center = Offset(w * 0.5f, w * 0.76f))
    }
}

/** Thin orange stripes along the right edge of a ride block: this ride has a disruption notice. */
fun Modifier.warningBand(): Modifier = drawWithContent {
    drawContent()
    val band = 7.dp.toPx()
    val left = size.width - band
    val step = 5.dp.toPx()
    clipRect(left = left, top = 0f, right = size.width, bottom = size.height) {
        drawRect(AppColors.Card, topLeft = Offset(left, 0f), size = Size(band, size.height))
        var y = -band
        while (y < size.height) {
            drawLine(
                AppColors.Warning,
                Offset(left, y + band),
                Offset(size.width, y),
                strokeWidth = 1.5.dp.toPx(),
            )
            y += step
        }
    }
}

/** Slanted stripes on the ground of an element, from [fromShare] of its height down to its lower edge. */
fun Modifier.stripes(color: Color, fromShare: Float = 0f, step: Dp = 7.dp, width: Dp = 3.dp): Modifier =
    stripes(color, { fromShare }, step, width)

/**
 * Slanted stripes as above, with [fromShare] read while drawing: where it moves every frame (the
 * late end of a block of the chart as it is zoomed) only the stripes are drawn again.
 */
fun Modifier.stripes(color: Color, fromShare: () -> Float, step: Dp = 7.dp, width: Dp = 3.dp): Modifier =
    drawWithContent {
        val top = size.height * fromShare().coerceIn(0f, 1f)
        val gap = step.toPx()
        clipRect(left = 0f, top = top, right = size.width, bottom = size.height) {
            var x = -size.height
            while (x < size.width) {
                drawLine(color, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = width.toPx())
                x += gap
            }
        }
        // The stripes lie on the ground of the element, below what it holds.
        drawContent()
    }

/**
 * The disruption notices of a journey in one calm row that says how many there are.
 * A tap unfolds them, so they do not push the journey itself down the page.
 */
@Composable
fun WarningSummary(warnings: List<Warning>, modifier: Modifier = Modifier) {
    if (warnings.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    val animate = rememberAnimationsOn()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(AppColors.WarningContainer)
            .clickable { open = !open }
            .then(if (animate) Modifier.animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WarningMark()
            val single = warnings.singleOrNull()
            Text(
                // One notice can say what it is about right away; several are counted.
                text = when {
                    single != null && !open -> single.summary.ifEmpty { single.text }
                    single != null -> stringResource(R.string.notice_one)
                    else -> stringResource(R.string.notices_many, warnings.size)
                },
                color = AppColors.Ink,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Chevron(open = open)
        }
        if (open) {
            warnings.forEach { warning ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    // Addresses, e-mail addresses and phone numbers in a notice can be tapped.
                    Text(
                        text = linkedText(warning.summary.ifEmpty { warning.text }, AppColors.Blue),
                        color = AppColors.Ink,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    )
                    if (warning.summary.isNotEmpty() && warning.text.isNotEmpty()) {
                        Text(text = linkedText(warning.text, AppColors.Blue), color = AppColors.Muted, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/** A small arrow that points down while something is folded and up while it is open; it turns over (at once, with animations off). */
@Composable
fun Chevron(open: Boolean, modifier: Modifier = Modifier, color: Color = AppColors.Muted) {
    val animate = rememberAnimationsOn()
    val turn by animateFloatAsState(
        targetValue = if (open) 180f else 0f,
        animationSpec = if (animate) spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow) else snap(),
        label = "chevron",
    )
    Canvas(
        modifier = modifier
            .size(16.dp)
            .graphicsLayer { rotationZ = turn },
    ) {
        val w = size.width
        val arrow = Path().apply {
            moveTo(w * 0.25f, w * 0.38f)
            lineTo(w * 0.5f, w * 0.64f)
            lineTo(w * 0.75f, w * 0.38f)
        }
        drawPath(arrow, color, style = Stroke(width = w * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** The icons of the app, drawn here so no icon library is needed. */
enum class Glyph {
    Back, Close, Gear, Star, StarFilled, Play, Swap, Plus, Minus, Locate, Search, Home, Work, Recent, Route, Map, Walk,
    Heart, Gym, School, Shop, Station, Navigate, Share, Calendar, Family, Friends, Party, Pin, Via, More, Bike,
    Paste, LiveShare, Message, Doctor, Food, Airport, Culture, Park, Hotel,
    Alarm, Bolt, Flag, OpenFull,
}

/** The icon a favourite place is drawn with. */
fun placeGlyph(icon: PlaceIcon): Glyph = when (icon) {
    PlaceIcon.Home -> Glyph.Home
    PlaceIcon.Work -> Glyph.Work
    PlaceIcon.Heart -> Glyph.Heart
    PlaceIcon.Star -> Glyph.Star
    PlaceIcon.Gym -> Glyph.Gym
    PlaceIcon.School -> Glyph.School
    PlaceIcon.Shop -> Glyph.Shop
    PlaceIcon.Station -> Glyph.Station
    PlaceIcon.Family -> Glyph.Family
    PlaceIcon.Friends -> Glyph.Friends
    PlaceIcon.Party -> Glyph.Party
    PlaceIcon.Pin -> Glyph.Pin
    PlaceIcon.Doctor -> Glyph.Doctor
    PlaceIcon.Food -> Glyph.Food
    PlaceIcon.Airport -> Glyph.Airport
    PlaceIcon.Culture -> Glyph.Culture
    PlaceIcon.Park -> Glyph.Park
    PlaceIcon.Hotel -> Glyph.Hotel
}

/** What a favourite place is called while the user gave it no name of its own. */
@Composable
fun placeIconLabel(icon: PlaceIcon): String = stringResource(placeIconLabelRes(icon))

/** The string of [placeIconLabel], for code outside of composition (the share texts). */
internal fun placeIconLabelRes(icon: PlaceIcon): Int = when (icon) {
    PlaceIcon.Home -> R.string.home
    PlaceIcon.Work -> R.string.work
    PlaceIcon.Heart -> R.string.icon_heart
    PlaceIcon.Star -> R.string.icon_star
    PlaceIcon.Gym -> R.string.icon_gym
    PlaceIcon.School -> R.string.icon_school
    PlaceIcon.Shop -> R.string.icon_shop
    PlaceIcon.Station -> R.string.icon_station
    PlaceIcon.Family -> R.string.icon_family
    PlaceIcon.Friends -> R.string.icon_friends
    PlaceIcon.Party -> R.string.icon_party
    PlaceIcon.Pin -> R.string.icon_pin
    PlaceIcon.Doctor -> R.string.icon_doctor
    PlaceIcon.Food -> R.string.icon_food
    PlaceIcon.Airport -> R.string.icon_airport
    PlaceIcon.Culture -> R.string.icon_culture
    PlaceIcon.Park -> R.string.icon_park
    PlaceIcon.Hotel -> R.string.icon_hotel
}

@Composable
fun GlyphIcon(glyph: Glyph, modifier: Modifier = Modifier, color: Color = AppColors.Ink, size: Dp = 22.dp) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val line = w * 0.1f
        val stroke = Stroke(width = line, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun segment(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color, Offset(w * x1, w * y1), Offset(w * x2, w * y2), strokeWidth = line, cap = StrokeCap.Round)
        fun polyline(vararg points: Float): Path = Path().apply {
            moveTo(w * points[0], w * points[1])
            for (index in 2 until points.size step 2) lineTo(w * points[index], w * points[index + 1])
        }
        when (glyph) {
            Glyph.Back -> {
                segment(0.2f, 0.5f, 0.82f, 0.5f)
                drawPath(polyline(0.46f, 0.24f, 0.2f, 0.5f, 0.46f, 0.76f), color, style = stroke)
            }

            Glyph.Close -> {
                segment(0.25f, 0.25f, 0.75f, 0.75f)
                segment(0.75f, 0.25f, 0.25f, 0.75f)
            }

            // A thick ring with short, wide teeth sitting directly on it.
            Glyph.Gear -> {
                drawCircle(color, radius = w * 0.24f, center = center, style = Stroke(width = w * 0.16f))
                for (tooth in 0 until 8) {
                    val angle = tooth * PI / 4
                    val dx = cos(angle).toFloat()
                    val dy = sin(angle).toFloat()
                    drawLine(
                        color,
                        Offset(center.x + dx * w * 0.3f, center.y + dy * w * 0.3f),
                        Offset(center.x + dx * w * 0.46f, center.y + dy * w * 0.46f),
                        strokeWidth = w * 0.17f,
                        cap = StrokeCap.Butt,
                    )
                }
            }

            Glyph.Star, Glyph.StarFilled -> {
                val star = Path()
                for (point in 0 until 10) {
                    val radius = if (point % 2 == 0) w * 0.44f else w * 0.19f
                    val angle = -PI / 2 + point * PI / 5
                    val x = w * 0.5f + radius * cos(angle).toFloat()
                    val y = w * 0.52f + radius * sin(angle).toFloat()
                    if (point == 0) star.moveTo(x, y) else star.lineTo(x, y)
                }
                star.close()
                if (glyph == Glyph.StarFilled) drawPath(star, color)
                drawPath(star, color, style = stroke)
            }

            Glyph.Play -> {
                val triangle = polyline(0.3f, 0.2f, 0.82f, 0.5f, 0.3f, 0.8f).apply { close() }
                drawPath(triangle, color)
                drawPath(triangle, color, style = stroke)
            }

            Glyph.Swap -> {
                segment(0.33f, 0.2f, 0.33f, 0.8f)
                drawPath(polyline(0.17f, 0.36f, 0.33f, 0.2f, 0.49f, 0.36f), color, style = stroke)
                segment(0.67f, 0.2f, 0.67f, 0.8f)
                drawPath(polyline(0.51f, 0.64f, 0.67f, 0.8f, 0.83f, 0.64f), color, style = stroke)
            }

            Glyph.Plus -> {
                segment(0.2f, 0.5f, 0.8f, 0.5f)
                segment(0.5f, 0.2f, 0.5f, 0.8f)
            }

            Glyph.Minus -> segment(0.2f, 0.5f, 0.8f, 0.5f)

            // A ring with a dot: where am I.
            Glyph.Locate -> {
                drawCircle(color, radius = w * 0.36f, center = center, style = stroke)
                drawCircle(color, radius = w * 0.13f, center = center)
            }

            Glyph.Search -> {
                drawCircle(color, radius = w * 0.27f, center = Offset(w * 0.45f, w * 0.45f), style = stroke)
                segment(0.66f, 0.66f, 0.86f, 0.86f)
            }

            Glyph.Home -> {
                drawPath(polyline(0.14f, 0.48f, 0.5f, 0.16f, 0.86f, 0.48f), color, style = stroke)
                drawPath(polyline(0.24f, 0.44f, 0.24f, 0.84f, 0.76f, 0.84f, 0.76f, 0.44f), color, style = stroke)
            }

            // A case with a handle.
            Glyph.Work -> {
                drawPath(
                    polyline(0.15f, 0.34f, 0.85f, 0.34f, 0.85f, 0.82f, 0.15f, 0.82f).apply { close() },
                    color,
                    style = stroke,
                )
                drawPath(polyline(0.37f, 0.34f, 0.37f, 0.2f, 0.63f, 0.2f, 0.63f, 0.34f), color, style = stroke)
            }

            // A clock.
            Glyph.Recent -> {
                drawCircle(color, radius = w * 0.4f, center = center, style = stroke)
                segment(0.5f, 0.5f, 0.5f, 0.27f)
                segment(0.5f, 0.5f, 0.67f, 0.6f)
            }

            // From one stop along a winding way to another, as the route tab draws it: a lone
            // diagonal from a ring to a dot read as a sign to enlarge something.
            // Three dots one above the other: more actions.
            Glyph.More -> {
                drawCircle(color, radius = w * 0.07f, center = Offset(w * 0.5f, w * 0.25f))
                drawCircle(color, radius = w * 0.07f, center = Offset(w * 0.5f, w * 0.5f))
                drawCircle(color, radius = w * 0.07f, center = Offset(w * 0.5f, w * 0.75f))
            }

            // The way of Route with a dot in its middle: a place on the way.
            Glyph.Via -> {
                val u = w / 24f
                drawCircle(color, radius = u * 2.5f, center = Offset(u * 6f, u * 18f), style = stroke)
                drawCircle(color, radius = u * 2.5f, center = Offset(u * 18f, u * 6f), style = stroke)
                val way = Path().apply {
                    moveTo(u * 15.5f, u * 6f)
                    lineTo(u * 10f, u * 6f)
                    arcTo(Rect(u * 7f, u * 6f, u * 13f, u * 12f), 270f, -180f, false)
                    lineTo(u * 14f, u * 12f)
                    arcTo(Rect(u * 11f, u * 12f, u * 17f, u * 18f), 270f, 180f, false)
                    lineTo(u * 8.5f, u * 18f)
                }
                drawPath(way, color, style = stroke)
                drawCircle(color, radius = u * 3f, center = Offset(u * 12f, u * 12f))
            }

            Glyph.Route -> {
                val u = w / 24f
                drawCircle(color, radius = u * 2.5f, center = Offset(u * 6f, u * 18f), style = stroke)
                drawCircle(color, radius = u * 2.5f, center = Offset(u * 18f, u * 6f))
                val way = Path().apply {
                    moveTo(u * 15.5f, u * 6f)
                    lineTo(u * 10f, u * 6f)
                    arcTo(Rect(u * 7f, u * 6f, u * 13f, u * 12f), 270f, -180f, false)
                    lineTo(u * 14f, u * 12f)
                    arcTo(Rect(u * 11f, u * 12f, u * 17f, u * 18f), 270f, 180f, false)
                    lineTo(u * 8.5f, u * 18f)
                }
                drawPath(way, color, style = stroke)
            }

            // A folded map.
            Glyph.Map -> {
                drawPath(
                    polyline(
                        0.1f, 0.27f, 0.37f, 0.15f, 0.63f, 0.27f, 0.9f, 0.15f,
                        0.9f, 0.73f, 0.63f, 0.85f, 0.37f, 0.73f, 0.1f, 0.85f,
                    ).apply { close() },
                    color,
                    style = stroke,
                )
                segment(0.37f, 0.15f, 0.37f, 0.73f)
                segment(0.63f, 0.27f, 0.63f, 0.85f)
            }

            // A figure in mid-stride.
            Glyph.Walk -> {
                drawCircle(color, radius = w * 0.09f, center = Offset(w * 0.56f, w * 0.14f))
                segment(0.53f, 0.32f, 0.46f, 0.58f)
                drawPath(polyline(0.36f, 0.5f, 0.53f, 0.34f, 0.66f, 0.48f, 0.78f, 0.47f), color, style = stroke)
                drawPath(polyline(0.24f, 0.9f, 0.36f, 0.74f, 0.46f, 0.58f, 0.6f, 0.72f, 0.63f, 0.9f), color, style = stroke)
            }

            // A bicycle from the side: two wheels, the frame, saddle and handlebar.
            Glyph.Bike -> {
                val thin = Stroke(width = w * 0.075f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                drawCircle(color, radius = w * 0.17f, center = Offset(w * 0.23f, w * 0.66f), style = thin)
                drawCircle(color, radius = w * 0.17f, center = Offset(w * 0.77f, w * 0.66f), style = thin)
                drawPath(polyline(0.23f, 0.66f, 0.46f, 0.66f, 0.66f, 0.42f, 0.4f, 0.42f, 0.23f, 0.66f), color, style = thin)
                drawPath(polyline(0.46f, 0.66f, 0.37f, 0.3f), color, style = thin)
                drawPath(polyline(0.77f, 0.66f, 0.62f, 0.26f, 0.72f, 0.26f), color, style = thin)
                drawPath(polyline(0.3f, 0.3f, 0.44f, 0.3f), color, style = thin)
            }

            Glyph.Heart -> {
                val heart = Path().apply {
                    moveTo(w * 0.5f, w * 0.84f)
                    cubicTo(w * 0.12f, w * 0.56f, w * 0.1f, w * 0.3f, w * 0.3f, w * 0.22f)
                    cubicTo(w * 0.4f, w * 0.18f, w * 0.48f, w * 0.24f, w * 0.5f, w * 0.32f)
                    cubicTo(w * 0.52f, w * 0.24f, w * 0.6f, w * 0.18f, w * 0.7f, w * 0.22f)
                    cubicTo(w * 0.9f, w * 0.3f, w * 0.88f, w * 0.56f, w * 0.5f, w * 0.84f)
                    close()
                }
                drawPath(heart, color, style = stroke)
            }

            // A dumbbell.
            Glyph.Gym -> {
                segment(0.3f, 0.5f, 0.7f, 0.5f)
                segment(0.26f, 0.3f, 0.26f, 0.7f)
                segment(0.14f, 0.4f, 0.14f, 0.6f)
                segment(0.74f, 0.3f, 0.74f, 0.7f)
                segment(0.86f, 0.4f, 0.86f, 0.6f)
            }

            // A graduation cap.
            Glyph.School -> {
                drawPath(polyline(0.08f, 0.4f, 0.5f, 0.22f, 0.92f, 0.4f, 0.5f, 0.58f).apply { close() }, color, style = stroke)
                drawPath(polyline(0.27f, 0.52f, 0.27f, 0.7f, 0.5f, 0.8f, 0.73f, 0.7f, 0.73f, 0.52f), color, style = stroke)
            }

            // A shopping bag.
            Glyph.Shop -> {
                drawPath(polyline(0.2f, 0.36f, 0.8f, 0.36f, 0.86f, 0.86f, 0.14f, 0.86f).apply { close() }, color, style = stroke)
                drawPath(polyline(0.36f, 0.36f, 0.36f, 0.26f, 0.5f, 0.16f, 0.64f, 0.26f, 0.64f, 0.36f), color, style = stroke)
            }

            // The front of a train.
            Glyph.Station -> {
                drawPath(polyline(0.24f, 0.14f, 0.76f, 0.14f, 0.76f, 0.7f, 0.24f, 0.7f).apply { close() }, color, style = stroke)
                segment(0.24f, 0.42f, 0.76f, 0.42f)
                drawCircle(color, radius = w * 0.045f, center = Offset(w * 0.37f, w * 0.56f))
                drawCircle(color, radius = w * 0.045f, center = Offset(w * 0.63f, w * 0.56f))
                segment(0.34f, 0.7f, 0.24f, 0.88f)
                segment(0.66f, 0.7f, 0.76f, 0.88f)
            }

            // The arrow of a compass: lead me there.
            Glyph.Navigate -> drawPath(
                polyline(0.5f, 0.12f, 0.84f, 0.86f, 0.5f, 0.68f, 0.16f, 0.86f).apply { close() },
                color,
                style = stroke,
            )

            // Three dots joined by two lines: one end hands on to the other two.
            Glyph.Share -> {
                segment(0.3f, 0.5f, 0.7f, 0.25f)
                segment(0.3f, 0.5f, 0.7f, 0.75f)
                drawCircle(color, radius = w * 0.12f, center = Offset(w * 0.27f, w * 0.5f))
                drawCircle(color, radius = w * 0.12f, center = Offset(w * 0.73f, w * 0.23f))
                drawCircle(color, radius = w * 0.12f, center = Offset(w * 0.73f, w * 0.77f))
            }

            // A tall and a small figure side by side.
            Glyph.Family -> {
                drawCircle(color, radius = w * 0.1f, center = Offset(w * 0.36f, w * 0.24f), style = stroke)
                drawPath(polyline(0.16f, 0.84f, 0.16f, 0.56f, 0.24f, 0.44f, 0.48f, 0.44f, 0.56f, 0.56f, 0.56f, 0.84f), color, style = stroke)
                drawCircle(color, radius = w * 0.075f, center = Offset(w * 0.74f, w * 0.46f), style = stroke)
                drawPath(polyline(0.62f, 0.84f, 0.62f, 0.7f, 0.68f, 0.62f, 0.8f, 0.62f, 0.86f, 0.7f, 0.86f, 0.84f), color, style = stroke)
            }

            // Two figures of the same size, one a little behind the other.
            Glyph.Friends -> {
                drawCircle(color, radius = w * 0.1f, center = Offset(w * 0.34f, w * 0.32f), style = stroke)
                drawPath(polyline(0.14f, 0.84f, 0.14f, 0.66f, 0.22f, 0.54f, 0.46f, 0.54f, 0.54f, 0.66f, 0.54f, 0.84f), color, style = stroke)
                drawCircle(color, radius = w * 0.1f, center = Offset(w * 0.68f, w * 0.26f), style = stroke)
                drawPath(polyline(0.64f, 0.48f, 0.8f, 0.48f, 0.88f, 0.6f, 0.88f, 0.78f), color, style = stroke)
            }

            // A flag on a pole: the party, a rally, a meeting.
            Glyph.Party -> {
                segment(0.24f, 0.12f, 0.24f, 0.9f)
                drawPath(
                    polyline(0.24f, 0.16f, 0.82f, 0.16f, 0.68f, 0.33f, 0.82f, 0.5f, 0.24f, 0.5f).apply { close() },
                    color,
                    style = stroke,
                )
            }

            // A map pin: a drop standing on its point, with a hole.
            Glyph.Pin -> {
                val drop = Path().apply {
                    moveTo(w * 0.5f, w * 0.9f)
                    cubicTo(w * 0.3f, w * 0.66f, w * 0.2f, w * 0.52f, w * 0.2f, w * 0.38f)
                    cubicTo(w * 0.2f, w * 0.2f, w * 0.34f, w * 0.08f, w * 0.5f, w * 0.08f)
                    cubicTo(w * 0.66f, w * 0.08f, w * 0.8f, w * 0.2f, w * 0.8f, w * 0.38f)
                    cubicTo(w * 0.8f, w * 0.52f, w * 0.7f, w * 0.66f, w * 0.5f, w * 0.9f)
                    close()
                }
                drawPath(drop, color, style = stroke)
                drawCircle(color, radius = w * 0.09f, center = Offset(w * 0.5f, w * 0.38f))
            }

            // A cross, as on a doctor's sign.
            Glyph.Doctor -> drawPath(
                polyline(
                    0.38f, 0.14f, 0.62f, 0.14f, 0.62f, 0.38f, 0.86f, 0.38f, 0.86f, 0.62f, 0.62f, 0.62f,
                    0.62f, 0.86f, 0.38f, 0.86f, 0.38f, 0.62f, 0.14f, 0.62f, 0.14f, 0.38f, 0.38f, 0.38f,
                ).apply { close() },
                color,
                style = stroke,
            )

            // A cup with steam: eating out, a café.
            Glyph.Food -> {
                drawPath(polyline(0.18f, 0.38f, 0.7f, 0.38f, 0.64f, 0.84f, 0.24f, 0.84f).apply { close() }, color, style = stroke)
                drawPath(polyline(0.7f, 0.46f, 0.84f, 0.48f, 0.84f, 0.62f, 0.67f, 0.66f), color, style = stroke)
                segment(0.36f, 0.14f, 0.36f, 0.27f)
                segment(0.52f, 0.14f, 0.52f, 0.27f)
            }

            // A plane from above, nose up.
            Glyph.Airport -> {
                segment(0.5f, 0.1f, 0.5f, 0.88f)
                drawPath(polyline(0.12f, 0.6f, 0.5f, 0.38f, 0.88f, 0.6f), color, style = stroke)
                drawPath(polyline(0.34f, 0.88f, 0.5f, 0.77f, 0.66f, 0.88f), color, style = stroke)
            }

            // A note of music: culture, a concert, a night out.
            Glyph.Culture -> {
                segment(0.62f, 0.16f, 0.62f, 0.7f)
                segment(0.62f, 0.16f, 0.84f, 0.3f)
                drawCircle(color, radius = w * 0.13f, center = Offset(w * 0.48f, w * 0.72f), style = stroke)
            }

            // A tree: a park, the green.
            Glyph.Park -> {
                drawCircle(color, radius = w * 0.25f, center = Offset(w * 0.5f, w * 0.38f), style = stroke)
                segment(0.5f, 0.63f, 0.5f, 0.9f)
                segment(0.36f, 0.9f, 0.64f, 0.9f)
            }

            // A bed with a pillow: a hotel, somewhere to sleep.
            Glyph.Hotel -> {
                segment(0.12f, 0.26f, 0.12f, 0.84f)
                segment(0.12f, 0.66f, 0.88f, 0.66f)
                segment(0.88f, 0.66f, 0.88f, 0.84f)
                drawPath(polyline(0.4f, 0.66f, 0.4f, 0.46f, 0.82f, 0.46f, 0.88f, 0.54f, 0.88f, 0.66f), color, style = stroke)
                drawCircle(color, radius = w * 0.08f, center = Offset(w * 0.25f, w * 0.54f), style = stroke)
            }

            // A clipboard: a board with its clip on top and two lines of text.
            Glyph.Paste -> {
                drawRoundRect(
                    color,
                    topLeft = Offset(w * 0.2f, w * 0.18f),
                    size = Size(w * 0.6f, w * 0.7f),
                    cornerRadius = CornerRadius(w * 0.1f),
                    style = stroke,
                )
                drawRoundRect(
                    color,
                    topLeft = Offset(w * 0.36f, w * 0.1f),
                    size = Size(w * 0.28f, w * 0.16f),
                    cornerRadius = CornerRadius(w * 0.05f),
                )
                segment(0.34f, 0.48f, 0.66f, 0.48f)
                segment(0.34f, 0.66f, 0.56f, 0.66f)
            }

            // A position dot sending waves to both sides: a trip that others follow live.
            Glyph.LiveShare -> {
                drawCircle(color, radius = w * 0.11f, center = Offset(w * 0.5f, w * 0.5f))
                for (radius in floatArrayOf(0.24f, 0.4f)) {
                    val topLeft = Offset(w * (0.5f - radius), w * (0.5f - radius))
                    val box = Size(w * radius * 2, w * radius * 2)
                    // Each wave is a quarter of a circle, on the left and on the right of the dot.
                    drawArc(color, startAngle = 135f, sweepAngle = 90f, useCenter = false, topLeft = topLeft, size = box, style = stroke)
                    drawArc(color, startAngle = -45f, sweepAngle = 90f, useCenter = false, topLeft = topLeft, size = box, style = stroke)
                }
            }

            // A speech bubble with two lines of text: a message without a link.
            Glyph.Message -> {
                drawRoundRect(
                    color,
                    topLeft = Offset(w * 0.12f, w * 0.16f),
                    size = Size(w * 0.76f, w * 0.56f),
                    cornerRadius = CornerRadius(w * 0.14f),
                    style = stroke,
                )
                drawPath(polyline(0.3f, 0.72f, 0.24f, 0.88f, 0.46f, 0.72f), color, style = stroke)
                segment(0.3f, 0.37f, 0.7f, 0.37f)
                segment(0.3f, 0.53f, 0.56f, 0.53f)
            }

            // An alarm clock as Material draws it: a round face with two hands and the two
            // bells on its shoulders.
            Glyph.Alarm -> {
                drawCircle(color, radius = w * 0.31f, center = Offset(w * 0.5f, w * 0.55f), style = stroke)
                drawPath(polyline(0.5f, 0.38f, 0.5f, 0.56f, 0.62f, 0.64f), color, style = stroke)
                drawArc(
                    color, startAngle = 200f, sweepAngle = 70f, useCenter = false,
                    topLeft = Offset(w * 0.06f, w * 0.1f), size = Size(w * 0.4f, w * 0.4f), style = stroke,
                )
                drawArc(
                    color, startAngle = 270f, sweepAngle = 70f, useCenter = false,
                    topLeft = Offset(w * 0.54f, w * 0.1f), size = Size(w * 0.4f, w * 0.4f), style = stroke,
                )
            }

            // A lightning bolt, filled: "every minute counts".
            Glyph.Bolt -> drawPath(
                polyline(0.58f, 0.08f, 0.22f, 0.56f, 0.48f, 0.56f, 0.4f, 0.92f, 0.78f, 0.42f, 0.52f, 0.42f).apply { close() },
                color,
            )

            // A flag on its pole: where the trip ends.
            // "Open in full" (#191): two arrows out to opposite corners, each ending in a corner.
            Glyph.OpenFull -> {
                segment(0.58f, 0.42f, 0.82f, 0.18f)
                drawPath(polyline(0.56f, 0.18f, 0.82f, 0.18f, 0.82f, 0.44f), color, style = stroke)
                segment(0.42f, 0.58f, 0.18f, 0.82f)
                drawPath(polyline(0.18f, 0.56f, 0.18f, 0.82f, 0.44f, 0.82f), color, style = stroke)
            }

            Glyph.Flag -> {
                segment(0.26f, 0.12f, 0.26f, 0.9f)
                drawPath(
                    polyline(0.26f, 0.16f, 0.78f, 0.16f, 0.66f, 0.33f, 0.78f, 0.5f, 0.26f, 0.5f).apply { close() },
                    color,
                )
            }

            // A sheet of a calendar: a frame with a bar on top, two rings and a marked day.
            Glyph.Calendar -> {
                drawRoundRect(
                    color,
                    topLeft = Offset(w * 0.16f, w * 0.22f),
                    size = Size(w * 0.68f, w * 0.62f),
                    cornerRadius = CornerRadius(w * 0.1f),
                    style = stroke,
                )
                segment(0.16f, 0.42f, 0.84f, 0.42f)
                segment(0.35f, 0.14f, 0.35f, 0.28f)
                segment(0.65f, 0.14f, 0.65f, 0.28f)
                drawRoundRect(
                    color,
                    topLeft = Offset(w * 0.52f, w * 0.56f),
                    size = Size(w * 0.16f, w * 0.14f),
                    cornerRadius = CornerRadius(w * 0.03f),
                )
            }
        }
    }
}

/**
 * A round button with an icon: glass over the map, or solid white where it lies on a card.
 * While [busy], a turning ring takes the place of the icon: what the button started is on its way.
 */
@Composable
fun RoundButton(
    glyph: Glyph,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    solid: Boolean = false,
    busy: Boolean = false,
) {
    val interactions = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .semantics { contentDescription = description }
            .springPress(interactions)
            .clip(CircleShape)
            .then(if (solid) Modifier.background(AppColors.Card) else glassGround())
            .border(1.dp, glassRim(), CircleShape)
            .clickable(interactionSource = interactions, indication = LocalIndication.current, onClick = onClick)
            .size(size),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = AppColors.Blue,
                strokeWidth = 2.5.dp,
            )
        } else {
            GlyphIcon(glyph)
        }
    }
}

/** The one main action of a page: blue, with an optional icon in front of its text. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    glyph: Glyph? = null,
    minHeight: Dp = 56.dp,
) {
    val interactions = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .springPress(interactions)
            .clip(shape)
            .background(AppColors.Blue)
            .clickable(interactionSource = interactions, indication = LocalIndication.current, onClick = onClick)
            .defaultMinSize(minHeight = minHeight)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (glyph != null) GlyphIcon(glyph, color = AppColors.OnBlue, size = 20.dp)
        Text(
            text = text,
            color = AppColors.OnBlue,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** An action next to the main one, in a quiet colour. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    ground: Color = AppColors.Card,
    ink: Color = AppColors.Ink,
    minHeight: Dp = 56.dp,
) {
    val interactions = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .springPress(interactions)
            .clip(shape)
            .background(ground)
            .clickable(interactionSource = interactions, indication = LocalIndication.current, onClick = onClick)
            .defaultMinSize(minHeight = minHeight)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = ink,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Rows of one group: the outer corners of the first and the last row are round, the ones between rows small. */
fun groupShape(index: Int, lastIndex: Int, outer: Dp = 20.dp): Shape = when {
    lastIndex <= 0 -> RoundedCornerShape(outer)
    index == 0 -> RoundedCornerShape(outer, outer, 8.dp, 8.dp)
    index == lastIndex -> RoundedCornerShape(8.dp, 8.dp, outer, outer)
    else -> RoundedCornerShape(8.dp)
}

/** The small label above a group of rows or tiles. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = AppColors.Muted,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        modifier = modifier.padding(start = 8.dp),
    )
}

/**
 * One stretch of the bar of a journey: a ride, or with no leg the time on foot or waiting,
 * of which [walkMinutes] are on foot.
 */
private data class BarPart(val leg: Leg?, val minutes: Long, val walkMinutes: Long = 0, val cycling: Boolean = false)

// True when a stretch between rides is (also) ridden by bike: the bike combo.
private fun cycles(legs: List<Leg>): Boolean = legs.any { it.walking && it.cycling }

private fun walkMinutes(legs: List<Leg>): Long =
    legs.filter { it.walking }.sumOf { minutesBetween(it.departure, it.arrival) }

/**
 * A walk in a small space: the walking figure and its minutes, with [unit] as "8 min"; with
 * [cycling] a ride by bike (the bike combo) with the bicycle instead.
 */
@Composable
fun WalkMark(
    minutes: Long,
    modifier: Modifier = Modifier,
    color: Color = AppColors.Muted,
    unit: Boolean = false,
    cycling: Boolean = false,
) {
    val description = stringResource(if (cycling) R.string.cycle_minutes else R.string.walk_minutes, minutes)
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        GlyphIcon(if (cycling) Glyph.Bike else Glyph.Walk, color = color, size = if (cycling) 15.dp else 13.dp)
        Text(
            text = if (unit) stringResource(R.string.in_minutes, minutes) else minutes.toString(),
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** A row of dots: time on foot or waiting. */
@Composable
private fun DottedLine(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.height(3.dp)) {
        drawLine(
            AppColors.Rail,
            Offset(size.height / 2f, size.height / 2f),
            Offset(size.width - size.height / 2f, size.height / 2f),
            strokeWidth = size.height,
            cap = StrokeCap.Round,
            // A dash of almost no length with round ends draws as a dot.
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.1f, 7.dp.toPx()), 0f),
        )
    }
}

/**
 * A journey as one bar: a block per ride in the colour of its line, as wide as the ride
 * is long, and a dotted line for the time on foot or waiting. With [labels] the blocks
 * are tall enough to carry the name of their line, and a walk says how long it is.
 */
@Composable
fun LegBar(journey: Journey, modifier: Modifier = Modifier, labels: Boolean = false) {
    val parts = remember(journey) {
        buildList {
            val legs = journey.legs
            var clock = journey.departure
            var from = 0
            legs.forEachIndexed { index, leg ->
                if (!leg.walking) {
                    val gap = minutesBetween(clock, leg.departure)
                    if (gap > 0) add(BarPart(null, gap, walkMinutes(legs.subList(from, index)), cycles(legs.subList(from, index))))
                    add(BarPart(leg, minutesBetween(leg.departure, leg.arrival).coerceAtLeast(1)))
                    clock = leg.arrival
                    from = index + 1
                }
            }
            val rest = minutesBetween(clock, journey.arrival)
            if (rest > 0 || isEmpty()) {
                add(BarPart(null, rest.coerceAtLeast(1), walkMinutes(legs.subList(from, legs.size)), cycles(legs.subList(from, legs.size))))
            }
        }
    }
    val total = parts.sumOf { it.minutes }.coerceAtLeast(1)
    // A very short part keeps a width that can still be seen, and read where it carries a name.
    val least = total * (if (labels) 0.16f else 0.06f)
    val shares = parts.map { maxOf(it.minutes.toFloat(), least) }
    val gap = if (labels) 4.dp else 3.dp
    // With names, every block is at least as wide as its name, so a badge is never cut off:
    // the measured names set the least widths, the rest is shared out by length.
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelStyle = LocalTextStyle.current.merge(TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 13.sp))
    val leastWidths = if (!labels) {
        null
    } else {
        remember(parts, labelStyle, density) {
            parts.map { part ->
                val leg = part.leg
                with(density) {
                    when {
                        leg != null -> measurer.measure(badgeLine(leg.line) ?: "?", labelStyle).size.width.toDp() + 12.dp +
                            (if (modeHint(leg.line, leg.product) != null) BAR_MODE_MARK_SIZE + 2.dp else 0.dp)
                        part.walkMinutes > 0 -> 34.dp
                        else -> 8.dp
                    }
                }
            }
        }
    }
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
    val widths = leastWidths?.let { least -> fitWidths(shares, least, maxWidth - gap * (parts.size - 1).coerceAtLeast(0)) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        parts.forEachIndexed { index, part ->
            val share = shares[index]
            // A fixed width where names need room, else a share of the row.
            val sized = widths?.let { Modifier.width(it[index]) } ?: Modifier.weight(share)
            val leg = part.leg
            if (leg == null && labels && part.walkMinutes > 0) {
                // A walk: the dots make room for the figure and its minutes.
                Row(
                    modifier = sized,
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    DottedLine(modifier = Modifier.weight(1f))
                    WalkMark(part.walkMinutes, cycling = part.cycling)
                    DottedLine(modifier = Modifier.weight(1f))
                }
            } else if (leg == null) {
                DottedLine(modifier = sized)
            } else {
                // Only the cancelled ride is grey; the rides that still run keep their colour, faded.
                val grey = leg.cancelled
                val color = if (grey) AppColors.Grid else lineColor(leg)
                Box(
                    modifier = sized
                        .height(if (labels) 28.dp else 8.dp)
                        .then(if (journey.cancelled && !grey) Modifier.alpha(0.55f) else Modifier)
                        .clip(RoundedCornerShape(if (labels) 10.dp else 4.dp))
                        .background(color),
                    contentAlignment = Alignment.Center,
                ) {
                    if (labels) {
                        val ink = if (grey) AppColors.Muted else onLineColor(color)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            // The kind of vehicle where the name does not tell it (see modeHint).
                            modeHint(leg.line, leg.product)?.let { ModeMark(it, ink, BAR_MODE_MARK_SIZE) }
                            Text(
                                text = badgeLine(leg.line) ?: "?",
                                color = ink,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                lineHeight = 16.sp,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }
        }
    }
    }
}

/**
 * Widths for parts of a row of [room]: each by its [shares], but none narrower than its
 * [least] width; what the narrow ones take more is taken from the others by their shares.
 * When even the least widths do not fit, they are all scaled down alike.
 */
private fun fitWidths(shares: List<Float>, least: List<Dp>, room: Dp): List<Dp> {
    val fixed = BooleanArray(shares.size)
    var widths = List(shares.size) { 0.dp }
    repeat(shares.size + 1) {
        val fixedRoom = least.filterIndexed { index, _ -> fixed[index] }.fold(0.dp) { sum, width -> sum + width }
        val freeShares = shares.filterIndexed { index, _ -> !fixed[index] }.sum().coerceAtLeast(0.0001f)
        val free = (room - fixedRoom).coerceAtLeast(0.dp)
        widths = shares.mapIndexed { index, share -> if (fixed[index]) least[index] else free * (share / freeShares) }
        var changed = false
        widths.forEachIndexed { index, width ->
            if (!fixed[index] && width < least[index]) {
                fixed[index] = true
                changed = true
            }
        }
        if (!changed) return widths
    }
    val sum = widths.fold(0.dp) { total, width -> total + width }
    return if (sum > room && sum > 0.dp) widths.map { it * (room / sum) } else widths
}

/** A thin line between two rows that lie on glass. */
@Composable
fun RowDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(AppColors.Divider),
    )
}

/**
 * A saved place or route as a tile: where it leads, how long the next journey there
 * takes, and that journey as a bar. [set] is false for home or work that is not chosen
 * yet; [journey] is null while it loads ([loaded] false) or when there is none. [here] says
 * that the user is at the place already.
 */
data class PlaceTile(
    val key: String,
    val glyph: Glyph,
    val title: String,
    val set: Boolean,
    val journey: Journey?,
    val loaded: Boolean,
    val onClick: () -> Unit,
    val here: Boolean = false,
    /** A long press: on the favourites page it opens the editor of the place. */
    val onLongClick: (() -> Unit)? = null,
    /** The notices around the place (on the favourites page), and what a tap on their line opens. */
    val disruptions: List<Disruption> = emptyList(),
    val onDisruptions: (() -> Unit)? = null,
    /** The radius [disruptions] were found in (see CardDisruptionLine). */
    val disruptionRadius: Int? = null,
    /** Minutes until the journey leaves, for "leave in x min" (the grid of "My routes"); null: not shown. */
    val leaveMinutes: Long? = null,
)

/**
 * The header of a place tile: [title] (its glyph and name) at the start and [time] at the end
 * of one row where both fit whole; else the title keeps the whole width and the time goes
 * under it (see [tileTimeBelowTitle]). The title is measured once at its natural width, so
 * the choice costs nothing per frame.
 */
@Composable
private fun TileHeader(title: @Composable () -> Unit, time: @Composable () -> Unit) {
    Layout(
        contents = listOf(title, time),
        modifier = Modifier.fillMaxWidth(),
    ) { (titleParts, timeParts), constraints ->
        val gap = 6.dp.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val titlePlaceable = titleParts.firstOrNull()?.measure(loose)
        val timePlaceables = timeParts.map { it.measure(loose) }
        val titleWidth = titlePlaceable?.width ?: 0
        val titleHeight = titlePlaceable?.height ?: 0
        val timeWidth = timePlaceables.maxOfOrNull { it.width } ?: 0
        val timeHeight = timePlaceables.maxOfOrNull { it.height } ?: 0
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else titleWidth + gap + timeWidth
        val below = tileTimeBelowTitle(titleWidth, timeWidth, gap, width)
        val height = when {
            timeHeight == 0 -> titleHeight
            below -> titleHeight + gap + timeHeight
            else -> maxOf(titleHeight, timeHeight)
        }.coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            if (below) {
                titlePlaceable?.place(0, 0)
                timePlaceables.forEach { it.place(0, titleHeight + gap) }
            } else {
                val rowHeight = maxOf(titleHeight, timeHeight)
                titlePlaceable?.place(0, (rowHeight - titleHeight) / 2)
                timePlaceables.forEach { it.place(width - it.width, (rowHeight - it.height) / 2) }
            }
        }
    }
}

// What the lower part of a place tile shows (see its cross-fade).
private const val TILE_JOURNEY = 0
private const val TILE_LOADING = 1
private const val TILE_NOTE = 2

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaceTileCard(tile: PlaceTile, modifier: Modifier = Modifier, onNaturalHeight: ((Int) -> Unit)? = null) {
    val journey = tile.journey
    // A pressed tile gives way a little and springs back, like a chip.
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "tilePress",
    )
    GlassSurface(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            // A place with notices around it is framed in the colour of warnings.
            .disruptionFrame(tile.disruptions.isNotEmpty(), RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // As tall as the tallest tile of the row (see PlaceTiles), all of it a target.
                .fillMaxHeight()
                // A journey that arrives in place of its placeholder grows the tile softly.
                .animateContentSize(Motion.settle())
                .combinedClickable(
                    interactionSource = interactions,
                    indication = LocalIndication.current,
                    onClick = tile.onClick,
                    onLongClick = tile.onLongClick,
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            // The content at its own height, reported to a grid that sizes its rows (see
            // PlaceTileGrid): measured without the height the tile is given, so no feedback.
            val verticalPadding = with(LocalDensity.current) { 24.dp.roundToPx() }
            val latestReport by rememberUpdatedState(onNaturalHeight)
            Column(
                modifier = if (onNaturalHeight != null) {
                    Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(Alignment.Top, unbounded = true)
                        .onSizeChanged { latestReport?.invoke(it.height + verticalPadding) }
                } else {
                    Modifier.fillMaxWidth()
                },
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                // The title has priority over the time: on a narrow tile the whole "Zuhause"
                // stays, and the time moves under it (see TileHeader, tileTimeBelowTitle).
                TileHeader(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            GlyphIcon(tile.glyph, size = 20.dp)
                            Text(
                                text = tile.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                ) {
                    // The minutes come in with the journey, fading and taking their room softly; the
                    // last ones stay while they go.
                    val minutes = journey?.durationMinutes?.toInt()
                    val heldMinutes = remember { IntArray(1) }
                    if (minutes != null) heldMinutes[0] = minutes
                    val shownMinutes = heldMinutes[0]
                    AnimatedVisibility(visible = minutes != null && !tile.here) {
                        // From an hour on in hours and minutes ("2:23" and a small "h"), never one
                        // big number; read out in words ("2 Stunden 23 Minuten").
                        val clock = durationClock(shownMinutes.toLong())
                        Row(modifier = Modifier.speaks(spokenDuration(shownMinutes.toLong()))) {
                            if (clock != null) {
                                RollingText(
                                    value = shownMinutes.toLong(),
                                    text = clock,
                                    color = AppColors.Ink,
                                    fontSize = 22.sp,
                                    modifier = Modifier.alignByBaseline(),
                                )
                                Text(
                                    text = stringResource(R.string.hours_unit),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    modifier = Modifier
                                        .alignByBaseline()
                                        .padding(start = 3.dp),
                                )
                            } else {
                                CountingText(
                                    value = shownMinutes,
                                    color = AppColors.Ink,
                                    fontSize = 22.sp,
                                    modifier = Modifier.alignByBaseline(),
                                )
                                // The prime of minutes hugs its number, at the number's size.
                                Text(
                                    text = stringResource(R.string.minutes_unit),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = unitSize(stringResource(R.string.minutes_unit), 12.sp, 22.sp),
                                    maxLines = 1,
                                    modifier = Modifier
                                        .alignByBaseline()
                                        .padding(start = unitGap(stringResource(R.string.minutes_unit), 3.dp)),
                                )
                            }
                        }
                    }
                }
                // The journey, its shimmering placeholder or a note: a change between them cross-fades
                // instead of switching; a newer journey in place of the last one just updates.
                val phase = when {
                    journey != null && !tile.here -> TILE_JOURNEY
                    tile.set && !tile.here && !tile.loaded -> TILE_LOADING
                    else -> TILE_NOTE
                }
                AnimatedContent(
                    targetState = phase to journey,
                    contentKey = { it.first },
                    transitionSpec = { fadeIn(tween(240, delayMillis = 60)) togetherWith fadeOut(tween(140)) using null },
                    modifier = Modifier.fillMaxWidth(),
                    label = "tileJourney",
                ) { (shownPhase, shownJourney) ->
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        if (shownPhase == TILE_JOURNEY && shownJourney != null) {
                            LegBar(journey = shownJourney, modifier = Modifier.padding(vertical = 4.dp))
                            // A newer journey's times cross-fade in place of the last ones.
                            val timesText = stringResource(R.string.tile_times, shownJourney.departure.clock(), shownJourney.arrival.clock())
                            val leave = tile.leaveMinutes
                            val line = when {
                                leave == null || leave < 0 || leave >= 60 -> timesText
                                leave == 0L -> stringResource(R.string.ways_leave_now) + " · " + timesText
                                else -> stringResource(R.string.ways_leave_tile, leave.toInt()) + " · " + timesText
                            }
                            FadeSwap(target = line) { times ->
                                Text(
                                    text = times,
                                    color = AppColors.Muted,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        } else if (shownPhase == TILE_LOADING) {
                            // While its journey loads: the bar and the times as shimmering placeholders.
                            SkeletonLine(modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth().height(10.dp))
                            SkeletonLine(modifier = Modifier.fillMaxWidth(0.6f).height(12.dp))
                        } else {
                            Text(
                                text = stringResource(
                                    when {
                                        !tile.set -> R.string.not_set
                                        tile.here -> R.string.tile_here
                                        !tile.loaded -> R.string.tile_loading
                                        else -> R.string.tile_none
                                    },
                                ),
                                color = if (tile.here) AppColors.Blue else AppColors.Muted,
                                fontWeight = if (tile.here) FontWeight.SemiBold else null,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                // Disruptions on the lines near the place, in one line (see CardDisruptionLine).
                CardDisruptionLine(items = tile.disruptions, onClick = tile.onDisruptions, radiusMeters = tile.disruptionRadius)
            }
        }
    }
}

/**
 * The tiles in a row: two share the width, further ones are reached by pushing the row
 * sideways. All tiles of the row are as tall as the tallest one, so a tile without a journey
 * does not end short above a gap.
 */
@Composable
fun PlaceTiles(tiles: List<PlaceTile>, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val tileWidth = (maxWidth - 8.dp) / 2
        val scroll = rememberScrollState()
        val density = LocalDensity.current
        // The tallest tile seen so far sets the height of all; intrinsic measuring is not
        // possible here, because the tiles contain subcomposed layouts.
        var tallest by remember(tiles) { mutableIntStateOf(0) }
        Row(
            modifier = Modifier
                .fadeEdges(scroll, horizontal = true)
                .horizontalScroll(scroll)
                // Room inside the scrolling area, so the shadows of the tiles are not cut off.
                .padding(top = 2.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tiles.forEach { tile ->
                key(tile.key) {
                    PlaceTileCard(
                        tile,
                        modifier = Modifier
                            .width(tileWidth)
                            .heightIn(min = with(density) { tallest.toDp() })
                            .onSizeChanged { if (it.height > tallest) tallest = it.height },
                    )
                }
            }
        }
    }
}

/** What a long press on a stop offers to do with it. */
/**
 * What every menu of a stop needs from the app (see [StopMenu]): the ids of the favourite stops,
 * and what a choice does ([onAction]). Provided by AppRoot, so a stop anywhere (the companion's
 * timeline, the search) offers the same menu without handing it through (#139). Null in previews.
 */
class StopMenuHost(val favoriteIds: Set<String>, val onAction: (Stop, StopAction) -> Unit)

val LocalStopMenuHost = compositionLocalOf<StopMenuHost?> { null }

/**
 * The small menu of a stop: "Von hier", "Hierhin", "Abfahrten hier", "Auf der Karte zeigen",
 * "Auf den Startbildschirm", "Als Lieblingshaltestelle" (see stopMenuEntries). It closes itself
 * after a choice. [omit] leaves out what the place it opens from offers on its own. With the
 * [place] given, the favourite entry says whether it is one already (see LocalStopMenuHost).
 */
@Composable
fun StopMenu(
    open: Boolean,
    onDismiss: () -> Unit,
    onAction: (StopAction) -> Unit,
    omit: Set<StopAction> = emptySet(),
    place: Stop? = null,
) {
    // "Add to home screen" only where the launcher takes pinned shortcuts.
    val context = LocalContext.current
    val pinnable = remember(context) { AppShortcuts.canPin(context) }
    val host = LocalStopMenuHost.current
    // A favourite is a stop of the timetable with an id (not a place or a position).
    val favoriteOk = host != null && place != null && place.kind == PlaceKind.Stop
    val favorite = favoriteOk && host!!.favoriteIds.contains(place!!.id)
    DropdownMenu(expanded = open, onDismissRequest = onDismiss) {
        stopMenuEntries(pinnable, favoriteOk, omit).forEach { action ->
            val label = when (action) {
                StopAction.From -> R.string.from_here
                StopAction.To -> R.string.to_here
                StopAction.Departures -> R.string.departures_here
                StopAction.Map -> R.string.show_on_map
                StopAction.Pin -> R.string.pin_to_home
                StopAction.Favorite -> if (favorite) R.string.r6_stop_favorite_remove else R.string.r6_stop_favorite_add
            }
            DropdownMenuItem(
                text = { Text(text = stringResource(label)) },
                onClick = {
                    onDismiss()
                    onAction(action)
                },
            )
        }
    }
}

/**
 * The menu of [place] behind a tap, for a stop that has no menu of its own (the companion's
 * timeline, #139): [content] is the row, the menu opens at its end. Nothing to tap without a
 * host (previews) or a place.
 */
@Composable
fun StopMenuBox(
    place: Stop?,
    modifier: Modifier = Modifier,
    omit: Set<StopAction> = emptySet(),
    // A stop between (#186): the menu opens on a long press as well as on a tap (see rowMenu).
    longPress: Boolean = false,
    content: @Composable () -> Unit,
) {
    val host = LocalStopMenuHost.current
    if (host == null || place == null) {
        Box(modifier = modifier) { content() }
        return
    }
    var open by remember { mutableStateOf(false) }
    val label = stringResource(R.string.stop_menu)
    val opening = if (longPress) Modifier.rowMenu(label) { open = true } else Modifier.clickable(onClickLabel = label, role = Role.Button) { open = true }
    Box(modifier = modifier.then(opening)) {
        content()
        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
            StopMenu(
                open = open,
                onDismiss = { open = false },
                onAction = { action -> host.onAction(place, action) },
                omit = omit,
                place = place,
            )
        }
    }
}

/**
 * True where a [SegmentedToggle] has to fit the width it is given (the rows of the settings
 * and the setup): parts that do not fit side by side wrap into a grid of two columns or a list
 * of their own, and nothing is ever pushed sideways or cut.
 */
val LocalWrapChoices = compositionLocalOf { false }

/**
 * A toggle of two or more parts: the active part is blue, and the blue moves over softly.
 * [ground] is the colour of the other parts; on a white card they need a grey one to show.
 * With [fill] the parts share the whole width evenly. With [twoLines] a label too long for
 * its part wraps onto further lines, centred, and is never cut or ellipsized. Where
 * [LocalWrapChoices] asks for it and the parts do not fit side by side, two or four parts
 * stand in a grid of two columns, three or more than four in a list, one below the other.
 */
@Composable
fun SegmentedToggle(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = 48.dp,
    ground: Color = AppColors.Glass,
    fill: Boolean = false,
    twoLines: Boolean = false,
) {
    if (LocalWrapChoices.current && labels.size > 1) {
        WrappingSegments(labels, selected, onSelect, modifier, minHeight, ground, fill)
        return
    }
    SegmentRow(labels, selected, onSelect, modifier, minHeight, ground, fill, twoLines)
}

/** The parts of a [SegmentedToggle] side by side (the toggle as it always was). */
@Composable
private fun SegmentRow(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier,
    minHeight: Dp,
    ground: Color,
    fill: Boolean,
    twoLines: Boolean,
    // The index of the first part, where a grid shows the parts in several rows.
    first: Int = 0,
    // The parts all as high as the highest (in a grid, where one label may wrap).
    even: Boolean = false,
    shapeOf: (Int) -> Shape = { index ->
        when (index) {
            0 -> RoundedCornerShape(24.dp, 8.dp, 8.dp, 24.dp)
            labels.lastIndex -> RoundedCornerShape(8.dp, 24.dp, 24.dp, 8.dp)
            else -> RoundedCornerShape(8.dp)
        }
    },
) {
    val ticks = rememberTicks()
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        labels.forEachIndexed { index, label ->
            val shape = shapeOf(index)
            val active = first + index == selected
            val background by animateColorAsState(
                if (active) AppColors.Blue else ground,
                spring(stiffness = Spring.StiffnessMediumLow),
                label = "segment",
            )
            val ink by animateColorAsState(
                if (active) AppColors.OnBlue else AppColors.Ink,
                spring(stiffness = Spring.StiffnessMediumLow),
                label = "segmentInk",
            )
            Box(
                modifier = (if (fill) Modifier.weight(1f) else Modifier)
                    .then(if (even) Modifier.fillMaxHeight() else Modifier)
                    .clip(shape)
                    .background(background)
                    .clickable {
                        if (!active) ticks.tick()
                        onSelect(first + index)
                    }
                    .defaultMinSize(minHeight = minHeight)
                    .padding(horizontal = if (fill || labels.size > 3) 4.dp else 14.dp, vertical = if (twoLines) 2.dp else 0.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = ink,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = if (labels.size > 3) 13.sp else 14.sp,
                    lineHeight = if (twoLines) 15.sp else TextUnit.Unspecified,
                    textAlign = TextAlign.Center,
                    // With twoLines a label wraps as far as it needs and is never cut.
                    maxLines = if (twoLines) Int.MAX_VALUE else 1,
                    overflow = if (twoLines) TextOverflow.Clip else TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * A [SegmentedToggle] that fits its width: side by side where the labels fit, else two or
 * four parts in a grid of two columns, and any other number as a list of radio rows.
 */
@Composable
private fun WrappingSegments(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier,
    minHeight: Dp,
    ground: Color,
    fill: Boolean,
) {
    val measurer = rememberTextMeasurer()
    val style = LocalTextStyle.current
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val count = labels.size
        val fontSize = if (count > 3) 13.sp else 14.sp
        val widths = remember(labels, style, density) {
            labels.map { label ->
                measurer.measure(label, style.merge(TextStyle(fontSize = fontSize, fontWeight = FontWeight.Bold)), maxLines = 1).size.width
            }
        }
        val layout = segmentLayout(
            labelWidths = widths,
            available = constraints.maxWidth,
            padding = with(density) { (if (fill || count > 3) 4.dp else 14.dp).roundToPx() },
            gap = with(density) { 3.dp.roundToPx() },
            evenly = fill,
        )
        when (layout) {
            SegmentLayout.Row -> SegmentRow(labels, selected, onSelect, Modifier.fillMaxWidth(), minHeight, ground, fill, twoLines = false)
            SegmentLayout.Grid -> Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                labels.chunked(2).forEachIndexed { row, pair ->
                    val lastRow = row == (count - 1) / 2
                    SegmentRow(
                        labels = pair,
                        selected = selected,
                        onSelect = onSelect,
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        minHeight = minHeight,
                        ground = ground,
                        fill = true,
                        twoLines = true,
                        first = row * 2,
                        even = true,
                        shapeOf = { index -> gridShape(top = row == 0, bottom = lastRow, start = index == 0, end = index == pair.lastIndex) },
                    )
                }
            }
            SegmentLayout.List -> ChoiceList(labels, selected, onSelect, minHeight, ground)
        }
    }
}

// The corners of a part of a grid: round on the outside of the whole, small inside.
private fun gridShape(top: Boolean, bottom: Boolean, start: Boolean, end: Boolean): Shape {
    val round = 20.dp
    val small = 8.dp
    return RoundedCornerShape(
        topStart = if (top && start) round else small,
        topEnd = if (top && end) round else small,
        bottomEnd = if (bottom && end) round else small,
        bottomStart = if (bottom && start) round else small,
    )
}

/** The choices one below the other, each a row with a round mark; the picked one is blue. */
@Composable
private fun ChoiceList(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, minHeight: Dp, ground: Color) {
    val ticks = rememberTicks()
    Column(modifier = Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        labels.forEachIndexed { index, label ->
            val active = index == selected
            val background by animateColorAsState(
                if (active) AppColors.Blue else ground,
                spring(stiffness = Spring.StiffnessMediumLow),
                label = "choiceRow",
            )
            val ink by animateColorAsState(
                if (active) AppColors.OnBlue else AppColors.Ink,
                spring(stiffness = Spring.StiffnessMediumLow),
                label = "choiceInk",
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(gridShape(top = index == 0, bottom = index == labels.lastIndex, start = true, end = true))
                    .background(background)
                    .selectable(selected = active, role = Role.RadioButton) {
                        if (!active) ticks.tick()
                        onSelect(index)
                    }
                    .defaultMinSize(minHeight = minHeight)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .border(2.dp, ink, CircleShape)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(if (active) ink else Color.Transparent),
                )
                Text(
                    text = label,
                    color = ink,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * A calm line for a request that failed, naming what went wrong in plain words. The raw
 * message of the exception is never shown. When no source could be reached at all (#196: no
 * network, or every provider timed out or refused, see failureKind) it is "Dienste nicht
 * erreichbar" and, on a second line, what to do: check the internet where the phone has none,
 * else try again later. [problemText] sets the first line in bold.
 */
fun errorText(context: Context, e: Exception): String = when (failureKind(e)) {
    FailureKind.NoNetwork -> context.getString(R.string.r6s2_services_down) + "\n" + context.getString(R.string.r6s2_check_internet)
    FailureKind.ServicesDown -> context.getString(R.string.r6s2_services_down) + "\n" + context.getString(R.string.r6s2_try_later)
    FailureKind.NothingFound, FailureKind.Failed -> when {
        e is SourceUnreachableException && e.cause is NotSupportedException -> context.getString(R.string.source_not_supported)
        else -> context.getString(R.string.request_failed)
    }
}

/** [text] of [errorText] with its first line in bold where it has two (headline and what to do). */
fun problemText(text: String): AnnotatedString {
    val cut = text.indexOf('\n')
    if (cut < 0) return AnnotatedString(text)
    return buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = AppColors.Ink)) { append(text.substring(0, cut)) }
        append(text.substring(cut))
    }
}


/**
 * The classic signs of a place: S and U for rail, R for regional and F for long-distance
 * trains, the yellow H for bus and tram stops, a dot for points of interest and addresses.
 */
@Composable
fun PlaceSymbols(place: Stop, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        when (place.kind) {
            PlaceKind.Poi -> PoiSymbol(size)
            PlaceKind.Address -> DotSymbol(AppColors.Muted, size)
            PlaceKind.Stop -> {
                val products = place.products
                val suburban = "suburban" in products
                val subway = "subway" in products
                val regional = "regional" in products
                val longDistance = "express" in products
                if (suburban) LetterSymbol("S", Color(0xFF008D4F), Color.White, CircleShape, size)
                if (subway) LetterSymbol("U", Color(0xFF0664AB), Color.White, RoundedCornerShape(5.dp), size)
                if (regional) LetterSymbol("R", Color(0xFFDA251D), Color.White, RoundedCornerShape(5.dp), size)
                if (longDistance) LetterSymbol("F", Color(0xFF5A6072), Color.White, RoundedCornerShape(5.dp), size)
                if (!suburban && !subway && !regional && !longDistance) {
                    LetterSymbol("H", Color(0xFFFFD500), Color(0xFF007A3D), CircleShape, size)
                }
            }
        }
    }
}

/** A point of interest: a pin in a blue disc, apart from the plain dot of an address. */
@Composable
private fun PoiSymbol(size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(AppColors.Blue),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(Glyph.Pin, color = AppColors.OnBlue, size = size * 0.62f)
    }
}

@Composable
private fun LetterSymbol(letter: String, background: Color, foreground: Color, shape: Shape, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter,
            color = foreground,
            fontWeight = FontWeight.ExtraBold,
            fontSize = (size.value * 0.6f).sp,
            lineHeight = (size.value * 0.7f).sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun DotSymbol(color: Color, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size / 3)
                .clip(CircleShape)
                .background(AppColors.Card),
        )
    }
}

/**
 * The switch of the mode "Every minute counts" of a search or a trip: a pill with a small
 * switch in it, so its state reads at a glance and it does not pass for a plain button. Off it
 * is pale glass with a grey switch; on it fills blue and the knob slides over. No rim: as all
 * glass in the app it stands out by brightness alone. A tap toggles it; to accessibility
 * services it is a switch ([Role.Switch]) with its state.
 */
@Composable
fun EveryMinuteChip(active: Boolean, onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier, minHeight: Dp = 48.dp) {
    val shape = RoundedCornerShape(24.dp)
    // A pressed pill gives way and springs back, as the other chips do, with the tick of a switch.
    val interactions = remember { MutableInteractionSource() }
    val ticks = rememberTicks()
    // The blue is laid over the glass and fades in and out, so the change is one smooth step.
    val fill by animateColorAsState(
        if (active) AppColors.Blue else AppColors.Blue.copy(alpha = 0f),
        Motion.quick(),
        label = "everyMinuteFill",
    )
    val ink by animateColorAsState(if (active) AppColors.OnBlue else AppColors.Ink, Motion.quick(), label = "everyMinuteInk")
    val track by animateColorAsState(
        if (active) AppColors.OnBlue else AppColors.Muted.copy(alpha = 0.35f),
        Motion.quick(),
        label = "everyMinuteTrack",
    )
    val knob by animateColorAsState(if (active) AppColors.Blue else AppColors.Card, Motion.quick(), label = "everyMinuteKnob")
    val knobOffset by animateDpAsState(
        if (active) EVERY_MINUTE_TRACK_WIDTH - EVERY_MINUTE_KNOB - 2.dp else 2.dp,
        Motion.quick(),
        label = "everyMinuteKnobOffset",
    )
    Row(
        modifier = modifier
            .springPress(interactions)
            .clip(shape)
            .then(glassGround())
            .background(fill)
            .toggleable(
                value = active,
                interactionSource = interactions,
                indication = LocalIndication.current,
                role = Role.Switch,
                onValueChange = { value ->
                    ticks.toggle(value)
                    onToggle(value)
                },
            )
            .defaultMinSize(minHeight = minHeight)
            .widthIn(max = 260.dp)
            .padding(start = 12.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // The small switch: a track with a knob, drawn here and not a Material Switch, which
        // would keep its own 48 dp touch target and make the pill taller than its neighbours.
        Box(
            modifier = Modifier
                .size(width = EVERY_MINUTE_TRACK_WIDTH, height = EVERY_MINUTE_KNOB + 4.dp)
                .clip(CircleShape)
                .background(track),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .offset(x = knobOffset)
                    .size(EVERY_MINUTE_KNOB)
                    .clip(CircleShape)
                    .background(knob),
            )
        }
        Text(
            text = stringResource(R.string.every_minute),
            color = ink,
            fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// The size of the small switch in the pill of "Every minute counts".
private val EVERY_MINUTE_TRACK_WIDTH = 28.dp
private val EVERY_MINUTE_KNOB = 12.dp

/** The name of a kind of transport as the settings show it. */
fun productLabel(key: String): Int = when (key) {
    "suburban" -> R.string.product_suburban
    "subway" -> R.string.product_subway
    "tram" -> R.string.product_tram
    "bus" -> R.string.product_bus
    "ferry" -> R.string.product_ferry
    "express" -> R.string.product_express
    "coach" -> R.string.product_coach
    else -> R.string.product_regional
}
