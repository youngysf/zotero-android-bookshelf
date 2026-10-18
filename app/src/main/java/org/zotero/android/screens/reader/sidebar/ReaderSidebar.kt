package org.zotero.android.screens.reader.sidebar

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import org.zotero.android.screens.reader.ReaderViewModel
import org.zotero.android.screens.reader.ReaderViewState
import org.zotero.android.screens.reader.data.ReaderFileType
import org.zotero.android.screens.reader.sidebar.annotations.ReaderAnnotationsSidebar
import org.zotero.android.screens.reader.sidebar.data.ReaderSliderOptions
import org.zotero.android.screens.reader.sidebar.thumbnails.ReaderThumbnailsSidebar
import org.zotero.android.uicomponents.foundation.safeStringResource

private val htmlEpubSliderOptions = listOf(
    ReaderSliderOptions.Annotations,
    ReaderSliderOptions.Outline
)

private val pdfSliderOptions = listOf(
    ReaderSliderOptions.Thumbnails,
    ReaderSliderOptions.Annotations,
    ReaderSliderOptions.Outline
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderSidebar(
    viewModel: ReaderViewModel,
    viewState: ReaderViewState,
    annotationsLazyListState: LazyListState,
    annotationMaxSideSize: Int,
) {
    Column(Modifier) {
        val selectorOptions = if (viewState.fileType == ReaderFileType.PDF) {
            pdfSliderOptions
        } else {
            htmlEpubSliderOptions
        }
        val selectorOptionsStrings = selectorOptions
            .map {
                safeStringResource(id = it.optionStringId)
            }

        val selectedOption = viewState.sidebarSliderSelectedOption
        // Bookshelf: pill-shaped tabs.
        androidx.compose.material3.SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .pointerInput(Unit) {
                    detectTapGestures {
                        //Prevent tap to be propagated to composables behind this screen.
                    }
                }
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            selectorOptionsStrings.forEachIndexed { index, selectorOption ->
                SegmentedButton(
                    selected = selectorOptions.indexOf(selectedOption) == index,
                    onClick = { viewModel.setSidebarSliderSelectedOption(selectorOptions[index]) },
                    shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(index, selectorOptionsStrings.size),
                    icon = {},
                    label = { Text(text = selectorOption, style = MaterialTheme.typography.labelLarge) },
                )
            }
        }

        when (selectedOption) {
            ReaderSliderOptions.Thumbnails -> {
                ReaderThumbnailsSidebar(
                    annotationMaxSideSize = annotationMaxSideSize,
                )
            }

            ReaderSliderOptions.Annotations -> {
                ReaderAnnotationsSidebar(
                    viewModel = viewModel,
                    viewState = viewState,
                    annotationsLazyListState = annotationsLazyListState,
                    annotationMaxSideSize = annotationMaxSideSize,
                )
            }

            ReaderSliderOptions.Outline -> {
                ReaderOutlineSidebar(
                    viewModel = viewModel,
                    viewState = viewState,
                )
            }
        }
    }
}
