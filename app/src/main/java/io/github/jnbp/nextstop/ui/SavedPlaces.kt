package io.github.jnbp.nextstop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.Disruption
import io.github.jnbp.nextstop.data.PlaceIcon
import io.github.jnbp.nextstop.data.SavedPlace
import io.github.jnbp.nextstop.data.shortStopName
import io.github.jnbp.nextstop.ui.theme.AppColors
import io.github.jnbp.nextstop.ui.theme.AppFont

// A saved place keeps a name of at most this many characters, as a favourite place does.
private const val MAX_NAME = 24

/** What a saved place is called: its own name, or else the name of the place itself. */
fun savedPlaceName(saved: SavedPlace): String = saved.name.ifBlank { saved.place.name.shortStopName() }

/**
 * The saved places as one list: a tap on a place leads there, the chevron opens its name,
 * icon and the way to remove it, and the handle at its end moves it up or down.
 */
@Composable
fun SavedPlacesList(
    places: List<SavedPlace>,
    onOpen: (SavedPlace) -> Unit,
    onChange: (List<SavedPlace>) -> Unit,
    modifier: Modifier = Modifier,
    disruptionsOf: (SavedPlace) -> List<Disruption> = { emptyList() },
    onDisruptions: (SavedPlace) -> Unit = {},
    /** The radius the notices around the places were found in (see CardDisruptionLine). */
    disruptionRadius: Int? = null,
    /** A bell on each place ("tell me about problems" near it, see placeWatchKey); [onBell] after a switch. */
    bell: Boolean = false,
    onBell: () -> Unit = {},
    onWatchSettings: (() -> Unit)? = null,
) {
    var openId by remember { mutableStateOf<String?>(null) }
    // While a place is dragged: the list in its new order, which place it is, and how far it
    // is drawn away from its slot in that order.
    var order by remember { mutableStateOf<List<SavedPlace>?>(null) }
    var draggedId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<String, Int>() }
    val latestPlaces by rememberUpdatedState(places)
    val latestChange by rememberUpdatedState(onChange)
    val gap = with(LocalDensity.current) { 3.dp.toPx() }
    // A firm buzz as a place is picked up, a click for each new slot (see Haptics).
    val ticks = rememberTicks()
    val shown = order ?: places
    GlassSurface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
        if (places.isEmpty()) {
            Text(
                text = stringResource(R.string.no_saved_places),
                color = AppColors.Muted,
                modifier = Modifier.padding(20.dp),
            )
        } else Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            shown.forEachIndexed { index, saved ->
                key(saved.id) {
                    val dragged = saved.id == draggedId
                    SavedPlaceRow(
                        saved = saved,
                        shape = groupShape(index, shown.lastIndex),
                        open = openId == saved.id,
                        onToggle = { openId = if (openId == saved.id) null else saved.id },
                        onOpen = { onOpen(saved) },
                        disruptions = disruptionsOf(saved),
                        onDisruptions = { onDisruptions(saved) },
                        disruptionRadius = disruptionRadius,
                        bellKey = if (bell) placeWatchKey(saved.place) else null,
                        onBell = onBell,
                        onWatchSettings = onWatchSettings,
                        onChange = { changed -> onChange(places.map { if (it.id == changed.id) changed else it }) },
                        onRemove = {
                            openId = null
                            onChange(places.filter { it.id != saved.id })
                        },
                        handleModifier = Modifier.pointerInput(saved.id) {
                            fun finish(keep: Boolean) {
                                val moved = order
                                if (keep && moved != null && moved.map { it.id } != latestPlaces.map { it.id }) {
                                    latestChange(moved)
                                }
                                order = null
                                draggedId = null
                                dragOffset = 0f
                            }
                            detectVerticalDragGestures(
                                onDragStart = {
                                    openId = null
                                    order = latestPlaces
                                    draggedId = saved.id
                                    dragOffset = 0f
                                    ticks.pickUp()
                                },
                                onDragEnd = { finish(keep = true) },
                                onDragCancel = { finish(keep = false) },
                                onVerticalDrag = { change, amount ->
                                    change.consume()
                                    val list = order ?: return@detectVerticalDragGestures
                                    dragOffset += amount
                                    val at = list.indexOfFirst { it.id == saved.id }
                                    if (at < 0) return@detectVerticalDragGestures
                                    // Past half of its neighbour, the place takes the neighbour's slot.
                                    val below = list.getOrNull(at + 1)
                                    val above = list.getOrNull(at - 1)
                                    if (dragOffset > 0f && below != null) {
                                        val step = (heights[below.id] ?: 0) + gap
                                        if (dragOffset > step / 2f) {
                                            order = list.toMutableList().apply { add(at + 1, removeAt(at)) }
                                            dragOffset -= step
                                            ticks.tick()
                                        }
                                    } else if (dragOffset < 0f && above != null) {
                                        val step = (heights[above.id] ?: 0) + gap
                                        if (-dragOffset > step / 2f) {
                                            order = list.toMutableList().apply { add(at - 1, removeAt(at)) }
                                            dragOffset += step
                                            ticks.tick()
                                        }
                                    }
                                },
                            )
                        },
                        modifier = Modifier
                            .onSizeChanged { heights[saved.id] = it.height }
                            .zIndex(if (dragged) 1f else 0f)
                            .graphicsLayer {
                                if (dragged) {
                                    translationY = dragOffset
                                    scaleX = 1.02f
                                    scaleY = 1.02f
                                }
                            },
                    )
                }
            }
        }
    }
}

/** One saved place: its icon, its name and what it is, and below them, once opened, name, icon and removing. */
@Composable
private fun SavedPlaceRow(
    saved: SavedPlace,
    shape: Shape,
    open: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onChange: (SavedPlace) -> Unit,
    onRemove: () -> Unit,
    handleModifier: Modifier,
    modifier: Modifier = Modifier,
    disruptions: List<Disruption> = emptyList(),
    onDisruptions: () -> Unit = {},
    disruptionRadius: Int? = null,
    bellKey: String? = null,
    onBell: () -> Unit = {},
    onWatchSettings: (() -> Unit)? = null,
) {
    val placeName = saved.place.name.shortStopName()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.Card)
            .disruptionFrame(disruptions.isNotEmpty(), shape)
            .animateContentSize(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 60.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpen)
                    .defaultMinSize(minHeight = 60.dp)
                    .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(AppColors.Blue),
                    contentAlignment = Alignment.Center,
                ) {
                    GlyphIcon(placeGlyph(saved.icon), color = AppColors.OnBlue, size = 18.dp)
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = savedPlaceName(saved),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (saved.name.isNotBlank() && saved.name != placeName) {
                        Text(
                            text = placeName,
                            color = AppColors.Muted,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // Disruptions on the lines near the place, in one line; a tap opens them.
                    CardDisruptionLine(items = disruptions, onClick = onDisruptions, radiusMeters = disruptionRadius)
                }
            }
            if (bellKey != null) DisruptionBell(watchKey = bellKey, onChange = onBell, onSettings = onWatchSettings, size = 40.dp)
            val editLabel = stringResource(R.string.saved_place_edit)
            Box(
                modifier = Modifier
                    .semantics { contentDescription = editLabel }
                    .clip(CircleShape)
                    .clickable(onClick = onToggle)
                    .size(44.dp),
                contentAlignment = Alignment.Center,
            ) {
                Chevron(open = open)
            }
            val moveLabel = stringResource(R.string.saved_place_move)
            Box(
                modifier = Modifier
                    .semantics { contentDescription = moveLabel }
                    .then(handleModifier)
                    .size(width = 44.dp, height = 56.dp)
                    .padding(end = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                DragHandle()
            }
        }
        if (open) {
            Column(
                modifier = Modifier.padding(start = 14.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                NameField(
                    value = saved.name,
                    placeholder = placeName,
                    onValue = { onChange(saved.copy(name = it)) },
                )
                IconPicker(selected = saved.icon, onSelect = { onChange(saved.copy(icon = it)) })
                SecondaryButton(
                    text = stringResource(R.string.place_remove),
                    onClick = onRemove,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    ground = AppColors.Chip,
                    minHeight = 44.dp,
                )
            }
        }
    }
}

/** Three short lines: the place can be moved by pulling here. */
@Composable
private fun DragHandle() {
    Canvas(modifier = Modifier.size(20.dp)) {
        val w = size.width
        listOf(0.3f, 0.5f, 0.7f).forEach { y ->
            drawLine(
                AppColors.Muted,
                Offset(w * 0.15f, w * y),
                Offset(w * 0.85f, w * y),
                strokeWidth = w * 0.1f,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** The field for the own name of a place; [placeholder] stands in while it is empty. */
@Composable
internal fun NameField(value: String, placeholder: String, onValue: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = { onValue(it.take(MAX_NAME)) },
        singleLine = true,
        textStyle = TextStyle(
            color = AppColors.Ink,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = AppFont,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.Chip)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        decorationBox = { innerTextField ->
            Box {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = AppColors.Muted,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                innerTextField()
            }
        },
    )
}

/**
 * All icons of places as a grid that wraps (no hidden icons off to the side), the name of the
 * picked one above it; the picked one is filled. Shared by the saved and the favourite places.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IconPicker(selected: PlaceIcon, onSelect: (PlaceIcon) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FadeSwap(target = placeIconLabel(selected)) { label ->
            Text(
                text = label,
                color = AppColors.Muted,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                maxLines = 1,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PlaceIcon.entries.forEach { icon ->
                val picked = icon == selected
                val label = placeIconLabel(icon)
                val ground by animateColorAsState(
                    if (picked) AppColors.Blue else AppColors.Chip,
                    spring(stiffness = Spring.StiffnessMediumLow),
                    label = "savedIconGround",
                )
                Box(
                    modifier = Modifier
                        .semantics { contentDescription = label }
                        .clip(RoundedCornerShape(14.dp))
                        .background(ground)
                        .clickable { onSelect(icon) }
                        .size(44.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    GlyphIcon(placeGlyph(icon), color = if (picked) AppColors.OnBlue else AppColors.Ink)
                }
            }
        }
    }
}

/**
 * Asks how to keep a place: with a name and an icon among the saved places, or, while one of
 * the few slots is free ([canFavorite]), as a favourite place.
 */
@Composable
fun SavePlaceDialog(
    initialName: String,
    placeName: String,
    canFavorite: Boolean,
    onSave: (String, PlaceIcon) -> Unit,
    onFavorite: (String, PlaceIcon) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName.take(MAX_NAME)) }
    var icon by remember { mutableStateOf(PlaceIcon.Pin) }
    Dialog(onDismissRequest = onDismiss) {
        // A dialog is a window of its own: the picture of the map does not line up behind it.
        CompositionLocalProvider(LocalBackdrop provides null) {
            GlassSurface(shape = RoundedCornerShape(32.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.save_place_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    NameField(value = name, placeholder = placeName, onValue = { name = it })
                    IconPicker(selected = icon, onSelect = { icon = it })
                    PrimaryButton(
                        text = stringResource(R.string.save_place_saved),
                        onClick = { onSave(name.trim(), icon) },
                        modifier = Modifier.fillMaxWidth(),
                        minHeight = 50.dp,
                    )
                    if (canFavorite) {
                        SecondaryButton(
                            text = stringResource(R.string.save_place_favorite),
                            onClick = { onFavorite(name.trim(), icon) },
                            modifier = Modifier.fillMaxWidth(),
                            ground = AppColors.BlueContainer,
                            ink = AppColors.OnBlueContainer,
                            minHeight = 48.dp,
                        )
                    }
                    SecondaryButton(
                        text = stringResource(R.string.cancel),
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        ground = AppColors.Chip,
                        minHeight = 48.dp,
                    )
                }
            }
        }
    }
}
