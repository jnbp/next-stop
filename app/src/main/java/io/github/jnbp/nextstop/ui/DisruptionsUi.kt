package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.ui.theme.AppColors
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import io.github.jnbp.nextstop.ui.theme.AppFont
import androidx.compose.foundation.layout.heightIn
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.CompositionLocalProvider
import io.github.jnbp.nextstop.data.WaysPill
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.CancellationException
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.AppLanguage
import io.github.jnbp.nextstop.data.BVG_DISRUPTIONS_URL
import io.github.jnbp.nextstop.data.colourPlace
import io.github.jnbp.nextstop.data.NoticeLinkKind
import io.github.jnbp.nextstop.data.Disruption
import io.github.jnbp.nextstop.data.DisruptionGroup
import io.github.jnbp.nextstop.data.PlaceDisruptions
import io.github.jnbp.nextstop.data.Stop
import io.github.jnbp.nextstop.data.RouteStatus
import io.github.jnbp.nextstop.data.SavedRoute
import io.github.jnbp.nextstop.data.groupDisruptions
import io.github.jnbp.nextstop.data.shortStopName

/** A page opened over the first page of "My routes"; the tab keeps a stack of them (see AppRoot). */
sealed interface FavoritesPage {
    /** All current disruptions. */
    object AllDisruptions : FavoritesPage

    /** The disruptions around one place, [key] as [placeDisruptionKey] gives it, [title] its name. */
    data class PlaceDisruptionsPage(val key: String, val title: String) : FavoritesPage
}

/**
 * What the favourites need to look for disruptions: the status of one route, the overview,
 * and the notices around a place (see placeDisruptions).
 */
class DisruptionAccess(
    val checkRoute: suspend (SavedRoute) -> RouteStatus,
    val loadAll: suspend () -> List<Disruption>,
    val checkPlace: (suspend (Stop) -> PlaceDisruptions)? = null,
)

/** Tells the places of the cache apart: the place and where it lies. Null for a place without a position. */
fun placeDisruptionKey(place: Stop): String? = place.position?.let { (latitude, longitude) ->
    place.id + "@" + String.format(Locale.ROOT, "%.5f,%.5f", latitude, longitude)
}

/**
 * The notices around the places of "My routes", by [placeDisruptionKey]: kept while the app
 * runs, so the cards show them at once and a place is asked again only when its notices are
 * older than [STATUS_MAX_AGE_MILLIS], as the status of a saved route is.
 */
object PlaceDisruptionCache {
    private class Entry(val found: PlaceDisruptions, val checkedMillis: Long)

    private val entries = HashMap<String, Entry>()

    fun get(key: String): PlaceDisruptions? = synchronized(entries) { entries[key]?.found }

    /** What is known of the place in [radiusMeters]; null when it was last looked at in another radius. */
    fun get(key: String, radiusMeters: Int): PlaceDisruptions? = get(key)?.takeIf { it.radiusMeters == radiusMeters }

    /** Whether the place was looked at in [radiusMeters] lately: a new radius is asked again at once. */
    internal fun fresh(key: String, radiusMeters: Int): Boolean = synchronized(entries) {
        val entry = entries[key] ?: return@synchronized false
        entry.found.radiusMeters == radiusMeters && System.currentTimeMillis() - entry.checkedMillis < STATUS_MAX_AGE_MILLIS
    }

    /** Everything known around the places, for the notices on the maps (see MapNoticePool). */
    fun all(): List<PlaceDisruptions> = synchronized(entries) { entries.values.map { it.found } }

    internal fun put(key: String, found: PlaceDisruptions) {
        synchronized(entries) { entries[key] = Entry(found, System.currentTimeMillis()) }
    }
}

/**
 * Keeps the notices around [places] fresh while the favourites are open, one place after the
 * other, as [rememberRouteStatusVersion] does for the routes: a number that changes with
 * each new answer, for the cards to read the cache again, and whether the round is over.
 */
@Composable
fun rememberPlaceDisruptionsVersion(places: List<Stop>, access: DisruptionAccess?, radiusMeters: Int): CheckRun {
    var version by remember { mutableIntStateOf(0) }
    var done by remember { mutableStateOf(false) }
    val keyed = places.mapNotNull { place -> placeDisruptionKey(place)?.let { it to place } }.distinctBy { it.first }
    LaunchedEffect(keyed.map { it.first }, access, radiusMeters) {
        done = false
        val check = access?.checkPlace
        if (check == null) {
            done = true
            return@LaunchedEffect
        }
        for ((key, place) in keyed.take(MAX_CHECKED_PLACES)) {
            if (PlaceDisruptionCache.fresh(key, radiusMeters)) continue
            try {
                PlaceDisruptionCache.put(key, check(place))
                version++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Nothing this time: the card keeps what it showed.
            }
        }
        done = true
    }
    return CheckRun(version, done)
}

/** A round of checks while "My routes" is open: [version] changes with each answer, [done] once all were asked. */
data class CheckRun(val version: Int, val done: Boolean)

/**
 * The one line on a card of "My routes" (a saved connection, route or place) that a notice
 * touches: the badge of the first notice's line in its own colour (as in the list of all
 * disruptions), its headline, and "+n" for the others. [cancelled] adds a cancelled journey of
 * the route, as the only entry or one of the others. Nothing for a card without either. A
 * tap opens the card's notices (see WaysDisruptionsSheet).
 */
@Composable
fun CardDisruptionLine(
    items: List<Disruption>,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    cancelled: Boolean = false,
    /** The radius the notices of a place were found in: two or more are then counted with it. */
    radiusMeters: Int? = null,
) {
    val shown = items.isNotEmpty() || cancelled
    // What was shown last stays while the line goes, so it fades out with its text.
    val held = remember { arrayOfNulls<Pair<List<Disruption>, Boolean>>(1) }
    if (shown) held[0] = items to cancelled
    val last = held[0]
    // The line comes and goes softly, as the checks of "My routes" find notices or none; one
    // there from the start is simply there.
    SoftAppear(visible = shown, modifier = modifier) {
        if (last != null) DisruptionLineBody(last.first, onClick, last.second, radiusMeters)
    }
}

@Composable
private fun DisruptionLineBody(items: List<Disruption>, onClick: (() -> Unit)?, cancelled: Boolean, radiusMeters: Int?) {
    val first = items.firstOrNull()
    if (first == null && !cancelled) return
    // Several notices around a place: how many, and how far around, rather than the first.
    val counted = radiusMeters != null && !cancelled && items.size >= 2
    val more = if (counted) 0 else items.size - 1 + if (cancelled && first != null) 1 else 0
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Quiet on purpose: a small warning mark and one muted line, no badge and no frame, so
        // the cards stay about their way; the details are one tap away.
        val badge = first?.badges?.firstOrNull()?.takeIf { !counted }
        WarningMark(size = 12.dp)
        val around = if (radiusMeters != null) radiusLabel(radiusMeters) else ""
        val headline = first?.summary?.ifEmpty { first.text }.orEmpty()
        val text = when {
            first == null -> stringResource(R.string.disruption_cancelled)
            counted -> pluralStringResource(R.plurals.disruptions_within, items.size, items.size, around)
            badge != null -> (shortLine(badge.line) ?: badge.line ?: "").let { if (it.isEmpty()) headline else "$it: $headline" }
            // A station's notice without a line names its station.
            first.station && first.stops.isNotEmpty() -> first.stops.first().name.shortStopName() + ": " + headline
            else -> headline
        }
        // A new notice in place of the last one cross-fades rather than switching.
        FadeSwap(target = text, modifier = Modifier.weight(1f, fill = false)) { shownText ->
            Text(
                text = shownText,
                color = AppColors.Muted,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (more > 0) {
            Text(text = "+$more", color = AppColors.Muted, fontWeight = FontWeight.Normal, fontSize = 12.sp, maxLines = 1)
        }
        // A tap opens the notices on a sheet: a small arrow says so.
        if (onClick != null) OpensChevron()
    }
}

/**
 * The notices of a departures board as one quiet line under its filters: one notice as a card
 * says it (see CardDisruptionLine), more as "2 notices here". A tap shows them all.
 */
@Composable
fun BoardDisruptionLine(items: List<Disruption>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (items.size < 2) {
        CardDisruptionLine(items = items, onClick = onClick, modifier = modifier)
        return
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        WarningMark(size = 12.dp)
        Text(
            text = pluralStringResource(R.plurals.disruptions_here, items.size, items.size),
            color = AppColors.Muted,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        OpensChevron()
    }
}

/** The small arrow at the end of a quiet line that opens a sheet: pointing right, muted. */
@Composable
private fun OpensChevron() {
    Chevron(open = false, modifier = Modifier.size(12.dp).rotate(-90f))
}

/** A radius as the settings and the cards say it: "500 m", "2 km". */
@Composable
fun radiusLabel(meters: Int): String =
    if (meters >= 1000 && meters % 1000 == 0) stringResource(R.string.radius_km, meters / 1000) else stringResource(R.string.metres, meters)

/** The frame of a card of "My routes" that a notice touches: a thin line in the colour of warnings. */
fun Modifier.disruptionFrame(on: Boolean, shape: androidx.compose.ui.graphics.Shape): Modifier =
    // No frame any more: the line on the card is enough (a frame on every touched card was too loud).
    this

/**
 * Keeps the status lines of [routes] fresh while the favourites are open: a route whose last
 * check is older than [STATUS_MAX_AGE_MILLIS] is asked again, one after the other. Returns a
 * number that changes with each new status, for the rows to read it again, and whether the
 * round is over (see [CheckRun]).
 */
@Composable
fun rememberRouteStatusVersion(routes: List<SavedRoute>, access: DisruptionAccess?, radiusMeters: Int): CheckRun {
    val context = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(routes.map { it.id }, access, radiusMeters) {
        done = false
        if (access == null) {
            done = true
            return@LaunchedEffect
        }
        for (route in routes) {
            val known = DisruptionWatch.statusOf(context, route.id)
            // A status of another radius (or of none) is asked again at once.
            if (known != null && known.radiusMeters == radiusMeters &&
                System.currentTimeMillis() - known.checkedMillis < STATUS_MAX_AGE_MILLIS
            ) {
                continue
            }
            try {
                DisruptionWatch.saveStatus(context, access.checkRoute(route))
                version++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // No status this time: the line keeps the last one.
            }
        }
        done = true
    }
    return CheckRun(version, done)
}

/**
 * Asks for the permission to notify, where Android wants that (13 and later) and it is not
 * given yet; for switching on a watch of disruptions.
 */
@Composable
fun rememberNotificationAsk(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/**
 * The bell of a saved route: on, its new disruptions are reported ("Störungen melden"). Asks
 * for the permission to notify the first time it is switched on, where Android wants that,
 * and explains once what the bell does (see [BellExplainSheet]); [onSettings] leads from there
 * to the settings of the watch.
 */
@Composable
fun DisruptionBell(route: SavedRoute, onChange: () -> Unit = {}, onSettings: (() -> Unit)? = null) =
    DisruptionBell(watchKey = route.id, onChange = onChange, onSettings = onSettings)

/**
 * The bell of anything that can be watched, by its key in the watched set: a saved route
 * (its id), a place or a favourite stop (see placeWatchKey). One logic for all: "tell me
 * about problems"; off until switched on. [size] is the size of the round button.
 */
@Composable
fun DisruptionBell(
    watchKey: String,
    onChange: () -> Unit = {},
    onSettings: (() -> Unit)? = null,
    size: androidx.compose.ui.unit.Dp = 48.dp,
) {
    val context = LocalContext.current
    var on by remember(watchKey) { mutableStateOf(watchKey in DisruptionWatch.watched(context)) }
    var explain by remember { mutableStateOf(false) }
    val askNotify = rememberNotificationAsk()
    val description = stringResource(R.string.disruption_bell_on)
    Box(
        modifier = Modifier
            .semantics { contentDescription = description }
            .clip(CircleShape)
            .background(if (on) AppColors.Blue else AppColors.Card)
            .border(1.dp, glassRim(), CircleShape)
            .toggleable(value = on, role = Role.Switch) { value ->
                on = value
                DisruptionWatch.setWatched(context, watchKey, value)
                if (value) {
                    askNotify()
                    if (!DisruptionWatch.bellExplained(context)) {
                        DisruptionWatch.setBellExplained(context)
                        explain = true
                    }
                }
                onChange()
            }
            .size(size),
        contentAlignment = Alignment.Center,
    ) {
        BellIcon(color = if (on) AppColors.OnBlue else AppColors.Ink, filled = on)
    }
    if (explain) {
        BellExplainSheet(
            onSettings = onSettings?.let { open ->
                {
                    explain = false
                    open()
                }
            },
            onDismiss = { explain = false },
        )
    }
}

/**
 * What a bell does, the first time one is switched on: a notification when a new disruption
 * appears on the route, when it is checked (every n minutes, at the set times), each notice
 * once. "Set times" leads to the settings of the watch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BellExplainSheet(onSettings: (() -> Unit)?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    // No check every n minutes any more (battery rule): only on opening the app and at the set times.
    val period = 0
    val times = remember { DisruptionWatch.checkTimes(context) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.PlainGround,
        contentColor = AppColors.Ink,
    ) {
        CompositionLocalProvider(LocalBackdrop provides null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BellIcon(color = AppColors.Blue, filled = true)
                    Text(text = stringResource(R.string.bell_sheet_title), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                }
                Text(text = stringResource(R.string.bell_sheet_text), fontSize = 15.sp, lineHeight = 20.sp)
                Text(
                    text = watchWhenText(context, period, times),
                    color = AppColors.Muted,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    if (onSettings != null) {
                        SheetButton(text = stringResource(R.string.bell_sheet_settings), filled = false, onClick = onSettings)
                    }
                    SheetButton(text = stringResource(R.string.bell_sheet_ok), filled = true, onClick = onDismiss)
                }
            }
        }
    }
}

@Composable
private fun SheetButton(text: String, filled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (filled) AppColors.Blue else AppColors.Chip)
            .clickable(role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = 44.dp)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (filled) AppColors.OnBlue else AppColors.Blue,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            maxLines = 1,
        )
    }
}

/** When the watch looks: "Checked every 30 minutes and at 7:00, 18:00.", or that no check is set. */
internal fun watchWhenText(context: android.content.Context, period: Int, times: List<Int>): String {
    val clock = times.joinToString(", ") { clockLabel(it) }
    return when {
        period > 0 && times.isNotEmpty() -> context.getString(R.string.dwatch_when_both, period, clock)
        period > 0 -> context.getString(R.string.dwatch_when_period, period)
        times.isNotEmpty() -> context.getString(R.string.dwatch_when_times, clock)
        else -> context.getString(R.string.dwatch_when_none)
    }
}

/** A bell, drawn: filled while on, an outline while off. */
@Composable
private fun BellIcon(color: androidx.compose.ui.graphics.Color, filled: Boolean) {
    Canvas(modifier = Modifier.size(22.dp)) {
        val w = size.width
        val bell = Path().apply {
            moveTo(w * 0.5f, w * 0.14f)
            cubicTo(w * 0.28f, w * 0.14f, w * 0.24f, w * 0.34f, w * 0.24f, w * 0.5f)
            lineTo(w * 0.24f, w * 0.66f)
            lineTo(w * 0.14f, w * 0.78f)
            lineTo(w * 0.86f, w * 0.78f)
            lineTo(w * 0.76f, w * 0.66f)
            lineTo(w * 0.76f, w * 0.5f)
            cubicTo(w * 0.76f, w * 0.34f, w * 0.72f, w * 0.14f, w * 0.5f, w * 0.14f)
            close()
        }
        if (filled) drawPath(bell, color) else drawPath(bell, color, style = Stroke(width = w * 0.09f))
        drawArc(
            color = color,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(w * 0.4f, w * 0.76f),
            size = Size(w * 0.2f, w * 0.2f),
        )
    }
}

/**
 * The pill beside the title of "My routes": how many notices touch the saved connections,
 * routes and places, in the colours of a warning; quietly "All running" when none does. A
 * tap opens them (see [WaysDisruptionsSheet]). While nothing is known yet it keeps its room
 * empty, so the title does not jump; it comes and changes softly, unless animations are off.
 */
@Composable
fun WaysStatusPill(pill: WaysPill, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val animate = rememberAnimationsOn()
    Box(modifier = modifier.height(PILL_ROOM), contentAlignment = Alignment.CenterEnd) {
        if (animate) {
            AnimatedContent(
                targetState = pill,
                transitionSpec = {
                    (fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.85f)) togetherWith
                        fadeOut(tween(120)) using SizeTransform(clip = false)
                },
                contentAlignment = Alignment.CenterEnd,
                label = "waysPill",
            ) { shown -> WaysPillBody(shown, onClick) }
        } else {
            WaysPillBody(pill, onClick)
        }
    }
}

@Composable
private fun WaysPillBody(pill: WaysPill, onClick: () -> Unit) {
    if (pill.loading) return
    val context = LocalContext.current
    val troubled = pill.count > 0
    val text = if (troubled) {
        context.resources.getQuantityString(R.plurals.ways_pill_disruptions, pill.count, pill.count)
    } else {
        stringResource(R.string.ways_pill_clear)
    }
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (troubled) AppColors.WarningContainer else AppColors.Chip)
            .then(if (troubled) Modifier.border(1.dp, AppColors.Warning, CircleShape) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (troubled) {
            WarningMark(size = 15.dp)
        } else {
            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(AppColors.Early))
        }
        Text(
            text = text,
            color = if (troubled) AppColors.Ink else AppColors.Muted,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            maxLines = 1,
        )
    }
}

/**
 * The notices of "My routes" as a sheet: [title] on top, the cancelled journeys of [cancelled]
 * routes (a tap opens the route), then [items] as in the list of all disruptions (a tap
 * unfolds one with its details and links), each once, and at the end the way to all current
 * disruptions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaysDisruptionsSheet(
    title: String,
    items: List<Disruption>,
    cancelled: List<SavedRoute>,
    onOpenRoute: (SavedRoute) -> Unit,
    // The way to all current disruptions at the end; null leaves it out.
    onAll: (() -> Unit)?,
    onShowOnMap: ((Stop) -> Unit)?,
    onDismiss: () -> Unit,
    /** A line under the title: where the notices were looked for (the radius of a place or route). */
    subtitle: String? = null,
    /** Hides a notice from the maps (the sheet of a marker on the map); null where it cannot be. */
    onHide: ((Disruption) -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.PlainGround,
        contentColor = AppColors.Ink,
    ) {
        // A sheet is a window of its own: the picture of the map does not line up behind it.
        CompositionLocalProvider(LocalBackdrop provides null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp, bottom = if (subtitle == null) 10.dp else 2.dp),
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = AppColors.Muted,
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(start = 8.dp, bottom = 10.dp),
                    )
                }
                val last = cancelled.size + items.size - 1
                if (last < 0) {
                    Text(
                        text = stringResource(R.string.ways_all_normal),
                        color = AppColors.Early,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                cancelled.forEachIndexed { index, route ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(groupShape(index, last))
                            .background(AppColors.Card)
                            .clickable {
                                onDismiss()
                                onOpenRoute(route)
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        WarningMark()
                        Text(
                            text = stringResource(R.string.route_pair, route.from.name.shortStopName(), route.to.name.shortStopName()) +
                                " · " + stringResource(R.string.disruption_cancelled),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                items.forEachIndexed { index, item ->
                    DisruptionRow(
                        item,
                        mine = false,
                        shape = groupShape(cancelled.size + index, last),
                        onShowOnMap = onShowOnMap,
                        onHide = onHide?.let { hide -> { hide(item) } },
                    )
                }
                if (onAll != null) {
                    Text(
                        text = stringResource(R.string.disruptions_all),
                        color = AppColors.Blue,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(AppColors.Card)
                            .clickable(onClick = onAll)
                            .defaultMinSize(minHeight = 52.dp)
                            .padding(horizontal = 16.dp, vertical = 15.dp),
                    )
                }
            }
        }
    }
}

/**
 * All current disruptions, grouped S-Bahn, U-Bahn, tram, bus and the rest; within a group those
 * on the user's lines first. A tap on a notice unfolds its full text.
 */
@Composable
fun AllDisruptionsPage(
    access: DisruptionAccess,
    onBack: () -> Unit,
    place: FavoritesPage.PlaceDisruptionsPage? = null,
    onShowOnMap: ((Stop) -> Unit)? = null,
    // The middles of the region (home, where the user is), and the watched ways: notices on
    // their lines or near their places count wherever they are (see inRegion, relevantDisruptions).
    // Without anchors there is no region switch.
    anchors: List<Pair<Double, Double>> = emptyList(),
    watchedLines: Set<String> = emptySet(),
    watchedSpots: List<Pair<Double, Double>> = emptyList(),
) {
    val context = LocalContext.current
    // The region or everything the network reports, and one kind of transport or all.
    var wide by rememberSaveable { mutableStateOf(false) }
    var kind by rememberSaveable { mutableStateOf<String?>(null) }
    // Back is AppRoot's: it goes back within the tab (see goBack there).
    var found by remember { mutableStateOf<List<Disruption>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    // Around a place: the notices the cache has for it (see rememberPlaceDisruptionsVersion), no new request.
    val around = place?.let { PlaceDisruptionCache.get(it.key) }
    LaunchedEffect(attempt, place) {
        failed = false
        found = null
        if (place != null) {
            found = around?.disruptions.orEmpty()
            return@LaunchedEffect
        }
        try {
            found = access.loadAll()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed = true
        }
    }
    val myLines = remember { DisruptionWatch.myLines(context) }
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .fadeEdges(scroll, horizontal = false)
            .verticalScroll(scroll)
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RoundButton(glyph = Glyph.Back, description = stringResource(R.string.back), onClick = onBack)
            Text(
                text = place?.let { stringResource(R.string.place_disruptions_title, it.title) }
                    ?: stringResource(R.string.disruptions_all),
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        val aroundStops = around?.stops.orEmpty()
        if (aroundStops.isNotEmpty()) {
            Text(
                text = stringResource(R.string.place_disruptions_stops, aroundStops.joinToString(", ") { it.name.shortStopName() }),
                color = AppColors.Muted,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        val all = found
        // The region and the kinds: worked out once per list and choice, not per frame.
        val regional = remember(all, wide, anchors, watchedLines, watchedSpots) {
            if (all == null || wide || place != null) {
                all
            } else {
                val always = relevantDisruptions(all, watchedLines, watchedSpots).mapTo(HashSet()) { it.id }
                all.filter { inRegion(it, anchors, always = always) }
            }
        }
        // The search field: a line ("U9", "RE1"), a station or words of the text; filters as typed.
        var query by rememberSaveable { mutableStateOf("") }
        val searchable = remember(regional) { regional?.let { indexDisruptions(it) } }
        val matching = remember(searchable, query) { searchable?.let { filterDisruptions(it, query) } }
        // The chips keep all kinds of the region; the list shows what fits the search.
        val allGroups = remember(regional, myLines) { groupDisruptions(regional.orEmpty(), myLines) }
        val groups = remember(matching, myLines) { groupDisruptions(matching.orEmpty(), myLines) }
        if (all != null && !failed) {
            DisruptionSearchField(value = query, onChange = { query = it })
        }
        if (place == null && all != null && !failed) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (anchors.isNotEmpty()) {
                    ToggleChip(text = stringResource(R.string.disruptions_region), on = !wide, onClick = { wide = false })
                    ToggleChip(text = stringResource(R.string.disruptions_everywhere), on = wide, onClick = { wide = true })
                    Spacer(modifier = Modifier.width(6.dp))
                }
                ToggleChip(text = stringResource(R.string.disruptions_kind_all), on = kind == null, onClick = { kind = null })
                allGroups.keys.forEach { group ->
                    ToggleChip(text = stringResource(groupLabel(group)), on = kind == group.name, onClick = { kind = group.name })
                }
            }
        }
        val list = if (kind == null) matching else groups.entries.firstOrNull { it.key.name == kind }?.value.orEmpty().takeIf { matching != null }
        // Loading, a note or the notices: a change between them cross-fades.
        val phase = when {
            failed -> PAGE_FAILED
            list == null -> PAGE_LOADING
            list.isEmpty() && query.isNotBlank() -> PAGE_NO_MATCH
            list.isEmpty() -> PAGE_NONE
            else -> PAGE_LIST
        }
        FadeSwap(target = phase, modifier = Modifier.fillMaxWidth()) { shownPhase ->
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (shownPhase) {
            PAGE_FAILED -> Text(
                text = stringResource(R.string.disruptions_failed),
                color = AppColors.Muted,
                modifier = Modifier
                    .clickable { attempt++ }
                    .padding(16.dp),
            )
            PAGE_LOADING -> Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                LoadingRow(text = stringResource(R.string.disruptions_loading))
            }
            PAGE_NO_MATCH -> Text(
                text = stringResource(R.string.r4_disruptions_search_none, query.trim()),
                color = AppColors.Muted,
                modifier = Modifier.padding(16.dp),
            )
            PAGE_NONE -> Text(
                text = stringResource(R.string.disruptions_none),
                color = AppColors.Muted,
                modifier = Modifier.padding(16.dp),
            )
            else -> (if (kind == null) groups else groups.filterKeys { it.name == kind }).forEach { (group, items) ->
                SectionLabel(stringResource(groupLabel(group)), modifier = Modifier.padding(top = 8.dp))
                GlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        items.forEachIndexed { index, item ->
                            DisruptionRow(
                                item,
                                mine = item.on(myLines),
                                shape = groupShape(index, items.lastIndex),
                                onShowOnMap = onShowOnMap,
                            )
                        }
                    }
                }
            }
        }
        }
        }
        Text(
            text = stringResource(if (place != null) R.string.place_disruptions_source else R.string.disruptions_source),
            color = AppColors.Muted,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        )
        Spacer(modifier = Modifier.height(LocalNavInset.current))
    }
}

@Composable
private fun DisruptionRow(
    item: Disruption,
    mine: Boolean,
    shape: androidx.compose.ui.graphics.Shape,
    onShowOnMap: ((Stop) -> Unit)?,
    onHide: (() -> Unit)? = null,
) {
    var open by remember(item.id) { mutableStateOf(false) }
    val animate = rememberAnimationsOn()
    val toggleLabel = stringResource(if (open) R.string.disruption_collapse else R.string.disruption_expand)
    // The first stop of the notice that has a place: "Map" under a closed card leads there.
    val located = item.stops.firstOrNull { it.position != null }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (mine) AppColors.BlueContainer else AppColors.Card)
            .clickable(onClickLabel = toggleLabel) { open = !open }
            .then(if (animate) Modifier.animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)) else Modifier),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // The first line leaves room for the chevron at the top right.
            val badges = item.badges
            val stationLine = item.station && item.stops.isNotEmpty()
            // The lines it is about, each in its own colour (tram 12 red, also where its
            // replacement buses carry the notice); a station's notice names the station instead.
            if (badges.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(end = CHEVRON_ROOM),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val place = item.colourPlace
                    badges.take(MAX_ROW_BADGES).forEach { badge ->
                        LineBadge(badge.line, badge.product, minWidth = 34.dp, at = place)
                    }
                    if (badges.size > MAX_ROW_BADGES) {
                        Text(text = "+${badges.size - MAX_ROW_BADGES}", color = AppColors.Muted, fontSize = 12.sp)
                    }
                }
            }
            if (stationLine) {
                Text(
                    text = item.stops.map { it.name.shortStopName() }.distinct().joinToString(" · "),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    lineHeight = 19.sp,
                    maxLines = if (open) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (badges.isEmpty()) Modifier.padding(end = CHEVRON_ROOM) else Modifier,
                )
            }
            // Open, its addresses, e-mail addresses and phone numbers can be tapped.
            val headline = item.summary.ifEmpty { item.text }
            Text(
                text = if (open) linkedText(headline, AppColors.Blue) else AnnotatedString(headline),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                maxLines = if (open) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = if (badges.isEmpty() && !stationLine) Modifier.padding(end = CHEVRON_ROOM) else Modifier,
            )
            // Closed, the start of its text too: two notices with the same headline ("Umleitung")
            // are told apart by what they say.
            if (item.summary.isNotEmpty() && item.text.isNotEmpty() && item.text != item.summary) {
                Text(
                    text = if (open) linkedText(item.text, AppColors.Blue) else AnnotatedString(item.text),
                    color = AppColors.Muted,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    maxLines = if (open) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Closed: quiet links that say the card opens, and the way to the map where it has a place.
            val showLocated = if (located != null && onShowOnMap != null) ({ onShowOnMap(located) }) else null
            if (animate) {
                AnimatedVisibility(visible = !open, enter = fadeIn(tween(200)), exit = fadeOut(tween(120))) {
                    DisruptionPeekLinks(onDetails = { open = true }, onMap = showLocated)
                }
                AnimatedVisibility(visible = open, enter = fadeIn(tween(220)), exit = fadeOut(tween(120))) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { DisruptionDetails(item, onShowOnMap) }
                }
            } else if (open) {
                DisruptionDetails(item, onShowOnMap)
            } else {
                DisruptionPeekLinks(onDetails = { open = true }, onMap = showLocated)
            }
            // On the sheet of a marker on the map: the notice off the maps (not off "My routes").
            if (onHide != null) {
                Text(
                    text = stringResource(R.string.map_notice_hide),
                    color = AppColors.Blue,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(AppColors.Chip)
                        .clickable(role = Role.Button, onClick = onHide)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
        // That the card opens: an arrow at its top right that turns over while it is open.
        Chevron(open = open, modifier = Modifier.align(Alignment.TopEnd).padding(top = 13.dp, end = 12.dp))
    }
}

/** Under a closed notice, quietly: "Details", and "Map" where [onMap] shows its place on the map. */
@Composable
private fun DisruptionPeekLinks(onDetails: () -> Unit, onMap: (() -> Unit)?) {
    // Back by the padding of the first link, so "Details" lines up with the text above.
    Row(modifier = Modifier.offset(x = (-6).dp), verticalAlignment = Alignment.CenterVertically) {
        PeekLink(text = stringResource(R.string.disruption_details), onClick = onDetails)
        if (onMap != null) {
            Text(text = "·", color = AppColors.Muted, fontSize = 12.sp)
            PeekLink(text = stringResource(R.string.disruption_link_map), onClick = onMap)
        }
    }
}

@Composable
private fun PeekLink(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        color = AppColors.Muted,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    )
}

/**
 * What an open notice adds below its text: from when until when it holds, where it was
 * reported, a small map of the place (a station's notice, or one found at a few stops only)
 * with "Show on the map", and its links.
 */
@Composable
private fun DisruptionDetails(item: Disruption, onShowOnMap: ((Stop) -> Unit)?) {
    val context = LocalContext.current
    validityText(context, item.validFrom, item.validUntil)?.let { validity ->
        Text(text = validity, color = AppColors.Ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 17.sp)
    }
    // A station's notice names its station above; a line's notice says where it was found.
    if (!item.station && item.stops.isNotEmpty()) {
        val names = item.stops.map { it.name.shortStopName() }.distinct()
        val shown = names.take(MAX_NAMED_STOPS).joinToString(", ") + if (names.size > MAX_NAMED_STOPS) ", …" else ""
        Text(
            text = stringResource(R.string.disruption_reported_at, shown),
            color = AppColors.Muted,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
    }
    // Only stops with a real place (see plausibleSpot): a stop without one draws no map.
    val placed = item.stops.filter { stop -> stop.position?.let(::plausibleSpot) == true }.distinctBy { it.position }
    // A line's notice found all over the city has no one place: no map for it.
    val mapped = if (item.station || placed.size <= MAX_MAPPED_STOPS) placed.take(MAX_MAPPED_STOPS) else emptyList()
    val first = mapped.firstOrNull()
    if (first != null) {
        val show: () -> Unit = { onShowOnMap?.invoke(first) }
        SpotsMiniMap(
            spots = mapped.mapNotNull { it.position },
            onClick = show,
            description = stringResource(R.string.disruption_minimap),
        )
        if (onShowOnMap != null) {
            Text(
                text = stringResource(R.string.show_on_map),
                color = AppColors.Blue,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = show)
                    .padding(horizontal = 4.dp, vertical = 6.dp),
            )
        }
    }
    DisruptionLinks(item, onShowOnMap, mapShown = first != null && onShowOnMap != null)
}

/** "Gültig 19.10.26, 04:30 – 30.11.26, 23:59" in the language of the app; null where neither end is known. */
private fun validityText(context: android.content.Context, from: OffsetDateTime?, until: OffsetDateTime?): String? {
    if (from == null && until == null) return null
    val language = AppLanguage.code
    val locale = if (Locale.getDefault().language == language) Locale.getDefault() else Locale.forLanguageTag(language)
    val format = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale)
    fun shown(time: OffsetDateTime): String = time.atZoneSameInstant(ZoneId.systemDefault()).format(format)
    return when {
        from != null && until != null -> context.getString(R.string.disruption_valid_range, shown(from), shown(until))
        from != null -> context.getString(R.string.disruption_valid_from, shown(from))
        else -> context.getString(R.string.disruption_valid_until, shown(until!!))
    }
}

/**
 * The links of an open notice: "More" (its page on bvg.de, else BVG's list of all
 * disruptions), "Map" (our map at the stop it was found at) and the provider's own map of a
 * diversion. They stand in for the placeholders "[MEHR/MORE]" and "[KARTE/MAPS]" of its text.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun DisruptionLinks(item: Disruption, onShowOnMap: ((Stop) -> Unit)?, mapShown: Boolean) {
    val context = LocalContext.current
    val more = item.links.firstOrNull { it.kind == NoticeLinkKind.More }
    val bvgMaps = item.links.filter { it.kind == NoticeLinkKind.Map && it.url != null }.mapNotNull { it.url }.distinct()
    val others = item.links.filter { it.kind == NoticeLinkKind.Other && it.url != null }
    val wantsMap = item.links.any { it.kind == NoticeLinkKind.Map }
    val place = item.stops.firstOrNull { it.position != null }
    val showPlace = if (place != null && onShowOnMap != null) ({ onShowOnMap(place) }) else null
    fun open(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            // No browser: the notice stays readable here.
        }
    }
    val chips = buildList<Pair<String, () -> Unit>> {
        if (more != null) {
            val url = more.url ?: BVG_DISRUPTIONS_URL
            add(context.getString(R.string.disruption_link_more) to { open(url) })
        }
        // Our map at the place, where the notice has a map or is about a place; else the provider's picture.
        if (showPlace != null && !mapShown && (wantsMap || item.stops.isNotEmpty())) {
            add(context.getString(R.string.disruption_link_map) to showPlace)
        }
        bvgMaps.forEach { url -> add(context.getString(R.string.disruption_link_bvg_map) to { open(url) }) }
        others.forEach { link ->
            val url = link.url ?: return@forEach
            add((link.label ?: context.getString(R.string.disruption_link_other)) to { open(url) })
        }
    }
    if (chips.isEmpty()) return
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        chips.forEach { (label, action) ->
            Text(
                text = label,
                color = AppColors.Blue,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppColors.Chip)
                    .clickable(onClick = action)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}

private fun groupLabel(group: DisruptionGroup): Int = when (group) {
    DisruptionGroup.Suburban -> R.string.product_suburban
    DisruptionGroup.Subway -> R.string.product_subway
    DisruptionGroup.Tram -> R.string.product_tram
    DisruptionGroup.Bus -> R.string.product_bus
    DisruptionGroup.Other -> R.string.disruptions_other
    DisruptionGroup.Stations -> R.string.disruptions_stations
}

// The room the pill beside the title of "My routes" keeps, also while it is empty.
private val PILL_ROOM = 34.dp

// A route's status line is checked again when it is older than this.
private const val STATUS_MAX_AGE_MILLIS = 10 * 60 * 1000L

// The places of "My routes" asked for their notices on one visit at most.
private const val MAX_CHECKED_PLACES = 12

// What the page of all disruptions shows (see AllDisruptionsPage).
private const val PAGE_FAILED = 0
private const val PAGE_LOADING = 1
private const val PAGE_NONE = 2
private const val PAGE_LIST = 3
private const val PAGE_NO_MATCH = 4

/** The search field at the top of "All disruptions": a glass of search, the query, and ✕ to clear it. */
@Composable
private fun DisruptionSearchField(value: String, onChange: (String) -> Unit) {
    val hint = stringResource(R.string.r4_disruptions_search_hint)
    BasicTextField(
        value = value,
        onValueChange = { onChange(it.take(SEARCH_MAX_LENGTH)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        textStyle = TextStyle(color = AppColors.Ink, fontSize = 15.sp, fontFamily = AppFont),
        cursorBrush = SolidColor(AppColors.Blue),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = hint },
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(AppColors.Chip)
                    .heightIn(min = 44.dp)
                    .padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GlyphIcon(Glyph.Search, color = AppColors.Muted, size = 18.dp)
                Box(modifier = Modifier.weight(1f).padding(vertical = 10.dp)) {
                    if (value.isEmpty()) Text(text = hint, color = AppColors.Muted, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    innerTextField()
                }
                FieldClearButton(visible = value.isNotEmpty(), onClear = { onChange("") })
            }
        },
    )
}

private const val SEARCH_MAX_LENGTH = 60

// The room the first line of a notice leaves for its chevron at the top right.
private val CHEVRON_ROOM = 22.dp

// Line badges on one notice before "+n".
private const val MAX_ROW_BADGES = 6

// Stops named under an open notice before "…".
private const val MAX_NAMED_STOPS = 4

// A notice found at more stops than this is shown without a map of them.
private const val MAX_MAPPED_STOPS = 3
