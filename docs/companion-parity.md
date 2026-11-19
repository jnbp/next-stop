# Companion parity inventory

What the connection detail page (`ui/Connections.kt`, `JourneyDetail` / `RideRow`) and the
classic companion (`ui/Companion.kt`) show per leg, and where the new calm and map views
(`ui/companion/`) show the same. The map view uses the same timeline as the calm view
(`CompanionTimeline`, `CompanionMapLayout.kt:134`, with `maps = false` because the big map
behind shows walks). Parts shared with the classic view (`CompanionWalkPart`,
`CompanionChangePart`, `WalkCard`, `ChangeEntry`) live in `ui/Companion.kt`.

Status: **ok** = shown in the new views; **added** = added in round 6 (branch r6content);
**partial** = shown in a reduced form; **missing** = not shown yet; **n/a** = not meant for the
companion.

## Whole journey

| Element | Detail | Classic | New views | Status |
|---|---|---|---|---|
| Disruption notices (alerting) | `WarningSummary` | yes | `CompanionModern.kt:312` | ok |
| Quiet notices (lifts for non step-free, others) | `QuietNotices` | yes | `CompanionModern.kt:313` | ok |
| Lift outages on the way (step-free travel) | `JourneyLiftWarning` | no | `CompanionModern.kt:315` | added |
| Fare zones | `FareZoneNote` | no | - | n/a (before the trip) |
| Platform change ahead | - | `CompanionExtrasStrip` | `CompanionModern.kt:299` (`CompanionExtras.kt:73`) | ok |
| Position ("Naechster Halt", on board early, standing) | - | `CompanionExtrasStrip` | `CompanionModern.kt:299` | ok (names per #157) |
| Faster way (shortcut) | - | `FasterCard` (now with preview too) | `CompanionModern.kt:301`, with preview (#120) | ok |
| Broken change, best way that works | - | `RescueOffer` | `CompanionModern.kt:310` | ok |
| Wake-up alarm | - | alarm tile | hero chip `CompanionModern.kt:177`, more menu (#116) | ok |
| Stay at a place on the way (via with minutes) | `BetweenRow` (stay) | - | stay row in `CompanionTimeline` `CompanionCalm.kt:198` | added |

## Ride

| Element | Detail | Classic | New views | Status |
|---|---|---|---|---|
| Line badge | `LineBadge` | yes | `CompanionCalm.kt:360`, map head `CompanionMapLayout.kt:165` | ok (#121 corner fix) |
| Direction | yes | yes | ride head (`towards`) | ok |
| Full train name with number ("FEX 021857") | `fullLineName` | no | ride head `CompanionCalm.kt:364` | added |
| Cancelled ride in words | struck, "faellt aus" | grey rail | `CompanionCalm.kt:392` tag "Faellt aus" | added |
| Rail replacement in words | badge + text | `RailReplacementTag` | `CompanionCalm.kt:404` | added |
| Occupancy, bikes, step-free, sun side | `RideHintsRow` | yes | `CompanionCalm.kt`, small chips of `CompactRideHints` (#220) to the right of the boarding hint, right-aligned; the hint keeps at least two thirds of the row and wraps there, the chips side by side in the rest, else stacked in the right quarter, else below, right-aligned (#264, `hintChipArrangement`, `RideChipRowTest`); the bicycle chip's dialog shows the provider's words about bicycles where sent (#248) | ok |
| Where to board for the change (carriage) | `CarriageHint` at every ride | blue box + timeline | `CompanionCalm.kt`, main chip of `CompactRideHints` (#220), every ride not over (#127); hero subline | added |
| Way out at the destination | `DestinationHint` | yes | `CompanionCalm.kt:437` | ok |
| Departure / arrival platform at the stop rows | in the head line | yes | `CompanionCalm.kt:467` (#153), head line, hero | added |
| Planned time struck beside the live one | `LegTime` | partly | `StopLine` `CompanionCalm.kt:631` (`plannedShown`) | added |
| Stops on the way, unfoldable | `RideStops` | yes | `StopRail` `CompanionCalm.kt:454`; on board the boarding stop and the stops passed stay above the own dot, grey with their times, more than four passed fold their middle into "N Halte gefahren ▾" (`stopRows`, #187) | ok |
| Town of a stop in another town | `RideStops` (`rideStopName`) | - | `StopRail` names `CompanionCalm.kt:466` | added |
| Railway names on long-distance rides | `shownRideStop` | ? | `StopLine` railway flag; other rows per #157 | ok |
| Line course | `onCourse` | - | visible link "Linienverlauf ›" under the open stops, long press on the ride head as a shortcut (`CompanionCalm.kt`, `CourseRequests`, opened by `AppRoot`) | added |
| Same vehicle changes its line (no change) | note | - | `CompanionCalm.kt:408` | added |
| Boarding position map (bus/tram at many positions) | `BoardingPositionMap` | yes | `CompanionPositionWalkPart` `CompanionCalm.kt:226` (first ride), change part (later rides) | ok |
| Way inside the station to the platform | - | `PlatformWalkPart` | `CompanionCalm.kt:230` | ok |

## Change

| Element | Detail | Classic | New views | Status |
|---|---|---|---|---|
| Change time and rating | `TransferRow` | yes | `CompanionChangePart` `CompanionCalm.kt:240` (`ChangeEntry`) | ok |
| Same platform / how to change | `ChangeHint` | yes | `ChangeEntry` `Companion.kt:1801` (shared) | ok |
| Walk card with minutes, exit, target | `BetweenRow` | `WalkCard` | `WalkCard` (layout #161, second line "Gleis 16 → Gleis 2") | ok |
| Google Maps for the walk | - | yes | `WalkCard` chip (`openWalkNavigation`) | ok |
| Change walk map | `ChangeMapOf` / `ChangeWalkMap` | yes | `FoldedMap` `Companion.kt:1787-1796`, calm only; big map in map view | ok |
| Two stop positions apart | `SpotWalkMap` | yes | `Companion.kt:1787` | ok |
| Choice of connections | `TransferChoice`, `AltChangeOffers` | chips | under the blue change card: the toggle chip "Anschluss" (#243, was the link "Anschluss wechseln"), on per `pickerShow` (#138 setting, shared with the detail, B4); its row of tiles below the chips (order by departure at the change, the chosen one at its place, #215; #115 marks); since Variante 2 (Joel 19.11.) the other ways are a list below it, never mixed into the row | ok |

## Walk / bike (first and last leg)

| Element | Detail | Classic | New views | Status |
|---|---|---|---|---|
| Walk with minutes and target | `BetweenRow` | `WalkCard` | `CompanionWalkPart` `CompanionCalm.kt:211` | ok |
| Bike leg with length | `BetweenRow` (cycling) | `WalkCard` cycling | same `WalkCard` (bike glyph) | ok |
| Walk map | `WalkMap` | yes | `CompanionWalkPart` (calm), big map (map view) | ok |
| Exit of the station ("ueber Ausgang H") | - | `walkExitText` | shared (`Companion.kt:1516`) | ok |
| Google Maps | - | yes | `WalkCard` | ok |

## Beyond the classic view

| Element | New views | Status |
|---|---|---|
| Other ways from a change ("Andere Wege ab hier", #164) | the toggle chip "Andere Wege" with a route icon beside "Anschluss" (#243, `ChangeChoiceArea` in `ChangeChips.kt`, `OtherWaysPart` in `CompanionOtherWays.kt`) at every change ahead; "Fahrt aendern" menu ("Andere Wege ab Umstieg" searches and turns both chips on) | added |
| One name per station (#157) | change, walks, next stop, reminder, hero and trip notification (`PageNames.kt`) | added |
| Pinned status line while the sheet covers the blue box (#225) | map view only: `CompanionPinnedStatus` (`CompanionPinnedStatus.kt`) at the top of the sheet's content under its handle, shown by `heroPinShown` (`HeroPin.kt`, more than 60 % of the blue box covered, gone below 30 %); line badge, the blue box's sentence (`stepLabel`) and the small lime pill (`MinutePill(compact = true)`, same times and rules); a tap scrolls the list to the top and puts the sheet to its middle; one content description. The list scrolls beneath the line and fades out there (#245, as under the detail page's pinned heads, #238: transparent at the line's middle, opaque 10 dp below it, only while scrolled; `rememberPinFade` in `PinFade.kt`, curve `underHeadAlpha`/`pinCoversContent` tested in `HeadFadeCurveTest`). The calm view keeps its sticky blue box (#156), with the timeline now scrolling beneath it and fading out the same way (#245); the classic companion and the detail page are unchanged. The push of stacked ride heads of the detail page (#251) has no counterpart here: the Live-Fahrt has no sticky ride heads | added |
| Handle line of the sheet over the map once no ride is left (#246) | map view only: the put-away sheet's one line (`SheetSummary`) shows the final walk ("Zu Fuß nach …" / "Mit dem Rad nach …", "an 10:12", walk or bike icon) or the arrival ("Angekommen", flag) where it used to stay empty and leave a bare strip; `sheetSummaryKind` (`SheetSummaryRule.kt`, `SheetSummaryRuleTest`) | added |

## Walks and changes in blue (r6blue)

Everything walked is a blue card on the dotted rail (the walk tint `AppColors.BlueContainer` /
`OnBlueContainer` the classic walk cards always had, with its dark counterpart); rides stay white
cards. This holds for the first walk, every change and the last walk, in the new detail body and
in both new Live-Fahrt layouts (`WalkCard` with `LocalWalkCardStyle` set). The classic Live-Fahrt
and the classic detail body are unchanged.

| Element | Before | Now |
|---|---|---|
| Walk card ground | white card (`AppColors.Card`) | blue (`BlueContainer`), text `OnBlueContainer`; grey (`Chip`) and pale once the walk is over, animated once |
| Walk glyph and chevron | main blue | `OnBlueContainer` (readable on blue in dark) |
| "Karte zeigen" button | grey chip | white chip on the blue card |
| Google Maps button | blue container chip | white chip on the blue card |
| Second line ("Gleis 16 → Gleis 2") | muted | `OnBlueContainer` at 80 % |

### The change card (B2, B3, B5)

Inventory of the change as it was (`ChangeEntry` in the new views, round 6) and where each part
is now (`ui/BlueChange.kt`, `BlueChangeEntry`; the classic Live-Fahrt keeps `ChangeEntry`):

| Element before | Now |
|---|---|
| Title "Umstieg in <station>" | dropped as a line (the station is the arrival of the ride above); still read out by screen readers in the head line |
| Minutes, large, in the rating colour | head line "Umstieg · 3′" with the walk symbol (`Glyph.Walk`, as chart, list and peek draw walks via `WalkMark`; bike glyph for the bike combo) |
| Rating word under the title (and a second one in the detail before "Anschluss wechseln") | once, as a small chip at the right of the head in the tone colours (`toneContainer` / `toneInk`); read out in words |
| "zu spät" where the change cannot work | the chip says "zu spät" |
| "Umstieg geschafft" (green, node springs into a check) | the chip turns green with a tick drawing in; the node on the rail as before |
| Platform step "Gleis 16 → Gleis 2" | second line `changeStepLine`: "Gleis 1 → Pos. 1 (Bus 285)" (from-platform → to-platform or stop position, next line in brackets; tested in `ChangeStepLineTest`) |
| "Umsteigen in 285: <station>, Pos. 1" (no walk leg) | dropped: platform, position and line are in the second line |
| Walk card "Fußweg 4′ nach <station>, <entrance>, über Ausgang H" | third line, only where it says more: walk minutes, another stop the walk leads to, entrance, exit; re-routed walk "Neuer Weg: noch …" |
| Same platform / other stop position (`ChangeHint`) | inside the blue card |
| Small map of the walk / change / two stop positions / position of the next bus (folded) | "Karte zeigen" in the blue card, opens in place; follows "Minikarte für Fußwege immer aufgeklappt" (`rememberWalkMapOpen`) |
| Google Maps button for the walk | kept, white chip in the row of buttons (#191, `BlueWalkActions`) |
| Over the big map: a tap shows the walk there | still a tap on the blue card, and now also a visible chip "Karte öffnen" in every blue walk and change card (detail and Live-Fahrt, first walk, changes, last walk; #191). Row of chips "Karte zeigen ▾" (only where the small map is drawn here) · "Karte öffnen" · Google Maps; where three do not fit, "Karte öffnen" becomes a round icon at the end ("Auf großer Karte öffnen"; `blueActionsFit`, tested in `BlueChangeRulesTest`). A change without a walk leg opens the way its small map draws (`changeFocusWalk`) |
| Change over: pale | turns grey and pale, animated once (snapped without animations) |
| "Anschluss wechseln ▾/▴" link | replaced by the toggle chip "Anschluss" (#243) under the blue card on the page ground, in line with it; "Alle ausgeklappt" only starts it on: it toggles in every setting (r7plan) |
| "Welchen Anschluss willst du nehmen?" over the tiles | dropped in the new views; kept as the accessibility description of the tile group (classic unchanged) |
| Connection tiles (`ConnectionChoice` / `TransferChoice`) | right under the link, white tiles on the page ground, never inside the blue |
| "Andere Wege ab hier" (Live-Fahrt) | #243: two independent toggle chips in one row, "Anschluss" and "Andere Wege" (route icon). Variante 2 (Joel 19.11.): each chip has its own section below. "Anschluss" on: the compact row of connection tiles (`ConnectionChips`, by departure, the chosen one at its place; "frühester"/Sprint, +/- deltas, walk minutes, long-wait colours, "zu spät", loading/failed lines, the rescue offer and Plan B). "Andere Wege" on: below it a list of full-width rows (`OtherWaysList` in `CompanionOtherWays.kt`): the arrival large ("an 04:43"), the delta and "20′ später" or the changes ±, the whole chain of lines as small badges with arrows (long walks of 5 min or more as a walking figure, `wayChain`), and a round take button with a check mark (read out "Übernehmen"); a tap on the row opens the preview with "Übernehmen" spelled out, the row of the last preview is framed. Three rows at first, the rest behind "Weitere anzeigen" (`wayRowsShown`/`wayRowsMore`). Loading, "Keine anderen Wege gefunden" and the failed line (tap tries again) below the list. Both sections fold in and out on their own (`MapFold`, expand + fade, at once with animations off), so all four changes of the chips animate (#257); tiles that come later (loaded, reachable again) come in softly. "Andere Wege" starts off, searches on its first tap, folds and unfolds after that (`waysChipTap`/`waysChipOn`, `ChangeTileRowTest`). Same in the detail page (`ChangeChoiceArea` in both) |
| Other change stops (`AltChangeOffers`, detail) | unchanged, under the change |
| Dotted rail only where the walk had minutes | always through the whole change (picker included) and into the gaps to the ride cards |

## The bottom bar of the Live-Fahrt (r7plan)

"Plan B", "Ändern" (was "Fahrt ändern", cut to "Fahrt änd…", #214) and ⋯ opened dropdown menus;
now each opens a sheet from the bottom in the look of "Teilen" (`BarSheet`,
`CompanionBarSheet.kt`, cards of `ShareOptionCard`): icon, name, one line of what it does (#217).
The sheet is opaque on a scrim (#213). Every entry kept, same conditions, same action:

| Button | Entry (shown when) | Icon | Action |
|---|---|---|---|
| Plan B | "<Bus/Tram/U-Bahn/S-Bahn/Zug/Fähre/Fahrt> verpasst" (before boarding, near the departure) | Search | `onMissed(missedFrom)` |
| Plan B | "Anschluss verpasst" (change ahead) | Swap | `onMissedChange` |
| Plan B | "Mein Bus … ist zu spät / fällt aus" (replan possible) | Recent | `onReplan` |
| Plan B | "Ich sitze im falschen Bus …" (replan possible) | Locate | `onReplan` |
| Ändern | "Neu planen von hier" | Locate | `onReplan` |
| Ändern | "Ziel ändern" | Flag | `onChangeDestination` |
| Ändern | "Anderen Anschluss wählen" (change ahead) | Swap | `onOtherConnection` |
| Ändern | "Andere Wege ab Umstieg" (change ahead) | Route | `onOtherWays` |
| ⋯ | "Abkürzungen melden" (switch, the sheet stays open) | Navigate | `onShortcutAlert` |
| ⋯ | "Jede Minute zählt" (switch, the sheet stays open) | Bolt | `onEveryMinute` |
| ⋯ | "Wecker …" | Alarm | `onWake` |

The ride words follow the kind of the ride concerned (`RideKind`, #212).

## Open gaps

None: every element of the detail page and the classic companion has its place in the new views.
