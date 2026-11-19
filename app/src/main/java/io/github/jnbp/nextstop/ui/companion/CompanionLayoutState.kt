package io.github.jnbp.nextstop.ui.companion

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.jnbp.nextstop.data.CompanionLayoutMode
import io.github.jnbp.nextstop.data.CompanionLayoutPrefs
import io.github.jnbp.nextstop.data.PickerMode
import io.github.jnbp.nextstop.data.DiagLog
import io.github.jnbp.nextstop.data.RideStage
import io.github.jnbp.nextstop.data.autoLegKey
import io.github.jnbp.nextstop.data.companionMapChoice
import io.github.jnbp.nextstop.data.effectiveCompanionLayout
import io.github.jnbp.nextstop.data.companionHidesTabs
import io.github.jnbp.nextstop.data.companionShowsMap
import io.github.jnbp.nextstop.ui.BatterySaver
import io.github.jnbp.nextstop.ui.CompanionMapFocus

private const val LOG_TAG = "CompanionLayout"

/**
 * The companion view while the app runs (see [CompanionLayoutMode]): the one place the
 * companion, the settings and the root of the app ask. Snapshot state, so whatever reads it
 * follows a change at once. The root of the app reads [showsMap] and [hidesTabs] directly, so
 * it hands nothing new down (its captures stay as they are).
 */
object CompanionLayout {
    /** The setting as chosen. */
    var mode by mutableStateOf(CompanionLayoutMode.Auto)
        private set

    /** "Anschluss unterwegs wählen" (#138, #201), as chosen; the next one open by default (#203). */
    var pickers by mutableStateOf(PickerMode.NextOpen)
        private set

    /** Where the traveller stands on the followed trip (see [follow]); null before any. */
    var stage by mutableStateOf<RideStage?>(null)
        private set

    /** The map button on this leg ([autoLegKey]) in "Ohne Karte": null while untouched; never stored. */
    var legChoice by mutableStateOf<Boolean?>(null)
        private set

    /** The map button in "Automatisch": stored, kept for every later companion until pressed again (#65). */
    var savedChoice by mutableStateOf<Boolean?>(null)
        private set

    /**
     * Before boarding, the map of the companion frames the traveller and the stop, not the vehicle
     * far away; "Wo ist mein Zug?" sets this until the next stage (#91). Read by the root of the app.
     */
    var vehicleShown by mutableStateOf(false)

    /**
     * What the companion map frames after its locate button (#104): the trip as before, the
     * user's own position up close, or the route still to go (see CompanionMapFocus). Read by
     * the root of the app, which frames for it; set back to the trip when the trip page opens.
     */
    var mapFocus by mutableStateOf(CompanionMapFocus.Trip)

    /** Counts the asks to frame the trip again (the chip of #91): the root frames anew for each. */
    var frameAsks by mutableIntStateOf(0)
        private set

    /** Frames the trip again as it frames itself (#91), also after the own position or the route was shown. */
    fun askTripFrame() {
        mapFocus = CompanionMapFocus.Trip
        frameAsks++
    }

    /** Counts the times the sheet over the companion map settled at another height: the root frames the trip into the room left (#137). */
    var coverAsks by mutableIntStateOf(0)
        private set

    /** The sheet over the map settled at another height (see [coverAsks]). */
    fun coverChanged() {
        coverAsks++
    }

    private var legKey: String? = null

    /** The map shown for a moment (a walk shown on it) without changing the remembered choice. */
    var mapPeek by mutableStateOf(false)
        private set

    /**
     * True while a new companion view is the page in front: the root of the app lets the tab
     * bar sink away then. Set by the view itself, so the root takes along nothing new.
     */
    var onScreen by mutableStateOf(false)
        internal set

    private var installed = false

    /** The view in effect: calm without the map while the battery saver is active. */
    val shown: CompanionLayoutMode get() = effectiveCompanionLayout(mode, BatterySaver.active)

    /**
     * True while the companion shows the map ("Mit Karte", the classic view, walking in "Automatisch", or a walk shown on it).
     * While the battery saver is active a map button pressed before it began no longer counts:
     * the view stays calm (the button is not offered then); a walk shown on the map still shows it.
     */
    val showsMap: Boolean
        get() = companionShowsMap(shown, stage, if (BatterySaver.active) null else companionMapChoice(shown, savedChoice, legChoice)) || mapPeek

    /** True for the new views (calm and with map), false for the classic one. */
    val modern: Boolean get() = shown != CompanionLayoutMode.Classic

    /** True where the tab bar goes away while the companion is open. */
    val hidesTabs: Boolean get() = companionHidesTabs(shown)

    /** Whether the map button is offered: in "Automatisch" and "Ohne Karte", not while saving battery. */
    val mapToggleOffered: Boolean get() = shown == CompanionLayoutMode.Auto ||
        (shown == CompanionLayoutMode.NoMap && !BatterySaver.active)

    /**
     * The companion reports where the traveller stands ([stage] on the leg [legIndex] of the
     * journey [journeyId]); a new leg ends the choice of the map button. Cheap when nothing changed.
     */
    fun follow(journeyId: String, stage: RideStage, legIndex: Int) {
        val key = autoLegKey(journeyId, stage, legIndex)
        if (this.stage != stage) {
            this.stage = stage
            if (vehicleShown) vehicleShown = false
        }
        if (key == legKey) return
        legKey = key
        if (legChoice != null) legChoice = null
    }

    /** Reads the stored choices; once per process. */
    fun install(context: Context) {
        if (installed) return
        installed = true
        val prefs = CompanionLayoutPrefs(context.applicationContext)
        mode = prefs.mode
        savedChoice = prefs.mapChoice
        pickers = prefs.pickers
    }

    /** Chooses and stores how the connection pickers show at first (#138). */
    fun changePickers(context: Context, value: PickerMode) {
        install(context)
        pickers = value
        CompanionLayoutPrefs(context.applicationContext).pickers = value
        DiagLog.i(LOG_TAG) { "pickers $value" }
    }

    /** Chooses and stores [value]. */
    fun change(context: Context, value: CompanionLayoutMode) {
        install(context)
        mode = value
        val prefs = CompanionLayoutPrefs(context.applicationContext)
        prefs.mode = value
        // A new setting starts afresh: the stored map button of "Automatisch" no longer counts.
        if (savedChoice != null) {
            savedChoice = null
            prefs.mapChoice = null
        }
        DiagLog.i(LOG_TAG) { "mode $value" }
    }

    /**
     * Shows or hides the map with the map button: in "Automatisch" stored for this and every
     * later companion (#65), in "Ohne Karte" for this leg only.
     */
    fun setMap(context: Context, on: Boolean) {
        install(context)
        mapPeek = false
        if (shown == CompanionLayoutMode.Auto) {
            if (savedChoice == on) return
            savedChoice = on
            CompanionLayoutPrefs(context.applicationContext).mapChoice = on
            DiagLog.i(LOG_TAG) { "map ${if (on) "on" else "off"}, kept" }
            return
        }
        if (legChoice == on) return
        legChoice = on
        DiagLog.i(LOG_TAG) { "map ${if (on) "on" else "off"} for this leg" }
    }

    /** Shows the map for a moment ([on]) or ends that; the remembered choice stays. */
    fun peekMap(on: Boolean) {
        mapPeek = on
    }
}
