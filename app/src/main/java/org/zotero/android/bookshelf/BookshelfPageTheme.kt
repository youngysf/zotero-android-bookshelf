package org.zotero.android.bookshelf

import androidx.compose.ui.graphics.Color

/**
 * Page colours for the reader, chosen separately from the app's light or dark
 * look (like the desktop app's View → Appearance). These match the themes
 * built into Zotero's web reader.
 */
enum class PageTheme(
    val label: String,
    val background: String?,
    val foreground: String?,
    val invertImages: Boolean = false,
) {
    /** The PDF exactly as it is (white pages). */
    Original("Original", null, null),
    Snow("Snow", "#ECEFF4", "#3B4252"),
    Sepia("Sepia", "#F4ECD8", "#5B4636"),
    Dark("Dark", "#2E3440", "#D8DEE9"),
    Black("Black", "#000000", "#FFFFFF", invertImages = true);

    val swatch: Color get() = Color(android.graphics.Color.parseColor(background ?: "#FFFFFF"))
    val swatchText: Color get() = Color(android.graphics.Color.parseColor(foreground ?: "#222222"))

    /** JavaScript value for the web reader's theme object (null = original pages). */
    private fun jsValue(): String {
        if (background == null || foreground == null) return "null"
        val id = name.lowercase()
        return "{id:'$id',label:'$label',background:'$background',foreground:'$foreground',invertImages:$invertImages}"
    }

    /**
     * Sets this page colour for both of the reader's colour schemes, so the
     * pages look the same whether the interface is light or dark. Safe to run
     * right after createView(), before the document has loaded.
     */
    fun applyScript(): String {
        return "(function(t){var v=window._view;if(!v)return;" +
            "v._lightTheme=t;v._darkTheme=t;" +
            "if(v._view){v._view.setLightTheme(t);v._view.setDarkTheme(t);}" +
            "if(v._sdtView){v._sdtView.setLightTheme(t);v._sdtView.setDarkTheme(t);}" +
            "})(${jsValue()});"
    }
}
