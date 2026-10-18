package org.zotero.android.bookshelf

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Shares a highlight as an image: the quote in serif type on a dark card
 * tinted like the book's cover, with the title and author underneath.
 */
object BookshelfQuoteCard {

    fun share(context: Context, quote: String, title: String, author: String, highlightColor: String, tint: Int?) {
        val file = render(context, quote, title, author, highlightColor, tint)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".provider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "“${quote.trim()}” — $title")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(send, "Share quote").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun render(context: Context, quote: String, title: String, author: String, highlightColor: String, tint: Int?): File {
        val width = 1080
        val height = 1350
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val base = tint ?: android.graphics.Color.rgb(24, 25, 28)
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(base, hsv)
        hsv[2] = hsv[2].coerceAtMost(0.22f)
        hsv[1] = hsv[1].coerceAtMost(0.45f)
        canvas.drawColor(android.graphics.Color.HSVToColor(hsv))

        val margin = 110f
        val accent = parseColor(highlightColor) ?: android.graphics.Color.rgb(74, 128, 245)
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
        canvas.drawRoundRect(RectF(margin, 150f, margin + 90f, 162f), 6f, 6f, accentPaint)

        val quotePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(240, 240, 243)
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        }
        val text = "“" + quote.trim().replace(Regex("\\s+"), " ") + "”"
        val textWidth = (width - 2 * margin).toInt()
        val available = height - 230f - 330f
        // Largest size that fits, down to a small one for long quotes.
        var size = 67f
        var layout: StaticLayout
        do {
            size -= 3f
            quotePaint.textSize = size
            layout = StaticLayout.Builder.obtain(text, 0, text.length, quotePaint, textWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.25f)
                .build()
        } while (layout.height > available && size > 30f)
        if (layout.height > available) {
            val maxLines = (available / (layout.height / layout.lineCount.toFloat())).toInt().coerceAtLeast(1)
            layout = StaticLayout.Builder.obtain(text, 0, text.length, quotePaint, textWidth)
                .setLineSpacing(0f, 1.25f)
                .setMaxLines(maxLines)
                .setEllipsize(android.text.TextUtils.TruncateAt.END)
                .build()
        }
        canvas.save()
        canvas.translate(margin, 230f)
        layout.draw(canvas)
        canvas.restore()

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(240, 240, 243)
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textSize = 40f
        }
        val authorPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(170, 240, 240, 243)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textSize = 32f
        }
        val linePaint = Paint().apply { color = android.graphics.Color.argb(60, 255, 255, 255) }
        canvas.drawRect(margin, height - 290f, width - margin, height - 288f, linePaint)
        val titleLayout = StaticLayout.Builder.obtain(title, 0, title.length, titlePaint, textWidth)
            .setMaxLines(2)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .build()
        canvas.save()
        canvas.translate(margin, height - 250f)
        titleLayout.draw(canvas)
        canvas.restore()
        if (author.isNotBlank()) {
            canvas.drawText(
                android.text.TextUtils.ellipsize(author, authorPaint, textWidth.toFloat(), android.text.TextUtils.TruncateAt.END).toString(),
                margin,
                height - 250f + titleLayout.height + 52f,
                authorPaint,
            )
        }

        val dir = File(context.filesDir, "bookshelf-share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "quote-${System.currentTimeMillis()}.png")
        FileOutputStream(out).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return out
    }

    private fun parseColor(hex: String): Int? = try {
        android.graphics.Color.parseColor(hex)
    } catch (e: Throwable) {
        null
    }
}
