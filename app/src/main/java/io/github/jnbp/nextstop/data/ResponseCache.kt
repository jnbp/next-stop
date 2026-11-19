package io.github.jnbp.nextstop.data

/**
 * Short-lived answers of a provider by their address, so that the same question asked again
 * within seconds costs nothing. An answer is forgotten once it is older than the time it was
 * kept for (asked again or not: answers whose address holds the time, as the vehicles of the
 * map, are never asked for again and only took memory, #72), the oldest go first beyond
 * [maxEntries] answers or [maxChars] characters of text in all, and an answer longer than
 * [maxEntryChars] is not kept at all. Pure logic, no Android.
 */
internal class ResponseCache(
    private val maxEntries: Int,
    private val maxChars: Long,
    private val maxEntryChars: Int,
) {
    private class Entry(val body: String, val at: Long, val until: Long)

    // In order of use, the one used longest ago first.
    private val entries = LinkedHashMap<String, Entry>(16, 0.75f, true)

    /** The characters of text kept in all. */
    var chars: Long = 0
        private set
        get() = synchronized(this) { field }

    /** The answers kept. */
    val size: Int get() = synchronized(this) { entries.size }

    /** The answer to [path] if it is younger than [keepMillis] at [now]; null otherwise. */
    fun get(path: String, keepMillis: Long, now: Long): String? = synchronized(this) {
        val entry = entries[path] ?: return null
        if (now - entry.at < keepMillis) return entry.body
        remove(path)
        null
    }

    /** Keeps [body] as the answer to [path] for [keepMillis] from [now]. */
    fun put(path: String, body: String, keepMillis: Long, now: Long) = synchronized(this) {
        remove(path)
        // Answers whose time is up go now, not only when their address is asked again.
        val expired = entries.entries.filter { it.value.until <= now }.map { it.key }
        expired.forEach { remove(it) }
        if (keepMillis <= 0 || body.length > maxEntryChars) return
        entries[path] = Entry(body, now, now + keepMillis)
        chars += body.length
        // Walked without reading by key: a read reorders a map in order of use.
        val order = entries.entries.iterator()
        while ((entries.size > maxEntries || chars > maxChars) && order.hasNext()) {
            val oldest = order.next()
            if (oldest.key == path) continue
            chars -= oldest.value.body.length
            order.remove()
        }
    }

    private fun remove(path: String) {
        entries.remove(path)?.let { chars -= it.body.length }
    }
}
