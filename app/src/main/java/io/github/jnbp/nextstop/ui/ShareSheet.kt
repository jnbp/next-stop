package io.github.jnbp.nextstop.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.AppPrefs
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.plainStopName
import io.github.jnbp.nextstop.ui.theme.AppColors
import java.time.OffsetDateTime

/*
 * The one way to share a connection or a followed trip: a sheet from the bottom with a short
 * summary (from → to, times) and up to three large cards, each with an icon, a title and one line
 * that says what the other person gets:
 *
 * - "Link zur Verbindung": the route page (web/route) with a short line, step by step with live
 *   times, in any browser. The recommended choice for a connection.
 * - "Live mitverfolgen lassen": the live page (web/live), which estimates from live data where the
 *   rides are, never from GPS. The recommended choice for a followed trip. For a connection it is
 *   only offered when the connection leaves within LIVE_OFFER_LEAD_MINUTES, and choosing it starts
 *   the trip, so the link and the trip the app follows are the same.
 * - "Nur als Text": departures and changes as a message, no link.
 *
 * The first live link asks once for the first name the page shows; that question is the second
 * step of the same sheet, not a dialog of its own. Each choice closes the sheet and opens the
 * share sheet of the system. "Link kopieren" puts the route link on the clipboard instead.
 * Without a valid address of the web pages (see siteAddress) the cards with a link stay visible
 * but disabled and say why.
 */

// How soon a connection must leave for the sheet to offer following it live, in minutes. Sharing
// live starts the trip; a connection that leaves later is shared as a route link instead.
private const val LIVE_OFFER_LEAD_MINUTES = 60L

// Opacity of a card that cannot be used now (no valid address of the web pages).
private const val SHARE_DISABLED_ALPHA = 0.45f

/** The steps of the share sheet: the choice, then (once) the name shown on the live page. */
private enum class ShareStep { Choice, LiveName }

/**
 * The share sheet of [journey] from [fromName] to [toName]. With [following] it shares the trip
 * the companion follows (live link first, the text says where the trip stands at [now]); without
 * it a connection of the results (route link first). [onStartTrip] lets the sheet offer the live
 * link for a connection that leaves soon: choosing it starts the trip as well. [onDismiss] closes
 * the sheet; every choice calls it before it opens the share sheet of the system.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(
    journey: Journey,
    fromName: String,
    toName: String,
    now: OffsetDateTime,
    following: Boolean,
    onDismiss: () -> Unit,
    onStartTrip: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val prefs = remember(context) { AppPrefs(context) }
    val base = remember(prefs) { siteAddress(prefs.liveBaseUrl) }
    val siteOk = liveBase(base) != null
    val from = fromName.ifBlank { journey.legs.first().from.plainStopName() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var step by remember { mutableStateOf(ShareStep.Choice) }
    var name by remember { mutableStateOf(prefs.liveName) }

    // A connection is only offered live when the traveller is about to set off on it.
    val liveOffered = following || (
        onStartTrip != null && !journey.cancelled && journey.arrival.isAfter(now) &&
            minutesBetween(now, journey.departure) <= LIVE_OFFER_LEAD_MINUTES
        )
    val shareText: () -> Unit = {
        onDismiss()
        if (following) shareTripText(context, journey, now) else shareConnectionText(context, journey, from, toName)
    }
    val shareRoute: () -> Unit = {
        val url = routeLink(context, base, journey, toName, now)
        onDismiss()
        if (url != null) shareRouteLink(context, url, toName)
    }
    val copyRoute: () -> Unit = {
        val url = routeLink(context, base, journey, toName, now)
        onDismiss()
        if (url != null) copyLink(context, url)
    }
    val sendLive: (String?) -> Unit = { liveName ->
        // A saved place goes by its label ("nach Hause"), not by its address.
        val target = sharedDestination(context, journey, toName)
        val url = liveLink(
            base, journey, target.name, now, liveName,
            destinationKind = target.kind, savedDestination = target.saved, language = shareLanguage(context),
        )
        onDismiss()
        when {
            url != null -> shareLiveLink(context, url, lead = tripShareHeading(context, target))
            following -> shareTripText(context, journey, now)
            else -> shareConnectionText(context, journey, from, toName)
        }
        // Sharing a connection live means taking it: the companion follows what the link shows.
        if (!following) onStartTrip?.invoke()
    }
    val shareLive: () -> Unit = {
        // The first live link asks once for the name it shows, in the same sheet.
        if (!prefs.liveNameAsked && prefs.liveName.isBlank()) {
            step = ShareStep.LiveName
        } else {
            sendLive(prefs.liveName)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.PlainGround,
        contentColor = AppColors.Ink,
    ) {
        // A sheet is a window of its own: the picture of the map does not line up behind it.
        CompositionLocalProvider(LocalBackdrop provides null) {
            FadeSwap(target = step, modifier = Modifier.fillMaxWidth()) { shown ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when (shown) {
                        ShareStep.Choice -> ShareChoice(
                            title = stringResource(if (following) R.string.trip_share else R.string.share_chooser),
                            summary = stringResource(
                                R.string.share_summary,
                                from,
                                toName,
                                journey.departure.clock(),
                                journey.arrival.clock(),
                            ),
                            following = following,
                            liveOffered = liveOffered,
                            siteOk = siteOk,
                            onLink = shareRoute,
                            onLive = shareLive,
                            onText = shareText,
                            onCopy = if (following) null else copyRoute,
                        )
                        ShareStep.LiveName -> LiveNameStep(
                            name = name,
                            onName = { name = it },
                            onShare = {
                                prefs.liveNameAsked = true
                                prefs.liveName = name.trim()
                                sendLive(prefs.liveName)
                            },
                            onSkip = {
                                prefs.liveNameAsked = true
                                sendLive(null)
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * The first step of the share sheet: title and summary, then the cards in the order that fits
 * ([following]: live first; a connection: route link first), and "Copy link" where [onCopy] is given.
 */
@Composable
private fun ColumnScope.ShareChoice(
    title: String,
    summary: String,
    following: Boolean,
    liveOffered: Boolean,
    siteOk: Boolean,
    onLink: () -> Unit,
    onLive: () -> Unit,
    onText: () -> Unit,
    onCopy: (() -> Unit)?,
) {
    Column(modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 6.dp)) {
        Text(text = title, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        Text(
            text = summary,
            color = AppColors.Muted,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    val unavailable = stringResource(R.string.live_link_not_set)
    val link: @Composable (Int, Boolean) -> Unit = { index, primary ->
        ShareOptionCard(
            glyph = Glyph.Route,
            title = stringResource(R.string.share_link_title),
            text = if (siteOk) stringResource(R.string.share_link_text) else unavailable,
            primary = primary,
            enabled = siteOk,
            index = index,
            onClick = onLink,
        )
    }
    val live: @Composable (Int, Boolean) -> Unit = { index, primary ->
        ShareOptionCard(
            glyph = Glyph.LiveShare,
            title = stringResource(R.string.share_live_title),
            text = when {
                !siteOk -> unavailable
                following -> stringResource(R.string.share_live_text)
                else -> stringResource(R.string.share_live_start_text)
            },
            primary = primary,
            enabled = siteOk,
            index = index,
            onClick = onLive,
        )
    }
    if (following) {
        live(0, true)
        link(1, false)
    } else {
        link(0, true)
        if (liveOffered) live(1, false)
    }
    ShareOptionCard(
        glyph = Glyph.Message,
        title = stringResource(R.string.share_text_title),
        text = stringResource(R.string.share_text_text),
        primary = false,
        enabled = true,
        index = 2,
        onClick = onText,
    )
    if (onCopy != null) {
        val interactions = remember { MutableInteractionSource() }
        Row(
            modifier = Modifier
                .then(appearModifier(3))
                .align(Alignment.CenterHorizontally)
                .padding(top = 4.dp)
                .alpha(if (siteOk) 1f else SHARE_DISABLED_ALPHA)
                .springPress(interactions)
                .clip(RoundedCornerShape(20.dp))
                .clickable(
                    interactionSource = interactions,
                    indication = LocalIndication.current,
                    enabled = siteOk,
                    role = Role.Button,
                    onClick = onCopy,
                )
                .defaultMinSize(minHeight = 44.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GlyphIcon(Glyph.Paste, color = AppColors.Blue, size = 18.dp)
            Text(
                text = stringResource(R.string.share_copy_link),
                color = AppColors.Blue,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            )
        }
    }
}

/**
 * One choice of the share sheet: a large rounded card with [glyph] in a round field, [title] and
 * one line of [text]. The [primary] card is tinted and marked "Recommended". A card that is not
 * [enabled] stays visible, faded, and does nothing. It settles in as item [index] of the sheet.
 * The sheets of the Live-Fahrt bar (#217) use the same card, with a [trailing] switch where the
 * entry is one, and its [role].
 */
@Composable
internal fun ShareOptionCard(
    glyph: Glyph,
    title: String,
    text: String,
    primary: Boolean,
    enabled: Boolean,
    index: Int,
    onClick: () -> Unit,
    role: Role = Role.Button,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interactions = remember { MutableInteractionSource() }
    val ink = if (primary) AppColors.OnBlueContainer else AppColors.Ink
    Row(
        modifier = Modifier
            .then(appearModifier(index))
            .fillMaxWidth()
            .alpha(if (enabled) 1f else SHARE_DISABLED_ALPHA)
            .springPress(interactions)
            .clip(RoundedCornerShape(20.dp))
            .background(if (primary) AppColors.BlueContainer else AppColors.Card)
            .clickable(
                interactionSource = interactions,
                indication = LocalIndication.current,
                enabled = enabled,
                role = role,
                onClick = onClick,
            )
            .defaultMinSize(minHeight = 72.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (primary) AppColors.Blue else AppColors.Chip),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(glyph, color = if (primary) AppColors.OnBlue else AppColors.Ink, size = 22.dp)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = title,
                    color = ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (primary) {
                    Text(
                        text = stringResource(R.string.share_recommended),
                        color = AppColors.OnBlue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(AppColors.Blue)
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                text = text,
                color = if (primary) AppColors.OnBlueContainer else AppColors.Muted,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing?.invoke()
    }
}

/**
 * The second step of the share sheet, shown once before the first live link: the first name the
 * live page shows ([name], empty is fine). "Share" keeps it, "Without name" shares without one;
 * either way it is not asked again (the setting is in Settings → Live link).
 */
@Composable
private fun ColumnScope.LiveNameStep(name: String, onName: (String) -> Unit, onShare: () -> Unit, onSkip: () -> Unit) {
    Column(
        modifier = Modifier.padding(start = 8.dp, end = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = stringResource(R.string.live_name_title), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        Text(
            text = stringResource(R.string.live_name_ask_text),
            color = AppColors.Muted,
            fontSize = 14.sp,
            lineHeight = 19.sp,
        )
    }
    Spacer(modifier = Modifier.height(4.dp))
    LiveNameField(value = name, onValue = onName, modifier = Modifier.fillMaxWidth())
    Spacer(modifier = Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryButton(
            text = stringResource(R.string.live_name_ask_skip),
            onClick = onSkip,
            modifier = Modifier.weight(1f),
        )
        PrimaryButton(
            text = stringResource(R.string.live_name_ask_share),
            onClick = onShare,
            modifier = Modifier.weight(1f),
            glyph = Glyph.LiveShare,
        )
    }
}
