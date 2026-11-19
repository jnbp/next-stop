// Offline tests of the logic of web/live/app.js: node web/test/live.test.js [v2 data from Kotlin]
// With an argument (base64url of the raw-deflated liveLinkCompactJson of LiveLinkTest.kt), it also
// checks that a fragment made by the app (JourneyShare.kt) decodes.
// test/fixtures/live-v2.json is the v2 JSON the app writes for a fixed trip (LiveLinkTest.kt checks
// the Kotlin encoder against the same file); live-v2.deflate.txt is that JSON raw-deflated by
// java.util.zip.Deflater(nowrap = true), as the app does it.
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const zlib = require('node:zlib');
const test = require('node:test');
const live = require('../live/app.js');

const T0 = Date.UTC(2026, 10, 13, 13, 0) / 1000; // 14:00 in Berlin
const sample = {
  n: 'Ana',
  t: T0 - 300,
  dest: { name: 'Hermannplatz', lat: 52.48693, lon: 13.42484 },
  legs: [
    { walk: true, from: { name: 'Start', lat: 52.52, lon: 13.405, dep: T0 }, to: { name: 'U Alexanderplatz', id: '900100005', lat: 52.52153, lon: 13.41123, arr: T0 + 300 } },
    { p: 'bvg', trip: '1|31911|21|86|13112026', line: 'U8', dir: 'Hermannstraße',
      from: { name: 'U Alexanderplatz', id: '900100005', lat: 52.52153, lon: 13.41123, dep: T0 + 360, dd: 2 },
      to: { name: 'U Hermannplatz', id: '900078101', lat: 52.48693, lon: 13.42484, arr: T0 + 1080, ad: 3 } },
  ],
};
const fragment = (obj) => '#v=1&d=' + live.toBase64Url(JSON.stringify(obj));
const iso = (s) => new Date(s * 1000).toISOString();

// A trip as the BVG API answers /trips/:id?polyline=true&stopovers=true (shortened).
const bvgTrip = {
  trip: {
    line: { name: 'U8' }, direction: 'S+U Hermannstr.', cancelled: false,
    stopovers: [
      { stop: { id: '900100005', name: 'U Alexanderplatz', location: { latitude: 52.52153, longitude: 13.41123 } }, arrival: iso(T0 + 470), plannedArrival: iso(T0 + 350), departure: iso(T0 + 480), plannedDeparture: iso(T0 + 360) },
      { stop: { id: '900100004', name: 'U Jannowitzbrücke', location: { latitude: 52.5153, longitude: 13.4183 } }, arrival: iso(T0 + 600), plannedArrival: iso(T0 + 480), departure: iso(T0 + 630), plannedDeparture: iso(T0 + 510) },
      { stop: { id: '900078101', name: 'U Hermannplatz', location: { latitude: 52.48693, longitude: 13.42484 } }, arrival: iso(T0 + 1260), plannedArrival: iso(T0 + 1080), departure: iso(T0 + 1290), plannedDeparture: iso(T0 + 1110) },
    ],
    polyline: { type: 'FeatureCollection', features: [
      { type: 'Feature', geometry: { type: 'Point', coordinates: [13.41123, 52.52153] } },
      { type: 'Feature', geometry: { type: 'Point', coordinates: [13.4160, 52.5180] } },
      { type: 'Feature', geometry: { type: 'Point', coordinates: [13.4183, 52.5153] } },
      { type: 'Feature', geometry: { type: 'Point', coordinates: [13.4220, 52.5000] } },
      { type: 'Feature', geometry: { type: 'Point', coordinates: [13.42484, 52.48693] } },
    ] },
  },
};

test('base64url round trip with umlauts, quotes and emoji', () => {
  const text = JSON.stringify({ a: 'Straße "x" ü€😀 /+?' });
  assert.equal(live.fromBase64Url(live.toBase64Url(text)), text);
  assert.match(live.toBase64Url(text), /^[A-Za-z0-9_-]+$/);
});

test('fragment parses and rejects bad links', () => {
  const j = live.parseFragment(fragment(sample));
  assert.equal(j.name, 'Ana');
  assert.equal(j.legs.length, 2);
  assert.equal(j.legs[0].walk, true);
  assert.equal(j.legs[1].provider, 'bvg');
  assert.equal(j.legs[1].from.delay, 2);
  assert.equal(j.legs[1].from.planned, (T0 + 360) * 1000);
  assert.throws(() => live.parseFragment('#v=3&d=x'), /version/);
  assert.throws(() => live.parseFragment('#v=1&d=%%%'), /data/);
  assert.throws(() => live.parseFragment('#v=1&d=' + live.toBase64Url('{"legs":[]}')), /data/);
});

test('states without live data follow the delays known when shared', () => {
  const j = live.parseFragment(fragment(sample));
  const legs = live.computeLegs(j, {});
  assert.equal(legs[1].dep, (T0 + 360 + 120) * 1000);
  assert.equal(legs[1].arr, (T0 + 1080 + 180) * 1000);
  assert.equal(live.tripState(legs, (T0 - 60) * 1000).phase, 'before');
  assert.equal(live.tripState(legs, (T0 + 100) * 1000).phase, 'walk');
  assert.equal(live.tripState(legs, (T0 + 400) * 1000).phase, 'wait');
  assert.equal(live.tripState(legs, (T0 + 700) * 1000).phase, 'ride');
  const arrived = live.tripState(legs, (T0 + 1300) * 1000);
  assert.equal(arrived.phase, 'arrived');
  assert.equal(arrived.delay, 3);
  assert.equal(live.tripState(legs, (T0 + 1260) * 1000 + live.EXPIRY_MS).phase, 'expired');
});

test('BVG live data: position between stops, next stop, delay, polling', () => {
  const j = live.parseFragment(fragment(sample));
  const data = live.parseBvgTrip(bvgTrip);
  assert.equal(data.stops.length, 3);
  assert.equal(data.path.length, 5);
  const legs = live.computeLegs(j, { 1: data });
  assert.equal(legs[1].live, true);
  assert.equal(legs[1].delayArr, 3);
  // Halfway between Jannowitzbrücke (dep +630) and Hermannplatz (arr +1260).
  const s = live.tripState(legs, (T0 + 945) * 1000);
  assert.equal(s.phase, 'ride');
  assert.equal(s.next.name, 'U Hermannplatz');
  assert.ok(s.pos[0] < 52.5153 && s.pos[0] > 52.48693, 'between the stops');
  const T = live.pickTexts('de-DE');
  assert.equal(live.stateLine(T, s), 'Im U8 · nächster Halt U Hermannplatz');
  // Standing at Jannowitzbrücke.
  const at = live.tripState(legs, (T0 + 610) * 1000);
  assert.equal(at.at.name, 'U Jannowitzbrücke');
  assert.equal(live.stateLine(live.pickTexts('en'), at), 'On the U8 · stopping at U Jannowitzbrücke');
  // Polling: only the ride, and only while it is relevant.
  assert.deepEqual(live.legsToPoll(j, legs, (T0 + 600) * 1000), [1]);
  assert.deepEqual(live.legsToPoll(j, legs, (T0 + 4000) * 1000), []);
  assert.equal(live.tripUrl(j.legs[1]), 'https://v6.bvg.transport.rest/trips/1%7C31911%7C21%7C86%7C13112026?polyline=true&stopovers=true&remarks=false');
  assert.equal(live.stateLine(T, live.tripState(legs, (T0 - 60) * 1000)), 'Startet um 14:00 zu Fuß');
  assert.equal(live.stateLine(T, live.tripState(legs, (T0 + 400) * 1000)), 'Steigt um in U Alexanderplatz · U8 um 14:08');
  assert.equal(live.stateLine(T, live.tripState(legs, (T0 + 1300) * 1000)), 'Angekommen um 14:21');
  assert.equal(live.stateLine(T, live.tripState(legs, (T0 + 1300) * 1000 + live.EXPIRY_MS)), 'Fahrt beendet · Link abgelaufen');
});

function encode(points, precision) {
  const factor = Math.pow(10, precision);
  let out = '';
  let pLat = 0;
  let pLon = 0;
  const value = (v) => {
    v = v < 0 ? ~(v << 1) : v << 1;
    while (v >= 0x20) {
      out += String.fromCharCode((0x20 | (v & 0x1f)) + 63);
      v >>= 5;
    }
    out += String.fromCharCode(v + 63);
  };
  for (const [lat, lon] of points) {
    const a = Math.round(lat * factor);
    const o = Math.round(lon * factor);
    value(a - pLat);
    value(o - pLon);
    pLat = a;
    pLon = o;
  }
  return out;
}

test('Transitous live data (MOTIS /api/v6/trip)', () => {
  const trJourney = {
    dest: { name: 'Erkner', lat: 52.42, lon: 13.75 }, t: T0,
    legs: [{ p: 'tr', trip: '20261113_14:05_de-DELFI_123', line: 'RE1', dir: 'Frankfurt (Oder)',
      from: { name: 'Ostkreuz', id: 'de-DELFI_de:11000:900120003', lat: 52.503, lon: 13.469, dep: T0 + 300 },
      to: { name: 'Erkner', id: 'de-DELFI_de:12067:900310001', lat: 52.42, lon: 13.75, arr: T0 + 1500 } }],
  };
  const shape = [[52.52, 13.41], [52.503, 13.469], [52.46, 13.6], [52.42, 13.75]];
  const answer = { legs: [{
    mode: 'REGIONAL_RAIL', routeShortName: 'RE1', headsign: 'Frankfurt (Oder)', realTime: true,
    from: { name: 'Alexanderplatz', stopId: 'de-DELFI_de:11000:900100003', lat: 52.52, lon: 13.41, departure: iso(T0), scheduledDeparture: iso(T0) },
    intermediateStops: [{ name: 'Ostkreuz', stopId: 'de-DELFI_de:11000:900120003', lat: 52.503, lon: 13.469, arrival: iso(T0 + 360), departure: iso(T0 + 420), scheduledArrival: iso(T0 + 240), scheduledDeparture: iso(T0 + 300) }],
    to: { name: 'Erkner', stopId: 'de-DELFI_de:12067:900310001', lat: 52.42, lon: 13.75, arrival: iso(T0 + 1620), scheduledArrival: iso(T0 + 1500) },
    legGeometry: { points: encode(shape, 6), precision: 6 },
  }] };
  const j = live.parseFragment(fragment(trJourney));
  const data = live.parseTransitousTrip(answer);
  assert.equal(data.stops.length, 3);
  assert.equal(data.path.length, 4);
  assert.ok(Math.abs(data.path[2][1] - 13.6) < 1e-6);
  const legs = live.computeLegs(j, { 0: data });
  assert.equal(legs[0].stops.length, 2, 'cut from Ostkreuz to Erkner');
  assert.equal(legs[0].delayDep, 2);
  assert.equal(legs[0].delayArr, 2);
  assert.equal(live.tripUrl(j.legs[0]), 'https://api.transitous.org/api/v6/trip?tripId=20261113_14%3A05_de-DELFI_123&detailedLegs=true');
  const s = live.tripState(legs, (T0 + 1000) * 1000);
  assert.equal(s.phase, 'ride');
  assert.ok(s.pos && s.pos[1] > 13.469 && s.pos[1] < 13.75);
});

test('progress of the whole trip and the marks of the changes', () => {
  const j = live.parseFragment(fragment(sample));
  const legs = live.computeLegs(j, {});
  // Walk 14:00–14:05, U8 14:08–14:21 (with the delays known when shared).
  assert.equal(live.tripProgress(legs, (T0 - 60) * 1000).value, 0);
  assert.equal(live.tripProgress(legs, (T0 + 2000) * 1000).value, 1);
  const half = live.tripProgress(legs, (T0 + 630) * 1000);
  assert.ok(Math.abs(half.value - 0.5) < 1e-9);
  assert.equal(half.marks.length, 1);
  assert.ok(Math.abs(half.marks[0] - 300 / 1260) < 1e-9);
});

test('path split at the vehicle and smooth easing toward a new position', () => {
  const path = [[52.5, 13.4], [52.5, 13.5], [52.6, 13.5]];
  const split = live.splitPathAt(path, [52.5001, 13.45]);
  assert.equal(split.passed.length, 2);
  assert.ok(Math.abs(split.passed[1][1] - 13.45) < 1e-9 && Math.abs(split.passed[1][0] - 52.5) < 1e-9, 'projected onto the way');
  assert.deepEqual(split.ahead.slice(1), [[52.5, 13.5], [52.6, 13.5]]);
  assert.deepEqual(live.splitPathAt(path, null).ahead, path);
  // Easing: part of the way after one frame, nearly there after a few time constants.
  const a = [52.5, 13.4];
  const b = [52.5, 13.401]; // about 68 m east
  const one = live.easeToward(a, b, 16, 700, 1500);
  assert.ok(one[1] > a[1] && one[1] < b[1]);
  const later = live.easeToward(a, b, 5000, 700, 1500);
  assert.ok(Math.abs(later[1] - b[1]) < 1e-5);
  // Far away (a new leg): jump.
  assert.deepEqual(live.easeToward(a, [52.6, 13.6], 16, 700, 1500), [52.6, 13.6]);
  assert.deepEqual(live.easeToward(null, b, 16), b);
  assert.equal(live.easeToward(a, null, 16), null);
});

test('countdown, age and freshness texts', () => {
  const de = live.pickTexts('de');
  const en = live.pickTexts('en-GB');
  const n = 1000000000000;
  assert.equal(live.countdownText(de, n + 23 * 60000, n), 'in 23 min');
  assert.equal(live.countdownText(de, n + 22 * 60000 + 1000, n), 'in 23 min');
  assert.equal(live.countdownText(de, n + 65 * 60000, n), 'in 1 h 05 min');
  assert.equal(live.countdownText(de, n + 20000, n), 'jetzt');
  assert.equal(live.countdownText(de, n - 1, n), '');
  assert.equal(live.ageText(de, 12000), 'vor 12 s');
  assert.equal(live.ageText(de, 3000), 'gerade eben');
  assert.equal(live.ageText(en, 125000), '2 min ago');
  assert.deepEqual(live.freshness(null, null, false, n), { kind: 'plan', age: null });
  assert.equal(live.freshness(null, null, true, n).kind, 'loading');
  assert.equal(live.freshness(null, n - 5, true, n).kind, 'none');
  assert.deepEqual(live.freshness(n - 12000, null, true, n), { kind: 'fresh', age: 12000 });
  // One failed request after a fresh answer is not stale yet.
  assert.equal(live.freshness(n - 30000, n - 5000, true, n).kind, 'fresh');
  const stale = live.freshness(n - 125000, n - 5000, true, n);
  assert.equal(stale.kind, 'stale');
  assert.equal(live.freshnessText(de, stale), 'Keine Live-Daten seit 2 min');
  assert.equal(live.freshnessText(de, live.freshness(n - 12000, null, true, n)), 'Live · vor 12 s');
});

test('polling: 20 s seen, 60 s hidden, longer after failures', () => {
  assert.equal(live.pollDelay(false, 0), 20000);
  assert.equal(live.pollDelay(true, 0), 60000);
  assert.equal(live.pollDelay(false, 1), 40000);
  assert.equal(live.pollDelay(true, 1), 60000);
  assert.equal(live.pollDelay(false, 3), 160000);
  assert.equal(live.pollDelay(false, 10), 300000);
});

test('timeline: only the current ride is open, with its next stops', () => {
  const j = live.parseFragment(fragment(sample));
  const legs = live.computeLegs(j, { 1: live.parseBvgTrip(bvgTrip) });
  const now = (T0 + 945) * 1000;
  const model = live.timelineModel(legs, live.tripState(legs, now), now, 3);
  assert.deepEqual(model.map((m) => m.status), ['done', 'current']);
  const ride = model[1];
  assert.equal(ride.line, 'U8');
  assert.equal(ride.color, '#013F75');
  assert.equal(ride.passed, 1, 'Jannowitzbrücke passed');
  assert.deepEqual(ride.preview, []);
  assert.equal(ride.to.next, true, 'Hermannplatz is the next stop');
  // Standing at Jannowitzbrücke.
  const at = (T0 + 610) * 1000;
  const m2 = live.timelineModel(legs, live.tripState(legs, at), at, 3)[1];
  assert.equal(m2.stops[0].status, 'at');
  assert.equal(m2.preview[0].name, 'U Jannowitzbrücke');
  assert.equal(m2.passed, 0);
  // Before the start nothing is done, the walk is current.
  const before = (T0 - 60) * 1000;
  assert.deepEqual(live.timelineModel(legs, live.tripState(legs, before), before).map((m) => m.status), ['current', 'upcoming']);
  // Arrived: everything done.
  const after = (T0 + 1300) * 1000;
  assert.deepEqual(live.timelineModel(legs, live.tripState(legs, after), after).map((m) => m.status), ['done', 'done']);
});

test('line colours of the app and readable ink', () => {
  assert.equal(live.lineColor('U8'), '#013F75');
  assert.equal(live.lineColor('S41', 'suburban'), '#AD5937');
  assert.equal(live.lineColor('M10'), '#BE1414');
  assert.equal(live.lineColor('M41'), '#95276E');
  assert.equal(live.lineColor('N60'), '#95276E');
  assert.equal(live.lineColor('RE1'), '#DA251D');
  assert.equal(live.lineColor('ICE 1234'), '#EC0016');
  assert.equal(live.lineColor('42', null, '#00ff00'), '#00ff00');
  assert.equal(live.lineColor('42', 'bus', 'red'), '#95276E', 'only hex colours of the feed');
  assert.equal(live.lineColor('?'), '#5A6072');
  assert.equal(live.inkOn('#F7EC6E'), '#16130A');
  assert.equal(live.inkOn('#224F86'), '#ffffff');
  // The ink of the BVG's U-Bahn signs: white, black on the U4.
  assert.equal(live.inkOn('#FFC101'), '#000000');
  assert.equal(live.inkOn('#f96815'), '#ffffff', 'white on the U9');
  for (const u of ['U1', 'U2', 'U3', 'U5', 'U6', 'U7', 'U8', 'U9']) assert.equal(live.inkOn(live.lineColor(u)), '#ffffff', u);
});

test('the demo trip runs through every state without network', async () => {
  global.window = {};
  require('../live/demo.js');
  const states = new Map();
  for (const at of [-5, 3, 7, 10, 20, 30, 43.5, 50, 70, 200]) {
    const demo = global.window.NextStopDemo.create({ toBase64Url: live.toBase64Url, hash: '#demo&at=' + at });
    const j = live.parseFragment(demo.hash);
    assert.equal(j.name, 'Ana');
    assert.equal(j.legs.length, 6);
    const data = {};
    for (const [i, leg] of j.legs.entries()) {
      if (leg.walk) continue;
      const response = await demo.fetch(live.tripUrl(leg));
      assert.equal(response.ok, true);
      data[i] = live.parseBvgTrip(await response.json());
    }
    const legs = live.computeLegs(j, data);
    assert.ok(legs.filter((l) => !l.walk).every((l) => l.live), 'every ride matched its live stops');
    const state = live.tripState(legs, demo.now());
    states.set(at, state.phase);
    live.stateLine(live.pickTexts('de'), state);
    live.timelineModel(legs, state, demo.now());
  }
  assert.deepEqual([...states.values()], ['before', 'walk', 'wait', 'ride', 'walk', 'ride', 'wait', 'ride', 'arrived', 'expired']);
  const noName = global.window.NextStopDemo.create({ toBase64Url: live.toBase64Url, hash: '#demo&name=' });
  assert.equal(live.parseFragment(noName.hash).name, null);
  delete global.window;
});

test('the rides of the demo follow dense ways that pass every stop', async () => {
  global.window = {};
  delete require.cache[require.resolve('../live/demo.js')];
  require('../live/demo.js');
  const demo = global.window.NextStopDemo.create({ toBase64Url: live.toBase64Url, hash: '#demo&at=10' });
  const j = live.parseFragment(demo.hash);
  const metres = (a, b) => Math.hypot((a[0] - b[0]) * 111320, (a[1] - b[1]) * 111320 * Math.cos((a[0] * Math.PI) / 180));
  for (const leg of j.legs.filter((l) => !l.walk)) {
    const body = await (await demo.fetch(live.tripUrl(leg))).json();
    const way = body.trip.polyline.features.map((f) => [f.geometry.coordinates[1], f.geometry.coordinates[0]]);
    // No straight line across the fields: a point at least every 50 m.
    for (let i = 1; i < way.length; i++) assert.ok(metres(way[i - 1], way[i]) <= 50, 'gap in the way of ' + leg.line);
    for (const stop of body.trip.stopovers) {
      const at = [stop.stop.location.latitude, stop.stop.location.longitude];
      assert.ok(Math.min(...way.map((p) => metres(p, at))) < 25, stop.stop.name + ' lies on the way of ' + leg.line);
    }
  }
  delete global.window;
});

/* ---------- Link format v2 ---------- */

const fixturePath = (name) => path.join(__dirname, 'fixtures', name);
const V2_FIXTURE = fs.readFileSync(fixturePath('live-v2.json'), 'utf8').trim();
const V2_FROM_JAVA = fs.readFileSync(fixturePath('live-v2.deflate.txt'), 'utf8').trim();
const V1_13_LEGS = JSON.parse(fs.readFileSync(fixturePath('live-v1-13-legs.json'), 'utf8'));
const deflated = (json) => zlib.deflateRawSync(Buffer.from(json, 'utf8'), { level: 9 }).toString('base64url');
const fragment2 = (json, lang) => '#v=2' + (lang ? '&l=' + lang : '') + '&d=' + deflated(json);

const V2_T0 = 1794640320; // the first departure of live-v2.json, epoch seconds
// The input of live-v2.json, as in LiveLinkTest.kt: a walk from an address, the S5, a change
// between two ids of Ostkreuz, the RE7 and a walk home.
const V2_INPUT = {
  n: 'Ana',
  t: V2_T0 - 600,
  dest: { name: 'Zuhause', lat: 52.366, lon: 13.51 },
  legs: [
    { walk: true, from: { name: 'Startstraße 1', lat: 52.52, lon: 13.405, dep: V2_T0 }, to: { name: 'S+U Alexanderplatz', id: '900100003', lat: 52.52151, lon: 13.41127, arr: V2_T0 + 240 } },
    { p: 'bvg', trip: '1|31911|21|86|14112026', line: 'S5', dir: 'Strausberg Nord',
      from: { name: 'S+U Alexanderplatz', id: '900100003', lat: 52.52151, lon: 13.41127, dep: V2_T0 + 300, dd: 2 },
      to: { name: 'S Ostkreuz', id: '900120003', lat: 52.50303, lon: 13.46902, arr: V2_T0 + 840, ad: 3 } },
    { walk: true, from: { name: 'S Ostkreuz', id: '900120003', lat: 52.50303, lon: 13.46902, dep: V2_T0 + 840 }, to: { name: 'S Ostkreuz', id: 'de-DELFI_000:11', lat: 52.50281, lon: 13.46985, arr: V2_T0 + 1020 } },
    { p: 'tr', trip: 'de-DELFI_1234#5', line: 'RE7', dir: 'Frankfurt (Oder) "Bhf" ü€😀',
      from: { name: 'S Ostkreuz', id: 'de-DELFI_000:11', lat: 52.50281, lon: 13.46985, dep: V2_T0 + 1200, dd: -1 },
      to: { name: 'Flughafen BER', id: 'de-DELFI_000:22', lat: 52.36443, lon: 13.5097, arr: V2_T0 + 2280 } },
    { walk: true, from: { name: 'Flughafen BER', id: 'de-DELFI_000:22', lat: 52.36443, lon: 13.5097, dep: V2_T0 + 2280 }, to: { name: 'Zuhause', lat: 52.366, lon: 13.51, arr: V2_T0 + 2700 } },
  ],
};

/** Checks [j], the journey of live-v2.json, as the page reads it. */
function checkV2Journey(j) {
  assert.equal(j.name, 'Ana');
  assert.equal(j.created, (V2_T0 - 600) * 1000);
  assert.equal(j.legs.length, 5);
  assert.deepEqual(j.legs.map((l) => l.walk), [true, false, true, false, true]);
  // The start is not in the link: the first walk starts at the stop it leads to.
  assert.equal(j.legs[0].from.name, 'S+U Alexanderplatz');
  assert.deepEqual(j.legs[0].from.pos, [52.5215, 13.4113]);
  assert.equal(j.legs[0].from.planned, V2_T0 * 1000);
  assert.equal(j.legs[0].to.planned, (V2_T0 + 240) * 1000);
  assert.equal(j.legs[1].provider, 'bvg');
  assert.equal(j.legs[1].trip, '1|31911|21|86|14112026');
  assert.equal(j.legs[1].line, 'S5');
  assert.equal(j.legs[1].from.delay, 2);
  assert.equal(j.legs[1].to.delay, 3);
  assert.equal(j.legs[1].to.id, '900120003');
  // The change walk runs from where the S5 ends to where the RE7 starts.
  assert.equal(j.legs[2].from.id, '900120003');
  assert.equal(j.legs[2].to.id, 'de-DELFI_000:11');
  assert.equal(j.legs[2].from.planned, (V2_T0 + 840) * 1000);
  assert.equal(j.legs[2].to.planned, (V2_T0 + 1020) * 1000);
  assert.equal(j.legs[3].direction, 'Frankfurt (Oder) "Bhf" ü€😀');
  assert.equal(j.legs[3].provider, 'tr');
  assert.equal(j.legs[3].from.delay, -1);
  assert.equal(j.legs[4].from.name, 'Flughafen BER');
  assert.equal(j.legs[4].to.name, 'Zuhause');
  assert.deepEqual(j.legs[4].to.pos, [52.366, 13.51]);
  assert.equal(j.legs[4].to.planned, (V2_T0 + 2700) * 1000);
  assert.deepEqual(j.dest, { name: 'Zuhause', pos: [52.366, 13.51], kind: 'h' });
}

test('v2: the JS encoder writes exactly the JSON of the app (live-v2.json)', () => {
  assert.equal(live.encodeLive(V2_INPUT, { k: 'h' }), V2_FIXTURE);
});

test('v2: the fixture deflated by Java (as the app does it) reads back', async () => {
  const j = await live.readFragment('#v=2&l=de&d=' + V2_FROM_JAVA);
  checkV2Journey(j);
  assert.equal(j.lang, 'de');
  // The same with node's own deflate.
  checkV2Journey(await live.readFragment(fragment2(V2_FIXTURE)));
});

test('v2: links of format v1 still read, newer or broken ones do not', async () => {
  const j = await live.readFragment(fragment(sample));
  assert.equal(j.legs.length, 2);
  assert.equal(j.legs[1].from.delay, 2);
  assert.equal(j.lang, null);
  await assert.rejects(live.readFragment('#v=3&d=x'), /version/);
  await assert.rejects(live.readFragment('#v=2&d='), /data/);
  await assert.rejects(live.readFragment('#v=2&d=' + live.toBase64Url('not deflate data at all')), /data/);
  await assert.rejects(live.readFragment(fragment2('{"b":1,"s":[],"r":[]}')), /data/);
  await assert.rejects(live.readFragment(fragment2('{"b":1,"s":[["A"]],"r":[{"f":0,"t":7,"d":0,"a":60}]}')), /data/);
  await assert.rejects(live.readFragment(fragment2('[1,2')), /data/);
});

test('v2: a browser without DecompressionStream gets its own message', async () => {
  const saved = globalThis.DecompressionStream;
  delete globalThis.DecompressionStream;
  try {
    await assert.rejects(live.readFragment(fragment2(V2_FIXTURE)), /unsupported/);
    // v1 needs no unpacking.
    assert.equal((await live.readFragment(fragment(sample))).legs.length, 2);
  } finally {
    globalThis.DecompressionStream = saved;
  }
});

test('v2: walks that are no change between two rides stay legs of their own', async () => {
  const raw = {
    t: T0,
    dest: { name: 'Hermannplatz', lat: 52.48693, lon: 13.42484 },
    legs: [
      { walk: true, from: { name: 'Startstraße 1', lat: 52.52, lon: 13.405, dep: T0 }, to: { name: 'Park', lat: 52.51, lon: 13.41, arr: T0 + 600 } },
      { walk: true, from: { name: 'Park', lat: 52.51, lon: 13.41, dep: T0 + 600 }, to: { name: 'Hermannplatz', lat: 52.48693, lon: 13.42484, arr: T0 + 1500 } },
    ],
  };
  const json = live.encodeLive(raw);
  assert.doesNotMatch(json, /Startstra/, 'the start of the trip is not in the link');
  assert.doesNotMatch(json, /52\.52,13\.405/);
  const j = await live.readFragment(fragment2(json));
  assert.equal(j.legs.length, 2);
  assert.ok(j.legs.every((l) => l.walk));
  assert.equal(j.legs[0].from.name, 'Park');
  assert.equal(j.legs[1].to.planned, (T0 + 1500) * 1000);
  assert.equal(j.name, null);
});

test('v2: a start that is a stop gives the first walk its name, still without its position', async () => {
  const json = live.encodeLive(V2_INPUT, { o: 'S Hackescher Markt' });
  const j = await live.readFragment(fragment2(json));
  assert.equal(j.legs[0].from.name, 'S Hackescher Markt');
  assert.deepEqual(j.legs[0].from.pos, [52.5215, 13.4113]);
  assert.equal(j.dest.kind, null);
});

test('v2: a trip of 13 legs from Bonn to Berlin fits in a short link', async (t) => {
  const site = 'https://nextstop.bapo.me/live/';
  const v1 = site + fragment(V1_13_LEGS);
  const v2 = site + fragment2(live.encodeLive(V1_13_LEGS), 'de');
  t.diagnostic('13 legs: v1 ' + v1.length + ' characters, v2 ' + v2.length);
  assert.ok(v1.length > 4096);
  assert.ok(v2.length < 1500, 'v2 is ' + v2.length + ' characters');
  const before = live.parseFragment(fragment(V1_13_LEGS));
  const after = await live.readFragment(v2.slice(v2.indexOf('#')));
  assert.equal(after.legs.length, 13);
  before.legs.forEach((a, i) => {
    const b = after.legs[i];
    assert.equal(b.walk, a.walk, 'leg ' + i);
    assert.equal(b.line, a.line);
    assert.equal(b.trip, a.trip);
    assert.equal(b.provider, a.provider);
    assert.equal(b.direction, a.direction);
    assert.equal(b.from.planned, a.from.planned, 'departure of leg ' + i);
    assert.equal(b.to.planned, a.to.planned, 'arrival of leg ' + i);
    if (!a.walk) {
      assert.equal(b.from.delay, a.from.delay);
      assert.equal(b.to.delay, a.to.delay);
      assert.equal(b.from.id, a.from.id);
      assert.equal(b.to.name, a.to.name);
      assert.ok(live.distance(b.from.pos, a.from.pos) < 10, 'positions to about 10 m');
    }
  });
  // Only the start of the trip is left out.
  assert.notEqual(after.legs[0].from.name, before.legs[0].from.name);
  assert.equal(after.dest.name, before.dest.name);
  // The states follow the same times.
  const now = (V1_13_LEGS.legs[5].from.dep + 600) * 1000;
  assert.equal(live.tripState(live.computeLegs(after, {}), now).phase, live.tripState(live.computeLegs(before, {}), now).phase);
});

test('page language: the recipient\'s German or English, else the sender\'s', () => {
  assert.equal(live.pickTexts('de-AT', 'en').lang, 'de');
  assert.equal(live.pickTexts('en-GB', 'de').lang, 'en');
  assert.equal(live.pickTexts('fr-FR', 'de').lang, 'de');
  assert.equal(live.pickTexts('fr-FR', 'en').lang, 'en');
  assert.equal(live.pickTexts('fr-FR', null).lang, 'en');
  assert.equal(live.pickTexts('pl', 'it').lang, 'en');
});

test('headline: a saved home or work goes by "nach Hause", not by its address', () => {
  const de = live.pickTexts('de');
  const en = live.pickTexts('en');
  const home = { name: 'Zuhause', kind: 'h' };
  assert.equal(live.headline(de, 'ride', 'Ana', home), 'Ana ist auf dem Weg nach Hause');
  assert.equal(live.headline(de, 'arrived', null, home), 'Zu Hause angekommen');
  assert.equal(live.headline(en, 'walk', 'Ana', home), 'Ana is on the way home');
  assert.equal(live.headline(de, 'before', 'Ana', { name: 'Arbeit', kind: 'w' }), 'Ana ist auf dem Weg zur Arbeit');
  assert.equal(live.headline(en, 'arrived', 'Ana', { name: 'Work', kind: 'w' }), 'Ana has arrived at work');
  assert.equal(live.headline(de, 'ride', 'Ana', { name: 'Hermannplatz', kind: null }), 'Ana ist unterwegs nach Hermannplatz');
  assert.equal(live.headline(de, 'expired', 'Ana', home), 'Fahrt beendet');
});

const fromKotlin = process.argv[2];
if (fromKotlin) {
  test('a fragment made by the app (Kotlin deflateRaw + base64Url of live-v2.json) decodes', async () => {
    checkV2Journey(await live.readFragment('#v=2&d=' + fromKotlin));
  });
}

test('changing numbers roll up while they count down and down when they grow', () => {
  assert.equal(live.textNumber('in 23 min'), 23);
  assert.equal(live.textNumber('14:05'), 14 * 60 + 5);
  assert.equal(live.textNumber('in 1 h 05 min'), 65);
  assert.equal(live.textNumber('jetzt'), null);
  assert.equal(live.rollsUp('in 5 min', 'in 4 min'), true);
  assert.equal(live.rollsUp('14:05', '14:07'), false);
  assert.equal(live.rollsUp('in 1 h 01 min', 'in 59 min'), true);
  assert.equal(live.rollsUp('in 1 min', 'jetzt'), true);
});

test('the route draws itself in like a snake, from its start to its end', () => {
  const line = (coords) => ({ type: 'Feature', properties: {}, geometry: { type: 'LineString', coordinates: coords } });
  const a = line([[13.40, 52.50], [13.41, 52.50]]);
  const b = line([[13.41, 52.50], [13.41, 52.51], [13.42, 52.51]]);
  const whole = live.lineLength(a.geometry.coordinates) + live.lineLength(b.geometry.coordinates);
  assert.deepEqual(live.snakeFeatures([a, b], 0), []);
  assert.equal(live.snakeFeatures([a, b], 1).length, 2);
  const half = live.snakeFeatures([a, b], 0.5);
  const shown = half.reduce((sum, f) => sum + live.lineLength(f.geometry.coordinates), 0);
  assert.ok(Math.abs(shown - whole / 2) < 1, 'half of the length is drawn');
  assert.equal(half[0], a, 'the first line is whole');
  // A little of the first line only: one cut piece of it.
  const start = live.snakeFeatures([a, b], 0.05);
  assert.equal(start.length, 1);
  assert.equal(start[0].geometry.coordinates.length, 2);
  assert.ok(start[0].geometry.coordinates[1][0] < 13.41);
  assert.equal(live.easeOutCubic(0), 0);
  assert.equal(live.easeOutCubic(1), 1);
  assert.equal(live.easeOutCubic(2), 1);
});

test('the confetti of an arrival shoots up, falls and fades', () => {
  const pieces = live.confettiPieces(live.CONFETTI_COUNT, ['#ff0000', '#00ff00'], 7);
  assert.equal(pieces.length, live.CONFETTI_COUNT);
  assert.deepEqual(live.confettiPieces(5, ['#ff0000'], 7), live.confettiPieces(5, ['#ff0000'], 7));
  for (const piece of pieces) {
    assert.ok(piece.vy < 0, 'every piece starts upwards');
    assert.ok(['#ff0000', '#00ff00'].includes(piece.color));
    assert.ok(live.confettiAt(piece, 0.15).y < 0);
    assert.ok(live.confettiAt(piece, live.CONFETTI_SECONDS).y > 0, 'and has fallen below its start');
  }
  assert.ok(Math.abs(live.confettiAt(pieces[0], 0).x) < 1e-9);
  assert.equal(live.confettiAt(pieces[0], 0.5).alpha, 1);
  assert.equal(live.confettiAt(pieces[0], live.CONFETTI_SECONDS).alpha, 0);
});

test('line colours by the ride: product and place, the feed colour first', () => {
  const core = require('../shared/core.js');
  const berlin = [52.52, 13.405];
  const bohemia = [50.46, 13.42];
  // A regional train named U1 in Bohemia is no Berlin U1.
  assert.equal(live.lineColor('U1', 'regional', null, bohemia), '#DA251D');
  assert.equal(live.lineColor('U1', 'regional', null, berlin), '#DA251D');
  assert.equal(live.lineColor('U1', null, null, bohemia), '#5A6072');
  assert.equal(live.lineColor('U1', 'subway', null, berlin), '#419328');
  // A Berlin U-Bahn always has the colour of the BVG's sign, whatever its feed sends.
  assert.equal(live.lineColor('U9', 'subway', '#F3791D', berlin), '#F96815');
  assert.equal(live.lineColor('U1', 'subway', '#123456', [53.55, 10.0]), '#123456', 'not a U1 of another city');
  assert.equal(live.lineColor('S41', 'suburban', '#123456', berlin), '#123456', 'not an S-Bahn');
  assert.equal(live.lineColor('U1', 'subway', null, [53.55, 10.0]), '#115D91', 'a U1 of another city');
  assert.equal(live.guessProduct('U1', bohemia), null);
  assert.equal(live.guessProduct('U1', berlin), 'subway');
  // The colour of the feed for this very ride comes first, wherever the page is.
  assert.equal(live.lineColor('585', 'bus', '#00A650', bohemia), '#00A650');
  assert.equal(live.lineColor('585', 'bus', null, bohemia), '#95276E');
  // Coaches are coaches.
  assert.equal(core.motisProduct('COACH'), 'coach');
  assert.equal(live.lineColor('FlixBus 040', 'coach', null, berlin), '#97D700');
  assert.equal(live.lineColor('X 12', 'coach', null, berlin), '#2F6F73');
  // Train kinds in capitals; others as they are.
  assert.equal(core.canonicalLine('rj 171', 'express'), 'RJ 171');
  assert.equal(core.canonicalLine('rj 171', null), 'RJ 171');
  assert.equal(core.canonicalLine('Os 6123', 'regional'), 'Os 6123');
  assert.equal(core.canonicalLine('Bus 585', 'bus'), 'Bus 585');
  assert.equal(core.canonicalLine('S 1', 'suburban'), 'S 1');
  assert.equal(core.inVbbArea(berlin), true);
  assert.equal(core.inVbbArea(bohemia), false);
});

// A ring line (S41/S42) whose run covers more than one lap calls at the same stations twice: the
// ride is matched to the calls of its own times and its way cut on the pass of that lap.
test('ring line: the ride on its own lap, its way and its vehicle', () => {
  const core = require('../shared/core.js');
  const names = ['Westkreuz', 'Halensee', 'Tempelhof', 'Südkreuz', 'Ostkreuz', 'Gesundbrunnen', 'Beusselstraße'];
  const centre = [52.5, 13.4];
  const r = 0.05;
  const lonScale = 1 / Math.cos((centre[0] * Math.PI) / 180);
  const angle = (k) => (k * 2 * Math.PI) / names.length;
  const onCircle = (a, f) => [centre[0] + r * f * Math.sin(a), centre[1] + r * f * lonScale * Math.cos(a)];
  const station = (k) => onCircle(angle(k % names.length), 0.995);
  const start = Date.UTC(2026, 2, 2, 11, 0);
  const delay = 3 * 60000;
  const hops = 10;
  const per = 24;
  const stops = [];
  for (let k = 0; k <= hops; k++) {
    const planned = start + k * 8 * 60000;
    stops.push({ name: names[k % names.length], id: 'id' + (k % names.length), pos: station(k),
      pdep: planned, parr: planned, dep: planned + delay, arr: planned + delay });
  }
  // The second lap's way runs a few metres nearer to the stations, so the nearest point of all lies a lap ahead.
  const path = [];
  for (let h = 0; h < hops; h++) {
    for (let s = 0; s < per; s++) path.push(onCircle(angle(h) + (angle(1) * s) / per, h < names.length ? 1 : 0.9985));
  }
  path.push(onCircle(angle(hops), 0.9985));
  assert.ok(core.nearestIndex(path, station(1), 0) < names.length * per, 'first pass');
  const place = (k) => ({ name: names[k % names.length], id: 'id' + (k % names.length), pos: station(k),
    planned: start + k * 8 * 60000, delay: 3 });
  // By id and by name alike: the second call at Halensee, then Südkreuz after it.
  assert.equal(core.matchStop(stops, place(8), null, 'dep'), 8);
  assert.equal(core.matchStop(stops, Object.assign(place(8), { id: null }), null, 'dep'), 8);
  assert.equal(core.matchStop(stops, place(10), 8, 'arr'), 10);
  assert.equal(core.matchStop(stops, place(3), 1, 'arr'), 3);
  // Without a key as before: the first call.
  assert.equal(core.matchStop(stops, place(8), null), 1);
  const journey = { legs: [{ walk: false, line: 'S42', from: place(8), to: place(10) }] };
  const legs = live.computeLegs(journey, { 0: { stops, path, line: 'S42', product: 'suburban' } });
  const leg = legs[0];
  assert.equal(leg.stops.length, 3);
  assert.deepEqual(leg.stops.map((s) => s.name), ['Halensee', 'Tempelhof', 'Südkreuz']);
  assert.equal(leg.dep, start + 64 * 60000 + delay);
  // The way covers Halensee, Tempelhof and Südkreuz only: never the far side of the ring.
  assert.equal(leg.path.length, 2 * per + 1);
  leg.path.forEach((p) => assert.ok(live.distance(p, station(6)) > 3000 && live.distance(p, station(4)) > 2000));
  assert.ok(leg.path.some((p) => live.distance(p, station(2)) < 60));
  // Half way from Halensee to Tempelhof by the times: there, on the second lap's tracks.
  const at = live.ridePosition(leg, start + 68 * 60000 + delay);
  assert.ok(live.distance(at.pos, onCircle((angle(1) + angle(2)) / 2, 0.9985)) < 50);
  // The plain cut of the old way finds the stretch too.
  const cut = core.cutPathBetween(path, stops, 8, 10);
  assert.equal(cut.length, 2 * per + 1);
});

test('look of the app: minutes as "19′" and "1:20 h", the counter of the hero', () => {
  const c = require('../shared/core.js');
  assert.equal(c.minutesMark(5), '5′');
  assert.equal(c.minutesMark(59.4), '59′');
  assert.equal(c.minutesMark(60), '1:00 h');
  assert.equal(c.minutesMark(80), '1:20 h');
  assert.equal(c.minutesMark(-3), '0′');
  assert.equal(c.minutesMark(null), null);
  const t = 10 * 3600000;
  assert.equal(live.counterText(t, t - 19 * 60000 + 1000, 'jetzt'), '19′'); // rounded up, like a departure board
  assert.equal(live.counterText(t, t - 30000, 'jetzt'), 'jetzt');
  assert.equal(live.counterText(t, t - 65 * 60000, 'jetzt'), '1:05 h');
  assert.equal(live.counterText(t, t, 'jetzt'), '');
});

test('look of the app: the bar of the trip in pieces by leg', () => {
  const segs = live.progressSegments([
    { dep: 0, arr: 100, walk: true },
    { dep: 100, arr: 600, color: '#EB7405' },
    { dep: 700, arr: 1000, color: '#992746' },
  ]);
  assert.equal(segs.length, 3);
  assert.deepEqual(segs.map((s) => s.walk), [true, false, false]);
  assert.equal(segs[0].left, 0);
  assert.ok(Math.abs(segs[1].width - 0.5) < 1e-9);
  assert.ok(Math.abs(segs[2].left - 0.7) < 1e-9, 'the wait before the S9 belongs to no piece');
  assert.equal(segs[2].color, '#992746');
  assert.equal(segs[0].color, null);
  // A leg of no length still shows as a sliver, never past the end.
  const zero = live.progressSegments([{ dep: 0, arr: 0, color: '#000' }, { dep: 0, arr: 1000, color: '#111' }]);
  assert.ok(zero[0].width > 0);
  assert.ok(zero[1].left + zero[1].width <= 1);
  assert.deepEqual(live.progressSegments([]), []);
});

test('look of the app: the swipe sheet rests open or collapsed', () => {
  assert.equal(live.sheetRest(100, 0, 500), 0, 'near the top: open');
  assert.equal(live.sheetRest(400, 0, 500), 500, 'near the bottom: collapsed');
  assert.equal(live.sheetRest(100, 0.8, 500), 500, 'a flick down collapses');
  assert.equal(live.sheetRest(450, -0.8, 500), 0, 'a flick up opens');
  assert.equal(live.sheetRest(10, 0, 0), 0, 'nothing to collapse');
  assert.equal(live.sheetClamp(250, 500), 250);
  assert.equal(live.sheetClamp(-30, 500), -10, 'a soft give above the top');
  assert.equal(live.sheetClamp(530, 500), 510, 'and below the bottom');
});

test('#262: the head of the open sheet pulls it down, a push up on the hero scrolls', () => {
  assert.equal(live.sheetDragMode('grabber', true, 40, 0), 'sheet', 'the handle row always moves the sheet');
  assert.equal(live.sheetDragMode('grabber', true, -40, 300), 'sheet');
  assert.equal(live.sheetDragMode('hero', false, -40, 0), 'sheet', 'collapsed: the hero opens it');
  assert.equal(live.sheetDragMode('hero', false, 40, 0), 'sheet');
  assert.equal(live.sheetDragMode('hero', true, 40, 0), 'sheet', 'open at the top: pulled down, it closes');
  assert.equal(live.sheetDragMode('hero', true, -40, 0), 'scroll', 'open: pushed up, the content scrolls');
  assert.equal(live.sheetDragMode('hero', true, 40, 12), 'scroll', 'content scrolled: first back to the top');
});

test('hero: what comes next, with counter, time, delay and platform', () => {
  const j = live.parseFragment(fragment(sample));
  const withPlatforms = JSON.parse(JSON.stringify(bvgTrip));
  withPlatforms.trip.stopovers[0].departurePlatform = '1';
  withPlatforms.trip.stopovers[2].arrivalPlatform = '2';
  const legs = live.computeLegs(j, { 1: live.parseBvgTrip(withPlatforms) });
  assert.equal(legs[1].depPlatform, '1');
  assert.equal(legs[1].arrPlatform, '2');
  const T = live.pickTexts('de');
  const at = (s) => live.heroModel(T, live.tripState(legs, (T0 + s) * 1000), (T0 + s) * 1000);
  const before = at(-60);
  assert.equal(before.kind, 'before');
  assert.equal(before.title, 'Los geht\'s zu Fuß');
  assert.equal(before.counter, '1′');
  const walk = at(100);
  assert.equal(walk.kind, 'walk');
  assert.equal(walk.title, 'Zu Fuß zu U Alexanderplatz');
  const board = at(400);
  assert.equal(board.kind, 'board');
  assert.equal(board.title, 'Einsteigen in U8 · U Alexanderplatz');
  assert.equal(board.platform, 'Gleis 1');
  assert.equal(board.delay, 2);
  const ride = at(945);
  assert.equal(ride.kind, 'off');
  assert.equal(ride.title, 'Aussteigen in U Hermannplatz');
  assert.equal(ride.counter, '6′'); // arrives at +1260
  assert.equal(ride.time, (T0 + 1260) * 1000);
  assert.equal(ride.delay, 3);
  assert.equal(ride.platform, 'Gleis 2');
  assert.equal(live.heroModel(live.pickTexts('en'), live.tripState(legs, (T0 + 945) * 1000), (T0 + 945) * 1000).title, 'Get off at U Hermannplatz');
  const arrived = at(1300);
  assert.equal(arrived.kind, 'arrived');
  assert.equal(arrived.counter, '');
  const expired = live.heroModel(T, live.tripState(legs, (T0 + 1300) * 1000 + live.EXPIRY_MS), (T0 + 1300) * 1000 + live.EXPIRY_MS);
  assert.equal(expired.kind, 'expired');
  assert.equal(expired.title, 'Fahrt beendet');
  // Without live data no platform is made up.
  const plain = live.computeLegs(j, {});
  assert.equal(live.heroModel(T, live.tripState(plain, (T0 + 700) * 1000), (T0 + 700) * 1000).platform, null);
});

test('platforms: "Gleis" for rail, "Steig" for bus, labels as they are', () => {
  const T = live.pickTexts('de');
  assert.equal(live.platformLabel(T, '3', 'S9', [52.5, 13.4]), 'Gleis 3');
  assert.equal(live.platformLabel(T, '3', 'M10', [52.5, 13.4]), 'Steig 3');
  assert.equal(live.platformLabel(T, 'Pos. 11', 'RE7', [52.5, 13.4]), 'Pos. 11');
  assert.equal(live.platformLabel(T, null, 'S9', null), null);
});

test('the app: opens by an intent link, else its store page', () => {
  assert.equal(live.PLAY_URL, 'https://play.google.com/store/apps/details?id=io.github.jnbp.nextstop');
  const url = live.appIntentUrl('', null);
  assert.match(url, /^intent:\/\/open#Intent;scheme=nextstop;package=io\.github\.jnbp\.nextstop;S\.browser_fallback_url=https%3A%2F%2Fplay\.google\.com%2F/);
  assert.match(url, /;end$/);
  assert.equal(live.appIntentUrl('route', 'toName=X', null), 'intent://open/route?toName=X#Intent;scheme=nextstop;package=io.github.jnbp.nextstop;end');
});

test('the hero is the arrival (#218): counter, "Ankunft", delay colour and what the traveller does', () => {
  const j = live.parseFragment(fragment(sample));
  const legs = live.computeLegs(j, { 1: live.parseBvgTrip(bvgTrip) });
  const T = live.pickTexts('de');
  const at = (s, showing) => live.arrivalHero(T, live.tripState(legs, (T0 + s) * 1000), legs, (T0 + s) * 1000, showing);
  // Arrival at +1260 (14:21, 3 min late by live data).
  const before = at(-60);
  assert.equal(before.kind, 'way');
  assert.equal(before.counter, '22′');
  assert.equal(before.title, 'Ankunft 14:21');
  assert.equal(before.level, 'late');
  assert.equal(before.delay, 3);
  assert.equal(before.doing, 'noch nicht losgefahren');
  assert.equal(at(100).doing, 'gerade: zu Fuß unterwegs');
  assert.equal(at(400).doing, 'gerade: an der Haltestelle U Alexanderplatz');
  assert.equal(at(945).doing, 'gerade: in der U8');
  assert.equal(at(945).counter, '6′');
  assert.equal(at(1240).counter, 'jetzt');
  const arrived = at(1300);
  assert.equal(arrived.kind, 'arrived');
  assert.equal(arrived.counter, '');
  assert.equal(arrived.doing, '');
  assert.equal(live.arrivalHero(T, live.tripState(legs, (T0 + 1300) * 1000 + live.EXPIRY_MS), legs, (T0 + 1300) * 1000 + live.EXPIRY_MS).kind, 'expired');
  // From an hour on the clock time, with a minute of hysteresis.
  const early = at(-3600);
  assert.equal(early.counter, '14:21');
  assert.equal(early.clock, true);
  assert.equal(at(1260 - 59 * 60, true).counter, '14:21');
  assert.equal(at(1260 - 59 * 60, false).counter, '59′');
  assert.equal(live.heroShowsClock(60, false), true);
  assert.equal(live.heroShowsClock(59, true), true);
  assert.equal(live.heroShowsClock(58, true), false);
  // Without any real-time data the time is plain; with the delays known when shared it is coloured.
  const plain = live.parseFragment(fragment(Object.assign({}, sample, { legs: [sample.legs[0],
    Object.assign({}, sample.legs[1], { from: Object.assign({}, sample.legs[1].from, { dd: 0 }), to: Object.assign({}, sample.legs[1].to, { ad: 0 }) })] })));
  const plainLegs = live.computeLegs(plain, {});
  assert.equal(live.arrivalHero(T, live.tripState(plainLegs, (T0 + 700) * 1000), plainLegs, (T0 + 700) * 1000).level, 'plan');
  const sharedLegs = live.computeLegs(j, {});
  assert.equal(live.arrivalHero(T, live.tripState(sharedLegs, (T0 + 700) * 1000), sharedLegs, (T0 + 700) * 1000).level, 'late');
  // The next step moves into the line under the hero.
  const step = live.heroModel(T, live.tripState(legs, (T0 + 945) * 1000), (T0 + 945) * 1000);
  assert.equal(live.nextStepLine(T, step), 'Als Nächstes: Aussteigen in U Hermannplatz · 14:21');
  assert.equal(live.nextStepLine(T, live.heroModel(T, live.tripState(legs, (T0 + 1300) * 1000), (T0 + 1300) * 1000)), '');
  assert.equal(live.arrivalHero(live.pickTexts('en'), live.tripState(legs, (T0 + 945) * 1000), legs, (T0 + 945) * 1000).title, 'Arrival 14:21');
});

test('what the traveller does: changing between rides, the bus by its product, the last walk', () => {
  const T = live.pickTexts('de');
  const p = (name, lat, lon, key, t) => ({ name, lat, lon, [key]: t });
  const trip = {
    legs: [
      { p: 'bvg', trip: 'a', line: 'M48', from: p('A', 52.522, 13.402, 'dep', T0), to: p('B', 52.524, 13.405, 'arr', T0 + 600) },
      { walk: true, from: p('B', 52.524, 13.405, 'dep', T0 + 600), to: p('C', 52.5245, 13.4055, 'arr', T0 + 780) },
      { p: 'bvg', trip: 'b', line: 'S41', from: p('C', 52.5245, 13.4055, 'dep', T0 + 900), to: p('D', 52.53, 13.42, 'arr', T0 + 1500) },
      { walk: true, from: p('D', 52.53, 13.42, 'dep', T0 + 1500), to: p('Ziel', 52.531, 13.421, 'arr', T0 + 1800) },
    ],
  };
  const legs = live.computeLegs(live.parseFragment(fragment(trip)), {});
  const doing = (s) => live.nowLine(T, live.tripState(legs, (T0 + s) * 1000), legs);
  assert.equal(doing(300), 'gerade: im Bus M48');
  assert.equal(doing(700), 'beim Umsteigen in C');
  assert.equal(doing(850), 'beim Umsteigen in C');
  assert.equal(doing(1200), 'gerade: in der S41');
  assert.equal(doing(1600), 'gerade: zu Fuß auf dem letzten Stück');
  assert.equal(doing(1900), '');
});
