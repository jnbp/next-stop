package io.github.jnbp.nextstop.data

/*
 * The one stop position a bus or tram ride is boarded at (#210). Pure logic, no Android.
 *
 * The big map draws the ride's boardingSpot (OsmStations.rideSpot: the position with the
 * provider's platform number, else the one OpenStreetMap lists the line at, else the nearest).
 * The walk to the ride (its small map, its "Pos." in the rows) took its target from the
 * platform number alone and had none where the provider gives no platform, which Transitous
 * leaves out for most bus and tram departures; the walk then ended at the stop's own point,
 * away from the position the big map showed. Both now take the same choice.
 */

/**
 * The stop position a bus or tram ride is boarded at: the one already on the ride
 * ([onRide], its boardingSpot), else the one rideSpot picks ([rideSpot]), else the position
 * with the provider's platform number among all of the stop ([numbered]). The same answer
 * for the big map, the walk to the ride and its rows.
 */
fun <T : Any> sharedBoardingSpot(onRide: T?, rideSpot: () -> T?, numbered: () -> T?): T? =
    onRide ?: rideSpot() ?: numbered()
