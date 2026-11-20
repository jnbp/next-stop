// Offline tests of the logic of web/route/app.js: node web/test/route.test.js [v3 data from the app]
// test/fixtures/route-v3.json is the JSON the app writes for a fixed connection (RouteLinkTest.kt
// checks the Kotlin encoder against the same file), so both sides of the link format stay in step;
// route-v1.json is the same connection as the app wrote it before v3 (links the page still reads).
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const zlib = require('node:zlib');
const route = require('../route/app.js');
const core = require('../shared/core.js');
const demoScript = require('../route/demo.js');

const FIXTURE = fs.readFileSync(path.join(__dirname, 'fixtures', 'route-v1.json'), 'utf8').trim();
const T0 = 1794640320; // the first departure of the fixture, epoch seconds
const fragment = (json) => '#v=1&d=' + route.toBase64Url(json);
const iso = (s) => new Date(s * 1000).toISOString();
const T = route.pickTexts('de');
const E = route.pickTexts('en');

// The fixture as plain objects, as encodeRoute takes them.
const fixtureObject = {
  legs: [
    { walk: true, m: 180, poly: route.encodePolyline([[52.52, 13.405], [52.5208, 13.4085], [52.52151, 13.41127]], 5),
      from: { name: 'Startstraße 1', lat: 52.52, lon: 13.405, dep: T0 },
      to: { name: 'S+U Alexanderplatz', id: '900100003', lat: 52.52151, lon: 13.41127, arr: T0 + 240, entr: 'B' } },
    { p: 'bvg', trip: '1|31911|21|86|14112026', line: 'S5', prod: 'suburban', dir: 'Strausberg Nord', n: 3, b: 'm',
      from: { name: 'S+U Alexanderplatz', id: '900100003', lat: 52.52151, lon: 13.41127, dep: T0 + 300, dd: 2, pl: '2' },
      to: { name: 'S Ostkreuz', id: '900120003', lat: 52.50303, lon: 13.46902, arr: T0 + 840, ad: 3, pl: '3' } },
    { p: 'tr', trip: 'de-DELFI_1234#5', line: 'RE7', prod: 'regional', dir: 'Frankfurt (Oder) "Bhf" ü€😀', n: 0, b: 'r', be: true, x: true,
      from: { name: 'S Ostkreuz', id: 'de-DELFI_000:11', lat: 52.50281, lon: 13.46985, dep: T0 + 1200, dd: -1, pl: 'Pos. 11', pos: '3' },
      to: { name: 'Flughafen BER', id: 'de-DELFI_000:22', lat: 52.36443, lon: 13.5097, arr: T0 + 2280 } },
  ],
  dest: { name: 'Flughafen BER, Terminal 1', lat: 52.3655, lon: 13.5101 },
  t: T0 - 600,
};

// A small connection for the states: walk 4 min, U8 (BVG) 9 min, walk 3 min, X7 (BVG) 12 min.
function sample(extra) {
  return Object.assign({
    legs: [
      { walk: true, m: 300, from: { name: 'Start', lat: 52.52, lon: 13.405, dep: T0 }, to: { name: 'U Alexanderplatz', id: '900100005', lat: 52.52153, lon: 13.41123, arr: T0 + 240 } },
      { p: 'bvg', trip: 'u8', line: 'U8', prod: 'subway', dir: 'Hermannstr.', n: 1,
        from: { name: 'U Alexanderplatz', id: '900100005', lat: 52.52153, lon: 13.41123, dep: T0 + 300, pl: '2' },
        to: { name: 'U Hermannplatz', id: '900078101', lat: 52.48693, lon: 13.42484, arr: T0 + 840, pl: '1' } },
      { walk: true, from: { name: 'U Hermannplatz', lat: 52.48693, lon: 13.42484, dep: T0 + 840 }, to: { name: 'Hermannplatz', id: '900078102', lat: 52.4866, lon: 13.4243, arr: T0 + 1020 } },
      { p: 'bvg', trip: 'x7', line: 'M41', prod: 'bus', dir: 'Sonnenallee', n: 2,
        from: { name: 'Hermannplatz', id: '900078102', lat: 52.4866, lon: 13.4243, dep: T0 + 1140 },
        to: { name: 'Sonnenallee', id: '900077106', lat: 52.4770, lon: 13.4500, arr: T0 + 1860 } },
    ],
    dest: { name: 'Sonnenallee', lat: 52.4770, lon: 13.4500 },
    t: T0 - 3600,
  }, extra || {});
}

/** A BVG answer for the U8 with [delayS] seconds of delay and [platform] at the start (null: no prognosis). */
function bvgU8(delayS, platform, cancelled) {
  const stop = (id, name, lat, lon) => ({ id, name, location: { latitude: lat, longitude: lon } });
  const rt = delayS !== null;
  const d = rt ? delayS : 0;
  return {
    trip: {
      line: { name: 'U8', product: 'subway' }, direction: 'S+U Hermannstr.', cancelled: !!cancelled,
      stopovers: [
        { stop: stop('900100005', 'U Alexanderplatz', 52.52153, 13.41123), plannedDeparture: iso(T0 + 300), departure: iso(T0 + 300 + d),
          departureDelay: rt ? d : null, plannedDeparturePlatform: '2', departurePlatform: platform || '2' },
        { stop: stop('900013102', 'U Kottbusser Tor', 52.4991, 13.4181), plannedArrival: iso(T0 + 600), arrival: iso(T0 + 600 + d), arrivalDelay: rt ? d : null,
          plannedDeparture: iso(T0 + 630), departure: iso(T0 + 630 + d), departureDelay: rt ? d : null },
        { stop: stop('900078101', 'U Hermannplatz', 52.48693, 13.42484), plannedArrival: iso(T0 + 840), arrival: iso(T0 + 840 + d),
          arrivalDelay: rt ? d : null, plannedArrivalPlatform: '1', arrivalPlatform: '1' },
      ],
      polyline: { type: 'FeatureCollection', features: [] },
    },
  };
}

test('the fixture of the app decodes, and the JS encoder writes exactly the same JSON', () => {
  assert.equal(route.encodeRoute(fixtureObject), FIXTURE);
  const r = route.parseRoute(fragment(FIXTURE));
  assert.equal(r.legs.length, 3);
  assert.equal(r.created, (T0 - 600) * 1000);
  assert.equal(r.dest.name, 'Flughafen BER, Terminal 1');
  const [walk, s5, re7] = r.legs;
  assert.equal(walk.walk, true);
  assert.equal(walk.meters, 180);
  assert.equal(walk.to.entrance, 'B');
  assert.deepEqual(walk.shape, [[52.52, 13.405], [52.5208, 13.4085], [52.52151, 13.41127]]);
  assert.equal(s5.provider, 'bvg');
  assert.equal(s5.product, 'suburban');
  assert.equal(s5.from.platform, '2');
  assert.equal(s5.from.delay, 2);
  assert.equal(s5.to.delay, 3);
  assert.equal(s5.stopCount, 3);
  assert.deepEqual(s5.board, { part: 'middle', estimated: false });
  assert.equal(re7.provider, 'tr');
  assert.equal(re7.direction, 'Frankfurt (Oder) "Bhf" ü€😀');
  assert.equal(re7.from.platform, 'Pos. 11');
  assert.equal(re7.from.spot, '3');
  assert.equal(re7.from.delay, -1);
  assert.deepEqual(re7.board, { part: 'rear', estimated: true });
  assert.equal(re7.cancelled, true);
  assert.equal(re7.stopCount, 0);
});

test('polylines match the encoder of the app (Kotlin encodePolyline)', () => {
  // The same values as printed by the Kotlin harness for these points.
  assert.equal(route.encodePolyline([[52.52, 13.405], [52.5208, 13.4085], [52.52151, 13.41127]], 5), '_yp_IgdypA_D{TmCiP');
  assert.equal(route.encodePolyline([[-33.86882, 151.20929], [-33.8, 151.3]], 5), 'b_vmEaa|y[cmL}uP');
});

test('bad links are refused, odd fields are dropped', () => {
  assert.throws(() => route.parseRoute('#v=2&d=x'), /version/);
  assert.throws(() => route.parseRoute('#v=1&d=%%%'), /data/);
  assert.throws(() => route.parseRoute(fragment('{"legs":[]}')), /data/);
  assert.throws(() => route.parseRoute(fragment('{"legs":[{"from":{"name":"a"},"to":{"name":"b","arr":1}}]}')), /data/);
  const odd = JSON.parse(FIXTURE);
  odd.legs[1].b = 'x';
  odd.legs[1].n = -4;
  odd.legs[0].poly = '?';
  odd.n = 'Ana'; // a name has no place in a route link
  const r = route.parseRoute(fragment(JSON.stringify(odd)));
  assert.equal(r.legs[1].board, null);
  assert.equal(r.legs[1].stopCount, null);
  assert.equal(r.legs[0].shape, null);
  assert.equal(r.name, undefined);
});

test('a fragment from the app decodes (v1 here; with an argument the data of a v3 link of the app)', async () => {
  const fromKotlin = process.argv[2];
  const r = fromKotlin ? await route.readRoute('#3d' + fromKotlin) : route.parseRoute('#v=1&d=' + route.toBase64Url(FIXTURE));
  assert.equal(r.legs[2].direction, 'Frankfurt (Oder) "Bhf" ü€😀');
});

// route-v1.deflate.txt: route-v1.json raw-deflated by java.util.zip.Deflater(nowrap = true), as the app does it.
const FROM_JAVA = fs.readFileSync(path.join(__dirname, 'fixtures', 'route-v1.deflate.txt'), 'utf8').trim();

test('v2: the same JSON raw-deflated reads back, with the language of the sender', async () => {
  const r = await route.readRoute('#v=2&l=de&d=' + FROM_JAVA);
  assert.equal(r.lang, 'de');
  assert.deepEqual(r, Object.assign(route.parseRoute(fragment(FIXTURE)), { lang: 'de' }));
  // Links of format v1 still read; newer or broken ones do not.
  assert.equal((await route.readRoute(fragment(FIXTURE))).legs.length, 3);
  await assert.rejects(route.readRoute('#v=9&d=' + FROM_JAVA), /version/);
  await assert.rejects(route.readRoute('#v=2&d=' + FROM_JAVA.slice(0, 40)), /data/);
});

test('v2: a browser without DecompressionStream gets its own message', async () => {
  const saved = globalThis.DecompressionStream;
  delete globalThis.DecompressionStream;
  try {
    await assert.rejects(route.readRoute('#v=2&d=' + FROM_JAVA), /unsupported/);
  } finally {
    globalThis.DecompressionStream = saved;
  }
});

const FIXTURE_V3 = fs.readFileSync(path.join(__dirname, 'fixtures', 'route-v3.json'), 'utf8').trim();
// route-v3.deflate.txt: route-v3.json raw-deflated with the link dictionary by java.util.zip.Deflater, as the app does it.
const FROM_JAVA_V3 = fs.readFileSync(path.join(__dirname, 'fixtures', 'route-v3.deflate.txt'), 'utf8').trim();
const fragment3 = (json) => '#3d' + zlib.deflateRawSync(Buffer.from(json, 'utf8'), { level: 9, dictionary: Buffer.from(core.LINK_DICTIONARY, 'utf8') }).toString('base64url');

test('v3: the JS encoder writes exactly the JSON of the app (route-v3.json)', () => {
  assert.equal(route.encodeRoute3(fixtureObject), FIXTURE_V3);
});

test('v3: the fixture deflated by Java reads back as the very same route as v1', async () => {
  const r = await route.readRoute('#3d' + FROM_JAVA_V3);
  assert.equal(r.lang, 'de');
  assert.deepEqual(r, Object.assign(route.parseRoute(fragment(FIXTURE)), { lang: 'de' }));
  assert.deepEqual(await route.readRoute(fragment3(FIXTURE_V3)), r);
  // Expanded, it is the v1 JSON of the app before v3.
  assert.equal(route.encodeRoute(route.expandRoute3(JSON.parse(FIXTURE_V3))), FIXTURE);
});

test('v3: a way is written after its stop and moved back', () => {
  const shape = '_yp_IgdypA_D{TmCiP';
  // The same strings as in RouteLinkTest.kt (Kotlin rebasePolyline).
  assert.equal(core.rebasePolyline(shape, -5252000, -1340500), '??_D{TmCiP');
  assert.equal(core.rebasePolyline('??_D{TmCiP', 5252000, 1340500), shape);
  assert.equal(core.rebasePolyline('', 1, 1), '');
  assert.equal(core.rebasePolyline('_', 1, 1), '_');
  assert.match(FIXTURE_V3, /"\?\?_D\{TmCiP"/);
});

test('v3: a walk by bike, a ride without trip, odd and broken links', async () => {
  const bike = await route.readRoute(fragment3('[0,0,[["A"],["B"]],[[0,1,0,2,-2]],["B"]]'));
  assert.equal(bike.legs[0].walk, true);
  assert.equal(bike.legs[0].bike, true);
  assert.equal(bike.legs[0].to.planned, 120000);
  const ride = await route.readRoute(fragment3('[0,0,[["A"],["B"]],[[0,1,0,0,0,0,"S1"]],["B"]]'));
  assert.equal(ride.legs[0].walk, false);
  assert.equal(ride.legs[0].line, 'S1');
  assert.equal(ride.legs[0].provider, null);
  assert.equal(ride.legs[0].stopCount, null);
  await assert.rejects(route.readRoute('#3d'), /version/);
  await assert.rejects(route.readRoute('#3d' + FROM_JAVA_V3.slice(0, 40)), /data/);
  await assert.rejects(route.readRoute(fragment3('[0,0,[["A"]],[[0,3]],["B"]]')), /data/);
  await assert.rejects(route.readRoute(fragment3('[0,0,[["A"]],[],["B"]]')), /data/);
  await assert.rejects(route.readRoute(fragment3('{"legs":[]}')), /data/);
});

test('page language: the recipient\'s German or English, else the sender\'s', () => {
  assert.equal(route.pickTexts('fr', 'de'), T);
  assert.equal(route.pickTexts('en-US', 'de'), E);
  assert.equal(route.pickTexts('de-CH', 'en'), T);
  assert.equal(route.pickTexts('es', null), E);
  assert.equal(core.linkLanguage('#3e' + FROM_JAVA_V3), 'en');
});

test('steps without live data follow the delays known when shared; walks start after the ride before', () => {
  const r = route.parseRoute(fragment(FIXTURE));
  const steps = route.computeSteps(r, {});
  assert.equal(steps[1].dep, (T0 + 300) * 1000 + 2 * 60000);
  assert.equal(steps[1].arr, (T0 + 840) * 1000 + 3 * 60000);
  assert.equal(steps[1].live, false);
  assert.equal(steps[1].trackable, true);
  assert.equal(steps[0].trackable, false);
  assert.equal(steps[2].cancelled, true);
  assert.equal(steps[1].color, '#EB7405'); // S5
  const s = route.computeSteps(route.parseRoute(fragment(route.encodeRoute(sample()))), {});
  assert.equal(s[2].dep, s[1].arr);
  assert.equal(s[2].arr - s[2].dep, 180000);
});

test('live data: delays, a platform change, the stops in between, real time or not', () => {
  const r = route.parseRoute(fragment(route.encodeRoute(sample())));
  const steps = route.computeSteps(r, { 1: route.parseBvgTrip(bvgU8(120, '3')) });
  const u8 = steps[1];
  assert.equal(u8.live, true);
  assert.equal(u8.realtime, true);
  assert.equal(u8.delayDep, 2);
  assert.equal(u8.depPlatform, '3');
  assert.equal(u8.depPlatformChanged, true);
  assert.equal(u8.arrPlatformChanged, false);
  assert.deepEqual(u8.between.map((b) => b.name), ['U Kottbusser Tor']);
  assert.equal(u8.stopCount, 1);
  assert.equal(steps[2].dep, u8.arr); // the walk after it moves along
  const plain = route.computeSteps(r, { 1: route.parseBvgTrip(bvgU8(null)) })[1];
  assert.equal(plain.live, true);
  assert.equal(plain.realtime, false);
  assert.equal(plain.depPlatformChanged, false);
  assert.ok(route.samePlatform('2', 'Gleis 2'));
  assert.ok(!route.samePlatform('2', '3'));
});

test('timeline states: before, the step now, done, arrived', () => {
  const r = route.parseRoute(fragment(route.encodeRoute(sample())));
  const steps = route.computeSteps(r, {});
  const at = (s) => (T0 + s) * 1000;
  let st = route.routeStatus(steps, at(-600));
  assert.equal(st.phase, 'before');
  assert.equal(st.next, 0);
  assert.equal(st.changes, 1);
  assert.deepEqual(route.stepStatus(steps, st, at(-600)), ['next', 'upcoming', 'upcoming', 'upcoming']);
  st = route.routeStatus(steps, at(400));
  assert.equal(st.phase, 'underway');
  assert.equal(st.current, 1);
  assert.deepEqual(route.stepStatus(steps, st, at(400)), ['done', 'current', 'next', 'upcoming']);
  st = route.routeStatus(steps, at(1000));
  assert.equal(st.current, 2);
  st = route.routeStatus(steps, at(1100)); // waiting for the bus
  assert.equal(st.current, -1);
  assert.equal(st.next, 3);
  st = route.routeStatus(steps, at(2000));
  assert.equal(st.phase, 'arrived');
  assert.deepEqual(route.stepStatus(steps, st, at(2000)), ['done', 'done', 'done', 'done']);
});

test('problems: missed first ride, cancelled ride, a change that breaks or gets tight', () => {
  const r = route.parseRoute(fragment(route.encodeRoute(sample())));
  const at = (s) => (T0 + s) * 1000;
  // After the U8 left (and a minute of grace), someone who opens the link has missed it.
  let st = route.routeStatus(route.computeSteps(r, {}), at(300 + 30));
  assert.deepEqual(st.problems, []);
  st = route.routeStatus(route.computeSteps(r, {}), at(300 + 90));
  assert.deepEqual(st.problems.map((p) => p.kind), ['missed']);
  let banner = route.bannerModel(T, st);
  assert.equal(banner.level, 'warn');
  assert.equal(banner.later, true);
  assert.match(banner.lines[0], /U8 ist um \d\d:\d\d abgefahren/);
  assert.match(banner.lines[1], /Schon unterwegs/);
  // The U8 runs 5 min late: arrives 14:19, walk 3 min, the bus leaves at 14:19 → broken.
  st = route.routeStatus(route.computeSteps(r, { 1: route.parseBvgTrip(bvgU8(300)) }), at(-600));
  assert.deepEqual(st.problems.map((p) => p.kind), ['broken']);
  assert.equal(st.problems[0].at, 'Hermannplatz');
  banner = route.bannerModel(T, st);
  assert.equal(banner.level, 'bad');
  assert.equal(banner.later, true);
  // 1 min late leaves 1 min: tight, but no later connection offered for that alone.
  st = route.routeStatus(route.computeSteps(r, { 1: route.parseBvgTrip(bvgU8(60)) }), at(-600));
  assert.deepEqual(st.problems.map((p) => p.kind), ['tight']);
  assert.equal(route.bannerModel(T, st).later, false);
  // Cancelled.
  st = route.routeStatus(route.computeSteps(r, { 1: route.parseBvgTrip(bvgU8(0, null, true)) }), at(-600));
  assert.deepEqual(st.problems.map((p) => p.kind), ['cancelled']);
  assert.match(route.bannerModel(T, st).lines[0], /U8 um \d\d:\d\d fällt aus/);
  // Nothing to say before the start without live data.
  st = route.routeStatus(route.computeSteps(r, {}), at(-600));
  assert.equal(route.bannerModel(T, st).level, null);
  // Arrived: the connection is behind; a later one can be searched.
  st = route.routeStatus(route.computeSteps(r, {}), at(5000));
  banner = route.bannerModel(E, st);
  assert.equal(banner.lines[0], 'This connection is behind you.');
});

test('real time: far ahead it says when it comes, near it says what is known', () => {
  const r = route.parseRoute(fragment(route.encodeRoute(sample())));
  const steps = route.computeSteps(r, {});
  const u8 = steps[1];
  assert.equal(route.realtimeKind(steps[0], 0), null);
  assert.equal(route.realtimeKind(u8, u8.dep - 3 * 3600000), 'far');
  assert.equal(route.realtimeKind(u8, u8.dep - 10 * 60000), 'plan');
  const live = route.computeSteps(r, { 1: route.parseBvgTrip(bvgU8(60)) })[1];
  assert.equal(route.realtimeKind(live, live.dep - 10 * 60000), 'live');
  assert.equal(route.realtimeKind(live, live.arr + 1), 'done');
  const noTrip = route.computeSteps(route.parseRoute(fragment(route.encodeRoute(sample({ legs: sample().legs.map((l) => Object.assign({}, l, { trip: undefined })) })))), {})[1];
  assert.equal(route.realtimeKind(noTrip, noTrip.dep - 60000), 'none');
  assert.equal(T.rtFar, 'Echtzeit ab ca. 30 min vor Abfahrt');
});

test('polling: only rides near now, 30 s seen, 2 min hidden, longer after failures', () => {
  const r = route.parseRoute(fragment(route.encodeRoute(sample())));
  const steps = route.computeSteps(r, {});
  const at = (s) => (T0 + s) * 1000;
  assert.deepEqual(route.ridesToPoll(steps, at(-3 * 3600)), []);
  assert.deepEqual(route.ridesToPoll(steps, at(-20 * 60)), [1, 3]);
  assert.deepEqual(route.ridesToPoll(steps, at(1000)), [3]);
  assert.deepEqual(route.ridesToPoll(steps, at(1860 + 300)), []);
  assert.equal(route.pollDelay(false, 0), 30000);
  assert.equal(route.pollDelay(true, 0), 120000);
  assert.equal(route.pollDelay(false, 2), 120000);
  assert.equal(route.pollDelay(false, 9), 300000);
});

test('links out: Google Maps walk, Transitous search, the app', () => {
  assert.equal(route.googleWalkUrl([52.521512345, 13.41127], [52.5208, 13.4085]),
    'https://www.google.com/maps/dir/?api=1&origin=52.52151%2C13.41127&destination=52.5208%2C13.4085&travelmode=walking');
  assert.equal(route.googleWalkUrl(null, [52.5, 13.4]), null);
  const from = { name: 'S+U Alexanderplatz', pos: [52.52151, 13.41127] };
  const to = { name: 'Flughafen BER', pos: [52.36443, 13.5097] };
  const web = route.transitousSearchUrl(from, to);
  assert.equal(web, 'https://api.transitous.org/?fromPlace=52.52151%2C13.41127&fromName=S%2BU+Alexanderplatz&toPlace=52.36443%2C13.5097&toName=Flughafen+BER');
  const app = route.appSearchUrl(from, to, web);
  assert.match(app, /^intent:\/\/open\/route\?fromName=S%2BU\+Alexanderplatz&from=52\.52151%2C13\.41127&toName=Flughafen\+BER&to=52\.36443%2C13\.5097#Intent;scheme=nextstop;package=io\.github\.jnbp\.nextstop;S\.browser_fallback_url=https%3A%2F%2Fapi\.transitous\.org%2F/);
  assert.match(app, /;end$/);
});

test('texts: platforms, spots, boarding, durations, days, countdown', () => {
  assert.equal(route.platformText(T, '2', 'suburban'), 'Gleis 2');
  assert.equal(route.platformText(T, '2', 'bus'), 'Steig 2');
  assert.equal(route.platformText(T, '4', null, 'U8'), 'Gleis 4');
  assert.equal(route.platformText(E, 'Pos. 11', 'bus'), 'Pos. 11');
  assert.equal(route.platformText(T, null), null);
  assert.equal(route.spotText(T, '3'), 'Pos. 3');
  assert.equal(route.exitText(T, 'A'), 'Ausgang A');
  assert.equal(route.exitText(T, 'Terminal 1'), 'Ausgang Terminal 1');
  assert.equal(route.exitText(T, null), null);
  assert.equal(route.entranceText(E, 'B'), 'Entrance B');
  assert.equal(route.boardText(T, { part: 'middle', estimated: false }), 'In der Mitte einsteigen');
  assert.equal(route.boardText(T, { part: 'rear', estimated: true }), 'Eher hinten einsteigen');
  assert.equal(route.durationText(T, 52 * 60000), '52 min');
  assert.equal(route.durationText(T, 65 * 60000), '1 h 05 min');
  assert.equal(route.metersText(T, 1234), '1,2 km');
  assert.equal(route.metersText(E, 183), '180 m');
  const noon = Date.UTC(2026, 10, 14, 11, 0); // 12:00 in Berlin
  assert.equal(route.dayText(T, noon + 3600000, noon), 'Heute');
  assert.equal(route.dayText(T, Date.UTC(2026, 10, 15, 7, 12), noon), 'Morgen'); // 08:12 tomorrow
  assert.equal(route.dayText(T, Date.UTC(2026, 10, 14, 23, 30), noon), 'Morgen'); // 00:30 in Berlin
  assert.match(route.dayText(T, Date.UTC(2026, 10, 18, 7, 0), noon), /Mi.*18\.11\./);
  assert.equal(route.countdownText(T, noon + 23 * 60000, noon), 'in 23 min');
  assert.equal(route.countdownText(T, noon + 65 * 60000, noon), 'in 1 h 05 min');
  assert.equal(route.countdownText(T, noon + 2 * 86400000, noon), '');
  assert.equal(route.countdownText(T, noon - 1, noon), '');
});

test('the demo runs through every state without network, with fast forward', async () => {
  let clock = Date.UTC(2026, 10, 14, 11, 0);
  const demo = demoScript.create({ routeFragment: route.routeFragment, encodePolyline: route.encodePolyline, hash: '#demo&at=-12', clock: () => clock });
  const r = route.parseRoute(demo.hash);
  assert.equal(r.legs.length, 5);
  assert.deepEqual(r.legs.map((l) => l.line), [null, 'S5', null, 'FEX', null]);
  assert.equal(r.legs[1].from.platform, '2');
  assert.equal(r.legs[0].to.entrance, 'B');
  const phases = [];
  const kinds = new Set();
  for (let k = 0; k < 14; k++) {
    const live = {};
    for (const i of [1, 3]) {
      const res = await demo.fetch('https://v6.bvg.transport.rest/trips/' + r.legs[i].trip + '?x');
      live[i] = route.parseBvgTrip(await res.json());
    }
    const steps = route.computeSteps(r, live);
    const st = route.routeStatus(steps, demo.now());
    if (phases[phases.length - 1] !== st.phase) phases.push(st.phase);
    steps.forEach((s) => kinds.add(route.realtimeKind(s, demo.now())));
    if (k === 0) {
      // Twelve minutes before: the S5 has a prognosis, the FEX (32 min ahead) not yet.
      assert.equal(route.realtimeKind(steps[1], demo.now()), 'live');
      assert.equal(route.realtimeKind(steps[3], demo.now()), 'far');
    }
    demo.forward(5 * 60000);
  }
  assert.deepEqual(phases, ['before', 'underway', 'arrived']);
  assert.ok(kinds.has('live') && kinds.has('far') && kinds.has('done'));
  // The FEX leaves from another platform than planned once real time is known.
  const late = demoScript.create({ routeFragment: route.routeFragment, encodePolyline: route.encodePolyline, hash: '#demo&at=10&late=1&cancel=1', clock: () => clock });
  const lr = route.parseRoute(late.hash);
  const live = {};
  for (const i of [1, 3]) live[i] = route.parseBvgTrip(await (await late.fetch('/trips/' + lr.legs[i].trip)).json());
  const steps = route.computeSteps(lr, live);
  assert.equal(steps[3].cancelled, true);
  assert.equal(steps[3].depPlatformChanged, true);
  const kindsLate = route.routeStatus(steps, late.now()).problems.map((p) => p.kind);
  assert.ok(kindsLate.includes('cancelled'));
  // Tomorrow at 08:12: real time only from about half an hour before.
  const tomorrow = demoScript.create({ routeFragment: route.routeFragment, encodePolyline: route.encodePolyline, hash: '#demo&tomorrow=1', clock: () => clock });
  const tr = route.parseRoute(tomorrow.hash);
  const s5 = route.computeSteps(tr, { 1: route.parseBvgTrip(await (await tomorrow.fetch('/trips/demo-s5')).json()) })[1];
  assert.equal(route.realtimeKind(s5, tomorrow.now()), 'far');
  assert.equal(route.dayText(T, s5.dep, tomorrow.now()), 'Morgen');
  assert.equal(require('../shared/core.js').clock(s5.dep), '08:12');
});

// A ride outside the VBB: a regional train named "U1" in the north of Bohemia, then a bus.
function abroad() {
  return {
    legs: [
      { p: 'tr', trip: 'cz_1#1', line: 'U1', prod: 'regional', dir: 'Kadan',
        from: { name: 'Town A', lat: 50.46, lon: 13.42, dep: T0, pl: '1' },
        to: { name: 'Town B', lat: 50.38, lon: 13.27, arr: T0 + 900, pl: '2' } },
      { p: 'tr', trip: 'at_1#1', line: 'rj 171', prod: 'express', dir: 'Graz',
        from: { name: 'Town C', lat: 48.2, lon: 16.37, dep: T0 + 3600 },
        to: { name: 'Town D', lat: 47.07, lon: 15.44, arr: T0 + 9000 } },
    ],
    dest: { name: 'Town D', lat: 47.07, lon: 15.44 },
    t: T0 - 600,
  };
}

test('a train named U1 abroad is no Berlin U-Bahn: neither colour nor platform word', () => {
  const steps = route.computeSteps(route.parseRoute(fragment(route.encodeRoute(abroad()))), {});
  assert.equal(steps[0].line, 'U1');
  assert.equal(steps[0].color, '#DA251D', 'the colour of regional trains, not the green of the Berlin U1');
  // Without a product the names of Berlin's network count only in the VBB: an M10 there is a tram.
  assert.equal(route.platformText(T, '1', null, 'M10', [52.52, 13.41]), 'Steig 1');
  assert.equal(route.platformText(T, '1', null, 'M10', [50.46, 13.42]), 'Gleis 1');
});

test('train kinds are written in capitals on the badge', () => {
  const r = route.parseRoute(fragment(route.encodeRoute(abroad())));
  assert.equal(r.legs[1].line, 'RJ 171');
  assert.equal(route.computeSteps(r, {})[1].line, 'RJ 171');
});

test('the line of the ride counts before the line the trip of the provider names', () => {
  const r = route.parseRoute(fragment(FIXTURE));
  const trip = route.parseBvgTrip(bvgU8(null));
  trip.line = 'N8';
  const steps = route.computeSteps(r, { 1: trip });
  assert.equal(steps[1].line, 'S5');
});

test('a way by bike (the bike combo) is read, kept and says so', () => {
  const r = route.parseRoute(fragment(FIXTURE));
  assert.equal(r.legs[0].bike, false);
  const bikeRoute = JSON.parse(FIXTURE);
  bikeRoute.legs[0].bike = true;
  // A ride cannot be ridden by bike: the flag counts on walks only.
  bikeRoute.legs[1].bike = true;
  const json = JSON.stringify(bikeRoute);
  const parsed = route.parseRoute(fragment(json));
  assert.equal(parsed.legs[0].bike, true);
  assert.equal(parsed.legs[1].bike, false);
  const steps = route.computeSteps(parsed, {});
  assert.equal(steps[0].bike, true);
  assert.equal(steps[1].bike, false);
  assert.equal(route.TEXTS.de.bike, 'Mit dem Rad');
  assert.equal(route.TEXTS.en.bike, 'By bike');
  assert.match(route.googleWalkUrl([52.5, 13.4], [52.51, 13.41], true), /travelmode=bicycling$/);
});

test('hero: duration as the app writes it, and the stop of the first ride (never an address)', () => {
  const r = route.parseRoute(fragment(FIXTURE));
  const steps = route.computeSteps(r, {});
  const status = route.routeStatus(steps, (T0 - 600) * 1000);
  const hero = route.heroSummary(T, steps, status);
  assert.equal(hero.origin, 'ab S+U Alexanderplatz');
  assert.ok(!/Startstraße/.test(hero.origin), 'the address of the start stays out');
  assert.equal(hero.duration, route.minutesMark((status.end - status.start) / 60000));
  assert.match(hero.duration, /^\d+′$|^\d+:\d\d h$/);
  assert.equal(route.minutesMark(80), '1:20 h');
  // A route of walks only has no first ride.
  const walkOnly = route.computeSteps({ legs: [r.legs[0]], dest: r.dest }, {});
  assert.equal(route.heroSummary(T, walkOnly, route.routeStatus(walkOnly, 0)).origin, null);
});

test('boarding: the part of the train as three flags, front to rear', () => {
  assert.deepEqual(route.boardParts('front'), [true, false, false]);
  assert.deepEqual(route.boardParts('middle'), [false, true, false]);
  assert.deepEqual(route.boardParts('rear'), [false, false, true]);
  assert.deepEqual(route.boardParts(null), [false, false, false]);
});

test('the app: "In Next Stop öffnen" falls back to the store page', () => {
  const from = { name: 'S+U Alexanderplatz', pos: [52.52151, 13.41127] };
  const to = { name: 'Flughafen BER', pos: [52.36443, 13.5097] };
  const app = route.appSearchUrl(from, to, route.PLAY_URL);
  assert.match(app, /S\.browser_fallback_url=https%3A%2F%2Fplay\.google\.com%2Fstore%2Fapps%2Fdetails%3Fid%3Dio\.github\.jnbp\.nextstop;end$/);
  assert.equal(route.appSearchUrl(from, to).indexOf('browser_fallback_url'), -1);
  assert.equal(route.TEXTS.de.openApp, 'In Next Stop öffnen');
  assert.equal(route.TEXTS.de.getApp, 'Bei Google Play laden');
});
