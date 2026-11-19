package io.github.jnbp.nextstop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.AppPrefs
import io.github.jnbp.nextstop.data.DataSource
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.LiveOrigin
import io.github.jnbp.nextstop.data.TimeTone
import io.github.jnbp.nextstop.data.journeySources
import io.github.jnbp.nextstop.ui.theme.AppColors

/*
 * #277b: the small footer of a journey, at the end of the detail and of the Live-Fahrt (calm
 * view and sheet over the map): what the colours of the times mean, with a tiny example time in
 * each colour, and where the data and the live times of this journey come from right now.
 */

// The example times of the legend: the same clock in every language.
private const val LEGEND_PLANNED = "09:00"
private const val LEGEND_ON_TIME = "09:00"
private const val LEGEND_LATE = "09:17"
private const val LEGEND_ESTIMATED = "09:18"

// A blank that never breaks: an example time stays with its word.
private const val KEEP = ' '

/** The footer of [journey]: the colour legend, then "Daten: … · Echtzeit: …" (see journeySources). */
@Composable
fun TimesFooter(journey: Journey, modifier: Modifier = Modifier) {
    val legend = timesLegend()
    val sources = journeySourcesText(journey)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = legend, color = AppColors.Muted, fontSize = 11.sp)
        if (sources != null) Text(text = sources, color = AppColors.Muted, fontSize = 11.sp)
    }
}

/** "09:00 Fahrplan · 09:00 pünktlich · 09:17 verspätet · ca. 09:18 geschätzt …", each time in its colour. */
@Composable
private fun timesLegend(): AnnotatedString {
    val about = stringResource(R.string.estimated_prefix)
    val items = listOf(
        Triple(LEGEND_PLANNED, timeToneColor(TimeTone.Scheduled, AppColors.Ink), stringResource(R.string.r9last_legend_planned)),
        Triple(LEGEND_ON_TIME, timeToneColor(TimeTone.OnTime, AppColors.Ink), stringResource(R.string.r9last_legend_on_time)),
        Triple(LEGEND_LATE, timeToneColor(TimeTone.Late, AppColors.Ink), stringResource(R.string.r9last_legend_late)),
        Triple("$about$KEEP$LEGEND_ESTIMATED", timeToneColor(TimeTone.Estimated, AppColors.Ink), stringResource(R.string.r9last_legend_estimated)),
    )
    return buildAnnotatedString {
        items.forEachIndexed { index, (time, color, word) -> appendLegendItem(index > 0, time, color, word) }
    }
}

private fun AnnotatedString.Builder.appendLegendItem(separated: Boolean, time: String, color: Color, word: String) {
    if (separated) append(" · ")
    withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) { append(time) }
    append(KEEP)
    append(word)
}

/** "Daten: Transitous · Echtzeit: BVG" for [journey]; the second part only with live data; null where nothing is known. */
@Composable
private fun journeySourcesText(journey: Journey): String? {
    val context = LocalContext.current
    val sources = remember(journey) { journeySources(journey) }
    // The HAFAS source the app asks for live times of Transitous rides: the first one in use (see RideKindCheck).
    val hafas = remember { AppPrefs(context).sourceChoice.inUse.firstOrNull { it.family == DataSource.Bvg } }
    val estimate = stringResource(R.string.r9last_live_estimate)
    val data = sources.provider?.let { context.getString(R.string.source_hint, backendName(context, it)) }
    val origins = sources.live.mapNotNull { origin ->
        when (origin) {
            LiveOrigin.Provider -> sources.provider?.let { backendName(context, it) }
            LiveOrigin.Hafas -> hafas?.let { backendName(context, it) } ?: "HAFAS"
            LiveOrigin.Estimate -> estimate
        }
    }.distinct()
    val live = origins.takeIf { it.isNotEmpty() }?.let { context.getString(R.string.r9last_live_from, it.joinToString(", ")) }
    return listOfNotNull(data, live).joinToString(" · ").ifEmpty { null }
}
