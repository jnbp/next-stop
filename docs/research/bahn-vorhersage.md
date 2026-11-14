# Bahn-Vorhersage: may Next Stop show its connection score?

Research of 2026-11-14. No code was written for this.

## Verdict

**No, not without written permission from the operator.** Bahn-Vorhersage has no public API.
The web app's own backend can be called, but the site's FAQ answers the question "Darf ich die
API von Bahn-Vorhersage verwenden?" with a plain **"Nein."**: the operators can send only a
limited number of requests to Deutsche Bahn's servers and need them for their own users. What
they offer third parties instead is their open data and trained models, to build and run an
application of one's own ([bahnvorhersage.de, FAQ][site]).

So a call from Next Stop to `bahnvorhersage.de/api/...` is not allowed today, even as an opt-in.
Öffi NG does it (see below), and the author once tolerated low-volume on-demand use with a
proper User-Agent, but that was an informal comment in someone else's issue tracker, and the
site's own statement is the later and more general one. If we want the score, the order is:

1. Ask the operator (contact via the [imprint][imprint]) for permission for an opt-in,
   on-demand, user-triggered request with an identifying User-Agent; get it in writing.
2. Or compute a score ourselves from their open data (ODbL) and published models: allowed,
   but a project of its own (see "Alternatives").

Because using the API is not clearly allowed, there is no design sketch of a setting here.

## What Bahn-Vorhersage is

A delay and connection-reliability predictor for German trains by Theo Döllmann and others,
trained on real-time departure data the project has collected since October 2020 and retrained
weekly ([site][site]). The site and its Android app show a score for each transfer of a
journey. It is independent of Deutsche Bahn ([privacy][privacy]).

## API: endpoints and format (unofficial, undocumented)

There is no API documentation; the "Code & Docs" page lists only the repositories and talks
([opensource][oss]). What is known comes from third-party clients:

- **Journeys search** (used by the web front end and by a now-archived Home Assistant
  integration): `POST https://bahnvorhersage.de/api/journeys`, JSON body with `start`,
  `destination`, `date` (`"25.01.2025 12:00"`), `search_for_arrival`, `only_regional`, `bike`;
  answers a JSON array of journeys whose legs carry times, platforms and delay predictions
  ([ha-bahnvorhersage README][ha]). This makes Bahn-Vorhersage itself query DB for the search.
- **Score of a given journey, v2** (used by Öffi NG): `POST https://bahnvorhersage.de/api/mobile/v2/journeys`
  with `{"journeys": [ { "type": "journey", "legs": [...], "refreshToken": "<DB refresh token>" } ],
  "trips": { "<trip id>": {...} } }`. Each ride leg carries origin and destination (id, name,
  latitude, longitude), planned and live times and platforms, the trip id, the line (name, DB
  train number, admin code, product), direction and every stopover; transfers between rides are
  legs of type `transfer` (walking, distance). The answer is an array of journeys; each leg of
  type `transfer` has a `transferScore` (a number; Öffi NG reads it as the probability of making
  the change) ([public-transport-enabler, BahnvorhersageProviderV2][ptev2]).
- **v1** (older, still in the same library): `POST https://bahnvorhersage.de/api/refresh-journey`
  with `{"refresh_token": "<DB refresh token>"}` ([BahnvorhersageProviderV1][ptev1]).

Both scoring endpoints need DB journey identifiers (a db-vendo/HAFAS refresh token and trip
ids), so they only work for journeys found with a DB-based source; Next Stop's VBB/BVG HAFAS and
Transitous journeys would need mapping first. Only changes with trains on both sides are
reliably scored (comment in the same code).

## Terms of use and licences

| What | Licence / terms | Source |
| --- | --- | --- |
| Use of the API by third parties | **Not permitted** ("Nein.", FAQ) | [site][site] |
| Rate limit | None published; the author blocked a client that polled every few seconds and asked for requests only on user demand with a proper User-Agent (Feb 2025) | [ha issue #1][haissue] |
| Backend code | GPL-3.0-or-later (GitLab; old GitHub repo GPL-3.0, archived) | [GitLab][gitlab], [GitHub][github] |
| Open data: stops, parsed delays, raw delays | ODbL, "best effort" | [open data][opendata] |
| Operator-code mapping | no licence stated | [open data][opendata] |
| Trained models | published, licence not stated on the site | [site][site] |

Next Stop is GPL-3.0 with an app-store exception that covers only our own code
(LICENSE-EXCEPTION.md). Copying GPL code from Bahn-Vorhersage would be licence-compatible but
not covered by that exception; calling an HTTP API does not raise this question.

## Privacy: what we would send

Their privacy policy says every API request is stored with the User-Agent, time, IP address and
the request parameters, including the journey searched, and that this cannot be tied to
persons; retention and hosting place are not stated ([privacy][privacy]). For the v2 endpoint
that means: the user's IP address plus the full planned journey (stations, times, trains,
platforms) and DB's refresh token. A journey from or to a saved place ("Zuhause") reveals that
place's nearest station and the user's daily routine. Any integration would have to be off by
default, explain this, and send only stations and trains, never addresses or coordinates of the
user's own places (the walking legs at the ends would have to be cut off).

## Öffi NG

Öffi NG (a fork of Öffi, GPL) integrates the score: "Integration des Anschluss-Scores von
Bahn-Vorhersage" ([Öffi NG wiki][oeffiwiki]). It is opt-in: the setting "Load extra info for
trips" ("Zusatzinformationen") under the connection settings, off by default, "so far only for
DB-based providers"; the trip details then colour each change red, stronger as the chance to
make it falls ([wiki][oeffiwiki]; `user_interface_directions_tripextrainfo_enabled` in
[oeffi-ng][oeffi]). The request is made by its fork of public-transport-enabler with the app's
User-Agent, once per opened trip, to `api/mobile/v2/journeys` ([ptev2][ptev2]). Whether Öffi
NG has the operator's permission is not stated anywhere we found.

## Alternatives that are allowed

- **Ask for permission** (see Verdict), offering: opt-in, one request per opened journey,
  never polling, a Next Stop User-Agent with a contact address, attribution in the journey
  detail and in "About".
- **Own estimate from open data**: the parsed delay data (ODbL) gives delay distributions per
  train and station; a transfer probability is P(arrival delay − departure delay < change time
  − walk). That runs on the device or as a static table built at build time, sends nothing,
  and needs ODbL attribution (and share-alike for a database derived from it). Large effort;
  only for long-distance and regional trains.

[site]: https://bahnvorhersage.de
[imprint]: https://bahnvorhersage.de/imprint
[privacy]: https://bahnvorhersage.de/privacy
[oss]: https://bahnvorhersage.de/opensource#content
[opendata]: https://bahnvorhersage.de/open-data
[gitlab]: https://gitlab.com/bahnvorhersage/bahnvorhersage
[github]: https://github.com/TrAInConnectionPrediction/tcp
[ha]: https://github.com/faserf/ha-bahnvorhersage
[haissue]: https://github.com/FaserF/ha-bahnvorhersage/issues/1
[oeffiwiki]: https://github.com/santawho/oeffi-ng/wiki/%C3%96ffi-NG-(Deutsch)
[oeffi]: https://github.com/santawho/oeffi-ng
[ptev2]: https://github.com/santawho/public-transport-enabler/blob/nextgen/src/de/schildbach/pte/provider/db/bahnvorhersage/BahnvorhersageProviderV2.java
[ptev1]: https://github.com/santawho/public-transport-enabler/blob/nextgen/src/de/schildbach/pte/provider/db/bahnvorhersage/BahnvorhersageProviderV1.java
