/*
 * Next Stop route page ("So kommst du hin"): a connection shared as a link, so that someone else
 * can travel it themselves, step by step.
 *
 * The link carries the planned connection in its fragment (#v=1&d=<base64url JSON>), so it never
 * reaches a server log. This page asks the public APIs itself (BVG HAFAS REST and Transitous
 * MOTIS) for the live times of each ride: delays, cancellations and platform changes. It never
 * knows where the reader is; it shows the timetable with real-time data, nothing more.
 *
 * No build step, no framework. The pure logic at the top runs in Node as well (see
 * web/test/route.test.js); the DOM part at the bottom only runs in a browser.
 */
'use strict';

// The shared logic of both pages (web/shared/core.js).
const core = typeof module !== 'undefined' && module.exports ? require('../shared/core.js') : window.NextStopCore;
const {
  MAP_STYLE, fromBase64Url, toBase64Url, str, num, coord, parseBvgTrip, parseTransitousTrip,
  decodePolyline, encodePolyline, cutPath, cutPathBetween, expectedTime, matchStop, tripUrl, lineColor, canonicalLine,
  ROUTE_DRAW_MS, snakeFeatures, easeOutCubic, freshness, freshnessText, clock, delayText, delayLevel, LIMITS,
  readLinkFragment, pickLanguage, minutesMark, appIntentUrl, boardParts, PLAY_URL,
} = core;

// Polling of the rides near now: every 30 s while the page is seen, every 2 min while hidden.
const POLL_MS = { visible: 30000, hidden: 120000 };
const MAX_BACKOFF_MS = 5 * 60000;
// A ride is asked for from this long before it leaves until it arrived.
const POLL_AHEAD_MS = 40 * 60000;
// Before this, the APIs rarely have a prognosis: the page says so instead of "no live data".
const REALTIME_WINDOW_MS = 30 * 60000;
// A ride that left this long ago counts as gone for someone who has not boarded it.
const MISSED_GRACE_MS = 60000;
// A change with less room than this (after the walk) is tight.
const TIGHT_MS = 2 * 60000;
const PARTS = { f: 'front', m: 'middle', r: 'rear' };

/* ---------- Link ---------- */

/** A place of the link: name, id, position, planned time (epoch ms), delay, platform and spots. */
function readPlace(raw, timeKey, delayKey) {
  if (!raw || typeof raw !== 'object') return null;
  const time = num(raw[timeKey]);
  return {
    name: str(raw.name) || '?',
    id: str(raw.id, LIMITS.id),
    pos: coord(raw.lat, raw.lon),
    planned: time !== null ? time * 1000 : null,
    delay: num(raw[delayKey]) || 0,
    platform: str(raw.pl, 20),
    spot: str(raw.pos, 20),
    exit: str(raw.exit, 60),
    entrance: str(raw.entr, 60),
  };
}

/** A shape of the link: an encoded polyline (precision 5), checked; null when unusable. */
function readShape(text) {
  if (typeof text !== 'string' || text.length === 0 || text.length > 4000) return null;
  const points = decodePolyline(text, 5).filter((p) => Math.abs(p[0]) <= 90 && Math.abs(p[1]) <= 180);
  return points.length >= 2 ? points : null;
}

/**
 * Reads the fragment of a route link ("v=1&d=..."). Returns the checked route, or throws an
 * Error whose message is "version" or "data".
 */
function parseRoute(hash) {
  const params = new URLSearchParams(String(hash || '').replace(/^#/, ''));
  if (params.get('v') !== '1') throw new Error('version');
  let raw;
  try {
    raw = JSON.parse(fromBase64Url(params.get('d') || ''));
  } catch (e) {
    throw new Error('data');
  }
  return parseRoutePayload(raw);
}

/**
 * Reads the fragment of a route link of either format: v1 ("v=1&d=<base64url JSON>") or v2
 * ("v=2&l=<language>&d=<base64url of the same JSON, raw-deflated>"). Resolves to the checked
 * route with the sender's language as [lang]; rejects with an Error whose message is "version",
 * "data" or "unsupported" (a browser that cannot unpack v2).
 */
async function readRoute(hash) {
  const link = await readLinkFragment(hash);
  let raw;
  try {
    raw = JSON.parse(link.text);
  } catch (e) {
    throw new Error('data');
  }
  const route = parseRoutePayload(raw);
  route.lang = link.lang;
  return route;
}

/** The route of a payload (the JSON of the route link), checked. */
function parseRoutePayload(raw) {
  if (!raw || !Array.isArray(raw.legs) || raw.legs.length === 0 || raw.legs.length > LIMITS.legs) {
    throw new Error('data');
  }
  const legs = raw.legs.map((leg) => {
    if (!leg || typeof leg !== 'object') throw new Error('data');
    const walk = leg.walk === true;
    const from = readPlace(leg.from, 'dep', 'dd');
    const to = readPlace(leg.to, 'arr', 'ad');
    if (!from || !to || from.planned === null || to.planned === null) throw new Error('data');
    const provider = leg.p === 'tr' ? 'tr' : leg.p === 'bvg' ? 'bvg' : null;
    return {
      walk,
      // The bike combo: a way on the street ridden by bike.
      bike: walk && leg.bike === true,
      provider: walk ? null : provider,
      trip: walk ? null : str(leg.trip, LIMITS.id),
      line: walk ? null : canonicalLine(str(leg.line, 30), str(leg.prod, 20)),
      product: walk ? null : str(leg.prod, 20),
      direction: walk ? null : str(leg.dir),
      board: !walk && PARTS[leg.b] ? { part: PARTS[leg.b], estimated: leg.be === true } : null,
      stopCount: !walk && num(leg.n) !== null && leg.n >= 0 ? Math.round(leg.n) : null,
      meters: walk && num(leg.m) !== null && leg.m > 0 ? Math.round(leg.m) : null,
      cancelled: leg.x === true,
      shape: readShape(leg.poly),
      from,
      to,
    };
  });
  const last = legs[legs.length - 1];
  const dest = raw.dest && typeof raw.dest === 'object' ? raw.dest : {};
  return {
    created: num(raw.t) !== null ? raw.t * 1000 : null,
    dest: { name: str(dest.name) || last.to.name, pos: coord(dest.lat, dest.lon) || last.to.pos },
    legs,
  };
}

/**
 * The JSON of a route link from plain objects, with the keys in the order the app writes them
 * (routeLinkJson in ui/JourneyShare.kt). Used by the tests and the demo; the app encodes in Kotlin.
 */
function encodeRoute(route) {
  const place = (p, timeKey, delayKey) => {
    const out = { name: p.name };
    if (p.id) out.id = p.id;
    if (p.lat != null) out.lat = p.lat;
    if (p.lon != null) out.lon = p.lon;
    if (p[timeKey] != null) out[timeKey] = p[timeKey];
    if (p[delayKey]) out[delayKey] = p[delayKey];
    if (p.pl) out.pl = p.pl;
    if (p.pos) out.pos = p.pos;
    if (p.exit) out.exit = p.exit;
    if (p.entr) out.entr = p.entr;
    return out;
  };
  const legs = route.legs.map((l) => {
    const out = {};
    if (l.p) out.p = l.p;
    if (l.trip) out.trip = l.trip;
    if (l.line) out.line = l.line;
    if (l.prod) out.prod = l.prod;
    if (l.dir) out.dir = l.dir;
    out.from = place(l.from, 'dep', 'dd');
    out.to = place(l.to, 'arr', 'ad');
    if (l.walk) out.walk = true;
    if (l.walk && l.bike) out.bike = true;
    if (l.m) out.m = l.m;
    if (l.n != null) out.n = l.n;
    if (l.b) out.b = l.b;
    if (l.be) out.be = true;
    if (l.x) out.x = true;
    if (l.poly) out.poly = l.poly;
    return out;
  });
  const dest = { name: route.dest.name };
  if (route.dest.lat != null) dest.lat = route.dest.lat;
  if (route.dest.lon != null) dest.lon = route.dest.lon;
  return JSON.stringify({ legs, dest, t: route.t });
}

/** The fragment of a route link for [route] (plain objects as encodeRoute takes them). */
function routeFragment(route) {
  return '#v=1&d=' + toBase64Url(encodeRoute(route));
}

/* ---------- Steps with live data ---------- */

/** "2", " 2" and "Gleis 2" are the same platform: compared without words, case and spaces. */
function samePlatform(a, b) {
  const norm = (p) => String(p || '').toLowerCase().replace(/^(gleis|gl\.?|platform|pl\.?|track)\s*/, '').replace(/\s+/g, '');
  return norm(a) === norm(b);
}

/**
 * The route with times as expected now. Each leg gets dep and arr (epoch ms), its platforms
 * (as planned and as announced now), the stops in between where live data is known, its way on
 * the map, and whether live data, a prognosis and a cancellation are known. A walk after a ride
 * starts when that ride arrives. [live] maps a leg index to parsed live data.
 */
function computeSteps(route, live) {
  const out = [];
  route.legs.forEach((leg, index) => {
    const data = live[index];
    const duration = leg.to.planned - leg.from.planned;
    let dep = leg.from.planned + leg.from.delay * 60000;
    let arr = leg.to.planned + leg.to.delay * 60000;
    let between = [];
    let path = leg.shape;
    let liveOk = false;
    let realtime = false;
    let cancelled = leg.cancelled;
    let depPlatform = leg.from.platform;
    let arrPlatform = leg.to.platform;
    if (leg.walk) {
      const prev = out[out.length - 1];
      dep = prev ? Math.max(leg.from.planned, prev.arr) : leg.from.planned;
      arr = dep + duration;
    } else if (data && data.stops && data.stops.length) {
      // A ring line calls at a stop more than once: the calls at the times of the ride.
      const i = matchStop(data.stops, leg.from, null, 'dep');
      const j = i >= 0 ? matchStop(data.stops, leg.to, i, 'arr') : -1;
      if (i >= 0 && j > i) {
        const stops = data.stops.slice(i, j + 1);
        const first = stops[0];
        const last = stops[stops.length - 1];
        dep = expectedTime(first, 'dep');
        arr = expectedTime(last, 'arr');
        liveOk = true;
        realtime = !!(first.realtime || last.realtime);
        cancelled = !!(data.cancelled || first.cancelled || last.cancelled);
        depPlatform = first.depPlatform || depPlatform;
        arrPlatform = last.arrPlatform || arrPlatform;
        between = stops.slice(1, -1).map((s) => ({
          name: s.name, time: expectedTime(s, 'arr'), cancelled: !!s.cancelled, pos: s.pos,
        }));
        if (!path) {
          const a = first.pos || leg.from.pos;
          const b = last.pos || leg.to.pos;
          // Cut between the very calls of the ride, each on its own pass of the way (a ring).
          path = data.path && data.path.length > 1
            ? cutPathBetween(data.path, data.stops, i, j) || cutPath(data.path, a, b)
            : stops.map((s) => s.pos).filter(Boolean);
        }
      }
    }
    if (!path || path.length < 2) path = [leg.from.pos, leg.to.pos].filter(Boolean);
    // The ride's own line first: the trip of the provider may name the line its run began as
    // (a night line that goes on as a day line).
    const product = leg.product || (data && data.product) || null;
    const line = canonicalLine(leg.line || (data && data.line) || null, product);
    out.push({
      index,
      walk: leg.walk,
      bike: !!leg.bike,
      line,
      product,
      color: leg.walk ? null : lineColor(line, product, data && data.color, leg.from.pos),
      direction: (data && data.direction) || leg.direction,
      from: leg.from,
      to: leg.to,
      dep,
      arr,
      delayDep: Math.round((dep - leg.from.planned) / 60000),
      delayArr: Math.round((arr - leg.to.planned) / 60000),
      depPlatform,
      arrPlatform,
      depPlatformChanged: !!(leg.from.platform && depPlatform && !samePlatform(leg.from.platform, depPlatform)),
      arrPlatformChanged: !!(leg.to.platform && arrPlatform && !samePlatform(leg.to.platform, arrPlatform)),
      board: leg.board,
      stopCount: liveOk ? between.length : leg.stopCount,
      between,
      meters: leg.meters,
      path,
      live: liveOk,
      realtime,
      cancelled,
      trackable: !leg.walk && !!(leg.trip && leg.provider),
    });
  });
  return out;
}

/**
 * Where the route stands at [now], for someone who travels it:
 * { phase: 'before'|'underway'|'arrived', current, next, start, end, rides, changes, problems }.
 * [current] is the index of the step that runs now (-1 when none); [next] the step to do next.
 * [problems] are what the page must say honestly: a cancelled ride, a change that will likely
 * not work out ('broken') or gets tight, and a first ride that has already left ('missed').
 */
function routeStatus(steps, now) {
  const rides = steps.filter((s) => !s.walk);
  const start = steps[0].dep;
  const end = steps[steps.length - 1].arr;
  let current = -1;
  steps.forEach((s, i) => {
    if (current < 0 && now >= s.dep && now < s.arr) current = i;
  });
  const phase = now < start ? 'before' : now >= end ? 'arrived' : 'underway';
  let next = -1;
  if (phase === 'before') next = 0;
  else if (phase === 'underway') next = steps.findIndex((s, i) => i > current && s.arr > now);
  const problems = [];
  rides.forEach((r) => {
    if (r.cancelled && r.arr > now) problems.push({ kind: 'cancelled', index: r.index, line: r.line, dep: r.dep });
  });
  // Changes: the room between getting off one ride (plus the walk after it) and the next ride.
  for (let k = 1; k < rides.length; k++) {
    const before = rides[k - 1];
    const after = rides[k];
    if (before.cancelled || after.cancelled || after.dep <= now) continue;
    if (!(before.live || after.live)) continue;
    const walking = steps.slice(before.index + 1, after.index).reduce((sum, s) => sum + (s.to.planned - s.from.planned), 0);
    const room = after.dep - (before.arr + walking);
    if (room < 0) problems.push({ kind: 'broken', index: after.index, line: after.line, at: after.from.name, minutes: Math.round(-room / 60000) });
    else if (room < TIGHT_MS) problems.push({ kind: 'tight', index: after.index, line: after.line, at: after.from.name, minutes: Math.round(room / 60000) });
  }
  const first = rides[0];
  if (first && !first.cancelled && phase !== 'arrived' && now > first.dep + MISSED_GRACE_MS) {
    problems.push({ kind: 'missed', index: first.index, line: first.line, dep: first.dep });
  }
  return { phase, current, next, start, end, rides: rides.length, changes: Math.max(0, rides.length - 1), problems };
}

/** The status of each step at [now]: 'done', 'current', 'next' or 'upcoming'. */
function stepStatus(steps, status, now) {
  return steps.map((s, i) => {
    if (status.phase === 'arrived' || s.arr <= now) return 'done';
    if (i === status.current) return 'current';
    if (i === status.next) return 'next';
    return 'upcoming';
  });
}

/**
 * What the page says about real-time data of a ride at [now]: 'cancelled', 'live' (a prognosis
 * is known), 'far' (more than about 30 min ahead: the APIs rarely know anything yet), 'plan'
 * (near, but no prognosis yet), 'none' (no handle to ask for) or 'done'; null for a walk.
 */
function realtimeKind(step, now) {
  if (step.walk) return null;
  if (step.cancelled) return 'cancelled';
  if (step.arr <= now) return 'done';
  if (!step.trackable) return 'none';
  if (step.realtime) return 'live';
  if (step.dep - now > REALTIME_WINDOW_MS) return 'far';
  return 'plan';
}

/** Which rides to ask for live data at [now]: those not over yet that leave within the look-ahead. */
function ridesToPoll(steps, now) {
  return steps
    .filter((s) => s.trackable && s.arr > now - 2 * 60000 && s.dep < now + POLL_AHEAD_MS)
    .map((s) => s.index);
}

/** The wait before the next request of a ride: 30 s seen, 2 min hidden, doubling after failures (max 5 min). */
function pollDelay(hidden, failures) {
  const base = hidden ? POLL_MS.hidden : POLL_MS.visible;
  if (!failures) return base;
  return Math.min(MAX_BACKOFF_MS, Math.max(base, POLL_MS.visible * Math.pow(2, failures)));
}

/* ---------- Links out ---------- */

function latLon(pos) {
  return Number(pos[0].toFixed(5)) + ',' + Number(pos[1].toFixed(5));
}

/**
 * The walk from [from] to [to] (positions [lat, lon]) in Google Maps: only the two points and
 * the travel mode (by bike with [bike]), nothing about the reader or the rest of the route.
 * Null without positions.
 */
function googleWalkUrl(from, to, bike) {
  if (!from || !to) return null;
  return 'https://www.google.com/maps/dir/?api=1&origin=' + encodeURIComponent(latLon(from)) +
    '&destination=' + encodeURIComponent(latLon(to)) + '&travelmode=' + (bike ? 'bicycling' : 'walking');
}

/** A search from [from] to [to] (places with name and pos) on the Transitous website, leaving now. */
function transitousSearchUrl(from, to) {
  const params = new URLSearchParams();
  if (from.pos) params.set('fromPlace', latLon(from.pos));
  params.set('fromName', from.name);
  if (to.pos) params.set('toPlace', latLon(to.pos));
  params.set('toName', to.name);
  return 'https://api.transitous.org/?' + params.toString();
}

/**
 * An Android intent link that opens the app (nextstop://open/route?...) with [from] and [to],
 * and [fallback] in the browser when the app is not installed.
 */
function appSearchUrl(from, to, fallback) {
  const params = new URLSearchParams();
  params.set('fromName', from.name);
  if (from.pos) params.set('from', latLon(from.pos));
  params.set('toName', to.name);
  if (to.pos) params.set('to', latLon(to.pos));
  return appIntentUrl('route', params, fallback || null);
}

/**
 * The hero of the page: the duration as the app writes it ("48′", "1:20 h") and where the first
 * ride leaves ("ab S Hackescher Markt"; null when the route has no ride). A start that is an
 * address stays out of it: only the stop of the first ride is named.
 */
function heroSummary(T, steps, status) {
  const firstRide = steps.find((s) => !s.walk);
  return {
    duration: minutesMark((status.end - status.start) / 60000),
    origin: firstRide ? T.from(firstRide.from.name) : null,
  };
}

/* ---------- Texts ---------- */

const RAIL = ['subway', 'suburban', 'regional', 'express'];

/**
 * "Gleis 2" for rail, "Steig 2" for bus and tram, texts such as "Pos. 11" as they are. Without a
 * product it is guessed from the [line] where the ride runs ([pos]).
 */
function platformText(T, platform, product, line, pos) {
  if (!platform) return null;
  if (!/^\d/.test(platform)) return platform;
  const p = product || core.guessProduct(line, pos);
  return p && RAIL.indexOf(p) < 0 ? T.bay(platform) : T.track(platform);
}

/** "Pos. 3" for the number of a stop position, other labels as they are. */
function spotText(T, spot) {
  if (!spot) return null;
  return /^\d/.test(spot) ? T.spot(spot) : spot;
}

/** "Ausgang A", "Ausgang Terminal 1": the label of a station exit (a letter, number or name). */
function exitText(T, label) {
  return label ? T.exit(label) : null;
}

/** "Eingang B": the label of a station entrance. */
function entranceText(T, label) {
  return label ? T.entrance(label) : null;
}

/** "In der Mitte einsteigen", softer for an estimate. */
function boardText(T, board) {
  if (!board) return null;
  return (board.estimated ? T.boardEstimated : T.board)[board.part];
}

/** "52 min", "1 h 05 min". */
function durationText(T, ms) {
  const minutes = Math.max(0, Math.round(ms / 60000));
  if (minutes < 60) return T.minutes(minutes);
  return T.hours(Math.floor(minutes / 60), minutes % 60);
}

/** "350 m", "1,2 km". */
function metersText(T, m) {
  if (m == null) return null;
  if (m < 1000) return Math.max(10, Math.round(m / 10) * 10) + ' m';
  return (m / 1000).toFixed(1).replace('.', T.decimal) + ' km';
}

const DAY_KEY = typeof Intl !== 'undefined'
  ? new Intl.DateTimeFormat('en-CA', { year: 'numeric', month: '2-digit', day: '2-digit', timeZone: 'Europe/Berlin' })
  : null;
function berlinDay(t) {
  const text = DAY_KEY ? DAY_KEY.format(new Date(t)) : new Date(t).toISOString().slice(0, 10);
  return Date.UTC(Number(text.slice(0, 4)), Number(text.slice(5, 7)) - 1, Number(text.slice(8, 10)));
}

/** "Heute", "Morgen", "Gestern" or a date such as "Sa., 15.11.", by the calendar in Berlin. */
function dayText(T, t, now) {
  const days = Math.round((berlinDay(t) - berlinDay(now)) / 86400000);
  if (days === 0) return T.today;
  if (days === 1) return T.tomorrow;
  if (days === -1) return T.yesterday;
  return new Intl.DateTimeFormat(T.locale, { weekday: 'short', day: 'numeric', month: 'numeric', timeZone: 'Europe/Berlin' }).format(new Date(t));
}

/** "in 23 min", "in 1 h 05 min", "jetzt"; empty once [t] has passed or when it is a day or more away. */
function countdownText(T, t, now) {
  if (t == null || now >= t) return '';
  const minutes = Math.ceil((t - now) / 60000);
  if (minutes <= 1 && t - now < 45000) return T.now;
  if (minutes < 60) return T.inMin(minutes);
  if (minutes < 24 * 60) return T.inHours(Math.floor(minutes / 60), minutes % 60);
  return '';
}

const TEXTS = {
  de: {
    lang: 'de',
    locale: 'de-DE',
    decimal: ',',
    title: 'So kommst du hin',
    to: (d) => 'nach ' + d,
    today: 'Heute',
    tomorrow: 'Morgen',
    yesterday: 'Gestern',
    changes: (n) => (n === 0 ? 'ohne Umstieg' : n === 1 ? '1 Umstieg' : n + ' Umstiege'),
    leaves: (t) => 'Los geht’s ' + t,
    underway: 'Unterwegs – nächster Schritt unten',
    arrivedAt: (t) => 'Ankunft war um ' + t,
    now: 'jetzt',
    inMin: (m) => 'in ' + m + ' min',
    inHours: (h, m) => 'in ' + h + ' h ' + (m < 10 ? '0' : '') + m + ' min',
    minutes: (m) => m + ' min',
    hours: (h, m) => h + ' h ' + (m < 10 ? '0' : '') + m + ' min',
    onTime: 'pünktlich',
    late: (m) => '+' + m + ' min',
    early: (m) => '−' + m + ' min',
    track: (p) => 'Gleis ' + p,
    bay: (p) => 'Steig ' + p,
    spot: (p) => 'Pos. ' + p,
    exit: (p) => 'Ausgang ' + p,
    entrance: (p) => 'Eingang ' + p,
    board: { front: 'Vorne einsteigen', middle: 'In der Mitte einsteigen', rear: 'Hinten einsteigen' },
    boardEstimated: { front: 'Eher vorne einsteigen', middle: 'Eher in der Mitte einsteigen', rear: 'Eher hinten einsteigen' },
    towards: (d) => 'Richtung ' + d,
    from: (s) => 'ab ' + s,
    getOff: 'Aussteigen',
    stops: (n) => (n === 0 ? 'ohne Zwischenhalt' : n === 1 ? '1 Station' : n + ' Stationen'),
    showStops: 'Stationen anzeigen',
    hideStops: 'Stationen ausblenden',
    walk: 'Zu Fuß',
    bike: 'Mit dem Rad',
    walkTo: (s) => 'zu ' + s,
    takeExit: (e) => e + ' nehmen',
    toSpot: (s) => 'bis ' + s,
    walkMaps: 'Weg in Google Maps',
    arrive: 'Ankunft',
    platformChanged: (now, before) => now + ' statt ' + before,
    rtLive: 'Echtzeit',
    rtFar: 'Echtzeit ab ca. 30 min vor Abfahrt',
    rtPlan: 'Noch keine Echtzeit – Fahrplan',
    rtNone: 'Keine Echtzeit für diese Fahrt',
    cancelled: 'Fällt aus',
    probCancelled: (line, t) => 'Die ' + line + ' um ' + t + ' fällt aus.',
    probBroken: (line, at) => 'Der Anschluss an die ' + line + ' in ' + at + ' klappt wohl nicht mehr.',
    probTight: (line, at) => 'Der Umstieg zur ' + line + ' in ' + at + ' wird knapp.',
    probMissed: (line, t) => 'Die ' + line + ' ist um ' + t + ' abgefahren.',
    missedHint: 'Schon unterwegs? Dann folge einfach weiter den Schritten.',
    over: 'Diese Verbindung liegt hinter dir.',
    later: 'Spätere Verbindung suchen',
    laterApp: 'In Next Stop öffnen',
    laterWeb: 'Bei Transitous suchen',
    laterTitle: 'Andere Verbindung',
    fromTo: (a, b) => a + ' → ' + b,
    justNow: 'gerade eben',
    agoS: (s) => 'vor ' + s + ' s',
    agoM: (m) => 'vor ' + m + ' min',
    agoH: (h) => 'vor ' + h + ' h',
    updated: (age) => 'aktualisiert ' + age,
    stale: (m) => 'Keine Live-Daten seit ' + m + ' min',
    noLive: 'Keine Live-Daten – Fahrplanzeiten',
    loading: 'Lädt Live-Daten …',
    planOnly: 'Fahrplanzeiten',
    disclaimer: 'Fahrplan mit Echtzeitdaten der Verkehrsbetriebe, ohne Gewähr. Kein GPS: Die Seite weiß nicht, wo du bist.',
    sources: 'Fahrplandaten',
    openSource: 'Open Source (GPL-3.0)',
    sourceCode: 'Quellcode auf GitHub',
    shared: (day, relative, t) => 'Geteilt ' + (relative ? '' : 'am ') + day + ' um ' + t,
    demo: 'Demo mit einer erfundenen Verbindung',
    demoForward: '+5 min',
    badLink: 'Dieser Link ist ungültig oder unvollständig.',
    badVersion: 'Dieser Link stammt aus einer neueren App-Version.',
    badBrowser: 'Dieser Browser kann den Link nicht öffnen. Bitte aktualisiere ihn oder nimm einen anderen Browser.',
    destination: 'Ziel',
    start: 'Start',
    openApp: 'In Next Stop öffnen',
    getApp: 'Bei Google Play laden',
  },
  en: {
    lang: 'en',
    locale: 'en-GB',
    decimal: '.',
    title: 'How to get there',
    to: (d) => 'to ' + d,
    today: 'Today',
    tomorrow: 'Tomorrow',
    yesterday: 'Yesterday',
    changes: (n) => (n === 0 ? 'direct' : n === 1 ? '1 change' : n + ' changes'),
    leaves: (t) => 'Leaves ' + t,
    underway: 'Under way – next step below',
    arrivedAt: (t) => 'Arrival was at ' + t,
    now: 'now',
    inMin: (m) => 'in ' + m + ' min',
    inHours: (h, m) => 'in ' + h + ' h ' + (m < 10 ? '0' : '') + m + ' min',
    minutes: (m) => m + ' min',
    hours: (h, m) => h + ' h ' + (m < 10 ? '0' : '') + m + ' min',
    onTime: 'on time',
    late: (m) => '+' + m + ' min',
    early: (m) => '−' + m + ' min',
    track: (p) => 'Platform ' + p,
    bay: (p) => 'Bay ' + p,
    spot: (p) => 'Pos. ' + p,
    exit: (p) => 'Exit ' + p,
    entrance: (p) => 'Entrance ' + p,
    board: { front: 'Board at the front', middle: 'Board in the middle', rear: 'Board at the rear' },
    boardEstimated: { front: 'Rather board at the front', middle: 'Rather board in the middle', rear: 'Rather board at the rear' },
    towards: (d) => 'towards ' + d,
    from: (s) => 'from ' + s,
    getOff: 'Get off',
    stops: (n) => (n === 0 ? 'non-stop' : n === 1 ? '1 stop' : n + ' stops'),
    showStops: 'Show stops',
    hideStops: 'Hide stops',
    walk: 'Walk',
    bike: 'By bike',
    walkTo: (s) => 'to ' + s,
    takeExit: (e) => 'Take ' + e,
    toSpot: (s) => 'to ' + s,
    walkMaps: 'Route in Google Maps',
    arrive: 'Arrival',
    platformChanged: (now, before) => now + ' instead of ' + before,
    rtLive: 'Real time',
    rtFar: 'Real time from about 30 min before departure',
    rtPlan: 'No real time yet – timetable',
    rtNone: 'No real time for this ride',
    cancelled: 'Cancelled',
    probCancelled: (line, t) => 'The ' + line + ' at ' + t + ' is cancelled.',
    probBroken: (line, at) => 'The connection to the ' + line + ' at ' + at + ' will likely not work out.',
    probTight: (line, at) => 'The change to the ' + line + ' at ' + at + ' gets tight.',
    probMissed: (line, t) => 'The ' + line + ' left at ' + t + '.',
    missedHint: 'Already on your way? Then just keep following the steps.',
    over: 'This connection is behind you.',
    later: 'Find a later connection',
    laterApp: 'Open in Next Stop',
    laterWeb: 'Search on Transitous',
    laterTitle: 'Another connection',
    fromTo: (a, b) => a + ' → ' + b,
    justNow: 'just now',
    agoS: (s) => s + ' s ago',
    agoM: (m) => m + ' min ago',
    agoH: (h) => h + ' h ago',
    updated: (age) => 'updated ' + age,
    stale: (m) => 'No live data for ' + m + ' min',
    noLive: 'No live data – timetable times',
    loading: 'Loading live data …',
    planOnly: 'Timetable times',
    disclaimer: 'Timetable with real-time data of the operators, no guarantee. No GPS: the page does not know where you are.',
    sources: 'Timetable data',
    openSource: 'Open source (GPL-3.0)',
    sourceCode: 'Source code on GitHub',
    shared: (day, relative, t) => 'Shared ' + (relative ? '' : 'on ') + day + ' at ' + t,
    demo: 'Demo with a made-up connection',
    demoForward: '+5 min',
    badLink: 'This link is invalid or incomplete.',
    badVersion: 'This link comes from a newer app version.',
    badBrowser: 'This browser cannot open the link. Please update it or use another browser.',
    destination: 'Destination',
    start: 'Start',
    openApp: 'Open in Next Stop',
    getApp: 'Get it on Google Play',
  },
};

/** The texts for the browser [language], or the [sender]'s language for any but German and English. */
function pickTexts(language, sender) {
  return TEXTS[pickLanguage(language, sender)];
}

/**
 * The banner of [status]: its lines (the most serious first), its level ('bad', 'warn' or null
 * for none) and whether to offer a later connection.
 */
function bannerModel(T, status) {
  const order = { cancelled: 0, broken: 1, missed: 2, tight: 3 };
  const list = status.problems.slice().sort((a, b) => order[a.kind] - order[b.kind]);
  const lines = list.map((p) => {
    if (p.kind === 'cancelled') return T.probCancelled(p.line || '?', clock(p.dep));
    if (p.kind === 'broken') return T.probBroken(p.line || '?', p.at);
    if (p.kind === 'tight') return T.probTight(p.line || '?', p.at);
    return T.probMissed(p.line || '?', clock(p.dep));
  });
  if (list.some((p) => p.kind === 'missed')) lines.push(T.missedHint);
  if (status.phase === 'arrived') lines.unshift(T.over);
  const level = list.some((p) => p.kind === 'cancelled' || p.kind === 'broken') ? 'bad'
    : lines.length ? 'warn' : null;
  return { level, lines, later: list.some((p) => p.kind !== 'tight') || status.phase === 'arrived' };
}

if (typeof module !== 'undefined' && module.exports) {
  module.exports = {
    parseRoute, readRoute, encodeRoute, routeFragment, computeSteps, routeStatus, stepStatus, realtimeKind, ridesToPoll,
    pollDelay, googleWalkUrl, transitousSearchUrl, appSearchUrl, platformText, spotText, exitText, entranceText,
    boardText, durationText, metersText, dayText, countdownText, bannerModel, pickTexts, samePlatform, TEXTS, heroSummary,
    boardParts, minutesMark, PLAY_URL,
    POLL_MS, REALTIME_WINDOW_MS, encodePolyline, toBase64Url, parseBvgTrip, parseTransitousTrip,
  };
}

/* ---------- Page ---------- */
if (typeof document !== 'undefined') {
  (function page() {
    // The recipient's language; the sender's ("l" in the link) only when it is neither German nor English.
    const T = pickTexts(navigator.language, new URLSearchParams(location.hash.slice(1)).get('l'));
    document.documentElement.lang = T.lang;
    const $ = (id) => document.getElementById(id);
    const reducedMotion = typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches;
    const darkQuery = typeof matchMedia === 'function' ? matchMedia('(prefers-color-scheme: dark)') : null;
    // Elements, the walk icon, line badges and rolling numbers (web/shared/ui.js).
    const { el, icon, badge, rollTo } = window.NextStopUi.create({ reducedMotion });
    // Material icons (Apache-2.0): place, train, open in new.
    const PIN = 'M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5a2.5 2.5 0 0 1 0-5 2.5 2.5 0 0 1 0 5z';
    const DOOR = 'M12 2c-4 0-8 .5-8 4v9.5A3.5 3.5 0 0 0 7.5 19L6 20.5v.5h2.23l2-2H14l2 2h2v-.5L16.5 19a3.5 3.5 0 0 0 3.5-3.5V6c0-3.5-3.58-4-8-4zM7.5 17a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3zm3.5-7H6V6h5v4zm2 0V6h5v4h-5zm3.5 7a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3z';
    const OPEN = 'M19 19H5V5h7V3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14c1.1 0 2-.9 2-2v-7h-2v7zM14 3v2h3.59l-9.83 9.83 1.41 1.41L19 6.41V10h2V3h-7z';

    $('disclaimer').textContent = T.disclaimer;
    $('src-label').textContent = T.sources;
    $('oss-label').textContent = T.openSource;
    $('code-link').textContent = T.sourceCode;
    $('later-title').textContent = T.laterTitle;
    $('title').textContent = T.title;
    $('get-app-label').textContent = T.getApp;
    $('get-app').href = PLAY_URL;

    let demo = null;
    const nowMs = () => (demo ? demo.now() : Date.now());

    if (/^#demo\b/.test(location.hash)) {
      // The demo script is only loaded for "#demo": it fakes the clock and the API answers.
      const script = document.createElement('script');
      script.src = 'demo.js';
      script.onload = () => {
        demo = window.NextStopRouteDemo.create({ routeFragment, encodePolyline, hash: location.hash });
        start(demo.hash);
      };
      script.onerror = () => start('');
      document.head.append(script);
    } else {
      start(location.hash);
    }
    window.addEventListener('hashchange', () => location.reload());

    function start(hash) {
      readRoute(hash).then(run, (e) => {
        $('title').textContent = e.message === 'version' ? T.badVersion : e.message === 'unsupported' ? T.badBrowser : T.badLink;
        document.body.classList.add('broken');
      });
    }

    function run(route) {
      const live = {};
      const nextPoll = {};
      const failures = {};
      let lastOk = null;
      let lastFail = null;
      let polling = false;
      let firstPoll = true;
      let steps = computeSteps(route, live);
      let status = routeStatus(steps, nowMs());
      let selected = -1;
      const rows = [];

      $('dest').textContent = route.dest.name;
      const origin = { name: route.legs[0].from.name, pos: route.legs[0].from.pos };
      const goal = { name: route.dest.name, pos: route.dest.pos };
      $('later-route').textContent = T.fromTo(origin.name, goal.name);
      fillLater($('later-actions'));
      if (/Android/i.test(navigator.userAgent)) {
        // The search of this connection in the app if it is installed; otherwise the store page.
        const open = $('open-app');
        open.textContent = T.openApp;
        open.href = appSearchUrl(origin, goal, PLAY_URL);
        open.hidden = false;
      }

      /* ----- Links to a later connection ----- */
      function fillLater(box) {
        box.replaceChildren();
        const web = transitousSearchUrl(origin, goal);
        // On Android the app opens if it is installed; otherwise the browser goes to Transitous.
        if (/Android/i.test(navigator.userAgent)) {
          const app = el('a', 'button primary', T.laterApp);
          app.href = appSearchUrl(origin, goal, web);
          box.append(app);
        }
        const a = el('a', 'button' + (/Android/i.test(navigator.userAgent) ? '' : ' primary'), T.laterWeb);
        a.href = web;
        a.target = '_blank';
        a.rel = 'noopener noreferrer';
        a.append(icon(OPEN));
        box.append(a);
      }

      /* ----- Map ----- */
      let map = null;
      let mapReady = false;
      let revealStart = null;
      let fitted = false;

      function styleUrl() {
        return darkQuery && darkQuery.matches ? MAP_STYLE.dark : MAP_STYLE.light;
      }
      function lineFeatures(only) {
        const features = [];
        steps.forEach((s) => {
          if (only != null && s.index !== only) return;
          if (s.path.length < 2) return;
          features.push({
            type: 'Feature',
            properties: { walk: s.walk, color: s.walk ? '#5b6680' : s.color || '#1B4FD8', dim: selected >= 0 && selected !== s.index, cancelled: !!s.cancelled },
            geometry: { type: 'LineString', coordinates: s.path.map((p) => [p[1], p[0]]) },
          });
        });
        return features;
      }
      function addRouteLayers(m, dark) {
        const empty = { type: 'FeatureCollection', features: [] };
        m.addSource('route', { type: 'geojson', data: empty });
        m.addLayer({
          id: 'route-casing', type: 'line', source: 'route', filter: ['!=', ['get', 'walk'], true],
          layout: { 'line-cap': 'round', 'line-join': 'round' },
          paint: { 'line-color': dark ? '#0b1020' : '#ffffff', 'line-width': 9, 'line-opacity': ['case', ['get', 'dim'], 0.3, 0.85] },
        });
        m.addLayer({
          id: 'route-ride', type: 'line', source: 'route', filter: ['!=', ['get', 'walk'], true],
          layout: { 'line-cap': 'round', 'line-join': 'round' },
          paint: {
            'line-color': ['case', ['get', 'cancelled'], dark ? '#4b5468' : '#a7afc0', ['get', 'color']],
            'line-width': 5.5,
            'line-opacity': ['case', ['get', 'dim'], 0.3, 1],
          },
        });
        m.addLayer({
          id: 'route-walk', type: 'line', source: 'route', filter: ['==', ['get', 'walk'], true],
          layout: { 'line-cap': 'round' },
          paint: { 'line-color': dark ? '#c9d3ee' : '#5b6680', 'line-width': 3.5, 'line-dasharray': [0.1, 2], 'line-opacity': ['case', ['get', 'dim'], 0.35, 1] },
        });
      }
      function addLayers() {
        if (map.getSource('route')) return;
        const dark = darkQuery && darkQuery.matches;
        addRouteLayers(map, dark);
        map.addSource('stops', { type: 'geojson', data: { type: 'FeatureCollection', features: [] } });
        map.addLayer({
          id: 'stops', type: 'circle', source: 'stops',
          paint: {
            'circle-radius': 5.5,
            'circle-color': dark ? '#121828' : '#ffffff',
            'circle-stroke-color': ['get', 'color'],
            'circle-stroke-width': 3,
            'circle-opacity': ['case', ['get', 'dim'], 0.4, 1],
            'circle-stroke-opacity': ['case', ['get', 'dim'], 0.4, 1],
          },
        });
        mapReady = true;
        drawMap();
      }
      function initMap() {
        if (typeof maplibregl === 'undefined') {
          document.body.classList.add('no-map');
          return;
        }
        const first = steps.find((s) => s.path.length)?.path[0] || route.dest.pos;
        if (!first) {
          document.body.classList.add('no-map');
          return;
        }
        map = new maplibregl.Map({
          container: 'map', style: styleUrl(), center: [first[1], first[0]], zoom: 12,
          attributionControl: { compact: true }, pitchWithRotate: false, dragRotate: false,
          cooperativeGestures: innerWidth < 900,
        });
        map.touchZoomRotate.disableRotation();
        map.on('style.load', addLayers);
        if (darkQuery && darkQuery.addEventListener) {
          darkQuery.addEventListener('change', () => {
            mapReady = false;
            map.setStyle(styleUrl(), { diff: false });
          });
        }
        const startEl = el('div', 'marker-start');
        startEl.title = T.start;
        if (origin.pos) new maplibregl.Marker({ element: startEl }).setLngLat([origin.pos[1], origin.pos[0]]).addTo(map);
        const dest = el('div', 'marker-dest');
        dest.title = T.destination;
        dest.append(el('i'));
        if (route.dest.pos) new maplibregl.Marker({ element: dest, anchor: 'bottom' }).setLngLat([route.dest.pos[1], route.dest.pos[0]]).addTo(map);
      }
      function boundsOf(points) {
        const b = new maplibregl.LngLatBounds();
        points.forEach((p) => p && b.extend([p[1], p[0]]));
        return b;
      }
      function mapPadding() {
        const box = $('map').getBoundingClientRect();
        const pad = Math.max(24, Math.min(56, Math.round(Math.min(box.width, box.height) * 0.12)));
        return { top: pad, bottom: pad, left: pad, right: pad };
      }
      function drawMap() {
        if (!map || !mapReady) return;
        const t = performance.now();
        if (revealStart === null) revealStart = reducedMotion ? t - ROUTE_DRAW_MS - 100 : t;
        const reveal = easeOutCubic((t - revealStart) / ROUTE_DRAW_MS);
        let features = lineFeatures(null);
        if (reveal < 1) features = snakeFeatures(features, reveal);
        map.getSource('route').setData({ type: 'FeatureCollection', features });
        const stops = [];
        steps.forEach((s) => {
          if (s.walk) return;
          [s.from.pos, s.to.pos].forEach((p) => p && stops.push({
            type: 'Feature', properties: { color: s.color || '#1B4FD8', dim: selected >= 0 && selected !== s.index },
            geometry: { type: 'Point', coordinates: [p[1], p[0]] },
          }));
        });
        map.getSource('stops').setData({ type: 'FeatureCollection', features: reveal < 1 ? [] : stops });
        if (!fitted) {
          fitted = true;
          const b = boundsOf(steps.flatMap((s) => s.path).concat([route.dest.pos]));
          if (!b.isEmpty()) map.fitBounds(b, { padding: mapPadding(), duration: 0, maxZoom: 15 });
        }
        if (reveal < 1) requestAnimationFrame(drawMap);
      }
      /** Tapping a step: the map zooms to it and the other steps fade a little. */
      function focusStep(index) {
        selected = selected === index ? -1 : index;
        rows.forEach((r) => r.li.classList.toggle('selected', r.index === selected));
        if (!map) return;
        drawMap();
        const points = selected >= 0 ? steps[selected].path : steps.flatMap((s) => s.path).concat([route.dest.pos]);
        const b = boundsOf(points);
        if (b.isEmpty()) return;
        const walk = selected >= 0 && steps[selected].walk;
        map.fitBounds(b, { padding: mapPadding(), maxZoom: walk ? 17.5 : 15.5, duration: reducedMotion ? 0 : 800 });
        // On a phone the map is above the steps: bring it into view if it scrolled away.
        const box = $('map').getBoundingClientRect();
        if (box.bottom < 0 || box.top > innerHeight) $('map').scrollIntoView({ behavior: reducedMotion ? 'auto' : 'smooth', block: 'start' });
      }

      /* ----- Mini maps of the walks: made when seen, removed when gone, so few run at a time ----- */
      const minis = new Map(); // node -> map
      // Made once the map library is there (see startMinis); until then the mini maps wait hidden.
      let observer = null;
      const waitingMinis = [];
      function watchMini(node) {
        if (!observer) {
          waitingMinis.push(node);
          return;
        }
        node.hidden = false;
        observer.observe(node);
      }
      function startMinis() {
        if (observer || typeof IntersectionObserver !== 'function') return;
        observer = new IntersectionObserver((entries) => entries.forEach((e) => (e.isIntersecting ? openMini(e.target) : closeMini(e.target))), { rootMargin: '120px' });
        waitingMinis.splice(0).forEach(watchMini);
      }
      function openMini(node) {
        if (minis.has(node)) return;
        const s = steps[Number(node.dataset.index)];
        if (!s || s.path.length < 2) return;
        const dark = darkQuery && darkQuery.matches;
        const mini = new maplibregl.Map({
          container: node, style: styleUrl(), interactive: false, attributionControl: { compact: true },
          bounds: boundsOf(s.path), fitBoundsOptions: { padding: 28, maxZoom: 17.5 },
        });
        mini.on('style.load', () => {
          addRouteLayers(mini, dark);
          mini.getSource('route').setData({ type: 'FeatureCollection', features: lineFeatures(s.index).map((f) => Object.assign(f, { properties: Object.assign({}, f.properties, { dim: false }) })) });
        });
        const a = el('div', 'marker-start');
        new maplibregl.Marker({ element: a }).setLngLat([s.path[0][1], s.path[0][0]]).addTo(mini);
        const end = s.path[s.path.length - 1];
        const b = el('div', 'marker-end');
        new maplibregl.Marker({ element: b }).setLngLat([end[1], end[0]]).addTo(mini);
        minis.set(node, mini);
      }
      function closeMini(node) {
        const mini = minis.get(node);
        if (!mini) return;
        mini.remove();
        minis.delete(node);
      }

      /* ----- Steps ----- */
      function line(cls, ...parts) {
        const p = el('p', cls);
        parts.filter((x) => x != null && x !== '').forEach((x) => p.append(x));
        return p;
      }
      function buildSteps() {
        const list = $('steps');
        list.replaceChildren();
        steps.forEach((s, i) => {
          const li = el('li', 'step ' + (s.walk ? 'walk' : 'ride'));
          li.style.setProperty('--line', s.walk ? 'var(--muted)' : s.color || 'var(--blue)');
          if (!reducedMotion) {
            li.classList.add('enter');
            li.style.setProperty('--i', String(Math.min(i, 8)));
          }
          const head = el('button', 'step-head');
          head.type = 'button';
          const time = el('span', 'step-time');
          const title = el('span', 'step-title');
          const delay = el('span', 'delay');
          head.append(time, badge(s), title, delay);
          head.addEventListener('click', () => focusStep(s.index));
          li.append(head);
          const body = el('div', 'step-body');
          const r = { index: s.index, li, time, title, delay, body };
          if (s.walk) {
            const exit = exitText(T, s.from.exit);
            if (exit) body.append(line('where', T.takeExit(exit)));
            const target = entranceText(T, s.to.entrance) || spotText(T, s.to.spot);
            r.where = line('where', el('b', '', T.walkTo(s.to.name)), target ? ' · ' + target : '');
            body.append(r.where);
            if (s.path.length >= 2 && typeof IntersectionObserver === 'function') {
              const mini = el('div', 'mini');
              mini.dataset.index = String(s.index);
              mini.setAttribute('aria-hidden', 'true');
              mini.hidden = true;
              body.append(mini);
              watchMini(mini);
            }
            const url = googleWalkUrl(s.path[0] || s.from.pos, s.path[s.path.length - 1] || s.to.pos, s.bike);
            if (url) {
              const a = el('a', 'button', T.walkMaps);
              a.href = url;
              a.target = '_blank';
              a.rel = 'noopener noreferrer';
              a.append(icon(OPEN));
              body.append(line('actions', a));
            }
          } else {
            r.where = el('p', 'where');
            r.hint = el('p', 'hint');
            r.info = el('p', 'info');
            r.toggle = el('button', 'more-btn');
            r.toggle.type = 'button';
            r.between = el('ol', 'between');
            r.between.hidden = true;
            r.toggle.addEventListener('click', () => {
              r.between.hidden = !r.between.hidden;
              r.toggle.textContent = r.between.hidden ? T.showStops : T.hideStops;
            });
            r.rt = el('p', 'rt');
            r.off = el('p', 'off');
            body.append(r.where, r.hint, r.info, r.between, r.rt, r.off);
          }
          li.append(body);
          list.append(li);
          rows.push(r);
        });
        // The arrival as the last step: the rail comes down to its pin in the way of the last step.
        const lastStep = steps[steps.length - 1];
        const li = el('li', 'step arrive' + (lastStep && lastStep.walk ? ' after-walk' : ''));
        li.style.setProperty('--line', !lastStep || lastStep.walk ? 'var(--muted)' : lastStep.color || 'var(--blue)');
        if (!reducedMotion) {
          li.classList.add('enter');
          li.style.setProperty('--i', String(Math.min(steps.length, 8)));
        }
        const head = el('div', 'step-head');
        const time = el('span', 'step-time');
        const pin = el('span', 'badge pin');
        pin.append(icon(PIN));
        head.append(time, pin, el('span', 'step-title', T.arrive + ' · ' + route.dest.name));
        li.append(head);
        list.append(li);
        rows.arrive = { li, time };
      }

      function platformSpan(s, which) {
        const now = which === 'dep' ? s.depPlatform : s.arrPlatform;
        const before = which === 'dep' ? s.from.platform : s.to.platform;
        const changed = which === 'dep' ? s.depPlatformChanged : s.arrPlatformChanged;
        const text = platformText(T, now, s.product, s.line, s.from && s.from.pos);
        if (!text) return null;
        if (!changed) return el('span', 'plat', text);
        return el('span', 'plat changed', T.platformChanged(text, before));
      }

      function drawSteps(now) {
        const marks = stepStatus(steps, status, now);
        rows.forEach((r) => {
          const s = steps[r.index];
          r.li.className = r.li.className.replace(/\b(done|current|next|upcoming|cancelled)\b/g, '').replace(/\s+/g, ' ').trim() + ' ' + marks[r.index] + (s.cancelled ? ' cancelled' : '');
          rollTo(r.time, clock(s.dep));
          r.time.classList.toggle('late', s.delayDep > 0 && !s.walk);
          if (s.walk) {
            r.title.textContent = [s.bike ? T.bike : T.walk, durationText(T, s.arr - s.dep), metersText(T, s.meters)].filter(Boolean).join(' · ');
            r.delay.hidden = true;
            return;
          }
          r.title.textContent = s.direction ? T.towards(s.direction) : s.line || '';
          const showDelay = s.live && s.realtime;
          r.delay.hidden = !showDelay && !s.cancelled;
          r.delay.textContent = s.cancelled ? T.cancelled : delayText(T, s.delayDep);
          r.delay.className = 'delay ' + (s.cancelled ? 'bad' : delayLevel(s.delayDep));
          r.where.replaceChildren(el('b', '', T.from(s.from.name)));
          const plat = platformSpan(s, 'dep');
          if (plat) r.where.append(' · ', plat);
          // A stop position the platform already names ("Pos. 3") is said once.
          const spot = spotText(T, s.from.spot);
          if (spot && spot !== platformText(T, s.depPlatform, s.product, s.line, s.from && s.from.pos)) r.where.append(' · ', spot);
          const hint = boardText(T, s.board);
          r.hint.hidden = !hint;
          if (hint) {
            // Where to board, as the app shows it: the text and the part of the train.
            const cars = el('span', 'cars');
            cars.setAttribute('aria-hidden', 'true');
            boardParts(s.board.part).forEach((on) => cars.append(el('i', on ? 'on' : '')));
            r.hint.replaceChildren(icon(DOOR), el('span', 'hint-text', hint), cars);
            r.hint.classList.toggle('estimated', !!s.board.estimated);
          }
          const info = [s.stopCount != null ? T.stops(s.stopCount) : null, durationText(T, s.arr - s.dep)].filter(Boolean).join(' · ');
          r.info.replaceChildren(document.createTextNode(info));
          if (s.between.length) {
            r.toggle.textContent = r.between.hidden ? T.showStops : T.hideStops;
            r.info.append(' ', r.toggle);
            const sig = JSON.stringify(s.between.map((b) => [b.name, b.time, b.cancelled]));
            if (r.between.dataset.sig !== sig) {
              r.between.dataset.sig = sig;
              r.between.replaceChildren(...s.between.map((b) => {
                const item = el('li', b.cancelled ? 'cancelled' : '');
                item.append(el('span', 'time', clock(b.time)), el('span', 'name', b.name));
                return item;
              }));
            }
          }
          const kind = realtimeKind(s, now);
          const rtText = { live: T.rtLive, far: T.rtFar, plan: T.rtPlan, none: T.rtNone, cancelled: T.cancelled }[kind];
          r.rt.hidden = !rtText;
          r.rt.className = 'rt ' + (kind || '');
          r.rt.textContent = rtText || '';
          r.off.replaceChildren(el('span', 'time' + (s.delayArr > 0 && s.realtime ? ' late' : ''), clock(s.arr)), el('span', '', T.getOff + ': '), el('b', '', s.to.name));
          const platOff = platformSpan(s, 'arr');
          if (platOff) r.off.append(' · ', platOff);
          const spotOff = spotText(T, s.to.spot);
          if (spotOff && spotOff !== platformText(T, s.arrPlatform, s.product, s.line, s.from && s.from.pos)) r.off.append(' · ', spotOff);
        });
        const last = steps[steps.length - 1];
        rollTo(rows.arrive.time, clock(last.arr));
        rows.arrive.li.classList.toggle('done', status.phase === 'arrived');
      }

      /* ----- Header and banner ----- */
      function drawHeader(now) {
        $('day').textContent = dayText(T, status.start, now);
        $('times').textContent = clock(status.start) + ' – ' + clock(status.end);
        let lead = '';
        if (status.phase === 'before') {
          const c = countdownText(T, status.start, now);
          lead = c ? T.leaves(c) : '';
        } else if (status.phase === 'underway') lead = T.underway;
        else lead = T.arrivedAt(clock(status.end));
        rollTo($('countdown'), lead);
        $('summary').textContent = T.changes(status.changes);
        const hero = heroSummary(T, steps, status);
        $('duration').textContent = hero.duration || '';
        $('duration').classList.toggle('long', (hero.duration || '').length > 4);
        $('origin').textContent = hero.origin || '';
        $('origin').hidden = !hero.origin;
        document.title = T.title + ' · ' + route.dest.name + ' · Next Stop';
        document.body.dataset.phase = status.phase;

        // The live dot: fresh, stale or none; far ahead it says when real time starts.
        const pollable = steps.some((s) => s.trackable && s.arr > now);
        const firstTracked = steps.find((s) => s.trackable && s.arr > now);
        let f = freshness(lastOk, lastFail, pollable, now);
        let text = freshnessText(T, f);
        if (firstTracked && firstTracked.dep - now > REALTIME_WINDOW_MS && !firstTracked.realtime && f.kind !== 'stale') {
          f = { kind: 'plan' };
          text = T.rtFar;
        }
        const liveEl = $('live');
        liveEl.className = 'live chip-live ' + f.kind;
        liveEl.hidden = status.phase === 'arrived';
        $('fresh').textContent = text;

        const banner = bannerModel(T, status);
        const box = $('banner');
        box.hidden = !banner.level;
        if (banner.level) {
          box.className = 'banner card ' + banner.level;
          const sig = banner.lines.join('|') + banner.later;
          if (box.dataset.sig !== sig) {
            box.dataset.sig = sig;
            $('banner-text').replaceChildren(...banner.lines.map((l) => el('span', '', l)));
            const actions = $('banner-actions');
            if (banner.later) {
              const later = el('button', 'button primary', T.later);
              later.type = 'button';
              later.addEventListener('click', () => {
                $('later').scrollIntoView({ behavior: reducedMotion ? 'auto' : 'smooth', block: 'center' });
                $('later').classList.add('flash');
                setTimeout(() => $('later').classList.remove('flash'), 1200);
              });
              actions.replaceChildren(later);
            } else actions.replaceChildren();
          }
        }
        const notes = [];
        if (demo) notes.push(T.demo);
        if (route.created) {
          const day = dayText(T, route.created, now);
          const relative = day === T.today || day === T.yesterday || day === T.tomorrow;
          notes.push(T.shared(relative ? day.toLowerCase() : day, relative, clock(route.created)));
        }
        $('note').textContent = notes.join(' · ');
      }

      function render() {
        const now = nowMs();
        steps = computeSteps(route, live);
        status = routeStatus(steps, now);
        drawHeader(now);
        drawSteps(now);
      }

      /* ----- Live data ----- */
      async function fetchLeg(index) {
        const leg = route.legs[index];
        const controller = typeof AbortController !== 'undefined' ? new AbortController() : null;
        const timeout = setTimeout(() => controller && controller.abort(), 15000);
        try {
          const options = {
            signal: controller ? controller.signal : undefined,
            headers: { Accept: 'application/json' },
            referrerPolicy: 'strict-origin-when-cross-origin',
          };
          const response = await (demo ? demo.fetch(tripUrl(leg), options) : fetch(tripUrl(leg), options));
          if (response.status === 429) throw new Error('rate');
          if (!response.ok) throw new Error('http ' + response.status);
          const json = await response.json();
          live[index] = leg.provider === 'tr' ? parseTransitousTrip(json) : parseBvgTrip(json);
          failures[index] = 0;
          lastOk = nowMs();
          return true;
        } catch (e) {
          // Network errors, a blocked request (CORS) or a refusal: wait longer each time.
          failures[index] = (failures[index] || 0) + 1;
          lastFail = nowMs();
          return false;
        } finally {
          nextPoll[index] = nowMs() + pollDelay(document.hidden, failures[index]);
          clearTimeout(timeout);
        }
      }

      async function poll() {
        if (polling) return;
        polling = true;
        try {
          const now = nowMs();
          // At the first load every ride is asked for once: its way, stops and cancellations.
          const due = firstPoll
            ? steps.filter((s) => s.trackable).map((s) => s.index)
            : ridesToPoll(steps, now).filter((i) => !(nextPoll[i] > now));
          for (const i of due) await fetchLeg(i); // one after another, gently
        } finally {
          const wasFirst = firstPoll;
          firstPoll = false;
          polling = false;
          render();
          if (wasFirst) fitted = false;
          drawMap();
        }
      }

      document.addEventListener('visibilitychange', () => {
        if (document.hidden) return;
        // Back on screen: ask at once, then every 30 s again.
        Object.keys(nextPoll).forEach((k) => {
          nextPoll[k] = 0;
        });
        poll();
        render();
      });

      if (demo) {
        $('demo-bar').hidden = false;
        document.body.classList.add('demo');
        $('demo-label').textContent = T.demo;
        $('demo-forward').textContent = T.demoForward;
        $('demo-forward').addEventListener('click', () => {
          demo.forward(5 * 60000);
          Object.keys(nextPoll).forEach((k) => {
            nextPoll[k] = 0;
          });
          render();
          poll();
        });
      }

      buildSteps();
      render();
      // The map library comes last: the steps work without it.
      window.NextStopUi.loadMapLibrary(() => {
        initMap();
        startMinis();
      }, () => document.body.classList.add('no-map'));
      poll();
      setInterval(() => {
        if (!document.hidden) render();
        poll();
      }, demo ? 500 : 1000);
    }
  })();
}
