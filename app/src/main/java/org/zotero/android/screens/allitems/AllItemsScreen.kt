package org.zotero.android.screens.allitems

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.hilt.navigation.compose.hiltViewModel
import org.zotero.android.appupdate.MaybeShowAppUpdateBanner
import org.zotero.android.architecture.ui.CustomLayoutSize
import org.zotero.android.bookshelf.BookshelfGrid
import org.zotero.android.bookshelf.BookshelfViewModel
import org.zotero.android.screens.allitems.bottomsheet.AllItemsAddBottomSheet
import org.zotero.android.screens.allitems.table.AllItemsTable
import org.zotero.android.uicomponents.CustomScaffoldM3
import org.zotero.android.uicomponents.Strings
import org.zotero.android.uicomponents.error.FullScreenError
import org.zotero.android.uicomponents.foundation.safeStringResource
import org.zotero.android.uicomponents.loading.BaseLceBox
import org.zotero.android.uicomponents.loading.CircularLoading
import org.zotero.android.uicomponents.themem3.AppThemeM3
import java.io.File

@Composable
internal fun AllItemsScreen(
    viewModel: AllItemsViewModel = hiltViewModel(),
    onPickFile: () -> Unit,
    onOpenFile: (file: File, mimeType: String) -> Unit,
    onOpenWebpage: (url: String) -> Unit,
    navigateToCollectionsScreen: (String) -> Unit,
    navigateToSinglePicker: () -> Unit,
    navigateToAddByIdentifier: (addByIdentifierParams: String) -> Unit,
    navigateToRetrieveMetadata: (params: String) -> Unit,
    navigateToAllItemsSort: () -> Unit,
    navigateToCollectionPicker: () -> Unit,
    navigateToItemDetails: (String) -> Unit,
    navigateToAddOrEditNote: (String) -> Unit,
    navigateToVideoPlayerScreen: () -> Unit,
    navigateToImageViewerScreen: () -> Unit,
    navigateToZoterWebViewScreen: (String) -> Unit,
    navigateToTagFilter: (params: String) -> Unit,
    navigateToScanBarcode: () -> Unit,
    navigateToSingleCitation: () -> Unit,
    navigateToCitationBibliographyExport:() -> Unit,
    onShowPdf: (String, String) -> Unit,
    onShowHtmlOrEpub: (String, String) -> Unit,
    navigateToSettings: () -> Unit = {},
    navigateToAllItemsInPlace: () -> Unit = {},
) {
    AppThemeM3 {
        val layoutType = CustomLayoutSize.calculateLayoutType()
        val viewState by viewModel.viewStates.observeAsState(AllItemsViewState())
        val viewEffect by viewModel.viewEffects.observeAsState()
        val lazyListState = rememberLazyListState()
        // Bookshelf: cover grid settings and state
        val bookshelfViewModel: BookshelfViewModel = hiltViewModel()
        val showCovers by bookshelfViewModel.showCovers.collectAsState()
        val coverSize by bookshelfViewModel.coverSize.collectAsState()
        val shelfFilter by bookshelfViewModel.shelfFilter.collectAsState()
        val bookshelfSnapshot by bookshelfViewModel.snapshot.collectAsState()
        val pageCounts by bookshelfViewModel.pageCounts.collectAsState()
        val bookshelfLayout by bookshelfViewModel.layout.collectAsState()
        val context = androidx.compose.ui.platform.LocalContext.current
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        var bookshelfTab by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(org.zotero.android.bookshelf.BookshelfTab.Library) }
        var showViewSheet by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
        var showTagFilterSheet by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
        var tagDialogKeys by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Set<String>?>(null) }
        var tagSuggestions by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<List<String>>(emptyList()) }
        var bookPageKey by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf<String?>(null) }
        var notebookReloads by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
        val lazyGridState = rememberLazyGridState()
        // Shelves and reading positions: read again when the list changes and
        // when coming back to this screen (e.g. from the reader).
        var bookshelfResumes by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
        org.zotero.android.architecture.ui.ObserveLifecycleEvent { event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                bookshelfResumes++
                notebookReloads++
                org.zotero.android.bookshelf.BookshelfTabRequest.take()?.let { bookshelfTab = it }
            }
        }
        LaunchedEffect(showCovers, viewState.itemCellModels.size, viewState.lce, bookshelfResumes, viewState.isRefreshing) {
            if (showCovers) viewModel.bookshelfLibraryId()?.let { bookshelfViewModel.refresh(it) }
        }

        val isTablet = layoutType.isTablet()

        if (!isTablet)  {
            // Bookshelf: Library is the home tab, so back leaves the app from
            // there; from Highlights it returns to Library.
            BackHandler(enabled = bookshelfTab != org.zotero.android.bookshelf.BookshelfTab.Library) {
                bookshelfTab = org.zotero.android.bookshelf.BookshelfTab.Library
            }
        }

        LaunchedEffect(key1 = viewModel) {
            viewModel.init(isTablet)
        }

        LaunchedEffect(key1 = viewEffect) {
            when (val consumedEffect = viewEffect?.consume()) {
                null -> Unit
                AllItemsViewEffect.BookshelfReloadCollection -> navigateToAllItemsInPlace()
                is AllItemsViewEffect.ShowCollectionsEffect -> navigateToCollectionsScreen(
                    consumedEffect.screenArgs
                )

                is AllItemsViewEffect.ShowItemDetailEffect -> navigateToItemDetails(consumedEffect.screenArgs)
                is AllItemsViewEffect.ShowAddOrEditNoteEffect -> navigateToAddOrEditNote(
                    consumedEffect.screenArgs
                )

                is AllItemsViewEffect.ShowPhoneFilterEffect -> {
                    navigateToTagFilter(consumedEffect.params)
                }

                AllItemsViewEffect.ShowItemTypePickerEffect -> {
                    navigateToSinglePicker()
                }

                is AllItemsViewEffect.ShowAddByIdentifierEffect -> {
                    navigateToAddByIdentifier(consumedEffect.params)
                }

                is AllItemsViewEffect.ShowRetrieveMetadataDialogEffect -> {
                    navigateToRetrieveMetadata(consumedEffect.params)
                }

                AllItemsViewEffect.ShowSortPickerEffect -> {
                    navigateToAllItemsSort()
                }

                AllItemsViewEffect.ShowCollectionPickerEffect -> {
                    navigateToCollectionPicker()
                }

                AllItemsViewEffect.ScreenRefresh -> {
                    //no-op
                }

                is AllItemsViewEffect.OpenFile -> onOpenFile(
                    consumedEffect.file,
                    consumedEffect.mimeType
                )

                is AllItemsViewEffect.OpenWebpage -> onOpenWebpage(consumedEffect.url)
                is AllItemsViewEffect.NavigateToPdfScreen -> {
                    onShowPdf(consumedEffect.params, consumedEffect.encodedFilePath)
                }
                is AllItemsViewEffect.NavigateToReaderScreen -> {
                    onShowHtmlOrEpub(consumedEffect.params, consumedEffect.readerEncodedFilePathParam)
                }

                is AllItemsViewEffect.ShowVideoPlayer -> {
                    navigateToVideoPlayerScreen()
                }

                is AllItemsViewEffect.ShowImageViewer -> {
                    navigateToImageViewerScreen()
                }

                is AllItemsViewEffect.ShowZoteroWebView -> {
                    navigateToZoterWebViewScreen(consumedEffect.url)
                }

                is AllItemsViewEffect.ShowScanBarcode -> {
                    navigateToScanBarcode()
                }

                is AllItemsViewEffect.MaybeScrollToTop -> {
                    if (showCovers) {
                        val gridIndex = lazyGridState.layoutInfo.visibleItemsInfo.firstOrNull()?.index
                        if ((1..2).contains(gridIndex)) lazyGridState.scrollToItem(index = 0, scrollOffset = 0)
                    }
                    val maybeIndex =
                        lazyListState.layoutInfo.visibleItemsInfo.firstOrNull()?.index
                    //Indices of the first visible item AFTER an update, so after the potential new item was added to the top.
                    if (((1..2).contains(maybeIndex))) {
                        lazyListState.scrollToItem(index = 0, scrollOffset = 0)
                    }
                }

                AllItemsViewEffect.ShowSingleCitationEffect -> {
                    navigateToSingleCitation()
                }

                AllItemsViewEffect.ShowCitationBibliographyExportEffect -> {
                    navigateToCitationBibliographyExport()
                }
            }
        }
        val scrollBehavior = if (isTablet) {
            TopAppBarDefaults.pinnedScrollBehavior()
        } else {
            TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        }
        val onBookshelfTab: (org.zotero.android.bookshelf.BookshelfTab) -> Unit = { tab ->
            when (tab) {
                org.zotero.android.bookshelf.BookshelfTab.Library,
                org.zotero.android.bookshelf.BookshelfTab.Highlights -> bookshelfTab = tab
                org.zotero.android.bookshelf.BookshelfTab.Settings -> navigateToSettings()
            }
        }

        CustomScaffoldM3(
            scrollBehavior = scrollBehavior,
            topBar = {
                if (isTablet || viewState.isEditing) {
                    AllItemsTopBar(
                        scrollBehavior = scrollBehavior,
                        viewState = viewState,
                        viewModel = viewModel,
                        layoutType = layoutType,
                    )
                } else if (bookshelfTab == org.zotero.android.bookshelf.BookshelfTab.Highlights) {
                    org.zotero.android.bookshelf.BookshelfNotebookTopBar(scrollBehavior = scrollBehavior)
                } else {
                    org.zotero.android.bookshelf.BookshelfLibraryTopBar(
                        title = viewState.collectionName,
                        searchTerm = viewState.searchTerm,
                        scrollBehavior = scrollBehavior,
                        currentCollection = viewModel.bookshelfCollectionId(),
                        loadCollections = {
                            viewModel.bookshelfLibraryId()?.let { bookshelfViewModel.collectionMenu(it) } ?: emptyList()
                        },
                        onSelectCollection = viewModel::bookshelfSwitchCollection,
                        onManageCollections = viewModel::navigateToCollections,
                        onSearch = viewModel::onSearch,
                        onView = { showViewSheet = true },
                        onAdd = viewModel::onAdd,
                        onSelect = viewModel::onSelect,
                        onSync = viewModel::startSync,
                    )
                }
            },
            bottomBar = {
                if (isTablet) {
                    AllItemsBottomPanelNew(
                        viewModel = viewModel,
                        viewState = viewState,
                    )
                } else if (!viewState.isEditing) {
                    org.zotero.android.bookshelf.BookshelfNavBar(selected = bookshelfTab, onSelect = onBookshelfTab)
                }
            },
        ) { innerPadding ->
            val bottomPadding = if (isTablet) {
                androidx.compose.material3.BottomAppBarDefaults.FlexibleBottomAppBarHeight
            } else if (viewState.isEditing) {
                96.dp
            } else {
                innerPadding.calculateBottomPadding()
            }
            if (!isTablet && !viewState.isEditing && bookshelfTab == org.zotero.android.bookshelf.BookshelfTab.Highlights) {
                org.zotero.android.bookshelf.BookshelfNotebook(
                    loadKey = notebookReloads,
                    load = { viewModel.bookshelfLibraryId()?.let { bookshelfViewModel.notes(it) } ?: emptyList() },
                    bottomPadding = bottomPadding,
                    onOpen = { note ->
                        if (!viewModel.bookshelfOpenAttachment(note.attachmentKey, note.parentKey, note.key)) {
                            android.widget.Toast.makeText(context, "Download this book first to open it here.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    onShare = { note ->
                        scope.launch {
                            val tint = bookshelfViewModel.tintFor(viewModel.bookshelfAttachment(note.bookKey))
                            org.zotero.android.bookshelf.BookshelfQuoteCard.share(context, note.text, note.bookTitle, "", note.color, tint)
                        }
                    },
                )
                return@CustomScaffoldM3
            }
            BaseLceBox(
                modifier = Modifier.fillMaxSize(),
                lce = viewState.lce,
                error = { _ ->
                    FullScreenError(
                        modifier = Modifier.align(Alignment.Center),
                        errorTitle = safeStringResource(id = Strings.error_list_load_check_crash_logs),
                    )
                },
                loading = {
                    if (showCovers && !isTablet) {
                        org.zotero.android.bookshelf.BookshelfLoadingGrid(coverSize)
                    } else {
                        CircularLoading()
                    }
                },
            ) {
                if (showCovers) {
                    BookshelfGrid(
                        gridState = lazyGridState,
                        data = org.zotero.android.bookshelf.BookshelfGridData(
                            itemCellModels = viewState.itemCellModels,
                            layout = bookshelfLayout,
                            coverSize = coverSize,
                            shelfFilter = shelfFilter,
                            snapshot = bookshelfSnapshot,
                            pageCounts = pageCounts,
                            searchTerm = viewState.searchTerm,
                            isEditing = viewState.isEditing,
                            isRefreshing = viewState.isRefreshing,
                            isItemSelected = viewState::isSelected,
                            getItemAccessory = viewState::getAccessoryForItem,
                            attachmentFor = viewModel::bookshelfAttachment,
                            coverFor = bookshelfViewModel::coverFor,
                            tintFor = bookshelfViewModel::tintFor,
                        ),
                        actions = org.zotero.android.bookshelf.BookshelfGridActions(
                            onShelfFilter = bookshelfViewModel::setShelfFilter,
                            onSetShelf = { key, shelf ->
                                viewModel.bookshelfLibraryId()?.let { bookshelfViewModel.setShelf(it, listOf(key), shelf) }
                            },
                            onItemTapped = viewModel::onItemTapped,
                            onItemLongTapped = viewModel::onItemLongTapped,
                            onBookPage = { key -> bookPageKey = key },
                            onDetails = viewModel::onAccessoryTapped,
                            onAdd = viewModel::onAdd,
                            onStartSync = viewModel::startSync,
                        ),
                        bottomPadding = bottomPadding,
                    )
                } else AllItemsTable(
                    lazyListState = lazyListState,
                    itemCellModels = viewState.itemCellModels,
                    isEditing = viewState.isEditing,
                    isRefreshing = viewState.isRefreshing,
                    isItemSelected = viewState::isSelected,
                    getItemAccessory = viewState::getAccessoryForItem,
                    onItemTapped = viewModel::onItemTapped,
                    onAccessoryTapped = viewModel::onAccessoryTapped,
                    onItemLongTapped = viewModel::onItemLongTapped,
                    onStartSync = viewModel::startSync
                )

                MaybeShowAppUpdateBanner(
                    appUpdateBannerPayload = viewState.appUpdateBannerPayload,
                    shouldShowAppUpdateBanner = viewState.shouldShowAppUpdateBanner,
                    onDownloadButtonTapped = viewModel::onAppUpdateDownloadButtonTapped,
                    onLaterButtonTapped = viewModel::onAppUpdateLaterButtonTapped
                )

                val itemsError = viewState.error
                if (itemsError != null) {
                    ShowErrorOrDialog(
                        itemsError = itemsError,
                        onDismissDialog = viewModel::onDismissDialog,
                        onDeleteItems = { viewModel.delete(it) },
                        onEmptyTrash = { viewModel.emptyTrash() },
                        deleteItemsFromCollection = { viewModel.deleteItemsFromCollection(it) },
                    )
                }

                if (viewState.isGeneratingBibliography) {
                    GeneratingBibliographyLoadingIndicator()
                }
                if (viewState.isGeneratingCitation) {
                    GeneratingCitationLoadingIndicator()
                }
            }
            if (!isTablet && viewState.isEditing) {
                androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
                    BookshelfPhoneSelectionBar(
                        viewModel = viewModel,
                        viewState = viewState,
                        onShelf = { shelf ->
                            val keys = viewModel.bookshelfSelectedKeys()
                            viewModel.bookshelfLibraryId()?.let { bookshelfViewModel.setShelf(it, keys, shelf) }
                            viewModel.onDone()
                        },
                        onTag = {
                            val keys = viewModel.bookshelfSelectedKeys()
                            if (keys.isNotEmpty()) {
                                scope.launch {
                                    tagSuggestions = viewModel.bookshelfLibraryId()?.let { bookshelfViewModel.tagNames(it) } ?: emptyList()
                                    tagDialogKeys = keys
                                }
                            }
                        },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
        val bottomSheetTitle = safeStringResource(id = Strings.item_type)
        AllItemsAddBottomSheet(
            onScanBarcode = viewModel::onScanBarcode,
            onAddFile = onPickFile,
            onAddNote = viewModel::onAddNote,
            onAddManually = { viewModel.onAddManually(bottomSheetTitle) },
            onAddByIdentifier = viewModel::onAddByIdentifier,
            onClose = viewModel::onAddBottomSheetCollapse,
            showBottomSheet = viewState.shouldShowAddBottomSheet
        )

        // Bookshelf: View sheet, tag filter, tagging, and the book page.
        if (showViewSheet) {
            org.zotero.android.bookshelf.BookshelfViewSheet(
                layout = bookshelfLayout,
                coverSize = coverSize,
                sort = viewModel.bookshelfSortType(),
                downloadedOnly = viewState.isDownloadsFilterEnabled(),
                tagFilterCount = viewState.filters
                    .filterIsInstance<org.zotero.android.screens.allitems.data.ItemsFilter.tags>()
                    .sumOf { it.tags.size },
                onLayout = bookshelfViewModel::setLayout,
                onCoverSize = bookshelfViewModel::setCoverSize,
                onSortField = { field ->
                    org.greenrobot.eventbus.EventBus.getDefault().post(
                        org.zotero.android.uicomponents.singlepicker.SinglePickerResult(
                            field.titleStr,
                            org.zotero.android.uicomponents.singlepicker.SinglePickerResult.CallPoint.AllItemsSortPicker,
                        )
                    )
                },
                onSortAscending = { ascending ->
                    org.greenrobot.eventbus.EventBus.getDefault().post(
                        org.zotero.android.screens.sortpicker.data.SortDirectionResult(ascending)
                    )
                },
                onDownloadedOnly = viewModel::onDownloadedFilesTapped,
                onTagFilter = {
                    showViewSheet = false
                    showTagFilterSheet = true
                },
                onDismiss = { showViewSheet = false },
            )
        }
        if (showTagFilterSheet) {
            org.zotero.android.bookshelf.BookshelfContentSheet(onDismiss = { showTagFilterSheet = false }) {
                org.zotero.android.screens.filter.FilterScreenTablet(viewModel.bookshelfFilterArgs())
            }
        }
        val tagKeys = tagDialogKeys
        if (tagKeys != null) {
            org.zotero.android.bookshelf.BookshelfTagDialog(
                count = tagKeys.size,
                suggestions = tagSuggestions,
                onAdd = { name ->
                    viewModel.bookshelfLibraryId()?.let { bookshelfViewModel.addTag(it, tagKeys, name) }
                    tagDialogKeys = null
                    viewModel.onDone()
                },
                onDismiss = { tagDialogKeys = null },
            )
        }
        val pageKey = bookPageKey
        val pageCell = pageKey?.let { key -> viewState.itemCellModels.firstOrNull { it.key == key } }
        if (pageKey != null && pageCell != null) {
            val attachment = viewModel.bookshelfAttachment(pageKey)
            org.zotero.android.bookshelf.BookshelfBookPage(
                data = org.zotero.android.bookshelf.BookshelfBookPageData(
                    key = pageKey,
                    fallbackTitle = pageCell.title,
                    attachment = attachment,
                    shelf = bookshelfSnapshot.shelves[pageKey],
                    progress = org.zotero.android.bookshelf.BookshelfProgress.fraction(
                        attachment,
                        attachment?.let { bookshelfSnapshot.positions[it.key] },
                        pageCounts,
                    ),
                    pages = attachment?.let { pageCounts[it.key] },
                    load = { viewModel.bookshelfLibraryId()?.let { bookshelfViewModel.bookInfo(it, pageKey) } },
                    coverFor = bookshelfViewModel::coverFor,
                    tintFor = bookshelfViewModel::tintFor,
                ),
                onRead = {
                    bookPageKey = null
                    viewModel.onItemTapped(pageCell)
                },
                onSetShelf = { shelf ->
                    viewModel.bookshelfLibraryId()?.let { bookshelfViewModel.setShelf(it, listOf(pageKey), shelf) }
                },
                onOpenNote = { note ->
                    bookPageKey = null
                    if (!viewModel.bookshelfOpenAttachment(note.attachmentKey, note.parentKey, note.key)) {
                        android.widget.Toast.makeText(context, "Download this book first to open it here.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                onShareNote = { note, authors ->
                    scope.launch {
                        val tint = bookshelfViewModel.tintFor(attachment)
                        org.zotero.android.bookshelf.BookshelfQuoteCard.share(context, note.text, note.bookTitle, authors, note.color, tint)
                    }
                },
                onDetails = {
                    bookPageKey = null
                    viewModel.onAccessoryTapped(pageKey)
                },
                onDismiss = { bookPageKey = null },
            )
        }

    }
}