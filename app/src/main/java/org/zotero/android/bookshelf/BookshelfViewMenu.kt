package org.zotero.android.bookshelf

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import org.zotero.android.uicomponents.Drawables

/** Toolbar button for choosing covers or list, and the cover size. */
@Composable
internal fun BookshelfViewMenuButton(
    showCovers: Boolean,
    coverSize: CoverSize,
    onShowCovers: (Boolean) -> Unit,
    onCoverSize: (CoverSize) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painter = painterResource(
                    if (showCovers) Drawables.bookshelf_grid_24px else Drawables.bookshelf_list_24px
                ),
                contentDescription = "View options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ViewOption("Covers", selected = showCovers) {
                onShowCovers(true)
                expanded = false
            }
            ViewOption("List", selected = !showCovers) {
                onShowCovers(false)
                expanded = false
            }
            if (showCovers) {
                HorizontalDivider()
                for (size in CoverSize.entries) {
                    ViewOption("${size.name} covers", selected = size == coverSize) {
                        onCoverSize(size)
                        expanded = false
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewOption(label: String, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { RadioButton(selected = selected, onClick = null) },
        onClick = onClick,
    )
}
