package org.zotero.android.bookshelf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import org.zotero.android.database.objects.Attachment
import org.zotero.android.files.FileStore
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Makes book covers from the first page of each PDF on this phone.
 *
 * Memory: each cover is drawn once with Android's built-in PDF renderer, saved
 * as a small JPEG, and reused after that. Only one PDF is opened at a time.
 * Books that aren't downloaded get no cover until they are (the grid shows a
 * title cover instead).
 */
@Singleton
class BookshelfCovers @Inject constructor(
    private val context: Context,
    private val fileStore: FileStore,
    private val progress: BookshelfProgress,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val renderDispatcher = Dispatchers.IO.limitedParallelism(1)

    private val directory: File by lazy {
        File(context.cacheDir, "bookshelf-covers").apply { mkdirs() }
    }

    // Files that couldn't be drawn (damaged, password-protected), so they aren't retried
    // on every scroll. Keyed by file identity, so a changed file is tried again.
    private val failed = ConcurrentHashMap.newKeySet<String>()

    /** The saved cover for this attachment, drawing it first if needed; null if there can't be one. */
    suspend fun coverFor(attachment: Attachment): File? {
        val kind = attachment.type as? Attachment.Kind.file ?: return null
        if (kind.contentType != "application/pdf") return null
        if (kind.location != Attachment.FileLocation.local
            && kind.location != Attachment.FileLocation.localAndChangedRemotely
        ) {
            return null
        }
        val pdf = withContext(Dispatchers.IO) {
            fileStore.attachmentFile(attachment.libraryId, attachment.key, kind.filename)
        }
        val cover = coverFor(pdf, "${attachment.libraryId.folderName}-${attachment.key}", attachment.key)
        if (cover != null && !progress.hasCount(attachment.key)) countPages(pdf, attachment.key)
        return cover
    }

    /** Page count for progress bars, for covers that were made before counts were kept. */
    private suspend fun countPages(pdf: File, key: String) = withContext(renderDispatcher) {
        if (progress.hasCount(key)) return@withContext
        try {
            ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { progress.setCount(key, it.pageCount) }
            }
        } catch (e: Throwable) {
            Timber.w(e, "Bookshelf: couldn't count pages for $key")
        }
    }

    private suspend fun coverFor(pdf: File, id: String, key: String): File? = withContext(Dispatchers.IO) {
        if (!pdf.isFile || pdf.length() == 0L) return@withContext null
        val identity = "$id-${pdf.length()}-${pdf.lastModified()}"
        val out = File(directory, "$identity.jpg")
        if (out.isFile) return@withContext out
        if (identity in failed) return@withContext null
        withContext(renderDispatcher) {
            if (out.isFile) return@withContext out
            try {
                render(pdf, out, key)
                removeOlderVersions(id, keep = out)
                trimCache()
                out
            } catch (e: Throwable) {
                Timber.w(e, "Bookshelf: couldn't make a cover for $id")
                failed += identity
                null
            }
        }
    }

    private fun render(pdf: File, out: File, key: String) {
        ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                if (renderer.pageCount < 1) error("PDF has no pages")
                progress.setCount(key, renderer.pageCount)
                renderer.openPage(0).use { page ->
                    val width = COVER_WIDTH
                    val height = (width.toLong() * page.height / page.width.coerceAtLeast(1))
                        .toInt()
                        .coerceIn(width / 3, MAX_COVER_HEIGHT)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    try {
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val partial = File(out.parentFile, out.name + ".part")
                        FileOutputStream(partial).use { stream ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                        }
                        if (!partial.renameTo(out)) error("Couldn't save cover")
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
        }
    }

    private fun removeOlderVersions(id: String, keep: File) {
        directory.listFiles { file -> file.name.startsWith("$id-") && file != keep }
            ?.forEach { it.delete() }
    }

    private fun trimCache() {
        val files = directory.listFiles() ?: return
        if (files.size <= MAX_SAVED_COVERS) return
        files.sortedBy { it.lastModified() }
            .take(files.size - MAX_SAVED_COVERS)
            .forEach { it.delete() }
    }

    companion object {
        /** Pixel width of saved covers: sharp at the largest grid size on high-density screens. */
        const val COVER_WIDTH = 360
        private const val MAX_COVER_HEIGHT = 720
        private const val MAX_SAVED_COVERS = 5000
    }
}
