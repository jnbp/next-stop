package io.github.jnbp.nextstop.ui

/**
 * The rides of a followed trip whose approach is drawn: the way their vehicle still has to come
 * to the boarding stop (grey and dashed, see tripRoutes). Only with the setting "Spätere
 * Fahrzeuge zeigen" ([shown], off by default) and only for the rides not yet begun
 * ([ahead]); without the setting none, so nothing is worked out for them.
 */
fun <T> approachRides(rides: List<T>, shown: Boolean, ahead: (T) -> Boolean): List<T> =
    if (!shown) emptyList() else rides.filter(ahead)

/**
 * The rides of a followed trip whose vehicle is drawn on the map (#160), of [rides] (those not
 * over yet, in order). With the setting "Spätere Fahrzeuge zeigen" ([shown]) all of them, as
 * before. Without it only the traveller's own: the ride of the step now ([current]: the one
 * ridden, or the one walked or waited for after getting off the one before), else the first of
 * [rides]; a later ride's vehicle comes once its ride is the step, not while the traveller
 * still sits in the one before.
 */
fun <T> vehicleRides(rides: List<T>, shown: Boolean, current: T?): List<T> =
    if (shown) rides else listOfNotNull(current?.takeIf { it in rides } ?: rides.firstOrNull())
