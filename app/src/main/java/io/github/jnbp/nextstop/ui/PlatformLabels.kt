package io.github.jnbp.nextstop.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.PlatformKind
import io.github.jnbp.nextstop.data.shownPlatform

/**
 * A platform as people read it, for a ride of [product]: "Gleis 4" for a track, "Steig 13"
 * for a bay of a tram or bus stop, texts such as "Pos. 11" as they are; null where there is
 * none or it is not plausible (an id of a stop point such as "78", see shownPlatform).
 */
@Composable
fun platformLabel(platform: String?, product: String? = null): String? {
    val shown = shownPlatform(platform, product) ?: return null
    return when (shown.kind) {
        PlatformKind.Track -> stringResource(R.string.platform, shown.name)
        PlatformKind.Bay -> stringResource(R.string.platform_bay, shown.name)
        PlatformKind.Text -> shown.name
    }
}

/** [platformLabel] outside of a composition: for notifications and shared texts. */
fun platformLabel(context: Context, platform: String?, product: String? = null): String? {
    val shown = shownPlatform(platform, product) ?: return null
    return when (shown.kind) {
        PlatformKind.Track -> context.getString(R.string.platform, shown.name)
        PlatformKind.Bay -> context.getString(R.string.platform_bay, shown.name)
        PlatformKind.Text -> shown.name
    }
}
