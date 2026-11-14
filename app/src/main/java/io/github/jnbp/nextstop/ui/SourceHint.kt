package io.github.jnbp.nextstop.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.Backend
import io.github.jnbp.nextstop.data.SourceHealth
import io.github.jnbp.nextstop.ui.theme.AppColors

/** The short name of a data backend, as the hint and error lines show it ("BVG"). */
fun backendName(context: Context, backend: Backend): String = context.getString(
    when (backend) {
        Backend.Vbb -> R.string.source_vbb_short
        Backend.Bvg -> R.string.source_bvg_short
        Backend.Pte -> R.string.source_pte_short
        Backend.Transitous -> R.string.source_transitous_short
    },
)

/**
 * "Daten: Transitous" while another backend than the first choice answers, because the first
 * one failed (see SourceChain); nothing while the first choice answers again.
 */
@Composable
fun SourceHint(modifier: Modifier = Modifier) {
    val answered by SourceHealth.shared.answered.collectAsState()
    val backend = answered ?: return
    val context = LocalContext.current
    Text(
        text = context.getString(R.string.source_hint, backendName(context, backend)),
        color = AppColors.Muted,
        fontSize = 12.sp,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.Chip)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** The name of a data backend as the settings list shows it ("Berlin – BVG"). */
fun backendTitle(context: Context, backend: Backend): String = context.getString(
    when (backend) {
        Backend.Vbb -> R.string.source_vbb
        Backend.Bvg -> R.string.source_bvg
        Backend.Pte -> R.string.source_pte
        Backend.Transitous -> R.string.source_transitous
    },
)
