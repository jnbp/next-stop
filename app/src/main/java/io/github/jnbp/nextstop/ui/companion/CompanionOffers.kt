package io.github.jnbp.nextstop.ui.companion

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.ui.TripChange
import io.github.jnbp.nextstop.ui.journeyChanges
import java.time.OffsetDateTime

/**
 * Which change of the followed trip the connecting departures are loaded for. By itself the
 * next one; in the new companion views any later change can be picked ("Anschluss wechseln"),
 * and the root of the app then loads the departures there instead. Snapshot state, read by
 * the root of the app, so it takes along nothing new.
 */
object CompanionOffers {
    private var journeyId by mutableStateOf<String?>(null)
    private var chosen by mutableStateOf<Int?>(null)
    private var opened by mutableStateOf<Int?>(null)
    private var folded by mutableStateOf<Int?>(null)

    /**
     * Picks the change keeping [keepLegs] legs of [journey] to choose the connection at, and
     * opens its picker; the picker open before folds (#94).
     */
    fun choose(journey: Journey, keepLegs: Int) {
        journeyId = journey.id
        chosen = keepLegs
        opened = keepLegs
        folded = null
    }

    /**
     * Folds the open picker, that of the change keeping [keepLegs] legs of [journey]; the
     * departures stay loaded where they are. Remembered, so a picker that opens by itself
     * ("Nur der nächste offen", #138) stays folded until opened again.
     */
    fun fold(journey: Journey, keepLegs: Int) {
        journeyId = journey.id
        opened = null
        folded = keepLegs
    }

    /** The keepLegs of the picker folded by hand in [journey], or null. */
    fun foldedKeepLegs(journey: Journey): Int? = if (journeyId == journey.id) folded else null

    /** The keepLegs of the picker open by hand in [journey], or null while all are folded. */
    fun openKeepLegs(journey: Journey): Int? = if (journeyId == journey.id) opened else null

    /** The keepLegs of the change the departures are loaded for at [now], or null without one. */
    fun activeKeepLegs(journey: Journey, now: OffsetDateTime): Int? = changeFor(journey, now)?.keepLegs

    /**
     * The change of [journey] to load the connecting departures for at [now]: the one picked
     * (only in the new views, and while it lies ahead), else the next one.
     */
    fun changeFor(journey: Journey, now: OffsetDateTime): TripChange? {
        val ahead = journeyChanges(journey).filter { changeAhead(it.next.departure, it.arriving.arrival, it.time.possible, now) }
        val picked = if (CompanionLayout.modern && journeyId == journey.id) chosen else null
        // A change that can no longer be made (a delay) gets the choice, so the ways on are loaded there (#98).
        val broken = ahead.firstOrNull { !it.time.possible }?.keepLegs
        val openByHand = if (CompanionLayout.modern && journeyId == journey.id) opened else null
        val keep = activeChangeKeepLegs(picked, ahead.map { it.keepLegs }, broken, openByHand) ?: return null
        return ahead.firstOrNull { it.keepLegs == keep }
    }
}

/**
 * What [ShortcutGuard] compares of the followed [journey] (#120): its arrival and whether a
 * change of it can no longer be made or a ride is cancelled.
 */
fun shortcutOwn(journey: Journey): ShortcutOwn = ShortcutOwn(
    arrivalMillis = journey.arrival.toInstant().toEpochMilli(),
    broken = journey.legs.any { !it.walking && it.cancelled } || journeyChanges(journey).any { !it.time.possible },
)

/** What [ShortcutGuard] compares of an [offer]: when it gets the traveller there. */
fun shortcutOfferArrival(offer: io.github.jnbp.nextstop.ui.FasterOffer): Long = offer.arrival.toInstant().toEpochMilli()
