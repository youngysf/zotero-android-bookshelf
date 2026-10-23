package org.zotero.android.bookshelf

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.util.Locale

/** Where a book's bookmark is: the annotation's key, its page index and the page's label. */
data class BookshelfBookmarkState(val key: String, val pageIndex: Int, val pageLabel: String)

/**
 * The book's bookmark: one per book, like a ribbon left between the pages.
 *
 * It is kept as a small Zotero note annotation with a fixed comment, so it
 * syncs with the desktop Bookshelf plugin like any other annotation. The
 * reader never draws it as a note; it shows a ribbon on the page instead, and
 * it stays out of the annotation lists and the Highlights notebook.
 * Putting the bookmark on another page moves it: the old one is removed.
 */
object BookshelfBookmark {
    const val COMMENT = "Bookshelf bookmark"
    const val COLOR = "#2ea8e5"

    fun isBookmark(type: String?, comment: String?): Boolean =
        type == "note" && comment?.trim() == COMMENT

    /** Note annotations are 22 points square; this one sits in the page's top-right corner. */
    fun json(key: String, pageIndex: Int, pageLabel: String, box: List<Double>?, dateIso: String): JsonObject {
        val x1 = box?.getOrNull(2) ?: 612.0
        val y1 = box?.getOrNull(3) ?: 792.0
        val right = x1 - 14
        val top = y1 - 14
        val rect = JsonArray().apply {
            add(Math.round(right - 22).toDouble())
            add(Math.round(top - 22).toDouble())
            add(Math.round(right).toDouble())
            add(Math.round(top).toDouble())
        }
        val fromTop = (y1 - top).toInt().coerceAtLeast(0)
        return JsonObject().apply {
            addProperty("id", key)
            addProperty("type", "note")
            addProperty("color", COLOR)
            addProperty("comment", COMMENT)
            addProperty("pageLabel", pageLabel)
            addProperty("sortIndex", String.format(Locale.US, "%05d|%06d|%05d", pageIndex, 0, fromTop))
            addProperty("dateCreated", dateIso)
            addProperty("dateModified", dateIso)
            add("tags", JsonArray())
            add("position", JsonObject().apply {
                addProperty("pageIndex", pageIndex)
                add("rects", JsonArray().apply { add(rect) })
            })
        }
    }

    private var ribbonSource: String? = null

    /** The page script that draws the ribbon (assets/bookshelf/bookmark.js). */
    fun script(context: Context): String {
        val cached = ribbonSource
        if (cached != null) return cached
        val source = context.assets.open("bookshelf/bookmark.js").bufferedReader().use { it.readText() }
        ribbonSource = source
        return source
    }

    /** Puts the ribbon on a page, or takes it away (null). */
    fun showScript(pageIndex: Int?): String =
        "window.__bookshelfBookmarkPage=${pageIndex ?: -1};" +
            "if(window.__bookshelfDrawBookmark)window.__bookshelfDrawBookmark();"

    /** Asks the page for a page's size in PDF points, as "[x0,y0,x1,y1]". */
    fun pageBoxScript(pageIndex: Int): String =
        "(function(){try{var v=window._view&&window._view._view;" +
            "var p=v._iframeWindow.PDFViewerApplication.pdfViewer._pages[$pageIndex];" +
            "var b=(p.viewport&&p.viewport.viewBox)||(p.pdfPage&&p.pdfPage.view);" +
            "return JSON.stringify(Array.prototype.slice.call(b));}catch(e){return null;}})()"
}
