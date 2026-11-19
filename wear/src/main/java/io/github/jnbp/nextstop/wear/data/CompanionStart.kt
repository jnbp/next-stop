package io.github.jnbp.nextstop.wear.data

/*
 * When a trip of the phone counts as just started, for the option "Open companion
 * automatically" (CompanionAutoOpen). Free of Android classes, tested in CompanionStartTest.
 */

// A trip of the phone counts as just started this long after its start (the phone's "started").
internal const val AUTO_OPEN_WINDOW_MILLIS = 5 * 60_000L

/**
 * True when [after], a state of the phone's trip the watch took, is a trip that has just begun
 * at [now] compared to what the watch showed [before]: running, of the phone, not arrived, new
 * here (no trip before, or another trip of the phone; never a trip the watch followed itself,
 * which the phone takes over), and started within [AUTO_OPEN_WINDOW_MILLIS] when the phone
 * says when (an older phone app sends no start: then it counts as new).
 */
fun startedOnPhone(before: TripSnapshot, after: TripSnapshot, now: Long): Boolean {
    if (!after.active || !after.fromPhone || after.kind == StepKind.Arrived) return false
    if (after.started > 0 && now - after.started > AUTO_OPEN_WINDOW_MILLIS) return false
    if (!before.active) return true
    if (!before.fromPhone) return false
    val key = after.tripKey ?: return false
    return key != before.tripKey
}
