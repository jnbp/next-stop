# Screenshot plan for Google Play

Extends [README.md](README.md) (rules: no personal data, no keys, central stations only). Target:
`fastlane/metadata/android/<locale>/images/phoneScreenshots/` and `.../wearScreenshots/` (the layout
Play, fastlane `supply` and F-Droid read). Take every shot twice, German UI (de-DE, main) and English UI
(en-US), light theme, same trips.

## Sizes (Play Console, [9866151](https://support.google.com/googleplay/android-developer/answer/9866151))

| Asset | Size | Format |
|---|---|---|
| Phone screenshots | 2 to 8; each side 320 to 3840 px, the long side at most twice the short side. Take **1080 x 1920** (9:16); at least four at 1080 px or more make the app eligible for large-format recommendations | JPEG or 24-bit PNG, no alpha |
| Wear OS screenshots | **1:1**, at least 384 x 384; take **454 x 454** from a round emulator. No device frames, no extra text or backgrounds; must match the real app | PNG |
| App icon | 512 x 512, max 1024 KB | 32-bit PNG |
| Feature graphic | 1024 x 500 | JPEG or 24-bit PNG, no alpha |

Phone shots without device frames or captions; Play shows them as they are. Captions, if wanted, must
not promise anything the app does not do.

## Phone: 8 shots (minimum 4)

All with sample data. Use the emulator (Pixel, 1080 x 2400 or 1080 x 1920) with a mocked position at Hauptbahnhof, a fixed
clock around 12:00 and a fixed time zone, so that departures look plausible.

| # | File | Screen | Content |
|---|---|---|---|
| 1 | `1_connections.png` | Connections as chart | Hauptbahnhof to Alexanderplatz with one change, several columns on the time axis, transfer levels visible |
| 2 | `2_transfer.png` | One connection opened | The change at Alexanderplatz with level (Normal / Knapp), exact walk minutes and the boarding hint |
| 3 | `3_companion.png` | Trip companion | Next step, countdown ring, stops left, get-off reminder |
| 4 | `4_notification.png` | Live notification | Lock screen or shade with the ongoing trip notification (Live Update on an Android 16 emulator) |
| 5 | `5_map.png` | Map | Live vehicles around Hauptbahnhof and a stop card |
| 6 | `6_station.png` | Station close-up | Alexanderplatz with entrances, lifts and stop positions |
| 7 | `7_start.png` | Start page | Nearby stops with next departures, destination field, Deutschlandticket / transport filter visible |
| 8 | `8_share.png` | Share | Live link or route link page ("So kommst du hin" / the route page) in the browser, using `web/live/#demo` and `web/route/#demo` |

If only four are wanted for the first test: 1, 2, 3, 5.

## Watch: 4 shots (minimum 2, at least one required for the Wear OS form factor)

Round emulator 454 x 454, Wear OS 3 or newer, same sample trip, standalone mode (no phone data).

| # | File | Screen |
|---|---|---|
| 1 | `1_tile.png` | The *Next departure* tile at Hauptbahnhof |
| 2 | `2_companion.png` | Trip companion page with countdown and boarding hint |
| 3 | `3_favorite_tile.png` | *Favourite* tile (Alexanderplatz) or the watch face with the complication |
| 4 | `4_map.png` | Map of the stops nearby (shows "© OSM") |

The listing text already says "Wear OS", names tiles and the complication, and states that layouts are for round displays.

## How to take them

```bash
adb exec-out screencap -p > 1_connections.png        # phone
adb -s <watch-serial> exec-out screencap -p > 1_tile.png   # watch
```

- Android Studio: *Running Devices > camera icon* works as well; on the watch emulator crop to the circle area only if a rectangular frame is captured (no masking, no transparent corners: Play rejects them).
- Remove alpha and reduce size if needed: `magick in.png -background white -alpha remove -alpha off out.png`, then `oxipng` or `pngquant` (keep each file under about 500 KB, README rule).
- Check each image for: no real names, contacts, appointments, saved places, keys or addresses; no stations near a private address.
- Put German files in `de-DE/images/phoneScreenshots/` and `wearScreenshots/`, English in `en-US/...`; Play Console takes the same files by upload (drag in order of the file names).

## Graphics

- **Icon:** see `docs/play-release.md` section 8 (Android Studio *Image Asset > Play Store icon* is the easiest, or export `docs/images/icon.svg` without the rounded corners to 512 x 512).
  Save as `fastlane/metadata/android/de-DE/images/icon.png` (and `en-US`) once it exists.
- **Feature graphic:** `fastlane/metadata/android/de-DE/images/featureGraphic.svg` (and `en-US`) in the hero style (brand blue `#1B4FD8`,
  lime `#C8F16B`, white). Export:
  `cairosvg featureGraphic.svg -W 1024 -H 500 -b '#1B4FD8' -o featureGraphic.png`
  (flat, no alpha; verified to give a 1024 x 500 8-bit sRGB PNG without an alpha channel). If another renderer is used, flatten alpha and check
  that the long tagline and the pill text stay inside their shapes with the font in use.
