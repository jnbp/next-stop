package io.github.jnbp.nextstop.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.jnbp.nextstop.R

/** Choices that are explained once, the first time the user makes them. */
enum class FirstHint { VeryRisky, EveryMinute }

/** Explains [hint] in a short dialog; the user closes it with one button. */
@Composable
fun FirstHintDialog(hint: FirstHint, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    when (hint) {
                        FirstHint.VeryRisky -> R.string.hint_very_risky_title
                        FirstHint.EveryMinute -> R.string.hint_every_minute_title
                    },
                ),
            )
        },
        text = {
            Text(
                text = stringResource(
                    when (hint) {
                        FirstHint.VeryRisky -> R.string.hint_very_risky_text
                        FirstHint.EveryMinute -> R.string.hint_every_minute_text
                    },
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.hint_understood)) }
        },
    )
}
