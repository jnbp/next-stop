package io.github.jnbp.nextstop.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The phone's side of the merge of the watch's settings; the twin of the watch's WatchSettingsSyncTest. */
class WatchSettingsSyncTest {
    private val key = WatchSettingKeys.LIVE_DEPARTURES

    @Test
    fun theNewerChangeWins() {
        val watch = mapOf(key to SyncedSetting("false", 2_000))
        val phone = mapOf(key to SyncedSetting("true", 3_000))
        // Whichever side merges, both end up with the newer value.
        assertEquals("true", mergeSettings(watch, phone)[key]?.value)
        assertEquals("true", mergeSettings(phone, watch)[key]?.value)
    }

    @Test
    fun aTieKeepsTheOwnValueSoNothingFlipsBackAndForth() {
        val watch = mapOf(key to SyncedSetting("false", 2_000))
        val phone = mapOf(key to SyncedSetting("true", 2_000))
        assertEquals("false", mergeSettings(watch, phone)[key]?.value)
        assertEquals("true", mergeSettings(phone, watch)[key]?.value)
    }

    @Test
    fun keysOnlyOneSideKnowsAreKept() {
        // A newer app on one side added a setting the other does not know: it travels on untouched.
        val watch = mapOf(key to SyncedSetting("false", 5_000))
        val phone = mapOf("someFutureSetting" to SyncedSetting("42", 1_000))
        val merged = mergeSettings(watch, phone)
        assertEquals(setOf(key, "someFutureSetting"), merged.keys)
        assertEquals("42", merged["someFutureSetting"]?.value)
    }

    @Test
    fun theWatchWorksOfflineAndCatchesUpLater() {
        // Changed on the watch while the phone was away, then on the phone later: the phone's change wins.
        var watch = withSetting(emptyMap(), key, "false", now = 1_000)
        val phone = withSetting(emptyMap(), key, "true", now = 9_000)
        watch = mergeSettings(watch, phone)
        assertTrue(settingFlag(watch, key, default = true))
        // Setting the same value again keeps its time (and the very same map: nothing to write).
        assertSame(watch, withSetting(watch, key, "true", now = 20_000))
    }

    @Test
    fun defaultsAndBadInput() {
        assertTrue(settingFlag(emptyMap(), key, default = true))
        assertFalse(settingFlag(mapOf(key to SyncedSetting("false", 0)), key, default = true))
        assertTrue(settingFlag(mapOf(key to SyncedSetting("maybe", 0)), key, default = true))
    }
}
