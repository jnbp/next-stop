package io.github.jnbp.nextstop.data

/**
 * The setting "Anschluss unterwegs wählen" (#138, B4, #201): how the connection pickers at the
 * changes show at first, in the companion and in the connection detail (there [NextOpen] opens
 * the first change; see detailPickerOpen). The small maps of walks have the same three ways
 * (#202, see walkMapModeOf).
 * - [Folded] ("Eingeklappt"): every picker is a small link, opened by hand.
 * - [AllOpen] ("Alle ausgeklappt"): every picker starts open where its departures are there;
 *   it can be folded by hand, as in [NextOpen] (r7plan). The departures are loaded for one change at a time (the next one, or one picked by
 *   hand); the link of another change loads them there and opens it.
 * - [NextOpen] ("Nur nächstes ausgeklappt", the default since #203): the picker of the next change
 *   that matters opens by itself; it can be folded by hand, the others are links.
 * Stored by name; [NextOpen] for none or an unknown one.
 */
enum class PickerMode {
    Folded, AllOpen, NextOpen;

    companion object {
        fun fromStored(name: String?): PickerMode = entries.firstOrNull { it.name == name } ?: NextOpen

        /** The order the settings offer them in: "Eingeklappt", "Nur nächstes ausgeklappt", "Alle ausgeklappt" (#201). */
        val shownOrder: List<PickerMode> = listOf(Folded, NextOpen, AllOpen)

        /**
         * The choice of "Anschluss unterwegs wählen" (#201) as one of four: 0 "Aus", then the
         * pickers in [shownOrder]. [on] is the stored on/off, [mode] how they show when on.
         */
        fun choiceIndex(on: Boolean, mode: PickerMode): Int = if (on) 1 + shownOrder.indexOf(mode) else 0

        /** The picker mode of the choice at [index] (see [choiceIndex]); null for "Aus". */
        fun ofChoice(index: Int): PickerMode? = shownOrder.getOrNull(index - 1)
    }
}

/**
 * "Minikarte für Fußwege" (#202), the same three ways for the small maps of walks: its stored
 * name ([stored]), else the old switch "immer aufgeklappt" ([oldAlwaysOpen]: on is
 * [PickerMode.AllOpen]), else the default [PickerMode.NextOpen].
 */
fun walkMapModeOf(stored: String?, oldAlwaysOpen: Boolean?): PickerMode =
    PickerMode.entries.firstOrNull { it.name == stored } ?: if (oldAlwaysOpen == true) PickerMode.AllOpen else PickerMode.NextOpen

/**
 * Whether the small map of a walk starts unfolded in [mode]; [next] tells whether its walk is
 * the next one (on the detail page the first walk, in the Live-Fahrt the next walk or change not
 * yet over), null where the page does not say.
 */
fun walkMapStartsOpen(mode: PickerMode, next: Boolean?): Boolean = when (mode) {
    PickerMode.AllOpen -> true
    PickerMode.Folded -> false
    PickerMode.NextOpen -> next == true
}

/**
 * The key of the next walk among [items] (in their order on the page): the first that [hasMap]
 * and is not [over]; null for none. Its small map is the one "Nur nächste ausgeklappt" opens.
 */
fun <T> nextWalkKey(items: List<T>, key: (T) -> String, hasMap: (T) -> Boolean, over: (T) -> Boolean = { false }): String? =
    items.firstOrNull { !over(it) && hasMap(it) }?.let(key)
