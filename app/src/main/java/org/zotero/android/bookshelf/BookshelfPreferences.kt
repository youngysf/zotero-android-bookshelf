package org.zotero.android.bookshelf

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Bookshelf's own settings, kept apart from Zotero's. */
@Singleton
class BookshelfPreferences @Inject constructor(context: Context) {
    private val prefs = context.getSharedPreferences("bookshelf", Context.MODE_PRIVATE)

    private val _showCovers = MutableStateFlow(prefs.getBoolean(KEY_SHOW_COVERS, true))
    /** Library shows the cover grid (true) or Zotero's list (false). */
    val showCovers: StateFlow<Boolean> = _showCovers.asStateFlow()

    private val _coverSize = MutableStateFlow(
        CoverSize.entries.firstOrNull { it.name == prefs.getString(KEY_COVER_SIZE, null) }
            ?: CoverSize.Medium
    )
    val coverSize: StateFlow<CoverSize> = _coverSize.asStateFlow()

    private val _pageTheme = MutableStateFlow(
        PageTheme.entries.firstOrNull { it.name == prefs.getString(KEY_PAGE_THEME, null) }
            ?: PageTheme.Original
    )
    /** Reader page colour, independent of the app's light/dark interface. */
    val pageTheme: StateFlow<PageTheme> = _pageTheme.asStateFlow()

    fun setPageTheme(theme: PageTheme) {
        prefs.edit().putString(KEY_PAGE_THEME, theme.name).apply()
        _pageTheme.value = theme
    }

    private val _selectionDiagnostics = MutableStateFlow(prefs.getBoolean(KEY_SELECTION_DIAGNOSTICS, false))
    /** Temporary: on-screen selection event log in the PDF reader. */
    val selectionDiagnostics: StateFlow<Boolean> = _selectionDiagnostics.asStateFlow()

    fun setSelectionDiagnostics(on: Boolean) {
        prefs.edit().putBoolean(KEY_SELECTION_DIAGNOSTICS, on).apply()
        _selectionDiagnostics.value = on
    }

    fun setShowCovers(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_COVERS, show).apply()
        _showCovers.value = show
    }

    fun setCoverSize(size: CoverSize) {
        prefs.edit().putString(KEY_COVER_SIZE, size.name).apply()
        _coverSize.value = size
    }

    private companion object {
        const val KEY_SHOW_COVERS = "showCovers"
        const val KEY_COVER_SIZE = "coverSize"
        const val KEY_PAGE_THEME = "pageTheme"
        const val KEY_SELECTION_DIAGNOSTICS = "selectionDiagnostics"
    }
}

/** Smallest width of a cover in the grid; the grid fits as many columns as it can. */
enum class CoverSize(val minWidthDp: Int) {
    Small(92),
    Medium(118),
    Large(156),
}
