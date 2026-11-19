package io.github.jnbp.nextstop.ui.companion

import io.github.jnbp.nextstop.ui.StepKind

/*
 * #246: what the one line of the put-away sheet over the map (its handle) shows. With a ride
 * still ahead, that ride; once only the final walk is left there is no ride, and the line used
 * to stay empty, so the sheet put away shrank to a bare strip above the bar. Now it shows the
 * walk (or the arrival) at the sheet's usual height.
 */

/** What the handle line of the sheet over the map shows. */
enum class SheetSummaryKind { Ride, Step, Arrived }

/** The handle line with [hasRide] (a ride still ahead or on board) at a step of [kind]. */
fun sheetSummaryKind(hasRide: Boolean, kind: StepKind): SheetSummaryKind = when {
    hasRide -> SheetSummaryKind.Ride
    kind == StepKind.Arrived -> SheetSummaryKind.Arrived
    else -> SheetSummaryKind.Step
}
