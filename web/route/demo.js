/*
 * Demo of the route page ("#demo"): a made-up connection across Berlin (Alexanderplatz →
 * Ostkreuz → Flughafen BER) with fake answers in the form of the BVG API, so the page can be
 * looked at without any request to a timetable API. Only loaded by app.js for "#demo".
 *
 * Options in the fragment: #demo&at=-12&speed=1&late=1&cancel=1&tomorrow=1
 *   at        minute of the trip to start at (default -12: twelve minutes before it starts;
 *             8 in the S5, 16 at the change, 50 arrived)
 *   speed     how many seconds pass per real second (default 1; the page has a +5 min button)
 *   late      the S5 runs 6 min late, so the change to the FEX at Ostkreuz breaks
 *   cancel    the FEX is cancelled
 *   tomorrow  the trip starts tomorrow at 08:12 (real time only from about 30 min before)
 */
'use strict';
(function () {
  // Stops with [name, id, lat, lon, minute of the trip].
  const S5 = [
    ['S+U Alexanderplatz', '900100003', 52.52151, 13.41127, 5],
    ['S+U Jannowitzbrücke', '900100004', 52.51504, 13.41812, 7],
    ['S Ostbahnhof', '900120005', 52.51035, 13.43489, 9],
    ['S+U Warschauer Str.', '900120004', 52.50597, 13.44965, 11],
    ['S Ostkreuz', '900120003', 52.50303, 13.46902, 14],
  ];
  const FEX = [
    ['S Ostkreuz', '900120003', 52.50281, 13.46985, 20],
    ['Flughafen BER', '900260005', 52.36443, 13.50970, 38],
  ];
  // The way of the FEX: along the Görlitzer Bahn and the airport link, roughly.
  const FEX_WAY = [[52.50281, 13.46985], [52.4930, 13.4800], [52.4770, 13.4990], [52.4550, 13.5110], [52.4310, 13.5300], [52.4030, 13.5260], [52.3800, 13.5150], [52.36443, 13.50970]];

  /** Epoch ms of 08:12 tomorrow in Berlin, whatever the zone of the browser. */
  function tomorrowAt812(nowMs) {
    const parts = new Intl.DateTimeFormat('en-CA', { year: 'numeric', month: '2-digit', day: '2-digit', timeZone: 'Europe/Berlin' })
      .format(new Date(nowMs + 86400000)).split('-').map(Number);
    const guess = Date.UTC(parts[0], parts[1] - 1, parts[2], 8, 12);
    const zone = new Intl.DateTimeFormat('en-US', { timeZone: 'Europe/Berlin', timeZoneName: 'shortOffset' })
      .formatToParts(new Date(guess)).find((p) => p.type === 'timeZoneName').value; // "GMT+1"
    const m = /GMT([+-]\d+)(?::(\d+))?/.exec(zone);
    const offset = m ? Number(m[1]) * 60 + Math.sign(Number(m[1])) * Number(m[2] || 0) : 60;
    return guess - offset * 60000;
  }

  function create(helpers) {
    const opts = new URLSearchParams(String(helpers.hash || '').replace(/^#demo&?/, ''));
    const tomorrow = opts.get('tomorrow') === '1';
    const late = opts.get('late') === '1';
    const cancel = opts.get('cancel') === '1';
    const speed = Math.max(1, Math.min(600, Number(opts.get('speed')) || 1));
    const realStart = (helpers.clock || Date.now)();
    let skipped = 0;
    let t0;
    let at;
    if (tomorrow) {
      // Starts with a walk of 5 minutes, so the S5 leaves at 08:12.
      t0 = tomorrowAt812(realStart) - 5 * 60000;
      at = (realStart - t0) / 60000;
    } else {
      at = opts.has('at') ? Number(opts.get('at')) || 0 : -12;
      t0 = Math.round(realStart / 60000) * 60000 - at * 60000;
    }
    const realNow = helpers.clock || Date.now;
    const now = () => t0 + at * 60000 + (realNow() - realStart) * speed + skipped;
    const minute = (m) => t0 + m * 60000;
    const sec = (m) => Math.round(minute(m) / 1000);

    const RIDES = {
      'demo-s5': { stops: S5, line: 'S5', product: 'suburban', direction: 'Strausberg Nord', way: null,
        delay: () => (late ? 6 : 2), platforms: ['2', '1', '2', '2', '3'], changed: {} },
      'demo-fex': { stops: FEX, line: 'FEX', product: 'regional', direction: 'Flughafen BER', way: FEX_WAY,
        delay: () => 0, platforms: ['5', '2'], changed: { 0: '6' }, cancelled: cancel },
    };

    const route = {
      t: Math.round(realStart / 1000) - 180,
      dest: { name: 'Flughafen BER, Terminal 1', lat: 52.3655, lon: 13.5101 },
      legs: [
        { walk: true, m: 180,
          from: { name: 'Alexanderplatz, Weltzeituhr', lat: 52.52107, lon: 13.41334, dep: sec(0) },
          to: { name: 'S+U Alexanderplatz', id: '900100003', lat: 52.52151, lon: 13.41127, arr: sec(4), entr: 'B' },
          poly: helpers.encodePolyline([[52.52107, 13.41334], [52.52160, 13.41290], [52.52185, 13.41205], [52.52151, 13.41127]], 5) },
        { p: 'bvg', trip: 'demo-s5', line: 'S5', prod: 'suburban', dir: 'Strausberg Nord', n: 3, b: 'm',
          from: { name: 'S+U Alexanderplatz', id: '900100003', lat: 52.52151, lon: 13.41127, dep: sec(5), pl: '2' },
          to: { name: 'S Ostkreuz', id: '900120003', lat: 52.50303, lon: 13.46902, arr: sec(14), pl: '3' } },
        { walk: true, m: 240,
          from: { name: 'S Ostkreuz', lat: 52.50303, lon: 13.46902, dep: sec(14) },
          to: { name: 'S Ostkreuz', id: '900120003', lat: 52.50281, lon: 13.46985, arr: sec(18) },
          poly: helpers.encodePolyline([[52.50303, 13.46902], [52.50322, 13.46950], [52.50300, 13.46995], [52.50281, 13.46985]], 5) },
        { p: 'bvg', trip: 'demo-fex', line: 'FEX', prod: 'regional', dir: 'Flughafen BER', n: 0, b: 'r', be: true,
          from: { name: 'S Ostkreuz', id: '900120003', lat: 52.50281, lon: 13.46985, dep: sec(20), pl: '5' },
          to: { name: 'Flughafen BER', id: '900260005', lat: 52.36443, lon: 13.50970, arr: sec(38), pl: '2' } },
        { walk: true, m: 350,
          from: { name: 'Flughafen BER', lat: 52.36443, lon: 13.50970, dep: sec(38), exit: 'Terminal 1' },
          to: { name: 'Terminal 1, Abflug', lat: 52.3655, lon: 13.5101, arr: sec(43) },
          poly: helpers.encodePolyline([[52.36443, 13.50970], [52.36480, 13.50960], [52.36520, 13.50990], [52.3655, 13.5101]], 5) },
      ],
    };

    /** An answer like v6.bvg.transport.rest/trips/:id?polyline=true&stopovers=true. */
    function trip(id) {
      const r = RIDES[id];
      const t = now();
      // As the real APIs: a prognosis only from about half an hour before the ride leaves.
      const prognosis = t >= minute(r.stops[0][4]) - 30 * 60000;
      const delay = r.delay() * 60000;
      const iso = (x) => new Date(x).toISOString();
      const stopovers = r.stops.map((s, i) => {
        const planned = minute(s[4]);
        const last = i === r.stops.length - 1;
        const first = i === 0;
        const platform = r.platforms[i];
        const now = prognosis && r.changed[i] ? r.changed[i] : platform;
        return {
          stop: { type: 'stop', id: s[1], name: s[0], location: { type: 'location', latitude: s[2], longitude: s[3] } },
          plannedArrival: first ? null : iso(planned - 30000),
          arrival: first ? null : iso(planned - 30000 + (prognosis ? delay : 0)),
          arrivalDelay: first || !prognosis ? null : delay / 1000,
          plannedArrivalPlatform: first ? null : platform,
          arrivalPlatform: first ? null : now,
          plannedDeparture: last ? null : iso(planned),
          departure: last ? null : iso(planned + (prognosis ? delay : 0)),
          departureDelay: last || !prognosis ? null : delay / 1000,
          plannedDeparturePlatform: last ? null : platform,
          departurePlatform: last ? null : now,
          cancelled: !!r.cancelled,
        };
      });
      // A slightly bent way between the stops, as a real polyline would be.
      const points = r.way || r.stops.reduce((all, s, i) => {
        all.push([s[2], s[3]]);
        const n = r.stops[i + 1];
        if (n) all.push([(s[2] + n[2]) / 2 + (n[3] - s[3]) * 0.1, (s[3] + n[3]) / 2 - (n[2] - s[2]) * 0.1]);
        return all;
      }, []);
      const features = points.map((p) => ({ type: 'Feature', properties: {}, geometry: { type: 'Point', coordinates: [p[1], p[0]] } }));
      return {
        trip: {
          id, direction: r.direction, cancelled: !!r.cancelled,
          line: { type: 'line', name: r.line, product: r.product },
          stopovers,
          polyline: { type: 'FeatureCollection', features },
        },
      };
    }

    function fakeFetch(url) {
      const m = /\/trips\/(demo-[a-z0-9]+)/.exec(url);
      return new Promise((resolve) => {
        setTimeout(() => {
          if (!m || !RIDES[m[1]]) {
            resolve({ ok: false, status: 404, json: () => Promise.resolve({}) });
            return;
          }
          const body = trip(m[1]);
          resolve({ ok: true, status: 200, json: () => Promise.resolve(body) });
        }, 200);
      });
    }

    return {
      hash: helpers.routeFragment(route),
      now,
      fetch: fakeFetch,
      /** Fast forward by [ms] of the trip. */
      forward(ms) {
        skipped += ms;
      },
    };
  }

  const api = { create };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else window.NextStopRouteDemo = api;
})();
