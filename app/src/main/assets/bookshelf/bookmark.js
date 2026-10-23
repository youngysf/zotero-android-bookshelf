// Bookshelf: the book's bookmark, drawn as a ribbon hanging from the top of its page.
// The app sets window.__bookshelfBookmarkPage (a page index, or -1 for none).
(function () {
  if (window.__bookshelfDrawBookmark) return;

  var CSS =
    '.bs-ribbon{position:absolute;top:-3px;z-index:5;pointer-events:none;' +
    'background:linear-gradient(90deg,#2a63d4,#3d80f2 45%,#2a63d4);' +
    'clip-path:polygon(0 0,100% 0,100% 100%,50% 78%,0 100%);' +
    'filter:drop-shadow(0 1px 2px rgba(0,0,0,.35));}';

  function viewer() {
    var v = window._view && window._view._view;
    var w = v && v._iframeWindow;
    var app = w && w.PDFViewerApplication;
    return app && app.pdfViewer ? { win: w, viewer: app.pdfViewer } : null;
  }

  function draw() {
    var found = viewer();
    if (!found) return;
    var doc = found.win.document;
    if (!doc.getElementById('bs-ribbon-style')) {
      var style = doc.createElement('style');
      style.id = 'bs-ribbon-style';
      style.textContent = CSS;
      (doc.head || doc.documentElement).appendChild(style);
    }
    var target = window.__bookshelfBookmarkPage;
    var pages = found.viewer._pages || [];
    var old = doc.querySelectorAll('.bs-ribbon');
    for (var i = 0; i < old.length; i++) {
      if (+old[i].getAttribute('data-page') !== target) old[i].remove();
    }
    if (typeof target !== 'number' || target < 0 || !pages[target]) return;
    var div = pages[target].div;
    if (!div) return;
    var width = Math.max(14, Math.round(div.clientWidth * 0.04));
    var ribbon = div.querySelector('.bs-ribbon');
    if (!ribbon) {
      ribbon = doc.createElement('div');
      ribbon.className = 'bs-ribbon';
      ribbon.setAttribute('data-page', String(target));
      div.appendChild(ribbon);
    }
    ribbon.style.width = width + 'px';
    ribbon.style.height = Math.round(width * 2.6) + 'px';
    ribbon.style.right = Math.round(div.clientWidth * 0.08) + 'px';
  }

  window.__bookshelfDrawBookmark = draw;
  // Pages are redrawn as you zoom and scroll, so keep the ribbon in place.
  setInterval(draw, 600);
})();
