package io.github.jnbp.nextstop.ui

import java.time.Duration
import java.time.OffsetDateTime

/*
 * What a tile of "Which connection do you want to take?" says, apart from its line and the
 * colour of how tight its change is: when it gets to the destination, the minutes of the
 * change, and how much sooner or later than the chosen connection it gets there.
 */

/**
 * Minutes [option] gets to the destination after [chosen] (negative: sooner); null when
 * either arrival is unknown.
 */
fun choiceDelta(option: OffsetDateTime?, chosen: OffsetDateTime?): Long? =
    if (option == null || chosen == null) null else Duration.between(chosen, option).toMinutes()

/** The delta chip of a tile: "+12", "−3" (with a real minus) or "±0". */
fun choiceDeltaLabel(delta: Long): String = when {
    delta > 0 -> "+$delta"
    delta < 0 -> "−${-delta}"
    else -> "±0"
}

/**
 * Whether an example change of [tightness] is offered with the transfer level [level] (the
 * live preview of the setup): every change up to as tight as the level allows.
 */
fun setupExampleShown(tightness: io.github.jnbp.nextstop.data.Tightness, level: io.github.jnbp.nextstop.data.TransferComfort): Boolean =
    io.github.jnbp.nextstop.data.transferComfort(tightness).ordinal <= level.ordinal
