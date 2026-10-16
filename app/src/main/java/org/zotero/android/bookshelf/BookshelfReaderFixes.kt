package org.zotero.android.bookshelf

/**
 * Small fixes injected into Zotero's web reader.
 *
 * Text selection: on Android the reader uses Chrome's native selection over
 * PDF.js's invisible text layer. When a selection handle is dragged over blank
 * space (e.g. past the end of a paragraph) there is no text under it, so Chrome
 * resolves the position by DOM order instead of by position, which in a PDF
 * text layer is often the start or end of the page: the selection suddenly
 * covers the whole page. PDF.js normally prevents this with its "endOfContent"
 * sentinel, but the reader turns that off for native handles.
 *
 * The fix keeps each handle where it was until it is over text again: if an
 * endpoint lands on a non-text node inside a text layer, or jumps much further
 * than a finger can move between two selection updates, the previous selection
 * is restored. Starting a new selection elsewhere (both ends change) is left
 * alone. Runs inside each PDF page frame; does nothing for EPUB or HTML.
 */
object BookshelfReaderFixes {

    /** Fraction of the page height an endpoint may move in one update before it counts as a jump. */
    private const val MAX_JUMP = 0.22

    val script: String = """
(function () {
  if (window.__bookshelfFixes) return;
  window.__bookshelfFixes = true;

  function install(win) {
    if (!win || win.__bookshelfSelectionFix || !win.document) return;
    win.__bookshelfSelectionFix = true;
    var doc = win.document;
    var TEXT = 3;
    var last = null;
    var restoring = false;

    function layerOf(node) {
      if (!node) return null;
      var el = node.nodeType === TEXT ? node.parentElement : node;
      return el && el.closest ? el.closest('.textLayer') : null;
    }
    function caretRect(node, offset) {
      if (!node || node.nodeType !== TEXT) return null;
      try {
        var r = doc.createRange();
        r.setStart(node, Math.min(offset, node.nodeValue.length));
        r.collapse(true);
        var rect = r.getBoundingClientRect();
        if (!rect || (!rect.height && !rect.width && !rect.top && !rect.left)) {
          rect = node.parentElement.getBoundingClientRect();
        }
        return rect;
      } catch (e) { return null; }
    }
    function jumped(prevNode, prevOffset, node, offset) {
      if (node === prevNode && offset === prevOffset) return false;
      var layer = layerOf(node);
      if (!layer) return false;
      if (node.nodeType !== TEXT) return true;
      if (layer !== layerOf(prevNode)) return false;
      var a = caretRect(prevNode, prevOffset);
      var b = caretRect(node, offset);
      if (!a || !b) return false;
      var pageHeight = layer.getBoundingClientRect().height || 1;
      var dy = Math.abs((a.top + a.bottom) / 2 - (b.top + b.bottom) / 2);
      return dy > pageHeight * $MAX_JUMP;
    }
    function remember(sel) {
      last = {
        anchorNode: sel.anchorNode, anchorOffset: sel.anchorOffset,
        focusNode: sel.focusNode, focusOffset: sel.focusOffset
      };
    }

    doc.addEventListener('selectionchange', function () {
      if (restoring) { restoring = false; return; }
      var sel = win.getSelection();
      if (!sel || !sel.rangeCount || sel.isCollapsed) { return; }
      if (!layerOf(sel.anchorNode) && !layerOf(sel.focusNode)) { last = null; return; }

      if (!last || !last.anchorNode.isConnected || !last.focusNode.isConnected) {
        if (sel.anchorNode.nodeType === TEXT && sel.focusNode.nodeType === TEXT) remember(sel);
        return;
      }
      var anchorChanged = sel.anchorNode !== last.anchorNode || sel.anchorOffset !== last.anchorOffset;
      var focusChanged = sel.focusNode !== last.focusNode || sel.focusOffset !== last.focusOffset;
      // Both ends moved at once: a new selection, not a handle drag.
      if (anchorChanged && focusChanged
          && sel.anchorNode.nodeType === TEXT && sel.focusNode.nodeType === TEXT) {
        remember(sel);
        return;
      }
      var bad = (anchorChanged && jumped(last.anchorNode, last.anchorOffset, sel.anchorNode, sel.anchorOffset))
        || (focusChanged && jumped(last.focusNode, last.focusOffset, sel.focusNode, sel.focusOffset));
      if (bad) {
        try {
          restoring = true;
          sel.setBaseAndExtent(last.anchorNode, last.anchorOffset, last.focusNode, last.focusOffset);
        } catch (e) { restoring = false; }
        return;
      }
      if (sel.anchorNode.nodeType === TEXT && sel.focusNode.nodeType === TEXT) remember(sel);
    }, true);
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
