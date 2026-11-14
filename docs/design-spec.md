# Design spec: Next Stop (working title)

Date: 2026-11-11. Source: design sessions with the owner. All mockups are rough drafts with invented data.

## 1. Goal

A modern Android app (plus Wear OS) with the feature scope of Öffi NG and a contemporary interface. The graphical column view of connections is the core of the app.

## 2. Decisions

| Topic | Decision |
|---|---|
| Platform | Native Android app, Kotlin, Jetpack Compose. Wear OS app later. |
| Distribution | Google Play internal testing track (private profile on the owner's phone). Free testing on a second phone via USB first. |
| Data | `public-transport-enabler` (library behind Öffi). Berlin via the BVG provider, not VBB. Deutsche Bahn and other networks later. |
| Style | Material 3 Expressive with frosted-glass surfaces over a calm, light map ("Mix"). |
| Colours | Primary blue `#1B4FD8`, primary container `#DBE3FF`, on-container `#00174D`, live accent lime `#C8F16B` with text `#1A2600`, text `#171A21`, muted text `#454A57`. Line colours come from the transport network and never change. |
| Navigation | Floating bottom bar: Route, Departures, Map, Favourites. The active item shows icon and label, the others icon only. App starts on Route. Settings via a gear icon. |
| Name | "Next Stop" is a working title. Keep the application id neutral so the visible name can change. |

## 3. Screens (all drafted)

1. **Route (start):** map in the background; nearby stops as switchable chips with their next departures; saved places (home, work) as tiles with live travel time and a schematic leg bar; recent destinations; destination input at the bottom with a changeable origin.
2. **Connections, chart:** shared vertical time axis, one column per connection, one block per leg in line colour, transfer stop named in the gap, "now" line. Switch "Relaxed / Normal / Risky" for how tight transfers may be. Toggle chart/list.
3. **Connections, list:** one card per connection with times, leg bar and status.
4. **Bottom sheet:** tapping a column opens a sheet; swiping up shrinks the chart and shows legs, stops, platforms; per transfer the user can pick an earlier (risky) or later (relaxed) connecting service. Tapping a leg shows line, direction, platform and stops.
5. **Trip companion:** countdown to the next action, live line diagram per leg with vehicle position (including the approaching connecting train), transfer help with a carriage-and-door diagram, connection choice, "Missed the train", "Re-plan from here". A running companion is shown as a bar above the navigation in every section.
6. **Departures:** stop selector, line filter, departures with platform and delay.
7. **Map:** stops as markers, sheet for the selected stop.
8. **Favourites:** places, routes, stops.
9. **Settings:** network, transfers, default view, companion options, appearance.
10. **Watch:** departures, saved place, companion, best door.

## 4. Disruption display in the chart

| Case | Display |
|---|---|
| Late arrival | Hatched tail on the block, arrival time with "+n min" |
| Late departure | Planned position dashed, old time struck through |
| Tight transfer | Gap in red with remaining minutes |
| Cancellation | Whole column grey and hatched, labels struck through, red "cancelled" tag |
| Early departure | Yellow tag above the departure time |
| Replacement service | Separate block |

## 5. First build step (approved order)

1. Create the project, neutral application id.
2. Prove the data: one real request for the departures of one BVG stop.
3. Build the Route screen as drafted: light map, glass surfaces, nearby stops, departures, input, floating bar. Saved-place tiles are dummies.
4. Run on the test phone. Independent review against the requirements.
5. Stop for owner review.

Not part of this step: connection search, chart, companion, watch, Play Store.

## 6. Open points

- Map provider and style (calm, light) and a real blur effect on Android: biggest risk of the style, test on a device first.
- Data source for transfer help (best door, carriage, path) in Berlin.
- Does the BVG provider deliver disruption messages?
- Widgets, live notification and further watch views are designed later, when the basics run.
- Name check before any public release (other apps called "Nextstop"/"Next Stop" exist).
- Consequences of GPL-3.0 when distributing.
