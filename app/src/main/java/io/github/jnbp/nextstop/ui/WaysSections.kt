package io.github.jnbp.nextstop.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.DiagLog
import io.github.jnbp.nextstop.data.Disruption
import io.github.jnbp.nextstop.data.colourPlace
import io.github.jnbp.nextstop.data.LineUse
import io.github.jnbp.nextstop.data.PlannedTrip
import io.github.jnbp.nextstop.data.TripRecord
import io.github.jnbp.nextstop.data.TripSummary
import io.github.jnbp.nextstop.data.averageDelayMinutes
import io.github.jnbp.nextstop.data.longestTrip
import io.github.jnbp.nextstop.data.mostUsedLines
import io.github.jnbp.nextstop.data.statTime
import io.github.jnbp.nextstop.data.tripsPerWeek
import io.github.jnbp.nextstop.ui.theme.AppColors
import java.io.File
import java.io.FileOutputStream
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * The sections of "My routes" (design "E2": one page, no tabs): the planned trips, the strip
 * of the user's own statistics, the disruptions on the user's ways, the favourite places as a
 * grid of four, the routes and connections in one list, and the statistics in detail. The
 * page itself (a lazy list) is FavoritesStartPage in OtherScreens.kt.
 */

/**
 * Which sections of the page came in already: a section scrolled back into the lazy list does
 * not come in again. A plain holder (no state), kept by the page.
 */
class WaysAppearance {
    internal val shown = HashSet<String>()
}

/**
 * Lets a section come in once: fading in and rising a little, [order] steps after the first
 * (staggered), with the spring the cards settle with; at once with animations off. Only the
 * layer changes while it moves (no recomposition per frame).
 */
fun Modifier.waysAppear(key: String, order: Int, seen: WaysAppearance): Modifier = composed {
    val animate = rememberAnimationsOn()
    val progress = remember { Animatable(if (!animate || key in seen.shown) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) {
            animationDelay(order.coerceAtMost(MAX_STAGGER) * STAGGER_MILLIS)
            progress.animateTo(1f, Motion.settle())
        }
        seen.shown += key
    }
    val rise = with(LocalDensity.current) { APPEAR_RISE.toPx() }
    Modifier.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * rise
    }
}

/** A warning mark that pops in with a small bounce when it first shows. */
@Composable
fun PoppingWarning(modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 15.dp) {
    val animate = rememberAnimationsOn()
    val pop = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) { if (pop.value < 1f) pop.animateTo(1f, Motion.pop()) }
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            }
            .size(size + 6.dp)
            .clip(CircleShape)
            .background(AppColors.WarningContainer),
        contentAlignment = Alignment.Center,
    ) {
        WarningMark(size = size - 2.dp)
    }
}

/** The current time, new every minute while the page is shown (nothing runs while it is not). */
@Composable
fun rememberMinuteClock(): OffsetDateTime {
    var now by remember { mutableStateOf(OffsetDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            val wait = 60_000L - System.currentTimeMillis() % 60_000L
            delay(wait + 50L)
            // In the background it waits for the app to come back (battery rule).
            AppVisibility.awaitVisible()
            now = OffsetDateTime.now()
        }
    }
    return now
}

// ---------------------------------------------------------------------------------------------
// a. Planned trips

/**
 * A planned trip as the blue card at the top: the day, where to and when to leave, its lines,
 * and how long until then. As on the start page, a tap opens the trip and the cross cancels it,
 * with "Undo" (see PlannedTripActions).
 */
@Composable
fun PlannedTripCard(trip: PlannedTrip, now: OffsetDateTime, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val days = ChronoUnit.DAYS.between(now.atZoneSameInstant(zone).toLocalDate(), trip.leaveAt.atZoneSameInstant(zone).toLocalDate())
    val day = when (days) {
        0L -> stringResource(R.string.ways_planned_today)
        1L -> stringResource(R.string.ways_planned_tomorrow)
        else -> trip.leaveAt.atZoneSameInstant(zone).dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    }
    val blue = AppColors.Blue
    val ground = remember(blue) { Brush.linearGradient(listOf(blue, lerp(blue, Color.Black, 0.28f))) }
    val minutes = Duration.between(now, trip.leaveAt).toMinutes().coerceAtLeast(0)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(ground)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.r3_planned_open)) { PlannedTripActions.open(trip) }
            .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.ways_planned_label, day),
                color = AppColors.OnBlue.copy(alpha = 0.8f),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            RoundButton(
                glyph = Glyph.Close,
                description = stringResource(R.string.planned_cancel),
                onClick = { PlannedTripActions.cancel(context, trip) },
                size = 40.dp,
            )
        }
        Text(
            text = stringResource(R.string.ways_planned_title, trip.title, trip.leaveAt.clock()),
            color = AppColors.OnBlue,
            fontWeight = FontWeight.Bold,
            fontSize = 19.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = 10.dp),
        )
        Row(
            modifier = Modifier.padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            trip.lines.take(MAX_PLANNED_BADGES).forEachIndexed { index, line ->
                LineBadge(line, trip.products.getOrNull(index) ?: guessedProduct(line), minWidth = 34.dp)
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = leaveInText(minutes),
                color = AppColors.OnLime,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(AppColors.Lime)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

/** "in 12′", "in 9:05 h", or "now". */
@Composable
private fun leaveInText(minutes: Long): String = when {
    minutes <= 0 -> stringResource(R.string.ways_leave_now)
    minutes < 60 -> stringResource(R.string.ways_leave_in_min, minutes.toInt())
    else -> stringResource(R.string.ways_leave_in_hours, (minutes / 60).toInt(), (minutes % 60).toInt())
}

/** The kind of transport of a line known only by its name, for its badge: S and U lines, trains; else unknown. */
internal fun guessedProduct(line: String): String {
    val name = line.trim().uppercase()
    return when {
        SUBURBAN_NAME.matches(name) -> "suburban"
        SUBWAY_NAME.matches(name) -> "subway"
        REGIONAL_NAME.matches(name) -> "regional"
        EXPRESS_NAME.matches(name) -> "express"
        else -> ""
    }
}

// ---------------------------------------------------------------------------------------------
// b. The strip of the statistics

/** Trips · km · punctual % of the last 30 days, under the map; a tap opens the details. */
@Composable
fun WaysStatsStrip(summary: TripSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    GlassSurface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatFigure(summary.trips, stringResource(R.string.ways_stats_trips), Modifier.weight(1f))
                StatFigure(summary.km, stringResource(R.string.ways_stats_km), Modifier.weight(1f))
                StatFigure(summary.punctualPercent, stringResource(R.string.ways_stats_punctual), Modifier.weight(1f), suffix = "%")
                Chevron(open = false, modifier = Modifier.graphicsLayer { rotationZ = -90f })
            }
            Text(
                text = stringResource(if (summary.trips == 0) R.string.ways_stats_empty else R.string.ways_stats_period),
                color = AppColors.Muted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** One figure of the strip: it counts up from nought when it first shows (see CountingText). */
@Composable
private fun StatFigure(value: Int?, label: String, modifier: Modifier = Modifier, suffix: String = "") {
    val animate = rememberAnimationsOn()
    var shown by remember { mutableIntStateOf(if (animate) 0 else value ?: 0) }
    LaunchedEffect(value) {
        if (animate && shown == 0) animationDelay(COUNT_DELAY_MILLIS)
        shown = value ?: 0
    }
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        if (value == null) {
            Text(text = "–", color = AppColors.Ink, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, modifier = Modifier.alignByBaseline())
        } else {
            CountingText(value = shown, color = AppColors.Ink, fontSize = 20.sp, modifier = Modifier.alignByBaseline(), slotDigits = 1)
            if (suffix.isNotEmpty()) {
                Text(text = suffix, color = AppColors.Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.alignByBaseline())
            }
        }
        Text(
            text = label,
            color = AppColors.Muted,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .alignByBaseline()
                .padding(start = 4.dp),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// c. Disruptions on the user's ways

/**
 * The notices on the user's ways, each with its lines and a short text; [onItem] opens one,
 * [onAll] all in the region. While nothing is known yet ([known] false) only the link shows.
 * The notices in [highlight] (a tap on a notification of the watch) light up, unfolded.
 */
@Composable
fun WaysDisruptionsSection(
    items: List<Disruption>,
    known: Boolean,
    onItem: (Disruption) -> Unit,
    onMore: () -> Unit,
    onAll: (() -> Unit)?,
    highlight: Set<String> = emptySet(),
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(stringResource(R.string.ways_notices_title), modifier = Modifier.weight(1f))
            if (onAll != null) {
                Text(
                    text = stringResource(R.string.ways_disruptions_all),
                    color = AppColors.Blue,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button, onClick = onAll)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
        if (items.isEmpty()) {
            if (known) {
                Text(
                    text = stringResource(R.string.ways_disruptions_clear),
                    color = AppColors.Muted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                )
            }
            return@Column
        }
        // More than two fold to one line by default (badges and a count); a tap unfolds them.
        // The user's choice holds while the screen lives.
        var choice by rememberSaveable { mutableStateOf<Boolean?>(null) }
        // Notices to light up unfold the list (and it stays open).
        val lit = highlight.isNotEmpty() && items.any { it.id in highlight }
        LaunchedEffect(lit) { if (lit) choice = true }
        val open = lit || (choice ?: !waysNoticesFoldedByDefault(items.size))
        val animate = rememberAnimationsOn()
        GlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
            Column(modifier = Modifier.softSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (!waysNoticesHaveFoldRow(items.size)) {
                    // One notice: its row alone, not the folding line above it saying the same (#134).
                    val item = items.first()
                    key(item.id) {
                        WaysNoticeRow(item = item, shape = groupShape(0, 0), onClick = { onItem(item) }, highlighted = item.id in highlight)
                    }
                    return@Column
                }
                val shown = items.take(MAX_WAYS_NOTICES)
                val rest = items.size - shown.size
                val last = if (rest > 0) shown.size else shown.lastIndex
                WaysNoticesFoldRow(
                    items = items,
                    open = open,
                    shape = if (open) groupShape(0, last + 1) else groupShape(0, 0),
                    onClick = { choice = !open },
                )
                AnimatedVisibility(
                    visible = open,
                    enter = if (animate) expandVertically(Motion.settle()) + fadeIn(Motion.fadeIn()) else EnterTransition.None,
                    exit = if (animate) shrinkVertically(Motion.settle()) + fadeOut(Motion.fadeOut()) else ExitTransition.None,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        shown.forEachIndexed { index, item ->
                            key(item.id) {
                                WaysNoticeRow(
                                    item = item,
                                    shape = groupShape(index + 1, last + 1),
                                    onClick = { onItem(item) },
                                    highlighted = item.id in highlight,
                                )
                            }
                        }
                        if (rest > 0) {
                            Text(
                                text = stringResource(R.string.ways_disruptions_more, rest),
                                color = AppColors.Blue,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(groupShape(last + 1, last + 1))
                                    .background(AppColors.Card)
                                    .clickable(role = Role.Button, onClick = onMore)
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The line that folds the notices: the badges of their lines, how many, and a chevron that turns. */
@Composable
private fun WaysNoticesFoldRow(items: List<Disruption>, open: Boolean, shape: androidx.compose.ui.graphics.Shape, onClick: () -> Unit) {
    val action = stringResource(if (open) R.string.r4_ways_notices_collapse else R.string.r4_ways_notices_expand)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.Card)
            .clickable(role = Role.Button, onClickLabel = action, onClick = onClick)
            .defaultMinSize(minHeight = 44.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Each badge coloured at the place of its notice, as in its row (see colourPlace).
        val (badges, more) = remember(items) { foldedNoticeBadgesPlaced(items, FOLDED_BADGES) }
        if (badges.isEmpty()) PoppingWarning()
        badges.forEach { badge -> LineBadge(badge.line.line, badge.line.product, minWidth = 30.dp, at = badge.place) }
        if (more > 0) {
            Text(text = "+$more", color = AppColors.Muted, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1)
        }
        Text(
            text = pluralStringResource(R.plurals.r4_ways_notices_count, items.size, items.size),
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
        Chevron(open = open)
    }
}

@Composable
private fun WaysNoticeRow(
    item: Disruption,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
    highlighted: Boolean = false,
) {
    // Lit briefly when a notification led here: a tint of the accent that fades in and out
    // (Compose follows the animator scale; without animations it only switches).
    val card = AppColors.Card
    val lit = lerp(card, AppColors.Blue, HIGHLIGHT_SHARE)
    val ground by animateColorAsState(if (highlighted) lit else card, Motion.settle(), label = "noticeHighlight")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .drawBehind { drawRect(ground) }
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 52.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val badges = item.badges
        if (badges.isEmpty()) {
            PoppingWarning()
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                val place = item.colourPlace
                badges.take(2).forEach { badge -> LineBadge(badge.line, badge.product, minWidth = 34.dp, at = place) }
            }
        }
        Text(
            text = item.summary.ifEmpty { item.text },
            fontSize = 14.sp,
            lineHeight = 18.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

// How much of the accent a notice lit by a notification tap takes.
private const val HIGHLIGHT_SHARE = 0.24f

// ---------------------------------------------------------------------------------------------
// d. The favourite places as a grid of four

/**
 * The favourite places, two by two. Each tile shows the travel time from here and when to
 * leave ([now]); "You are here" at the place; a "!" where a notice touches it. A long press
 * and a drag moves a tile to another cell ([onMove] gets the new order of the keys); a long
 * press without a drag opens the tile's editor, as before.
 */
@Composable
fun PlaceTileGrid(tiles: List<PlaceTile>, now: OffsetDateTime, onMove: (List<String>) -> Unit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val animate = rememberAnimationsOn()
    val scope = rememberCoroutineScope()
    // Each tile's own height (its content, see PlaceTileCard), by key. Every row is as tall as
    // its taller tile and at least GRID_MIN_TILE; the grid is as tall as its rows, so nothing
    // under it is overlapped, and a row shrinks again when its tiles do.
    val naturals = remember { mutableStateMapOf<String, Int>() }
    // While a tile is dragged: the order of the keys, which tile, and how far it is from its cell.
    var order by remember { mutableStateOf<List<String>?>(null) }
    var dragged by remember { mutableStateOf<String?>(null) }
    var drag by remember { mutableStateOf(Offset.Zero) }
    val latestTiles by rememberUpdatedState(tiles)
    val latestMove by rememberUpdatedState(onMove)
    val keys = order ?: tiles.map { it.key }
    val rows = (tiles.size + 1) / 2
    val minTile = with(density) { GRID_MIN_TILE.roundToPx() }
    val rowHeights = IntArray(rows)
    for (row in 0 until rows) {
        var target = minTile
        var measured = true
        for (column in 0 until 2) {
            val key = keys.getOrNull(row * 2 + column) ?: continue
            val natural = naturals[key]
            if (natural == null) measured = false else if (natural > target) target = natural
        }
        rowHeights[row] = key(row) { animatedRowHeight(target, measured, animate) }
    }
    // The current measures for the finger handlers, which outlive a composition; written by the
    // layout, read there (not state).
    val geometry = remember { GridGeometry() }
    geometry.slop = with(density) { 10.dp.toPx() }
    // Where each tile is shown, gliding to a new cell after a move.
    val glides = remember { HashMap<String, Animatable<IntOffset, AnimationVector2D>>() }
    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            tiles.forEach { tile ->
                key(tile.key) {
                    val isDragged = dragged == tile.key
                    // How far the finger went in all, to tell a drag from a long press.
                    val travelled = remember { FloatArray(1) }
                    // The long press of the card starts it; the grid follows the finger from there.
                    val start = {
                        order = latestTiles.map { it.key }
                        dragged = tile.key
                        drag = Offset.Zero
                        travelled[0] = 0f
                    }
                    // The finger lifted: the new order is kept; without a drag it was a long press,
                    // which opens the editor of the place as before. The tile glides on from where
                    // it was let go.
                    fun finish(keep: Boolean) {
                        val moved = order
                        val wasDrag = travelled[0] > geometry.slop
                        glides[tile.key]?.let { glide ->
                            val from = geometry.draggedAt
                            scope.launch { glide.snapTo(from) }
                        }
                        if (keep && moved != null && moved != latestTiles.map { it.key }) latestMove(moved)
                        if (keep && !wasDrag) latestTiles.firstOrNull { it.key == tile.key }?.onLongClick?.invoke()
                        order = null
                        dragged = null
                        drag = Offset.Zero
                    }
                    fun follow(amount: Offset) {
                        travelled[0] += amount.getDistance()
                        val list = order ?: return
                        val from = list.indexOf(tile.key)
                        val tops = geometry.tops
                        var shifted = drag + amount
                        if (from >= 0 && tops.isNotEmpty()) {
                            val to = gridDropSlotRows(from, shifted.x, shifted.y, geometry.cellWidth, tops, geometry.heights, geometry.gap, list.size)
                            if (to != from) {
                                // The tile takes the other cell; its offset is counted from there now.
                                val (fromColumn, fromRow) = gridCell(from)
                                val (toColumn, toRow) = gridCell(to)
                                val dy = tops.getOrElse(toRow) { 0f } - tops.getOrElse(fromRow) { 0f }
                                shifted -= Offset((toColumn - fromColumn) * (geometry.cellWidth + geometry.gap), dy)
                                order = list.movedTo(from, to)
                            }
                        }
                        drag = shifted
                    }
                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                val lift = if (isDragged) 1.04f else 1f
                                scaleX = lift
                                scaleY = lift
                            }
                            .pointerInput(tile.key) {
                                // Seen before the card (Initial pass) and consumed while this tile is
                                // dragged, so the card takes no tap and the list does not scroll.
                                awaitEachGesture {
                                    // Cut short (the tile left, the input restarted): the tile goes back.
                                    try {
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        val change = event.changes.firstOrNull() ?: continue
                                        if (dragged != tile.key) {
                                            if (event.changes.none { it.pressed }) break
                                            continue
                                        }
                                        if (!change.pressed) {
                                            change.consume()
                                            finish(keep = true)
                                            break
                                        }
                                        val amount = change.positionChange()
                                        change.consume()
                                        if (amount != Offset.Zero) follow(amount)
                                    }
                                    } catch (e: CancellationException) {
                                        if (dragged == tile.key) finish(keep = false)
                                        throw e
                                    }
                                }
                            },
                    ) {
                        val journey = tile.journey
                        val leave = journey?.takeIf { !tile.here }?.let { Duration.between(now, it.departure).toMinutes() }
                        val tileKey = tile.key
                        PlaceTileCard(
                            tile.copy(onLongClick = start, leaveMinutes = leave),
                            modifier = Modifier.fillMaxSize(),
                            onNaturalHeight = { height -> if (naturals[tileKey] != height) naturals[tileKey] = height },
                        )
                        if (tile.disruptions.isNotEmpty()) {
                            PoppingWarning(modifier = Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-4).dp))
                        }
                    }
                }
            }
        },
    ) { measurables, constraints ->
        // Measured at no width (an entering animation can ask so): nothing to lay out yet.
        val minWidth = GRID_MIN_WIDTH.roundToPx()
        if (!constraints.hasBoundedWidth || constraints.maxWidth < minWidth || rows == 0) {
            return@Layout layout(constraints.minWidth, constraints.minHeight) {}
        }
        val width = constraints.maxWidth
        val gap = GRID_GAP.roundToPx()
        val cellWidth = (width - gap) / 2
        val heights = FloatArray(rows) { rowHeights[it].toFloat() }
        val tops = rowTopsOf(heights, gap.toFloat())
        geometry.cellWidth = cellWidth.toFloat()
        geometry.gap = gap.toFloat()
        geometry.tops = tops
        geometry.heights = heights
        val slots = IntArray(measurables.size) { index -> (tiles.getOrNull(index)?.key?.let { keys.indexOf(it) } ?: 0).coerceAtLeast(0) }
        val placeables = measurables.mapIndexed { index, measurable ->
            val row = gridCell(slots[index]).second.coerceIn(0, rows - 1)
            measurable.measure(Constraints.fixed(cellWidth, rowHeights[row]))
        }
        val total = (tops[rows - 1] + heights[rows - 1]).toInt()
        layout(width, total.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            placeables.forEachIndexed { index, placeable ->
                val key = tiles.getOrNull(index)?.key ?: return@forEachIndexed
                val (column, row) = gridCell(slots[index])
                val target = IntOffset(column * (cellWidth + gap), tops[row.coerceIn(0, rows - 1)].toInt())
                val glide = glides.getOrPut(key) { Animatable(target, IntOffset.VectorConverter) }
                if (key == dragged) {
                    val at = IntOffset(target.x + drag.x.toInt(), target.y + drag.y.toInt())
                    geometry.draggedAt = at
                    placeable.place(at, zIndex = 1f)
                } else {
                    if (glide.targetValue != target) {
                        scope.launch { if (animate) glide.animateTo(target, TILE_GLIDE) else glide.snapTo(target) }
                    }
                    placeable.place(glide.value)
                }
            }
        }
    }
}

/** The measures of the place grid the finger handlers need, written by its layout. */
private class GridGeometry {
    var cellWidth = 0f
    var gap = 0f
    var slop = 0f
    var tops = FloatArray(0)
    var heights = FloatArray(0)
    var draggedAt = IntOffset.Zero
}

private val TILE_GLIDE = spring<IntOffset>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)

/**
 * The height of a row of the place grid: it follows [target] softly, and takes it at once
 * while its tiles are not [measured] yet (the first layout) or without animations.
 */
@Composable
private fun animatedRowHeight(target: Int, measured: Boolean, animate: Boolean): Int {
    val height = remember { Animatable(target, Int.VectorConverter) }
    val settled = remember { BooleanArray(1) }
    LaunchedEffect(target, measured, animate) {
        if (!measured || !settled[0] || !animate) {
            if (measured) settled[0] = true
            height.snapTo(target)
        } else {
            height.animateTo(target, ROW_GROW)
        }
    }
    return height.value
}

private val ROW_GROW = spring<Int>(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)

// ---------------------------------------------------------------------------------------------
// e. Routes and connections in one list

/** The label above a row of the routes: "Route · any connection" or "Connection · exactly these lines", and the next departure. */
@Composable
fun WaysTypeLine(pattern: Boolean, next: Long?, now: OffsetDateTime) {
    Row(
        modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(if (pattern) R.string.ways_type_pattern else R.string.ways_type_route),
            color = AppColors.Muted,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (next != null) {
            val minutes = (next - now.toInstant().toEpochMilli()) / 60_000L
            Text(
                text = if (minutes in 0..59) {
                    stringResource(R.string.ways_next_in, minutes.toInt())
                } else {
                    stringResource(R.string.ways_next_at, statTime(next).clock())
                },
                color = AppColors.Blue,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                maxLines = 1,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// g. The statistics in detail

/**
 * What the strip does not show: trips per week as bars, the most used lines, the average
 * delay and the longest trip; "Share" makes a picture of them for the share sheet.
 */
@Composable
fun WaysStatsDetails(records: List<TripRecord>, today: LocalDate, summary: TripSummary, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Worked out once per change of the records, not while drawing.
    val weeks = remember(records, today) { tripsPerWeek(records, today) }
    val lines = remember(records) { mostUsedLines(records) }
    val delay = remember(records) { averageDelayMinutes(records) }
    val longest = remember(records) { longestTrip(records) }
    GlassSurface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (records.isEmpty()) {
                Text(text = stringResource(R.string.ways_stats_none), color = AppColors.Muted, fontSize = 14.sp)
                return@Column
            }
            Text(text = stringResource(R.string.ways_stats_weeks), color = AppColors.Muted, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            WeekBars(weeks)
            if (lines.isNotEmpty()) {
                Text(text = stringResource(R.string.ways_stats_lines), color = AppColors.Muted, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                lines.forEach { use -> LineUseRow(use) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatBox(
                    label = stringResource(R.string.ways_stats_delay),
                    value = delay?.let { String.format(Locale.getDefault(), "%.1f′", it) } ?: "–",
                    modifier = Modifier.weight(1f),
                )
                StatBox(
                    label = stringResource(R.string.ways_stats_longest),
                    value = longest?.let { stringResource(R.string.ways_stats_longest_value, it.minutes.toInt()) } ?: "–",
                    modifier = Modifier.weight(1f),
                )
            }
            val shareTitle = stringResource(R.string.ways_stats_share_title)
            val labels = StatsPictureLabels(
                title = shareTitle,
                trips = stringResource(R.string.ways_stats_trips),
                km = stringResource(R.string.ways_stats_km),
                punctual = stringResource(R.string.ways_stats_punctual),
                weeks = stringResource(R.string.ways_stats_weeks),
                delay = stringResource(R.string.ways_stats_delay),
                period = stringResource(R.string.ways_stats_period),
            )
            val ink = AppColors.Ink.toArgb()
            val blue = AppColors.Blue.toArgb()
            val ground = AppColors.Card.toArgb()
            SecondaryButton(
                text = stringResource(R.string.ways_stats_share),
                onClick = {
                    scope.launch {
                        val picture = StatsPicture(summary, weeks, lines, delay, labels, ink, blue, ground)
                        shareStatsPicture(context, picture, shareTitle)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                ground = AppColors.BlueContainer,
                ink = AppColors.OnBlueContainer,
                minHeight = 44.dp,
            )
            // Forgets the recorded trips after asking (#195); the same as in Settings › On the way.
            StatsResetButton(modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

/** The trips of the last weeks as bars that grow in once; the newest week on the right. */
@Composable
private fun WeekBars(weeks: IntArray) {
    val animate = rememberAnimationsOn()
    val grow = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) { if (grow.value < 1f) grow.animateTo(1f, Motion.settle()) }
    val bar = AppColors.Blue
    val faint = AppColors.Chip
    val most = (weeks.maxOrNull() ?: 0).coerceAtLeast(1)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
    ) {
        val count = weeks.size
        val space = 6.dp.toPx()
        val width = (size.width - space * (count - 1)) / count
        val radius = CornerRadius(4.dp.toPx())
        for (at in 0 until count) {
            val x = at * (width + space)
            drawRoundRect(color = faint, topLeft = Offset(x, 0f), size = Size(width, size.height), cornerRadius = radius)
            val height = size.height * weeks[at] / most * grow.value
            if (height > 0f) {
                drawRoundRect(color = bar, topLeft = Offset(x, size.height - height), size = Size(width, height), cornerRadius = radius)
            }
        }
    }
}

@Composable
private fun LineUseRow(use: LineUse) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LineBadge(use.line, use.product, minWidth = 40.dp)
        Text(text = stringResource(R.string.ways_stats_line_trips, use.trips), color = AppColors.Muted, fontSize = 13.sp)
    }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.Card)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = label, color = AppColors.Muted, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1)
        Text(text = value, color = AppColors.Ink, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, maxLines = 1)
    }
}

/** The words of the shared picture, read while composing (the picture is drawn off the main thread). */
class StatsPictureLabels(
    val title: String,
    val trips: String,
    val km: String,
    val punctual: String,
    val weeks: String,
    val delay: String,
    val period: String,
)

/** What the shared picture shows, and its colours (ARGB). */
class StatsPicture(
    val summary: TripSummary,
    val weeks: IntArray,
    val lines: List<LineUse>,
    val delay: Double?,
    val labels: StatsPictureLabels,
    val ink: Int,
    val accent: Int,
    val ground: Int,
)

/** Draws [picture] as a PNG in the cache and opens the share sheet with it (FileProvider of the app). */
private suspend fun shareStatsPicture(context: Context, picture: StatsPicture, title: String) {
    val file = try {
        withContext(Dispatchers.Default) {
            val bitmap = drawStatsPicture(picture)
            withContext(Dispatchers.IO) {
                val folder = File(context.cacheDir, SHARE_FOLDER).apply { mkdirs() }
                File(folder, SHARE_FILE).also { out -> FileOutputStream(out).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        DiagLog.w(TAG, e) { "stats picture failed" }
        return
    }
    val uri = FileProvider.getUriForFile(context, context.packageName + ".diag", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        DiagLog.w(TAG, e) { "no app to share the stats picture" }
    }
}

/** The picture of the statistics: the title, the three figures, the bars of the weeks, the most used lines. */
private fun drawStatsPicture(picture: StatsPicture): Bitmap {
    val width = 1080
    val height = 1080
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(picture.ground)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = picture.ink }
    val labels = picture.labels
    paint.textSize = 64f
    paint.isFakeBoldText = true
    canvas.drawText(labels.title, 80f, 160f, paint)
    paint.isFakeBoldText = false
    paint.textSize = 36f
    paint.alpha = 160
    canvas.drawText(labels.period, 80f, 220f, paint)
    paint.alpha = 255
    val figures = listOf(
        picture.summary.trips.toString() to labels.trips,
        picture.summary.km.toString() to labels.km,
        (picture.summary.punctualPercent?.let { "$it%" } ?: "–") to labels.punctual,
    )
    figures.forEachIndexed { at, (value, label) ->
        val x = 80f + at * 320f
        paint.textSize = 120f
        paint.isFakeBoldText = true
        paint.color = picture.accent
        canvas.drawText(value, x, 400f, paint)
        paint.isFakeBoldText = false
        paint.textSize = 40f
        paint.color = picture.ink
        canvas.drawText(label, x, 460f, paint)
    }
    paint.textSize = 40f
    canvas.drawText(labels.weeks, 80f, 580f, paint)
    val most = (picture.weeks.maxOrNull() ?: 0).coerceAtLeast(1)
    val barSpace = 16f
    val barWidth = (width - 160f - barSpace * (picture.weeks.size - 1)) / picture.weeks.size
    val barTop = 620f
    val barHeight = 220f
    picture.weeks.forEachIndexed { at, count ->
        val x = 80f + at * (barWidth + barSpace)
        paint.color = picture.ink
        paint.alpha = 30
        canvas.drawRoundRect(RectF(x, barTop, x + barWidth, barTop + barHeight), 12f, 12f, paint)
        paint.color = picture.accent
        val h = barHeight * count / most
        if (h > 0f) canvas.drawRoundRect(RectF(x, barTop + barHeight - h, x + barWidth, barTop + barHeight), 12f, 12f, paint)
    }
    paint.color = picture.ink
    paint.textSize = 40f
    val linesText = picture.lines.joinToString("   ") { "${it.line} ×${it.trips}" }
    canvas.drawText(linesText, 80f, 930f, paint)
    picture.delay?.let { canvas.drawText(labels.delay + ": " + String.format(Locale.getDefault(), "%.1f′", it), 80f, 1000f, paint) }
    paint.textSize = 30f
    paint.alpha = 140
    canvas.drawText("Next Stop", width - 260f, height - 50f, paint)
    return bitmap
}

// Sections come in this long after the one before, the later ones together.
private const val STAGGER_MILLIS = 55L
private const val MAX_STAGGER = 6
private val APPEAR_RISE = 18.dp

// The figures of the strip start counting this long after the page shows.
private const val COUNT_DELAY_MILLIS = 220L

// Line badges on a planned trip, notices listed on the page before "+n more".
private const val MAX_PLANNED_BADGES = 4
private const val MAX_WAYS_NOTICES = 4
// How many line badges the folded line of the notices shows before "+n".
private const val FOLDED_BADGES = 4

private val GRID_GAP = 8.dp

// Line names by their kind (see guessedProduct).
private val SUBURBAN_NAME = Regex("^S\\s?\\d.*")
private val SUBWAY_NAME = Regex("^U\\s?\\d.*")
private val REGIONAL_NAME = Regex("^(RE|RB|FEX|IRE)\\s?\\d.*")
private val EXPRESS_NAME = Regex("^(ICE|IC|EC)\\s?\\d.*")

// Where the shared picture is kept (see res/xml/diag_paths.xml).
private const val SHARE_FOLDER = "share"
private const val SHARE_FILE = "next-stop-stats.png"
private const val TAG = "Ways"

// The place grid needs at least this width to lay out its two columns; a row is at least this tall.
private val GRID_MIN_WIDTH = 160.dp
private val GRID_MIN_TILE = 96.dp
