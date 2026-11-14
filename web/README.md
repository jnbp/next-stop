# Next Stop web pages

Two static web pages for links that the app shares. The browser of the recipient asks the
public timetable APIs itself; there is no server of our own.

- **Live page** (`<site>/live/`): follow the trip of someone live – where their vehicle is and
  when they arrive.
- **Route page** (`<site>/route/`, "So kommst du hin"): a connection shared so that someone
  else can travel it themselves, step by step, with live times.

**Both show the planned trip with real-time data, not the GPS position of a phone.**

```
web/
  shared/core.js       logic of both pages: link codec, parsers of BVG REST and Transitous,
                       geometry, line colours, clock and freshness texts (no DOM, also for Node)
  shared/ui.js         DOM helpers of both pages: badges, walk icon, rolling numbers
  shared/base.css      design of both pages: colours, dark mode, glass, badges, live dot, motion
  shared/fonts/        Google Sans Flex (SIL OFL 1.1, see fonts/OFL.txt), served by the site itself
  live/index.html      live page (German, English by browser language; dark mode by the system)
  live/app.js          its logic, no build step, no framework
  live/style.css       its layout: glass cards over a full-screen map
  live/demo.js         made-up trip for "#demo" (only loaded then); live/demo.html redirects to it
  route/index.html     route page (German, English by browser language; dark mode by the system)
  route/app.js         its logic
  route/style.css      its layout: steps in one column, the map sticky above them (beside them on wide screens)
  route/demo.js        made-up connection for "#demo"; route/demo.html redirects to it
  test/live.test.js    offline tests: node web/test/live.test.js
  test/route.test.js   offline tests: node web/test/route.test.js
  test/fixtures/       route-v1.json: the route link JSON of the app for a fixed connection
```

## Live page

- **Hero card** on top: "<Name> ist unterwegs nach <Ziel>" (without a name in the link:
  "Unterwegs nach …"), the arrival in large digits coloured by the delay (green on time,
  amber 1–4 min late, red from 5 min), a countdown ("in 23 min"), a thin progress bar of the
  whole trip with a tick at each change, one line with the current state ("Im U8 · nächster
  Halt Moritzplatz", "Steigt um in U Rudow · X7 um 15:16", "Zu Fuß zu …", "Angekommen um …")
  and a pulsing live dot with "aktualisiert vor 12 s" / "Keine Live-Daten seit 2 min".
- **Map** (MapLibre over OpenFreeMap, its dark style in dark mode): the route in the line
  colours of the app, the part already travelled greyed out, a destination pin and the vehicle
  as a badge with the line name (a walker on foot). The vehicle moves continuously along the
  trip's polyline between the stops (computed from the live times every frame and eased, so a
  correction after an update glides instead of jumping). The map follows the vehicle; a pan or
  pinch stops that and shows a "Folgen" button.
- **Timeline** in a sheet below (a column on the left on wide screens): one compact row per leg
  (badge, from → to, times); only the current leg is open, with the stops passed counted, the
  next three stops and "+ n weitere Halte". Tap a row to open or close it.
- **End states:** "Angekommen" after the arrival, "Fahrt beendet · Link abgelaufen" 2 h later.

## Route page ("So kommst du hin")

- **Header:** where it leads, the day ("Heute", "Morgen", "Sa., 15.11."), departure – arrival,
  "Los geht's in 12 min", duration and changes, and the live dot.
- **Steps**, one card per leg:
  - a ride: the departure time (amber when late) and delay, line badge and direction, the stop
    with its platform ("Gleis 2", "Steig 3", "Pos. 11") or stop position ("Pos. 3") – a
    platform change as "Gleis 6 statt 5" –, where to board for the next change ("In der Mitte
    einsteigen"; "Eher …" when the app estimated it from the map), the number of stops (the
    stops themselves once live data came in), the state of real time, and where to get off;
  - a walk: minutes and metres, the station exit to take or the entrance / stop position it
    leads to, a mini map of the way (made only while it is on screen) and a button
    "Weg in Google Maps" (walking directions between the two points, nothing else);
  - the arrival.
  Tapping a step zooms the map to it and fades the rest of the route.
- **Map:** the whole route in the line colours, walks dashed, start and destination marked.
- **Real time:** more than about 30 min before a ride the APIs rarely know anything, so it says
  "Echtzeit ab ca. 30 min vor Abfahrt" (e.g. for a connection tomorrow at 8:12) instead of "no
  live data". Then delays, cancellations and platform changes as they come.
- **Honest banner** when something goes wrong: a cancelled ride, a change that will likely not
  work out (the ride before plus the walk arrives after the next one leaves) or gets tight
  (under 2 min), or a first ride that has already left ("Schon unterwegs? Dann folge einfach
  weiter den Schritten."). It offers "Spätere Verbindung suchen", which leads to the box at the
  end: a search from the start to the destination on the Transitous website, and on Android
  "In Next Stop öffnen" (an `intent://open/route?…` link to the app, with the Transitous search
  as fallback when the app is not installed).

## Demos

Both demos ask no timetable API: their `demo.js` fakes the clock and BVG-shaped answers. The
map itself still needs the MapLibre script and the OpenFreeMap tiles.

- `https://<site>/live/#demo` (or `live/demo.html`) plays a made-up trip (walk, U8, U7, X7 to
  BER) in fast motion. Options: `#demo&at=-5` (before the start), `&at=70` (arrived), `&at=200`
  (expired), `&speed=60`, `&name=` (no name), `&stale=1` (the fake API stops answering after a
  minute), `&arrive=1`.
- `https://<site>/route/#demo` (or `route/demo.html`) shows Alexanderplatz → (S5) Ostkreuz →
  (FEX) Flughafen BER twelve minutes before the start, with a "+5 min" button to fast-forward.
  Options: `#demo&at=8` (minute of the trip), `&speed=30`, `&late=1` (the S5 is 6 min late, the
  change breaks), `&cancel=1` (the FEX is cancelled), `&tomorrow=1` (tomorrow at 08:12).

## Hosting

Any static host works; there is nothing to build. Upload the whole `web/` folder, so that the
pages are reachable at `https://<your-site>/live/` and `https://<your-site>/route/` (both use
`../shared/`).

- **Cloudflare Pages:** Workers & Pages → Create → Pages → *Upload assets*, drop the `web`
  folder. Or connect the repository with no build command and `web` as the output directory.
- **GitHub Pages:** Pages only publishes the root or `/docs` of a branch, so either push the
  contents of `web/` to a branch of its own (e.g. `gh-pages`) or use a Pages workflow that
  uploads `web/` as the artifact.

The app links to the project website `https://nextstop.bapo.me` (which hosts this folder) by
default. To use your own copy, enter the address of the site (for example
`https://next-stop-live.pages.dev`, without `/live` or `/route`) in the app under
*Settings → Live link → Address of the web pages*; clearing it goes back to the project website.
Sharing goes through one sheet (`ShareSheet.kt`): the share button of a connection's detail and the
live-share button of the trip companion open it. It offers "Link zur Verbindung" (the route page with a
short line; recommended for a connection, with "Link kopieren" beside it), "Live mitverfolgen lassen"
(the live page; recommended for a followed trip, and for a connection only when it leaves within the
hour, which then starts the trip) and "Nur als Text". An address that is no web address leaves the
link cards shown but disabled, with a hint.

Put a contact (e.g. a link to the repository) on the site: Transitous asks web apps that cannot
send a User-Agent to be identifiable by their Referer and to name a contact there.

## Info site

`index.html` (German) and `en/index.html` (English) are the project's website, with their CSS,
icon and images in `site/` (the images are copies of `docs/images` plus German variants). Plain
HTML and CSS: no scripts, no cookies, nothing loaded from other hosts (fonts from `shared/fonts/`),
light and dark by the system. It is published with the rest of `web/`, so `/live/` and `/route/`
stay where they are. The Google Play badge is prepared as a comment in both pages.

## Link formats (v1)

Both are `#v=1&d=<base64url JSON>` (UTF-8, no padding) in the fragment. Coordinates have 5
decimals; delays (minutes known when shared) and empty fields are left out. `p` is `bvg` (HAFAS
ids, `v6.bvg.transport.rest`) or `tr` (Transitous/MOTIS ids, without the app's `tr:` prefix).

**Live link** `https://<site>/live/#v=1&d=…`, built by `liveLinkJson` in `ui/JourneyShare.kt`:

```json
{"n":"optional first name","t":1794570000,
 "dest":{"name":"…","lat":52.48693,"lon":13.42484},
 "legs":[{"p":"bvg","trip":"<trip id>","line":"U8","dir":"…",
          "from":{"name":"…","id":"<stop id>","lat":…,"lon":…,"dep":<epoch s planned>,"dd":<delay min>},
          "to":{"name":"…","id":"…","lat":…,"lon":…,"arr":<epoch s planned>,"ad":<delay min>}},
         {"walk":true,"from":{…},"to":{…}}]}
```

**Route link** `https://<site>/route/#v=1&d=…`, built by `routeLinkJson` in
`ui/JourneyShare.kt` (no name field). Key order as in `test/fixtures/route-v1.json`, which
`RouteLinkTest.kt` (app) and `route.test.js` (page) both check, so the two sides stay in step:

```json
{"legs":[{"p":"bvg","trip":"…","line":"S5","prod":"suburban","dir":"Strausberg Nord",
          "from":{"name":"…","id":"…","lat":…,"lon":…,"dep":…,"dd":2,"pl":"2","pos":"3"},
          "to":{"name":"…","id":"…","lat":…,"lon":…,"arr":…,"ad":3,"pl":"3"},
          "n":3,"b":"m","be":true,"x":true,"poly":"<encoded polyline>"},
         {"from":{…,"exit":"A"},"to":{…,"entr":"B"},"walk":true,"m":180,"poly":"…"}],
 "dest":{"name":"…","lat":…,"lon":…},"t":<epoch s shared>}
```

`pl` platform as the provider names it, `pos` OpenStreetMap stop position ("Pos. 3"), `exit` /
`entr` the station exit a walk leaves by / the entrance it leads to, `n` stops in between,
`b` where to board for the next change (`f` front, `m` middle, `r` rear; `be` estimated from
the map), `x` cancelled when shared, `m` walk metres, `poly` the way (Google encoded polyline,
precision 5, simplified by the app to about 4 m for walks and 25 m for rides; left out for a
straight line). A connection of three legs gives a link of about 1–2 kB.

## Privacy

- The trip is in the **fragment** (`#…`) of the link. Browsers never send the fragment to the
  web server, so the host only sees a request for `/live/` or `/route/`, not the trip.
- The pages ask `v6.bvg.transport.rest` and/or `api.transitous.org` for the trips in the link
  (trip ids only), and `tiles.openfreemap.org` for the map. Those services see the IP address
  of the recipient and, as Referer, only the origin of the site (`strict-origin-when-cross-origin`).
- The route page's buttons open Google Maps (walking directions: only the two ends of that
  walk) and the Transitous website (start and destination of the connection) only when tapped.
- MapLibre GL JS is loaded from unpkg with a pinned version and Subresource Integrity. The font
  comes from the site itself (no Google Fonts request).
- Nothing is stored (the live page keeps one flag in sessionStorage so the arrival confetti
  plays once), no cookies, no analytics. A Content-Security-Policy limits the pages to exactly
  these hosts.
- Anyone who has a link can see what is in it: the stops, times and the optional first name
  (live link), and the start of the connection – which is the current position when the search
  in the app started there.

## Behaviour and limits

- Live page: polls every 20 s only the rides that run now or start within 45 min, every 60 s
  while the tab is hidden, and at once when it becomes visible again; backs off up to 5 min on
  errors or HTTP 429; stops 2 h after the arrival ("link expired"). The animation pauses while
  hidden.
- Route page: asks every ride once when opened (its way, stops, cancellations), then every 30 s
  the rides that leave within 40 min or run now, every 2 min while hidden; the same back-off.
  The link does not expire; after the arrival it says the connection is behind and offers a
  later one.
- When requests fail, the pages say so ("Keine Live-Daten seit 2 min") and keep the last
  known times; without any answer they show the times of the link.
- The BVG API allows about 100 requests per minute per client and has CORS enabled. For
  Transitous the browser access depends on the CORS headers of `api.transitous.org`; if a
  request is blocked, the pages keep showing the times as they were when shared and say so.
- If the traveller changes plans, a shared link still shows the old journey. Share a new link.
- Trip ids of HAFAS can change or expire after a timetable change; then the pages fall back to
  the times in the link.
- The live position is an estimate between stops. Without live data it is the timetable position.
- "In Next Stop öffnen" opens the app with the search between the start and destination of
  that link (`nextstop://open/route?fromName=…&from=lat,lon&toName=…&to=lat,lon`, see
  `AppLinks.kt`); the places are routed to by their coordinates.

## Testing offline

```
node web/test/live.test.js                  # logic tests of the live page with sample answers
node web/test/live.test.js <base64url>      # also checks a live fragment made by the app
node web/test/route.test.js                 # logic tests of the route page, the shared fixture, the demo
node web/test/route.test.js <base64url>     # also checks a route fragment made by the app
```

To try the pages, serve the folder (`python3 -m http.server -d web 8080`) and open
`http://localhost:8080/live/#demo` or `http://localhost:8080/route/#demo`, or a fragment from
the app.

## License and attribution

The pages are part of Next Stop and licensed like the app: GNU GPL version 3 with the app store
permission (see `LICENSE`, `LICENSE-EXCEPTION.md` and `NOTICE` at the root of the repository).
The foot of both pages says so and links the source code on GitHub, as the GPL and the app store
permission ask. It also names what the pages show:

- timetable and real-time data: BVG/VBB (through `v6.bvg.transport.rest`) and Transitous, with a
  link to [the list of Transitous' sources](https://transitous.org/sources/);
- the map: tiles and styles of [OpenFreeMap](https://openfreemap.org/), map data
  © [OpenStreetMap contributors](https://www.openstreetmap.org/copyright) (ODbL 1.0); the map's
  own compact attribution button (MapLibre) shows the same;
- MapLibre GL JS (BSD-3-Clause) and Google Sans Flex (SIL OFL 1.1, `shared/fonts/OFL.txt`).

Keep these lines when you change the pages or host your own copy.
