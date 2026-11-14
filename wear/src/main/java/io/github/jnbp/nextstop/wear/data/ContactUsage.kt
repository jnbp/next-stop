package io.github.jnbp.nextstop.wear.data

import android.content.Context
import java.text.Collator
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray

/**
 * Which contacts of the phone were used as a destination on the watch, the last one first,
 * kept on the watch alone (its own preferences). The destination page shows the three used
 * last ([top]); without any, the first ones by name.
 */
object ContactUsage {
    /** The keys of the contacts used ([keyOf]), the last one first; empty until [load]. */
    val used = MutableStateFlow<List<String>>(emptyList())

    @Volatile
    private var loaded = false

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** A contact by name and address (a contact with two addresses is two entries). */
    fun keyOf(contact: SyncedContact): String = contact.name + "\n" + contact.address

    /** Reads the stored order once per process; cheap. */
    fun load(context: Context) {
        if (loaded) return
        val text = prefs(context).getString(KEY_USED, null)
        used.value = try {
            if (text == null) emptyList() else JSONArray(text).let { list -> (0 until list.length()).map { list.optString(it) } }
        } catch (e: Exception) {
            emptyList()
        }
        loaded = true
    }

    /** Marks [contact] as used just now. */
    fun record(context: Context, contact: SyncedContact) {
        load(context)
        val key = keyOf(contact)
        val list = (listOf(key) + used.value.filter { it != key }).take(MAX_KEPT)
        used.value = list
        prefs(context).edit().putString(KEY_USED, JSONArray(list).toString()).apply()
    }

    /** The [count] contacts of [all] used last (by [used]), filled up with the first ones by name. */
    fun top(all: List<SyncedContact>, used: List<String>, count: Int): List<SyncedContact> {
        val byKey = all.associateBy { keyOf(it) }
        val recent = used.mapNotNull { byKey[it] }.distinct().take(count)
        if (recent.size >= count) return recent
        return recent + byName(all).filter { it !in recent }.take(count - recent.size)
    }

    /** [all] by name, in the order of the language of the watch. */
    fun byName(all: List<SyncedContact>): List<SyncedContact> {
        val collator = Collator.getInstance()
        return all.sortedWith(compareBy(collator) { it.name })
    }

    private const val PREFS = "contact-usage"
    private const val KEY_USED = "used"
    private const val MAX_KEPT = 30
}

/**
 * The letter a name is filed under in the list of all contacts: its first letter without
 * accents, in capitals ("Ä" under "A"); "#" for names that begin with no letter.
 */
fun indexLetter(name: String): Char {
    val first = name.trim().firstOrNull() ?: return '#'
    if (!first.isLetter()) return '#'
    val plain = Normalizer.normalize(first.toString(), Normalizer.Form.NFD).firstOrNull() ?: first
    return plain.uppercase(Locale.ROOT).firstOrNull() ?: '#'
}
