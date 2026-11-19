package io.github.jnbp.nextstop.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import io.github.jnbp.nextstop.wear.R
import io.github.jnbp.nextstop.wear.data.RescueOffer

/**
 * The phone's offer where a delay broke the chosen change ahead, over the pages of the trip:
 * "Anschluss klappt nicht mehr", "S7 um 16:06 nehmen?" with the platform and the stop, and
 * "Wechseln" ([onSwitch]: the phone switches the trip) or "Nicht jetzt" ([onLater]). It makes
 * no sound of its own: the phone decides whether to notify (its channel and its quiet rules).
 */
@Composable
fun RescueCard(offer: RescueOffer, onSwitch: () -> Unit, onLater: () -> Unit) {
    // Never scrolls (#180): the question and "Switch" always stay; what does not fit goes first.
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        FitColumn(top = 36.dp, bottom = 28.dp, horizontal = 24.dp, edge = 8.dp, spacing = 4.dp) {
            FitLine(RESCUE_ICON) {
                Icon(painterResource(R.drawable.ic_warning), contentDescription = null, tint = RESCUE_AMBER, modifier = Modifier.size(22.dp))
            }
            FitLine(RESCUE_TITLE) {
                Text(
                    stringResource(R.string.r6_rescue_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
            FitLine(RESCUE_BADGE) { LineBadge(offer.line, offer.product, color = offer.color) }
            FitLine {
                Text(
                    stringResource(R.string.r6_rescue_take, offer.line ?: "", clock(offer.departure)),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
            val where = listOfNotNull(platformText(offer.platform, offer.line, offer.product), offer.stop.ifEmpty { null }).joinToString(" · ")
            if (where.isNotEmpty()) {
                FitLine(RESCUE_WHERE) {
                    Text(
                        where,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            FitLine { WatchAction(stringResource(R.string.r6_rescue_switch), onClick = onSwitch, icon = R.drawable.ic_replan, primary = true) }
            FitLine(RESCUE_LATER) { WatchAction(stringResource(R.string.r6_rescue_later), onClick = onLater, icon = R.drawable.ic_close) }
        }
    }
}

// What of the rescue card goes first where the screen is small (#180): the icon, the badge,
// the platform, then "Not now" (a swipe or the back gesture also closes it), then the title.
private const val RESCUE_TITLE = 90
private const val RESCUE_LATER = 80
private const val RESCUE_WHERE = 70
private const val RESCUE_BADGE = 60
private const val RESCUE_ICON = 50

// The amber of a warning, as the companion's.
private val RESCUE_AMBER = Color(0xFFFFB300)
