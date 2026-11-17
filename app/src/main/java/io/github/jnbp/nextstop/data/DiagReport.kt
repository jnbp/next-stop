package io.github.jnbp.nextstop.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * The bug report of the developer options ("Fehlerbericht teilen"): one text with the app,
 * the device, the settings that matter, the last crash and the diagnostic log (see DiagLog).
 * Pure Kotlin: built and redacted here, written and shared by the app (see ui/DiagCapture.kt).
 * Nothing is ever sent by the app itself; the user picks where the report goes.
 */

/** What goes into a report; every text is redacted by [diagReportText]. */
class DiagReportInput(
    /** Unix millis of the report. */
    val time: Long,
    /** "Was ist passiert?" as typed; may be empty. */
    val note: String,
    /** App and device, as label and value. */
    val about: List<Pair<String, String>>,
    /** Settings that matter for a bug (no places, no names), as label and value. */
    val settings: List<Pair<String, String>>,
    /** The last crash as written by the crash handler, null when there is none. */
    val crash: String?,
    /** The log, oldest first, as lines (see DiagRing.lines). */
    val log: List<String>,
)

/**
 * What must not leave the phone in a report: [secrets] (access keys and tokens) become
 * "[key]", [privateTerms] (names and addresses of the user's places, the own server, the
 * name for the live page) become "[private]".
 */
class DiagRedaction(secrets: Collection<String>, privateTerms: Collection<String>) {
    // Longest first, so a term inside a longer one does not leave the rest of that one behind.
    private val secrets = secrets.map { it.trim() }.filter { it.length >= MIN_SECRET }.distinct().sortedByDescending { it.length }
    private val terms = privateTerms.map { it.trim() }.filter { it.length >= MIN_TERM }.distinct().sortedByDescending { it.length }

    /** [text] with the secrets, the private terms, credentials, e-mail addresses and exact positions taken out. */
    fun redact(text: String): String {
        var out = text
        for (secret in secrets) out = out.replace(secret, KEY_MARK)
        for (term in terms) out = out.replace(term, PRIVATE_MARK, ignoreCase = true)
        out = out.replace(URL_CREDENTIALS, "$1[cred]@")
        out = out.replace(KEY_PARAMETER, "$1$KEY_MARK")
        out = out.replace(AUTH_HEADER, "$1$KEY_MARK")
        out = out.replace(PLACE_PARAMETER, "$1…")
        out = out.replace(EMAIL, "[email]")
        out = out.replace(LONG_DECIMAL, "$1")
        return out
    }

    companion object {
        const val KEY_MARK = "[key]"
        const val PRIVATE_MARK = "[private]"

        // Shorter secrets or terms would replace ordinary words.
        private const val MIN_SECRET = 4
        private const val MIN_TERM = 3

        // user:password@ in an address.
        private val URL_CREDENTIALS = Regex("(?i)\\b([a-z][a-z0-9+.-]*://)[^/\\s@]+@")

        // Parameters that carry keys: accessId=…, apikey=…, token=…, client_id=…, password=…
        private val KEY_PARAMETER = Regex(
            "(?i)((?:^|[?&;\\s])[a-z_]*(?:key|token|secret|password|passwd|accessid|clientid|client_id|auth|authorization)=)[^&\\s]*",
        )

        // "Authorization: Bearer …", "DB-Api-Key: …" and the like.
        private val AUTH_HEADER = Regex("(?i)((?:authorization|api-key|client-id|x-api-key)\\s*[:=]\\s*(?:bearer\\s+|basic\\s+)?)[^\\s,;]+")

        // Names and addresses of places in a request (as in loggableRequest).
        private val PLACE_PARAMETER = Regex("((?:^|[?&])(?:[A-Za-z]+\\.(?:address|name)|query|text|input)=)[^&\\s]*")

        private val EMAIL = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")

        // A number with more than 2 decimals (a coordinate) keeps 2 (about 1 km); not the
        // seconds of a time ("01:02:03.456"), which follow a colon.
        private val LONG_DECIMAL = Regex("(?<![\\d.:])(-?\\d{1,3}\\.\\d{2})\\d+")
    }
}

/** The report as one text, redacted by [redaction]. */
fun diagReportText(input: DiagReportInput, redaction: DiagRedaction): String {
    val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT).format(Date(input.time))
    val text = buildString {
        append("Next Stop bug report\n")
        append("Created: ").append(stamp).append('\n')
        append("\n## What happened\n")
        append(input.note.trim().ifEmpty { "(no description)" }).append('\n')
        append("\n## App and device\n")
        for ((label, value) in input.about) append(label).append(": ").append(value).append('\n')
        append("\n## Settings\n")
        for ((label, value) in input.settings) append(label).append(": ").append(value).append('\n')
        append("\n## Last crash\n")
        append(input.crash?.trim()?.ifEmpty { null } ?: "(none)").append('\n')
        append("\n## Log (").append(input.log.size).append(" events, oldest first)\n")
        for (line in input.log) append(line).append('\n')
    }
    return redaction.redact(text)
}
