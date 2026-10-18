package org.zotero.android.bookshelf

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.zotero.android.uicomponents.Drawables

/** A titled group of settings on a soft card. */
@Composable
fun BookshelfSettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, top = 22.dp, bottom = 8.dp),
        )
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) { content() }
        }
    }
}

/** A tappable row: title, optional description, chevron. */
@Composable
fun BookshelfSettingsLink(title: String, description: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (!description.isNullOrBlank()) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(
            painterResource(Drawables.chevron_right_24px),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SwitchRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoiceRow(title: String, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, option ->
                SegmentedButton(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                    label = { Text(label(option), maxLines = 1) },
                )
            }
        }
    }
}

/** Bookshelf's own settings: appearance, reading and library. */
@Composable
fun BookshelfSettingsSections(viewModel: BookshelfViewModel) {
    val appTheme by viewModel.appTheme.collectAsState()
    val layout by viewModel.layout.collectAsState()
    val coverSize by viewModel.coverSize.collectAsState()
    val pageTheme by viewModel.pageTheme.collectAsState()
    val brightness by viewModel.readerBrightness.collectAsState()
    val autoShelves by viewModel.autoShelves.collectAsState()

    BookshelfSettingsGroup("Appearance") {
        ChoiceRow("App theme", AppTheme.entries, appTheme, { it.label }, viewModel::setAppTheme)
        ChoiceRow("Library layout", LibraryLayout.entries, layout, { it.label }, viewModel::setLayout)
        if (layout == LibraryLayout.Covers) {
            ChoiceRow("Cover size", CoverSize.entries, coverSize, { it.name }, viewModel::setCoverSize)
        }
    }
    BookshelfSettingsGroup("Reading") {
        BookshelfPageThemeRow(selected = pageTheme, onSelected = viewModel::setPageTheme)
        BookshelfBrightnessRow(value = brightness, onChange = viewModel::setReaderBrightness)
        Spacer(Modifier.width(1.dp).padding(bottom = 8.dp))
    }
    BookshelfSettingsGroup("Library") {
        SwitchRow(
            title = "Automatic shelves",
            description = "Opening a book puts it on Reading; reaching the last page puts it on Finished",
            checked = autoShelves,
            onChange = viewModel::setAutoShelves,
        )
    }
}
