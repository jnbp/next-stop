package io.github.jnbp.nextstop.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.Leg
import io.github.jnbp.nextstop.data.SunSide
import io.github.jnbp.nextstop.data.SunWeather
import io.github.jnbp.nextstop.data.UxPrefs
import io.github.jnbp.nextstop.data.shownSunSide
import io.github.jnbp.nextstop.data.sunPosition
import io.github.jnbp.nextstop.data.sunIntroDue
import io.github.jnbp.nextstop.data.sunChipCount
import io.github.jnbp.nextstop.data.sunShines
import io.github.jnbp.nextstop.ui.theme.AppColors

/*
 * The hint of the sunny side as the user sees it: switchable (in its info dialog and under
 * Settings > Appearance), shown only where the sun really shines on the ride (SunWeather), and
 * forced on every ride by the developer option "Sonnenseite testen".
 */

/** The setting "Sonnenseite anzeigen" and the developer test, while the app runs; stored in [UxPrefs]. */
object SunSideSetting {
    var on by mutableStateOf(true)
        private set

    /** Developer option: the hint shows on every ride, whatever the sun and the weather. */
    var test by mutableStateOf(false)
        private set

    /** True once the explanation of the sunny side was seen (see [SunSideIntroSheet]). */
    var introSeen by mutableStateOf(true)
        private set

    /** The hint row whose explanation is open now, so only one opens at a time; null for none. */
    var introOwner by mutableStateOf<Any?>(null)
        private set

    /** How many rides the sun chip was shown on (#220; see sunChipProminent). */
    var chipRides by mutableStateOf(0)
        private set

    // The rides counted in this process, so a ride shown in the detail and the companion counts once.
    private val chipCounted = mutableSetOf<String>()

    private var loaded = false

    /** Reads the stored settings once per process; cheap to call again. */
    fun load(context: Context) {
        if (loaded) return
        loaded = true
        // The rail tunnels are read in the background (see RailTunnelAsset), never here.
        RailTunnelAsset.warm(context)
        val prefs = UxPrefs(context)
        on = prefs.sunSide
        test = prefs.sunSideTest
        introSeen = prefs.sunSideIntroSeen
        chipRides = prefs.sunChipRides
    }

    /** The sun chip was shown on the ride [key]: counted once per ride, while it is still new. */
    fun countChipRide(context: Context, key: String) {
        load(context)
        val next = sunChipCount(chipCounted, key, chipRides)
        chipCounted += key
        if (next != chipRides) {
            chipRides = next
            UxPrefs(context).sunChipRides = next
        }
    }

    /** A hint of the sunny side is shown by [owner]: opens the explanation there where it is due. */
    fun claimIntro(owner: Any, hintShown: Boolean) {
        if (sunIntroDue(introSeen, hintShown, introOwner != null)) introOwner = owner
    }

    /** [owner] left before its explanation was closed: the next hint may open it. */
    fun releaseIntro(owner: Any) {
        if (introOwner === owner) introOwner = null
    }

    /** The explanation was closed: it never opens by itself again. */
    fun finishIntro(context: Context) {
        load(context)
        introSeen = true
        introOwner = null
        UxPrefs(context).sunSideIntroSeen = true
    }

    /** Developer option: the explanation opens again with the next hint. */
    fun resetIntro(context: Context) {
        load(context)
        introSeen = false
        UxPrefs(context).sunSideIntroSeen = false
        // The sun chip starts large again too (#220).
        chipRides = 0
        chipCounted.clear()
        UxPrefs(context).sunChipRides = 0
    }

    fun update(context: Context, value: Boolean) {
        load(context)
        on = value
        UxPrefs(context).sunSide = value
    }

    fun updateTest(context: Context, value: Boolean) {
        load(context)
        test = value
        UxPrefs(context).sunSideTest = value
    }
}

/** The way of [leg] the hint is worked out along: its path, else its stops, else its two ends. */
internal fun legWay(leg: Leg): List<Pair<Double, Double>> =
    leg.path.ifEmpty { leg.stopovers.mapNotNull { it.place?.position } }
        .ifEmpty { listOfNotNull(leg.fromPlace?.position, leg.toPlace?.position) }

/**
 * True where the sun really shines on [leg]: above the horizon in the middle of the ride, at
 * its middle point, and a forecast cloud cover below SUNNY_CLOUD_COVER_MAX. False without
 * network or forecast. With [mayAsk] false only a cached forecast counts.
 */
internal suspend fun legSunShines(leg: Leg, mayAsk: Boolean): Boolean {
    val ask = sunAskOf(leg) ?: return false
    val cover = SunWeather.cloudCover(ask.latitude, ask.longitude, ask.from, ask.to, mayAsk)
    return sunShines(cover, ask.elevation)
}

/**
 * As [legSunShines], but never waiting: only a kept forecast counts, and where none is kept it
 * is asked in [scope] in the background (see SunWeather.keptCloudCover), for a later call.
 */
internal fun legSunShinesKept(leg: Leg, mayAsk: Boolean, scope: kotlinx.coroutines.CoroutineScope): Boolean {
    val ask = sunAskOf(leg) ?: return false
    val cover = SunWeather.keptCloudCover(ask.latitude, ask.longitude, ask.from, ask.to, mayAsk, scope)
    return sunShines(cover, ask.elevation)
}

// Where and when the forecast of a ride is asked for: the middle of its way, its times, the sun's height.
private class SunAsk(val latitude: Double, val longitude: Double, val from: Long, val to: Long, val elevation: Double)

// Null where nothing is to be asked: no way, or the sun below the horizon (at night nothing is asked at all).
private fun sunAskOf(leg: Leg): SunAsk? {
    val way = legWay(leg)
    if (way.isEmpty()) return null
    val (latitude, longitude) = way[way.size / 2]
    val from = leg.departure.toInstant().toEpochMilli()
    val to = leg.arrival.toInstant().toEpochMilli()
    val elevation = sunPosition((from + to) / 2, latitude, longitude).elevation
    if (elevation <= 0.0) return null
    return SunAsk(latitude, longitude, from, to, elevation)
}

/**
 * The sunny side to show on [leg] now, or null: off in the settings, a walk, no clear side,
 * or no sun (night, clouds, no weather). The developer test shows it on every ride. The
 * forecast is asked only while this is composed, so only in the foreground or the companion;
 * at the battery saver's level "Extrem" only a forecast already kept counts.
 */
@Composable
fun rememberShownSunSide(leg: Leg): SunSide? {
    val context = LocalContext.current
    SunSideSetting.load(context)
    // Worked out again once the tunnels are read (until then no hint on rails in Germany, see legSunSide).
    val tunnelsRead = RailTunnelAsset.ready
    val side = remember(leg, tunnelsRead) { legSunSide(leg) }
    val on = SunSideSetting.on && !leg.walking && !leg.cancelled
    val test = SunSideSetting.test
    val ask = on && !test && side != null
    val mayAsk = !BatterySaver.extreme
    // Keyed by what the answer depends on, not by the leg itself: a live update of the same
    // ride neither asks again nor lets the hint blink.
    val key = if (ask) "${leg.from}|${leg.to}|${leg.departure.toEpochSecond() / 600}" else null
    val shines by produceState(initialValue = false, key, mayAsk) {
        value = if (ask) legSunShines(leg, mayAsk) else false
    }
    return shownSunSide(on, test, side, shines)
}

/**
 * What the hint means, with the moving picture of the first explanation (the sun on [side], see
 * SunSidePicture, #132), and the switch to show it or not.
 */
@Composable
fun SunSideInfoDialog(side: SunSide, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.sun_side_info_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                val description = stringResource(R.string.r5_sun_intro_picture)
                SunSidePicture(
                    sunLeft = side == SunSide.Left,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .semantics { contentDescription = description },
                )
                SunChipLegend(side)
                Text(text = stringResource(R.string.sun_side_info_text))
                val ticks = rememberTicks()
                // The whole row is the switch: one touch target, read as one switch.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = SunSideSetting.on, role = Role.Switch) { value ->
                            ticks.toggle(value)
                            SunSideSetting.update(context, value)
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.sun_side_switch),
                        color = AppColors.Ink,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = SunSideSetting.on,
                        onCheckedChange = null,
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.hint_understood)) }
        },
    )
}

/** The setting "Sonnenseite anzeigen", for the settings, with a small ride card and its hint as the preview. */
@Composable
internal fun SunSideRow(shape: androidx.compose.ui.graphics.Shape) {
    val context = LocalContext.current
    SunSideSetting.load(context)
    SwitchRow(
        shape = shape,
        title = stringResource(R.string.sun_side_switch),
        text = stringResource(R.string.sun_side_setting_text),
        checked = SunSideSetting.on,
        onChange = { value -> SunSideSetting.update(context, value) },
        preview = { SunSidePreview(SunSideSetting.on) },
    )
}

/**
 * The mark of the sun chip with what it means (#269): shown with the explanation, so the small
 * car on a ride is known again, and that a tap on it opens this explanation.
 */
@Composable
internal fun SunChipLegend(side: SunSide) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SunWagonMark(left = side == SunSide.Left, height = 22.dp, ink = AppColors.Muted)
        Text(text = stringResource(R.string.r9_sun_chip_legend), color = AppColors.Muted, fontSize = 13.sp, lineHeight = 18.sp)
    }
}
