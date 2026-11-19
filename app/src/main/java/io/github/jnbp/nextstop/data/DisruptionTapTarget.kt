package io.github.jnbp.nextstop.data

/**
 * What a tap on a notification of the disruption watch asks for: the notices it reported, by
 * their [ids] (see [Disruption.id]) and the provider's own ids of them ([providerIds]), so a
 * notice merged under another copy on "My routes" is still found. Empty for a notification that
 * only reports a cancelled journey.
 */
data class DisruptionTapRequest(val ids: List<String>, val providerIds: List<String> = emptyList())

/** Where a tap on a notification of the disruption watch leads on "My routes". */
sealed interface DisruptionTapTarget {
    /** The one notice reported, still current: its row highlighted and its sheet opened. */
    data class One(val id: String) : DisruptionTapTarget

    /** Several notices reported in one notification: the section unfolded, those still current highlighted. */
    data class Several(val ids: List<String>) : DisruptionTapTarget

    /** Nothing in particular reported (a cancelled journey): the section as it is. */
    data object Section : DisruptionTapTarget

    /** What was reported is no longer current (resolved or expired). */
    data object Gone : DisruptionTapTarget
}

/**
 * Where the tap [request] leads, given the notices [present] on "My routes" now: a reported
 * notice is still there when one of them has its id, or shares one of the provider's ids.
 */
fun disruptionTapTarget(request: DisruptionTapRequest, present: List<Disruption>): DisruptionTapTarget {
    if (request.ids.isEmpty() && request.providerIds.isEmpty()) return DisruptionTapTarget.Section
    val ids = request.ids.toSet()
    val providerIds = request.providerIds.toSet()
    val found = present.filter { item -> item.id in ids || item.providerIds.any { it in providerIds } }.map { it.id }.distinct()
    return when {
        found.isEmpty() -> DisruptionTapTarget.Gone
        request.ids.size <= 1 -> DisruptionTapTarget.One(found.first())
        else -> DisruptionTapTarget.Several(found)
    }
}

/**
 * The place in the list of "My routes" of the section of the disruptions: after the title,
 * the [planned] trips, the map when it [hasMap] and the strip of the statistics.
 */
fun waysDisruptionsIndex(planned: Int, hasMap: Boolean): Int = 1 + planned + (if (hasMap) 1 else 0) + 1
