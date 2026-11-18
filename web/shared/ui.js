/*
 * Next Stop web pages: small DOM helpers both pages share (web/live and web/route): elements,
 * the walk icon, line badges and numbers that roll when they change. Browser only; loaded as a
 * classic script after core.js (window.NextStopUi).
 */
(function (root) {
  'use strict';
  const SVG = 'http://www.w3.org/2000/svg';
  // Material "directions_walk" (Apache-2.0).
  const WALK_PATH = 'M13.5 5.5c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zM9.8 8.9L7 23h2.1l1.8-8 2.1 2v6h2v-7.5l-2.1-2 .6-3C14.8 12 16.8 13 19 13v-2c-1.9 0-3.5-1-4.3-2.4l-1-1.6c-.4-.6-1-1-1.7-1-.3 0-.5.1-.8.1L6 8.3V13h2V9.6l1.8-.7';

  /** The helpers; [reducedMotion] turns rolling numbers into plain text changes. */
  function create(options) {
    const core = root.NextStopCore;
    const reducedMotion = !!(options && options.reducedMotion);

    function el(tag, cls, text) {
      const node = document.createElement(tag);
      if (cls) node.className = cls;
      if (text != null) node.textContent = text;
      return node;
    }

    function icon(d) {
      const svg = document.createElementNS(SVG, 'svg');
      svg.setAttribute('viewBox', '0 0 24 24');
      svg.setAttribute('aria-hidden', 'true');
      const path = document.createElementNS(SVG, 'path');
      path.setAttribute('d', d);
      path.setAttribute('fill', 'currentColor');
      svg.append(path);
      return svg;
    }

    function walkIcon() {
      return icon(WALK_PATH);
    }

    /** A line badge in the colour of [leg] (line, color), or the walker for a walk. */
    function badge(leg, extra) {
      const b = el('span', 'badge' + (extra ? ' ' + extra : ''));
      if (leg.walk) {
        b.classList.add('walk');
        b.append(walkIcon());
      } else {
        b.textContent = leg.line || '?';
        b.style.background = leg.color || '#5A6072';
        b.style.color = core.inkOn(leg.color);
      }
      return b;
    }

    // A number that changes rolls: the new text slides in from below (counting down) or
    // above (growing) with a spring, the old one leaves the other way.
    function rollTo(node, text) {
      if (node.dataset.text === text) return;
      const before = node.dataset.text;
      node.dataset.text = text;
      if (reducedMotion || before === undefined || before === '' || text === '') {
        node.replaceChildren(document.createTextNode(text));
        return;
      }
      const dir = core.rollsUp(before, text) ? 'up' : 'down';
      const out = el('span', 'roll-out ' + dir, before);
      const into = el('span', 'roll-in ' + dir, text);
      out.setAttribute('aria-hidden', 'true');
      node.replaceChildren(out, into);
      node.classList.add('rolling');
      setTimeout(() => {
        if (node.dataset.text !== text) return;
        node.classList.remove('rolling');
        node.replaceChildren(document.createTextNode(text));
      }, 600);
    }

    return { el, icon, walkIcon, badge, rollTo };
  }

  // The map library, served by the site itself (shared/vendor, BSD-3-Clause, see
  // shared/vendor/LICENSE-maplibre-gl.txt): no third party sees who opens a link.
  const MAPLIBRE_SRC = '../shared/vendor/maplibre-gl.js';
  let mapLibrary = null;

  /**
   * Loads MapLibre GL JS once, after the page logic has started, and calls [ready] when it is
   * there, [failed] (optional) when it cannot be loaded: the page then works without its map.
   */
  function loadMapLibrary(ready, failed) {
    if (typeof root.maplibregl !== 'undefined') {
      ready();
      return;
    }
    if (!mapLibrary) {
      mapLibrary = new Promise((resolve) => {
        const script = document.createElement('script');
        script.src = MAPLIBRE_SRC;
        script.async = true;
        script.onload = () => resolve(typeof root.maplibregl !== 'undefined');
        script.onerror = () => resolve(false);
        document.head.append(script);
      });
    }
    mapLibrary.then((ok) => {
      if (!ok) {
        if (failed) failed();
        return;
      }
      try {
        ready();
      } catch (e) {
        // A map that fails (no WebGL, say) leaves the rest of the page as it is.
      }
    });
  }

  root.NextStopUi = { create, loadMapLibrary };
})(typeof globalThis !== 'undefined' ? globalThis : this);
