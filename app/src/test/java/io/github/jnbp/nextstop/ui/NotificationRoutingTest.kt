package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationRoutingTest {
    @Test
    fun everyFeatureGoesToItsChannel() {
        val expected = mapOf(
            NotifyFeature.Trip to ChannelKind.Live,
            NotifyFeature.WatchStarted to ChannelKind.Live,
            NotifyFeature.Alight to ChannelKind.Important,
            NotifyFeature.Platform to ChannelKind.Important,
            NotifyFeature.Connection to ChannelKind.Important,
            NotifyFeature.Planned to ChannelKind.Important,
            NotifyFeature.Shortcut to ChannelKind.Suggestions,
            NotifyFeature.WakeAlarm to ChannelKind.Alarm,
            NotifyFeature.Disruptions to ChannelKind.Disruptions,
        )
        assertEquals(NotifyFeature.entries.toSet(), expected.keys)
        NotifyFeature.entries.forEach { assertEquals(it.name, expected[it], channelKindOf(it)) }
    }

    @Test
    fun exactlyFiveChannelsAllUsed() {
        assertEquals(5, ChannelKind.entries.size)
        assertEquals(ChannelKind.entries.toSet(), NotifyFeature.entries.map(::channelKindOf).toSet())
    }

    @Test
    fun newIdsAreUniqueAndNeverRetired() {
        val ids = ChannelKind.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        ids.forEach { assertTrue(it, it !in RETIRED_CHANNEL_IDS) }
        // Every id of the channels before is removed on start.
        listOf("trip", "trip_live", "alight", "faster", "watch", "disruptions", "wake_alarm", "platform", "planned", "connection")
            .forEach { assertTrue(it, it in RETIRED_CHANNEL_IDS) }
    }
}
