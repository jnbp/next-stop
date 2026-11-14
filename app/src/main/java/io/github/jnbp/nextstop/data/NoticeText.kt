package io.github.jnbp.nextstop.data

import java.util.Locale

/** What a link of a notice leads to: more about it, a map of it, or something else. */
enum class NoticeLinkKind { More, Map, Other }

/**
 * A link of a notice, taken out of its text: [url] where the provider sent one (null for a
 * bare placeholder such as "[MEHR/MORE]"), [label] the words it had where they were no
 * placeholder.
 */
data class NoticeLink(val kind: NoticeLinkKind, val url: String?, val label: String? = null)

/** BVG's page of all current disruptions: where "more" leads when a notice names no page of its own. */
const val BVG_DISRUPTIONS_URL = "https://www.bvg.de/de/verbindungen/stoerungsmeldungen"

/** The text of a notice without its link placeholders, and the links taken out of it. */
data class NoticeText(val text: String, val links: List<NoticeLink>)

// An HTML link: its attributes and its content.
private val ANCHOR = Regex("""(?is)<a\b([^>]*)>(.*?)</a\s*>""")
private val HREF = Regex("""(?is)\bhref\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""")
private val TAG = Regex("""<[^>]+>""")

// A placeholder for a link in a notice: "[MEHR/MORE]", "[KARTE/MAPS]", "[Karte]". Only these
// words count, so a name in brackets ("S+U Alexanderplatz [Grunerstr.]") stays.
private val PLACEHOLDER = Regex("""\[\s*(\p{L}+)((?:\s*/\s*\p{L}+)*)\s*]""")
private val MORE_WORDS = setOf("mehr", "more", "info", "infos", "details", "weitere", "link")
private val MAP_WORDS = setOf("karte", "karten", "map", "maps", "plan", "lageplan")

/** The kind of a placeholder by its words, or null where they are no placeholder. */
private fun placeholderKind(inner: String): NoticeLinkKind? {
    val match = PLACEHOLDER.matchEntire(inner.trim()) ?: return null
    val words = (listOf(match.groupValues[1]) + match.groupValues[2].split('/'))
        .map { it.trim().lowercase(Locale.ROOT) }
        .filter { it.isNotEmpty() }
    return when {
        words.isEmpty() -> null
        words.all { it in MORE_WORDS } -> NoticeLinkKind.More
        words.all { it in MAP_WORDS } -> NoticeLinkKind.Map
        words.all { it in MORE_WORDS || it in MAP_WORDS } -> NoticeLinkKind.More
        else -> null
    }
}

/**
 * [html], a notice as the provider sends it, with its links taken out: BVG ends its notices
 * with links whose text is a placeholder ("<a href=…>[MEHR/MORE]</a>" to the notice on
 * bvg.de, "<a href=…>[KARTE/MAPS]</a>" to a picture of a diversion). Those become [NoticeLink]s
 * and leave the text; other links keep their words in the text and are kept as links too.
 * [plain] turns what is left into plain text (Html.fromHtml in the app); placeholders that
 * come without a link are taken out after it, so none is ever shown.
 */
fun parseNoticeText(html: String?, plain: (String) -> String): NoticeText {
    if (html.isNullOrBlank()) return NoticeText("", emptyList())
    val links = ArrayList<NoticeLink>()
    val withoutAnchors = ANCHOR.replace(html) { match ->
        val href = HREF.find(match.groupValues[1])?.let { found ->
            found.groupValues.drop(1).firstOrNull { it.isNotEmpty() }
        }?.trim()?.replace("&amp;", "&")?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        val words = TAG.replace(match.groupValues[2], "").trim()
        val kind = placeholderKind(words)
        when {
            kind != null -> {
                links += NoticeLink(kind, href)
                ""
            }
            else -> {
                if (href != null) links += NoticeLink(NoticeLinkKind.Other, href, words.takeIf { it.isNotEmpty() })
                match.groupValues[2]
            }
        }
    }
    val text = plain(withoutAnchors)
    val cleaned = PLACEHOLDER.replace(text) { match ->
        val kind = placeholderKind(match.value)
        if (kind == null) {
            match.value
        } else {
            links += NoticeLink(kind, null)
            ""
        }
    }
    return NoticeText(tidy(cleaned), mergeLinks(links))
}

/** [text] without the placeholders a provider left in plain text (see [parseNoticeText]). */
fun withoutPlaceholders(text: String): String =
    tidy(PLACEHOLDER.replace(text) { match -> if (placeholderKind(match.value) == null) match.value else "" })

/** Each link once: a placeholder without an address gives way to one of the same kind with one. */
private fun mergeLinks(links: List<NoticeLink>): List<NoticeLink> {
    val distinct = links.distinct()
    return distinct.filter { link ->
        link.url != null || distinct.none { it.kind == link.kind && it.url != null }
    }
}

// Spaces and empty lines a removed placeholder leaves behind.
private fun tidy(text: String): String =
    text.lines()
        .map { it.replace(Regex("""[ \t]{2,}"""), " ").trimEnd() }
        .joinToString("\n")
        .replace(Regex("""\n{3,}"""), "\n\n")
        .trim()
