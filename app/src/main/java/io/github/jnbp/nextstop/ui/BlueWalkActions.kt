package io.github.jnbp.nextstop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.ui.theme.AppColors

/*
 * #191: the buttons of every blue walk and change card of the new views (detail and Live-Fahrt),
 * one row of chips: "Karte zeigen ▾" unfolds the small map in place, "Karte öffnen" shows the walk
 * on the big map (what a tap on the card over the map always did, now visible), and Google Maps.
 * Where the three do not fit the width, "Karte öffnen" becomes a round icon at the end of the row,
 * and where Google Maps would then have to break a word, it becomes one too (#206).
 */

/**
 * The row of a blue card's buttons (see above): [onToggleMap] null without a small map here,
 * [onOpenMap] null without a way to show, [onNavigate] null without Google Maps. Nothing at all
 * where none is given.
 */
@Composable
internal fun BlueWalkActions(
    mapOpen: Boolean,
    onToggleMap: (() -> Unit)?,
    onOpenMap: (() -> Unit)?,
    onNavigate: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (onToggleMap == null && onOpenMap == null && onNavigate == null) return
    val openLabel = stringResource(R.string.r6l2_map_open_big)
    val navigateLabel = stringResource(R.string.navigate)
    Layout(
        contents = listOf<@Composable () -> Unit>(
            { if (onToggleMap != null) MapFoldButton(open = mapOpen, onToggle = onToggleMap, ground = AppColors.Card, ink = AppColors.Ink) },
            { if (onOpenMap != null) BlueActionChip(Glyph.OpenFull, stringResource(R.string.r6l2_map_open), onOpenMap) },
            { if (onNavigate != null) BlueActionChip(Glyph.Navigate, navigateLabel, onNavigate, lines = 2) },
            { if (onOpenMap != null) BlueActionIcon(Glyph.OpenFull, openLabel, onOpenMap) },
            { if (onNavigate != null) BlueActionIcon(Glyph.Navigate, navigateLabel, onNavigate) },
            // Only measured, never placed: the map button at the width of the label it shows
            // (#206). The button itself grows softly to "Karte ausblenden", and while it grows it
            // tells the width it has, not the one it goes to.
            { if (onToggleMap != null) MapFoldButton(open = mapOpen, onToggle = {}, modifier = Modifier.clearAndSetSemantics { }, ground = AppColors.Card, ink = AppColors.Ink, animateSize = false) },
        ),
        modifier = modifier,
    ) { parts, constraints ->
        // A list destructures to five parts at most.
        val (fold, open, navigate, icon, navigateIcon) = parts
        val foldProbe = parts[5]
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val gap = BLUE_ACTION_GAP.roundToPx()
        val room = constraints.maxWidth
        // Decided by the widths the chips want, before any of them is measured.
        val foldWants = foldProbe.firstOrNull()?.maxIntrinsicWidth(Constraints.Infinity) ?: 0
        val openWants = open.firstOrNull()?.maxIntrinsicWidth(Constraints.Infinity) ?: 0
        val navigateChip = navigate.firstOrNull()
        val navigateWants = navigateChip?.maxIntrinsicWidth(Constraints.Infinity) ?: 0
        // Google Maps at its longest word: narrower, its label would break inside a word.
        val navigateWord = navigateChip?.minIntrinsicWidth(Constraints.Infinity) ?: 0
        val look = if (room == Constraints.Infinity) {
            BlueActionsLook.Chips
        } else {
            blueActionsLook(foldWants, openWants, navigateWants, navigateWord, BLUE_ACTION_HEIGHT.roundToPx(), gap, room)
        }
        val chips = look == BlueActionsLook.Chips
        val foldPlaced = fold.firstOrNull()?.measure(loose)
        val openPlaced = if (chips) open.firstOrNull()?.measure(loose) else null
        val iconPlaced = if (chips) null else icon.firstOrNull()?.measure(loose)
        val navigateIconPlaced = if (look == BlueActionsLook.IconsOnly) navigateIcon.firstOrNull()?.measure(loose) else null
        // Google Maps takes what is left, its label in two lines where it must.
        val used = listOfNotNull(foldPlaced, openPlaced, iconPlaced).sumOf { it.width + gap }
        val navigatePlaced = if (navigateIconPlaced != null) {
            null
        } else {
            navigateChip?.measure(
                loose.copy(maxWidth = if (room == Constraints.Infinity) room else (room - used).coerceAtLeast(0)),
            )
        }
        val row = listOfNotNull(foldPlaced, openPlaced, navigatePlaced)
        val ends = listOfNotNull(navigateIconPlaced, iconPlaced)
        val height = (row + ends).maxOfOrNull { it.height } ?: 0
        val rowWidth = row.sumOf { it.width } + gap * (row.size - 1).coerceAtLeast(0)
        val width = if (ends.isNotEmpty() && room != Constraints.Infinity) room else rowWidth
        layout(width, height) {
            var x = 0
            row.forEach { placeable ->
                placeable.placeRelative(x, (height - placeable.height) / 2)
                x += placeable.width + gap
            }
            // The round icons at the end of the row: Google Maps, then "Karte öffnen" last.
            var end = width
            ends.reversed().forEach { placeable ->
                end -= placeable.width
                placeable.placeRelative(end, (height - placeable.height) / 2)
                end -= gap
            }
        }
    }
}

/** A round icon button of the row, on the card colour, named [description] for TalkBack. */
@Composable
private fun BlueActionIcon(glyph: Glyph, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(BLUE_ACTION_HEIGHT)
            .clip(CircleShape)
            .background(AppColors.Card)
            .semantics {
                contentDescription = description
                role = Role.Button
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(glyph, color = AppColors.Ink, size = 18.dp)
    }
}

/** One chip of the row: an icon and its label, on the card colour. */
@Composable
private fun BlueActionChip(glyph: Glyph, text: String, onClick: () -> Unit, lines: Int = 1) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.Card)
            .clickable(role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = BLUE_ACTION_HEIGHT)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        GlyphIcon(glyph, color = AppColors.Ink, size = 16.dp)
        Text(
            text = text,
            color = AppColors.Ink,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            maxLines = lines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// The height of the buttons (as the button of the small map) and the gap between two.
private val BLUE_ACTION_HEIGHT = 40.dp
private val BLUE_ACTION_GAP = 8.dp
