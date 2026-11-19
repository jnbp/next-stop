package io.github.jnbp.nextstop.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The step-free setting as the interface observes it follows the one the walks use. */
class StepFreeSettingTest {
    @Test
    fun theObservedSettingFollowsTheWalks() {
        val before = stepFreeWalks
        try {
            stepFreeWalks = true
            assertTrue(stepFreeSetting.value)
            stepFreeWalks = false
            assertFalse(stepFreeSetting.value)
        } finally {
            stepFreeWalks = before
        }
    }
}
