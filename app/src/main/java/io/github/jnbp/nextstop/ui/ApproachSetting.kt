package io.github.jnbp.nextstop.ui

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import io.github.jnbp.nextstop.data.AppPrefs

/**
 * The setting "Spätere Fahrzeuge zeigen" while the app runs: whether the map of a
 * followed trip shows the vehicles of the later rides (#160) and, dashed in grey, their way
 * still to the boarding stop; without it only the traveller's own vehicle (see vehicleRides).
 * Stored in [AppPrefs.showApproaches], off by default.
 */
object ApproachSetting {
    private var state: MutableState<Boolean>? = null

    private fun state(context: Context): MutableState<Boolean> =
        state ?: mutableStateOf(AppPrefs(context).showApproaches).also { state = it }

    /** True while the approaches of the later vehicles are drawn. */
    fun shown(context: Context): Boolean = state(context).value

    fun update(context: Context, value: Boolean) {
        AppPrefs(context).showApproaches = value
        state(context).value = value
    }
}
