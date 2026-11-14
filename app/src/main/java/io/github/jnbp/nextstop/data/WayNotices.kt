package io.github.jnbp.nextstop.data

/**
 * The notices on the cards of "My routes" (saved connections, routes and places): [all] of
 * them, each once however many cards it touches, and each card's own out of those, by the
 * key of the card. A card without a notice has no entry.
 */
data class WayNotices(
    val all: List<Disruption>,
    val byCard: Map<String, List<Disruption>>,
) {
    /** The notices of the card [key], each once; empty for a card without one. */
    fun of(key: String): List<Disruption> = byCard[key].orEmpty()

    companion object {
        val NONE = WayNotices(emptyList(), emptyMap())
    }
}

/**
 * The notices of [cards] (each a card's key and what was found for it) as [WayNotices]:
 * copies of one notice (see [mergeDisruptions]) are one, on one card and across cards, in the
 * order first seen. A card's list holds the same merged notices as [WayNotices.all].
 */
fun wayNotices(cards: List<Pair<String, List<Disruption>>>): WayNotices {
    val owners = cards.flatMap { (key, items) -> items.map { key to it } }
    if (owners.isEmpty()) return WayNotices.NONE
    val items = owners.map { it.second }
    val groups = copyGroups(items)
    val all = groups.map { group -> combine(group.map { items[it] }) }
    val byCard = LinkedHashMap<String, MutableList<Disruption>>()
    groups.forEachIndexed { at, group ->
        group.map { owners[it].first }.distinct().forEach { key -> byCard.getOrPut(key) { mutableListOf() }.add(all[at]) }
    }
    return WayNotices(all, byCard)
}

/**
 * The notices of [items] that touch a connection riding [lines] ("M27", "U9"): those about one
 * of its lines, or found on one of them. Line names are compared as their badges show them
 * (see [lineKey]).
 */
fun noticesOnLines(items: List<Disruption>, lines: List<String>): List<Disruption> {
    val keys = lines.map { lineKey(it) }.filter { it.isNotEmpty() }.toSet()
    if (keys.isEmpty()) return emptyList()
    return items.filter { item ->
        item.about.any { lineKey(it.line) in keys } || item.lines.any { lineKey(it) in keys } ||
            item.carriers.any { lineKey(it.line) in keys }
    }
}

/**
 * What the title of "My routes" shows: an empty room while the first answers are awaited
 * ([loading]), the count of notices once something is known, and nothing at all ([unknown])
 * when every check has failed.
 */
data class WaysPill(val loading: Boolean, val count: Int, val unknown: Boolean = false)

/**
 * The pill from the state of the checks: [checkable] something is asked at all, [known] an
 * answer is there (from now or before), [done] the round of checks is over.
 */
fun waysPill(checkable: Boolean, known: Boolean, done: Boolean, count: Int): WaysPill = when {
    !checkable || known -> WaysPill(loading = false, count = count)
    done -> WaysPill(loading = false, count = 0, unknown = true)
    else -> WaysPill(loading = true, count = 0)
}
