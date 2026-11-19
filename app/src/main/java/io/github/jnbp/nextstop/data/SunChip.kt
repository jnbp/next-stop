package io.github.jnbp.nextstop.data

/*
 * The small sun chip of the compact ride card (#220): for the first rides it is shown on, it is
 * larger and says "Sonne links" in words; after that only the sun and an arrow to the side.
 */

/** The number of rides the sun chip is shown large on, while it is new. */
const val SUN_CHIP_LEARN_RIDES = 10

/** Whether the sun chip is shown large: on the first [SUN_CHIP_LEARN_RIDES] rides it showed on ([shownRides] so far). */
fun sunChipProminent(shownRides: Int): Boolean = shownRides < SUN_CHIP_LEARN_RIDES

/** A key of a ride for counting it once: its trip, else its ends and departure. */
fun sunChipRideKey(tripId: String?, from: String, to: String, departureEpochSecond: Long): String =
    tripId ?: "$from|$to|$departureEpochSecond"

/**
 * The count of rides the sun chip was shown on after it shows on the ride [key]: one more for a
 * ride not yet [counted], the same for one counted already, and no more counting once learnt.
 */
fun sunChipCount(counted: Set<String>, key: String, shownRides: Int): Int = when {
    shownRides >= SUN_CHIP_LEARN_RIDES -> shownRides
    key in counted -> shownRides
    else -> shownRides + 1
}

/**
 * The form of the sun chips on one page (#236): the first chip that shows decides, by the
 * count of rides it was shown on then ([sunChipProminent]); every later chip on the same page
 * takes the same form, so one page never mixes "Sonne links" in words with the bare sun.
 */
class SunChipPage {
    private var decided: Boolean? = null

    /** Whether the sun chips of this page are large; [shownRides] counts only for the first. */
    fun prominent(shownRides: Int): Boolean = decided ?: sunChipProminent(shownRides).also { decided = it }
}
