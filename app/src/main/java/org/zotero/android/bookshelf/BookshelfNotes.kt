package org.zotero.android.bookshelf

import io.realm.Realm
import io.realm.kotlin.where
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.zotero.android.database.DbResponseRequest
import org.zotero.android.database.DbWrapperMain
import org.zotero.android.database.objects.FieldKeys
import org.zotero.android.database.objects.ItemTypes
import org.zotero.android.database.objects.RItem
import org.zotero.android.database.requests.key
import org.zotero.android.database.requests.library
import org.zotero.android.sync.LibraryIdentifier
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** One highlight, underline or note, with the book it belongs to. */
data class BookshelfNote(
    val key: String,
    val type: String,
    val text: String,
    val comment: String,
    val color: String,
    val pageLabel: String,
    val tags: List<String>,
    val sortIndex: String,
    val dateModified: Long,
    val dateAdded: Long,
    val attachmentKey: String,
    /** The book: the attachment's parent item, or the attachment itself for a standalone PDF. */
    val bookKey: String,
    val bookTitle: String,
    val parentKey: String?,
)

/** What the book page shows besides the cover. */
data class BookshelfBookInfo(
    val key: String,
    val title: String,
    val authors: String,
    val year: String,
    val publisher: String,
    val abstract: String,
    val collections: List<String>,
    val tags: List<String>,
    val notes: List<BookshelfNote>,
    /** The book's bookmark, if it has one. */
    val bookmark: BookshelfNote? = null,
)

private fun RItem.field(key: String): String = fields.firstOrNull { it.key == key }?.value ?: ""

private fun RItem.isLive(): Boolean = !deleted && !trash

private fun BookshelfNote.isBookmark() = BookshelfBookmark.isBookmark(type, comment)

private fun noteOf(annotation: RItem): BookshelfNote? {
    val attachment = annotation.parent ?: return null
    if (!attachment.isLive()) return null
    val book = attachment.parent ?: attachment
    if (!book.isLive()) return null
    return BookshelfNote(
        key = annotation.key,
        type = annotation.field(FieldKeys.Item.Annotation.type),
        text = annotation.field(FieldKeys.Item.Annotation.text),
        comment = annotation.field(FieldKeys.Item.Annotation.comment),
        color = annotation.field(FieldKeys.Item.Annotation.color),
        pageLabel = annotation.field(FieldKeys.Item.Annotation.pageLabel),
        tags = annotation.tags?.mapNotNull { it.tag?.name } ?: emptyList(),
        sortIndex = annotation.field(FieldKeys.Item.Annotation.sortIndex),
        dateModified = annotation.dateModified.time,
        dateAdded = annotation.dateAdded.time,
        attachmentKey = attachment.key,
        bookKey = book.key,
        bookTitle = book.displayTitle,
        parentKey = attachment.parent?.key,
    )
}

private class ReadLibraryNotesDbRequest(
    private val libraryId: LibraryIdentifier,
) : DbResponseRequest<List<BookshelfNote>> {
    override val needsWrite: Boolean get() = false

    override fun process(database: Realm): List<BookshelfNote> {
        return database.where<RItem>()
            .library(libraryId)
            .and()
            .equalTo("rawType", ItemTypes.annotation)
            .and()
            .equalTo("deleted", false)
            .findAll()
            .mapNotNull { noteOf(it) }
            // Only highlights, underlines and notes with words in them (not the bookmark).
            .filter { (it.text.isNotBlank() || it.comment.isNotBlank()) && !it.isBookmark() }
    }
}

private class ReadBookInfoDbRequest(
    private val libraryId: LibraryIdentifier,
    private val key: String,
) : DbResponseRequest<BookshelfBookInfo> {
    override val needsWrite: Boolean get() = false

    override fun process(database: Realm): BookshelfBookInfo {
        val book = database.where<RItem>().key(key, libraryId).findFirst()
            ?: return BookshelfBookInfo(key, "", "", "", "", "", emptyList(), emptyList(), emptyList())
        val attachments = if (book.rawType == ItemTypes.attachment) listOf(book) else book.children?.filter { it.rawType == ItemTypes.attachment && it.isLive() } ?: emptyList()
        val all = attachments.flatMap { attachment ->
            attachment.children?.filter { it.rawType == ItemTypes.annotation && !it.deleted }?.mapNotNull { noteOf(it) } ?: emptyList()
        }
        val bookmark = all.filter { it.isBookmark() }.maxByOrNull { it.dateModified }
        val notes = all
            .filter { (it.text.isNotBlank() || it.comment.isNotBlank()) && !it.isBookmark() }
            .sortedWith(compareBy({ it.attachmentKey }, { it.sortIndex }))
        val authors = book.creators
            ?.sortedBy { it.orderId }
            ?.map { creator ->
                creator.name.ifBlank { listOf(creator.firstName, creator.lastName).filter { it.isNotBlank() }.joinToString(" ") }
            }
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        val date = book.field(FieldKeys.Item.date)
        return BookshelfBookInfo(
            key = book.key,
            title = book.displayTitle,
            authors = authors.joinToString(", "),
            year = Regex("\\d{4}").find(date)?.value ?: "",
            publisher = book.field(FieldKeys.Item.publisher),
            abstract = book.field(FieldKeys.Item.abstractN),
            collections = book.collections?.filter { !it.deleted }?.map { it.name } ?: emptyList(),
            tags = book.tags?.mapNotNull { it.tag?.name }?.filter { Shelf.ofTag(it) == null } ?: emptyList(),
            notes = notes,
            bookmark = bookmark,
        )
    }
}

@Singleton
class BookshelfNotes @Inject constructor(
    private val dbWrapperMain: DbWrapperMain,
) {
    suspend fun all(libraryId: LibraryIdentifier): List<BookshelfNote> = withContext(Dispatchers.IO) {
        if (!dbWrapperMain.isInitialized) return@withContext emptyList()
        try {
            dbWrapperMain.realmDbStorage.perform(ReadLibraryNotesDbRequest(libraryId))
        } catch (e: Throwable) {
            Timber.w(e, "Bookshelf: couldn't read highlights")
            emptyList()
        }
    }

    suspend fun book(libraryId: LibraryIdentifier, key: String): BookshelfBookInfo? = withContext(Dispatchers.IO) {
        if (!dbWrapperMain.isInitialized) return@withContext null
        try {
            dbWrapperMain.realmDbStorage.perform(ReadBookInfoDbRequest(libraryId, key))
        } catch (e: Throwable) {
            Timber.w(e, "Bookshelf: couldn't read book $key")
            null
        }
    }
}
