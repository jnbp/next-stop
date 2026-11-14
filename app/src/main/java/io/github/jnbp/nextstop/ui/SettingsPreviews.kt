package io.github.jnbp.nextstop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.AppPrefs
import io.github.jnbp.nextstop.data.OsmLine
import io.github.jnbp.nextstop.ui.theme.AppColors
import io.github.jnbp.nextstop.ui.theme.Appearance
import io.github.jnbp.nextstop.ui.theme.ColorChoice
import io.github.jnbp.nextstop.ui.theme.DarkChoice
import io.github.jnbp.nextstop.ui.theme.MapStyleChoice
import io.github.jnbp.nextstop.ui.theme.NotificationLook
import io.github.jnbp.nextstop.ui.theme.VehicleDirectionChoice
import io.github.jnbp.nextstop.widget.WidgetColorChoice

/*
 * Small schematic previews for the settings, in the manner of the sketches of the setup
 * (SetupWizard): drawn, not screenshots, in the colours of the app and the official line
 * colours. A setting with a visual effect gets a quiet "Preview" expander under its control
 * ([PreviewExpander]); unfolded, it shows the sketch of the value chosen now and follows a
 * change at once. A choice of a few values shows the sketch of every value side by side
 * ([SketchOptions]), the chosen one with a ring. The sketches are decorative: hidden from
 * accessibility services, while the expander is named "Show preview" / "Hide preview".
 */

/** The usual height of a preview sketch. */
internal val PREVIEW_HEIGHT = 104.dp

// The height of a sketch when the options stand side by side.
private val OPTION_SKETCH_HEIGHT = 84.dp

/**
 * Which previews are unfolded, by key, while the settings screen is open (see
 * [ProvidePreviewStates]); without it every expander keeps its own state.
 */
internal val LocalPreviewStates = staticCompositionLocalOf<SnapshotStateMap<String, Boolean>?> { null }

/** Keeps the unfolded previews of [content] for as long as it stays, e.g. across the page "Advanced". */
@Composable
internal fun ProvidePreviewStates(content: @Composable () -> Unit) {
    val states = remember { mutableStateMapOf<String, Boolean>() }
    CompositionLocalProvider(LocalPreviewStates provides states, content = content)
}

/**
 * The quiet "Preview ⌄" under the control of a setting, and below it the animated area with
 * [content], folded by default. [key] names the setting, for the state kept per row.
 */
@Composable
internal fun PreviewExpander(key: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val states = LocalPreviewStates.current
    var own by remember { mutableStateOf(false) }
    val open = states?.get(key) ?: own
    val animate = rememberAnimationsOn()
    val label = stringResource(if (open) R.string.preview_hide else R.string.preview_show)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .semantics(mergeDescendants = true) { contentDescription = label }
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClickLabel = label) {
                    if (states != null) states[key] = !open else own = !open
                }
                .defaultMinSize(minHeight = 40.dp)
                .padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.preview_label),
                color = AppColors.Blue,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier.clearAndSetSemantics { },
            )
            Chevron(open = open, color = AppColors.Blue)
        }
        AnimatedVisibility(
            visible = open,
            enter = if (animate) {
                expandVertically(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) + fadeIn()
            } else {
                EnterTransition.None
            },
            exit = if (animate) shrinkVertically(spring(stiffness = Spring.StiffnessMedium)) + fadeOut() else ExitTransition.None,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 2.dp)
                    .clearAndSetSemantics { },
            ) {
                content()
            }
        }
    }
}

/**
 * A setting that shows only while another one allows it (e.g. the options of the shortcut
 * alert): it unfolds and fades in, and folds and fades out, instead of popping; with
 * animations switched off it simply appears and goes.
 */
@Composable
internal fun SettingReveal(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val animate = rememberAnimationsOn()
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (animate) {
            expandVertically(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)) + fadeIn()
        } else {
            EnterTransition.None
        },
        exit = if (animate) shrinkVertically(spring(stiffness = Spring.StiffnessMedium)) + fadeOut() else ExitTransition.None,
    ) {
        content()
    }
}

/**
 * Runs from 0 to 1 whenever [key] changes while [active], for the small motion of a sketch
 * as its value is chosen; rests at 1 otherwise and with animations switched off.
 */
@Composable
internal fun rememberPreviewReveal(key: Any?, active: Boolean = true): Animatable<Float, AnimationVector1D> {
    val animate = rememberAnimationsOn()
    val reveal = remember { Animatable(if (animate && active) 0f else 1f) }
    LaunchedEffect(key, active, animate) {
        if (!animate || !active) {
            reveal.snapTo(1f)
        } else {
            reveal.snapTo(0f)
            reveal.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow))
        }
    }
    return reveal
}

/** One sketch across the width, played again whenever [key] (the value it shows) changes. */
@Composable
internal fun SketchPane(key: Any?, height: Dp = PREVIEW_HEIGHT, sketch: DrawScope.(reveal: Float) -> Unit) {
    val reveal = rememberPreviewReveal(key)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.Chip),
    ) {
        sketch(reveal.value)
    }
}

/** The sketch of every value side by side with its [labels]; the [selected] one wears a ring. */
@Composable
internal fun SketchOptions(
    labels: List<String>,
    selected: Int,
    height: Dp = OPTION_SKETCH_HEIGHT,
    sketch: DrawScope.(index: Int, reveal: Float) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        labels.forEachIndexed { index, label ->
            val picked = index == selected
            val reveal = rememberPreviewReveal(picked, active = picked)
            val ring by animateColorAsState(
                if (picked) AppColors.Blue else Color.Transparent,
                if (rememberAnimationsOn()) spring<Color>(stiffness = Spring.StiffnessMediumLow) else snap<Color>(),
                label = "previewRing",
            )
            val shape = RoundedCornerShape(16.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(shape)
                    .border(2.dp, ring, shape)
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(height)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppColors.Chip),
                ) {
                    sketch(index, reveal.value)
                }
                Text(
                    text = label,
                    color = if (picked) AppColors.Blue else AppColors.Muted,
                    fontWeight = if (picked) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------
// The previews of the settings, one per setting.
// ---------------------------------------------------------------------------------------

/** The colour scheme: a departure card in the colours of Next Stop, of the system and of an own colour. */
@Composable
internal fun ColorSchemePreview(selected: ColorChoice) {
    SketchOptions(
        labels = listOf(
            stringResource(R.string.color_next_stop),
            stringResource(R.string.color_system),
            stringResource(R.string.color_custom),
        ),
        selected = ColorChoice.entries.indexOf(selected),
    ) { index, reveal -> colorSchemeSketch(index, reveal) }
}

/** Light, dark, or as the system: a small screen in each. */
@Composable
internal fun DarkModePreview(selected: DarkChoice) {
    SketchOptions(
        labels = listOf(
            stringResource(R.string.dark_light),
            stringResource(R.string.dark_dark),
            stringResource(R.string.dark_system),
        ),
        selected = DarkChoice.entries.indexOf(selected),
    ) { index, reveal -> darkModeSketch(index, reveal) }
}

/** The colours of the widgets: the app's own, or the tones of Material You, on a home screen. */
@Composable
internal fun WidgetColorsPreview(selected: WidgetColorChoice) {
    SketchOptions(
        labels = listOf(stringResource(R.string.widget_colors_app), stringResource(R.string.widget_colors_material)),
        selected = WidgetColorChoice.entries.indexOf(selected),
    ) { index, reveal -> widgetSketch(material = index == 1, reveal = reveal) }
}

/** The direction of travel at the vehicles on the map: none, or a small arrow ahead of each. */
@Composable
internal fun VehicleDirectionPreview(selected: VehicleDirectionChoice) {
    SketchOptions(
        labels = listOf(stringResource(R.string.vehicle_direction_off), stringResource(R.string.vehicle_direction_on)),
        selected = VehicleDirectionChoice.entries.indexOf(selected),
    ) { index, reveal -> vehicleDirectionSketch(arrow = index == 1, reveal = reveal) }
}

/** The vehicles nearby on the map of the start page, or a quiet map around the own position. */
@Composable
internal fun StartVehiclesPreview(on: Boolean) {
    SketchPane(key = on) { reveal -> startVehiclesSketch(on, reveal) }
}

/** A station close up: its stop positions, entrances and lifts, or one plain marker. */
@Composable
internal fun StationDetailsPreview(on: Boolean) {
    SketchPane(key = on) { reveal -> stationDetailsSketch(on, reveal) }
}

/** Entrances and exits: automatically, always, or only at the selected station. */
@Composable
internal fun EntranceModePreview(selected: EntranceMode) {
    SketchOptions(
        labels = listOf(
            stringResource(R.string.entrance_mode_auto),
            stringResource(R.string.entrance_mode_always),
            stringResource(R.string.entrance_mode_marked),
        ),
        selected = EntranceMode.entries.indexOf(selected),
    ) { index, reveal -> entranceModeSketch(EntranceMode.entries[index], reveal) }
}

/** Bus and tram stops close up: the round H, or their names in green. */
@Composable
internal fun StopNamesPreview(on: Boolean) {
    SketchPane(key = on) { reveal -> stopNamesSketch(on, reveal) }
}

/** The signs of disruptions at the stops of the map: a warning sign comes in at a station when on. */
@Composable
internal fun MapNoticesPreview(on: Boolean) {
    SketchPane(key = on) { reveal -> mapNoticesSketch(on, reveal) }
}

/** The small hint at the bottom of the map that vehicles are worked out, not tracked. */
@Composable
internal fun VehicleHintPreview(on: Boolean) {
    SketchPane(key = on) { reveal -> vehicleHintSketch(on, reveal) }
}

/** A tapped vehicle: the map stays close, or moves out until the whole line shows. */
@Composable
internal fun FitLinePreview(on: Boolean) {
    SketchPane(key = on) { reveal -> fitLineSketch(on, reveal) }
}

/** The notification of a trip: Live Update with its chip, or the blue box of the app. */
@Composable
internal fun NotificationLookPreview(selected: NotificationLook) {
    SketchOptions(
        labels = listOf(stringResource(R.string.notification_look_live), stringResource(R.string.notification_look_app)),
        selected = NotificationLook.entries.indexOf(selected),
    ) { index, reveal -> notificationSketch(chip = index == 0, reveal = reveal) }
}

/** The start page as one sheet to pull up, or its parts floating on the map. */
@Composable
internal fun StartLayoutPreview(classic: Boolean) {
    SketchOptions(
        labels = listOf(stringResource(R.string.start_layout_sheet), stringResource(R.string.start_layout_classic)),
        selected = if (classic) 1 else 0,
    ) { index, reveal -> startLayoutSketch(classic = index == 1, reveal = reveal) }
}

/** The part of the start page a setting of the start page stands for (see [StartPagePreview]). */
internal enum class StartPart { Cards, Departures, Calendar, Map }

/**
 * The start page as the settings make it now: map or calm ground, the field "Where to", the
 * tiles, the departures nearby and the next appointment. The [part] of the row plays in.
 */
@Composable
internal fun StartPagePreview(part: StartPart, map: Boolean, cards: Boolean, departures: Boolean, calendar: Boolean) {
    val key = when (part) {
        StartPart.Cards -> cards
        StartPart.Departures -> departures
        StartPart.Calendar -> calendar
        StartPart.Map -> map
    }
    SketchPane(key = key) { reveal ->
        startPageSketch(part = part, map = map, cards = cards, departures = departures, calendar = calendar, reveal = reveal)
    }
}

/**
 * The next appointment on a small start page, as the setup shows it under its switch: the
 * map, the field "Where to" and the card with an example appointment and when to leave. The
 * card springs in when [on] turns true and fades out when it turns false; with animations
 * switched off it simply appears and goes. Decorative, hidden from accessibility services.
 */
@Composable
internal fun NextAppointmentPreview(on: Boolean) {
    val animate = rememberAnimationsOn()
    val shown by animateFloatAsState(
        targetValue = if (on) 1f else 0f,
        animationSpec = if (animate) spring<Float>(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow) else snap<Float>(),
        label = "appointmentCard",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(PREVIEW_HEIGHT)
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.Chip)
            .clearAndSetSemantics { },
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            previewMap()
            // "Where to" across the top.
            val margin = 8.dp.toPx()
            block(AppColors.Card, margin, margin, size.width - margin * 2, 18.dp.toPx(), radius = 9.dp.toPx())
            textBar(margin + 12.dp.toPx(), margin + 7.5.dp.toPx(), size.width * 0.22f)
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(8.dp)
                .graphicsLayer {
                    alpha = shown.coerceIn(0f, 1f)
                    translationY = (1f - shown) * 14.dp.toPx()
                }
                .clip(RoundedCornerShape(12.dp))
                .background(AppColors.Card)
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // A small calendar sheet.
            Canvas(modifier = Modifier.size(width = 18.dp, height = 20.dp)) {
                block(AppColors.Muted, 0f, 0f, size.width, size.height, radius = 3.dp.toPx(), alpha = 0.3f)
                block(AppColors.DelayRed, 0f, 0f, size.width, size.height * 0.32f, radius = 3.dp.toPx())
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.setup_sample_appointment),
                    color = AppColors.Ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.setup_sample_leave),
                    color = AppColors.Blue,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The radius of "nearby": a circle around the own position, the stops inside it in colour. */
@Composable
internal fun NearbyRadiusPreview(radius: Int) {
    val largest = AppPrefs.NEARBY_RADII.maxOrNull() ?: radius
    val target = radius.toFloat() / largest.coerceAtLeast(1)
    val animate = rememberAnimationsOn()
    val shown by animateFloatAsState(
        target,
        if (animate) spring<Float>(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow) else snap<Float>(),
        label = "nearbyRadius",
    )
    SketchPane(key = Unit) { _ -> nearbyRadiusSketch(shown, largest) }
}

/**
 * A search: through the map of its routes to the connections, or straight to them; the
 * connections at the end as the setting "Connections as" shows them, a chart or a [list].
 */
@Composable
internal fun SkipPlanPreview(skip: Boolean, list: Boolean) {
    SketchPane(key = skip to list) { reveal -> skipPlanSketch(skip, list, reveal) }
}

/** The connections as a chart (columns on a time axis) or as a list. */
@Composable
internal fun ConnectionsViewPreview(list: Boolean) {
    SketchOptions(
        labels = listOf(stringResource(R.string.view_chart), stringResource(R.string.view_list)),
        selected = if (list) 1 else 0,
    ) { index, _ ->
        val inset = 6.dp.toPx()
        if (index == 0) {
            chartSketch(inset, inset, size.width - inset * 2, size.height - inset * 2)
        } else {
            listSketch(inset, inset, size.width - inset * 2, size.height - inset * 2)
        }
    }
}

/** The connection detail under the chart, or on a page of its own. */
@Composable
internal fun ConnectionsFullPreview(full: Boolean) {
    SketchPane(key = full, height = 112.dp) { reveal -> if (full) singleSketch(reveal) else splitSketch(reveal) }
}

/** The change between two rides in the chart: only its minutes, or its stop too. */
@Composable
internal fun ChartChangeStopsPreview(on: Boolean) {
    SketchPane(key = on) { reveal -> chartChangeStopsSketch(on, reveal) }
}

/** The choice of connecting departures at a change, or only the planned one. */
@Composable
internal fun ConnectionChoicePreview(on: Boolean) {
    SketchPane(key = on, height = 72.dp) { reveal -> choiceSketch(choice = on, reveal = reveal) }
}

/** A walk in the companion: its small map unfolded at once, or behind "Show map". */
@Composable
internal fun WalkMapsPreview(on: Boolean) {
    SketchPane(key = on) { reveal -> walkMapsSketch(on, reveal) }
}

/** The train at the platform: with the carriage to board for the shortest way on, or plain. */
@Composable
internal fun BoardingHintsPreview(on: Boolean) {
    SketchPane(key = on) { reveal -> boardingHintsSketch(on, reveal) }
}

/** The reminder before getting off, on the watch too: it buzzes, or stays quiet. */
@Composable
internal fun AlightReminderPreview(on: Boolean) {
    SketchPane(key = on) { reveal -> alightReminderSketch(on, reveal) }
}

// ---------------------------------------------------------------------------------------
// The drawings.
// ---------------------------------------------------------------------------------------

// Fixed screens for "light" and "dark", whatever the app shows now.
private val LIGHT_SCREEN = listOf(Color(0xFFF2F3F5), Color.White, Color(0xFF171A21), Color(0xFF1B4FD8))
private val DARK_SCREEN = listOf(Color(0xFF121418), Color(0xFF22262E), Color(0xFFE4E7EE), Color(0xFF8FA8FF))

// The green of the stop names on the map (MapLayers: STOP_NAME_GREEN), white haloed there too.
private const val MAP_STOP_NAME_GREEN = 0xFF007A3D.toInt()

/*
 * The icons of the map itself, from the very bitmaps the map draws (stopSignBitmap,
 * entranceBitmap, liftBitmap, stopPositionBitmap), made at a smaller density so they fit the
 * small sketches; kept per kind, size and colours, so a frame of an animation makes none anew.
 */
private val previewIcons = HashMap<String, ImageBitmap>()

private fun DrawScope.mapIcon(key: String, size: Float, make: (density: Float) -> android.graphics.Bitmap): ImageBitmap {
    val at = density * size
    return previewIcons.getOrPut("$key@$at") { make(at).asImageBitmap() }
}

/** The colours of the stop signs as the map has them now (see AppRoot). */
private fun previewSignColors(): StopSignColors =
    StopSignColors(ground = AppColors.Card.toArgb(), poi = AppColors.Blue.toArgb(), address = AppColors.Muted.toArgb())

/** A stop sign of the map ("H", "U", "SU", ...), the selected one with its green ring. */
private fun DrawScope.signIcon(sign: String, size: Float, ring: Boolean = false): ImageBitmap {
    val colors = previewSignColors()
    return mapIcon("sign:$sign:$ring:${colors.ground}", size) { stopSignBitmap(sign, it, colors, ring) }
}

/** [image] centred on [center], grown by [grow] about it and faded by [alpha]. */
private fun DrawScope.drawIcon(image: ImageBitmap, center: Offset, alpha: Float = 1f, grow: Float = 1f) {
    val shown = alpha.coerceIn(0f, 1f)
    if (shown <= 0f || grow <= 0f) return
    val topLeft = Offset(center.x - image.width / 2f, center.y - image.height / 2f)
    if (grow == 1f) {
        drawImage(image, topLeft = topLeft, alpha = shown)
    } else {
        scale(grow, pivot = center) { drawImage(image, topLeft = topLeft, alpha = shown) }
    }
}

/** A text as the map writes it: centred on [center], bold, with an optional [halo] around it. */
private fun DrawScope.mapText(text: String, center: Offset, textSize: Float, color: Int, halo: Int? = null, alpha: Float = 1f) {
    val shown = (alpha.coerceIn(0f, 1f) * 255).toInt()
    if (shown <= 0) return
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        this.textSize = textSize
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        textAlign = android.graphics.Paint.Align.CENTER
    }
    val baseline = center.y - (paint.descent() + paint.ascent()) / 2f
    drawIntoCanvas { canvas ->
        if (halo != null) {
            paint.style = android.graphics.Paint.Style.STROKE
            paint.strokeWidth = 3.dp.toPx()
            paint.strokeJoin = android.graphics.Paint.Join.ROUND
            paint.color = halo
            paint.alpha = shown
            canvas.nativeCanvas.drawText(text, center.x, baseline, paint)
        }
        paint.style = android.graphics.Paint.Style.FILL
        paint.color = color
        paint.alpha = shown
        canvas.nativeCanvas.drawText(text, center.x, baseline, paint)
    }
}

// The line names of the sketch colours (SketchLines), for the badges of the vehicles.
private val SKETCH_LINE_NAMES = listOf("S1", "U7", "M10", "U4")

private fun sketchLineName(color: Color): String = SKETCH_LINE_NAMES.getOrElse(SketchLines.indexOf(color)) { "" }

/** The map style chosen in the settings, with "automatic" as the app shows it now. */
private fun previewMapColors(): List<Color> {
    val style = Appearance.mapStyle
    val shown = if (style == MapStyleChoice.Auto) {
        if (AppColors.palette.dark) MapStyleChoice.Dark else MapStyleChoice.Light
    } else {
        style
    }
    return mapStyleColors(shown)
}

/** A piece of map in the chosen style: ground, a river, a park and three roads. */
private fun DrawScope.previewMap(left: Float = 0f, top: Float = 0f, width: Float = size.width, height: Float = size.height) {
    val colors = previewMapColors()
    clipRect(left, top, left + width, top + height) {
        drawRect(colors[0], topLeft = Offset(left, top), size = Size(width, height))
        drawRect(colors[1], topLeft = Offset(left, top + height * 0.74f), size = Size(width, height * 0.12f))
        drawCircle(colors[2], radius = height * 0.24f, center = Offset(left + width * 0.86f, top + height * 0.2f))
        val road = 3.dp.toPx()
        drawLine(colors[3], Offset(left, top + height * 0.38f), Offset(left + width, top + height * 0.5f), strokeWidth = road)
        drawLine(colors[3], Offset(left + width * 0.3f, top), Offset(left + width * 0.4f, top + height), strokeWidth = road)
        drawLine(colors[3], Offset(left + width * 0.68f, top), Offset(left + width * 0.62f, top + height), strokeWidth = road)
    }
}

/** A short bar standing for a line of text. */
private fun DrawScope.textBar(x: Float, y: Float, width: Float, color: Color = AppColors.Muted, alpha: Float = 0.55f, thick: Dp = 3.dp) {
    if (width <= 0f) return
    val height = thick.toPx()
    block(color, x, y, width, height, radius = height / 2, alpha = alpha)
}

/** The own position: a blue dot in a white ring with a soft halo. */
private fun DrawScope.ownPosition(center: Offset) {
    drawCircle(AppColors.Blue.copy(alpha = 0.18f), radius = 11.dp.toPx(), center = center)
    drawCircle(Color.White, radius = 6.dp.toPx(), center = center)
    drawCircle(AppColors.Blue, radius = 4.5.dp.toPx(), center = center)
}

/** A vehicle on the map: a badge in the colour of its line with a white rim and the line's name, as the map draws it. */
private fun DrawScope.vehicleMark(center: Offset, color: Color, scale: Float = 1f, alpha: Float = 1f) {
    val label = sketchLineName(color)
    val height = 12.dp.toPx() * scale
    val width = (label.length.coerceAtLeast(2) * 5.6f + 8f).dp.toPx() * scale
    if (width <= 0f || height <= 0f) return
    val rim = 1.5.dp.toPx() * scale
    block(Color.White, center.x - width / 2 - rim, center.y - height / 2 - rim, width + rim * 2, height + rim * 2, radius = height / 2 + rim, alpha = alpha)
    block(color, center.x - width / 2, center.y - height / 2, width, height, radius = height / 2, alpha = alpha)
    val ink = if (color.luminance() > 0.5f) Color(0xFF14161B) else Color.White
    if (label.isNotEmpty()) mapText(label, center, 8.5.sp.toPx() * scale, ink.toArgb(), alpha = alpha)
}

/** A small arrow in front of a vehicle at [center], pointing along ([dx], [dy]). */
private fun DrawScope.directionArrow(center: Offset, dx: Float, dy: Float, color: Color, alpha: Float) {
    val length = kotlin.math.sqrt(dx * dx + dy * dy)
    if (length <= 0f) return
    val ux = dx / length
    val uy = dy / length
    val reach = 12.dp.toPx()
    val tip = Offset(center.x + ux * (reach + 6.dp.toPx()), center.y + uy * (reach + 6.dp.toPx()))
    val base = Offset(center.x + ux * reach, center.y + uy * reach)
    val half = 4.dp.toPx()
    val arrow = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(base.x - uy * half, base.y + ux * half)
        lineTo(base.x + uy * half, base.y - ux * half)
        close()
    }
    drawPath(arrow, color, alpha = alpha.coerceIn(0f, 1f))
}

/** An arrow between two screens, from [x1] to [x2] at height [y]. */
private fun DrawScope.screenArrow(x1: Float, x2: Float, y: Float, alpha: Float) {
    val color = AppColors.Blue.copy(alpha = alpha.coerceIn(0f, 1f))
    val stroke = 2.dp.toPx()
    val head = 4.dp.toPx()
    drawLine(color, Offset(x1, y), Offset(x2, y), strokeWidth = stroke, cap = StrokeCap.Round)
    drawLine(color, Offset(x2, y), Offset(x2 - head, y - head), strokeWidth = stroke, cap = StrokeCap.Round)
    drawLine(color, Offset(x2, y), Offset(x2 - head, y + head), strokeWidth = stroke, cap = StrokeCap.Round)
}

private fun DrawScope.colorSchemeSketch(index: Int, reveal: Float) {
    val accent = when (index) {
        0 -> Color(0xFF1B4FD8)
        1 -> Color(0xFF65558F)
        else -> Color(Appearance.accent)
    }
    val margin = 7.dp.toPx()
    val width = size.width - margin * 2
    val height = size.height - margin * 2
    block(AppColors.Card, margin, margin, width, height, radius = 10.dp.toPx())
    // The header of the card, drawn out as the scheme is chosen.
    block(accent, margin, margin, width * (0.35f + 0.65f * reveal.coerceIn(0f, 1f)), 14.dp.toPx(), radius = 10.dp.toPx())
    textBar(margin + 8.dp.toPx(), margin + 5.5.dp.toPx(), width * 0.3f, color = Color.White, alpha = 0.85f)
    // System colours come from the wallpaper: a small picture of it in the corner.
    if (index == 1) {
        drawCircle(
            brush = Brush.linearGradient(listOf(Color(0xFFB9A7E8), Color(0xFF65558F))),
            radius = 5.dp.toPx(),
            center = Offset(margin + width - 8.dp.toPx(), margin + 7.dp.toPx()),
        )
    }
    val rowHeight = 12.dp.toPx()
    for (row in 0..1) {
        val y = margin + 20.dp.toPx() + row * (rowHeight + 5.dp.toPx())
        block(SketchLines[row * 2], margin + 7.dp.toPx(), y, 16.dp.toPx(), rowHeight, radius = 3.dp.toPx())
        textBar(margin + 28.dp.toPx(), y + 2.dp.toPx(), width * 0.36f)
        textBar(margin + 28.dp.toPx(), y + 7.dp.toPx(), width * 0.2f, alpha = 0.3f)
        block(accent.copy(alpha = 0.2f), margin + width - 30.dp.toPx(), y, 22.dp.toPx(), rowHeight, radius = rowHeight / 2)
    }
    drawCircle(accent, radius = 7.dp.toPx(), center = Offset(margin + width - 11.dp.toPx(), margin + height - 11.dp.toPx()))
}

private fun DrawScope.miniScreen(colors: List<Color>, reveal: Float) {
    val (ground, card, ink, blue) = colors
    drawRect(ground)
    val margin = 7.dp.toPx()
    val width = size.width - margin * 2
    block(card, margin, margin + 14.dp.toPx(), width, 18.dp.toPx(), radius = 9.dp.toPx())
    textBar(margin + 10.dp.toPx(), margin + 21.5.dp.toPx(), width * 0.4f, color = ink, alpha = 0.6f)
    val cardTop = margin + 38.dp.toPx()
    val cardHeight = size.height - cardTop - margin
    block(card, margin, cardTop + (1f - reveal) * 6.dp.toPx(), width, cardHeight, radius = 8.dp.toPx())
    block(SketchLines[1], margin + 7.dp.toPx(), cardTop + 7.dp.toPx(), 14.dp.toPx(), 9.dp.toPx(), radius = 2.dp.toPx())
    textBar(margin + 26.dp.toPx(), cardTop + 10.dp.toPx(), width * 0.42f, color = ink, alpha = 0.5f)
    drawCircle(blue, radius = 5.dp.toPx(), center = Offset(margin + width - 11.dp.toPx(), cardTop + 11.5.dp.toPx()))
}

private fun DrawScope.sun(center: Offset, color: Color) {
    drawCircle(color, radius = 3.5.dp.toPx(), center = center)
    for (ray in 0 until 8) {
        val angle = ray * Math.PI / 4
        val dx = kotlin.math.cos(angle).toFloat()
        val dy = kotlin.math.sin(angle).toFloat()
        drawLine(
            color,
            Offset(center.x + dx * 5.dp.toPx(), center.y + dy * 5.dp.toPx()),
            Offset(center.x + dx * 6.5.dp.toPx(), center.y + dy * 6.5.dp.toPx()),
            strokeWidth = 1.2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.moon(center: Offset, color: Color, ground: Color) {
    drawCircle(color, radius = 5.dp.toPx(), center = center)
    drawCircle(ground, radius = 4.2.dp.toPx(), center = Offset(center.x + 2.5.dp.toPx(), center.y - 1.5.dp.toPx()))
}

private fun DrawScope.darkModeSketch(index: Int, reveal: Float) {
    val iconY = 10.dp.toPx()
    when (index) {
        0 -> {
            miniScreen(LIGHT_SCREEN, reveal)
            sun(Offset(size.width / 2, iconY), Color(0xFFF0B400))
        }
        1 -> {
            miniScreen(DARK_SCREEN, reveal)
            moon(Offset(size.width / 2, iconY), DARK_SCREEN[2], DARK_SCREEN[0])
        }
        else -> {
            clipRect(right = size.width / 2) { miniScreen(LIGHT_SCREEN, reveal) }
            clipRect(left = size.width / 2) { miniScreen(DARK_SCREEN, reveal) }
            sun(Offset(size.width / 2 - 10.dp.toPx(), iconY), Color(0xFFF0B400))
            moon(Offset(size.width / 2 + 10.dp.toPx(), iconY), DARK_SCREEN[2], DARK_SCREEN[0])
        }
    }
}

private fun DrawScope.widgetSketch(material: Boolean, reveal: Float) {
    // A home screen: the wallpaper, and the widget in the middle of it.
    drawRect(Brush.linearGradient(listOf(Color(0xFF34618F), Color(0xFF8DB7D9))))
    val dark = AppColors.palette.dark
    val ground = when {
        material && dark -> Color(0xFF4A4458)
        material -> Color(0xFFE8DEF8)
        else -> AppColors.Card
    }
    val ink = when {
        material && dark -> Color(0xFFE8DEF8)
        material -> Color(0xFF1D192B)
        else -> AppColors.Ink
    }
    val margin = 10.dp.toPx()
    val width = size.width - margin * 2
    val height = size.height - margin * 2
    val drop = (1f - reveal) * 6.dp.toPx()
    val radius = if (material) 16.dp.toPx() else 11.dp.toPx()
    block(ground, margin, margin + drop, width, height, radius = radius)
    val inner = 9.dp.toPx()
    textBar(margin + inner, margin + inner + drop, width * 0.45f, color = ink, alpha = 0.75f, thick = 4.dp)
    for (row in 0..1) {
        val y = margin + inner + 12.dp.toPx() + row * 16.dp.toPx() + drop
        val badge = if (material) ink.copy(alpha = 0.75f) else SketchLines[row]
        block(badge, margin + inner, y, 16.dp.toPx(), 10.dp.toPx(), radius = 3.dp.toPx())
        textBar(margin + inner + 21.dp.toPx(), y + 3.5.dp.toPx(), width * 0.3f, color = ink, alpha = 0.45f)
        val pill = if (material) ink.copy(alpha = 0.18f) else AppColors.Lime
        block(pill, margin + width - inner - 20.dp.toPx(), y, 20.dp.toPx(), 10.dp.toPx(), radius = 5.dp.toPx())
    }
}

private fun DrawScope.vehicleDirectionSketch(arrow: Boolean, reveal: Float) {
    previewMap()
    val start = Offset(size.width * 0.06f, size.height * 0.84f)
    val end = Offset(size.width * 0.94f, size.height * 0.16f)
    val line = SketchLines[0]
    drawLine(line, start, end, strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round, alpha = 0.8f)
    val at = 0.32f + 0.18f * reveal
    val vehicle = Offset(start.x + (end.x - start.x) * at, start.y + (end.y - start.y) * at)
    if (arrow) directionArrow(vehicle, end.x - start.x, end.y - start.y, line, alpha = reveal)
    vehicleMark(vehicle, line)
    // A second vehicle the other way, on a tram line.
    val tramStart = Offset(size.width * 0.1f, size.height * 0.3f)
    val tramEnd = Offset(size.width * 0.9f, size.height * 0.62f)
    drawLine(SketchLines[2], tramStart, tramEnd, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round, alpha = 0.7f)
    val tramAt = 0.78f - 0.14f * reveal
    val tram = Offset(tramStart.x + (tramEnd.x - tramStart.x) * tramAt, tramStart.y + (tramEnd.y - tramStart.y) * tramAt)
    if (arrow) directionArrow(tram, tramStart.x - tramEnd.x, tramStart.y - tramEnd.y, SketchLines[2], alpha = reveal)
    vehicleMark(tram, SketchLines[2])
}

private fun DrawScope.startVehiclesSketch(on: Boolean, reveal: Float) {
    previewMap()
    val me = Offset(size.width * 0.5f, size.height * 0.55f)
    if (on) {
        val spots = listOf(
            Offset(size.width * 0.22f, size.height * 0.36f) to SketchLines[0],
            Offset(size.width * 0.74f, size.height * 0.3f) to SketchLines[2],
            Offset(size.width * 0.64f, size.height * 0.78f) to SketchLines[1],
            Offset(size.width * 0.34f, size.height * 0.8f) to SketchLines[3],
        )
        spots.forEachIndexed { index, (spot, color) ->
            // One after the other, each with a little pop.
            val own = ((reveal - index * 0.12f) / 0.64f).coerceIn(0f, 1.15f)
            vehicleMark(spot, color, scale = own)
        }
    }
    ownPosition(me)
}

/** A lift as the map shows it (liftBitmap): two white triangles, up and down, on a grey square. */
private fun DrawScope.liftMark(center: Offset, alpha: Float, escalator: Boolean = false) {
    drawIcon(mapIcon("lift:$escalator", 0.85f) { liftBitmap(it, escalator) }, center, alpha)
}

/** An entrance of a station as the map shows it (entranceBitmap): "U ↗" on the blue of the U-Bahn. */
private fun DrawScope.entranceMark(center: Offset, alpha: Float, scale: Float = 1f, sign: String = "U") {
    drawIcon(mapIcon("entrance:$sign", 0.75f) { entranceBitmap(sign, it) }, center, alpha, grow = scale)
}

private fun DrawScope.stationDetailsSketch(on: Boolean, reveal: Float) {
    previewMap()
    // The station: a building with the platform under it.
    val left = size.width * 0.3f
    val top = size.height * 0.3f
    val width = size.width * 0.4f
    val height = size.height * 0.36f
    block(AppColors.Muted, left, top, width, height, radius = 6.dp.toPx(), alpha = 0.22f)
    val center = Offset(left + width / 2, top + height / 2)
    if (!on) {
        // Far out: only the sign of the station, as on the map.
        drawIcon(signIcon("SU", 0.7f), center)
        return
    }
    val alpha = reveal.coerceIn(0f, 1f)
    drawIcon(signIcon("SU", 0.55f), Offset(center.x, top + height * 0.36f))
    // The stop positions of buses and trams beside it, with their lines (stopPositionBitmap).
    listOf(
        0.14f to OsmLine("M10", "tram"),
        0.86f to OsmLine("100", "bus"),
    ).forEach { (x, line) ->
        val image = mapIcon("pos:${line.name}", 0.8f) { stopPositionBitmap(null, listOf(line), it) }
        drawIcon(image, Offset(size.width * x, size.height * 0.48f), alpha)
    }
    // Entrances at the corners, a passage through a building, a lift and an escalator.
    val grow = reveal.coerceIn(0f, 1.15f)
    entranceMark(Offset(left, top), alpha, grow)
    entranceMark(Offset(left + width, top), alpha, grow)
    entranceMark(Offset(left, top + height), alpha, grow)
    entranceMark(Offset(left + width, top + height), alpha, grow)
    drawLine(
        color = SketchLines[1].copy(alpha = alpha * 0.7f),
        start = Offset(left + width, top + height),
        end = Offset(left + width + 16.dp.toPx(), top + height + 14.dp.toPx()),
        strokeWidth = 1.5.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
    )
    liftMark(Offset(center.x - 9.dp.toPx(), top + height * 0.78f), alpha)
    liftMark(Offset(center.x + 9.dp.toPx(), top + height * 0.78f), alpha, escalator = true)
}

/** A station for the sketch of the entrances: a building, its ring when marked, entrances and a lift. */
private fun DrawScope.entranceStation(center: Offset, marked: Boolean, entrances: Boolean, lift: Boolean, reveal: Float) {
    val width = size.width * 0.3f
    val height = size.height * 0.34f
    val left = center.x - width / 2
    val top = center.y - height / 2
    block(AppColors.Muted, left, top, width, height, radius = 5.dp.toPx(), alpha = 0.22f)
    val alpha = reveal.coerceIn(0f, 1f)
    if (entrances) {
        entranceMark(Offset(left, top), alpha)
        entranceMark(Offset(left + width, top + height), alpha)
        entranceMark(Offset(left, top + height), alpha)
    }
    if (lift) liftMark(Offset(left + width, top), alpha)
    // The sign of the station on top, the selected one with its green ring, as on the map.
    drawIcon(signIcon("U", 0.5f, ring = marked), center)
}

private fun DrawScope.entranceModeSketch(mode: EntranceMode, reveal: Float) {
    previewMap()
    val marked = Offset(size.width * 0.28f, size.height * 0.46f)
    val other = Offset(size.width * 0.74f, size.height * 0.56f)
    when (mode) {
        // Entrances from close up, lifts nearer or at the marked station.
        EntranceMode.Auto -> {
            entranceStation(marked, marked = true, entrances = true, lift = true, reveal = reveal)
            entranceStation(other, marked = false, entrances = true, lift = false, reveal = reveal)
        }
        EntranceMode.Always -> {
            entranceStation(marked, marked = true, entrances = true, lift = true, reveal = reveal)
            entranceStation(other, marked = false, entrances = true, lift = true, reveal = reveal)
        }
        EntranceMode.MarkedOnly -> {
            entranceStation(marked, marked = true, entrances = true, lift = true, reveal = reveal)
            entranceStation(other, marked = false, entrances = false, lift = false, reveal = reveal)
        }
    }
}

// Names for the stops of the sketch of the stop names.
private val SKETCH_STOP_NAMES = listOf("Rathaus", "Markt", "Parkstr.", "Kirche")

private fun DrawScope.stopNamesSketch(on: Boolean, reveal: Float) {
    previewMap()
    val stops = listOf(
        Offset(size.width * 0.18f, size.height * 0.4f),
        Offset(size.width * 0.5f, size.height * 0.24f),
        Offset(size.width * 0.58f, size.height * 0.64f),
        Offset(size.width * 0.84f, size.height * 0.5f),
    )
    val played = reveal.coerceIn(0f, 1f)
    stops.forEachIndexed { index, stop ->
        if (on) {
            // The name in green, white haloed, centred on the stop, as the map writes it.
            mapText(SKETCH_STOP_NAMES[index], stop, 11.sp.toPx(), MAP_STOP_NAME_GREEN, halo = android.graphics.Color.WHITE, alpha = played)
        } else {
            // The sign of a bus or tram stop: the yellow H on green, as the map draws it.
            drawIcon(signIcon("H", 0.6f), stop)
        }
    }
}

private fun DrawScope.mapNoticesSketch(on: Boolean, reveal: Float) {
    previewMap()
    val station = Offset(size.width * 0.34f, size.height * 0.5f)
    val stop = Offset(size.width * 0.72f, size.height * 0.36f)
    if (on) {
        // The sign at the upper right of the station, as the map puts it, grown in; below the
        // station's own sign, as on the map.
        val colors = noticeMarkColors()
        val image = mapIcon("notice:${colors.ground}:${colors.warning}", 0.6f) { noticeMarkBitmap(it, colors, null, null, null) }
        val corner = Offset(station.x + 8.dp.toPx(), station.y - 8.dp.toPx())
        drawIcon(image, corner, alpha = reveal, grow = reveal.coerceIn(0f, 1.15f))
    }
    drawIcon(signIcon("U", 0.5f), station)
    drawIcon(signIcon("H", 0.6f), stop)
}

private fun DrawScope.vehicleHintSketch(on: Boolean, reveal: Float) {
    previewMap()
    vehicleMark(Offset(size.width * 0.3f, size.height * 0.3f), SketchLines[0])
    vehicleMark(Offset(size.width * 0.7f, size.height * 0.38f), SketchLines[2])
    if (!on) return
    val width = size.width * 0.66f
    val height = 18.dp.toPx()
    val left = (size.width - width) / 2
    val top = size.height - height - 8.dp.toPx() + (1f - reveal) * 14.dp.toPx()
    val alpha = reveal.coerceIn(0f, 1f)
    block(AppColors.Card, left, top, width, height, radius = height / 2, alpha = alpha * 0.95f)
    drawCircle(
        color = AppColors.Muted.copy(alpha = alpha),
        radius = 4.dp.toPx(),
        center = Offset(left + 12.dp.toPx(), top + height / 2),
        style = Stroke(width = 1.2.dp.toPx()),
    )
    textBar(left + 22.dp.toPx(), top + height / 2 - 1.5.dp.toPx(), width - 32.dp.toPx(), alpha = 0.55f * alpha)
}

private fun DrawScope.fitLineSketch(on: Boolean, reveal: Float) {
    previewMap()
    val points = listOf(
        Offset(size.width * 0.06f, size.height * 0.78f),
        Offset(size.width * 0.28f, size.height * 0.6f),
        Offset(size.width * 0.48f, size.height * 0.62f),
        Offset(size.width * 0.7f, size.height * 0.34f),
        Offset(size.width * 0.94f, size.height * 0.22f),
    )
    val line = SketchLines[2]
    val path = Path().apply {
        moveTo(points[0].x, points[0].y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
    }
    drawPath(path, line, style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round))
    points.forEach { stop ->
        drawCircle(Color.White, radius = 3.dp.toPx(), center = stop)
        drawCircle(line, radius = 3.dp.toPx(), center = stop, style = Stroke(width = 1.5.dp.toPx()))
    }
    val vehicle = Offset((points[1].x + points[2].x) / 2, (points[1].y + points[2].y) / 2)
    vehicleMark(vehicle, line)
    // What the screen shows: close around the vehicle, or out until the whole line fits.
    val tight = floatArrayOf(vehicle.x - size.width * 0.17f, vehicle.y - size.height * 0.26f, size.width * 0.34f, size.height * 0.52f)
    val margin = 4.dp.toPx()
    val whole = floatArrayOf(margin, margin, size.width - margin * 2, size.height - margin * 2)
    val t = if (on) reveal.coerceIn(0f, 1f) else 0f
    val frame = FloatArray(4) { tight[it] + (whole[it] - tight[it]) * t }
    val shade = Color.Black.copy(alpha = 0.22f)
    drawRect(shade, topLeft = Offset.Zero, size = Size(size.width, frame[1]))
    drawRect(shade, topLeft = Offset(0f, frame[1] + frame[3]), size = Size(size.width, size.height - frame[1] - frame[3]))
    drawRect(shade, topLeft = Offset(0f, frame[1]), size = Size(frame[0], frame[3]))
    drawRect(shade, topLeft = Offset(frame[0] + frame[2], frame[1]), size = Size(size.width - frame[0] - frame[2], frame[3]))
    drawRoundRect(
        color = AppColors.Blue,
        topLeft = Offset(frame[0], frame[1]),
        size = Size(frame[2], frame[3]),
        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
        style = Stroke(width = 2.dp.toPx()),
    )
}

private fun DrawScope.startLayoutSketch(classic: Boolean, reveal: Float) {
    previewMap()
    ownPosition(Offset(size.width * 0.5f, size.height * 0.3f))
    val margin = 6.dp.toPx()
    val width = size.width - margin * 2
    if (!classic) {
        // One sheet to pull up: a handle, the field "Where to" and the rows below it.
        val top = size.height * (0.44f + (1f - reveal) * 0.3f)
        block(AppColors.Card, 0f, top, size.width, size.height - top + 12.dp.toPx(), radius = 12.dp.toPx())
        textBar(size.width / 2 - 8.dp.toPx(), top + 4.dp.toPx(), 16.dp.toPx(), alpha = 0.4f)
        block(AppColors.Chip, margin, top + 10.dp.toPx(), width, 12.dp.toPx(), radius = 6.dp.toPx())
        for (row in 0..1) {
            val y = top + 27.dp.toPx() + row * 10.dp.toPx()
            block(SketchLines[row], margin, y, 12.dp.toPx(), 7.dp.toPx(), radius = 2.dp.toPx())
            textBar(margin + 16.dp.toPx(), y + 2.dp.toPx(), width * 0.5f)
        }
    } else {
        // The parts float one by one over the map.
        val alpha = reveal.coerceIn(0f, 1f)
        block(AppColors.Card, margin, margin, width, 13.dp.toPx(), radius = 6.5.dp.toPx())
        textBar(margin + 8.dp.toPx(), margin + 5.dp.toPx(), width * 0.4f)
        val cardWidth = (width - 5.dp.toPx()) / 2
        val cardHeight = 24.dp.toPx()
        val cardTop = size.height - margin - cardHeight + (1f - reveal) * 8.dp.toPx()
        for (card in 0..1) {
            val x = margin + card * (cardWidth + 5.dp.toPx())
            block(AppColors.Card, x, cardTop, cardWidth, cardHeight, radius = 8.dp.toPx(), alpha = alpha)
            block(SketchLines[card + 1], x + 5.dp.toPx(), cardTop + 5.dp.toPx(), 12.dp.toPx(), 7.dp.toPx(), radius = 2.dp.toPx(), alpha = alpha)
            textBar(x + 5.dp.toPx(), cardTop + 16.dp.toPx(), cardWidth * 0.6f, alpha = 0.5f * alpha)
        }
    }
}

// The share of the width of the start page sketch left free beside its parts.
private const val START_FREE = 0.38f

/** The plain ground of the start page without its map, with the quiet mark of the app in the free part. */
private fun DrawScope.calmGround(alpha: Float = 1f) {
    drawRect(AppColors.PlainGround, alpha = alpha)
    val free = size.width * START_FREE
    val width = minOf(free * 0.72f, size.height * 0.9f)
    drawStopMark(Offset(free / 2, size.height / 2), width, stopMarkTone().copy(alpha = alpha))
}

private fun DrawScope.startPageSketch(
    part: StartPart,
    map: Boolean,
    cards: Boolean,
    departures: Boolean,
    calendar: Boolean,
    reveal: Float,
) {
    val played = reveal.coerceIn(0f, 1f)
    // The ground: the map, or a calm one that spares the battery; the change fades over.
    if (part == StartPart.Map) {
        previewMap()
        calmGround(alpha = if (map) 1f - played else played)
    } else if (map) {
        previewMap()
    } else {
        calmGround()
    }
    // The parts on the right; the left stays free for the map or the mark of the app.
    val left = size.width * START_FREE
    val width = size.width - left - 8.dp.toPx()
    var y = 6.dp.toPx()
    val gap = 5.dp.toPx()
    // The part of this row comes in; the others stand.
    fun alphaOf(own: StartPart) = if (own == part) played else 1f
    // "Where to".
    block(AppColors.Card, left, y, width, 14.dp.toPx(), radius = 7.dp.toPx())
    textBar(left + 9.dp.toPx(), y + 5.5.dp.toPx(), width * 0.3f)
    y += 14.dp.toPx() + gap
    if (cards) {
        val alpha = alphaOf(StartPart.Cards)
        val tile = (width - gap * 2) / 3
        for (index in 0..2) {
            val x = left + index * (tile + gap)
            block(AppColors.Card, x, y, tile, 20.dp.toPx(), radius = 6.dp.toPx(), alpha = alpha)
            drawCircle(AppColors.Blue.copy(alpha = alpha), radius = 3.dp.toPx(), center = Offset(x + 7.dp.toPx(), y + 7.dp.toPx()))
            textBar(x + 4.dp.toPx(), y + 13.dp.toPx(), tile * 0.6f, alpha = 0.5f * alpha)
        }
        y += 20.dp.toPx() + gap
    }
    if (departures) {
        val alpha = alphaOf(StartPart.Departures)
        val height = 26.dp.toPx()
        block(AppColors.Card, left, y, width, height, radius = 7.dp.toPx(), alpha = alpha)
        for (row in 0..1) {
            val rowY = y + 5.dp.toPx() + row * 9.dp.toPx()
            block(SketchLines[row * 2], left + 6.dp.toPx(), rowY, 12.dp.toPx(), 7.dp.toPx(), radius = 2.dp.toPx(), alpha = alpha)
            textBar(left + 22.dp.toPx(), rowY + 2.dp.toPx(), width * 0.4f, alpha = 0.5f * alpha)
            block(AppColors.Lime, left + width - 20.dp.toPx(), rowY, 14.dp.toPx(), 7.dp.toPx(), radius = 3.5.dp.toPx(), alpha = alpha)
        }
        y += height + gap
    }
    if (calendar) {
        val alpha = alphaOf(StartPart.Calendar)
        val height = 16.dp.toPx()
        if (y + height <= size.height) {
            block(AppColors.Card, left, y, width, height, radius = 6.dp.toPx(), alpha = alpha)
            // A small calendar sheet, the appointment and when to leave.
            block(AppColors.DelayRed, left + 6.dp.toPx(), y + 4.dp.toPx(), 8.dp.toPx(), 3.dp.toPx(), radius = 1.dp.toPx(), alpha = alpha)
            block(AppColors.Muted, left + 6.dp.toPx(), y + 7.dp.toPx(), 8.dp.toPx(), 5.dp.toPx(), radius = 1.dp.toPx(), alpha = 0.35f * alpha)
            textBar(left + 19.dp.toPx(), y + 6.5.dp.toPx(), width * 0.38f, alpha = 0.55f * alpha)
            block(AppColors.Blue, left + width - 26.dp.toPx(), y + 4.dp.toPx(), 20.dp.toPx(), 8.dp.toPx(), radius = 4.dp.toPx(), alpha = alpha)
        }
    }
}

// Stops of the sketch of "nearby": how far away (in metres) and in which direction.
private val NEARBY_STOPS = listOf(
    180f to 0.4f, 260f to 2.3f, 340f to 4.1f, 420f to 5.4f, 560f to 1.2f, 640f to 3.2f, 720f to 4.9f, 900f to 0.1f, 980f to 2.8f,
)

private fun DrawScope.nearbyRadiusSketch(fraction: Float, largest: Int) {
    previewMap()
    val center = Offset(size.width / 2, size.height / 2)
    val full = size.height * 0.44f
    val radius = full * fraction.coerceIn(0f, 1.2f)
    drawCircle(AppColors.Blue.copy(alpha = 0.1f), radius = radius, center = center)
    drawCircle(
        color = AppColors.Blue.copy(alpha = 0.7f),
        radius = radius,
        center = center,
        style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
    )
    NEARBY_STOPS.forEachIndexed { index, (metres, angle) ->
        val distance = full * metres / largest.coerceAtLeast(1)
        val stop = Offset(center.x + kotlin.math.cos(angle) * distance, center.y + kotlin.math.sin(angle) * distance)
        val inside = metres / largest.coerceAtLeast(1) <= fraction
        if (inside) {
            drawCircle(Color.White, radius = 4.5.dp.toPx(), center = stop)
            drawCircle(SketchLines[index % SketchLines.size], radius = 3.5.dp.toPx(), center = stop)
        } else {
            drawCircle(AppColors.Muted.copy(alpha = 0.4f), radius = 3.dp.toPx(), center = stop)
        }
    }
    ownPosition(center)
}

/** A screen of a small phone with the field "Where to" and the start and destination below. */
private fun DrawScope.searchScreen(screen: FloatArray) {
    val (left, top, width, height) = screen.toList()
    block(AppColors.Card, left + 2.dp.toPx(), top + 2.dp.toPx(), width - 4.dp.toPx(), height * 0.3f, radius = 4.dp.toPx())
    drawCircle(AppColors.Blue, radius = 2.dp.toPx(), center = Offset(left + 7.dp.toPx(), top + 2.dp.toPx() + height * 0.1f))
    drawCircle(AppColors.DelayRed, radius = 2.dp.toPx(), center = Offset(left + 7.dp.toPx(), top + 2.dp.toPx() + height * 0.22f))
    textBar(left + 12.dp.toPx(), top + 2.dp.toPx() + height * 0.1f - 1.5.dp.toPx(), width * 0.5f)
    textBar(left + 12.dp.toPx(), top + 2.dp.toPx() + height * 0.22f - 1.5.dp.toPx(), width * 0.4f)
    block(AppColors.Blue, left + width * 0.2f, top + height - 12.dp.toPx(), width * 0.6f, 8.dp.toPx(), radius = 4.dp.toPx())
}

/** A screen with the map of the routes of a search. */
private fun DrawScope.planScreen(screen: FloatArray) {
    val (left, top, width, height) = screen.toList()
    previewMap(left, top, width, height)
    val from = Offset(left + width * 0.2f, top + height * 0.82f)
    val to = Offset(left + width * 0.8f, top + height * 0.16f)
    val routeA = Path().apply {
        moveTo(from.x, from.y)
        lineTo(left + width * 0.2f, top + height * 0.4f)
        lineTo(to.x, to.y)
    }
    val routeB = Path().apply {
        moveTo(from.x, from.y)
        lineTo(left + width * 0.75f, top + height * 0.7f)
        lineTo(to.x, to.y)
    }
    drawPath(routeB, SketchLines[2], alpha = 0.55f, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    drawPath(routeA, AppColors.Blue, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
    drawCircle(AppColors.Blue, radius = 2.5.dp.toPx(), center = from)
    drawCircle(AppColors.DelayRed, radius = 2.5.dp.toPx(), center = to)
}

private fun DrawScope.skipPlanSketch(skip: Boolean, list: Boolean, reveal: Float) {
    val phoneHeight = size.height - 12.dp.toPx()
    val phoneWidth = phoneHeight * 0.5f
    val arrowRoom = 20.dp.toPx()
    val phones = if (skip) 2 else 3
    val total = phones * phoneWidth + (phones - 1) * arrowRoom
    var x = (size.width - total) / 2
    val top = 6.dp.toPx()
    val arrowY = top + phoneHeight / 2
    searchScreen(phoneFrame(x, top, phoneWidth, phoneHeight))
    if (!skip) {
        screenArrow(x + phoneWidth + 4.dp.toPx(), x + phoneWidth + arrowRoom - 4.dp.toPx(), arrowY, 1f)
        x += phoneWidth + arrowRoom
        planScreen(phoneFrame(x, top, phoneWidth, phoneHeight))
    }
    screenArrow(x + phoneWidth + 4.dp.toPx(), x + phoneWidth + arrowRoom - 4.dp.toPx(), arrowY, reveal)
    x += phoneWidth + arrowRoom
    val result = phoneFrame(x + (1f - reveal.coerceIn(0f, 1f)) * 6.dp.toPx(), top, phoneWidth, phoneHeight)
    if (list) {
        listSketch(result[0], result[1], result[2], result[3])
    } else {
        chartSketch(result[0], result[1], result[2], result[3])
    }
}

private fun DrawScope.chartChangeStopsSketch(on: Boolean, reveal: Float) {
    val columns = 4
    val gap = size.width * 0.04f
    val columnWidth = (size.width - gap * (columns + 1)) / columns
    for (line in 1..3) {
        val y = size.height * line / 4f
        drawLine(AppColors.Grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
    }
    val rides = listOf(
        Triple(0.06f, 0.4f, 0.56f) to (0 to 1),
        Triple(0.14f, 0.46f, 0.64f) to (2 to 3),
        Triple(0.24f, 0.52f, 0.7f) to (1 to 0),
        Triple(0.32f, 0.6f, 0.78f) to (3 to 2),
    )
    rides.forEachIndexed { column, (times, lines) ->
        val (start, change, next) = times
        val x = gap + column * (columnWidth + gap)
        val end = (next + 0.34f).coerceAtMost(0.96f)
        block(SketchLines[lines.first], x, size.height * start, columnWidth, size.height * (change - start))
        block(SketchLines[lines.second], x, size.height * next, columnWidth, size.height * (end - next))
        // In the gap of the change: its minutes, and with the setting the name of its stop.
        val gapTop = size.height * change
        val gapHeight = size.height * (next - change)
        val inner = columnWidth * 0.12f
        if (on) {
            val alpha = reveal.coerceIn(0f, 1f)
            textBar(x + inner, gapTop + gapHeight * 0.22f, (columnWidth - inner * 2) * (0.5f + 0.5f * alpha), color = AppColors.Ink, alpha = 0.7f * alpha + 0.1f, thick = 3.dp)
            textBar(x + inner, gapTop + gapHeight * 0.6f, (columnWidth - inner * 2) * 0.4f, alpha = 0.6f, thick = 2.5.dp)
        } else {
            textBar(x + columnWidth * 0.3f, gapTop + gapHeight * 0.4f, columnWidth * 0.4f, alpha = 0.6f, thick = 2.5.dp)
        }
    }
}

private fun DrawScope.walkMapsSketch(on: Boolean, reveal: Float) {
    val margin = 8.dp.toPx()
    val width = size.width - margin * 2
    block(AppColors.Card, margin, margin, width, size.height - margin * 2, radius = 10.dp.toPx())
    // The walk: a dotted rail with its two ends and the text beside it.
    val railX = margin + 14.dp.toPx()
    val railTop = margin + 10.dp.toPx()
    val railBottom = size.height - margin - 10.dp.toPx()
    drawLine(
        color = AppColors.Muted,
        start = Offset(railX, railTop),
        end = Offset(railX, railBottom),
        strokeWidth = 2.5.dp.toPx(),
        cap = StrokeCap.Round,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.1f, 5.dp.toPx())),
    )
    textBar(railX + 10.dp.toPx(), railTop, width * 0.32f, color = AppColors.Ink, alpha = 0.6f, thick = 4.dp)
    textBar(railX + 10.dp.toPx(), railTop + 9.dp.toPx(), width * 0.22f)
    if (on) {
        // The small map, unfolded at once.
        val mapLeft = margin + width * 0.44f
        val mapTop = margin + 6.dp.toPx()
        val mapWidth = width * 0.56f - 6.dp.toPx()
        val mapHeight = (size.height - margin * 2 - 12.dp.toPx()) * (0.4f + 0.6f * reveal.coerceIn(0f, 1f))
        clipRect(mapLeft, mapTop, mapLeft + mapWidth, mapTop + mapHeight) {
            previewMap(mapLeft, mapTop, mapWidth, size.height - margin * 2 - 12.dp.toPx())
            val from = Offset(mapLeft + mapWidth * 0.18f, mapTop + mapHeight * 0.75f)
            val to = Offset(mapLeft + mapWidth * 0.82f, mapTop + mapHeight * 0.28f)
            drawLine(
                color = AppColors.Blue,
                start = from,
                end = to,
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.1f, 5.dp.toPx())),
            )
            drawCircle(AppColors.Blue, radius = 3.dp.toPx(), center = from)
            drawCircle(SketchLines[0], radius = 3.dp.toPx(), center = to)
        }
    } else {
        // "Show map" as a link, folded.
        textBar(railX + 10.dp.toPx(), railBottom - 4.dp.toPx(), width * 0.24f, color = AppColors.Blue, alpha = 0.9f, thick = 4.dp)
    }
}

private fun DrawScope.boardingHintsSketch(on: Boolean, reveal: Float) {
    val margin = 10.dp.toPx()
    val platformTop = size.height * 0.58f
    block(AppColors.Muted, margin, platformTop, size.width - margin * 2, size.height * 0.2f, radius = 4.dp.toPx(), alpha = 0.22f)
    // The train: six carriages over the edge of the platform.
    val carriages = 6
    val gap = 3.dp.toPx()
    val carriageWidth = (size.width - margin * 2 - gap * (carriages - 1)) / carriages
    val carriageTop = size.height * 0.34f
    val carriageHeight = size.height * 0.2f
    val best = 4
    val alpha = reveal.coerceIn(0f, 1f)
    for (index in 0 until carriages) {
        val x = margin + index * (carriageWidth + gap)
        block(SketchLines[0], x, carriageTop, carriageWidth, carriageHeight, radius = 4.dp.toPx())
        block(Color.White, x + carriageWidth * 0.15f, carriageTop + carriageHeight * 0.25f, carriageWidth * 0.7f, carriageHeight * 0.28f, radius = 2.dp.toPx(), alpha = 0.6f)
        if (on && index == best) {
            drawRoundRect(
                color = AppColors.Blue.copy(alpha = alpha),
                topLeft = Offset(x - 2.dp.toPx(), carriageTop - 2.dp.toPx()),
                size = Size(carriageWidth + 4.dp.toPx(), carriageHeight + 4.dp.toPx()),
                cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
                style = Stroke(width = 2.5.dp.toPx()),
            )
        }
    }
    if (!on) return
    val bestX = margin + best * (carriageWidth + gap) + carriageWidth / 2
    // The marker above the carriage drops in.
    val markerY = carriageTop - 6.dp.toPx() - (1f - alpha) * 8.dp.toPx()
    val marker = Path().apply {
        moveTo(bestX, markerY)
        lineTo(bestX - 5.dp.toPx(), markerY - 7.dp.toPx())
        lineTo(bestX + 5.dp.toPx(), markerY - 7.dp.toPx())
        close()
    }
    drawPath(marker, AppColors.Blue, alpha = alpha)
    // The stairs to the connection on the platform below it, and the short way there.
    val stairsLeft = bestX + carriageWidth * 0.2f
    val stairsTop = platformTop + 4.dp.toPx()
    for (step in 0..2) {
        block(
            AppColors.Ink,
            stairsLeft + step * 4.dp.toPx(),
            stairsTop + (2 - step) * 3.dp.toPx(),
            4.dp.toPx(),
            (step + 1) * 3.dp.toPx(),
            radius = 1.dp.toPx(),
            alpha = 0.7f * alpha,
        )
    }
    drawLine(
        color = AppColors.Blue.copy(alpha = alpha),
        start = Offset(bestX, carriageTop + carriageHeight + 2.dp.toPx()),
        end = Offset(stairsLeft, stairsTop + 6.dp.toPx()),
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
    )
}

private fun DrawScope.alightReminderSketch(on: Boolean, reveal: Float) {
    val center = Offset(size.width * 0.5f, size.height * 0.5f)
    val radius = size.height * 0.36f
    val dim = if (on) 1f else 0.5f
    // The watch: band, round face, and on it the stop to get off at.
    block(AppColors.Muted, center.x - radius * 0.55f, 4.dp.toPx(), radius * 1.1f, size.height - 8.dp.toPx(), radius = 6.dp.toPx(), alpha = 0.35f)
    drawCircle(Color(0xFF14161B), radius = radius, center = center, alpha = dim)
    drawCircle(AppColors.Muted, radius = radius, center = center, style = Stroke(width = 2.dp.toPx()), alpha = dim)
    drawCircle(SketchLines[0], radius = 4.dp.toPx(), center = Offset(center.x, center.y - radius * 0.42f), alpha = dim)
    textBar(center.x - radius * 0.5f, center.y - 2.dp.toPx(), radius, color = Color.White, alpha = 0.85f * dim, thick = 4.dp)
    textBar(center.x - radius * 0.35f, center.y + 6.dp.toPx(), radius * 0.7f, color = Color.White, alpha = 0.45f * dim)
    if (!on) return
    // It buzzes: arcs on both sides that spread as the reminder comes.
    val spread = reveal.coerceIn(0f, 1f)
    for (wave in 1..2) {
        val waveRadius = radius + wave * 7.dp.toPx() * (0.5f + 0.5f * spread)
        val alpha = spread * (if (wave == 1) 0.9f else 0.5f)
        listOf(-30f, 150f).forEach { start ->
            drawArc(
                color = AppColors.Blue.copy(alpha = alpha),
                startAngle = start,
                sweepAngle = 60f,
                useCenter = false,
                topLeft = Offset(center.x - waveRadius, center.y - waveRadius),
                size = Size(waveRadius * 2, waveRadius * 2),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}
