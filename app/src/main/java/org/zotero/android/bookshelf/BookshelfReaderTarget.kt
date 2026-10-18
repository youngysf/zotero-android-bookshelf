package org.zotero.android.bookshelf

/**
 * A highlight to show when a book opens (from the notebook or the book page).
 * Set just before opening the reader; the reader takes it once.
 */
object BookshelfReaderTarget {
    @Volatile
    private var pending: Pair<String, String>? = null

    fun set(attachmentKey: String, annotationKey: String) {
        pending = attachmentKey to annotationKey
    }

    fun take(attachmentKey: String): String? {
        val target = pending ?: return null
        pending = null
        return if (target.first == attachmentKey) target.second else null
    }
}
