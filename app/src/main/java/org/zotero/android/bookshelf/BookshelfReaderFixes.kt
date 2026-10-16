package org.zotero.android.bookshelf

/**
 * Small fixes injected into Zotero's web reader.
 *
 * Text selection in PDFs: on Android the reader uses Chrome's native selection
 * handles over PDF.js's invisible text layer. The text layer is a pile of
 * absolutely positioned spans, so when a handle is over blank space (past the
 * end of a paragraph, between lines) Chrome picks a position by DOM order
 * rather than by distance, and the selection jumps elsewhere on the page.
 *
 * PDF.js guards against this with an invisible "endOfContent" sheet that,
 * while selecting, covers the page and sits in the DOM right next to the end
 * being moved, so blank space resolves to that end. Zotero's Android mode turns
 * the sheet's hit-testing off (pointer-events: none), and PDF.js guesses which
 * end is moving from the previous selection, which is wrong right after a
 * selection is made and when the other handle is grabbed.
 *
 * Also, whenever the moving end lands on a non-text position (the sheet, or one
 * of PDF.js's <br> line breaks), Chrome draws the handle at the page's top-left
 * corner and Android continues the drag from there, so the selection flips and
 * the handles swap. The moving end is snapped to the equivalent text position
 * (end of the previous word, or start of the next when it is the start).
 *
 * The fix turns the sheet back on and places it deterministically: on Android
 * the handle being dragged is always the selection's focus, so after every
 * selection change the sheet goes immediately after the focus's span (or before
 * it, when the focus is the start of the selection). After a finger lifts,
 * PDF.js resets the sheet; it is placed again right away.
 *
 * Verified against the reader bundle in phone-sized Chromium: handles dragged
 * into blank space stay at the nearest end of the line instead of jumping.
 */
object BookshelfReaderFixes {

    val script: String = """
(function () {
  if (window.__bookshelfFixes) return;
  window.__bookshelfFixes = true;

  var CSS = ':root[data-native-text-selection] .textLayer.selecting .endOfContent' +
    '{pointer-events:auto !important;user-select:text !important;-webkit-user-select:text !important;}';

  function install(win) {
    if (!win || win.__bookshelfSelectionFix || !win.document || !win.document.head) return;
    win.__bookshelfSelectionFix = true;
    var doc = win.document;
    var style = doc.createElement('style');
    style.textContent = CSS;
    doc.head.appendChild(style);

    function layerOf(node) {
      if (!node) return null;
      var el = node.nodeType === 3 ? node.parentElement : node;
      return el && el.closest ? el.closest('.textLayer') : null;
    }

    // The nearest text position before (or after) a non-text position in the
    // text layer, skipping the sheet. Chrome draws a handle at the page's
    // top-left corner for non-text positions, and Android then continues the
    // drag from there, flipping the selection.
    function textPositionNear(node, offset, layer, forward) {
      var walker = doc.createTreeWalker(layer, win.NodeFilter.SHOW_TEXT, {
        acceptNode: function (n) {
          var p = n.parentElement;
          return p && !p.classList.contains('endOfContent') && n.nodeValue.length
            ? win.NodeFilter.FILTER_ACCEPT : win.NodeFilter.FILTER_SKIP;
        }
      });
      var probe = doc.createRange();
      try { probe.setStart(node, offset); } catch (e) { return null; }
      var before = null, after = null, n;
      while ((n = walker.nextNode())) {
        var r = doc.createRange();
        r.selectNodeContents(n);
        if (r.compareBoundaryPoints(win.Range.START_TO_START, probe) < 0) before = n;
        else { after = n; break; }
      }
      if (forward) return before ? [before, before.nodeValue.length] : (after ? [after, 0] : null);
      return after ? [after, 0] : (before ? [before, before.nodeValue.length] : null);
    }

    function normalizeFocus(sel, layer) {
      var focus = sel.focusNode;
      if (focus.nodeType === 3 && !(focus.parentElement && focus.parentElement.classList.contains('endOfContent'))) {
        return false;
      }
      var range = sel.getRangeAt(0);
      var focusIsStart = range.startContainer === sel.focusNode && range.startOffset === sel.focusOffset;
      var target = textPositionNear(focus, sel.focusOffset, layer, !focusIsStart);
      if (!target) return false;
      try {
        // extend() moves only the focus; the fixed end (anchor) is untouched.
        sel.extend(target[0], target[1]);
        return true;
      } catch (e) { return false; }
    }

    function place() {
      var sel = win.getSelection();
      if (!sel || !sel.rangeCount || sel.isCollapsed) return;
      var layer = layerOf(sel.focusNode);
      if (!layer) return;
      if (normalizeFocus(sel, layer)) return; // a new selectionchange follows
      var focus = sel.focusNode;
      // Only move the sheet when the focus is real text; if it is already on
      // the sheet (blank space), leave it where it is.
      if (focus.nodeType !== 3) return;
      var span = focus.parentElement;
      if (!span || span.classList.contains('endOfContent') || span === layer) return;
      var sheet = layer.querySelector('.endOfContent');
      if (!sheet) return;
      var range = sel.getRangeAt(0);
      var focusIsStart = range.startContainer === sel.focusNode && range.startOffset === sel.focusOffset;
      layer.classList.add('selecting');
      sheet.style.width = layer.style.width;
      sheet.style.height = layer.style.height;
      sheet.style.userSelect = 'text';
      var alreadyPlaced = focusIsStart ? sheet.nextSibling === span : span.nextSibling === sheet;
      if (!alreadyPlaced) {
        span.parentElement.insertBefore(sheet, focusIsStart ? span : span.nextSibling);
      }
    }

    // Runs after PDF.js's own selectionchange handler (registered earlier) and overrides its placement.
    doc.addEventListener('selectionchange', place);
    function placeSoon() {
      win.setTimeout(function () { doc.dispatchEvent(new win.Event('selectionchange')); }, 0);
      win.setTimeout(function () { doc.dispatchEvent(new win.Event('selectionchange')); }, 150);
    }
    doc.addEventListener('pointerup', placeSoon, true);
    doc.addEventListener('pointercancel', placeSoon, true);
    doc.addEventListener('touchend', placeSoon, true);
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
