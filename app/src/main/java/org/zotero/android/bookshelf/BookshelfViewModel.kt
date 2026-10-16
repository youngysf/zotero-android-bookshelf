package org.zotero.android.bookshelf

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import org.zotero.android.database.objects.Attachment
import java.io.File
import javax.inject.Inject

/** Holds the Bookshelf view settings and cover lookups for the library screen. */
@HiltViewModel
class BookshelfViewModel @Inject constructor(
    private val preferences: BookshelfPreferences,
    private val covers: BookshelfCovers,
) : ViewModel() {
    val showCovers: StateFlow<Boolean> = preferences.showCovers
    val coverSize: StateFlow<CoverSize> = preferences.coverSize

    fun setShowCovers(show: Boolean) = preferences.setShowCovers(show)
    fun setCoverSize(size: CoverSize) = preferences.setCoverSize(size)

    suspend fun coverFor(attachment: Attachment?): File? {
        return attachment?.let { covers.coverFor(it) }
    }
}
