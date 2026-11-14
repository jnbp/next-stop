package io.github.jnbp.nextstop.wear.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject

/*
 * The lines of the last connection started on the watch to each destination, kept on the watch
 * alone (its own preferences, "usual-lines"). A favourite that is not a stop shows them as the
 * usual way there: no search runs for it, so the start page costs no request per favourite.
 * At most [MAX_KEPT] destinations, the latest first.
 */
object UsualLines {
    /** Lines by the id of the destination; empty until [load]. */
    val byPlace = MutableStateFlow<Map<String, List<StopLine>>>(emptyMap())

    @Volatile
    private var loaded = false

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Reads the stored lines once per process; cheap. */
    fun load(context: Context) {
        if (loaded) return
        byPlace.value = try {
            parse(prefs(context).getString(KEY_LINES, null))
        } catch (e: Exception) {
            emptyMap()
        }
        loaded = true
    }

    /** Keeps the lines of [journey] as the usual way to [destination]; a walk alone keeps nothing. */
    fun record(context: Context, destination: Place, journey: Journey) {
        load(context)
        val lines = usualLinesOf(journey)
        if (lines.isEmpty()) return
        val kept = LinkedHashMap<String, List<StopLine>>()
        kept[destination.id] = lines
        byPlace.value.forEach { (id, value) -> if (id != destination.id && kept.size < MAX_KEPT) kept[id] = value }
        byPlace.value = kept
        prefs(context).edit().putString(KEY_LINES, format(kept)).apply()
    }

    private fun parse(text: String?): Map<String, List<StopLine>> {
        if (text.isNullOrEmpty()) return emptyMap()
        val root = JSONObject(text)
        val result = LinkedHashMap<String, List<StopLine>>()
        val order = root.optJSONArray("order") ?: return emptyMap()
        val lines = root.optJSONObject("lines") ?: return emptyMap()
        for (index in 0 until order.length()) {
            val id = order.optString(index)
            val list = lines.optJSONArray(id) ?: continue
            result[id] = (0 until list.length()).mapNotNull { k ->
                val item = list.optJSONObject(k) ?: return@mapNotNull null
                val name = item.optString("line").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                StopLine(name, item.optString("product"))
            }
        }
        return result
    }

    private fun format(map: Map<String, List<StopLine>>): String {
        val lines = JSONObject()
        map.forEach { (id, list) ->
            lines.put(id, JSONArray().apply { list.forEach { put(JSONObject().put("line", it.name).put("product", it.product)) } })
        }
        return JSONObject().put("order", JSONArray(map.keys.toList())).put("lines", lines).toString()
    }

    // The names stored on disk: keep them as they are.
    private const val PREFS = "usual-lines"
    private const val KEY_LINES = "lines"
    private const val MAX_KEPT = 30
}
