package io.github.jnbp.nextstop.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.ui.theme.AppColors
import java.time.OffsetDateTime
import kotlinx.coroutines.flow.MutableStateFlow

/*
 * #120: "Vorschau" of a shortcut. The card in the companion and the notification of a faster way
 * offer, besides taking it and putting it aside, a look at the offered way first: the normal
 * detail of a connection, with "Nehmen" and "Ignorieren" below it.
 */

/** The shortcut whose preview is asked for (its key), by the card or the notification; null for none. */
object ShortcutPreview {
    val requested = MutableStateFlow<String?>(null)

    /** Asks for the preview of the offer [key]. */
    fun open(key: String) {
        requested.value = key
    }

    /** Closes the preview. */
    fun close() {
        requested.value = null
    }
}

/**
 * The preview of [offer] over the whole screen: its headline, the detail of the offered journey
 * as the connection list shows it, and its two answers. Back or the close button go back to the
 * companion; starting the trip from the detail takes the offer as well.
 */
@Composable
internal fun ShortcutPreviewDialog(offer: FasterOffer, now: OffsetDateTime, onTake: () -> Unit, onIgnore: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    JourneyPreviewDialog(
        caption = stringResource(R.string.r6c_preview_title),
        title = FasterWatch.offerTitle(context, offer),
        journey = offer.journey,
        now = now,
        onTake = onTake,
        onIgnore = onIgnore,
        onClose = onClose,
    )
}

/**
 * A journey over the whole screen before it is taken (#120, #164): [caption] and [title] on
 * top, the detail of [journey] as the connection list shows it, "Übernehmen" and, where given,
 * "Ignorieren" below. Back or the close button go back.
 */
@Composable
internal fun JourneyPreviewDialog(
    caption: String,
    title: String,
    journey: io.github.jnbp.nextstop.data.Journey,
    now: OffsetDateTime,
    onTake: () -> Unit,
    onIgnore: (() -> Unit)?,
    onClose: () -> Unit,
) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BackHandler(onBack = onClose)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.PlainGround)
                .safeDrawingPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RoundButton(glyph = Glyph.Close, description = stringResource(R.string.back), onClick = onClose, solid = true)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = caption,
                        color = AppColors.Muted,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                    )
                    Text(
                        text = title,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                JourneyPreview(journey = journey, now = now, onStart = onTake, modifier = Modifier.fillMaxSize())
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onIgnore != null) {
                    SecondaryButton(
                        text = stringResource(R.string.faster_ignore_action),
                        onClick = onIgnore,
                        shape = RoundedCornerShape(16.dp),
                        minHeight = 48.dp,
                    )
                }
                PrimaryButton(
                    text = stringResource(R.string.faster_take_action),
                    onClick = onTake,
                    shape = RoundedCornerShape(16.dp),
                    minHeight = 48.dp,
                )
            }
        }
    }
}
