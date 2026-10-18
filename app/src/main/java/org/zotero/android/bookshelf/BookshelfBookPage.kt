package org.zotero.android.bookshelf

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.zotero.android.database.objects.Attachment
import org.zotero.android.uicomponents.Drawables
import java.io.File

/** Everything the book page needs from the library screen. */
internal class BookshelfBookPageData(
    val key: String,
    val fallbackTitle: String,
    val attachment: Attachment?,
    val shelf: Shelf?,
    val progress: Float?,
    val pages: Int?,
    val load: suspend () -> BookshelfBookInfo?,
    val coverFor: suspend (Attachment?) -> File?,
    val tintFor: suspend (Attachment?) -> Int?,
)

/**
 * A book's own page: cover, title and author, progress, shelf, a big Read
 * button, its highlights, collections and tags. Zotero's full record is one
 * tap away under "Edit details".
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun BookshelfBookPage(
    data: BookshelfBookPageData,
    onRead: () -> Unit,
    onSetShelf: (Shelf?) -> Unit,
    onOpenNote: (BookshelfNote) -> Unit,
    onShareNote: (BookshelfNote, String) -> Unit,
    onDetails: () -> Unit,
    onDismiss: () -> Unit,
) {
    val info by produceState<BookshelfBookInfo?>(initialValue = null, data.key) { value = data.load() }
    val cover by produceState<File?>(initialValue = null, data.key) { value = data.coverFor(data.attachment) }
    val tint by produceState<Int?>(initialValue = null, data.key) { value = data.tintFor(data.attachment) }
    var shelf by remember(data.key) { mutableStateOf(data.shelf) }
    var aboutExpanded by remember { mutableStateOf(false) }
    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    val heroColor = tint?.let { Color(it) } ?: MaterialTheme.colorScheme.surfaceContainerHigh

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = surface,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            // Hero: the cover, over a blurred, darkened copy of itself.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(330.dp)
                    .background(heroColor),
                contentAlignment = Alignment.Center,
            ) {
                if (cover != null) {
                    AsyncImage(
                        model = cover,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(36.dp),
                        alpha = 0.55f,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.15f),
                                0.7f to Color.Transparent,
                                1f to surface,
                            )
                        ),
                )
                Box(
                    modifier = Modifier
                        .padding(top = 28.dp)
                        .width(150.dp)
                        .aspectRatio(2f / 3f)
                        .shadow(14.dp, RoundedCornerShape(6.dp))
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    if (cover != null) {
                        AsyncImage(
                            model = cover,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.TopCenter,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text(
                            data.fallbackTitle,
                            fontFamily = FontFamily.Serif,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                val title = info?.title?.ifBlank { null } ?: data.fallbackTitle
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 25.sp, lineHeight = 31.sp),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                val authors = info?.authors.orEmpty()
                if (authors.isNotBlank()) {
                    Text(
                        text = authors,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                    )
                }
                val meta = listOf(info?.year.orEmpty(), info?.publisher.orEmpty(), data.pages?.let { "$it pages" }.orEmpty())
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    )
                }

                val progress = data.progress
                if (progress != null && shelf != Shelf.Finished) {
                    Spacer(Modifier.height(20.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        drawStopIndicator = {},
                        gapSize = 0.dp,
                    )
                    val pages = data.pages
                    Text(
                        text = "${(progress * 100).toInt()}% read" + if (pages != null) " · page ${(progress * pages).toInt().coerceAtLeast(1)} of $pages" else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onRead,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Icon(painterResource(Drawables.bookshelf_open_book_24px), contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (progress != null && progress > 0f && shelf != Shelf.Finished) "Continue reading" else "Read",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                Spacer(Modifier.height(16.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    Shelf.entries.forEachIndexed { i, option ->
                        SegmentedButton(
                            selected = shelf == option,
                            onClick = {
                                val next = if (shelf == option) null else option
                                shelf = next
                                onSetShelf(next)
                            },
                            shape = SegmentedButtonDefaults.itemShape(i, Shelf.entries.size),
                            label = { Text(option.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }

                val about = info?.abstract.orEmpty()
                if (about.isNotBlank()) {
                    SectionLabel("About")
                    Text(
                        text = about,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = if (aboutExpanded) Int.MAX_VALUE else 5,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { aboutExpanded = !aboutExpanded },
                    )
                }

                val notes = info?.notes.orEmpty()
                if (notes.isNotEmpty()) {
                    SectionLabel("Highlights and notes · ${notes.size}")
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (note in notes) {
                            NoteCard(
                                note = note,
                                onOpen = { onOpenNote(note) },
                                onShare = { onShareNote(note, authors) },
                            )
                        }
                    }
                }

                val collections = info?.collections.orEmpty()
                if (collections.isNotEmpty()) {
                    SectionLabel("Collections")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (name in collections) {
                            AssistChip(
                                onClick = {},
                                label = { Text(name) },
                                leadingIcon = { Icon(painterResource(Drawables.bookshelf_folder_24px), null, Modifier.size(16.dp)) },
                            )
                        }
                    }
                }
                val tags = info?.tags.orEmpty()
                if (tags.isNotEmpty()) {
                    SectionLabel("Tags")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (name in tags) {
                            AssistChip(onClick = {}, label = { Text(name) })
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onDetails)
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painterResource(Drawables.info_24px),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Edit details", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Title, authors, attachments and Zotero's full record",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        painterResource(Drawables.chevron_right_24px),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(36.dp))
            }
        }
    }
}
