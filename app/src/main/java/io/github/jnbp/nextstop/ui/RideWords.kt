package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.R

/*
 * #212: the words for a ride by its kind (RideKind), so a bus is never called a train. Where the
 * kind is not known the texts say "Fahrt".
 */

/** "Bus verpasst", "Zug verpasst", … ; "Fahrt verpasst" where the kind is not known. */
fun missedRideRes(kind: RideKind): Int = when (kind) {
    RideKind.Bus -> R.string.r7p_missed_bus
    RideKind.Tram -> R.string.r7p_missed_tram
    RideKind.UBahn -> R.string.r7p_missed_ubahn
    RideKind.SBahn -> R.string.r7p_missed_sbahn
    RideKind.Train -> R.string.missed_train
    RideKind.Ferry -> R.string.r7p_missed_ferry
    RideKind.Unknown -> R.string.r7p_missed_ride
}

/** "Mein Bus ist zu spät / fällt aus", "Meine Tram …", … ; "Meine Fahrt …" where the kind is not known. */
fun lateRideRes(kind: RideKind): Int = when (kind) {
    RideKind.Bus -> R.string.r7p_late_bus
    RideKind.Tram -> R.string.r7p_late_tram
    RideKind.UBahn -> R.string.r7p_late_ubahn
    RideKind.SBahn -> R.string.r7p_late_sbahn
    RideKind.Train -> R.string.r6c_train_late
    RideKind.Ferry -> R.string.r7p_late_ferry
    RideKind.Unknown -> R.string.r7p_late_ride
}

/** "Ich sitze im falschen Bus", "… in der falschen Tram", … ; "… im falschen Fahrzeug" where the kind is not known. */
fun wrongRideRes(kind: RideKind): Int = when (kind) {
    RideKind.Bus -> R.string.r7p_wrong_bus
    RideKind.Tram -> R.string.r7p_wrong_tram
    RideKind.UBahn -> R.string.r7p_wrong_ubahn
    RideKind.SBahn -> R.string.r7p_wrong_sbahn
    RideKind.Train -> R.string.r6c_wrong_train
    RideKind.Ferry -> R.string.r7p_wrong_ferry
    RideKind.Unknown -> R.string.r7p_wrong_ride
}

/** "Wo ist mein Bus?", "Wo ist meine Bahn?" (S- and U-Bahn), … ; "Wo ist mein Fahrzeug?" where the kind is not known. */
fun whereRideRes(kind: RideKind): Int = when (kind) {
    RideKind.Bus -> R.string.r5c_where_bus
    RideKind.Tram -> R.string.r5c_where_tram
    RideKind.UBahn, RideKind.SBahn -> R.string.r5c_where_bahn
    RideKind.Train -> R.string.r5c_where_ride
    RideKind.Ferry -> R.string.r5c_where_ferry
    RideKind.Unknown -> R.string.r7p_where_ride
}
