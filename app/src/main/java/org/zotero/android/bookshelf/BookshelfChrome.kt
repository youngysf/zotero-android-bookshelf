package org.zotero.android.bookshelf

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.zotero.android.screens.allitems.data.ItemsSortType
import org.zotero.android.uicomponents.Drawables

/* ------------------------------------------------------------------ bottom navigation */

enum class BookshelfTab(val label: String, val icon: Int) {
    Library("Library", Drawables.bookshelf_library_24px),
    Collections("Collections", Drawables.bookshelf_folder_24px),
    Highlights("Highlights", Drawables.bookshelf_highlight_24px),
    Settings("Settings", Drawables.settings_24px),
}

@Composable
fun BookshelfNavBar(selected: BookshelfTab, onSelect: (BookshelfTab) -> Unit) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        for (tab in BookshelfTab.entries) {
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = { Icon(painter = painterResource(tab.icon), contentDescription = null) },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

/* ------------------------------------------------------------------ library top bar */

/**
 * A big serif title that shrinks as the library scrolls, a search field that
 * opens in place, the View sheet, and one ⋮ menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookshelfLibraryTopBar(
    title: String,
    searchTerm: String?,
    scrollBehavior: TopAppBarScrollBehavior,
    onSearch: (String) -> Unit,
    onView: () -> Unit,
    onAdd: () -> Unit,
    onSelect: () -> Unit,
    onSync: () -> Unit,
) {
    var searching by remember { mutableStateOf(!searchTerm.isNullOrEmpty()) }
    AnimatedContent(
        targetState = searching,
        transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
        label = "library-top-bar",
    ) { isSearching ->
        if (isSearching) {
            SearchBar(
                text = searchTerm ?: "",
                onText = onSearch,
                onClose = {
                    onSearch("")
                    searching = false
                },
            )
        } else {
            LargeTopAppBar(
                title = {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    IconButton(onClick = { searching = true }) {
                        Icon(painterResource(Drawables.search_24px), contentDescription = "Search")
                    }
                    IconButton(onClick = onView) {
                        Icon(painterResource(Drawables.bookshelf_tune_24px), contentDescription = "View options")
                    }
                    OverflowMenu(onAdd = onAdd, onSelect = onSelect, onSync = onSync)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                scrollBehavior = scrollBehavior,
            )
        }
    }
}

@Composable
private fun OverflowMenu(onAdd: () -> Unit, onSelect: () -> Unit, onSync: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(painterResource(Drawables.bookshelf_more_vert_24px), contentDescription = "More")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Add books or items") },
                leadingIcon = { Icon(painterResource(Drawables.add_24px), null) },
                onClick = { open = false; onAdd() },
            )
            DropdownMenuItem(
                text = { Text("Select") },
                leadingIcon = { Icon(painterResource(Drawables.check_circle), null) },
                onClick = { open = false; onSelect() },
            )
            DropdownMenuItem(
                text = { Text("Sync now") },
                leadingIcon = { Icon(painterResource(Drawables.redo_24px), null) },
                onClick = { open = false; onSync() },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(text: String, onText: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    var value by remember { mutableStateOf(text) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(painterResource(Drawables.arrow_back_24dp), contentDescription = "Close search")
            }
        },
        title = {
            TextField(
                value = value,
                onValueChange = { value = it; onText(it) },
                placeholder = { Text("Search your library") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {}),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
            )
        },
        actions = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { value = ""; onText("") }) {
                    Icon(painterResource(Drawables.ic_close_24dp), contentDescription = "Clear")
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

/* ------------------------------------------------------------------ view sheet */

data class BookshelfSortOption(val field: ItemsSortType.Field, val label: String)

val bookshelfSortOptions = listOf(
    BookshelfSortOption(ItemsSortType.Field.title, "Title"),
    BookshelfSortOption(ItemsSortType.Field.creator, "Author"),
    BookshelfSortOption(ItemsSortType.Field.dateAdded, "Date added"),
    BookshelfSortOption(ItemsSortType.Field.dateModified, "Date modified"),
    BookshelfSortOption(ItemsSortType.Field.year, "Year published"),
    BookshelfSortOption(ItemsSortType.Field.publisher, "Publisher"),
    BookshelfSortOption(ItemsSortType.Field.itemType, "Item type"),
)

/** Layout, cover size, sorting, and what to show, in one sheet. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun BookshelfViewSheet(
    layout: LibraryLayout,
    coverSize: CoverSize,
    sort: ItemsSortType,
    downloadedOnly: Boolean,
    tagFilterCount: Int,
    onLayout: (LibraryLayout) -> Unit,
    onCoverSize: (CoverSize) -> Unit,
    onSortField: (ItemsSortType.Field) -> Unit,
    onSortAscending: (Boolean) -> Unit,
    onDownloadedOnly: () -> Unit,
    onTagFilter: () -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var sortField by remember { mutableStateOf(sort.field) }
    var ascending by remember { mutableStateOf(sort.ascending) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            SheetTitle("View")
            SectionLabel("Layout")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                LibraryLayout.entries.forEachIndexed { i, option ->
                    SegmentedButton(
                        selected = option == layout,
                        onClick = { onLayout(option) },
                        shape = SegmentedButtonDefaults.itemShape(i, LibraryLayout.entries.size),
                        label = { Text(option.label) },
                    )
                }
            }
            if (layout == LibraryLayout.Covers) {
                SectionLabel("Cover size")
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    CoverSize.entries.forEachIndexed { i, option ->
                        SegmentedButton(
                            selected = option == coverSize,
                            onClick = { onCoverSize(option) },
                            shape = SegmentedButtonDefaults.itemShape(i, CoverSize.entries.size),
                            label = { Text(option.name) },
                        )
                    }
                }
            }

            SectionLabel("Sort by")
            for (option in bookshelfSortOptions) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            sortField = option.field
                            ascending = option.field.defaultOrderAscending
                            onSortField(option.field)
                        }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = sortField == option.field, onClick = null, modifier = Modifier.padding(12.dp))
                    Text(option.label, style = MaterialTheme.typography.bodyLarge)
                }
            }
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf(true to "Ascending", false to "Descending").forEachIndexed { i, (value, label) ->
                    SegmentedButton(
                        selected = ascending == value,
                        onClick = { ascending = value; onSortAscending(value) },
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                        label = { Text(label) },
                    )
                }
            }

            SectionLabel("Show")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onDownloadedOnly)
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Downloaded books only", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Hide books that are only in the cloud",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = downloadedOnly, onCheckedChange = { onDownloadedOnly() })
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onTagFilter)
                    .padding(vertical = 12.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(Drawables.bookshelf_label_24px),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(16.dp))
                Text("Filter by tag", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                if (tagFilterCount > 0) {
                    Text(
                        "$tagFilterCount selected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Icon(
                    painterResource(Drawables.chevron_right_24px),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun SheetTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

/** Any content (such as Zotero's tag filter) in a tall sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookshelfContentSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(560.dp)) {
            content()
        }
    }
}

/* ------------------------------------------------------------------ selection bar */

data class BookshelfBarAction(val label: String, val icon: Int, val tint: Color? = null, val onClick: () -> Unit)

/**
 * Floating bar shown while books are selected: the main actions as buttons,
 * everything else Zotero offers in a ⋮ menu.
 */
@Composable
internal fun BookshelfSelectionBar(
    count: Int,
    onShelf: (Shelf?) -> Unit,
    mainActions: List<BookshelfBarAction>,
    moreActions: List<BookshelfBarAction>,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val enabled = count > 0
            ShelfBarButton(enabled = enabled, onShelf = onShelf)
            for (action in mainActions) {
                BarButton(action.label, action.icon, enabled, action.tint, action.onClick)
            }
            if (moreActions.isNotEmpty()) {
                var open by remember { mutableStateOf(false) }
                Box {
                    BarButton("More", Drawables.bookshelf_more_vert_24px, enabled, null) { open = true }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        for (action in moreActions) {
                            DropdownMenuItem(
                                text = { Text(action.label, color = action.tint ?: Color.Unspecified) },
                                leadingIcon = {
                                    Icon(
                                        painterResource(action.icon),
                                        contentDescription = null,
                                        tint = action.tint ?: MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                onClick = { open = false; action.onClick() },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShelfBarButton(enabled: Boolean, onShelf: (Shelf?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        BarButton("Shelf", Drawables.bookshelf_bookmark_24px, enabled, null) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (shelf in Shelf.entries) {
                DropdownMenuItem(text = { Text(shelf.label) }, onClick = { open = false; onShelf(shelf) })
            }
            HorizontalDivider()
            DropdownMenuItem(text = { Text("No shelf") }, onClick = { open = false; onShelf(null) })
        }
    }
}

@Composable
private fun BarButton(label: String, icon: Int, enabled: Boolean, tint: Color?, onClick: () -> Unit) {
    val color = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        tint != null -> tint
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
    }
}

/* ------------------------------------------------------------------ tag dialog */

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BookshelfTagDialog(
    count: Int,
    suggestions: List<String>,
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (count == 1) "Tag this book" else "Tag $count books") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Tag") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                val matches = suggestions
                    .filter { text.isBlank() || it.contains(text.trim(), ignoreCase = true) }
                    .take(12)
                if (matches.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (tag in matches) {
                            SuggestionChip(onClick = { onAdd(tag) }, label = { Text(tag) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(text) }, enabled = text.isNotBlank()) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/* ------------------------------------------------------------------ empty and loading states */

@Composable
internal fun BookshelfEmptyState(
    searchTerm: String?,
    shelf: Shelf?,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painterResource(Drawables.bookshelf_open_book_24px),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        val (title, body) = when {
            !searchTerm.isNullOrBlank() -> "Nothing found" to "No books match “$searchTerm”."
            shelf != null -> "No books on ${shelf.label}" to "Use a book's ⋮ menu to put it on this shelf."
            else -> "No books here yet" to "Add a PDF or EPUB to start your library."
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (searchTerm.isNullOrBlank() && shelf == null) {
            Spacer(Modifier.height(20.dp))
            TextButton(onClick = onAdd) { Text("Add books") }
        }
    }
}

/** A soft moving sheen for placeholders while things load. */
@Composable
fun shimmerBrush(): Brush {
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val light = MaterialTheme.colorScheme.surfaceContainerHighest
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -300f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer-x",
    )
    return Brush.linearGradient(
        colors = listOf(base, light, base),
        start = androidx.compose.ui.geometry.Offset(x, 0f),
        end = androidx.compose.ui.geometry.Offset(x + 300f, 300f),
    )
}

@Composable
fun ShimmerBox(modifier: Modifier) {
    Box(modifier = modifier.background(shimmerBrush()))
}

/** A tab chosen on another screen (e.g. Highlights from Collections), picked up by the library. */
object BookshelfTabRequest {
    @Volatile
    private var requested: BookshelfTab? = null

    fun set(tab: BookshelfTab) {
        requested = tab
    }

    fun take(): BookshelfTab? {
        val tab = requested
        requested = null
        return tab
    }
}
