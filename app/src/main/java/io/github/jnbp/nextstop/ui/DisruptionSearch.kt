package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.Disruption
import io.github.jnbp.nextstop.data.searchTokens

/**
 * A notice made ready for the search field of "All disruptions": its lines in a compact form
 * ("U9", "RE 1" → "u9", "re1") and the words of its summary, text and stations (see
 * searchTokens: small letters, umlauts spelled out, accents dropped).
 */
class DisruptionSearchEntry(val item: Disruption) {
    val lines: Set<String> = buildSet {
        item.lines.forEach { add(compactLine(it)) }
        item.carriers.forEach { add(compactLine(it.line)) }
        item.about.forEach { add(compactLine(it.line)) }
    }.filterTo(HashSet()) { it.isNotEmpty() }

    val words: Set<String> = buildSet {
        addAll(searchTokens(item.summary))
        addAll(searchTokens(item.text))
        item.stops.forEach { addAll(searchTokens(it.name)) }
        addAll(lines)
    }
}

/** The entries of [items], worked out once per list (not per typed letter). */
fun indexDisruptions(items: List<Disruption>): List<DisruptionSearchEntry> = items.map { DisruptionSearchEntry(it) }

/**
 * The notices that fit [query], in their order. Every word of the query must be found: a word
 * with a digit ("u9", "re1", "245") as a whole line or word, so "RE1" does not find RE10; a
 * word of letters at the start of a word ("zoolog" finds "Zoologischer Garten"). A query that
 * is a line written apart ("U 9", "re 1") finds that line too. An empty query keeps all.
 */
fun filterDisruptions(entries: List<DisruptionSearchEntry>, query: String): List<Disruption> {
    val tokens = searchTokens(query)
    if (tokens.isEmpty()) return entries.map { it.item }
    val joined = tokens.joinToString("")
    return entries.filter { entry -> joined in entry.lines || tokens.all { token -> entry.has(token) } }.map { it.item }
}

private fun DisruptionSearchEntry.has(token: String): Boolean =
    if (token.any { it.isDigit() }) {
        token in lines || token in words
    } else {
        token in lines || words.any { it.startsWith(token) }
    }

/** A line name as one lower-case word without spaces or signs: "RE 1" → "re1", "M10" → "m10". */
internal fun compactLine(line: String): String = searchTokens(line).joinToString("")
