package io.github.jnbp.nextstop.data

/*
 * A small memo for lookups that are asked again and again with the same key from the interface,
 * often several times per frame (the change positions: every row, every recomposition and every
 * rating of a change asks the dataset). The lookup runs once per distinct key; its answer is
 * kept, also when it is "nothing" (null), until the data behind it changes and [LookupCache.clear]
 * is called. The oldest keys are dropped beyond [LookupCache.capacity], so a long session does
 * not grow it without end.
 *
 * Free of Android classes, so it is tested as a JVM test (LookupCacheTest).
 */

/**
 * Remembers the answer of a lookup per key, null answers included, for at most [capacity] keys
 * (the least recently used go first). Thread-safe; the lookup itself runs outside the lock, so
 * two threads may compute one key at the same time once, which is harmless for a pure lookup.
 * An answer computed while [clear] was called is not kept: it may come from the old data.
 */
class LookupCache<K : Any, V : Any>(val capacity: Int) {
    // One answer; a wrapper so a kept null can be told from a missing key.
    private class Entry<T>(val value: T?)

    private val entries = object : LinkedHashMap<K, Entry<V>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, Entry<V>>?): Boolean = size > capacity
    }

    // Counts the calls of [clear]; an answer is kept only when it did not change while computing.
    private var generation = 0L

    /** The kept answer for [key], or the one [compute] gives now (then kept, also null). */
    fun getOrCompute(key: K, compute: (K) -> V?): V? {
        val started = synchronized(this) {
            entries[key]?.let { return it.value }
            generation
        }
        val value = compute(key)
        synchronized(this) {
            if (generation == started) entries[key] = Entry(value)
        }
        return value
    }

    /** Forgets all answers: the data behind them changed. */
    fun clear() {
        synchronized(this) {
            generation++
            entries.clear()
        }
    }

    /** How many answers are kept. */
    val size: Int
        get() = synchronized(this) { entries.size }
}
