package org.zotero.android.bookshelf

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.zotero.android.database.objects.Attachment
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reading progress. The reading position is Zotero's own (synced between
 * devices); the page count is remembered on this phone, from the reader or
 * from drawing the cover.
 */
@Singleton
class BookshelfProgress @Inject constructor(context: Context) {
    private val prefs = context.getSharedPreferences("bookshelf-pages", Context.MODE_PRIVATE)

    private val _counts = MutableStateFlow<Map<String, Int>>(
        prefs.all.mapNotNull { (k, v) -> (v as? Int)?.let { k to it } }.toMap()
    )
    /** Attachment key → number of pages (PDFs) or locations (EPUBs). */
    val counts: StateFlow<Map<String, Int>> = _counts.asStateFlow()

    fun setCount(attachmentKey: String, count: Int) {
        if (count <= 0 || _counts.value[attachmentKey] == count) return
        prefs.edit().putInt(attachmentKey, count).apply()
        _counts.value = _counts.value + (attachmentKey to count)
    }

    fun hasCount(attachmentKey: String) = _counts.value.containsKey(attachmentKey)

    companion object {
        /**
         * How far through a book is, 0..1, or null if it hasn't been opened (or
         * the page count isn't known yet).
         */
        fun fraction(attachment: Attachment?, position: String?, counts: Map<String, Int>): Float? {
            if (attachment == null || position.isNullOrBlank()) return null
            val kind = attachment.type as? Attachment.Kind.file ?: return null
            return when (kind.contentType) {
                "application/pdf" -> {
                    val index = position.toIntOrNull() ?: return null
                    val total = counts[attachment.key] ?: return null
                    ((index + 1).toFloat() / total).coerceIn(0f, 1f)
                }
                // Web pages save how far down they're scrolled, in percent.
                "text/html" -> position.toFloatOrNull()?.let { (it / 100f).coerceIn(0f, 1f) }
                else -> null
            }
        }

        fun isFinished(fraction: Float?) = fraction != null && fraction >= 0.999f
    }
}
