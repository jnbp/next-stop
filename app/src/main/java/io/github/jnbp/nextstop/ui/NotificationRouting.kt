package io.github.jnbp.nextstop.ui

/** What the app notifies about. Each goes out on one of the few channels of [ChannelKind]. */
enum class NotifyFeature {
    /** The followed trip, live and quiet (the ongoing notification or Live Update). */
    Trip,

    /** "Trip started on the watch, tap to follow it here" (see PhoneBridge). */
    WatchStarted,

    /** Get off: one stop before and shortly before the arrival (see AlightReminders). */
    Alight,

    /** A change of platform on a followed trip (see PlatformAlerts). */
    Platform,

    /** A connection at a change broke by a delay (see ConnectionRescue). */
    Connection,

    /** "Bald losgehen" / "Jetzt losgehen" of a planned trip, with its buttons (see PlannedTripAlarms). */
    Planned,

    /** Something faster turned up for a followed trip (see FasterWatch). */
    Shortcut,

    /** Rings like an alarm clock before getting off a long ride (see WakeAlarm). */
    WakeAlarm,

    /** New disruptions on a watched route (see DisruptionWatch). */
    Disruptions,
}

/**
 * The notification channels of the app, by importance, with their ids. The ids are new
 * with this set of channels: the ones before are removed (see [RETIRED_CHANNEL_IDS]).
 */
enum class ChannelKind(val id: String) {
    /** "Live-Fahrt": the followed trip, quiet. */
    Live("live_trip"),

    /** "Live-Fahrt: Wichtig": prompts to act now, with sound and vibration. */
    Important("live_trip_important"),

    /** "Live-Fahrt: Vorschläge": shown, but without sound. */
    Suggestions("live_trip_suggestions"),

    /** "Wecker": the wake-up alarm, full screen on the stream of alarms. */
    Alarm("wake_up_alarm"),

    /** "Störungen": disruptions on the watched routes. */
    Disruptions("route_disruptions"),
}

/** The channel [feature] goes out on. */
fun channelKindOf(feature: NotifyFeature): ChannelKind = when (feature) {
    NotifyFeature.Trip, NotifyFeature.WatchStarted -> ChannelKind.Live
    NotifyFeature.Alight, NotifyFeature.Platform, NotifyFeature.Connection, NotifyFeature.Planned -> ChannelKind.Important
    NotifyFeature.Shortcut -> ChannelKind.Suggestions
    NotifyFeature.WakeAlarm -> ChannelKind.Alarm
    NotifyFeature.Disruptions -> ChannelKind.Disruptions
}

/** The ids of the channels before [ChannelKind]; removed on every start of the app. */
val RETIRED_CHANNEL_IDS: Set<String> = setOf(
    "trip", "trip_live", "alight", "faster", "watch", "disruptions", "wake_alarm", "platform", "planned", "connection",
)
