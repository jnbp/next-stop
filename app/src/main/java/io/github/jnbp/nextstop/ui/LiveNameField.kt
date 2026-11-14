package io.github.jnbp.nextstop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.AppPrefs
import io.github.jnbp.nextstop.ui.theme.AppColors
import io.github.jnbp.nextstop.ui.theme.AppFont

/**
 * The field for the first name shown on shared live links ("<name> ist unterwegs nach …"),
 * used in the settings and in the setup wizard. Empty is fine: the page then says
 * "Unterwegs nach …".
 */
@Composable
fun LiveNameField(value: String, onValue: (String) -> Unit, modifier: Modifier = Modifier) {
    BasicTextField(
        value = value,
        onValueChange = { onValue(it.replace("\n", " ").take(AppPrefs.LIVE_NAME_MAX)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Done,
        ),
        textStyle = TextStyle(color = AppColors.Ink, fontSize = 14.sp, fontFamily = AppFont),
        cursorBrush = SolidColor(AppColors.Blue),
        modifier = modifier
            .width(240.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.Chip)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        decorationBox = { innerTextField ->
            Box {
                if (value.isEmpty()) {
                    Text(text = stringResource(R.string.live_name_placeholder), color = AppColors.Muted, fontSize = 14.sp, maxLines = 1)
                }
                innerTextField()
            }
        },
    )
}
