package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.PickerMode

/*
 * The rules of the change parts of the connection detail that the Live-Fahrt settles by its own
 * state, free of Compose so they can be tested (B4 and the done look).
 */

/**
 * Whether the connection picker of the change keeping [keepLegs] legs is open on the detail page
 * (B4), by the setting "Anschlusswahl" ([mode]) that the Live-Fahrt follows too: [PickerMode.Folded]
 * opens only what was opened by hand, [PickerMode.NextOpen] the first change of the journey
 * ([firstKeepLegs]) unless folded by hand, [PickerMode.AllOpen] every one unless folded by hand
 * (r7plan: the setting says only how they start). [toggled] is true where the link of this change
 * was tapped (it turns the change from how the setting shows it).
 */
fun detailPickerOpen(mode: PickerMode, keepLegs: Int, firstKeepLegs: Int?, toggled: Boolean): Boolean = when (mode) {
    PickerMode.AllOpen -> !toggled
    PickerMode.NextOpen -> (keepLegs == firstKeepLegs) != toggled
    PickerMode.Folded -> toggled
}

/**
 * Whether a walk or change of the detail page counts as over: only while the journey is
 * [followed] as the Live-Fahrt, once the ride after it has left ([nextDeparture], epoch millis)
 * by [now]. A page of a journey that is not followed shows nothing as over.
 */
fun detailPartOver(followed: Boolean, nextDeparture: Long?, now: Long): Boolean =
    followed && nextDeparture != null && now >= nextDeparture

/** The next of [departures] (epoch millis) after [now], when the detail has to look again; null for none. */
fun detailNextWake(departures: List<Long>, now: Long): Long? = departures.filter { it > now }.minOrNull()

/**
 * Whether the buttons of a blue walk or change card fit one row as chips (#191): [widths] of
 * those shown ("Karte zeigen", "Karte öffnen", Google Maps; 0 for one not there), [gap] between
 * two, in [room]. Otherwise "Karte öffnen" becomes a round icon at the end of the row.
 */
fun blueActionsFit(widths: List<Int>, gap: Int, room: Int): Boolean {
    val shown = widths.filter { it > 0 }
    if (shown.isEmpty()) return true
    return shown.sum() + gap * (shown.size - 1) <= room
}

/** How the buttons of a blue walk or change card stand (#191, #206). */
enum class BlueActionsLook {
    /** All as chips with their labels in one row. */
    Chips,

    /** "Karte öffnen" as a round icon at the end; Google Maps keeps its label, in two lines where it must. */
    OpenAsIcon,

    /** "Karte öffnen" and Google Maps both as round icons: the label would have to break a word. */
    IconsOnly,
}

/**
 * The look of the buttons of a blue card (#206), by the widths they want in one line: [fold] (the
 * map button with the label it shows now, "Karte ausblenden" while the map is open), [open],
 * [navigate] (0 for one not there), [navigateWord] the width of Google Maps at its longest word
 * (its label may take two lines, never break a word), [icon] a round icon button, [gap], [room].
 */
fun blueActionsLook(fold: Int, open: Int, navigate: Int, navigateWord: Int, icon: Int, gap: Int, room: Int): BlueActionsLook {
    if (blueActionsFit(listOf(fold, open, navigate), gap, room)) return BlueActionsLook.Chips
    if (navigate <= 0) return BlueActionsLook.OpenAsIcon
    // What Google Maps has left beside the map button and the icon of "Karte öffnen".
    val before = listOf(fold, if (open > 0) icon else 0).filter { it > 0 }
    val rest = room - before.sum() - gap * before.size
    return if (rest >= navigateWord) BlueActionsLook.OpenAsIcon else BlueActionsLook.IconsOnly
}
