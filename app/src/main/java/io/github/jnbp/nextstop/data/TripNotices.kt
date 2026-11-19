package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime

/**
 * A notification about the followed trip that can go out of date, named by a stable key; its
 * [tag] is the notification's tag, so it can be found among the shown ones and cancelled (#172).
 * Journeys are named by the hash of their id only, to keep the tag short.
 */
sealed interface TripNotice {
    val tag: String

    /** A change of platform of the ride [rideKey] (see [platformKey]), where it is boarded ([departure]) or left. */
    data class Platform(val rideKey: String, val departure: Boolean) : TripNotice {
        override val tag: String get() = "$PLATFORM_PREFIX|${if (departure) "d" else "a"}|$rideKey"
    }

    /** "Anschluss klappt nicht mehr" at the change after [keepLegs] legs of the journey [journeyHash]. */
    data class Rescue(val journeyHash: String, val keepLegs: Int) : TripNotice {
        override val tag: String get() = "$RESCUE_PREFIX|$keepLegs|$journeyHash"
    }

    /** A reminder to get off at the end of leg [legIndex] of the journey [journeyHash]. */
    data class Alight(val journeyHash: String, val legIndex: Int) : TripNotice {
        override val tag: String get() = "$ALIGHT_PREFIX|$legIndex|$journeyHash"
    }

    companion object {
        const val PLATFORM_PREFIX = "trip.platform"
        const val RESCUE_PREFIX = "trip.rescue"
        const val ALIGHT_PREFIX = "trip.alight"

        /** The notice a [tag] names; null for any other tag. */
        fun parse(tag: String?): TripNotice? {
            if (tag == null) return null
            val parts = tag.split('|', limit = 3)
            if (parts.size < 3) return null
            return when (parts[0]) {
                PLATFORM_PREFIX -> when (parts[1]) {
                    "d" -> Platform(parts[2], true)
                    "a" -> Platform(parts[2], false)
                    else -> null
                }
                RESCUE_PREFIX -> parts[1].toIntOrNull()?.let { Rescue(parts[2], it) }
                ALIGHT_PREFIX -> parts[1].toIntOrNull()?.let { Alight(parts[2], it) }
                else -> null
            }
        }
    }
}

/** The short name of [journey] in a notice's tag. */
fun noticeJourneyHash(journey: Journey): String = Integer.toHexString(journey.id.hashCode())

/** The notice of [shift] on [journey]; null where its ride is not part of it. */
fun platformNotice(journey: Journey, shift: PlatformShift): TripNotice.Platform? {
    val leg = journey.legs.getOrNull(shift.legIndex)?.takeIf { !it.walking } ?: return null
    return TripNotice.Platform(platformKey(leg, shift.departure), shift.departure)
}

/**
 * True while [notice] still says something about the followed [journey] at [now]: there is a
 * followed journey (null: the trip ended, every notice goes) and
 * - a platform change: its ride is still part of the journey, still shows a change of
 *   platform there ([shifts], the current ones), and has not left that stop yet
 *   (boarding: its departure, getting off: its arrival);
 * - a broken connection: it is the journey it was found for, [rescue] (the suggestion of the
 *   moment as journey hash and kept legs) is still this one, and the next ride from the change
 *   has not left yet;
 * - a reminder to get off: it is the journey it was sent for and its ride has not arrived yet.
 * Times are the plausible ones (see [plausible]).
 */
fun keepTripNotice(
    notice: TripNotice,
    journey: Journey?,
    now: OffsetDateTime,
    shifts: List<PlatformShift>,
    rescue: TripNotice.Rescue?,
): Boolean {
    if (journey == null) return false
    val legs = journey.plausible().legs
    return when (notice) {
        is TripNotice.Platform -> {
            val index = legs.indices.firstOrNull { !legs[it].walking && platformKey(legs[it], notice.departure) == notice.rideKey }
                ?: return false
            val leg = legs[index]
            val shown = shifts.any { it.legIndex == index && it.departure == notice.departure }
            shown && (if (notice.departure) leg.departure else leg.arrival).isAfter(now)
        }
        is TripNotice.Rescue -> {
            if (notice.journeyHash != noticeJourneyHash(journey) || rescue != notice) return false
            val next = legs.drop(notice.keepLegs).firstOrNull { !it.walking } ?: return false
            next.departure.isAfter(now)
        }
        is TripNotice.Alight -> {
            if (notice.journeyHash != noticeJourneyHash(journey)) return false
            val leg = legs.getOrNull(notice.legIndex) ?: return false
            leg.arrival.isAfter(now)
        }
    }
}

/** The tag of the reminder of the planned trip [plannedId] (see PlannedTripAlarms). */
const val PLANNED_NOTICE_PREFIX = "planned|"

/** The planned trip a tag names; null for any other tag. */
fun plannedNoticeId(tag: String?): String? = tag?.takeIf { it.startsWith(PLANNED_NOTICE_PREFIX) }?.removePrefix(PLANNED_NOTICE_PREFIX)

/**
 * True while the reminder of a planned trip still applies: the trip is still planned (its
 * journey [plannedJourneyId], null once it was started, cancelled or is past) and it is not
 * the journey followed now ([followedJourneyId]: started another way).
 */
fun keepPlannedNotice(plannedJourneyId: String?, followedJourneyId: String?): Boolean =
    plannedJourneyId != null && plannedJourneyId != followedJourneyId
