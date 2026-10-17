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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
    shelfFilter: Shelf?,
    snapshot: BookshelfSnapshot,
    pageCounts: Map<String, Int>,
    showContinueReading: Boolean,
    onShelfFilter: (Shelf?) -> Unit,
    onSetShelf: (key: String, shelf: Shelf?) -> Unit,
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
            item(span = { GridItemSpan(maxLineSpan) }, key = "bookshelf-shelves") {
                ShelfTabs(
                    selected = shelfFilter,
                    counts = Shelf.entries.associateWith { shelf ->
                        itemCellModels.count { snapshot.shelves[it.key] == shelf }
                    },
                    onSelect = onShelfFilter,
                )
            }
            if (showContinueReading && shelfFilter == null && !isEditing) {
                val recent = itemCellModels
                    .filter { snapshot.lastRead.containsKey(it.key) && snapshot.shelves[it.key] != Shelf.Finished }
                    .sortedByDescending { snapshot.lastRead[it.key] }
                    .take(12)
                if (recent.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "bookshelf-continue") {
                        ContinueReading(
                            books = recent,
                            progressFor = { key ->
                                val attachment = attachmentFor(key)
                                BookshelfProgress.fraction(attachment, attachment?.let { snapshot.positions[it.key] }, pageCounts)
                            },
                            attachmentFor = attachmentFor,
                            coverFor = coverFor,
                            onTap = onItemTapped,
                        )
                    }
                }
            }
            val shown = if (shelfFilter == null) itemCellModels else itemCellModels.filter { snapshot.shelves[it.key] == shelfFilter }
            if (shown.isEmpty() && shelfFilter != null) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "bookshelf-empty") {
                    Text(
                        text = "No books on ${shelfFilter.label} here yet. Use a book's ⋮ menu to add one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 24.dp),
                    )
                }
            }
            items(items = shown, key = { it.key }) { item ->
                val attachment = attachmentFor(item.key)
                BookCard(
                    modifier = Modifier.animateItem(),
                    cell = item,
                    shelf = snapshot.shelves[item.key],
                    progress = BookshelfProgress.fraction(attachment, attachment?.let { snapshot.positions[it.key] }, pageCounts),
                    onSetShelf = { onSetShelf(item.key, it) },
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
    shelf: Shelf?,
    progress: Float?,
    onSetShelf: (Shelf?) -> Unit,
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
            ProgressOverlay(
                progress = progress,
                finished = shelf == Shelf.Finished,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
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
                BookMenu(shelf = shelf, onInfo = onInfo, onSetShelf = onSetShelf)
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

/** All / Want to read / Reading / Finished. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ShelfTabs(selected: Shelf?, counts: Map<Shelf, Int>, onSelect: (Shelf?) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item {
            FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("All") })
        }
        rowItems(Shelf.entries) { shelf ->
            val count = counts[shelf] ?: 0
            FilterChip(
                selected = selected == shelf,
                onClick = { onSelect(if (selected == shelf) null else shelf) },
                label = { Text(if (count > 0) "${shelf.label}  $count" else shelf.label) },
            )
        }
    }
}

/** Recently read books that aren't finished, newest first. */
@Composable
private fun ContinueReading(
    books: List<ItemCellModel>,
    progressFor: (String) -> Float?,
    attachmentFor: (itemKey: String) -> Attachment?,
    coverFor: suspend (Attachment?) -> File?,
    onTap: (ItemCellModel) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Continue reading",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            rowItems(books, key = { "continue-" + it.key }) { cell ->
                val coverFile by produceState<File?>(initialValue = null, cell.key) {
                    value = coverFor(attachmentFor(cell.key))
                }
                val progress = progressFor(cell.key)
                val shape = RoundedCornerShape(4.dp)
                Column(
                    modifier = Modifier
                        .width(84.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .combinedClickable(role = Role.Button, onClick = { onTap(cell) }),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                            .shadow(elevation = 2.dp, shape = shape)
                            .clip(shape)
                            .background(Color.White),
                    ) {
                        TitleCover(title = cell.title)
                        if (coverFile != null) {
                            AsyncImage(
                                model = coverFile,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                alignment = Alignment.TopCenter,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        ProgressOverlay(progress = progress, finished = false, modifier = Modifier.align(Alignment.BottomCenter))
                    }
                    Text(
                        text = cell.title.ifBlank { "Untitled" },
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 15.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), modifier = Modifier.padding(top = 12.dp))
    }
}

/** A thin bar along the bottom of the cover with the percentage read. */
@Composable
private fun ProgressOverlay(progress: Float?, finished: Boolean, modifier: Modifier) {
    if (finished) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            color = Color.Black.copy(alpha = 0.6f),
        ) {
            Text(
                text = "Finished",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 3.dp),
            )
        }
        return
    }
    if (progress == null) return
    Column(modifier = modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .align(Alignment.Start)
                .padding(start = 5.dp, bottom = 4.dp),
            shape = RoundedCornerShape(4.dp),
            color = Color.Black.copy(alpha = 0.6f),
        ) {
            Text(
                text = "${(progress * 100).toInt().coerceIn(if (progress > 0f) 1 else 0, 100)}%",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color.Black.copy(alpha = 0.25f),
            drawStopIndicator = {},
            gapSize = 0.dp,
        )
    }
}

/** The ⋮ button under a cover: shelves and details. */
@Composable
private fun BookMenu(shelf: Shelf?, onInfo: () -> Unit, onSetShelf: (Shelf?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .combinedClickable(role = Role.Button, onClick = { open = true }),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(Drawables.bookshelf_more_vert_24px),
                contentDescription = "Book options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (option in Shelf.entries) {
                DropdownMenuItem(
                    text = { Text(option.label) },
                    leadingIcon = {
                        if (option == shelf) {
                            Icon(
                                painter = painterResource(Drawables.bookshelf_check_24px),
                                contentDescription = "Current shelf",
                                modifier = Modifier.size(18.dp),
                            )
                        } else {
                            Spacer(modifier = Modifier.size(18.dp))
                        }
                    },
                    onClick = {
                        open = false
                        onSetShelf(if (option == shelf) null else option)
                    },
                )
            }
            if (shelf != null) {
                DropdownMenuItem(
                    text = { Text("Remove from ${shelf.label}") },
                    leadingIcon = { Spacer(modifier = Modifier.size(18.dp)) },
                    onClick = {
                        open = false
                        onSetShelf(null)
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Details") },
                leadingIcon = {
                    Icon(
                        painter = painterResource(Drawables.info_24px),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                onClick = {
                    open = false
                    onInfo()
                },
            )
        }
    }
}
