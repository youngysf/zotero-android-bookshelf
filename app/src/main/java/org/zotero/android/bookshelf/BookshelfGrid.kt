package org.zotero.android.bookshelf

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.zotero.android.database.objects.Attachment
import org.zotero.android.screens.allitems.data.ItemCellModel
import org.zotero.android.uicomponents.Drawables
import org.zotero.android.uicomponents.attachmentprogress.State
import java.io.File

/** What the library grid can ask the screen to do. */
internal class BookshelfGridActions(
    val onShelfFilter: (Shelf?) -> Unit,
    val onSetShelf: (key: String, shelf: Shelf?) -> Unit,
    val onItemTapped: (item: ItemCellModel) -> Unit,
    val onItemLongTapped: (key: String) -> Unit,
    val onBookPage: (key: String) -> Unit,
    val onDetails: (key: String) -> Unit,
    val onAdd: () -> Unit,
    val onStartSync: () -> Unit,
)

/** What the library grid shows. */
internal class BookshelfGridData(
    val itemCellModels: SnapshotStateList<ItemCellModel>,
    val layout: LibraryLayout,
    val coverSize: CoverSize,
    val shelfFilter: Shelf?,
    val snapshot: BookshelfSnapshot,
    val pageCounts: Map<String, Int>,
    val searchTerm: String?,
    val isEditing: Boolean,
    val isRefreshing: Boolean,
    val isItemSelected: (key: String) -> Boolean,
    val getItemAccessory: (itemKey: String) -> ItemCellModel.Accessory?,
    val attachmentFor: (itemKey: String) -> Attachment?,
    val coverFor: suspend (Attachment?) -> File?,
    val tintFor: suspend (Attachment?) -> Int?,
) {
    fun progressFor(key: String): Float? {
        val attachment = attachmentFor(key)
        return BookshelfProgress.fraction(attachment, attachment?.let { snapshot.positions[it.key] }, pageCounts)
    }

    fun pagesFor(key: String): Int? = attachmentFor(key)?.let { pageCounts[it.key] }
}

/**
 * The library: a home view (continue reading, recently added, shelves) above
 * every book as covers or spines. Uses the same items, taps and selection as
 * Zotero's list, so filters, search, sorting and collections all work.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookshelfGrid(
    gridState: LazyGridState,
    data: BookshelfGridData,
    actions: BookshelfGridActions,
    bottomPadding: Dp,
) {
    val cells = data.itemCellModels
    val shown = if (data.shelfFilter == null) cells.toList() else cells.filter { data.snapshot.shelves[it.key] == data.shelfFilter }
    val home = data.shelfFilter == null && data.searchTerm.isNullOrBlank() && !data.isEditing
    val spines = data.layout == LibraryLayout.Spines
    PullToRefreshBox(
        isRefreshing = data.isRefreshing,
        onRefresh = actions.onStartSync,
    ) {
        LazyVerticalGrid(
            modifier = Modifier.fillMaxSize(),
            state = gridState,
            columns = if (spines) GridCells.Adaptive(minSize = 40.dp) else GridCells.Adaptive(minSize = data.coverSize.minWidthDp.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = bottomPadding + 16.dp),
            horizontalArrangement = Arrangement.spacedBy(if (spines) 0.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(if (spines) 10.dp else 22.dp),
        ) {
            fullWidth("bookshelf-shelves") {
                ShelfTabs(
                    selected = data.shelfFilter,
                    counts = Shelf.entries.associateWith { shelf -> cells.count { data.snapshot.shelves[it.key] == shelf } },
                    onSelect = actions.onShelfFilter,
                )
            }
            if (home && cells.isNotEmpty()) {
                homeSections(data, actions)
                fullWidth("bookshelf-all-header") {
                    SectionHeader(title = "All books", count = cells.size, onSeeAll = null)
                }
            }
            if (shown.isEmpty()) {
                fullWidth("bookshelf-empty") {
                    BookshelfEmptyState(searchTerm = data.searchTerm, shelf = data.shelfFilter, onAdd = actions.onAdd)
                }
            }
            items(items = shown, key = { it.key }) { item ->
                if (spines) {
                    BookSpine(
                        modifier = Modifier.animateItem(),
                        cell = item,
                        data = data,
                        onTap = { actions.onItemTapped(item) },
                        onLongTap = { actions.onItemLongTapped(item.key) },
                    )
                } else {
                    BookCard(
                        modifier = Modifier.animateItem(),
                        cell = item,
                        data = data,
                        actions = actions,
                    )
                }
            }
            fullWidth("bookshelf-bottom") {
                Spacer(modifier = Modifier.windowInsetsPadding(NavigationBarDefaults.windowInsets))
            }
        }
    }
}

private fun LazyGridScope.fullWidth(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

/** Continue reading, recently added, and a row per shelf. */
private fun LazyGridScope.homeSections(data: BookshelfGridData, actions: BookshelfGridActions) {
    val cells = data.itemCellModels
    val snapshot = data.snapshot
    val recent = cells
        .filter { snapshot.lastRead.containsKey(it.key) && snapshot.shelves[it.key] != Shelf.Finished }
        .sortedByDescending { snapshot.lastRead[it.key] }
        .take(12)
    if (recent.isNotEmpty()) {
        fullWidth("bookshelf-continue") {
            Column {
                SectionHeader(title = "Continue reading", count = null, onSeeAll = null)
                CoverRow(books = recent, data = data, actions = actions, width = 118.dp, showProgress = true)
            }
        }
    }
    val added = cells
        .sortedByDescending { snapshot.added[it.key] ?: 0L }
        .take(12)
    if (added.size >= 3) {
        fullWidth("bookshelf-added") {
            Column {
                SectionHeader(title = "Recently added", count = null, onSeeAll = null)
                CoverRow(books = added, data = data, actions = actions, width = 88.dp, showProgress = false)
            }
        }
    }
    for (shelf in listOf(Shelf.Want, Shelf.Reading, Shelf.Finished)) {
        val books = cells.filter { snapshot.shelves[it.key] == shelf }
        // Reading is already covered by Continue reading when that shows.
        if (books.isEmpty() || (shelf == Shelf.Reading && recent.isNotEmpty())) continue
        fullWidth("bookshelf-shelf-${shelf.name}") {
            Column {
                SectionHeader(title = shelf.label, count = books.size, onSeeAll = { actions.onShelfFilter(shelf) })
                CoverRow(books = books.take(12), data = data, actions = actions, width = 88.dp, showProgress = shelf != Shelf.Finished)
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int?, onSeeAll: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp),
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (count != null) {
            Text(
                text = "  $count",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        if (onSeeAll != null) {
            Text(
                text = "See all",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onSeeAll)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CoverRow(
    books: List<ItemCellModel>,
    data: BookshelfGridData,
    actions: BookshelfGridActions,
    width: Dp,
    showProgress: Boolean,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(books.size, key = { "row-" + books[it].key }) { index ->
            val cell = books[index]
            Column(
                modifier = Modifier
                    .width(width)
                    .clip(RoundedCornerShape(6.dp))
                    .combinedClickable(
                        role = Role.Button,
                        onClick = { actions.onItemTapped(cell) },
                        onLongClick = { actions.onBookPage(cell.key) },
                    ),
            ) {
                Cover(
                    cell = cell,
                    data = data,
                    progress = if (showProgress) data.progressFor(cell.key) else null,
                    finished = data.snapshot.shelves[cell.key] == Shelf.Finished,
                    accessory = null,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = cell.title.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 12.5.sp, lineHeight = 16.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 7.dp),
                )
            }
        }
    }
}

/** A cover image (first page), a title cover until it's ready, and the progress bar. */
@Composable
private fun Cover(
    cell: ItemCellModel,
    data: BookshelfGridData,
    progress: Float?,
    finished: Boolean,
    accessory: ItemCellModel.Accessory?,
    modifier: Modifier,
) {
    val state = (accessory as? ItemCellModel.Accessory.attachment)?.state
    // Looked up again whenever the download state changes, so a cover appears
    // as soon as a book finishes downloading.
    val cover by produceState<Pair<Boolean, File?>>(initialValue = false to null, cell.key, state) {
        value = true to data.coverFor(data.attachmentFor(cell.key))
    }
    val shape = RoundedCornerShape(5.dp)
    Box(
        modifier = modifier
            .aspectRatio(2f / 3f)
            .shadow(elevation = 2.dp, shape = shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        val (loaded, file) = cover
        if (!loaded) {
            ShimmerBox(Modifier.fillMaxSize())
        } else {
            TitleCover(title = cell.title)
            if (file != null) {
                AsyncImage(
                    model = file,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        DownloadBadge(state = state, modifier = Modifier.align(Alignment.BottomEnd))
        ProgressOverlay(progress = progress, finished = finished, modifier = Modifier.align(Alignment.BottomCenter))
        if (data.snapshot.bookmarks.containsKey(cell.key)) {
            CoverRibbon(modifier = Modifier.align(Alignment.TopEnd).padding(end = 10.dp))
        }
    }
}

/** A small ribbon over the top of the cover when the book has a bookmark in it. */
@Composable
private fun CoverRibbon(modifier: Modifier) {
    val color = MaterialTheme.colorScheme.primary
    androidx.compose.foundation.Canvas(
        modifier = modifier
            .width(11.dp)
            .height(24.dp)
            .shadow(elevation = 1.dp, shape = androidx.compose.foundation.shape.GenericShape { size, _ ->
                moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width, size.height)
                lineTo(size.width / 2f, size.height * 0.75f); lineTo(0f, size.height); close()
            }),
    ) {
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width, size.height)
            lineTo(size.width / 2f, size.height * 0.75f); lineTo(0f, size.height); close()
        }
        drawPath(path, color)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookCard(
    modifier: Modifier,
    cell: ItemCellModel,
    data: BookshelfGridData,
    actions: BookshelfGridActions,
) {
    val isSelected = data.isItemSelected(cell.key)
    val shelf = data.snapshot.shelves[cell.key]
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(
                role = Role.Button,
                onClick = { actions.onItemTapped(cell) },
                onLongClick = { actions.onItemLongTapped(cell.key) },
            )
            .then(
                if (isSelected) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)) else Modifier
            )
            .padding(4.dp),
    ) {
        Box {
            Cover(
                cell = cell,
                data = data,
                progress = data.progressFor(cell.key),
                finished = shelf == Shelf.Finished,
                accessory = data.getItemAccessory(cell.key),
                modifier = Modifier.fillMaxWidth(),
            )
            if (data.isEditing) {
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
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontSize = 13.sp, lineHeight = 17.sp),
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
            if (!data.isEditing) {
                BookMenu(
                    shelf = shelf,
                    onBookPage = { actions.onBookPage(cell.key) },
                    onDetails = { actions.onDetails(cell.key) },
                    onSetShelf = { actions.onSetShelf(cell.key, it) },
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ spines */

/** A book as a spine standing on a shelf; taller for longer books. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookSpine(
    modifier: Modifier,
    cell: ItemCellModel,
    data: BookshelfGridData,
    onTap: () -> Unit,
    onLongTap: () -> Unit,
) {
    val tint by produceState<Int?>(initialValue = null, cell.key) {
        value = data.tintFor(data.attachmentFor(cell.key))
    }
    val hue = remember(cell.title) { hueOf(cell.title) }
    val color = tint?.let { Color(it) } ?: Color.hsl(hue, 0.22f, 0.32f)
    val textColor = if (color.luminance() > 0.45f) Color(0xFF1B1B1D) else Color(0xFFF2F2F4)
    val pages = data.pagesFor(cell.key)
    val height = when {
        pages != null -> 128.dp + (52f * (pages.coerceAtMost(900) / 900f)).dp
        else -> 140.dp + ((hue.toInt() % 5) * 6).dp
    }
    val spineWidth = 28.dp + ((hue.toInt() % 3) * 2).dp
    val selected = data.isItemSelected(cell.key)
    Column(
        modifier = modifier
            .combinedClickable(role = Role.Button, onClick = onTap, onLongClick = onLongTap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(184.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier = Modifier
                    .width(spineWidth)
                    .height(height)
                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp, bottomStart = 1.dp, bottomEnd = 1.dp))
                    .background(color)
                    .background(
                        Brush.horizontalGradient(
                            0f to Color.White.copy(alpha = 0.10f),
                            0.25f to Color.Transparent,
                            0.85f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.22f),
                        )
                    )
                    .then(
                        if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)) else Modifier
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = cell.title.ifBlank { "Untitled" },
                    color = textColor,
                    fontFamily = FontFamily.Serif,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .verticalText()
                        .rotate(-90f)
                        .padding(horizontal = 8.dp),
                )
            }
        }
        // The shelf plank under the row.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
    }
}

/** Lays text out sideways: measured as if the box were rotated, then drawn turned. */
private fun Modifier.verticalText(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(
        Constraints(maxWidth = constraints.maxHeight, maxHeight = constraints.maxWidth)
    )
    layout(placeable.height, placeable.width) {
        placeable.place(
            x = -(placeable.width / 2 - placeable.height / 2),
            y = -(placeable.height / 2 - placeable.width / 2),
        )
    }
}

private fun hueOf(title: String): Float {
    var h = 0
    for (c in title) h = (h * 31 + c.code)
    return (((h % 360) + 360) % 360).toFloat()
}

/* ------------------------------------------------------------------ shared pieces */

/** All / Want to read / Reading / Finished. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShelfTabs(selected: Shelf?, counts: Map<Shelf, Int>, onSelect: (Shelf?) -> Unit) {
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
        selectedLabelColor = MaterialTheme.colorScheme.primary,
    )
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text("All") },
                colors = chipColors,
                border = null,
            )
        }
        items(Shelf.entries.size) { index ->
            val shelf = Shelf.entries[index]
            val count = counts[shelf] ?: 0
            FilterChip(
                selected = selected == shelf,
                onClick = { onSelect(if (selected == shelf) null else shelf) },
                label = { Text(if (count > 0) "${shelf.label}  $count" else shelf.label) },
                colors = chipColors,
                border = null,
            )
        }
    }
}

/** Shown until a real cover is ready, and for books without a PDF on this phone. */
@Composable
private fun TitleCover(title: String) {
    val hue = remember(title) { hueOf(title) }
    val base = Color.hsl(hue, 0.18f, 0.24f)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(base)
            // A soft shadow down the left edge, like a book's spine.
            .background(
                Brush.horizontalGradient(
                    0f to Color.Black.copy(alpha = 0.22f),
                    0.07f to Color.Transparent,
                )
            )
            .padding(horizontal = 10.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title.ifBlank { "Untitled" },
            color = Color.hsl(hue, 0.25f, 0.92f),
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
            trackColor = Color.Black.copy(alpha = 0.3f),
            drawStopIndicator = {},
            gapSize = 0.dp,
        )
    }
}

/** The ⋮ button under a cover: shelves, the book page and Zotero's details. */
@Composable
private fun BookMenu(shelf: Shelf?, onBookPage: () -> Unit, onDetails: () -> Unit, onSetShelf: (Shelf?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = { open = true }),
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
            DropdownMenuItem(
                text = { Text("About this book") },
                leadingIcon = { Icon(painterResource(Drawables.bookshelf_open_book_24px), null, Modifier.size(18.dp)) },
                onClick = { open = false; onBookPage() },
            )
            HorizontalDivider()
            for (option in Shelf.entries) {
                DropdownMenuItem(
                    text = {
                        Text(
                            option.label,
                            fontWeight = if (option == shelf) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (option == shelf) MaterialTheme.colorScheme.primary else Color.Unspecified,
                        )
                    },
                    leadingIcon = {
                        if (option == shelf) {
                            Icon(
                                painter = painterResource(Drawables.bookshelf_check_24px),
                                contentDescription = "Current shelf",
                                tint = MaterialTheme.colorScheme.primary,
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
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Edit details") },
                leadingIcon = { Icon(painterResource(Drawables.info_24px), null, Modifier.size(18.dp)) },
                onClick = { open = false; onDetails() },
            )
        }
    }
}

/** Placeholder covers while the library loads. */
@Composable
internal fun BookshelfLoadingGrid(coverSize: CoverSize) {
    LazyVerticalGrid(
        modifier = Modifier.fillMaxSize(),
        columns = GridCells.Adaptive(minSize = coverSize.minWidthDp.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
        userScrollEnabled = false,
    ) {
        items(12) {
            Column(modifier = Modifier.padding(4.dp)) {
                ShimmerBox(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(5.dp))
                )
                Spacer(Modifier.height(8.dp))
                ShimmerBox(
                    Modifier
                        .fillMaxWidth(0.8f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
            }
        }
    }
}
