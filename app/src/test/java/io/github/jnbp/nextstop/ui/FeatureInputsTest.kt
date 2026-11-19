package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** A vehicle that did not change gives the map its feature again; any change makes a new one. */
class FeatureInputsTest {
    private val look = Any()
    private val badge = Any()

    private fun values(lat: Double = 52.52, nose: Double = Double.NaN) =
        doubleArrayOf(lat, 13.41, nose, 1.0, 0.0, 0.0, Double.NaN, 1.0)

    private fun refs(l: Any = look) = arrayOf<Any?>(l, badge, null, null)

    @Test
    fun theSameInputsGiveTheSameFeature() {
        val inputs = FeatureInputs()
        assertFalse(inputs.same(values(), refs()))
        val made = Any()
        inputs.keep(made, values(), refs())
        assertTrue(inputs.same(values(), refs()))
        assertSame(made, inputs.feature)
    }

    @Test
    fun anyChangeMakesANewOne() {
        val inputs = FeatureInputs()
        inputs.keep(Any(), values(), refs())
        assertFalse(inputs.same(values(lat = 52.5200001), refs()))
        assertFalse(inputs.same(values(nose = 90.0), refs()))
        assertFalse(inputs.same(values(), refs(l = Any())))
        inputs.forget()
        assertFalse(inputs.same(values(), refs()))
    }

    @Test
    fun theInputsAreCopiedNotHeld() {
        val inputs = FeatureInputs()
        val v = values()
        val r = refs()
        inputs.keep(Any(), v, r)
        v[0] = 1.0
        r[0] = Any()
        assertTrue(inputs.same(values(), refs()))
    }
}
