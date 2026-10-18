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

    private val tints = ConcurrentHashMap<String, Int>()

    /**
     * A muted colour taken from the book's cover (for spines and the book
     * page), or null if there's no cover on this phone.
     */
    suspend fun tintFor(attachment: Attachment): Int? {
        tints[attachment.key]?.let { return it }
        val cover = coverFor(attachment) ?: return null
        return tintOfCover(cover, attachment.key)
    }

    /** The same, for a PDF the reader has open. */
    suspend fun tintForPdf(pdf: File, libraryId: org.zotero.android.sync.LibraryIdentifier, key: String): Int? {
        tints[key]?.let { return it }
        val cover = coverFor(pdf, "${libraryId.folderName}-$key", key) ?: return null
        return tintOfCover(cover, key)
    }

    private suspend fun tintOfCover(cover: File, key: String): Int? {
        return withContext(Dispatchers.IO) {
            try {
                val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = 16 }
                val bitmap = android.graphics.BitmapFactory.decodeFile(cover.absolutePath, options) ?: return@withContext null
                val tint = try { coverTint(bitmap) } finally { bitmap.recycle() }
                tints[key] = tint
                tint
            } catch (e: Throwable) {
                null
            }
        }
    }

    /** The most colourful common colour of a small cover image, darkened to read as a spine. */
    private fun coverTint(bitmap: Bitmap): Int {
        val buckets = HashMap<Int, IntArray>()
        val hsv = FloatArray(3)
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val c = bitmap.getPixel(x, y)
                Color.colorToHSV(c, hsv)
                // Ignore near-white page and near-black text.
                if (hsv[2] < 0.12f || (hsv[1] < 0.08f && hsv[2] > 0.85f)) continue
                val key = ((hsv[0] / 30f).toInt() shl 4) or ((hsv[1] * 3).toInt() shl 2) or (hsv[2] * 3).toInt()
                val b = buckets.getOrPut(key) { IntArray(4) }
                b[0] += Color.red(c); b[1] += Color.green(c); b[2] += Color.blue(c); b[3]++
            }
        }
        val best = buckets.values.maxByOrNull { b ->
            val r = b[0] / b[3]; val g = b[1] / b[3]; val bl = b[2] / b[3]
            Color.RGBToHSV(r, g, bl, hsv)
            b[3] * (0.35f + hsv[1])
        } ?: return Color.rgb(60, 62, 68)
        val r = best[0] / best[3]; val g = best[1] / best[3]; val bl = best[2] / best[3]
        Color.RGBToHSV(r, g, bl, hsv)
        hsv[1] = hsv[1].coerceAtMost(0.55f)
        hsv[2] = hsv[2].coerceIn(0.22f, 0.5f)
        return Color.HSVToColor(hsv)
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
