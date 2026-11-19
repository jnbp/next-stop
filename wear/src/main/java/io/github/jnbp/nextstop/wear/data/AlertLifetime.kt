package io.github.jnbp.nextstop.wear.data

/*
 * #183: the alert "Nächste Station umsteigen" stayed on the watch after the change was done and
 * the train had left: it went only with a tap (auto-cancel). It belongs to one ride, by its
 * [TripSnapshot.alertKey]; once the trip shows anything else, it has done its job.
 */

/**
 * True when the alert posted for the ride [shownKey] (null: none is up) no longer fits the trip
 * [trip] now shown: the trip ended, the next step is no longer getting off (the traveller got
 * off and walks or waits for the next ride), or getting off another ride.
 */
fun alertOutlived(shownKey: String?, trip: TripSnapshot): Boolean {
    if (shownKey.isNullOrEmpty()) return false
    return !trip.active || trip.kind != StepKind.GetOff || trip.alertKey != shownKey
}

/** The alert goes by itself at the latest this long after it came, should no trip state follow (ms). */
const val ALERT_TIMEOUT_MILLIS = 15 * 60_000L
