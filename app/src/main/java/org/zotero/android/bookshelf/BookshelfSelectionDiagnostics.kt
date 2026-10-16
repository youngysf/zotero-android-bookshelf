package org.zotero.android.bookshelf

/**
 * Temporary on-screen log for diagnosing PDF text selection on the phone.
 * Shows touch/pointer/focus events, every selection change (where each end is)
 * and where PDF.js's endOfContent sheet sits, in a small panel in the reader.
 * Turned on from the reader's Aa menu; nothing is recorded or sent anywhere.
 */
object BookshelfSelectionDiagnostics {
    val script: String = """
(function () {
  if (window.__bookshelfDiag) return;
  window.__bookshelfDiag = true;
  function install(win) {
    if (!win || win.__bookshelfDiagInstalled || !win.document || !win.document.body) return;
    win.__bookshelfDiagInstalled = true;
    var doc = win.document;
    var panel = doc.createElement('div');
    panel.style.cssText = 'position:fixed;left:4px;right:4px;bottom:90px;z-index:2147483647;' +
      'background:rgba(0,0,0,0.8);color:#9f9;font:9px/11px monospace;padding:4px 6px;' +
      'pointer-events:none;white-space:pre-wrap;word-break:break-all;overflow:hidden;max-height:260px;border-radius:6px;';
    doc.body.appendChild(panel);
    var lines = [];
    var t0 = Date.now();
    function log(s) {
      var t = ((Date.now() - t0) / 1000).toFixed(2);
      lines.push(t + ' ' + s);
      if (lines.length > 12) lines.shift();
      panel.textContent = lines.join('\n');
    }
    function layerOf(n) { var e = n && (n.nodeType === 3 ? n.parentElement : n); return e && e.closest ? e.closest('.textLayer') : null; }
    function spanY(el) { try { var l = layerOf(el); return Math.round(el.getBoundingClientRect().top - l.getBoundingClientRect().top); } catch (e) { return '?'; } }
    function desc(n, off) {
      if (!n) return 'null';
      if (n.nodeType === 3) {
        var v = n.nodeValue || '';
        return '"' + v.slice(Math.max(0, off - 5), off).replace(/\s/g, '_') + '|"y' + spanY(n.parentElement);
      }
      var cls = (n.className && n.className.baseVal === undefined ? n.className : n.tagName) || n.tagName;
      var child = n.childNodes[off];
      var cdesc = child ? (child.nodeType === 3 ? 'txt' : (child.className || child.tagName)) : 'END';
      return '<' + String(cls).slice(0, 14) + '>[' + off + '/' + n.childNodes.length + ' ' + String(cdesc).slice(0, 10) + ']';
    }
    function sheetInfo() {
      var sel = win.getSelection();
      var layer = layerOf(sel && sel.focusNode) || doc.querySelector('.textLayer.selecting');
      if (!layer) return 'noLayer';
      var sheet = layer.querySelector('.endOfContent');
      if (!sheet) return 'noSheet';
      var prev = sheet.previousSibling, atEnd = !sheet.nextSibling;
      var pe = win.getComputedStyle(sheet).pointerEvents;
      return (layer.classList.contains('selecting') ? 'SEL ' : 'idle ') + (atEnd ? 'atEnd ' : '') +
        'after:' + (prev ? (prev.nodeType === 3 ? 'txt' : '"' + (prev.textContent || '').slice(-8).replace(/\s/g, '_') + '"y' + spanY(prev)) : '-') +
        ' pe:' + pe;
    }
    function tgt(e) { var t = e.target; return t ? (t.className && typeof t.className === 'string' ? t.className.split(' ')[0] : t.tagName) : '?'; }
    ['pointerdown', 'pointerup', 'pointercancel', 'touchstart', 'touchend', 'touchcancel', 'contextmenu', 'keyup'].forEach(function (type) {
      doc.addEventListener(type, function (e) {
        var p = e.touches ? ' n=' + e.touches.length : (e.pointerType ? ' ' + e.pointerType : '');
        log(type.toUpperCase() + p + ' on ' + tgt(e) + ' | ' + sheetInfo());
      }, true);
    });
    win.addEventListener('blur', function () { log('BLUR | ' + sheetInfo()); }, true);
    win.addEventListener('focus', function () { log('FOCUS'); }, true);
    var lastKey = '';
    doc.addEventListener('selectionchange', function () {
      var s = win.getSelection();
      var key = !s || !s.rangeCount ? 'none' : s.isCollapsed ? 'collapsed ' + desc(s.focusNode, s.focusOffset)
        : 'A' + desc(s.anchorNode, s.anchorOffset) + ' F' + desc(s.focusNode, s.focusOffset);
      if (key === lastKey) return;
      lastKey = key;
      // Log after other handlers ran, so the sheet position is what they left behind.
      win.setTimeout(function () { log('SEL ' + key + ' | ' + sheetInfo()); }, 0);
    });
    log('diagnostics on');
  }
  function scan() {
    var frames = document.querySelectorAll('iframe');
    for (var i = 0; i < frames.length; i++) {
      try {
        var w = frames[i].contentWindow;
        if (w && w.PDFViewerApplication && w.document && w.document.readyState !== 'loading') install(w);
      } catch (e) {}
    }
  }
  scan();
  setInterval(scan, 1000);
})();
""".trimIndent()
}
