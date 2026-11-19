package io.github.jnbp.nextstop.data

/*
 * Small pieces of the words of a change card (#239, #240): never "Pos." without a number,
 * never "über Ausgang Ausgang …".
 */

/** The label of a stop position worth printing after "Pos.": null for a missing or blank one (#239). */
fun shownPositionLabel(label: String?): String? = label?.trim()?.takeIf { it.isNotEmpty() }

/**
 * Where the step of a change card leads ("Gleis 2 → …", #239): the stop position the walk ends
 * at by OpenStreetMap ([osmPosition], worded), else the platform of the next ride, else the
 * stop position the provider names ([spot], worded), else the name of the other stop the next
 * ride leaves from ([otherStop], where it is another than the change's), else [roadStop] (the
 * plain word "Haltestelle" for a bus or tram), else null.
 */
fun changeStepTarget(osmPosition: String?, platform: String?, spot: String?, otherStop: String?, roadStop: String?): String? =
    listOf(osmPosition, platform, spot, otherStop, roadStop).firstOrNull { !it.isNullOrBlank() }

// A leading word for a door in an OSM name ("Eingang Europaplatz", "Ausgang B", "Exit 3").
private val LEADING_DOOR_WORD = Regex(
    "^(ein-?\\s*(/|und|&)\\s*ausgang|eingang|ausgang|zugang|aufgang|entrance|exit|way out)\\b[\\s:,.\\-–]*",
    RegexOption.IGNORE_CASE,
)

/**
 * The name of a door without a leading "Eingang", "Ausgang", "Exit" and the like (any case),
 * for texts that say the word themselves ("über Ausgang %s", #240); empty where the name is
 * only the word.
 */
fun doorLabelWithoutWord(label: String): String = label.trim().replace(LEADING_DOOR_WORD, "").trim()
