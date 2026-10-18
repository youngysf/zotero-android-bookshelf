package org.zotero.android.bookshelf

import io.realm.Realm
import io.realm.kotlin.where
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.zotero.android.database.DbResponseRequest
import org.zotero.android.database.DbWrapperMain
import org.zotero.android.database.objects.RCollection
import org.zotero.android.database.requests.library
import org.zotero.android.sync.Collection
import org.zotero.android.sync.CollectionIdentifier
import org.zotero.android.sync.LibraryIdentifier
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** A collection in the library's menu, with how deeply it's nested. */
data class BookshelfCollectionEntry(
    val collection: Collection,
    val depth: Int,
)

private class ReadCollectionTreeDbRequest(
    private val libraryId: LibraryIdentifier,
) : DbResponseRequest<List<BookshelfCollectionEntry>> {
    override val needsWrite: Boolean get() = false

    override fun process(database: Realm): List<BookshelfCollectionEntry> {
        val all = database.where<RCollection>()
            .library(libraryId)
            .and()
            .equalTo("deleted", false)
            .and()
            .equalTo("trash", false)
            .findAll()
        val byParent = all.groupBy { it.parentKey }
        val keys = all.map { it.key }.toSet()
        val out = mutableListOf<BookshelfCollectionEntry>()
        fun add(parent: String?, depth: Int) {
            val children = byParent[parent].orEmpty().sortedBy { it.name.lowercase() }
            for (c in children) {
                val count = c.items.count { !it.deleted && !it.trash && it.parent == null }
                out += BookshelfCollectionEntry(Collection.initWithCollection(c, count), depth)
                add(c.key, depth + 1)
            }
        }
        add(null, 0)
        // Collections whose parent no longer exists still show, at the top level.
        for (c in all) {
            if (c.parentKey != null && c.parentKey !in keys) {
                val count = c.items.count { !it.deleted && !it.trash && it.parent == null }
                out += BookshelfCollectionEntry(Collection.initWithCollection(c, count), 0)
                add(c.key, 1)
            }
        }
        return out
    }
}

@Singleton
class BookshelfCollections @Inject constructor(
    private val dbWrapperMain: DbWrapperMain,
) {
    /** Every book, then the collection tree, then Unfiled and Trash. */
    suspend fun menu(libraryId: LibraryIdentifier): List<BookshelfCollectionEntry> = withContext(Dispatchers.IO) {
        val tree = if (!dbWrapperMain.isInitialized) emptyList() else try {
            dbWrapperMain.realmDbStorage.perform(ReadCollectionTreeDbRequest(libraryId))
        } catch (e: Throwable) {
            Timber.w(e, "Bookshelf: couldn't read collections")
            emptyList()
        }
        listOf(BookshelfCollectionEntry(Collection.initWithCustomType(CollectionIdentifier.CustomType.all), 0)) +
            tree +
            listOf(
                BookshelfCollectionEntry(Collection.initWithCustomType(CollectionIdentifier.CustomType.unfiled), 0),
                BookshelfCollectionEntry(Collection.initWithCustomType(CollectionIdentifier.CustomType.trash), 0),
            )
    }
}
