# Vibrations on the watch

Every event of a trip has a rhythm of its own, so the wrist tells them apart without looking.
Each plays once and lasts at most about a second (pattern in `wear/.../data/BuzzPatterns.kt`).

| Event | Rhythm | Feels like |
|---|---|---|
| Boarding / departure (trip starts, next ride boarded) | short · short | "ta-ta" |
| Get off at the next stop (last ride) | long · short | "taaa-ta" |
| Change at the next stop | short · short · short | "ta-ta-ta" |
| Platform changed, ride missed or cancelled, change broken | long · short · long | "taaa-ta-taaa" (urgent) |
| Arrived | one soft pulse | "mm" |

They play as notification vibrations: the watch's notification vibration strength applies,
nothing plays during Do Not Disturb or bedtime, and the reminder before getting off follows
its setting (phone or watch). The urgent rhythm plays while the live trip is open on the
watch; in the background the phone's notification for it arrives with the watch's usual
notification vibration. The wake-up alarm keeps its own long alarm pattern.
