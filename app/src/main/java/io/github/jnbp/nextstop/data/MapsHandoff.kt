package io.github.jnbp.nextstop.data

/**
 * What the button "Google Maps" does on the last way of a journey (on foot or by bike) (#143):
 * ask each time, only open Google Maps, or open it and end the trip (the companion).
 */
enum class MapsHandoff { Ask, Open, OpenAndEnd }

/**
 * What a tap on "Google Maps" of a way does: on the [lastLeg] of the journey what the user
 * chose ([remembered], [MapsHandoff.Ask] for the sheet); on every other way it simply opens.
 */
fun mapsHandoffStep(lastLeg: Boolean, remembered: MapsHandoff): MapsHandoff = if (lastLeg) remembered else MapsHandoff.Open

/** The stored choice ([MapsHandoff.name]); anything else or nothing is [MapsHandoff.Ask]. */
fun mapsHandoffOf(stored: String?): MapsHandoff = MapsHandoff.entries.firstOrNull { it.name == stored } ?: MapsHandoff.Ask

/** Within this long before the final arrival (or after it) "Google Maps" in the trip notification also ends the trip (#189). */
const val NOTIFICATION_END_HANDOFF_SECONDS = 120L

/**
 * What the "Google Maps" action of the trip notification does (#189), by the situation, never
 * asking (a notification has no sheet): on the [lastWalk] of the journey, or within
 * [NOTIFICATION_END_HANDOFF_SECONDS] of its final arrival ([secondsToArrival], negative after it),
 * Google Maps takes over and the trip ends ("Maps & beenden") as in the app, unless the user chose
 * to only open it there ([remembered] [MapsHandoff.Open]). Mid-trip, on a walk to a change
 * ([walkToChange]), it only opens Google Maps and the trip goes on. Null: no action.
 */
fun notificationMapsHandoff(lastWalk: Boolean, walkToChange: Boolean, secondsToArrival: Long, remembered: MapsHandoff): MapsHandoff? = when {
    lastWalk || secondsToArrival <= NOTIFICATION_END_HANDOFF_SECONDS ->
        if (remembered == MapsHandoff.Open) MapsHandoff.Open else MapsHandoff.OpenAndEnd
    walkToChange -> MapsHandoff.Open
    else -> null
}
