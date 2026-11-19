package io.github.jnbp.nextstop.data

import android.content.Context
import java.time.OffsetDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The store of the recorded trips. [records] is read once on first use and follows every
 * new trip; a trip is recorded where the companion ends ([ended]).
 */
object TripStats {
    private val loaded = MutableStateFlow<List<TripRecord>?>(null)

    /** The recorded trips, oldest first; empty until [load] ran. */
    val records: StateFlow<List<TripRecord>?> get() = loaded

    private val book = TripRecordBook(loaded)

    // The one small preferences file of the statistics; only its records key is used.
    private fun text(context: Context) = object : TripRecordText {
        private val prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        override fun read(): String? = prefs.getString(KEY_RECORDS, null)
        override fun write(text: String?) {
            if (text == null) prefs.edit().remove(KEY_RECORDS).apply() else prefs.edit().putString(KEY_RECORDS, text).apply()
        }
    }

    /** Reads the records once (cheap: one small JSON string). */
    fun load(context: Context): List<TripRecord> = book.load(text(context))

    /**
     * The companion of [journey] ends at [now]: kept when it was ridden to its end (see
     * [tripRecordOf]). Called from every place a trip ends; the same journey is kept once.
     */
    fun ended(context: Context, journey: Journey?, now: OffsetDateTime = OffsetDateTime.now()) {
        journey ?: return
        val record = tripRecordOf(journey, now) ?: return
        book.add(text(context), record)
        DiagLog.i(TAG) { "trip recorded: ${record.legs.size} rides, delay ${record.arrivalDelayMinutes}" }
    }

    /** Forgets all recorded trips ("Statistik zurücksetzen", #195); nothing else is touched. */
    fun clear(context: Context) {
        book.clear(text(context))
        DiagLog.i(TAG) { "trip statistics reset" }
    }

    private const val STORE = "trip_stats"
    private const val KEY_RECORDS = "records"
    private const val TAG = "TripStats"
}
