# Screenshots

The main README shows these images once they exist. Until then it keeps them in a collapsed
block, so GitHub shows no broken images.

| File              | Screen                                                        |
|-------------------|---------------------------------------------------------------|
| `start.png`       | Start page with the map and live vehicles                     |
| `connections.png` | Connections as columns on a shared time axis                  |
| `companion.png`   | Trip companion during a ride, with a boarding hint            |
| `map.png`         | Close-up of a station: entrances, lifts, stop positions       |
| `watch.png`       | Wear OS: trip companion or tile                               |

## How to take them

- Phone: a recent Pixel or emulator, light theme, 1080 px wide, PNG.
  `adb exec-out screencap -p > docs/screenshots/start.png`
- Watch: a round Wear OS emulator (e.g. 454 × 454), PNG.
- Use sample trips between well-known central stations.

## Rules

- No personal data: no names, contacts, appointments or saved places of real people.
- No home or work addresses, and no stations near them. Use well-known central stations
  (for example Alexanderplatz, Hauptbahnhof, Zoologischer Garten) as examples.
- No keys or tokens visible in any settings screen.
- Keep each file under about 500 KB (`pngquant` or `oxipng` help).

When the images are in place, move the screenshot table in the main README out of the
`<details>` block.
