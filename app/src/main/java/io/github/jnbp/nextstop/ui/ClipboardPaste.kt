package io.github.jnbp.nextstop.ui

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.pasteSearchText
import io.github.jnbp.nextstop.ui.theme.AppColors

/*
 * The paste button at the end of a search field. It shows only while the clipboard holds text,
 * and finding that out does not read the text: only the description of the clip (its MIME
 * types) is asked for, which Android allows without the "pasted from your clipboard" notice.
 * The text itself is read when the button is tapped, and it is made one line to search for
 * (see pasteSearchText). A clip the sender marked as sensitive (a password) is not offered.
 * The button listens to changes of the clipboard while it is shown and checks again whenever
 * the window gets the focus back (Android tells an app about its clipboard only while it has
 * the focus, so a text copied in another app is seen when the user comes back).
 */

// The size of the button, in dp: a touch target inside the field, smaller than a RoundButton.
private val PASTE_BUTTON_SIZE = 44.dp

// The size of its icon, in dp.
private val PASTE_GLYPH_SIZE = 20.dp

// How small the button is when it comes in and when it goes.
private const val PASTE_HIDDEN_SCALE = 0.6f

/** True when the clipboard holds text that may be offered; reads only the description of the clip. */
private fun clipboardOffersText(clipboard: ClipboardManager): Boolean = try {
    val description = if (clipboard.hasPrimaryClip()) clipboard.primaryClipDescription else null
    description != null &&
        (description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) || description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)) &&
        !clipIsSensitive(description)
} catch (e: SecurityException) {
    false
}

// A clip the sending app marked as sensitive (Android 13 on): a password, a code.
private fun clipIsSensitive(description: ClipDescription): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        description.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true

/** The text of the clip, read on a tap; null when there is none or Android refuses it. */
private fun readClipboardText(context: Context, clipboard: ClipboardManager): String? {
    return try {
        val clip = clipboard.primaryClip
        if (clip == null || clip.itemCount == 0) null else clip.getItemAt(0).coerceToText(context)?.toString()
    } catch (e: SecurityException) {
        null
    }
}

/** Whether the clipboard holds text, followed while this is shown. */
@Composable
private fun rememberClipboardOffersText(clipboard: ClipboardManager?): Boolean {
    var offers by remember { mutableStateOf(false) }
    DisposableEffect(clipboard) {
        if (clipboard == null) return@DisposableEffect onDispose {}
        val listener = ClipboardManager.OnPrimaryClipChangedListener { offers = clipboardOffersText(clipboard) }
        offers = clipboardOffersText(clipboard)
        clipboard.addPrimaryClipChangedListener(listener)
        onDispose { clipboard.removePrimaryClipChangedListener(listener) }
    }
    // Back in the app (after copying elsewhere), the clipboard is asked again.
    val focused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(focused, clipboard) {
        if (focused && clipboard != null) offers = clipboardOffersText(clipboard)
    }
    return offers
}

/**
 * A small paste icon for the end of a search field: shown, with a short pop, only while the
 * clipboard holds text; a tap reads it and hands it to [onPaste] as one line to search for.
 */
@Composable
fun ClipboardPasteButton(onPaste: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val clipboard = remember(context) { context.getSystemService(ClipboardManager::class.java) }
    val offers = rememberClipboardOffersText(clipboard)
    val animate = rememberAnimationsOn()
    AnimatedVisibility(
        visible = offers,
        modifier = modifier,
        enter = if (animate) {
            fadeIn(Motion.fadeIn()) + scaleIn(Motion.pop(), initialScale = PASTE_HIDDEN_SCALE)
        } else {
            EnterTransition.None
        },
        exit = if (animate) {
            fadeOut(Motion.fadeOut()) + scaleOut(Motion.quick(), targetScale = PASTE_HIDDEN_SCALE)
        } else {
            ExitTransition.None
        },
    ) {
        val description = stringResource(R.string.paste_from_clipboard)
        val interactions = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .semantics { contentDescription = description }
                .springPress(interactions)
                .clip(CircleShape)
                .clickable(
                    interactionSource = interactions,
                    indication = LocalIndication.current,
                    role = Role.Button,
                ) {
                    val text = clipboard?.let { readClipboardText(context, it) }?.let { pasteSearchText(it) }
                    if (text != null) onPaste(text)
                }
                .size(PASTE_BUTTON_SIZE),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(Glyph.Paste, color = AppColors.Muted, size = PASTE_GLYPH_SIZE)
        }
    }
}

/**
 * The ✕ at the end of a text field, beside the paste button: shown, with the same short pop,
 * only while there is text; a tap runs [onClear] (empty the field, the keyboard stays).
 */
@Composable
fun FieldClearButton(visible: Boolean, onClear: () -> Unit, modifier: Modifier = Modifier) {
    val animate = rememberAnimationsOn()
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (animate) {
            fadeIn(Motion.fadeIn()) + scaleIn(Motion.pop(), initialScale = PASTE_HIDDEN_SCALE)
        } else {
            EnterTransition.None
        },
        exit = if (animate) {
            fadeOut(Motion.fadeOut()) + scaleOut(Motion.quick(), targetScale = PASTE_HIDDEN_SCALE)
        } else {
            ExitTransition.None
        },
    ) {
        val description = stringResource(R.string.clear_field)
        val interactions = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .semantics { contentDescription = description }
                .springPress(interactions)
                .clip(CircleShape)
                .clickable(
                    interactionSource = interactions,
                    indication = LocalIndication.current,
                    role = Role.Button,
                    onClick = onClear,
                )
                .size(PASTE_BUTTON_SIZE),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(Glyph.Close, color = AppColors.Muted, size = CLEAR_GLYPH_SIZE)
        }
    }
}

// The ✕ is drawn a little smaller than the clipboard, so both look the same weight.
private val CLEAR_GLYPH_SIZE = 16.dp
