# Connection detail parity (#129)

The connection detail page (`ui/Connections.kt`, `JourneyDetail`) gets the look of the calm
Live-Fahrt as its new default ("Verbindungsdetails: Neu"); the old body stays selectable as
"Klassisch". Per the rule "Neue Ansicht = vollständige Bestandsliste", this lists every element
and conditional state of the classic page first. The header of the page is kept as it is for
both styles; only the body below it changes.

Status: **todo** = not yet in the new body; **ok** = present in the new body (with its place);
**same** = shared code that both styles run unchanged (the header).

New body: `ui/DetailTimeline.kt` (`TimelineJourneyBody`), plan `ui/DetailRows.kt` (tested in
`DetailRowsTest`); parts shared with the Live-Fahrt from `ui/Companion.kt` (`CompanionWalkPart`,
`CompanionChangePart` / `ChangeEntry`, `PlatformWalkPart`, `CompanionPositionWalkPart`), so changes
there (#169) reach the detail too. Setting: `DetailStyleSettings` (Settings › Darstellung).

Line numbers of the classic page refer to `ui/Connections.kt` at commit 2ce87c2.

## Page frame and header (kept for both styles)

| Element | Classic | Condition | Status |
|---|---|---|---|
| Glass card with first-frame trace "NS journey page first frame" | `:4265` | always | same |
| Allocation probe `AllocProbe.DETAIL_COMPOSE` around the composition | `:4236`, `:4560` | always | same |
| Drag on the head moves the sheet border (`onDrag`, `onDragEnd`) | `:4273-4281` | `handle` | same |
| Handle bar, tap puts the card away / brings it back (`onHandle`) | `:4284-4300` | `handle` (sheet layout) | same |
| Minutes until departure pill (`MinutesPill`, live, cancelled) | `:4307-4313` | only where the width leaves room (`PillBeforeText`) | same |
| Time span departure - arrival, never cut | `:4315-4322` | always | same |
| Duration and number of changes | `:4329-4340` | always | same |
| Share button opens the share sheet (`ShareSheet`) | `:4343-4360` | always | same |
| Menu: save route (star / filled star) | `JourneyMenu` `:4362`, `:4697-4701` | always | same |
| Menu: remember pattern | `:4367` | journey has rides | same |
| Menu: add to calendar | `:4365` | always | same |
| Menu: plan trip / cancel plan | `:4369-4370` | departure in the future | same |
| Start button "Live-Fahrt starten" (`StartPill`) | `:4372` | always | same |
| Shared-element grow from the column / shrink back (`growFromColumn`) | caller `:1229` | animations on, opened from the chart/list | same |
| Jank meter page hooks (`jankPageDrawn("Detail")`) | caller `:1212` | always | same |
| Preview of the page in the shortcut sheet (`JourneyPreview`) | `:4184` | shortcut offer | same |

## Body: whole journey

| Element | Classic | Condition | Status |
|---|---|---|---|
| Body scrolls on its own, fading edges | `:4376-4386` | always | ok: `DetailTimeline.kt:176-180` (lazy list; no edge fade at the top under a pinned head: instead every item fades out beneath the pinned head, transparent at its middle, opaque 10 dp below it, #238, `DetailHeadFade.kt`, curve `underHeadAlpha` tested in `HeadFadeCurveTest`; the next ride's head is never faded; #251: as the next ride's head comes up, the pinned head's bottom corners round to the card radius over its last 40 dp and the next head pushes it up keeping an 8 dp gap, `rememberHeadPush` in `HeadPush.kt`, curves `pushCornerRadius`/`pushLift` tested in `HeadFadeCurveTest`; at rest the head looks as before) |
| Another journey is read from its top | `:4241` | journey changes | ok: `DetailTimeline.kt:140-142` |
| Stops loaded once when a carriage hint needs them | `:4252-4258` | boarding hints on, change between trains without stops | ok: `Connections.kt:4252` (shared, before the body) |
| "Verbindung angepasst" banner with "Rückgängig" | `:4387-4411` | `adapted` | ok: `Connections.kt:4389` (`AdaptedBanner`, shared) |
| Alerting disruption notices (`WarningSummary`) | `:4413` | journey has alerting warnings | ok: `Connections.kt:4390`, in item Top `DetailTimeline.kt:236` |
| Quiet notices (lifts for non step-free, others) | `:4414` | quiet warnings | ok: `Connections.kt:4391` |
| Fare zone note | `:4415` | fare zones known | ok: `Connections.kt:4392` |
| Lift outages on the way (step-free travel) | `:4416` | step-free setting, outage on the way | ok: `Connections.kt:4393` |
| Stay at a place on the way ("x min Aufenthalt in …") | `:4419-4428` | via with minutes | ok: `DetailTimeline.kt:237`, `DetailStayRow`; plan `DetailRows.kt` (`detailItems`) |
| Live hint at the foot ("Zeiten live …") | `:4552-4556` | always | ok: `DetailTimeline.kt:285-290` |

## Body: ride

| Element | Classic | Condition | Status |
|---|---|---|---|
| Boarding position map (bus/tram stop of many positions), folded | `:4432-4443` | first ride, or after a ride with no spot map / not the same platform | ok: first ride `DetailTimeline.kt:404` (`PositionWalkEntry`, map + Google Maps); later rides in the change `Companion.kt:1786-1793` |
| Line badge | `RideRow` `:5017` | always | ok: `DetailTimeline.kt:440` |
| Rail replacement badge (`PlainReplacementBadge`) | `:5016-5017` | `railReplacement` | ok: `DetailTimeline.kt:440`, in words `DetailTimeline.kt:511` |
| Full line name with number ("FEX 021857") | `:5034-5037` | `fullLineName` known, no replacement | ok: `DetailTimeline.kt:454` |
| Direction ("→ …") with spoken "Richtung …" | `:5029-5045` | direction known | ok: `DetailTimeline.kt:443-451` |
| Cancelled ride ("fällt aus"), grey rail | `:4965`, `:5019-5026` | `leg.cancelled` | ok: tag `DetailTimeline.kt:499-509`, grey rail `DetailTimeline.kt:551` |
| Chevron; tap on the line unfolds / folds the stops | `:5005-5014`, `:5047` | always | ok: `DetailTimeline.kt:427-470` (head, as 0.1.2) |
| Unfolding loads the stops when missing | `:4459-4463` | stops not loaded | ok: `DetailTimeline.kt:169` |
| Long press on the line: menu "Linienverlauf" | `:5009-5011`, `:5049-5059` | `tripId` known | ok: `DetailTimeline.kt:432-433, 472-482` |
| Visible link "Linienverlauf" under unfolded stops | `:5065-5076` | open and `tripId` known | ok: `DetailTimeline.kt:738-749` (`DetailRideFoot`) |
| Departure time: live colour, delay "+2", planned struck (delay setting) | `LegTime` `:4983`, `:4856-4900` | per delay display setting | ok: `DetailTimeline.kt:704` (`LegTime`) |
| Arrival time with plausible estimate "ca." | `:4984`, `:4913-4922` | prognosis implausible | ok: `DetailTimeline.kt:705` |
| Rail in line colour with rings at both ends | `:4987-5001` | always | ok: `DetailTimeline.kt:603-627` (`drawStopRail`, destination lime as in the Live-Fahrt) |
| Boarding stop name (town where another town) | `:5003`, `rideStopName` | always | ok: `DetailTimeline.kt:642` |
| Departure platform; moved platform marked amber | `:5004-5005`, `platformMoved` `:5140` | platform known / changed | ok: `DetailTimeline.kt:669-699` |
| Arrival stop name and platform; moved platform marked | `:5078-5084` | always / changed | ok: `DetailTimeline.kt:643, 681` |
| Stop menu on each end stop (dots and long press): from here, to here, departures, map, pin, favourite | `StopLine` `:5149-5222` | stop has a place | ok: `DetailTimeline.kt` `DetailStopLine` (tap on the row and the ⋮, drawn dots centred on the row, #186) |
| Stops between: name (town), time, "ca." estimate, stop menu | `RideStops` `:5089-5130` | unfolded, stops known | changed (#186): no ⋮, the row itself is the control: a tap opens the menu and so does a long press (`rowMenu`, click and long-click action both read out as "Optionen für diese Haltestelle"; nothing is reachable by a long press alone), the time stays at the right edge; `stopMenuOpening` in `DetailRows.kt`. Same in both Live-Fahrt layouts (`StopMenuBox(longPress)` in `CompanionCalm` and `Companion.StopRow`); folded row `DetailTimeline.kt:568-581` |
| "Halte werden geladen" while loading | `:5104` | unfolded, stops missing | ok: `DetailTimeline.kt:590` |
| "Halte konnten nicht geladen werden" | `:5106-5110` | load failed | ok: `DetailTimeline.kt:591` |
| "Keine Halte dazwischen" | `:5112-5116` | no stops between | ok: `DetailTimeline.kt:592` |
| Carriage boarding hint for the change after the ride | `:4447-4448` | boarding hints on, short change | ok: `DetailTimeline.kt` `DetailRideInfo`, main chip of `CompactRideHints` (#220) |
| Way out at the destination (`DestinationHint`) | `:4449-4451` | last ride, journey ends on foot | ok: `DetailTimeline.kt:518` |
| Occupancy, bikes, step-free, sun side (`RideHintsRow`) | `:5062` | known | ok: small chips of `CompactRideHints` (`RideChips.kt`, #220) in the row of the boarding hint, right-aligned to its right; the hint keeps at least two thirds of the row and wraps there, the chips side by side in the rest, else stacked in the right quarter, else below (#264, `hintChipArrangement`, `RideChipRowTest`); the sun side as a tappable chip (sun + arrow, "Sonne links" on the first 10 rides), its sentence as the spoken description and in the dialog; the bicycle chip (shown as before, from the provider's bicycle hints) opens its dialog with the provider's own words about bicycles on top where sent (#248, `Leg.bikeNote` from `bikeRemarkText`, `RideHintsTest`); bicycles and step-free come from HAFAS ReST remarks and, since #264, from the attribute notes of the official VBB API (`vbbNoteHints`), and survive a restart in the kept journey |
| Same vehicle continues as another line (no change) | `:4468-4479` | `throughRuns` with line change | ok: `DetailTimeline.kt:521-532` |

## Body: change between two rides

| Element | Classic | Condition | Status |
|---|---|---|---|
| Change time in minutes with rating word and tone colour | `TransferRow` `:4484`, `:4818-4822` | two rides in a row | ok: minutes `Companion.kt:1735-1745` (`ChangeEntry`), word `DetailTimeline.kt:807-814` |
| "zu spät" where the next ride leaves before the arrival | `:4819` | change not possible | ok: `Companion.kt:1742` |
| Same platform / how to change (`ChangeHint`) | `:4486` | numbers say so | ok: `Companion.kt:1801` |
| Spot walk map between two stop positions, folded | `:4488-4489` | `hasSpotWalkMap` | ok: `Companion.kt:1786-1787` |
| Change walk map to another place, folded | `:4490-4492` | `hasChangeMap`, next has no position map | ok: `Companion.kt:1794-1796` |
| Connection choice chips (`TransferChoice`) | `:4494`, `:4760-4780` | offers loaded, two or more options | ok: under the blue change card (`DetailChangeItem`), behind the toggle chip "Anschluss" (#243), on by the setting "Anschlusswahl" (B4) |
| Change also possible at another stop (`AltChangeOffers`) | `:4495`, `:4734-4757` | `altChanges` for this change | ok: `DetailTimeline.kt:835` |

## Body: walk

| Element | Classic | Condition | Status |
|---|---|---|---|
| Walk between rides: whole change time, rating | `:4505-4520` | walk between two rides | ok: `Companion.kt:1735-1754` + word `DetailTimeline.kt:807` |
| Walk between two stations: same platform hint | `:4521-4523` | both neighbours rides | ok: `Companion.kt:1801` |
| Walk of a change as small map (own way or change map) | `:4527-4530` | `changeWalkMapKind` not None | ok: `Companion.kt:1766-1778` |
| Choice chips and other change stops after a walk | `:4531-4532` | as above | ok: `DetailTimeline.kt:816-835` |
| Walk at start / end with minutes and target | `:4542` | minutes > 0 | ok: `DetailTimeline.kt:238` (`CompanionWalkPart`, `Companion.kt:1376`) |
| Bike leg with minutes and length | `:4535-4541` | cycling | ok: `Companion.kt:1376`, length `DetailTimeline.kt:249-258` |
| Walk map of the first / last walk, folded | `:4545-4547` | `hasWalkMap` | ok: `Companion.kt:1391` |
| Tap on a walk shows it on the map (`onFocusWalk`) | maps above | walk with way | ok: walk cards (tap unfolds the small map, map shows the way) and Google Maps chip `Companion.kt:1771` |

## Added in the new body (not in the classic one)

- Way inside the station to the platform before the first ride (#87): `DetailTimeline.kt:401`.
- Head line "ab · Gleis · an · Dauer" per ride, pinned at the top while its stops scroll
  (LazyColumn `stickyHeader`, `DetailTimeline.kt:186`).
- Folded middle stops as one row "N weitere Halte" with the stop dots on the rail. Folding them in and out is animated (#250): the stop rows are items of the lazy list, they fade in and out (`animateItem`) and the rows and heads below glide to their places while the fold settles (only then, so nothing chases a card growing with `animateContentSize`); off with animations off.
- Rail replacement also in words (`RailReplacementTag`).

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
| "Anschluss wechseln ▾/▴" link | replaced by the toggle chip "Anschluss" (#243) under the blue card on the page ground, in line with it; it toggles in every setting (r7plan) |
| "Welchen Anschluss willst du nehmen?" over the tiles | dropped in the new views; kept as the accessibility description of the tile group (classic unchanged) |
| Connection tiles (`ConnectionChoice` / `TransferChoice`) | right under the link, white tiles on the page ground, never inside the blue |
| "Andere Wege ab hier" (Live-Fahrt) | #243: now on the detail page too, as the toggle chip "Andere Wege" (route icon) beside "Anschluss"; a way taken adapts the journey shown (`onAdapt`, as a connection tile). Each chip with its own section below, as in the Live-Fahrt (Variante 2: the connection tiles, then the other ways as a list of rows; see companion-parity.md); `ChangeChoiceArea`, `ConnectionChips` and `OtherWaysList` in both. Not in a journey preview (`JourneyPreview`, `otherWays = false`): it has nothing to adapt |
| Other change stops (`AltChangeOffers`, detail) | unchanged, under the change |
| Dotted rail only where the walk had minutes | always through the whole change (picker included) and into the gaps to the ride cards |

### Done and made on the detail page

The detail page has no live progress of its own: nothing is over there unless its journey is
the one followed as the Live-Fahrt (`TripState.journey`). Then it mirrors the Live-Fahrt: the
first walk and every change are over once the ride after them has left (`detailPartOver`,
tested in `BlueChangeRulesTest`): grey and pale (animated once), a knapp or sprint change made
turns its chip green with a tick, maps, hints and the connection choice of a change that is over
are left out as in the Live-Fahrt. The page looks again only when the next ride leaves
(`detailNextWake`, one wake per departure, no ticking). Ride cards of the detail keep their look.

## Known differences

- The connection choice (#243: the chip "Anschluss") starts on by the setting "Anschlusswahl" (B4, shared with the Live-Fahrt, under Settings › Unterwegs beside the walk mini maps): Eingeklappt = off at every change; Alle offen = on at all, still a toggle; Nur der nächste = on at the first change. A tap on the chip turns that change for this page (not stored); `detailPickerOpen`, tested in `BlueChangeRulesTest`. The classic detail keeps all open.
