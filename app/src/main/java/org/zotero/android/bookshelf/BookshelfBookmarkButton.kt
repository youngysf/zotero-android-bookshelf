package org.zotero.android.bookshelf

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import org.zotero.android.uicomponents.Drawables

/**
 * The reader's ribbon button. Filled when this page holds the bookmark (tap to
 * take it out); outlined otherwise (tap to move the bookmark here). When the
 * bookmark is on another page, a small pill with its page number jumps there.
 */
@Composable
fun BookshelfBookmarkActions(
    bookmark: BookshelfBookmarkState?,
    currentPage: Int?,
    onToggle: () -> Unit,
    onGoTo: () -> Unit,
) {
    val here = bookmark != null && bookmark.pageIndex == currentPage
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (bookmark != null && !here) {
            Surface(
                onClick = onGoTo,
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Row(
                    modifier = Modifier.padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(Drawables.bookshelf_ribbon_filled_24px),
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = "p. " + bookmark.pageLabel.ifBlank { (bookmark.pageIndex + 1).toString() },
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
        IconButton(onClick = onToggle, enabled = currentPage != null) {
            Icon(
                painter = painterResource(
                    if (here) Drawables.bookshelf_ribbon_filled_24px else Drawables.bookshelf_ribbon_24px
                ),
                contentDescription = if (here) "Remove bookmark" else "Bookmark this page",
                tint = if (here) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
