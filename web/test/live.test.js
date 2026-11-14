// Offline tests of the logic of web/live/app.js: node web/test/live.test.js [base64url from Kotlin]
// With an argument, it also checks that a fragment made by the app (JourneyShare.kt) decodes.
'use strict';
const assert = require('node:assert/strict');
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
  assert.throws(() => live.parseFragment('#v=2&d=x'), /version/);
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
  assert.equal(live.freshnessText(de, live.freshness(n - 12000, null, true, n)), 'aktualisiert vor 12 s');
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
  assert.equal(ride.color, '#224F86');
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
  assert.equal(live.lineColor('U8'), '#224F86');
  assert.equal(live.lineColor('S41', 'suburban'), '#AD5937');
  assert.equal(live.lineColor('M10'), '#BE1414');
  assert.equal(live.lineColor('M41'), '#95276E');
  assert.equal(live.lineColor('N60'), '#95276E');
  assert.equal(live.lineColor('RE1'), '#DA251D');
  assert.equal(live.lineColor('ICE 1234'), '#EC0016');
  assert.equal(live.lineColor('42', null, '#00ff00'), '#00ff00');
  assert.equal(live.lineColor('42', 'bus', 'red'), '#95276E', 'only hex colours of the feed');
  assert.equal(live.lineColor('?'), '#5A6072');
  assert.equal(live.inkOn('#F0D722'), '#16130A');
  assert.equal(live.inkOn('#224F86'), '#ffffff');
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

const fromKotlin = process.argv[2];
if (fromKotlin) {
  test('a fragment made by the app (Kotlin base64Url) decodes', () => {
    const j = live.parseFragment('#v=1&d=' + fromKotlin);
    assert.equal(j.legs.length, 3);
    assert.equal(j.name, 'Ana');
    assert.equal(j.legs[0].from.name, 'Startstraße 1');
    assert.equal(j.legs[1].direction, 'Hermannstraße');
    assert.equal(j.legs[2].direction, 'Frankfurt (Oder) "Bhf" ü€😀');
    assert.equal(j.legs[2].provider, 'tr');
    assert.equal(j.legs[2].from.delay, -1);
    assert.equal(j.legs[0].from.pos[0], 52.52);
    assert.equal(j.dest.name, 'Erkner');
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
  assert.equal(live.lineColor('U1', 'subway', null, berlin), '#7DAD4C');
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
