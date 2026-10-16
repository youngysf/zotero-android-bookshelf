package org.zotero.android.bookshelf

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.zotero.android.database.objects.Attachment
import org.zotero.android.screens.allitems.data.ItemCellModel
import org.zotero.android.uicomponents.Drawables
import org.zotero.android.uicomponents.attachmentprogress.State
import java.io.File

/**
 * The library as a grid of book covers. Uses the same items, taps and
 * selection as Zotero's list, so filters, search, sorting, collections and
 * the editing toolbar all work unchanged.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun BookshelfGrid(
    gridState: LazyGridState,
    itemCellModels: SnapshotStateList<ItemCellModel>,
    coverSize: CoverSize,
    isEditing: Boolean,
    isRefreshing: Boolean,
    isItemSelected: (key: String) -> Boolean,
    getItemAccessory: (itemKey: String) -> ItemCellModel.Accessory?,
    attachmentFor: (itemKey: String) -> Attachment?,
    coverFor: suspend (Attachment?) -> File?,
    onItemTapped: (item: ItemCellModel) -> Unit,
    onItemLongTapped: (key: String) -> Unit,
    onInfoTapped: (key: String) -> Unit,
    onStartSync: () -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onStartSync,
    ) {
        LazyVerticalGrid(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = BottomAppBarDefaults.FlexibleBottomAppBarHeight),
            state = gridState,
            columns = GridCells.Adaptive(minSize = coverSize.minWidthDp.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(items = itemCellModels, key = { it.key }) { item ->
                BookCard(
                    modifier = Modifier.animateItem(),
                    cell = item,
                    accessory = getItemAccessory(item.key),
                    isEditing = isEditing,
                    isSelected = isItemSelected(item.key),
                    attachmentFor = attachmentFor,
                    coverFor = coverFor,
                    onTap = { onItemTapped(item) },
                    onLongTap = { onItemLongTapped(item.key) },
                    onInfo = { onInfoTapped(item.key) },
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Spacer(modifier = Modifier.windowInsetsPadding(NavigationBarDefaults.windowInsets))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookCard(
    modifier: Modifier,
    cell: ItemCellModel,
    accessory: ItemCellModel.Accessory?,
    isEditing: Boolean,
    isSelected: Boolean,
    attachmentFor: (itemKey: String) -> Attachment?,
    coverFor: suspend (Attachment?) -> File?,
    onTap: () -> Unit,
    onLongTap: () -> Unit,
    onInfo: () -> Unit,
) {
    val state = (accessory as? ItemCellModel.Accessory.attachment)?.state
    // Looked up again whenever the download state changes, so a cover appears
    // as soon as a book finishes downloading.
    val coverFile by produceState<File?>(initialValue = null, cell.key, state) {
        value = coverFor(attachmentFor(cell.key))
    }
    val shape = RoundedCornerShape(4.dp)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                role = Role.Button,
                onClick = onTap,
                onLongClick = onLongTap,
            )
            .then(
                if (isSelected) {
                    Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                } else {
                    Modifier
                }
            )
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .shadow(elevation = 3.dp, shape = shape)
                .clip(shape)
                .background(Color.White)
        ) {
            TitleCover(title = cell.title)
            if (coverFile != null) {
                // Drawn over the title cover, which shows until the image has loaded.
                AsyncImage(
                    model = coverFile,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            DownloadBadge(state = state, modifier = Modifier.align(Alignment.BottomEnd))
            if (isEditing) {
                SelectionMark(
                    selected = isSelected,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = cell.title.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 16.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (cell.subtitle.isNotBlank()) {
                    Text(
                        text = cell.subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (!isEditing) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .combinedClickable(role = Role.Button, onClick = onInfo),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(Drawables.info_24px),
                        contentDescription = "Details",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/** Shown until a real cover is ready, and for books without a PDF on this phone. */
@Composable
private fun TitleCover(title: String) {
    val hue = remember(title) {
        var h = 0
        for (c in title) h = (h * 31 + c.code)
        ((h % 360) + 360) % 360
    }
    val base = Color.hsl(hue.toFloat(), 0.32f, 0.38f)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(base)
            // A soft shadow down the left edge, like a book's spine.
            .background(
                Brush.horizontalGradient(
                    0f to Color.Black.copy(alpha = 0.18f),
                    0.07f to Color.Transparent,
                )
            )
            .padding(horizontal = 10.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title.ifBlank { "Untitled" },
            color = Color.hsl(hue.toFloat(), 0.4f, 0.94f),
            fontFamily = FontFamily.Serif,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center,
            maxLines = 6,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DownloadBadge(state: State?, modifier: Modifier) {
    when (state) {
        is State.progress -> {
            Surface(
                modifier = modifier.padding(6.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.6f),
            ) {
                CircularProgressIndicator(
                    progress = { state.progress / 100f },
                    modifier = Modifier
                        .padding(4.dp)
                        .size(18.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            }
        }
        is State.ready -> {
            val kind = state.attachmentKind as? Attachment.Kind.file
            if (kind?.location == Attachment.FileLocation.remote) {
                Surface(
                    modifier = modifier.padding(6.dp),
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.55f),
                ) {
                    Icon(
                        painter = painterResource(Drawables.bookshelf_cloud_24px),
                        contentDescription = "Not downloaded",
                        tint = Color.White,
                        modifier = Modifier
                            .padding(4.dp)
                            .size(16.dp),
                    )
                }
            }
        }
        else -> Unit
    }
}

@Composable
private fun SelectionMark(selected: Boolean, modifier: Modifier) {
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.35f)
            )
            .border(2.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                painter = painterResource(Drawables.bookshelf_check_24px),
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
