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
    val script: String by lazy {
        context.assets.open("bookshelf/selection.js").bufferedReader().use { it.readText() }
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
