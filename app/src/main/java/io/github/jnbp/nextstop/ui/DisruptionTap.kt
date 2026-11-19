package io.github.jnbp.nextstop.ui

import android.content.Context
import android.content.Intent
import io.github.jnbp.nextstop.MainActivity
import io.github.jnbp.nextstop.data.Disruption
import io.github.jnbp.nextstop.data.DisruptionTapRequest
import kotlinx.coroutines.flow.MutableStateFlow

internal const val ACTION_OPEN_DISRUPTION = "io.github.jnbp.nextstop.OPEN_DISRUPTION"
private const val EXTRA_DISRUPTION_IDS = "disruptionIds"
private const val EXTRA_DISRUPTION_PROVIDER_IDS = "disruptionProviderIds"

/**
 * A tap on a notification of the disruption watch: "My routes" opens (AppRoot) and its section
 * of the disruptions on the user's ways shows the notices reported (FavoritesStartPage), or
 * says they are no longer current ([gone], shown by AppRoot as a snackbar).
 */
object DisruptionTap {
    /** The tap to follow; null once the page of "My routes" took it. */
    val pending = MutableStateFlow<DisruptionTapRequest?>(null)

    /** True when what was reported is no longer current; AppRoot shows that and clears it. */
    val gone = MutableStateFlow(false)

    /** The intent that opens the app on the notices [fresh] (empty: the section as it is). */
    fun intent(context: Context, fresh: List<Disruption>): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN_DISRUPTION)
            .putExtra(EXTRA_DISRUPTION_IDS, fresh.map { it.id }.toTypedArray())
            .putExtra(EXTRA_DISRUPTION_PROVIDER_IDS, fresh.flatMap { it.providerIds }.distinct().toTypedArray())
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    /** Takes the tap of [intent] when it is one on a notification of the watch. */
    fun follow(intent: Intent?) {
        if (intent?.action != ACTION_OPEN_DISRUPTION) return
        pending.value = DisruptionTapRequest(
            ids = intent.getStringArrayExtra(EXTRA_DISRUPTION_IDS)?.toList().orEmpty(),
            providerIds = intent.getStringArrayExtra(EXTRA_DISRUPTION_PROVIDER_IDS)?.toList().orEmpty(),
        )
    }
}
