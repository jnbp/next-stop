package io.github.jnbp.nextstop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import io.github.jnbp.nextstop.data.Stop
import io.github.jnbp.nextstop.data.hereFallbackLabel
import io.github.jnbp.nextstop.data.hereFieldLabel
import io.github.jnbp.nextstop.data.hereKey
import io.github.jnbp.nextstop.data.hereRounded
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The addresses of "Aktueller Standort" as the start or the destination (#207, see
 * HereAddressText.kt): looked up with the device's geocoder (as a place marked on the map,
 * see reverseGeocode), once per rounded position, only while a field shows it. The fields show
 * the train of the search ("Adresse wird gesucht…") while [fromResolving] or [toResolving].
 */
internal object HereAddress {
    val fromResolving = mutableStateOf(false)
    val toResolving = mutableStateOf(false)

    // Rounded position -> label; null where nothing was found. Only in memory, a few at most.
    val labels = mutableStateMapOf<String, String?>()
}

// A lookup is given up after this long; the field then keeps the name with the coordinates.
// 8 s let the train of the search stand for long on a slow geocoder (#261); and the limit is
// now kept (see awaitAtMost), not only checked once the blocking geocoder answered.
private const val HERE_RESOLVE_MILLIS = 4_000L

// Where lookups run: one that is given up may finish in the background without holding the field.
private val HERE_LOOKUPS = CoroutineScope(SupervisorJob() + Dispatchers.IO)

// Labels kept in memory at most; the oldest go first.
private const val HERE_LABELS_KEPT = 16

/**
 * The label of [place] for a field where it is "Aktueller Standort" ([currentId]) and its
 * position is a real one ([enabled]: not the fallback point): the address once it is known,
 * the name with the coordinates where none was found, and null while it is looked up (and for
 * any other place). [resolving] is true while it is looked up.
 */
@Composable
fun hereLabel(place: Stop?, currentId: String, enabled: Boolean, resolving: MutableState<Boolean>): String? {
    val context = LocalContext.current
    val position = place?.takeIf { it.id == currentId && enabled }?.position
    val key = position?.let { hereKey(it) }
    // The key shown now: a lookup cancelled for a newer one must not end the newer one's "searching".
    val shownKey = rememberUpdatedState(key)
    LaunchedEffect(key) {
        if (key == null || position == null || HereAddress.labels.containsKey(key)) {
            resolving.value = false
            return@LaunchedEffect
        }
        resolving.value = true
        try {
            val at = hereRounded(position)
            val app = context.applicationContext
            val details = awaitAtMost(HERE_RESOLVE_MILLIS, HERE_LOOKUPS) { reverseGeocode(app, at.first, at.second) }
            if (HereAddress.labels.size >= HERE_LABELS_KEPT) HereAddress.labels.remove(HereAddress.labels.keys.first())
            HereAddress.labels[key] = hereFieldLabel(details?.title, details?.address)
        } finally {
            if (shownKey.value == key) resolving.value = false
        }
    }
    if (key == null || position == null || !HereAddress.labels.containsKey(key)) return null
    return HereAddress.labels[key] ?: hereFallbackLabel(place.name, hereRounded(position))
}
