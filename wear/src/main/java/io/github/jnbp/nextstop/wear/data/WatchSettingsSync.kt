package io.github.jnbp.nextstop.wear.data

import org.json.JSONObject

/*
 * The settings of the watch app that can be changed on the watch and on the phone, and how
 * the two copies are merged. Each side keeps every setting as a value with the time it was
 * changed (epoch milliseconds) and writes all of them as one data item
 * (the phone at Protocol.PATH_WATCH_SETTINGS, the watch at Protocol.PATH_WATCH_SETTINGS_WATCH;
 * one JSON object under "json": {"v": 1, "settings": {key:
 * {"value": "...", "at": 123}}}). Reading the other side's item, the newer change of each
 * setting wins (last write wins); on a tie the own value stays, so both sides end up equal
 * without a ping-pong. Keys a side does not know are kept and written on unchanged, so an
 * older app neither loses nor breaks a setting a newer one added; an older app that does not
 * know the data item at all simply ignores it. The watch works offline with its own copy.
 *
 * Two settings stay where they were: the reminder before getting off and the change time are
 * the phone's own settings, sent with the places, and a change on the watch holds until the
 * phone changes its own (WatchStore, "base" keys).
 *
 * Twin: the phone has the same rules in P wear/WatchSettingsSync.kt; keep them alike
 * (docs/conventions.md, "Keep twin implementations alike").
 */

/** One setting: its [value] as text and when it was changed ([changedAt], epoch ms; 0 = never, a default). */
data class SyncedSetting(val value: String, val changedAt: Long)

/** The keys of the settings both sides edit. Stored on disk and sent over the wire: never rename them. */
object WatchSettingKeys {
    /** "true"/"false": the live departures of the nearest stop on the start page (default on). */
    const val LIVE_DEPARTURES = "liveDepartures"

    /** "true"/"false": the animations of the watch app (default on; off snaps everything, for older or slower watches). */
    const val ANIMATIONS = "animations"

    /** The style of the map pictures: a name of MapStyleSetting ("Auto", "Light", "Dark"; default Auto). */
    const val MAP_STYLE = "mapStyle"

    /** The destination of the tile "To a favourite": a place as JSON (placeJson), "" for none chosen. */
    const val TILE_TARGET = "tileTarget"

    /**
     * "true"/"false": the watch opens the trip companion by itself when a trip is started on the
     * phone while the watch app is closed (default off; see CompanionAutoOpen).
     */
    const val OPEN_COMPANION = "openCompanion"

    /**
     * "true"/"false": the complications show the icon of the trip's state (waiting, vehicle,
     * walking ...) in place of the app's icon during a trip (default off, #253).
     */
    const val STATE_ICONS = "complicationStateIcons"

    /**
     * "true"/"false": the crown on the overview of the live trip shows the pill of the time
     * travel (the time and buffer of the row in the middle; default off, #254).
     */
    const val CROWN_TIME_TRAVEL = "crownTimeTravel"
}

/** The version of the settings item; a reader takes every version and keeps what it does not know. */
const val WATCH_SETTINGS_VERSION = 1

/**
 * [local] and [remote] merged: for each key the newer of the two (by [SyncedSetting.changedAt]),
 * the local one on a tie; keys only one side has are kept.
 */
fun mergeSettings(local: Map<String, SyncedSetting>, remote: Map<String, SyncedSetting>): Map<String, SyncedSetting> {
    val merged = LinkedHashMap(local)
    for ((key, theirs) in remote) {
        val ours = merged[key]
        if (ours == null || theirs.changedAt > ours.changedAt) merged[key] = theirs
    }
    return merged
}

/** [settings] with [key] set to [value] at [now]; unchanged (same map) when the value is the same already. */
fun withSetting(settings: Map<String, SyncedSetting>, key: String, value: String, now: Long): Map<String, SyncedSetting> {
    if (settings[key]?.value == value) return settings
    return LinkedHashMap(settings).apply { put(key, SyncedSetting(value, now)) }
}

/** The boolean setting [key] of [settings], [default] when it was never set or does not read as one. */
fun settingFlag(settings: Map<String, SyncedSetting>, key: String, default: Boolean): Boolean =
    settings[key]?.value?.toBooleanStrictOrNull() ?: default

/** The settings as the JSON text of the data item. */
fun settingsJson(settings: Map<String, SyncedSetting>): String {
    val items = JSONObject()
    settings.forEach { (key, setting) -> items.put(key, JSONObject().put("value", setting.value).put("at", setting.changedAt)) }
    return JSONObject().put("v", WATCH_SETTINGS_VERSION).put("settings", items).toString()
}

/** The settings of a data item's JSON [text]; empty for none or text that does not parse, entries without a value left out. */
fun parseSettings(text: String?): Map<String, SyncedSetting> {
    if (text.isNullOrEmpty()) return emptyMap()
    return try {
        val items = JSONObject(text).optJSONObject("settings") ?: return emptyMap()
        val result = LinkedHashMap<String, SyncedSetting>()
        for (key in items.keys()) {
            val item = items.optJSONObject(key) ?: continue
            if (!item.has("value") || item.isNull("value")) continue
            result[key] = SyncedSetting(item.optString("value"), item.optLong("at", 0L))
        }
        result
    } catch (e: Exception) {
        emptyMap()
    }
}
