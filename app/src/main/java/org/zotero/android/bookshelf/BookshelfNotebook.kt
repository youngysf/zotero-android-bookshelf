package org.zotero.android.bookshelf

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.zotero.android.uicomponents.Drawables

/** Parses Zotero's "#rrggbb" annotation colours. */
fun noteColor(hex: String, fallback: Color): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (e: Throwable) {
    fallback
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookshelfNotebookTopBar(scrollBehavior: TopAppBarScrollBehavior) {
    LargeTopAppBar(
        title = { Text("Highlights") },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface,
        ),
        scrollBehavior = scrollBehavior,
    )
}

/**
 * Every highlight and note in the library, grouped by book, newest books
 * first. Filter by colour or tag, or search the text.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookshelfNotebook(
    loadKey: Any?,
    load: suspend () -> List<BookshelfNote>,
    bottomPadding: Dp,
    onOpen: (BookshelfNote) -> Unit,
    onShare: (BookshelfNote) -> Unit,
) {
    var notes by remember { mutableStateOf<List<BookshelfNote>?>(null) }
    LaunchedEffect(loadKey) { notes = load() }
    var query by remember { mutableStateOf("") }
    var color by remember { mutableStateOf<String?>(null) }
    var tag by remember { mutableStateOf<String?>(null) }

    val all = notes
    if (all == null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            repeat(5) {
                ShimmerBox(
                    Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
                Spacer(Modifier.height(12.dp))
            }
        }
        return
    }
    if (all.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                painterResource(Drawables.bookshelf_highlight_24px),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text("No highlights yet", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                "Highlight or underline text while reading and it will appear here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val colors = remember(all) { all.map { it.color }.filter { it.isNotBlank() }.distinct() }
    val tags = remember(all) { all.flatMap { it.tags }.distinct().sortedBy { it.lowercase() } }
    val filtered = all.filter { note ->
        (color == null || note.color == color) &&
            (tag == null || tag in note.tags) &&
            (query.isBlank() || note.text.contains(query, true) || note.comment.contains(query, true) || note.bookTitle.contains(query, true))
    }
    val books = filtered
        .groupBy { it.bookKey }
        .values
        .sortedByDescending { group -> group.maxOf { it.dateModified } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = bottomPadding + 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "search") {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search highlights") },
                singleLine = true,
                leadingIcon = { Icon(painterResource(Drawables.search_24px), null) },
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (colors.size > 1 || tags.isNotEmpty()) {
            item(key = "filters") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(colors, key = { "c$it" }) { c ->
                        val selected = color == c
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(noteColor(c, MaterialTheme.colorScheme.primary))
                                .then(
                                    if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier
                                )
                                .clickable { color = if (selected) null else c },
                        )
                    }
                    items(tags, key = { "t$it" }) { t ->
                        FilterChip(
                            selected = tag == t,
                            onClick = { tag = if (tag == t) null else t },
                            label = { Text(t) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }
                }
            }
        }
        if (books.isEmpty()) {
            item(key = "none") {
                Text(
                    "No highlights match.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 32.dp),
                )
            }
        }
        for (group in books) {
            val first = group.first()
            item(key = "book-" + first.bookKey) {
                Row(
                    modifier = Modifier.padding(top = 18.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        text = first.bookTitle.ifBlank { "Untitled" },
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        "  ${group.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(
                group.sortedWith(compareBy({ it.attachmentKey }, { it.sortIndex })),
                key = { "note-" + it.key },
            ) { note ->
                NoteCard(note = note, onOpen = { onOpen(note) }, onShare = { onShare(note) })
            }
        }
    }
}

/** A highlight: its colour down the side, the quote, the comment and page. */
@Composable
internal fun NoteCard(note: BookshelfNote, onOpen: () -> Unit, onShare: (() -> Unit)?) {
    val accent = noteColor(note.color, MaterialTheme.colorScheme.primary)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onOpen),
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accent),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 10.dp),
            ) {
                if (note.text.isNotBlank()) {
                    Text(
                        text = note.text.trim(),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = FontFamily.Serif,
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                            fontStyle = if (note.type == "underline") FontStyle.Normal else FontStyle.Normal,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 12,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (note.comment.isNotBlank()) {
                    Text(
                        text = note.comment.trim(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = if (note.text.isNotBlank()) 8.dp else 0.dp),
                    )
                }
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val meta = listOfNotNull(
                        note.pageLabel.takeIf { it.isNotBlank() }?.let { "p. $it" },
                        note.tags.takeIf { it.isNotEmpty() }?.joinToString(" · ") { "#$it" },
                    ).joinToString("   ")
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (onShare != null && note.text.isNotBlank()) {
                        IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) {
                            Icon(
                                painterResource(Drawables.share_24),
                                contentDescription = "Share as image",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
