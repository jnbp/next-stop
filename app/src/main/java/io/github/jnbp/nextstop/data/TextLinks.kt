package io.github.jnbp.nextstop.data

/** A link found in a text: the characters [start] until [end] (exclusive), opened as [target]. */
data class TextLink(val start: Int, val end: Int, val target: String)

private val EMAIL = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}""")
private val URL = Regex("""(?i)\b(?:https?://|www\.)[^\s<>"]+""")

// A web address without "www." or a scheme, as notices write it ("Infos unter bvg.de/stoerungen").
private val BARE_DOMAIN = Regex(
    """(?i)(?<![@\w.\-/])(?:[a-z0-9](?:[a-z0-9\-]*[a-z0-9])?\.)+(?:de|com|org|net|info|eu|berlin|app|io)(?:/[^\s<>"]*)?(?![\w@])""",
)

// A phone number as German notices write it: "030 19449", "(030) 25 41 41 41", "+49 30 1234567",
// "0800 1 507090", "030/19449". It starts with 0 or +, so times, dates and lines are no numbers.
private val PHONE = Regex("""(?<![\w/+.,:])(?:\+\d{1,3}[ /-]?(?:\(0\)[ ]?)?\d{1,5}|\(0\d{1,5}\)|0\d{1,5})[ /-]?\d[\d /-]{3,14}\d(?![\w.,:]\d)(?!\w)""")

// Characters that end a sentence rather than an address.
private const val TRAILING = ".,;:!?)]}'\""

/** [end] moved back over punctuation that closes the sentence around a link. */
private fun trimEnd(text: String, start: Int, end: Int): Int {
    var last = end
    while (last > start && text[last - 1] in TRAILING) {
        // A bracket that belongs to the address ("…/Linie_(Berlin)") stays.
        if (text[last - 1] == ')' && text.substring(start, last).count { it == '(' } >= text.substring(start, last).count { it == ')' }) break
        last--
    }
    return last
}

/**
 * The links in a notice of a provider: web addresses (with or without "https://" or "www."),
 * e-mail addresses and phone numbers, each once, in the order of the text. Web addresses
 * open in the browser, e-mail addresses as mailto:, phone numbers as tel:.
 */
fun findTextLinks(text: String): List<TextLink> {
    val found = mutableListOf<TextLink>()
    fun free(start: Int, end: Int) = found.none { start < it.end && it.start < end }
    EMAIL.findAll(text).forEach { match ->
        val end = trimEnd(text, match.range.first, match.range.last + 1)
        if (free(match.range.first, end)) found += TextLink(match.range.first, end, "mailto:" + text.substring(match.range.first, end))
    }
    (URL.findAll(text) + BARE_DOMAIN.findAll(text)).forEach { match ->
        val start = match.range.first
        val end = trimEnd(text, start, match.range.last + 1)
        if (end <= start || !free(start, end)) return@forEach
        val address = text.substring(start, end)
        val target = if (address.startsWith("http://", ignoreCase = true) || address.startsWith("https://", ignoreCase = true)) {
            address
        } else {
            "https://$address"
        }
        found += TextLink(start, end, target)
    }
    PHONE.findAll(text).forEach { match ->
        val start = match.range.first
        val end = match.range.last + 1
        if (!free(start, end)) return@forEach
        val number = text.substring(start, end)
        val digits = number.filter { it.isDigit() }
        if (digits.length < 6 || digits.length > 15) return@forEach
        // "+49 (0)30 …" is dialled without the 0 in brackets.
        val dial = (if (number.trimStart().startsWith("+")) "+" else "") + number.replace("(0)", "").filter { it.isDigit() }
        found += TextLink(start, end, "tel:$dial")
    }
    return found.sortedBy { it.start }
}
