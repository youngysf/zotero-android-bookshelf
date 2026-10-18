package org.zotero.android.screens.reader.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.with
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import org.zotero.android.screens.reader.ReaderViewModel
import org.zotero.android.screens.reader.ReaderViewState
import org.zotero.android.uicomponents.theme.CustomTheme

/**
 * Bookshelf: on phones the reader settings ("Aa") open as one sheet over the
 * page, so changes can be seen as they're made.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderSettingsView(
    viewState: ReaderViewState,
    viewModel: ReaderViewModel
) {
    val args = viewState.readerSettingsArgs ?: return
    val settingsViewModel: ReaderSettingsViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = {
            settingsViewModel.applyChangesImmediately()
            viewModel.hideSettingsView()
        },
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(560.dp),
        ) {
            ReaderSettingsScreen(
                args = args,
                onBack = viewModel::hideSettingsView,
                viewModel = settingsViewModel,
            )
        }
    }
}

private fun AnimatedContentTransitionScope<Boolean>.createTransitionSpec(): ContentTransform {
    val intOffsetSpec = tween<IntOffset>()
    return (slideInHorizontally(intOffsetSpec) { it } with
            slideOutHorizontally(intOffsetSpec) { it }).using(
        // Disable clipping since the faded slide-in/out should
        // be displayed out of bounds.
        SizeTransform(
            clip = false,
            sizeAnimationSpec = { _, _ -> tween() }
        ))
}