package io.github.jnbp.nextstop.ui

/**
 * What a map feature was made from, to give the map the same feature again where nothing of
 * it changed (#71): a push of the vehicle layer made every badge anew, also those of vehicles
 * standing at a stop or settled in their row. [same] compares the inputs with those of the
 * feature kept; [keep] notes the inputs of a feature made now. Numbers compare exactly (NaN
 * equals NaN), objects by identity: a feature is reused only where it would come out alike.
 * Pure logic, no Android.
 */
internal class FeatureInputs {
    private val numbers = DoubleArray(SLOTS)
    private val objects = arrayOfNulls<Any>(OBJECT_SLOTS)
    private var known = false

    /** The feature made from the inputs noted last; null before the first. */
    var feature: Any? = null
        private set

    /** True when [values] and [refs] are exactly those noted with [feature]. */
    fun same(values: DoubleArray, refs: Array<Any?>): Boolean {
        if (!known || feature == null) return false
        for (i in 0 until SLOTS) {
            val a = numbers[i]
            val b = values[i]
            if (a != b && !(a.isNaN() && b.isNaN())) return false
        }
        for (i in 0 until OBJECT_SLOTS) if (objects[i] !== refs[i]) return false
        return true
    }

    /** Notes [made] as the feature of [values] and [refs]. */
    fun keep(made: Any, values: DoubleArray, refs: Array<Any?>) {
        values.copyInto(numbers)
        refs.copyInto(objects)
        feature = made
        known = true
    }

    /** Forgets the feature (a new style: its images are new). */
    fun forget() {
        known = false
        feature = null
    }

    companion object {
        /** How many numbers and objects make a feature. */
        const val SLOTS = 8
        const val OBJECT_SLOTS = 4
    }
}
