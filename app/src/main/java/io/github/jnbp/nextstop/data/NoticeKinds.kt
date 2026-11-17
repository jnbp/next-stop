package io.github.jnbp.nextstop.data

/**
 * What a disruption notice ([Warning]) is about, for whether it raises the warning sign:
 *
 * - [Accessibility]: lifts, escalators and step-free access ("Aufzug außer Betrieb"). It
 *   matters to those who travel step-free (the accessibility setting is not "none"); for the
 *   others it stays in the detail of a journey, quietly, without the sign and without a count.
 * - [Disruption]: everything else, detours, cancellations, closures, replacement buses; it
 *   always raises the sign, as every notice did before.
 */
enum class NoticeKind { Disruption, Accessibility }

// The codes HAFAS gives remarks about step-free access and its equipment (BVG "bf", DB "EA",
// "EH", "RO", "RG", "OA"), lower case.
private val ACCESSIBILITY_CODES = setOf("bf", "ea", "eh", "ro", "rg", "oa")

// Parts of words that name lifts, escalators or step-free access, in German and English; parts,
// so compounds count too ("Bahnsteigaufzug", "Aufzugsstörung", "elevators").
private val ACCESSIBILITY_PARTS = listOf(
    "aufzug", "aufzüg", "fahrstuhl", "fahrstühl", "rolltreppe", "fahrtreppe", "barrierefrei", "stufenfrei",
    "elevator", "escalator", "step-free", "step free", "wheelchair access",
)

// Whole words for a lift ("Lift", "lifts"), which as a part would match other words.
private val LIFT_WORD = Regex("\\blifts?\\b", RegexOption.IGNORE_CASE)

// Parts of words of a real disruption of the service. A notice that has one of them always
// raises the sign, even where it also names a lift ("Ersatzverkehr ... Aufzug nutzen").
// "Gesperrt", "Störung", "Ausfall" and "außer Betrieb" are not among them: a lift has those too.
private val DISRUPTION_PARTS = listOf(
    "ersatzverkehr", "schienenersatz", "sev ", "umleitung", "umgeleitet", "zugausfall", "fahrtausfall", "fahrten aus", "fällt aus", "fallen aus",
    "entfällt", "entfallen", "verkehrt nicht", "kein halt", "ohne halt", "hält nicht", "halten nicht",
    "pendelverkehr", "streckensperrung", "bahnhof gesperrt", "station gesperrt", "haltestelle gesperrt",
    "verlegt", "verspätung", "unterbrochen", "unterbrechung", "streik", "einsatz",
    "replacement", "detour", "diverted", "diversion", "cancel", "does not stop", "do not stop", "not stopping",
    "no service", "not running", "suspended", "interrupted", "delay", "relocated", "strike", "station closed",
    "stop closed", "line closed",
)

/** The [NoticeKind] of [warning], by the code of its remark and by the words of its text. */
fun noticeKind(warning: Warning): NoticeKind {
    val words = (warning.summary + " " + warning.text).lowercase() + " "
    if (DISRUPTION_PARTS.any { it in words }) return NoticeKind.Disruption
    val code = warning.code?.trim()?.lowercase()
    if (code != null && code in ACCESSIBILITY_CODES) return NoticeKind.Accessibility
    if (ACCESSIBILITY_PARTS.any { it in words } || LIFT_WORD.containsMatchIn(words)) return NoticeKind.Accessibility
    return NoticeKind.Disruption
}

/** True when [warning] raises the warning sign for a traveller who travels step-free ([stepFree]) or not. */
fun warningAlerts(warning: Warning, stepFree: Boolean): Boolean =
    stepFree || noticeKind(warning) != NoticeKind.Accessibility

/** The notices of [warnings] that raise the warning sign (see [warningAlerts]); the others are [quietWarnings]. */
fun alertingWarnings(warnings: List<Warning>, stepFree: Boolean): List<Warning> =
    warnings.filter { warningAlerts(it, stepFree) }

/** The notices of [warnings] shown only quietly in the detail of a journey: about lifts and the like, without step-free travel. */
fun quietWarnings(warnings: List<Warning>, stepFree: Boolean): List<Warning> =
    warnings.filterNot { warningAlerts(it, stepFree) }
