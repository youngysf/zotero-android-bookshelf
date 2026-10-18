package org.zotero.android.screens.settings

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.zotero.android.screens.dashboard.BuildInfo
import org.zotero.android.screens.settings.elements.NewSettingsDivider
import org.zotero.android.screens.settings.elements.NewSettingsItem
import org.zotero.android.uicomponents.CustomScaffoldM3
import org.zotero.android.uicomponents.Strings
import org.zotero.android.uicomponents.themem3.AppThemeM3

@Composable
internal fun SettingsScreen(
    onBack: () -> Unit,
    onOpenWebpage: (url: String) -> Unit,
    toAccountScreen: () -> Unit,
    toDebugScreen: () -> Unit,
    toCiteScreen: () -> Unit,
    toQuickCopyScreen: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    AppThemeM3 {
        val viewEffect by viewModel.viewEffects.observeAsState()
        LaunchedEffect(key1 = viewModel) {
            viewModel.init()
        }

        LaunchedEffect(key1 = viewEffect) {
            when (val consumedEffect = viewEffect?.consume()) {
                null -> Unit
                is SettingsViewEffect.OnBack -> {
                    onBack()
                }

                is SettingsViewEffect.OpenWebpage -> {
                    onOpenWebpage(consumedEffect.url)
                }
            }
        }
        CustomScaffoldM3(
            topBar = {
                SettingsTopBar(
                    onClose = onBack,
                )
            },
        ) {
            // Bookshelf: settings grouped as Appearance, Reading, Library, Sync,
            // Advanced (Zotero's research tools) and About.
            val bookshelfViewModel: org.zotero.android.bookshelf.BookshelfViewModel = hiltViewModel()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                org.zotero.android.bookshelf.BookshelfSettingsSections(bookshelfViewModel)
                org.zotero.android.bookshelf.BookshelfSettingsGroup("Sync") {
                    org.zotero.android.bookshelf.BookshelfSettingsLink(
                        title = stringResource(id = Strings.settings_sync_account),
                        description = "Your Zotero account, syncing and file storage",
                        onClick = toAccountScreen,
                    )
                }
                org.zotero.android.bookshelf.BookshelfSettingsGroup("Advanced") {
                    org.zotero.android.bookshelf.BookshelfSettingsLink(
                        title = stringResource(id = Strings.settings_export_title),
                        description = "Quick copy format for citations",
                        onClick = toQuickCopyScreen,
                    )
                    org.zotero.android.bookshelf.BookshelfSettingsLink(
                        title = stringResource(id = Strings.settings_cite_title),
                        description = "Citation styles",
                        onClick = toCiteScreen,
                    )
                    org.zotero.android.bookshelf.BookshelfSettingsLink(
                        title = stringResource(id = Strings.settings_debug),
                        onClick = toDebugScreen,
                    )
                }
                org.zotero.android.bookshelf.BookshelfSettingsGroup("About") {
                    org.zotero.android.bookshelf.BookshelfSettingsLink(
                        title = stringResource(id = Strings.support_feedback),
                        onClick = viewModel::openSupportAndFeedback,
                    )
                    org.zotero.android.bookshelf.BookshelfSettingsLink(
                        title = stringResource(id = Strings.privacy_policy),
                        onClick = viewModel::openPrivacyPolicy,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                BuildInfo()
            }
        }
    }
}