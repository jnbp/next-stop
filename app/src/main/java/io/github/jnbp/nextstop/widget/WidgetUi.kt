package io.github.jnbp.nextstop.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import io.github.jnbp.nextstop.MainActivity
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.AppPrefs
import io.github.jnbp.nextstop.data.ChangePositions
import io.github.jnbp.nextstop.data.DelayDisplay
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.PlaceIcon
import io.github.jnbp.nextstop.data.plainStopName
import io.github.jnbp.nextstop.data.railReplacement
import io.github.jnbp.nextstop.data.replacedProduct
import io.github.jnbp.nextstop.ui.ACTION_OPEN_TRIP
import io.github.jnbp.nextstop.ui.SEV_MARK
import io.github.jnbp.nextstop.ui.StepKind
import io.github.jnbp.nextstop.ui.TripStep
import io.github.jnbp.nextstop.ui.boardingHint
import io.github.jnbp.nextstop.ui.boardingPositionOf
import io.github.jnbp.nextstop.ui.clock
import io.github.jnbp.nextstop.ui.lineColor
import io.github.jnbp.nextstop.ui.onLineColor
import io.github.jnbp.nextstop.ui.placeLine
import io.github.jnbp.nextstop.ui.rideAfter
import io.github.jnbp.nextstop.ui.shortLine
import io.github.jnbp.nextstop.ui.badgeLine
import io.github.jnbp.nextstop.ui.stepDelay
import io.github.jnbp.nextstop.ui.stepPlannedTime
import io.github.jnbp.nextstop.ui.tripStep
import io.github.jnbp.nextstop.ui.theme.AppPalette
import io.github.jnbp.nextstop.ui.theme.ColorChoice
import io.github.jnbp.nextstop.ui.theme.appPalette
import androidx.glance.color.ColorProvider as dayNight

/** Taps on a widget that open the app on a stop or start a trip. */
internal const val ACTION_WIDGET_STOP = "io.github.jnbp.nextstop.WIDGET_STOP"
internal const val ACTION_WIDGET_START = "io.github.jnbp.nextstop.WIDGET_START"
internal const val ACTION_WIDGET_SEARCH = "io.github.jnbp.nextstop.WIDGET_SEARCH"
internal const val EXTRA_STOP = "stop"
internal const val EXTRA_FROM = "from"
internal const val EXTRA_TO = "to"
internal const val EXTRA_KEY = "key"
internal const val EXTRA_DEPARTURE = "departure"
internal const val EXTRA_ARRIVAL = "arrival"
internal const val EXTRA_LOCATION = "location"

/** True while a widget is drawn as the generated picture of the widget picker: no taps there. */
internal val LocalWidgetPreview = staticCompositionLocalOf { false }

/** From this width on a widget counts as wide (about three cells and more). */
internal val WIDE_WIDGET = 250.dp

/** From this height on a widget counts as tall (about three rows of cells and more). */
internal val TALL_WIDGET = 170.dp

/**
 * How a widget lays out at its size. [compact] is a single row of cells: one line of content.
 * [roomy] has room for a line more than the basic layout (such as the boarding hint), [tall]
 * for the extras of a large widget. [rows] is how many departures fit below the header.
 */
internal data class WidgetLayout(
    val compact: Boolean = false,
    val wide: Boolean = false,
    val roomy: Boolean = false,
    val tall: Boolean = false,
    val rows: Int = 2,
)

/** The layout for a widget [width] by [height] dp; an unknown size gives the basic layout. */
internal fun widgetLayout(width: Float, height: Float): WidgetLayout {
    val compact = height < 80f
    return WidgetLayout(
        compact = compact,
        wide = width >= WIDE_WIDGET.value,
        roomy = height >= 130f,
        tall = height >= TALL_WIDGET.value,
        // The header takes about 26 dp, each row 28 dp, the padding 24 dp.
        rows = if (compact) 1 else ((height - 24f - 26f) / 28f).toInt().coerceIn(1, 8),
    )
}

/** The colours a widget is drawn with, for its scheme and its light or dark. */
internal class WidgetColors(
    val card: ColorProvider,
    val ink: ColorProvider,
    val muted: ColorProvider,
    val accent: ColorProvider,
    val onAccent: ColorProvider,
    val lime: ColorProvider,
    val onLime: ColorProvider,
    val delay: ColorProvider,
    val chip: ColorProvider,
    val early: ColorProvider,
)

/**
 * The colours of a widget: the Next Stop scheme (white cards, blue and lime), or Material You.
 * Material You following the system takes the dynamic colours of GlanceTheme, which change with
 * the wallpaper on their own; forced light or dark take the dynamic scheme of that moment.
 */
@Composable
internal fun widgetColors(config: WidgetConfig): WidgetColors {
    val context = LocalContext.current
    val material = when (config.scheme) {
        WidgetScheme.Global -> AppPrefs(context).widgetColors == WidgetColorChoice.MaterialYou
        WidgetScheme.NextStop -> false
        WidgetScheme.MaterialYou -> true
    }
    val light = appPalette(context, if (material) ColorChoice.System else ColorChoice.NextStop, 0, false)
    val dark = appPalette(context, if (material) ColorChoice.System else ColorChoice.NextStop, 0, true)
    val nextLight = appPalette(context, ColorChoice.NextStop, 0, false)
    val nextDark = appPalette(context, ColorChoice.NextStop, 0, true)
    fun pick(from: (AppPalette) -> Color, day: AppPalette = light, night: AppPalette = dark): ColorProvider =
        when (config.shade) {
            WidgetShade.System -> dayNight(day = from(day), night = from(night))
            WidgetShade.Light -> ColorProvider(from(day))
            WidgetShade.Dark -> ColorProvider(from(night))
        }
    if (material && config.shade == WidgetShade.System) {
        val theme = GlanceTheme.colors
        return WidgetColors(
            card = theme.widgetBackground,
            ink = theme.onSurface,
            muted = theme.onSurfaceVariant,
            accent = theme.primary,
            onAccent = theme.onPrimary,
            lime = theme.primaryContainer,
            onLime = theme.onPrimaryContainer,
            delay = theme.error,
            chip = theme.secondaryContainer,
            early = pick({ it.early }, nextLight, nextDark),
        )
    }
    return WidgetColors(
        card = pick({ it.card }),
        ink = pick({ it.ink }),
        muted = pick({ it.muted }),
        accent = pick({ it.blue }),
        onAccent = pick({ it.onBlue }),
        lime = pick({ it.lime }),
        onLime = pick({ it.onLime }),
        delay = pick({ it.delayRed }),
        chip = pick({ it.chip }),
        early = pick({ it.early }, nextLight, nextDark),
    )
}

/** The rounded card every widget sits on; a tap runs [onClick] where given. */
@Composable
internal fun WidgetCard(
    colors: WidgetColors,
    onClick: Action?,
    padding: Dp = 12.dp,
    content: @Composable () -> Unit,
) {
    val base = GlanceModifier
        .fillMaxSize()
        .appWidgetBackground()
        .background(colors.card)
        .cornerRadius(android.R.dimen.system_app_widget_background_radius)
    val tappable = onClick != null && !LocalWidgetPreview.current
    Box(modifier = (if (tappable && onClick != null) base.clickable(onClick) else base).padding(padding)) {
        content()
    }
}

@Composable
internal fun Label(
    text: String,
    color: ColorProvider,
    size: TextUnit = 13.sp,
    bold: Boolean = false,
    maxLines: Int = 1,
    modifier: GlanceModifier = GlanceModifier,
    strike: Boolean = false,
) {
    Text(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = size,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            textDecoration = if (strike) TextDecoration.LineThrough else null,
        ),
        maxLines = maxLines,
    )
}

/**
 * The badge of a line as in the app: its short name on its colour, [color] (ARGB, worked out
 * from the ride when the snapshot was made) where known.
 */
@Composable
internal fun LineBadge(line: String?, product: String, height: Dp = 20.dp, size: TextUnit = 12.sp, color: Int? = null) {
    // A rail replacement service (by the name of its line): "SEV" and the replaced line on
    // white, ringed in the colour of that line, as in the app (Glance draws no stripes).
    val replacement = railReplacement(line, product)
    if (replacement != null) {
        val replaced = replacement.replaced
        val ring = if (replaced != null) lineColor(replaced, replacedProduct(replaced)) else color?.let { Color(it) } ?: lineColor(line, product)
        Box(
            modifier = GlanceModifier
                .height(height)
                .background(ColorProvider(ring))
                .cornerRadius(6.dp)
                .padding(2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = GlanceModifier
                    .height(height - 4.dp)
                    .background(ColorProvider(Color.White))
                    .cornerRadius(4.dp)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Label(
                    listOfNotNull(SEV_MARK, replaced).joinToString(" "),
                    ColorProvider(onLineColor(Color.White)),
                    size = size,
                    bold = true,
                )
            }
        }
        return
    }
    val ground = color?.let { Color(it) } ?: lineColor(line, product)
    Box(
        modifier = GlanceModifier
            .height(height)
            .background(ColorProvider(ground))
            .cornerRadius(6.dp)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Label(badgeLine(line) ?: "", ColorProvider(onLineColor(ground)), size = size, bold = true)
    }
}

@Composable
internal fun Gap(width: Dp = 0.dp, height: Dp = 0.dp) {
    Spacer(modifier = GlanceModifier.width(width).height(height))
}

/** Minutes until [time] as the app writes them: "now", "5 min", or the clock time from 100 min on. */
internal fun minutesText(context: Context, time: Long, now: Long): String {
    val minutes = Math.floorDiv(time - now, 60_000L)
    return when {
        minutes < 1 -> context.getString(R.string.trip_now)
        minutes < 100 -> context.getString(R.string.trip_unit_minutes, minutes)
        else -> clockOf(time)
    }
}

internal fun clockOf(time: Long): String =
    OffsetDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneId.systemDefault()).clock()

/**
 * "Location from 25 min ago" where the position the content starts from ([positionAt]) is
 * old enough to say so; the clock time once it is 100 minutes and more. Null otherwise.
 */
internal fun positionNote(context: Context, positionAt: Long?, now: Long): String? {
    val minutes = positionAgeMinutes(positionAt, now) ?: return null
    return if (minutes < 100 || positionAt == null) {
        context.getString(R.string.widget_position_minutes, minutes)
    } else {
        context.getString(R.string.widget_position_clock, clockOf(positionAt))
    }
}

/** What a favourite place is called on a widget: "Home", "To work", "To <name>". */
internal fun favoriteTitle(context: Context, label: String?, icon: PlaceIcon?): String = when {
    !label.isNullOrBlank() -> context.getString(R.string.widget_to_place, label)
    icon == PlaceIcon.Home -> context.getString(R.string.widget_to_home)
    icon == PlaceIcon.Work -> context.getString(R.string.widget_to_work)
    else -> context.getString(R.string.widget_to_favorite)
}

/** The sign of "load anew" on a widget. */
internal const val REFRESH_MARK = "\u21BB"

/** A tappable ↻ that loads all widgets anew (see WidgetRefreshAction); plain in the picker's preview. */
@Composable
internal fun RefreshMark(colors: WidgetColors) {
    val tappable = !LocalWidgetPreview.current
    Label(
        REFRESH_MARK,
        colors.muted,
        size = 13.sp,
        modifier = if (tappable) GlanceModifier.clickable(actionRunCallback<WidgetRefreshAction>()).padding(horizontal = 6.dp) else GlanceModifier,
    )
}

/** Opens the departures of a stop in the app. */
internal fun openStopAction(context: Context, board: StopBoard?): Action {
    val intent = Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    if (board != null) {
        intent.setAction(ACTION_WIDGET_STOP)
            .putExtra(EXTRA_STOP, placeToText(board.stop))
            // Each stop its own intent, so two widgets never share one.
            .setData(Uri.parse("nextstop://widget/stop/" + Uri.encode(board.stop.id)))
    }
    return actionStartActivity(intent)
}

/** Opens the app and starts the trip companion for [connection]. */
internal fun startTripAction(context: Context, connection: WidgetConnection): Action =
    actionStartActivity(
        Intent(context, MainActivity::class.java)
            .setAction(ACTION_WIDGET_START)
            .putExtra(EXTRA_FROM, placeToText(connection.from))
            .putExtra(EXTRA_TO, placeToText(connection.to))
            .putExtra(EXTRA_KEY, connection.key)
            .putExtra(EXTRA_DEPARTURE, connection.departure)
            .setData(Uri.parse("nextstop://widget/start/" + Uri.encode(connection.key)))
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
    )

/**
 * Opens the journey search to the place of [appointment], to be there by its start; the
 * plain app where there is no appointment.
 */
internal fun searchAppointmentAction(context: Context, appointment: WidgetAppointment?): Action {
    if (appointment == null || (appointment.place == null && appointment.location.isBlank())) {
        return openAppAction(context)
    }
    val place = appointment.place ?: appointment.connection?.to
    val intent = Intent(context, MainActivity::class.java)
        .setAction(ACTION_WIDGET_SEARCH)
        .putExtra(EXTRA_LOCATION, appointment.location)
        .putExtra(EXTRA_ARRIVAL, appointment.begin)
        // Each appointment its own intent, so an old one is never reused.
        .setData(Uri.parse("nextstop://widget/search/" + appointment.begin + "/" + Uri.encode(appointment.location)))
        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    if (place != null) intent.putExtra(EXTRA_TO, placeToText(place))
    return actionStartActivity(intent)
}

/** Opens the app; on the page of the trip while one is followed. */
internal fun openAppAction(context: Context, trip: Boolean = false): Action {
    val intent = Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    if (trip) intent.setAction(ACTION_OPEN_TRIP)
    return actionStartActivity(intent)
}

/** A short line that says why there is nothing to show. */
@Composable
internal fun ProblemText(problem: WidgetProblem?, colors: WidgetColors, maxLines: Int = 3) {
    val context = LocalContext.current
    val text = when (problem) {
        WidgetProblem.NoLocation -> context.getString(R.string.widget_no_location)
        WidgetProblem.NoFavorite -> context.getString(R.string.widget_no_favorite)
        WidgetProblem.NoConnection -> context.getString(R.string.widget_no_connection)
        WidgetProblem.Offline -> context.getString(R.string.widget_offline)
        WidgetProblem.NoCalendar -> context.getString(R.string.appointment_denied)
        WidgetProblem.NoAppointment -> context.getString(R.string.appointment_none)
        null -> context.getString(R.string.widget_loading)
    }
    Label(text, colors.muted, size = 12.sp, maxLines = maxLines)
}

// ---------------------------------------------------------------------------------------
// Departures

/**
 * The departures of a stop: its name, then one row per departure with the badge, the
 * direction, the delay in red and the minutes. [rows] is as many as fit; [wide] adds the
 * clock time of each departure, so the widget never only shows minutes that may be old.
 * [compact] is the single row of a widget one cell high: the rows lose their gap. Where the
 * nearest stop comes from an old position ([positionAt]), the header says how old instead
 * of the clock.
 */
@Composable
internal fun DeparturesBody(
    board: StopBoard?,
    problem: WidgetProblem?,
    colors: WidgetColors,
    now: Long,
    rows: Int,
    wide: Boolean,
    showHeader: Boolean = true,
    compact: Boolean = false,
    positionAt: Long? = null,
) {
    val context = LocalContext.current
    Column(modifier = GlanceModifier.fillMaxSize()) {
        if (showHeader) {
            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Label(
                    board?.stop?.name?.plainStopName() ?: context.getString(R.string.widget_departures_name),
                    colors.ink,
                    size = 14.sp,
                    bold = true,
                    modifier = GlanceModifier.defaultWeight(),
                )
                // A tap on the time (with ↻) loads all widgets anew: the way to fresh data
                // without opening the app (and the only one at "Extrem", see WidgetUpdates).
                val stamp = REFRESH_MARK + " " + (positionNote(context, positionAt, now) ?: clockOf(now))
                val tappable = !LocalWidgetPreview.current
                Label(
                    stamp,
                    colors.muted,
                    size = 11.sp,
                    modifier = if (tappable) GlanceModifier.clickable(actionRunCallback<WidgetRefreshAction>()).padding(start = 6.dp) else GlanceModifier,
                )
            }
        }
        val shown = board?.departures
            ?.filter { (it.leaves ?: 0L) >= now - 30_000 }
            ?.take(rows)
            .orEmpty()
        if (board == null || shown.isEmpty()) {
            if (board == null) {
                ProblemText(problem, colors, maxLines = if (compact) 1 else 3)
            } else {
                Label(context.getString(R.string.widget_no_departures), colors.muted, size = 12.sp)
            }
            return@Column
        }
        // Glance draws at most ten children in one column: the rows carry their gap themselves.
        shown.forEach { departure ->
            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(top = if (compact) 0.dp else 4.dp).height(if (compact) 24.dp else 28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = GlanceModifier.width(44.dp)) { LineBadge(departure.line, departure.product, color = departure.color) }
                Gap(width = 6.dp)
                Label(
                    departure.direction.plainStopName(),
                    colors.ink,
                    modifier = GlanceModifier.defaultWeight(),
                    strike = departure.cancelled,
                )
                if (departure.cancelled) {
                    Label(context.getString(R.string.cancelled_tag), colors.delay, size = 12.sp, bold = true)
                } else {
                    // As the delay setting says (see DelayDisplay): "+2" or not, expected or timetable time.
                    val display = AppPrefs(context).delayDisplay
                    val struck = display == DelayDisplay.StruckPlanned
                    val planned = departure.plannedTime
                    if (struck && departure.delayMinutes > 0 && planned != null) {
                        // Takt style: the timetable time struck through, the new one beside it (below, when wide).
                        Label(clockOf(planned), colors.muted, size = 11.sp, strike = true)
                        Gap(width = 4.dp)
                        if (!wide) departure.leaves?.let {
                            Label(clockOf(it), colors.delay, size = 11.sp, bold = true)
                            Gap(width = 6.dp)
                        }
                    } else if (departure.delayMinutes > 0 && display != DelayDisplay.RealtimeOnly && !struck) {
                        Label("+${departure.delayMinutes}", colors.delay, size = 12.sp, bold = true)
                        Gap(width = 6.dp)
                    }
                    if (wide) {
                        val shownTime = if (display == DelayDisplay.PlannedAndDelay) departure.plannedTime ?: departure.leaves else departure.leaves
                        val lateAlone = departure.delayMinutes > 0 && (display == DelayDisplay.RealtimeOnly || struck)
                        shownTime?.let { Label(clockOf(it), if (lateAlone) colors.delay else colors.muted, size = 11.sp) }
                        Gap(width = 6.dp)
                    }
                    Label(departure.leaves?.let { minutesText(context, it, now) } ?: "–", colors.ink, size = 13.sp, bold = true)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------
// Connection to a favourite place

/**
 * The next connection to a favourite place: "Home", the minutes until it leaves, departure
 * and arrival, the badges of its lines and a lime "Start" that starts the trip companion.
 * Compact it is only the minutes and the lines; tall it also names where it goes from and to.
 * A start at an old position of the user says how old it is next to the title.
 */
@Composable
internal fun ConnectionBody(snapshot: WidgetSnapshot?, colors: WidgetColors, now: Long, layout: WidgetLayout = WidgetLayout()) {
    val context = LocalContext.current
    val connection = snapshot?.connection
    if (layout.compact) {
        Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            if (connection == null) {
                Label(favoriteTitle(context, snapshot?.label, snapshot?.icon), colors.muted, size = 12.sp, bold = true)
                Gap(width = 6.dp)
                ProblemText(snapshot?.problem, colors, maxLines = 1)
                return@Row
            }
            Label(minutesText(context, connection.departure, now), colors.accent, size = 18.sp, bold = true)
            connection.rides.take(2).forEach { ride ->
                Gap(width = 4.dp)
                LineBadge(ride.line, ride.product, height = 18.dp, size = 11.sp, color = ride.color)
            }
        }
        return
    }
    Column(modifier = GlanceModifier.fillMaxSize()) {
        val note = if (connection == null) null else positionNote(context, snapshot?.positionAt, now)
        if (note == null) {
            Label(favoriteTitle(context, snapshot?.label, snapshot?.icon), colors.muted, size = 12.sp, bold = true)
        } else {
            // An old position shares the title line, so the "Start" below keeps its room.
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Label(
                    favoriteTitle(context, snapshot?.label, snapshot?.icon),
                    colors.muted,
                    size = 12.sp,
                    bold = true,
                    modifier = GlanceModifier.defaultWeight(),
                )
                Gap(width = 4.dp)
                Label(note, colors.muted, size = 10.sp)
            }
        }
        if (connection == null) {
            Gap(height = 6.dp)
            ProblemText(snapshot?.problem, colors)
            return@Column
        }
        Label(minutesText(context, connection.departure, now), colors.accent, size = if (layout.tall) 32.sp else 26.sp, bold = true)
        Row(verticalAlignment = Alignment.CenterVertically) {
            val display = AppPrefs(context).delayDisplay
            val delay = (connection.departure - connection.plannedDeparture) / 60_000
            // The timetable time with "+2", or the expected time (red when late and alone), as the delay setting says.
            val start = if (display == DelayDisplay.PlannedAndDelay) connection.plannedDeparture else connection.departure
            val struck = display == DelayDisplay.StruckPlanned
            val late = delay > 0 && (display == DelayDisplay.RealtimeOnly || struck)
            // Takt style: the timetable time struck through before the new one.
            if (struck && delay > 0) {
                Label(clockOf(connection.plannedDeparture), colors.muted, size = 12.sp, strike = true)
                Gap(width = 4.dp)
            }
            Label("${clockOf(start)} – ${clockOf(connection.arrival)}", if (late) colors.delay else colors.ink, size = 13.sp)
            if (delay > 0 && display != DelayDisplay.RealtimeOnly && !struck) {
                Gap(width = 4.dp)
                Label("+$delay", colors.delay, size = 12.sp, bold = true)
            }
        }
        if (layout.tall) {
            Gap(height = 2.dp)
            Label(
                "${connection.from.name.plainStopName()} → ${connection.to.name.plainStopName()}",
                colors.muted,
                size = 12.sp,
                maxLines = 2,
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
        }
        Gap(height = 6.dp)
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(modifier = GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                connection.rides.take(3).forEachIndexed { index, ride ->
                    if (index > 0) Gap(width = 3.dp)
                    LineBadge(ride.line, ride.product, height = 18.dp, size = 11.sp, color = ride.color)
                }
            }
            StartButton(connection, colors)
        }
    }
}

/**
 * The lime "Start" that follows [connection] in the trip companion (see [startTripAction]);
 * [small] for a caption row.
 */
@Composable
internal fun StartButton(connection: WidgetConnection, colors: WidgetColors, small: Boolean = false) {
    val context = LocalContext.current
    val button = GlanceModifier
        .background(colors.lime)
        .cornerRadius(16.dp)
        .padding(horizontal = if (small) 10.dp else 12.dp, vertical = if (small) 3.dp else 6.dp)
    Box(
        modifier = if (LocalWidgetPreview.current) button else button.clickable(startTripAction(context, connection)),
        contentAlignment = Alignment.Center,
    ) {
        Label(context.getString(R.string.widget_start), colors.onLime, size = if (small) 12.sp else 13.sp, bold = true)
    }
}

// ---------------------------------------------------------------------------------------
// The followed trip

/**
 * The next step of the followed trip: the minutes until it, what to do ("Take U7") with the
 * badge, where (stop and platform), where to board as a small drawn train, and a bar of how
 * far the trip has come. Compact it is the minutes and the step in one line; the hint of
 * where to board needs a roomy widget.
 */
@Composable
internal fun TripBody(journey: Journey, colors: WidgetColors, now: OffsetDateTime, layout: WidgetLayout = WidgetLayout(roomy = true)) {
    val context = LocalContext.current
    val step = tripStep(journey, now)
    val leg = step.leg
    val minutes = Math.floorDiv(java.time.Duration.between(now, step.time).seconds, 60L).coerceAtLeast(0)
    val minutesLabel = when {
        step.kind == StepKind.Arrived -> context.getString(R.string.arrived)
        minutes < 1 -> context.getString(R.string.trip_now)
        else -> context.getString(R.string.trip_unit_minutes, minutes)
    }
    if (layout.compact) {
        Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Label(minutesLabel, colors.accent, size = 18.sp, bold = true)
            if (step.kind != StepKind.Arrived) {
                Gap(width = 6.dp)
                if (leg != null && step.kind == StepKind.Board) {
                    LineBadge(leg.line, leg.product, height = 18.dp, size = 11.sp, color = lineColor(leg).toArgb())
                    Gap(width = 4.dp)
                }
                Label(stepText(context, step, leg?.line), colors.ink, size = 13.sp, bold = true, modifier = GlanceModifier.defaultWeight())
            }
        }
        return
    }
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Label(
                minutesLabel,
                colors.accent,
                size = 24.sp,
                bold = true,
                modifier = GlanceModifier.defaultWeight(),
            )
            val display = AppPrefs(context).delayDisplay
            val planned = if (display == DelayDisplay.StruckPlanned) stepPlannedTime(step) else null
            val delay = if (display == DelayDisplay.RealtimeOnly || display == DelayDisplay.StruckPlanned) 0L else stepDelay(step)
            if (planned != null) {
                // Takt style: the timetable time struck through, the new one in the colour of delay.
                Label(planned.clock(), colors.muted, size = 12.sp, strike = true)
                Gap(width = 4.dp)
                Label(step.time.clock(), colors.delay, size = 13.sp, bold = true)
            }
            if (delay > 0) Label("+$delay", colors.delay, size = 13.sp, bold = true)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leg != null && step.kind == StepKind.Board) {
                LineBadge(leg.line, leg.product, height = 18.dp, size = 11.sp, color = lineColor(leg).toArgb())
                Gap(width = 5.dp)
            }
            Label(stepText(context, step, leg?.line), colors.ink, size = 14.sp, bold = true)
        }
        Label(placeLine(context, step, journey), colors.muted, size = 12.sp)
        val hint = if (layout.roomy) boardingHint(context, step, journey) else null
        if (hint != null) {
            Gap(height = 4.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                val part = if (step.kind == StepKind.Board && leg != null && ChangePositions.loaded) {
                    rideAfter(journey, leg)?.let { next -> boardingPositionOf(leg, next) }
                        ?.takeIf { !it.samePlatform }?.part
                } else {
                    null
                }
                if (part != null && leg != null) {
                    TrainCapsule(lineColor(leg), part, if (leg.product == "suburban") 4 else 3)
                    Gap(width = 6.dp)
                }
                Label(hint, colors.ink, size = 11.sp, maxLines = 2)
            }
        }
        Spacer(modifier = GlanceModifier.defaultWeight())
        val total = (journey.arrival.toInstant().toEpochMilli() - journey.departure.toInstant().toEpochMilli())
            .coerceAtLeast(1L)
        val done = (now.toInstant().toEpochMilli() - journey.departure.toInstant().toEpochMilli()).coerceIn(0L, total)
        LinearProgressIndicator(
            progress = done.toFloat() / total,
            modifier = GlanceModifier.fillMaxWidth().height(6.dp).cornerRadius(3.dp),
            color = colors.accent,
            backgroundColor = colors.chip,
        )
    }
}

/** "Take U7", "Get off", "Walk", "Arrived": the step in a few words. */
private fun stepText(context: Context, step: TripStep, line: String?): String = when (step.kind) {
    // Before the walk to the first ride: when to leave ("Leave 05:07"), not the ride.
    StepKind.Board -> if (step.leave) {
        context.getString(R.string.widget_leave_at, clockOf(step.time.toInstant().toEpochMilli()))
    } else {
        context.getString(R.string.widget_take_line, shortLine(line) ?: "")
    }
    StepKind.GetOff -> context.getString(R.string.widget_get_off)
    StepKind.WalkToEnd -> context.getString(if (step.cycling) R.string.widget_cycle else R.string.widget_walk)
    StepKind.Arrived -> context.getString(R.string.arrived)
}

/**
 * The minimal train of the boarding hint, of plain boxes: [segments] cars, those of the
 * third at [part] (0 rear, 1 middle, 2 front) in the colour of the line, the front rounded.
 */
@Composable
internal fun TrainCapsule(line: Color, part: Int, segments: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        for (index in 0 until segments) {
            val middle = (index + 0.5f) / segments
            val picked = when (part) {
                0 -> middle < 1f / 3f
                2 -> middle > 2f / 3f
                else -> middle in (1f / 3f)..(2f / 3f)
            }
            if (index > 0) Gap(width = 2.dp)
            Box(
                modifier = GlanceModifier
                    .width(if (index == segments - 1) 12.dp else 10.dp)
                    .height(8.dp)
                    .background(ColorProvider(if (picked) line else line.copy(alpha = 0.22f)))
                    .cornerRadius(if (index == segments - 1) 4.dp else 2.dp),
            ) {}
        }
    }
}

// ---------------------------------------------------------------------------------------
// Mini

/**
 * One line at one stop: its badge and the minutes until it leaves. With [count] 2 (a widget
 * two cells wide) the next two departures side by side, each as a small stack of badge and
 * minutes, so neither is cut at the width of half a widget. Sized for the smallest cell (40 dp
 * square, 32 dp inside): a late departure shows its minutes in the colour of delay; only
 * [roomy] (64 dp high and more) gives bigger text and the delay a line of its own.
 */
@Composable
internal fun MiniBody(
    board: StopBoard?,
    problem: WidgetProblem?,
    colors: WidgetColors,
    now: Long,
    count: Int = 1,
    roomy: Boolean = false,
) {
    val upcoming = board?.departures?.filter { !it.cancelled && (it.leaves ?: 0L) >= now - 30_000 }.orEmpty()
    val next = upcoming.firstOrNull()
    if (next == null) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Label(if (board == null && problem != null) "!" else "–", colors.muted, size = 16.sp, bold = true)
        }
        return
    }
    if (count > 1 && upcoming.size > 1) {
        Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            upcoming.take(count).forEach { departure ->
                Box(modifier = GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) {
                    MiniDeparture(departure, colors, now, roomy)
                }
            }
        }
        return
    }
    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        MiniDeparture(next, colors, now, roomy)
    }
}

/** One departure of the mini widget: the badge, below it the minutes (in the colour of delay when late). */
@Composable
private fun MiniDeparture(departure: WidgetDeparture, colors: WidgetColors, now: Long, roomy: Boolean) {
    val context = LocalContext.current
    val late = departure.delayMinutes > 0
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
        LineBadge(
            departure.line,
            departure.product,
            height = if (roomy) 20.dp else 16.dp,
            size = if (roomy) 12.sp else 10.sp,
            color = departure.color,
        )
        Gap(height = if (roomy) 3.dp else 1.dp)
        Label(
            departure.leaves?.let { minutesText(context, it, now) } ?: "–",
            if (late && !roomy) colors.delay else colors.ink,
            size = if (roomy) 15.sp else 12.sp,
            bold = true,
        )
        val display = AppPrefs(context).delayDisplay
        val planned = departure.plannedTime
        if (late && roomy && display == DelayDisplay.StruckPlanned && planned != null) {
            // Takt style: the timetable time struck through (the minutes above are the new time).
            Label(clockOf(planned), colors.muted, size = 10.sp, strike = true)
        } else if (late && roomy && display != DelayDisplay.RealtimeOnly && display != DelayDisplay.StruckPlanned) {
            Label("+${departure.delayMinutes}", colors.delay, size = 10.sp, bold = true)
        }
    }
}

// ---------------------------------------------------------------------------------------
// Appointment

/**
 * The next appointment: its start and title, when to leave ("Leave 13:32"), and how far that
 * is with the lines: "in 12 min · U7 6 stops".
 */
@Composable
internal fun AppointmentBody(
    snapshot: WidgetSnapshot?,
    colors: WidgetColors,
    now: Long,
    showTitle: Boolean = true,
    layout: WidgetLayout = WidgetLayout(),
) {
    val context = LocalContext.current
    val appointment = snapshot?.appointment
    if (layout.compact) {
        // One line: when to leave (or when it starts) and what.
        Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            if (appointment == null) {
                ProblemText(snapshot?.problem, colors, maxLines = 1)
                return@Row
            }
            val connection = appointment.connection
            if (connection != null) {
                Label(context.getString(R.string.widget_leave_at, clockOf(connection.departure)), colors.accent, size = 14.sp, bold = true)
            } else {
                Label(clockOf(appointment.begin), colors.muted, size = 13.sp, bold = true)
            }
            Gap(width = 6.dp)
            Label(appointment.title.ifBlank { appointment.location }, colors.ink, size = 13.sp, modifier = GlanceModifier.defaultWeight())
        }
        return
    }
    Column(modifier = GlanceModifier.fillMaxSize()) {
        if (showTitle) {
            Label(
                if (appointment == null) {
                    context.getString(R.string.appointment_title)
                } else {
                    context.getString(R.string.widget_appointment_at, clockOf(appointment.begin))
                },
                colors.muted,
                size = 12.sp,
                bold = true,
            )
        }
        if (appointment == null) {
            Gap(height = 6.dp)
            ProblemText(snapshot?.problem, colors)
            return@Column
        }
        Label(appointment.title.ifBlank { appointment.location }, colors.ink, size = 15.sp, bold = true, maxLines = 2)
        Gap(height = 4.dp)
        val connection = appointment.connection
        if (connection == null) {
            Label(appointment.location, colors.muted, size = 12.sp, maxLines = 2)
            return@Column
        }
        Label(context.getString(R.string.widget_leave_at, clockOf(connection.departure)), colors.accent, size = 15.sp, bold = true)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label(minutesUntil(context, connection.departure, now), colors.muted, size = 12.sp)
            val ride = connection.rides.firstOrNull()
            if (ride != null) {
                Label(" · ", colors.muted, size = 12.sp)
                LineBadge(ride.line, ride.product, height = 16.dp, size = 10.sp, color = ride.color)
                if (ride.stops > 0) {
                    Gap(width = 4.dp)
                    Label(
                        context.resources.getQuantityString(R.plurals.widget_stops, ride.stops, ride.stops),
                        colors.muted,
                        size = 12.sp,
                    )
                } else if (connection.rides.size > 1) {
                    Gap(width = 3.dp)
                    Label("+${connection.rides.size - 1}", colors.muted, size = 12.sp)
                }
            }
        }
    }
}

/** "in 12 min", "now", or the clock time when it is far. */
private fun minutesUntil(context: Context, time: Long, now: Long): String {
    val minutes = Math.floorDiv(time - now, 60_000L)
    return when {
        minutes < 1 -> context.getString(R.string.trip_now)
        minutes < 100 -> context.getString(R.string.in_minutes_text, minutes)
        else -> clockOf(time)
    }
}

// ---------------------------------------------------------------------------------------
// Automatic

/** The small caption of the automatic widget that says why it shows what it shows. */
@Composable
internal fun ReasonCaption(reason: AutoReason, colors: WidgetColors) {
    val context = LocalContext.current
    val text = context.getString(
        when (reason) {
            AutoReason.Trip -> R.string.widget_reason_trip
            AutoReason.Appointment -> R.string.widget_reason_appointment
            AutoReason.ToWork -> R.string.widget_reason_to_work
            AutoReason.ToHome -> R.string.widget_reason_to_home
            AutoReason.Nearby -> R.string.widget_reason_nearby
        },
    )
    Box(
        modifier = GlanceModifier.background(colors.lime).cornerRadius(8.dp).padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Label(text, colors.onLime, size = 10.sp, bold = true)
    }
}

/** True while the app knows the dataset of where to board; the trip widget loads it once. */
internal suspend fun loadBoardingData(context: Context) {
    if (!ChangePositions.loaded) ChangePositions.load(context)
}
