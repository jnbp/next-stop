package io.github.jnbp.nextstop.wear

import android.content.Context
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

/*
 * The settings of the watch app that can be changed on the phone (Settings › Watch) and on the
 * watch, and how the two copies are merged. Each side keeps every setting as a value with the
 * time it was changed (epoch milliseconds) and writes all of them as one data item: the phone
 * at [PhoneBridge.PATH_WATCH_SETTINGS], the watch at [PhoneBridge.PATH_WATCH_SETTINGS_WATCH];
 * one JSON object under "json": {"v": 1, "settings": {key: {"value": "...", "at": 123}}}.
 * Reading the other side's item, the newer change of each setting wins (last write wins); on a
 * tie the own value stays, so both sides end up equal without a ping-pong. Keys a side does
 * not know are kept and written on unchanged, so an older app neither loses nor breaks a
 * setting a newer one added; an older app that does not know the data item ignores it. The
 * Data Layer keeps an item and hands it over once phone and watch are connected, so a change
 * made while the watch is away arrives later by itself.
 *
 * Two settings of the watch stay where they were: the reminder before getting off and the
 * change time are the phone's own settings (AppPrefs), sent with the places
 * (PhoneBridge.syncPlaces), and a change on the watch holds until the phone changes its own.
 *
 * Twin: the watch has the same rules in W data/WatchSettingsSync.kt; keep them alike
 * (docs/conventions.md, "Keep twin implementations alike").
 */

/** One setting: its [value] as text and when it was changed ([changedAt], epoch ms; 0 = never, a default). */
data class SyncedSetting(val value: String, val changedAt: Long)

/** The keys of the settings both sides edit. Stored on disk and sent over the wire: never rename them. */
object WatchSettingKeys {
    /** "true"/"false": the live departures of the nearest stop on the watch's start page (default on). */
    const val LIVE_DEPARTURES = "liveDepartures"

    /** "true"/"false": the animations of the watch app (default on; off snaps everything, for older or slower watches). */
    const val ANIMATIONS = "animations"

    /** The style of the watch's map pictures: "Auto", "Light" or "Dark" (default Auto). */
    const val MAP_STYLE = "mapStyle"

    /** The destination of the watch's tile "To a favourite": a place as JSON (id, name, lat, lon, kind), "" for none chosen. */
    const val TILE_TARGET = "tileTarget"
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

/**
 * The phone's copy of the watch's settings (its own preferences "watch-settings"), as the
 * category "Watch" of the settings shows and edits it. [values] follows every change, from the
 * page or from the watch.
 */
object WatchSettings {
    /** The settings as known on the phone; empty (all defaults) until [load]. */
    val values = MutableStateFlow<Map<String, SyncedSetting>>(emptyMap())

    @Volatile
    private var loaded = false

    // Writing the data item runs off the page that changed a setting.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Reads the stored copy once per process; cheap. */
    fun load(context: Context) {
        if (loaded) return
        values.value = parseSettings(prefs(context).getString(KEY_SETTINGS, null))
        loaded = true
    }

    /** Sets [key] to [value] now and sends all settings to the watch (delivered once it is connected). */
    fun set(context: Context, key: String, value: String) {
        load(context)
        val changed = withSetting(values.value, key, value, System.currentTimeMillis())
        if (changed === values.value) return
        store(context, changed)
        val app = context.applicationContext
        scope.launch { PhoneBridge.putWatchSettings(app, settingsJson(changed)) }
    }

    /** Takes the settings the watch wrote ([json]): each one changed there later replaces the phone's. True when anything changed. */
    fun takeFromWatch(context: Context, json: String): Boolean {
        load(context)
        val merged = mergeSettings(values.value, parseSettings(json))
        if (merged == values.value) return false
        store(context, merged)
        return true
    }

    /** Reads the item the watch wrote, if any, and merges it (see [takeFromWatch]); for the settings page as it opens. */
    suspend fun refreshFromWatch(context: Context) {
        try {
            val items = Wearable.getDataClient(context).dataItems.await()
            try {
                for (item in items) {
                    if (item.uri.path != PhoneBridge.PATH_WATCH_SETTINGS_WATCH) continue
                    val json = DataMapItem.fromDataItem(item).dataMap.getString(KEY_JSON) ?: continue
                    takeFromWatch(context, json)
                }
            } finally {
                items.release()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // No Google Play services or no watch: the phone's copy stays.
        }
    }

    private fun store(context: Context, settings: Map<String, SyncedSetting>) {
        values.value = settings
        prefs(context).edit().putString(KEY_SETTINGS, settingsJson(settings)).apply()
    }

    // The names stored on disk: keep them as they are.
    private const val PREFS = "watch-settings"
    private const val KEY_SETTINGS = "settings"
    private const val KEY_JSON = "json"
}
