package io.github.jnbp.nextstop.data

import java.text.Normalizer
import java.util.Locale

/*
 * Contacts and appointments found while typing in the search (#68): the search looks into them
 * by itself when the app may read them, with no need to open their lists first. Pure: the
 * entries are read elsewhere and never stored or logged here.
 */

/**
 * The entries of [items] that fit what is typed ([text]): every word of it starts a word of one
 * of the entry's [fields] (the name first). Those whose first field (the name or the title) has
 * the words come first, the others after; the order within each kept. At most [limit]; none for
 * a text shorter than [minChars].
 */
fun <T> peopleMatching(items: List<T>, text: String, limit: Int, minChars: Int = 2, fields: (T) -> List<String?>): List<T> =
    peopleMatchingIn(peopleIndex(items, fields), text, limit, minChars)

/** One entry with the words of its first field and of all its fields, taken apart once (see [peopleIndex]). */
class PeopleEntry<T>(val item: T, val first: List<String>, val all: List<String>)

/**
 * The words of the [fields] of every one of [items], taken apart once when the list is read,
 * so typing only compares words (#109).
 */
fun <T> peopleIndex(items: List<T>, fields: (T) -> List<String?>): List<PeopleEntry<T>> =
    items.map { item ->
        val texts = fields(item)
        val first = texts.firstOrNull()?.let { matchWords(it) }.orEmpty()
        PeopleEntry(item, first, texts.flatMap { it?.let { field -> matchWords(field) }.orEmpty() })
    }

/** [peopleMatching] over an [index] made before. */
fun <T> peopleMatchingIn(index: List<PeopleEntry<T>>, text: String, limit: Int, minChars: Int = 2): List<T> {
    if (text.trim().length < minChars || limit <= 0) return emptyList()
    val wanted = matchWords(text)
    if (wanted.isEmpty()) return emptyList()
    val byName = ArrayList<T>()
    val other = ArrayList<T>()
    for (entry in index) {
        when {
            wanted.all { word -> entry.first.any { it.startsWith(word) } } -> byName.add(entry.item)
            wanted.all { word -> entry.all.any { it.startsWith(word) } } -> other.add(entry.item)
        }
        if (byName.size >= limit) break
    }
    return (byName + other).take(limit)
}

/** The words of a text for matching: lower case, umlauts written out, accents taken off. */
fun matchWords(text: String): List<String> {
    val written = text.lowercase(Locale.ROOT)
        .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
    val plain = if (written.all { it.code < 0x80 }) written else Normalizer.normalize(written, Normalizer.Form.NFD).replace(MATCH_MARKS, "")
    return plain.split(MATCH_NON_WORD).filter { it.isNotEmpty() }
}

// Made once, not for every field of every entry at every key typed.
private val MATCH_MARKS = Regex("\\p{M}+")
private val MATCH_NON_WORD = Regex("[^\\p{L}\\p{N}]+")
