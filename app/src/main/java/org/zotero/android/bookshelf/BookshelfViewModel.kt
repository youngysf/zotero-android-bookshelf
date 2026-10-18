package org.zotero.android.bookshelf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.zotero.android.database.objects.Attachment
import org.zotero.android.sync.LibraryIdentifier
import java.io.File
import javax.inject.Inject

/** Holds the Bookshelf view settings, shelves, progress and cover lookups for the library screen. */
@HiltViewModel
class BookshelfViewModel @Inject constructor(
    private val preferences: BookshelfPreferences,
    private val covers: BookshelfCovers,
    private val shelves: BookshelfShelves,
    private val notes: BookshelfNotes,
    private val collections: BookshelfCollections,
    progress: BookshelfProgress,
) : ViewModel() {
    val showCovers: StateFlow<Boolean> = preferences.showCovers
    val layout: StateFlow<LibraryLayout> = preferences.layout
    val coverSize: StateFlow<CoverSize> = preferences.coverSize
    val shelfFilter: StateFlow<Shelf?> = preferences.shelfFilter
    val snapshot: StateFlow<BookshelfSnapshot> = shelves.snapshot
    val pageCounts: StateFlow<Map<String, Int>> = progress.counts

    fun setShowCovers(show: Boolean) = preferences.setShowCovers(show)
    fun setLayout(layout: LibraryLayout) = preferences.setLayout(layout)

    val appTheme: StateFlow<AppTheme> = preferences.appTheme
    fun setAppTheme(theme: AppTheme) = preferences.setAppTheme(theme)
    val autoShelves: StateFlow<Boolean> = preferences.autoShelvesFlow
    fun setAutoShelves(on: Boolean) = preferences.setAutoShelves(on)
    val pageTheme: StateFlow<PageTheme> = preferences.pageTheme
    fun setPageTheme(theme: PageTheme) = preferences.setPageTheme(theme)
    val readerBrightness: StateFlow<Float> = preferences.readerBrightness
    fun setReaderBrightness(value: Float) = preferences.setReaderBrightness(value)

    suspend fun tintFor(attachment: Attachment?): Int? {
        return attachment?.let { covers.tintFor(it) }
    }

    suspend fun collectionMenu(libraryId: LibraryIdentifier): List<BookshelfCollectionEntry> = collections.menu(libraryId)

    suspend fun notes(libraryId: LibraryIdentifier): List<BookshelfNote> = notes.all(libraryId)

    suspend fun bookInfo(libraryId: LibraryIdentifier, key: String): BookshelfBookInfo? = notes.book(libraryId, key)

    suspend fun tagNames(libraryId: LibraryIdentifier): List<String> = shelves.tagNames(libraryId)

    fun addTag(libraryId: LibraryIdentifier, bookKeys: Collection<String>, name: String) {
        viewModelScope.launch { shelves.addTag(libraryId, bookKeys, name) }
    }
    fun setCoverSize(size: CoverSize) = preferences.setCoverSize(size)
    fun setShelfFilter(shelf: Shelf?) = preferences.setShelfFilter(shelf)

    suspend fun coverFor(attachment: Attachment?): File? {
        return attachment?.let { covers.coverFor(it) }
    }

    fun refresh(libraryId: LibraryIdentifier) {
        viewModelScope.launch { shelves.refresh(libraryId) }
    }

    fun setShelf(libraryId: LibraryIdentifier, bookKeys: Collection<String>, shelf: Shelf?) {
        viewModelScope.launch { shelves.setShelf(libraryId, bookKeys, shelf) }
    }
}
