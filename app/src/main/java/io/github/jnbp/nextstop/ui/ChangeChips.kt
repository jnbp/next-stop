package io.github.jnbp.nextstop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.changeTileRow
import io.github.jnbp.nextstop.data.ChangeTileShow
import io.github.jnbp.nextstop.data.changeTileShow
import io.github.jnbp.nextstop.ui.companion.OtherWaysList
import io.github.jnbp.nextstop.ui.companion.OtherWaysNote
import io.github.jnbp.nextstop.ui.companion.OtherWaysPart
import io.github.jnbp.nextstop.ui.theme.AppColors
import java.time.OffsetDateTime

/*
 * #243: under a blue change card of the detail page and of the Live-Fahrt, the two links
 * "Anschluss wechseln" and "Andere Wege ab hier", each with a row of tiles of its own, became two
 * independent toggle chips side by side, "Anschluss" and "Andere Wege" (with a route icon), and
 * ONE shared row of tiles below them (see ChangeTileRow): only the connections (as before, by
 * their departure at the change, #215), only the other ways, both mixed by their arrival (the
 * other ways with a small route icon), or nothing. The chip "Anschluss" starts as the setting
 * "Anschlusswahl" says (folded, the next one open, all open) and keeps the state it had
 * (CompanionOffers in the Live-Fahrt, the page's own toggles in the detail); "Andere Wege" starts
 * off and searches on its first tap (OtherWaysSearch, as the link did; "Andere Wege ab Umstieg"
 * from the bar of the Live-Fahrt turns it on too).
 * Variante 2 (Joel 19.11.): the shared row is gone; "Anschluss" keeps the row of connection
 * tiles, "Andere Wege" shows a list of full-width rows below it (OtherWaysList), each section
 * folding on its own.
 */

/**
 * Which kinds of tiles a row of connection tiles shows (#257; see ChangeTileShow): both by
 * default. Since Variante 2 the other ways are no tiles under a change; the row keeps the means
 * to let a kind leave softly.
 */
internal val LocalChangeTileShow = compositionLocalOf { ChangeTileShow(connections = true, ways = true) }

/** The chip "Anschluss" of a change: whether it is [on], and what a tap does. */
internal class ConnectionChipState(val on: Boolean, val onToggle: () -> Unit)

/**
 * The two chips under the change [change] of [journey] and their two sections below them
 * (Variante 2, Joel 19.11.): "Anschluss" on shows the row of connection tiles ([connections],
 * its loading and failure lines included); "Andere Wege" on shows below it the list of the other
 * ways as full-width rows ([OtherWaysList]) with its loading / none / failed line. Each section
 * folds in and out on its own (expand and fade, at once with animations off, see MapFold), so
 * all four changes of the chips animate (#257). [connection] null: no choice of connections here
 * (switched off, too few, over); [ways] false: no other ways (the change is over). [onTakeWay]
 * takes another way (null: the followed trip). Nothing without either chip.
 */
@Composable
internal fun ChangeChoiceArea(
    journey: Journey,
    change: TripChange,
    now: OffsetDateTime,
    connection: ConnectionChipState?,
    ways: Boolean,
    onTakeWay: ((Journey) -> Unit)?,
    modifier: Modifier = Modifier,
    connections: @Composable () -> Unit,
) {
    if (connection == null && !ways) return
    OtherWaysPart(journey = journey, change = change, now = now, onTake = onTakeWay) { view ->
        val waysOn = ways && view.on
        val connectionsOn = connection?.on == true
        val show = changeTileShow(changeTileRow(connectionsOn, waysOn))
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (connection != null) {
                    ChangeChoiceChip(
                        text = stringResource(R.string.r8ui_chip_connection),
                        glyph = null,
                        on = connectionsOn,
                        onClick = connection.onToggle,
                    )
                }
                if (ways) {
                    ChangeChoiceChip(
                        text = stringResource(R.string.r8ui_chip_ways),
                        glyph = Glyph.Route,
                        on = waysOn,
                        onClick = view.onChipTap,
                    )
                }
            }
            // The row of the connection tiles, folded in and out with its chip.
            MapFold(open = connection != null && show.connections) {
                Column(modifier = Modifier.softSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    connections()
                }
            }
            // Below it the other ways as a list of rows, folded in and out with their chip.
            MapFold(open = show.ways) {
                Column(modifier = Modifier.softSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OtherWaysList(view)
                    OtherWaysNote(view)
                }
            }
        }
    }
}

/**
 * A toggle chip of a change (#243): blue while [on], on the card colour while off, with an
 * optional icon before its label; read out as a checkbox with its state.
 */
@Composable
private fun ChangeChoiceChip(text: String, glyph: Glyph?, on: Boolean, onClick: () -> Unit) {
    val spec: AnimationSpec<Color> = if (rememberAnimationsOn()) spring(stiffness = Spring.StiffnessMediumLow) else snap()
    val ground by animateColorAsState(if (on) AppColors.Blue else AppColors.Card, spec, label = "changeChipGround")
    val ink by animateColorAsState(if (on) AppColors.OnBlue else AppColors.Ink, spec, label = "changeChipInk")
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            // Read while drawing: the colour glides without composing the chip for each frame.
            .drawBehind { drawRect(ground) }
            .toggleable(value = on, role = Role.Checkbox, onValueChange = { onClick() })
            .defaultMinSize(minHeight = CHIP_HEIGHT)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (glyph != null) GlyphIcon(glyph, color = ink, size = 16.dp)
        Text(
            text = text,
            color = ink,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// The height of the chips, as the buttons of the blue cards (BlueWalkActions).
private val CHIP_HEIGHT = 40.dp
