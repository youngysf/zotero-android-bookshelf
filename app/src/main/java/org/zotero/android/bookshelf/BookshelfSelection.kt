package org.zotero.android.bookshelf

import android.content.Context
import android.view.View
import android.webkit.JavascriptInterface
import org.zotero.android.screens.reader.web.ReaderSelectionActionModeDelegate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bookshelf's own text selection for PDFs (assets/bookshelf/selection.js):
 * long-press a word, drag the pins, and pick Highlight / Underline / Copy.
 * It replaces Chrome's selection handles, which jump around on PDF text.
 */
@Singleton
class BookshelfSelection @Inject constructor(private val context: Context) {
    private val source: String by lazy {
        context.assets.open("bookshelf/selection.js").bufferedReader().use { it.readText() }
    }

    /** The script, told the selection menu's current colour. */
    val script: String
        get() {
            val color = menuColor(context).filter { it == '#' || it.isLetterOrDigit() }
            return "window.__bookshelfHighlightColor='$color';" + source
        }

    companion object {
        const val DEFAULT_COLOR = "#ffd400"

        fun menuColor(context: Context): String =
            context.getSharedPreferences("bookshelf", Context.MODE_PRIVATE)
                .getString("selectionMenuColor", DEFAULT_COLOR) ?: DEFAULT_COLOR

        fun setMenuColor(context: Context, color: String) {
            context.getSharedPreferences("bookshelf", Context.MODE_PRIVATE)
                .edit().putString("selectionMenuColor", color).apply()
        }
    }
}

/**
 * What the selection menu in the page calls (as window.BookshelfSelection).
 * Runs the same actions as the old text-selection menu.
 */
class BookshelfSelectionBridge(
    private val view: View,
    private val delegate: () -> ReaderSelectionActionModeDelegate?,
) {
    @JavascriptInterface
    fun highlight() {
        view.post { delegate()?.onHighlight() }
    }

    @JavascriptInterface
    fun highlightColor(color: String) {
        BookshelfSelection.setMenuColor(view.context, color)
        view.post { delegate()?.onHighlightColor(color) }
    }

    @JavascriptInterface
    fun underlineColor(color: String) {
        BookshelfSelection.setMenuColor(view.context, color)
        view.post { delegate()?.onUnderlineColor(color) }
    }

    @JavascriptInterface
    fun underline() {
        view.post { delegate()?.onUnderline() }
    }

    @JavascriptInterface
    fun copy() {
        view.post { delegate()?.onCopy() }
    }

    companion object {
        const val NAME = "BookshelfSelection"
    }
}
