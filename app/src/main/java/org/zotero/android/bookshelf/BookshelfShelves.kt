package org.zotero.android.bookshelf

import io.realm.Realm
import io.realm.kotlin.where
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.zotero.android.database.DbResponseRequest
import org.zotero.android.database.DbWrapperMain
import org.zotero.android.database.objects.RItem
import org.zotero.android.database.objects.RPageIndex
import org.zotero.android.database.objects.RTypedTag
import org.zotero.android.database.requests.EditTagsForItemDbRequest
import org.zotero.android.database.requests.baseItemPredicates
import org.zotero.android.database.requests.key
import org.zotero.android.database.requests.library
import org.zotero.android.sync.LibraryIdentifier
import org.zotero.android.sync.Tag
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shelves are ordinary Zotero tags on the book (the top-level item), the same
 * ones the desktop Bookshelf plugin uses, so they sync both ways.
 */
enum class Shelf(val tag: String, val label: String) {
    Want("Want to read", "Want to read"),
    Reading("Reading", "Reading"),
    Finished("Finished", "Finished");

    companion object {
        fun ofTag(name: String): Shelf? = entries.firstOrNull { it.tag == name }
    }
}

/** What the library screen needs to know about shelves and reading, read in one go. */
data class BookshelfSnapshot(
    /** Book (top-level item) key → its shelf. */
    val shelves: Map<String, Shelf> = emptyMap(),
    /** Attachment key → saved reading position (page index for PDFs). */
    val positions: Map<String, String> = emptyMap(),
    /** Book key → when it was last read (ms), for "Continue reading". */
    val lastRead: Map<String, Long> = emptyMap(),
    /** Book key → when it was added (ms), for "Recently added". */
    val added: Map<String, Long> = emptyMap(),
)

private class ReadBookshelfSnapshotDbRequest(
    private val libraryId: LibraryIdentifier,
) : DbResponseRequest<BookshelfSnapshot> {
    override val needsWrite: Boolean get() = false

    override fun process(database: Realm): BookshelfSnapshot {
        val shelves = HashMap<String, Shelf>()
        val tagged = database.where<RTypedTag>()
            .`in`("tag.name", Shelf.entries.map { it.tag }.toTypedArray())
            .findAll()
        for (typed in tagged) {
            val item = typed.item ?: continue
            if (item.deleted || item.libraryId != libraryId) continue
            val shelf = Shelf.ofTag(typed.tag?.name ?: continue) ?: continue
            // If a book somehow has several shelf tags, the later shelf wins.
            val current = shelves[item.key]
            if (current == null || shelf.ordinal > current.ordinal) shelves[item.key] = shelf
        }

        val positions = HashMap<String, String>()
        for (index in database.where<RPageIndex>().library(libraryId).findAll()) {
            if (!index.deleted) positions[index.key] = index.index
        }

        val lastRead = HashMap<String, Long>()
        val added = HashMap<String, Long>()
        val books = database.where<RItem>()
            .baseItemPredicates(isTrash = false, libraryId = libraryId)
            .findAll()
        for (item in books) {
            item.effectiveLastRead?.let { lastRead[item.key] = it.time }
            added[item.key] = item.dateAdded.time
        }
        return BookshelfSnapshot(shelves, positions, lastRead, added)
    }
}

private class ReadItemTagsDbRequest(
    private val key: String,
    private val libraryId: LibraryIdentifier,
) : DbResponseRequest<List<Tag>> {
    override val needsWrite: Boolean get() = false

    override fun process(database: Realm): List<Tag> {
        val item = database.where<RItem>().key(key, libraryId).findFirst() ?: return emptyList()
        return item.tags?.map { Tag(it) } ?: emptyList()
    }
}

private class ReadTagNamesDbRequest(
    private val libraryId: LibraryIdentifier,
) : DbResponseRequest<List<String>> {
    override val needsWrite: Boolean get() = false

    override fun process(database: Realm): List<String> {
        return database.where<org.zotero.android.database.objects.RTag>()
            .library(libraryId)
            .findAll()
            .map { it.name }
            .filter { it.isNotBlank() && Shelf.ofTag(it) == null }
            .distinct()
            .sortedBy { it.lowercase() }
    }
}

@Singleton
class BookshelfShelves @Inject constructor(
    private val dbWrapperMain: DbWrapperMain,
) {
    private val _snapshot = MutableStateFlow(BookshelfSnapshot())
    val snapshot: StateFlow<BookshelfSnapshot> = _snapshot.asStateFlow()

    suspend fun refresh(libraryId: LibraryIdentifier) {
        if (!dbWrapperMain.isInitialized) return
        val result = withContext(Dispatchers.IO) {
            try {
                dbWrapperMain.realmDbStorage.perform(ReadBookshelfSnapshotDbRequest(libraryId))
            } catch (e: Throwable) {
                Timber.w(e, "Bookshelf: couldn't read shelves")
                null
            }
        }
        if (result != null) _snapshot.value = result
    }

    suspend fun shelfOf(libraryId: LibraryIdentifier, bookKey: String): Shelf? {
        return withContext(Dispatchers.IO) {
            try {
                dbWrapperMain.realmDbStorage.perform(ReadItemTagsDbRequest(bookKey, libraryId))
                    .mapNotNull { Shelf.ofTag(it.name) }
                    .maxByOrNull { it.ordinal }
            } catch (e: Throwable) {
                null
            }
        }
    }

    /** Puts books on a shelf (null takes them off all shelves). Other tags are kept. */
    suspend fun setShelf(libraryId: LibraryIdentifier, bookKeys: Collection<String>, shelf: Shelf?) {
        withContext(Dispatchers.IO) {
            for (key in bookKeys) {
                try {
                    val tags = dbWrapperMain.realmDbStorage.perform(ReadItemTagsDbRequest(key, libraryId))
                    val kept = tags.filter { Shelf.ofTag(it.name) == null }
                    val updated = if (shelf != null) kept + Tag(name = shelf.tag, color = "") else kept
                    if (updated.map { it.name }.toSet() == tags.map { it.name }.toSet()) continue
                    dbWrapperMain.realmDbStorage.perform(
                        EditTagsForItemDbRequest(key = key, libraryId = libraryId, tags = updated)
                    )
                } catch (e: Throwable) {
                    Timber.w(e, "Bookshelf: couldn't change shelf for $key")
                }
            }
        }
        // Update the screen straight away rather than waiting for a refresh.
        val current = _snapshot.value
        val shelves = current.shelves.toMutableMap()
        for (key in bookKeys) if (shelf == null) shelves.remove(key) else shelves[key] = shelf
        _snapshot.value = current.copy(shelves = shelves)
    }

    /** Tags in this library, for suggestions when tagging books (shelf tags left out). */
    suspend fun tagNames(libraryId: LibraryIdentifier): List<String> = withContext(Dispatchers.IO) {
        try {
            dbWrapperMain.realmDbStorage.perform(ReadTagNamesDbRequest(libraryId))
        } catch (e: Throwable) {
            emptyList()
        }
    }

    /** Adds a tag to several books at once, keeping their other tags. */
    suspend fun addTag(libraryId: LibraryIdentifier, bookKeys: Collection<String>, name: String) {
        val tag = name.trim()
        if (tag.isEmpty()) return
        withContext(Dispatchers.IO) {
            for (key in bookKeys) {
                try {
                    val tags = dbWrapperMain.realmDbStorage.perform(ReadItemTagsDbRequest(key, libraryId))
                    if (tags.any { it.name == tag }) continue
                    dbWrapperMain.realmDbStorage.perform(
                        EditTagsForItemDbRequest(key = key, libraryId = libraryId, tags = tags + Tag(name = tag, color = ""))
                    )
                } catch (e: Throwable) {
                    Timber.w(e, "Bookshelf: couldn't tag $key")
                }
            }
        }
    }
}
