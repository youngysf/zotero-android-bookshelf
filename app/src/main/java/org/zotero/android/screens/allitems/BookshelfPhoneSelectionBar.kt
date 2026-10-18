package org.zotero.android.screens.allitems

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.zotero.android.bookshelf.BookshelfBarAction
import org.zotero.android.bookshelf.BookshelfSelectionBar
import org.zotero.android.bookshelf.Shelf
import org.zotero.android.uicomponents.Drawables
import org.zotero.android.uicomponents.Strings

/**
 * Bookshelf: while books are selected on a phone, a floating bar with Shelf,
 * Collection, Tag and Delete; every other Zotero action is under More.
 */
@Composable
internal fun BookshelfPhoneSelectionBar(
    viewModel: AllItemsViewModel,
    viewState: AllItemsViewState,
    onShelf: (Shelf?) -> Unit,
    onTag: () -> Unit,
    modifier: Modifier,
) {
    val error = MaterialTheme.colorScheme.error
    val count = viewState.selectedKeys?.size ?: 0
    val zoteroActions = when {
        count == 1 -> editingSingleItemSelectedActions(viewModel, viewState, error)
        count > 1 -> editingMultipleItemsSelectedActions(viewModel, viewState, error)
        else -> null
    }
    val shownElsewhere = setOf(
        Strings.items_action_add_to_collection,
        Strings.move_to_trash,
        Strings.restore,
        Strings.delete,
    )
    val more = (zoteroActions?.panelItems.orEmpty() + zoteroActions?.overflowItems.orEmpty())
        .filter { it.overflowTextResId !in shownElsewhere }
        .map { BookshelfBarAction(stringResource(it.overflowTextResId), it.iconRes, it.iconTint ?: it.textColor, it.onClick) }

    val main = if (viewState.isCollectionTrash) {
        listOf(
            BookshelfBarAction(stringResource(Strings.restore), Drawables.restore_trash) { viewModel.onTrashRestore() },
            BookshelfBarAction(stringResource(Strings.delete), Drawables.delete_24px, error) { viewModel.onTrashDelete() },
        )
    } else {
        listOf(
            BookshelfBarAction("Collection", Drawables.bookshelf_folder_24px) { viewModel.onAddToCollection() },
            BookshelfBarAction("Tag", Drawables.bookshelf_label_24px, onClick = onTag),
            BookshelfBarAction("Delete", Drawables.delete_24px, error) { viewModel.onMoveToTrash() },
        )
    }
    BookshelfSelectionBar(
        count = count,
        onShelf = onShelf,
        mainActions = main,
        moreActions = more,
        modifier = modifier,
    )
}
