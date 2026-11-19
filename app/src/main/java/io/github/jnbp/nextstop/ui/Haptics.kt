package io.github.jnbp.nextstop.ui

import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.View
import java.lang.ref.WeakReference
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import io.github.jnbp.nextstop.data.UxPrefs

/** The setting "Haptik bei Bedienung" while the app runs; stored in [UxPrefs.haptics]. */
object HapticSetting {
    var on by mutableStateOf(true)
        private set
    private var loaded = false

    /** Reads the stored setting once per process. */
    fun load(context: Context) {
        if (loaded) return
        loaded = true
        on = UxPrefs(context).haptics
    }

    fun update(context: Context, value: Boolean) {
        on = value
        UxPrefs(context).haptics = value
    }
}

/** How strongly a kind of haptic feedback is felt, from the lightest to the strongest. */
internal enum class HapticStrength { Tick, Click, Confirm, Reject, Heavy }

/**
 * The strength of [type]. The types of Compose that map to the subtle "tick" primitives on
 * Android 14 and later (segment ticks, toggles, text handles) are hardly felt on a Pixel; the
 * controls of the app get a clear click instead, only the frequent ticks of a drag stay light.
 */
internal fun hapticStrength(type: HapticFeedbackType): HapticStrength = when (type) {
    HapticFeedbackType.Confirm, HapticFeedbackType.ToggleOn -> HapticStrength.Confirm
    HapticFeedbackType.Reject -> HapticStrength.Reject
    HapticFeedbackType.LongPress -> HapticStrength.Heavy
    HapticFeedbackType.SegmentFrequentTick, HapticFeedbackType.TextHandleMove -> HapticStrength.Tick
    else -> HapticStrength.Click
}

/** The constant of View.performHapticFeedback for [strength] (minSdk 31: CONFIRM and REJECT exist). */
private fun hapticConstant(strength: HapticStrength): Int = when (strength) {
    // EFFECT_TICK: light, for frequent ticks.
    HapticStrength.Tick -> HapticFeedbackConstants.CONTEXT_CLICK
    // EFFECT_CLICK: a clear click for chips, segments, switches turned off.
    HapticStrength.Click -> HapticFeedbackConstants.VIRTUAL_KEY
    HapticStrength.Confirm -> HapticFeedbackConstants.CONFIRM
    HapticStrength.Reject -> HapticFeedbackConstants.REJECT
    HapticStrength.Heavy -> HapticFeedbackConstants.LONG_PRESS
}

/**
 * The haptic feedback of the app: straight through the view with constants that are felt on
 * a Pixel (see [hapticStrength]), silent while the setting is off. It still follows the
 * system's own "touch feedback" setting.
 */
private class GatedHaptics(private val view: View) : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        if (!HapticSetting.on) return
        view.performHapticFeedback(hapticConstant(hapticStrength(hapticFeedbackType)))
    }
}

/**
 * Lets every haptic feedback of [content] follow the setting: whatever asks
 * LocalHapticFeedback below (the app's own ticks, the switches of Material) stays silent
 * while it is off, and is felt clearly while it is on. Put once around the whole app.
 */
@Composable
fun ProvideGatedHaptics(content: @Composable () -> Unit) {
    val view = LocalView.current
    val gated = remember(view) { GatedHaptics(view) }
    DisposableEffect(gated) {
        AppHaptics.attach(gated)
        onDispose { AppHaptics.detach(gated) }
    }
    CompositionLocalProvider(LocalHapticFeedback provides gated, content = content)
}

/**
 * The haptics of the app for callbacks outside a composable, such as starting or ending a
 * ride from a button; silent while the setting is off or while no screen of the app is there.
 */
object AppHaptics {
    private var current: WeakReference<HapticFeedback>? = null

    internal fun attach(feedback: HapticFeedback) {
        current = WeakReference(feedback)
    }

    internal fun detach(feedback: HapticFeedback) {
        if (current?.get() === feedback) current = null
    }

    /** Something important was done by the user: a ride started or ended. */
    fun confirm() {
        current?.get()?.performHapticFeedback(HapticFeedbackType.Confirm)
    }
}

/** The light ticks of the controls (see [rememberTicks]). */
@Stable
class Ticks internal constructor(private val feedback: HapticFeedback) {
    /** A light tick: a chip, a part of a toggle or a tab was picked. */
    fun tick() = feedback.performHapticFeedback(HapticFeedbackType.SegmentTick)

    /** A switch was turned [on] or off. */
    fun toggle(on: Boolean) = feedback.performHapticFeedback(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)

    /** A pull to refresh was let go far enough. */
    fun refresh() = feedback.performHapticFeedback(HapticFeedbackType.Confirm)

    /** Something important was done: a ride started or ended. */
    fun confirm() = feedback.performHapticFeedback(HapticFeedbackType.Confirm)

    /** A row was picked up to be dragged to another place. */
    fun pickUp() = feedback.performHapticFeedback(HapticFeedbackType.LongPress)
}

/** The ticks of the controls here, silent while the setting "Haptik bei Bedienung" is off. */
@Composable
fun rememberTicks(): Ticks {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) { Ticks(feedback) }
}
