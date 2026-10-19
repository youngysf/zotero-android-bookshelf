/*
 * Bookshelf: text selection for PDFs, drawn by the app instead of Chrome.
 *
 * Chrome's own selection handles work on the DOM, and a PDF's text layer is a
 * pile of absolutely positioned spans in an arbitrary order, so the handles
 * jump around whenever a finger is over blank space. This replaces them:
 *
 *  - Long-press a word to select it; keep the finger down and drag to extend.
 *  - Two pins of our own; dragging a pin follows the letters nearest the
 *    finger, using the character positions Zotero already extracts from the
 *    PDF (the same data the desktop app uses for mouse selection).
 *  - A small menu (Highlight / Underline / Copy) that calls the app.
 *  - Tap anywhere on the page to clear.
 *
 * The selection itself is Zotero's (view._selectionRanges), so the reader
 * draws it and tells the app about it exactly as for a mouse selection, and
 * the app's highlight/underline/copy code is unchanged.
 */
(function () {
  if (window.__bookshelfSelection) return;
  window.__bookshelfSelection = true;

  var LONG_PRESS_MS = 420;
  var TOUCH_SLOP = 10;
  var EDGE_SCROLL_ZONE = 56;
  var PIN = 22;
  var LOUPE_W = 150, LOUPE_H = 62, LOUPE_ZOOM = 1.5;

  // ---------------------------------------------------------------- UI (top document)

  var css = document.createElement('style');
  css.textContent = [
    '#bs-sel{position:fixed;left:0;top:0;width:0;height:0;z-index:2147483000;}',
    '.bs-pin,.bs-menu{pointer-events:auto;}',
    '.bs-pin{position:fixed;width:44px;height:44px;margin:0;padding:0;border:0;background:transparent;',
    '  touch-action:none;-webkit-tap-highlight-color:transparent;display:none;}',
    '.bs-pin::after{content:"";position:absolute;top:0;width:' + PIN + 'px;height:' + PIN + 'px;',
    '  background:var(--bs-accent,#2F6FEB);border-radius:50%;box-shadow:0 1px 2px rgba(0,0,0,.25);}',
    '.bs-pin.start::after{right:0;border-top-right-radius:0;}',
    '.bs-pin.end::after{left:0;border-top-left-radius:0;}',
    '.bs-menu{position:fixed;display:none;align-items:stretch;height:44px;border-radius:22px;padding:0 6px;',
    '  background:#fff;color:#1d1d1f;box-shadow:0 2px 10px rgba(0,0,0,.18),0 0 0 .5px rgba(0,0,0,.08);',
    '  font:500 14px/44px system-ui,-apple-system,Roboto,sans-serif;white-space:nowrap;}',
    '.bs-menu.dark{background:#2b2d31;color:#f1f1f3;box-shadow:0 2px 10px rgba(0,0,0,.5),0 0 0 .5px rgba(255,255,255,.08);}',
    '.bs-menu button{all:unset;padding:0 14px;cursor:pointer;-webkit-tap-highlight-color:transparent;}',
    '.bs-menu button:active{background:rgba(127,127,127,.18);}',
    '.bs-menu button.bs-color{padding:0 4px 0 12px;display:flex;align-items:center;}',
    '.bs-dot{display:inline-block;width:20px;height:20px;border-radius:50%;box-shadow:inset 0 0 0 1.5px rgba(0,0,0,.18);}',
    '.bs-palette{display:none;align-items:center;gap:10px;padding:0 10px;}',
    '.bs-palette button{padding:0;display:flex;}',
    '.bs-palette .bs-dot{width:26px;height:26px;}',
    '.bs-palette .bs-dot.on{box-shadow:0 0 0 2.5px var(--bs-ring,#fff),0 0 0 4.5px currentColor;}',
    '.bs-menu.dark{--bs-ring:#2b2d31;}',
    '.bs-menu.picking > button{display:none;}',
    '.bs-menu.picking .bs-palette{display:flex;}',
    '.bs-loupe{position:fixed;display:none;width:' + LOUPE_W + 'px;height:' + LOUPE_H + 'px;border-radius:14px;',
    '  box-shadow:0 3px 12px rgba(0,0,0,.28),0 0 0 .5px rgba(0,0,0,.12);pointer-events:none;background:#fff;}'
  ].join('\n');
  document.head.appendChild(css);

  var ui = document.createElement('div');
  ui.id = 'bs-sel';
  var startPin = document.createElement('div');
  startPin.className = 'bs-pin start';
  var endPin = document.createElement('div');
  endPin.className = 'bs-pin end';
  var menu = document.createElement('div');
  menu.className = 'bs-menu';
  // The menu's colour (yellow unless changed here), used for Highlight and
  // Underline. Tap the dot to pick another; picking one highlights with it.
  var COLORS = ['#ffd400', '#ff6666', '#5fb236', '#2ea8e5', '#a28ae5', '#e56eee', '#f19837', '#aaaaaa'];
  var menuColor = window.__bookshelfHighlightColor || '#ffd400';
  var colorBtn = document.createElement('button');
  colorBtn.className = 'bs-color';
  colorBtn.dataset.action = 'color';
  colorBtn.setAttribute('aria-label', 'Highlight colour');
  var colorDot = document.createElement('span');
  colorDot.className = 'bs-dot';
  colorDot.style.background = menuColor;
  colorBtn.appendChild(colorDot);
  menu.appendChild(colorBtn);
  [['highlight', 'Highlight'], ['underline', 'Underline'], ['copy', 'Copy']].forEach(function (b) {
    var btn = document.createElement('button');
    btn.textContent = b[1];
    btn.dataset.action = b[0];
    menu.appendChild(btn);
  });
  var palette = document.createElement('div');
  palette.className = 'bs-palette';
  COLORS.forEach(function (c) {
    var b = document.createElement('button');
    b.dataset.action = 'pick';
    b.dataset.color = c;
    var d = document.createElement('span');
    d.className = 'bs-dot' + (c === menuColor ? ' on' : '');
    d.style.background = c;
    d.style.color = c;
    b.appendChild(d);
    palette.appendChild(b);
  });
  menu.appendChild(palette);
  ui.appendChild(startPin);
  ui.appendChild(endPin);
  ui.appendChild(menu);
  // Magnifier shown above the end being dragged, since the finger covers it.
  var loupe = document.createElement('canvas');
  loupe.className = 'bs-loupe';
  ui.appendChild(loupe);
  document.body.appendChild(ui);

  // ---------------------------------------------------------------- state

  var pv = null;          // Zotero's PDFView
  var win = null;         // its iframe window
  var iframe = null;
  var sel = null;         // { a: {p, o}, h: {p, o} } anchor (fixed end) and head (moving end)
  var drag = null;        // pin drag in progress
  var press = null;       // touch on the page in progress
  var menuHiddenUntil = 0;
  var loop = 0;
  var linesCache = new WeakMap();

  function cmp(x, y) { return x.p - y.p || x.o - y.o; }
  function same(x, y) { return x && y && x.p === y.p && x.o === y.o; }
  function ordered() {
    if (!sel) return null;
    return cmp(sel.a, sel.h) <= 0 ? { s: sel.a, e: sel.h } : { s: sel.h, e: sel.a };
  }

  // ---------------------------------------------------------------- geometry

  function pages() { return win.PDFViewerApplication.pdfViewer._pages; }

  function charsOf(p) {
    var page = pv._pdfPages && pv._pdfPages[p];
    return page && page.chars && page.chars.length ? page.chars : null;
  }

  function needPage(p) {
    if (pv._pdfPages && pv._pdfPages[p]) return;
    try { pv._ensureBasicPageData(p); } catch (e) {}
  }

  function box(ch) { return ch.inlineRect || ch.rect; }

  // Lines of a page (from Zotero's line breaks), each with its box and a
  // "block" box: the line widened to the lines just above and below it in the
  // same column. Space to the right of a paragraph's short last line belongs to
  // that line, as it would in a web page, instead of to the line under it.
  function linesOf(chars) {
    var cached = linesCache.get(chars);
    if (cached) return cached;
    var lines = [], start = 0;
    for (var i = 0; i < chars.length; i++) {
      if (chars[i].lineBreakAfter || i === chars.length - 1) {
        var x1 = Infinity, y1 = Infinity, x2 = -Infinity, y2 = -Infinity;
        for (var j = start; j <= i; j++) {
          var r = box(chars[j]);
          if (r[0] < x1) x1 = r[0];
          if (r[1] < y1) y1 = r[1];
          if (r[2] > x2) x2 = r[2];
          if (r[3] > y2) y2 = r[3];
        }
        lines.push({ start: start, end: i, rect: [x1, y1, x2, y2] });
        start = i + 1;
      }
    }
    lines.forEach(function (l, k) {
      var b = l.rect.slice();
      [lines[k - 1], lines[k + 1]].forEach(function (n) {
        if (!n) return;
        var h = Math.max(l.rect[3] - l.rect[1], n.rect[3] - n.rect[1]);
        var gap = Math.max(n.rect[1] - l.rect[3], l.rect[1] - n.rect[3]);
        var overlap = Math.min(l.rect[2], n.rect[2]) - Math.max(l.rect[0], n.rect[0]);
        if (gap < h * 1.5 && overlap > 0) {
          b[0] = Math.min(b[0], n.rect[0]);
          b[2] = Math.max(b[2], n.rect[2]);
        }
      });
      l.block = b;
    });
    linesCache.set(chars, lines);
    return lines;
  }

  function dist(r, x, y) {
    var dx = x < r[0] ? r[0] - x : x > r[2] ? x - r[2] : 0;
    var dy = y < r[1] ? r[1] - y : y > r[3] ? y - r[3] : 0;
    return Math.sqrt(dx * dx + dy * dy);
  }

  // Page under a point (iframe client coordinates); off the pages, the nearest
  // one, with the point clamped onto it. Returns the point in PDF coordinates.
  function pdfPointAt(cx, cy) {
    var list = pages(), best = null, bestD = Infinity;
    for (var i = 0; i < list.length; i++) {
      var div = list[i].div;
      if (!div || !list[i].viewport) continue;
      var r = div.getBoundingClientRect();
      if (r.bottom < -2000 || r.top > win.innerHeight + 2000) continue;
      var d = cy < r.top ? r.top - cy : cy > r.bottom ? cy - r.bottom : 0;
      if (d < bestD) { bestD = d; best = { i: i, r: r }; }
      if (d === 0) break;
    }
    if (!best) return null;
    var r2 = best.r;
    var x = Math.min(Math.max(cx, r2.left), r2.right) - r2.left;
    var y = Math.min(Math.max(cy, r2.top), r2.bottom) - r2.top;
    var pt = list[best.i].viewport.convertToPdfPoint(x, y);
    return { p: best.i, x: pt[0], y: pt[1] };
  }

  // Caret position (offset between characters) nearest a point.
  function offsetAt(cx, cy) {
    var pt = pdfPointAt(cx, cy);
    if (!pt) return null;
    var chars = charsOf(pt.p);
    if (!chars) { needPage(pt.p); return null; }
    var lines = linesOf(chars), line = null, bestD = Infinity;
    for (var i = 0; i < lines.length; i++) {
      var d = dist(lines[i].block, pt.x, pt.y);
      // Ties (the point inside several blocks): the line itself decides.
      if (d === 0) d = dist(lines[i].rect, pt.x, pt.y) * 1e-3;
      if (d < bestD) { bestD = d; line = lines[i]; }
    }
    if (!line) return null;
    var o = line.end + 1, bestDx = Infinity;
    for (var j = line.start; j <= line.end; j++) {
      var r = chars[j].rect;
      var dx = pt.x < r[0] ? r[0] - pt.x : pt.x > r[2] ? pt.x - r[2] : 0;
      if (dx < bestDx) {
        bestDx = dx;
        o = pt.x > (r[0] + r[2]) / 2 ? j + 1 : j;
      }
    }
    return { p: pt.p, o: o };
  }

  // The word under a point, or null if the point isn't on text.
  function wordAt(cx, cy) {
    var pt = pdfPointAt(cx, cy);
    if (!pt) return null;
    var chars = charsOf(pt.p);
    if (!chars) { needPage(pt.p); return null; }
    var best = -1, bestD = Infinity;
    for (var i = 0; i < chars.length; i++) {
      var d = dist(chars[i].rect, pt.x, pt.y);
      if (d < bestD) { bestD = d; best = i; }
    }
    if (best < 0) return null;
    var h = chars[best].rect[3] - chars[best].rect[1];
    if (bestD > Math.max(h, 6)) return null;
    var s = best, e = best;
    while (s > 0 && !chars[s - 1].wordBreakAfter && !chars[s - 1].lineBreakAfter) s--;
    while (e < chars.length - 1 && !chars[e].wordBreakAfter && !chars[e].lineBreakAfter) e++;
    return { s: { p: pt.p, o: s }, e: { p: pt.p, o: e + 1 } };
  }

  // Where a caret position is on screen (top-document coordinates).
  function caretRect(pos, atEnd) {
    var chars = charsOf(pos.p);
    var page = pages()[pos.p];
    if (!chars || !page || !page.div || !page.viewport) return null;
    var idx = atEnd ? pos.o - 1 : pos.o;
    var useRight = atEnd;
    if (idx >= chars.length) { idx = chars.length - 1; useRight = true; }
    if (idx < 0) { idx = 0; useRight = false; }
    var vr = page.viewport.convertToViewportRectangle(chars[idx].rect);
    var dr = page.div.getBoundingClientRect();
    var fr = iframe.getBoundingClientRect();
    var left = fr.left + dr.left + Math.min(vr[0], vr[2]);
    var right = fr.left + dr.left + Math.max(vr[0], vr[2]);
    var top = fr.top + dr.top + Math.min(vr[1], vr[3]);
    var bottom = fr.top + dr.top + Math.max(vr[1], vr[3]);
    return { x: useRight ? right : left, top: top, bottom: bottom };
  }

  // ---------------------------------------------------------------- selection

  function apply() {
    if (!sel || same(sel.a, sel.h)) return;
    var a = sel.a, h = sel.h;
    if (a.p === h.p) {
      pv._selectionRanges = [{ anchor: true, head: true, position: { pageIndex: a.p, rects: [] }, anchorOffset: a.o, headOffset: h.o }];
      try { pv._syncSelectionAfterSemanticChange(); } catch (e) { console.log('bookshelf selection', e); }
    }
    else if (!applyAcrossPages(a, h)) {
      pv._selectionRanges = [
        { anchor: true, position: { pageIndex: a.p, rects: [] }, anchorOffset: a.o },
        { head: true, position: { pageIndex: h.p, rects: [] }, headOffset: h.o }
      ];
      try { pv._syncSelectionAfterSemanticChange(); } catch (e) { console.log('bookshelf selection', e); }
    }
    pv._render();
    startLoop();
  }

  // Zotero's selection of one stretch of one page, from caret offset to offset.
  function pageRange(p, from, to) {
    pv._selectionRanges = [{ anchor: true, head: true, position: { pageIndex: p, rects: [] }, anchorOffset: from, headOffset: to }];
    pv._syncSelectionAfterSemanticChange();
    return pv._selectionRanges.filter(function (r) { return r.position && r.position.pageIndex === p && !r.collapsed; });
  }

  // A selection running onto the next page leaves out the running header and
  // page number in between: the start page stops at the end of its body
  // text, the end page starts at the beginning of its body text.
  function applyAcrossPages(a, h) {
    try {
      var o = ordered();
      var pieces = [];
      for (var p = o.s.p; p <= o.e.p; p++) {
        var chars = charsOf(p);
        if (!chars) return false;
        var body = bodyBounds(chars);
        var from = p === o.s.p ? o.s.o : body.start;
        var to = p === o.e.p ? o.e.o : body.end;
        // If the selection starts or ends in a header or footer itself, keep it.
        if (to <= from) {
          if (p === o.s.p) to = chars.length;
          else from = 0;
        }
        if (to > from) pieces = pieces.concat(pageRange(p, from, to));
      }
      if (!pieces.length) return false;
      pieces.forEach(function (r) {
        delete r.anchor;
        delete r.head;
      });
      var anchorPiece = pieces.filter(function (r) { return r.position.pageIndex === a.p; })[0];
      var headPiece = pieces.filter(function (r) { return r.position.pageIndex === h.p; }).slice(-1)[0];
      if (anchorPiece) { anchorPiece.anchor = true; anchorPiece.anchorOffset = a.o; }
      if (headPiece) { headPiece.head = true; headPiece.headOffset = h.o; }
      pv._setSelectionRanges(pieces);
      return true;
    } catch (e) {
      console.log('bookshelf selection across pages', e);
      return false;
    }
  }

  var boundsCache = new WeakMap();

  // Where a page's body text starts and ends (caret offsets), leaving out a
  // running header at the top and a page number or footer at the bottom:
  // short lines set apart from the text by a wider gap than between lines.
  function bodyBounds(chars) {
    var cached = boundsCache.get(chars);
    if (cached) return cached;
    var lines = linesOf(chars);
    var result = { start: 0, end: chars.length };
    if (lines.length >= 4) {
      var byY = lines.slice().sort(function (x, y) { return y.rect[3] - x.rect[3]; });
      var gaps = [], widths = [];
      for (var i = 1; i < byY.length; i++) gaps.push(Math.max(0, byY[i - 1].rect[1] - byY[i].rect[3]));
      byY.forEach(function (l) { widths.push(l.rect[2] - l.rect[0]); });
      var median = function (v) { var w = v.slice().sort(function (x, y) { return x - y; }); return w[Math.floor(w.length / 2)] || 0; };
      var gap = median(gaps), width = median(widths);
      var textOf = function (l) { var t = ''; for (var j = l.start; j <= l.end; j++) t += chars[j].c || ''; return t.trim(); };
      var marginal = function (l, gapToBody) {
        var t = textOf(l);
        var short = (l.rect[2] - l.rect[0]) < width * 0.75;
        var number = /^[\divxlcdmIVXLCDM\s.\-–—|]+$/.test(t);
        return gapToBody > gap * 1.6 + 1 && (short || number);
      };
      var marked = [];
      // Up to two lines at the top (a header, and a page number above or below it).
      for (var k = 0; k < 2 && k < byY.length - 3; k++) {
        if (marginal(byY[k], byY[k].rect[1] - byY[k + 1].rect[3])) marked.push(byY[k]);
        else break;
      }
      for (var m = byY.length - 1; m > byY.length - 3 && m > 2; m--) {
        if (marginal(byY[m], byY[m - 1].rect[1] - byY[m].rect[3])) marked.push(byY[m]);
        else break;
      }
      var body = lines.filter(function (l) { return marked.indexOf(l) < 0; });
      if (body.length) {
        result = { start: body[0].start, end: body[body.length - 1].end + 1 };
      }
    }
    boundsCache.set(chars, result);
    return result;
  }

  function clear(notify) {
    var had = !!sel;
    sel = null;
    menuHiddenUntil = 0;
    drag = null;
    hideUI();
    if (pv && had) {
      if (notify === false) pv._selectionRanges = [];
      else pv._setSelectionRanges();
      pv._render();
    }
  }

  function setSelection(a, h) {
    if (!a || !h || same(a, h)) return;
    if (sel && same(sel.a, a) && same(sel.h, h)) return;
    sel = { a: a, h: h };
    apply();
  }

  // ---------------------------------------------------------------- pins and menu

  function hideUI() {
    menu.classList.remove('picking');
    loupe.style.display = 'none';
    startPin.style.display = 'none';
    endPin.style.display = 'none';
    menu.style.display = 'none';
  }

  function startLoop() {
    if (loop) return;
    var frame = function () {
      loop = 0;
      if (!sel || !pv) { hideUI(); return; }
      if (pv._tool && pv._tool.type !== 'pointer') { clear(); return; }
      layout();
      loop = requestAnimationFrame(frame);
    };
    loop = requestAnimationFrame(frame);
  }

  function layout() {
    var o = ordered();
    var s = caretRect(o.s, false), e = caretRect(o.e, true);
    var fr = iframe.getBoundingClientRect();
    var visible = function (r) { return r && r.bottom > fr.top && r.bottom < fr.bottom; };
    // Pins hang below the line; the start pin's point is on the selection's
    // left edge and the end pin's on its right edge.
    if (visible(s)) {
      startPin.style.display = 'block';
      startPin.style.left = (s.x - 44) + 'px';
      startPin.style.top = s.bottom + 'px';
    }
    else startPin.style.display = 'none';
    if (visible(e)) {
      endPin.style.display = 'block';
      endPin.style.left = e.x + 'px';
      endPin.style.top = e.bottom + 'px';
    }
    else endPin.style.display = 'none';

    if (drag) drawLoupe(); else loupe.style.display = 'none';

    var showMenu = !drag && !press && Date.now() >= menuHiddenUntil && (visible(s) || visible(e));
    if (!showMenu) { menu.style.display = 'none'; return; }
    var dark = isDark();
    menu.classList.toggle('dark', dark);
    menu.style.display = 'flex';
    var mw = menu.offsetWidth, mh = menu.offsetHeight;
    var topEdge = visible(s) ? s.top : fr.top + 8;
    var y = topEdge - mh - 12;
    if (y < fr.top + 8) {
      var below = (visible(e) ? e.bottom : fr.bottom - mh - 8) + PIN + 12;
      y = below + mh < fr.bottom - 8 ? below : fr.top + (fr.height - mh) / 2;
    }
    var xs = [];
    if (visible(s)) xs.push(s.x);
    if (visible(e)) xs.push(e.x);
    var cx = s && e && o.s.p === o.e.p && Math.abs(s.top - e.top) < 2
      ? (s.x + e.x) / 2
      : fr.left + fr.width / 2;
    var x = Math.min(Math.max(cx - mw / 2, fr.left + 8), fr.right - mw - 8);
    menu.style.left = x + 'px';
    menu.style.top = y + 'px';
  }

  // Draws the page around the moving end of the selection, magnified: the
  // rendered page canvas, the selection on top, and a caret at the end.
  function drawLoupe() {
    var h = sel.h;
    var atEnd = cmp(h, sel.a) > 0;
    var c = caretRect(h, atEnd);
    var fr = iframe.getBoundingClientRect();
    if (!c || c.bottom < fr.top || c.top > fr.bottom) { loupe.style.display = 'none'; return; }
    var dpr = window.devicePixelRatio || 1;
    if (loupe.width !== Math.round(LOUPE_W * dpr)) {
      loupe.width = Math.round(LOUPE_W * dpr);
      loupe.height = Math.round(LOUPE_H * dpr);
    }
    var midY = (c.top + c.bottom) / 2;
    // Source area on screen (top-document coordinates).
    var sw = LOUPE_W / LOUPE_ZOOM, sh = LOUPE_H / LOUPE_ZOOM;
    var sx = c.x - sw / 2, sy = midY - sh / 2;
    var k = dpr * LOUPE_ZOOM;
    var ctx = loupe.getContext('2d');
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    var container = win.document.getElementById('viewerContainer');
    ctx.fillStyle = (container && win.getComputedStyle(container).backgroundColor) || '#888';
    if (ctx.fillStyle === 'rgba(0, 0, 0, 0)') ctx.fillStyle = '#888';
    ctx.fillRect(0, 0, loupe.width, loupe.height);
    var list = pages();
    var dark = pv._themeColorScheme === 'dark';
    for (var i = 0; i < list.length; i++) {
      var page = list[i];
      if (!page.div || !page.viewport) continue;
      var dr = page.div.getBoundingClientRect();
      var top = fr.top + dr.top, left = fr.left + dr.left;
      if (top > sy + sh || top + dr.height < sy) continue;
      var canvas = page.div.querySelector('.canvasWrapper canvas');
      if (canvas && canvas.width) {
        var cr = canvas.getBoundingClientRect();
        var cl = fr.left + cr.left, ct = fr.top + cr.top;
        var rx = canvas.width / cr.width, ry = canvas.height / cr.height;
        // Intersect the source area with this canvas.
        var x1 = Math.max(sx, cl), y1 = Math.max(sy, ct);
        var x2 = Math.min(sx + sw, cl + cr.width), y2 = Math.min(sy + sh, ct + cr.height);
        if (x2 > x1 && y2 > y1) {
          try {
            ctx.drawImage(canvas, (x1 - cl) * rx, (y1 - ct) * ry, (x2 - x1) * rx, (y2 - y1) * ry,
              (x1 - sx) * k, (y1 - sy) * k, (x2 - x1) * k, (y2 - y1) * k);
          } catch (e) {}
        }
      }
      // The selection, as the reader draws it.
      ctx.save();
      ctx.globalAlpha = dark ? 0.7 : 0.4;
      ctx.globalCompositeOperation = dark ? 'lighten' : 'multiply';
      ctx.fillStyle = '#71ADFD';
      (pv._selectionRanges || []).forEach(function (r) {
        if (!r.position || r.position.pageIndex !== i || !r.position.rects) return;
        r.position.rects.forEach(function (rect) {
          var v = page.viewport.convertToViewportRectangle(rect);
          var vx = Math.min(v[0], v[2]), vy = Math.min(v[1], v[3]);
          ctx.fillRect((left + vx - sx) * k, (top + vy - sy) * k,
            Math.abs(v[2] - v[0]) * k, Math.abs(v[3] - v[1]) * k);
        });
      });
      ctx.restore();
    }
    // Caret.
    ctx.fillStyle = '#2F6FEB';
    ctx.fillRect((c.x - sx) * k - dpr, (c.top - sy) * k, 2 * dpr, (c.bottom - c.top) * k);
    // Above the line, clear of the finger; below the pin if there's no room.
    var ly = c.top - LOUPE_H - 14;
    if (ly < fr.top + 4) ly = c.bottom + PIN + 40;
    var lx = Math.min(Math.max(c.x - LOUPE_W / 2, fr.left + 6), fr.right - LOUPE_W - 6);
    loupe.style.left = lx + 'px';
    loupe.style.top = ly + 'px';
    loupe.style.display = 'block';
  }

  function isDark() {
    var cs = window._view && window._view._colorScheme;
    if (cs === 'dark') return true;
    if (cs === 'light') return false;
    return !!(window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches);
  }

  // Pin dragging. The point that follows the finger is the middle of the
  // pin's line, kept at the same distance from the finger as when grabbed.
  function pinDown(ev, which) {
    if (!sel) return;
    ev.preventDefault();
    ev.stopPropagation();
    var o = ordered();
    var moving = which === 'start' ? o.s : o.e;
    var fixed = which === 'start' ? o.e : o.s;
    var c = caretRect(moving, which === 'end');
    if (!c) return;
    drag = {
      id: ev.pointerId,
      fixed: fixed,
      dx: ev.clientX - c.x,
      dy: ev.clientY - (c.top + c.bottom) / 2,
      x: ev.clientX,
      y: ev.clientY,
      scroll: 0
    };
    try { ev.currentTarget.setPointerCapture(ev.pointerId); } catch (e) {}
    menu.style.display = 'none';
  }

  function pinMove(ev) {
    if (!drag || ev.pointerId !== drag.id) return;
    ev.preventDefault();
    drag.x = ev.clientX;
    drag.y = ev.clientY;
    followFinger();
    edgeScroll();
  }

  function followFinger() {
    var fr = iframe.getBoundingClientRect();
    var pos = offsetAt(drag.x - drag.dx - fr.left, drag.y - drag.dy - fr.top);
    if (!pos) return;
    var w = drag.word;
    if (w) {
      // Extending a long-pressed word: the word always stays selected.
      if (cmp(pos, w.s) < 0) setSelection(w.e, pos);
      else if (cmp(pos, w.e) > 0) setSelection(w.s, pos);
      else setSelection(w.s, w.e);
      return;
    }
    if (same(pos, drag.fixed)) return; // never collapse to nothing
    setSelection(drag.fixed, pos);
  }

  function edgeScroll() {
    var fr = iframe.getBoundingClientRect();
    var speed = 0;
    if (drag.y < fr.top + EDGE_SCROLL_ZONE) speed = -(fr.top + EDGE_SCROLL_ZONE - drag.y);
    else if (drag.y > fr.bottom - EDGE_SCROLL_ZONE) speed = drag.y - (fr.bottom - EDGE_SCROLL_ZONE);
    speed = Math.max(-EDGE_SCROLL_ZONE, Math.min(EDGE_SCROLL_ZONE, speed)) / 4.5;
    drag.speed = speed;
    if (!speed || drag.scroll) return;
    var tick = function () {
      if (!drag || !drag.speed) { if (drag) drag.scroll = 0; return; }
      var c = win.document.getElementById('viewerContainer');
      if (c) c.scrollTop += drag.speed;
      followFinger();
      drag.scroll = requestAnimationFrame(tick);
    };
    drag.scroll = requestAnimationFrame(tick);
  }

  function pinUp(ev) {
    if (!drag || ev.pointerId !== drag.id) return;
    if (drag.scroll) cancelAnimationFrame(drag.scroll);
    drag = null;
  }

  [startPin, endPin].forEach(function (pin) {
    var which = pin === startPin ? 'start' : 'end';
    pin.addEventListener('pointerdown', function (ev) { pinDown(ev, which); });
    pin.addEventListener('pointermove', pinMove);
    pin.addEventListener('pointerup', pinUp);
    pin.addEventListener('pointercancel', pinUp);
    pin.addEventListener('contextmenu', function (ev) { ev.preventDefault(); });
  });

  menu.addEventListener('pointerdown', function (ev) { ev.stopPropagation(); });
  menu.addEventListener('click', function (ev) {
    var btn = ev.target.closest('button');
    if (!btn || !sel) return;
    var action = btn.dataset.action;
    var bridge = window.BookshelfSelection;
    if (action === 'color') {
      menu.classList.toggle('picking');
      return;
    }
    if (action === 'pick') {
      menuColor = btn.dataset.color;
      colorDot.style.background = menuColor;
      palette.querySelectorAll('.bs-dot').forEach(function (d) {
        d.classList.toggle('on', d.parentNode.dataset.color === menuColor);
      });
      action = 'highlight';
    }
    menu.classList.remove('picking');
    hideUI();
    menuHiddenUntil = Infinity;
    if (action === 'highlight' && bridge && bridge.highlightColor) bridge.highlightColor(menuColor);
    else if (action === 'underline' && bridge && bridge.underlineColor) bridge.underlineColor(menuColor);
    else if (bridge && bridge[action]) bridge[action]();
    // Highlight/underline: the app saves from the selection it was sent, then
    // deselects (window._view.selectAnnotations([])), which clears this too.
    // Copy, or an app that doesn't answer: clear shortly.
    setTimeout(function () { if (sel) clear(); }, action === 'copy' ? 250 : 1500);
  });

  // ---------------------------------------------------------------- page touches

  function onTouchStart(ev) {
    if (!pv || ev.touches.length !== 1 || (pv._tool && pv._tool.type !== 'pointer')) {
      cancelPress();
      return;
    }
    var t = ev.touches[0];
    cancelPress();
    press = { x: t.clientX, y: t.clientY, t: Date.now(), fired: false, moved: false, word: null };
    press.timer = setTimeout(longPress, LONG_PRESS_MS);
    if (sel) menu.style.display = 'none';
  }

  function cancelPress() {
    if (press && press.timer) clearTimeout(press.timer);
    press = null;
    if (drag && drag.touch) {
      if (drag.scroll) cancelAnimationFrame(drag.scroll);
      drag = null;
    }
  }

  function longPress() {
    if (!press || press.moved) return;
    press.timer = null;
    var position = null;
    try { position = pv.pointerEventToPosition({ clientX: press.x, clientY: press.y }); } catch (e) {}
    // Leave annotations and links to the reader.
    if (position) {
      var onAnnotation = false;
      try {
        onAnnotation = !!(pv.getSelectableAnnotations(position) || []).length || !!pv._getSelectableOverlay(position);
      } catch (e) {}
      if (onAnnotation) { press = null; return; }
    }
    var w = wordAt(press.x, press.y);
    if (!w) return;
    press.fired = true;
    press.word = w;
    // Not a tap: don't let the reader toggle its toolbars when the finger lifts.
    pv._pointerDownTap = null;
    try { if (navigator.vibrate) navigator.vibrate(12); } catch (e) {}
    sel = null;
    menuHiddenUntil = 0;
    setSelection(w.s, w.e);
    var fr = iframe.getBoundingClientRect();
    drag = { touch: true, word: w, dx: 0, dy: 0, x: press.x + fr.left, y: press.y + fr.top, scroll: 0 };
  }

  function onTouchMove(ev) {
    if (!press) return;
    var t = ev.touches[0];
    if (!press.fired) {
      if (Math.abs(t.clientX - press.x) > TOUCH_SLOP || Math.abs(t.clientY - press.y) > TOUCH_SLOP) {
        press.moved = true;
        if (press.timer) clearTimeout(press.timer);
        press.timer = null;
        // Scrolling: hide the menu until things settle.
        menuHiddenUntil = Date.now() + 400;
      }
      return;
    }
    // Finger still down after a long-press: extend the selection from the word.
    if (ev.cancelable) ev.preventDefault();
    if (!drag || !drag.touch) return;
    var fr = iframe.getBoundingClientRect();
    drag.x = t.clientX + fr.left;
    drag.y = t.clientY + fr.top;
    followFinger();
    edgeScroll();
  }

  function onTouchEnd(ev) {
    if (!press) return;
    var p = press;
    if (p.timer) clearTimeout(p.timer);
    press = null;
    if (p.fired) {
      if (ev.cancelable) ev.preventDefault();
      if (drag && drag.touch) {
        if (drag.scroll) cancelAnimationFrame(drag.scroll);
        drag = null;
      }
      return;
    }
    // A tap clears the selection.
    if (!p.moved && sel && Date.now() - p.t < LONG_PRESS_MS) clear();
  }

  function onScroll() {
    if (sel && !drag) menuHiddenUntil = Date.now() + 250;
  }

  // ---------------------------------------------------------------- install

  var CSS_IN_PAGE = '.textLayer,.textLayer *{-webkit-user-select:none!important;user-select:none!important;' +
    '-webkit-touch-callout:none!important;-webkit-user-drag:none!important;}' +
    '#viewerContainer{-webkit-touch-callout:none!important;}';

  function install(view) {
    var w = view._iframeWindow;
    if (!w || !w.document || !w.document.head || w.__bookshelfSelection) return;
    w.__bookshelfSelection = true;
    clear(false);
    pv = view;
    win = w;
    iframe = w.frameElement;

    // Turn off Chrome's native selection in the reader (Zotero enables it on mobile).
    var nts = view._nativeTextSelection;
    if (nts && !nts.__bookshelf) {
      nts.__bookshelf = true;
      nts.updateEnabledState = function () {
        if (this._enabled) {
          this._enabled = false;
          this._setRootState(false, false);
          this.clear();
        }
        w.document.querySelectorAll('.textLayer').forEach(function (l) { l.draggable = false; });
      };
      var rendered = nts.handleTextLayerRendered;
      nts.handleTextLayerRendered = function (layer) {
        rendered.call(this, layer);
        if (layer) layer.draggable = false;
      };
      nts.updateEnabledState();
    }

    var style = w.document.createElement('style');
    style.textContent = CSS_IN_PAGE;
    w.document.head.appendChild(style);

    var d = w.document;
    d.addEventListener('touchstart', onTouchStart, { capture: true, passive: true });
    d.addEventListener('touchmove', onTouchMove, { capture: true, passive: false });
    d.addEventListener('touchend', onTouchEnd, { capture: true, passive: false });
    d.addEventListener('touchcancel', function () { cancelPress(); }, true);
    w.addEventListener('contextmenu', function (ev) {
      if (sel || press) { ev.preventDefault(); ev.stopImmediatePropagation(); }
    }, true);
    var container = d.getElementById('viewerContainer');
    if (container) container.addEventListener('scroll', onScroll, { passive: true });
  }

  function hookView() {
    var v = window._view;
    if (!v || v.__bookshelfSelection) return;
    v.__bookshelfSelection = true;
    var selectAnnotations = v.selectAnnotations;
    v.selectAnnotations = function (ids) {
      if (sel && (!ids || !ids.length)) clear();
      return selectAnnotations.apply(this, arguments);
    };
  }

  function scan() {
    var v = window._view;
    var view = v && v._view;
    if (!view || !view._iframeWindow || !view._iframeWindow.PDFViewerApplication || !('_pdfPages' in view)) return;
    hookView();
    install(view);
  }

  scan();
  setInterval(scan, 500);
})();
