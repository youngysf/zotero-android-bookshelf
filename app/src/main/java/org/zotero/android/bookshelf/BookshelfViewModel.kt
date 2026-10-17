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
    progress: BookshelfProgress,
) : ViewModel() {
    val showCovers: StateFlow<Boolean> = preferences.showCovers
    val coverSize: StateFlow<CoverSize> = preferences.coverSize
    val shelfFilter: StateFlow<Shelf?> = preferences.shelfFilter
    val snapshot: StateFlow<BookshelfSnapshot> = shelves.snapshot
    val pageCounts: StateFlow<Map<String, Int>> = progress.counts

    fun setShowCovers(show: Boolean) = preferences.setShowCovers(show)
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
