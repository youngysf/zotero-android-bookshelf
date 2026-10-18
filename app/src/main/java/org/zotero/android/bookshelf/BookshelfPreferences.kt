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

    private val _layout = MutableStateFlow(
        LibraryLayout.entries.firstOrNull { it.name == prefs.getString(KEY_LAYOUT, null) }
            ?: if (prefs.getBoolean(KEY_SHOW_COVERS, true)) LibraryLayout.Covers else LibraryLayout.List
    )
    /** How the library shows books: covers, spines on shelves, or Zotero's list. */
    val layout: StateFlow<LibraryLayout> = _layout.asStateFlow()

    fun setLayout(layout: LibraryLayout) {
        prefs.edit().putString(KEY_LAYOUT, layout.name)
            .putBoolean(KEY_SHOW_COVERS, layout != LibraryLayout.List).apply()
        _layout.value = layout
        _showCovers.value = layout != LibraryLayout.List
    }

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

    private val _shelfFilter = MutableStateFlow(
        Shelf.entries.firstOrNull { it.name == prefs.getString(KEY_SHELF_FILTER, null) }
    )
    /** Shelf the library is showing; null shows every book. */
    val shelfFilter: StateFlow<Shelf?> = _shelfFilter.asStateFlow()

    fun setShelfFilter(shelf: Shelf?) {
        prefs.edit().putString(KEY_SHELF_FILTER, shelf?.name).apply()
        _shelfFilter.value = shelf
    }

    /** Opening a book puts it on Reading; reaching the last page puts it on Finished. */
    val autoShelves: Boolean get() = prefs.getBoolean(KEY_AUTO_SHELVES, true)

    fun setShowCovers(show: Boolean) {
        setLayout(if (show) LibraryLayout.Covers else LibraryLayout.List)
    }

    fun setCoverSize(size: CoverSize) {
        prefs.edit().putString(KEY_COVER_SIZE, size.name).apply()
        _coverSize.value = size
    }

    private companion object {
        const val KEY_SHOW_COVERS = "showCovers"
        const val KEY_COVER_SIZE = "coverSize"
        const val KEY_PAGE_THEME = "pageTheme"
        const val KEY_SHELF_FILTER = "shelfFilter"
        const val KEY_LAYOUT = "layout"
        const val KEY_AUTO_SHELVES = "autoShelves"
    }
}

enum class LibraryLayout(val label: String) {
    Covers("Covers"),
    Spines("Spines"),
    List("List"),
}

/** Smallest width of a cover in the grid; the grid fits as many columns as it can. */
enum class CoverSize(val minWidthDp: Int) {
    Small(92),
    Medium(118),
    Large(156),
}
