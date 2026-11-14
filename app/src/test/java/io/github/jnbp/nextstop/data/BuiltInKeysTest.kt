package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Keys built into the app (secrets.properties) and the ones the user enters: the user's win. */
class BuiltInKeysTest {
    @Test
    fun enteredKeyWinsOverBuiltIn() {
        assertEquals("mine", effectiveKey(" mine ", "built"))
        assertEquals("built", effectiveKey("  ", "built"))
        assertEquals("", effectiveKey("", ""))
    }

    @Test
    fun keyPairsAreNeverMixed() {
        assertEquals("id" to "key", effectiveKeyPair("id", "key", "bid", "bkey"))
        // Only half a pair entered: the built-in pair as a whole.
        assertEquals("bid" to "bkey", effectiveKeyPair("id", "", "bid", "bkey"))
        assertNull(effectiveKeyPair("id", "", "bid", ""))
        assertNull(effectiveKeyPair("", "", "", ""))
    }

    @Test
    fun builtInVbbKeyMakesTheBackendUsableWithoutShowingIt() {
        val choice = SourceChoice(builtInVbbAccessId = "secret-built-in")
        assertTrue(choice.configured(Backend.Vbb))
        assertEquals("secret-built-in", choice.vbbKey)
        assertEquals("", choice.vbbAccessId)
        assertEquals("entered", choice.copy(vbbAccessId = "entered").vbbKey)
        assertTrue("secret" !in choice.toString())
    }
}
