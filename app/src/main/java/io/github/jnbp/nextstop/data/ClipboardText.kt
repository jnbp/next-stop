package io.github.jnbp.nextstop.data

/*
 * A copied text as a place search: an address copied from an e-mail or a website often comes
 * in several lines ("Name", "Street 1", "12345 Town"); its lines are joined with ", " as a
 * one-line address is written, runs of spaces and tabs become one space, and a long text is
 * cut so a whole copied page does not go to the search.
 */

/** The longest pasted text that is searched for, in characters; longer ones are cut at a word. */
const val PASTE_MAX_CHARS = 200

// Spaces of all kinds (tabs, no-break and narrow spaces) that become one plain space.
private val PASTE_SPACES = Regex("""[\s   ]+""")

// Characters without width that copying from web pages leaves behind.
private val PASTE_INVISIBLE = Regex("""[​‌‍⁠﻿]""")

/** [raw] as one line to search for; null when it holds nothing but space. */
fun pasteSearchText(raw: CharSequence): String? {
    val parts = raw.toString()
        .replace(PASTE_INVISIBLE, "")
        .lines()
        .map { line -> line.replace(PASTE_SPACES, " ").trim().trimEnd(',', ';').trim() }
        .filter { it.isNotEmpty() }
    if (parts.isEmpty()) return null
    val joined = parts.joinToString(", ")
    if (joined.length <= PASTE_MAX_CHARS) return joined
    val cut = joined.take(PASTE_MAX_CHARS)
    // At the last space, when there is one in the second half: no half word at the end.
    val space = cut.lastIndexOf(' ')
    val atWord = if (space > PASTE_MAX_CHARS / 2) cut.substring(0, space) else cut
    return atWord.trimEnd(',', ';', ' ')
}
