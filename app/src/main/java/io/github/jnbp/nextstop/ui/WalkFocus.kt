package io.github.jnbp.nextstop.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.FootPaths
import io.github.jnbp.nextstop.data.FootWay
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.Leg
import io.github.jnbp.nextstop.data.WalkTargetKind
import io.github.jnbp.nextstop.data.plainStopName
import io.github.jnbp.nextstop.data.shownWalkMinutes
import io.github.jnbp.nextstop.ui.theme.AppColors

/**
 * A walk shown on the big map, opened by a tap on its small map in the journey detail or the
 * companion: [walk] is the walk (or the way between two stop positions), [journey] the
 * journey it belongs to, drawn around it where known.
 */
data class WalkFocus(val walk: Leg, val journey: Journey?)

/**
 * What lies over the map while it shows a walk (see [WalkFocus]): a visible "Back" at the top
 * that returns to the page the walk was opened from, and at the bottom where the walk leads,
 * how long it takes, and whether the map follows the own position ([following]); a tap on
 * "Follow" frames walk and position again after the map was moved by hand ([onFollow]).
 * [bottomInset] keeps the card clear of what lies below it. [rerouteMinutes] are the minutes
 * still to walk along a new way, once the walk was re-routed.
 */
@Composable
fun WalkFocusOverlay(
    focus: WalkFocus,
    following: Boolean,
    hasPosition: Boolean,
    onBack: () -> Unit,
    onFollow: () -> Unit,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 16.dp,
    rerouteMinutes: Long? = null,
) {
    Box(modifier = modifier.fillMaxSize()) {
        BackChip(onBack = onBack, modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 8.dp))
        GlassSurface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 12.dp, end = 12.dp, bottom = bottomInset)
                .widthIn(max = PANEL_MAX_WIDTH)
                .fillMaxWidth()
                // Its top edge: the floating "centre on me" button stands above it (#165).
                .onGloballyPositioned { MapEdges.report(MapEdges.walkCard, 0, it.boundsInRoot().top) },
            shape = RoundedCornerShape(24.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GlyphIcon(if (focus.walk.cycling) Glyph.Bike else Glyph.Walk, size = 24.dp)
                Column(modifier = Modifier.weight(1f)) {
                    val walk = focus.walk
                    // A walk re-routed from where the traveller left its way: the minutes along the new way.
                    // Otherwise the planned minutes, never fewer than the drawn way takes at the app's pace.
                    val footVersion by FootPaths.version.collectAsState()
                    val shown = remember(walk, footVersion) { shownWalkMinutes(walk) }
                    val minutes = rerouteMinutes ?: shown
                    val target = walk.walkTarget
                    val goal = when {
                        target?.kind == WalkTargetKind.StopPosition && target.label != null ->
                            stringResource(R.string.walk_focus_position, target.label)
                        else -> walk.to.plainStopName()
                    }
                    Text(
                        text = stringResource(if (walk.cycling) R.string.cycle_focus_title else R.string.walk_focus_title, goal),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // Drawn straight because no real way on foot is known: said so, not pretended.
                    val straight = remember(walk, footVersion) { FootPaths.status(walk) == FootWay.Straight }
                    if (straight) StraightLineNote(modifier = Modifier.padding(vertical = 2.dp))
                    Text(
                        text = when {
                            !hasPosition -> stringResource(R.string.walk_focus_no_position)
                            following && !walk.cycling -> stringResource(R.string.walk_focus_following, walkDurationLabel(minutes))
                            walk.cycling -> stringResource(R.string.cycle_focus_minutes, minutes)
                            else -> stringResource(R.string.walk_focus_minutes, walkDurationLabel(minutes))
                        },
                        color = AppColors.Muted,
                        fontSize = 13.sp,
                        maxLines = 2,
                    )
                    // What the way passes and where it leads, from OpenStreetMap: "Ausgang B · Aufzug · zu Pos. 9".
                    val context = LocalContext.current
                    val details = remember(walk, footVersion) { walkFocusDetails(walk) }
                    val broken = rememberBrokenLifts(details)
                    val toName = walk.to.plainStopName()
                    val summary = remember(details, broken, minutes, toName) {
                        walkSummaryText(
                            context,
                            details,
                            target?.takeIf { it.kind == WalkTargetKind.StopPosition }?.label,
                            minutes.takeIf { !walk.cycling },
                            broken,
                            targetName = toName,
                        )
                    }
                    // Never cut here: as many lines as it takes.
                    WalkSummaryLine(summary, modifier = Modifier.padding(top = 2.dp), maxLines = Int.MAX_VALUE)
                }
                // Moved by hand, the map no longer follows: the floating button at the bottom
                // right brings walk and position back (#165, see FloatingLocateButton).
            }
        }
    }
}

/** The way back from the walk on the map: an arrow and the word, so it cannot be missed. */
@Composable
private fun BackChip(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .semantics { role = Role.Button }
            .heightIn(min = 44.dp)
            .clip(CircleShape)
            .then(glassGround())
            .border(1.dp, glassRim(), CircleShape)
            .clickable(onClick = onBack)
            .padding(start = 10.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        GlyphIcon(Glyph.Back, size = 20.dp)
        Text(text = stringResource(R.string.back), fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
    }
}

// The widest the card at the bottom gets on a wide screen.
private val PANEL_MAX_WIDTH = 560.dp
