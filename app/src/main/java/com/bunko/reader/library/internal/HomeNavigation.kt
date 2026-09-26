package com.bunko.reader.library.internal

import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import com.bunko.reader.ui.theme.themeToggleModifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.rotate
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.res.painterResource
import com.bunko.reader.R
import androidx.compose.material3.VerticalDivider
import com.bunko.reader.library.detail.LocalBookDetailContent
import com.bunko.reader.library.dialogs.BunkoSortViewDialog
import com.bunko.reader.offline.LocalBook
import com.bunko.reader.offline.LocalBookRepository
import com.bunko.reader.offline.LocalFolder
import com.bunko.reader.series.ChapterPickScreen
import com.bunko.reader.series.SeriesLibrarySort
import com.bunko.reader.KavitaServerProfile
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalWideNavigationRail
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.WideNavigationRailDefaults
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.bunko.reader.BuildConfig
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.bunko.reader.CollectionDto
import com.bunko.reader.KavitaApi
import com.bunko.reader.KavitaSession
import com.bunko.reader.KavitaSessionStore
import com.bunko.reader.LibraryDto
import com.bunko.reader.SearchHistoryStore
import com.bunko.reader.SeriesDto
import com.bunko.reader.download.OfflineIssueRecord
import com.bunko.reader.library.HomeShelfKind
import com.bunko.reader.library.SearchSeriesTarget
import com.bunko.reader.ui.theme.LocalThemeTransitionState
import com.bunko.reader.NavigationBarStyle
import com.bunko.reader.ui.theme.BunkoChrome
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
enum class HomeDestination(
    val label: String,
    val icon: ImageVector,
    val expandedLabel: String = label
) {
    Home("Home", Icons.Filled.Home),
    History("History", Icons.Filled.History),
    Libraries("Libraries", Icons.Filled.CollectionsBookmark),
    WantToRead("Want", Icons.Filled.BookmarkBorder, "Want to Read"),
    Browse("Browse", Icons.Filled.Explore),
    Search("Search", Icons.Filled.Search)
}

/** Internal to library, not for external use. */
@Composable
internal fun HomeShell(
    libraries: List<LibraryDto>,
    librarySeriesCounts: Map<Int, Int>,
    isAdmin: Boolean,
    scanningLibraryIds: Set<Int>,
    serverName: String,
    loading: Boolean,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    error: String?,
    session: KavitaSession,
    sessionStore: KavitaSessionStore,
    onDeck: List<SeriesDto>,
    recentlyUpdated: List<SeriesDto>,
    newlyAdded: List<SeriesDto>,
    wantToRead: List<SeriesDto>,
    wantToReadError: String?,
    wantToReadHasMore: Boolean,
    wantToReadLoadingMore: Boolean,
    wantToReadLoadMoreError: String?,
    downloaded: List<OfflineIssueRecord>,
    api: KavitaApi?,
    searchHistoryStore: SearchHistoryStore,
    initialSearchQuery: String = "",
    onOpenSettings: () -> Unit,
    onOpenManageServers: () -> Unit = onOpenSettings,
    onOpenShelf: (HomeShelfKind) -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenCollections: () -> Unit,
    onOpenDownloaded: () -> Unit,
    onOpenBookmark: (libraryId: Int, seriesId: Int, volumeId: Int, chapterId: Int, page: Int) -> Unit = { _, _, _, _, _ -> },
    onOpenCollection: (CollectionDto) -> Unit = {},
    onPickIssue: (libraryId: Int, seriesId: Int, volumeId: Int, chapterId: Int, incognito: Boolean) -> Unit = { _, _, _, _, _ -> },
    onOpenFilteredSeries: (SearchSeriesTarget, Int, String) -> Unit,
    onSelectLibrary: (LibraryDto) -> Unit,
    onScanLibrary: (LibraryDto) -> Unit,
    onSelectSeries: (SeriesDto, HomeDestination) -> Unit,
    onRemoveWantToRead: (List<SeriesDto>) -> Unit,
    onLoadMoreWantToRead: () -> Unit,
    onLoadAllWantToRead: suspend () -> Result<List<SeriesDto>>,
    onSwitchToOffline: (() -> Unit)? = null,
    isOffline: Boolean = false,
    offlineBooks: List<LocalBook> = emptyList(),
    offlineFolders: List<LocalFolder> = emptyList(),
    offlineFolderName: String? = null,
    isOfflineScanning: Boolean = false,
    onOpenOfflineBook: (LocalBook) -> Unit = {},
    onChangeOfflineFolder: () -> Unit = {},
    onAddOfflineFolder: () -> Unit = onChangeOfflineFolder,
    onRescanOffline: () -> Unit = {},
    onToggleLibraryMode: () -> Unit = {},
    onToggleTheme: (() -> Unit)? = null,
    navigationBarStyle: NavigationBarStyle = NavigationBarStyle.Standard,
    initialDestination: HomeDestination? = null
) {
    var destination by rememberSaveable(
        initialSearchQuery,
        initialDestination,
        stateSaver = Saver(
            save = { it.ordinal },
            restore = { HomeDestination.entries[it] }
        )
    ) {
        mutableStateOf(
            when {
                initialDestination != null && initialDestination != HomeDestination.Search -> initialDestination
                else -> HomeDestination.Home
            }
        )
    }

    // mpvRx-style inline search: the query field lives in the top bar and the
    // results overlay the current tab, which stays mounted underneath.
    var searchQuery by rememberSaveable(initialSearchQuery) { mutableStateOf(initialSearchQuery) }
    var isSearching by rememberSaveable(initialSearchQuery, initialDestination) {
        mutableStateOf(initialSearchQuery.isNotBlank() || initialDestination == HomeDestination.Search)
    }
    // One-time migration off the retired Search page (e.g. restored state).
    if (destination == HomeDestination.Search) {
        destination = HomeDestination.Home
        isSearching = true
    }

    var reselectionCount by remember { mutableIntStateOf(0) }
    var isGridView by rememberSaveable { mutableStateOf(true) }
    var isSortDescending by rememberSaveable { mutableStateOf(false) }
    var selectedSort by rememberSaveable { mutableStateOf(LocalBookSort.Modified) }
    var kavitaSort by rememberSaveable { mutableStateOf(SeriesLibrarySort.Title) }
    var isSortViewDialogOpen by rememberSaveable { mutableStateOf(false) }
    var kavitaProfiles by remember { mutableStateOf<List<KavitaServerProfile>>(emptyList()) }
    var activeKavitaProfile by remember { mutableStateOf<KavitaServerProfile?>(null) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(sessionStore) {
        kavitaProfiles = sessionStore.profiles()
        activeKavitaProfile = sessionStore.activeProfile()
    }

    var browseDrilldown by rememberSaveable { mutableStateOf<BrowseDrilldown?>(null) }
    var selectedLibrary by remember { mutableStateOf<LibraryDto?>(null) }
    var selectedShelf by remember { mutableStateOf<HomeShelfKind?>(null) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val localRepository = remember(ctx) { LocalBookRepository(ctx) }
    var selectedOfflineBook by remember { mutableStateOf<LocalBook?>(null) }
    var selectedKavitaSeries by remember { mutableStateOf<SeriesDto?>(null) }

    LaunchedEffect(offlineBooks, selectedOfflineBook?.id) {
        val currentId = selectedOfflineBook?.id ?: return@LaunchedEffect
        val updated = offlineBooks.firstOrNull { it.id == currentId }
        if (updated != null) {
            selectedOfflineBook = updated
        }
    }

    LaunchedEffect(initialDestination) {
        if (initialDestination == HomeDestination.Search) {
            isSearching = true
            return@LaunchedEffect
        }
        if (initialDestination != null && initialDestination != destination) {
            destination = initialDestination
            browseDrilldown = null
            selectedShelf = null
            selectedOfflineBook = null
            selectedKavitaSeries = null
            if (initialDestination != HomeDestination.Libraries) {
                selectedLibrary = null
            }
        }
    }

    fun selectDestination(next: HomeDestination) {
        selectedOfflineBook = null
        selectedKavitaSeries = null
        if (next == destination) {
            reselectionCount++
            // Reset drilldowns on re-clicking the same tab
            browseDrilldown = null
            selectedLibrary = null
            selectedShelf = null
        } else {
            destination = next
            browseDrilldown = null
            selectedShelf = null
            if (next != HomeDestination.Libraries) {
                selectedLibrary = null
            }
        }
    }

    BackHandler(enabled = selectedOfflineBook != null || selectedKavitaSeries != null) {
        selectedOfflineBook = null
        selectedKavitaSeries = null
    }
    BackHandler(enabled = (selectedOfflineBook == null && selectedKavitaSeries == null) && browseDrilldown != null) {
        browseDrilldown = null
    }
    BackHandler(enabled = (selectedOfflineBook == null && selectedKavitaSeries == null) && browseDrilldown == null && selectedLibrary != null) {
        selectedLibrary = null
    }
    BackHandler(enabled = (selectedOfflineBook == null && selectedKavitaSeries == null) && browseDrilldown == null && selectedLibrary == null && selectedShelf != null) {
        selectedShelf = null
    }
    BackHandler(enabled = (selectedOfflineBook == null && selectedKavitaSeries == null) && browseDrilldown == null && selectedLibrary == null && selectedShelf == null && destination != HomeDestination.Home) {
        selectDestination(HomeDestination.Home)
    }
    // Registered last so exiting search wins over every destination handler.
    BackHandler(enabled = isSearching) {
        isSearching = false
        searchQuery = ""
    }

    fun openInlineSearch() {
        isSearching = true
    }

    fun closeInlineSearch() {
        isSearching = false
        searchQuery = ""
    }

    if (isSortViewDialogOpen) {
        BunkoSortViewDialog(
            isOffline = isOffline,
            selectedLocalSort = selectedSort,
            onLocalSortChange = { selectedSort = it },
            selectedKavitaSort = kavitaSort,
            onKavitaSortChange = { kavitaSort = it },
            isSortDescending = isSortDescending,
            onSortDescendingChange = { isSortDescending = it },
            isGridView = isGridView,
            onGridViewChange = { isGridView = it },
            onDismissRequest = { isSortViewDialogOpen = false }
        )
    }

    val topBarActions: @Composable RowScope.() -> Unit = {
        val showSortViewAction = isOffline || (destination == HomeDestination.Home || destination == HomeDestination.History || destination == HomeDestination.Libraries || destination == HomeDestination.WantToRead)
        if (showSortViewAction) {
            IconButton(onClick = { isSortViewDialogOpen = true }) {
                Icon(
                    imageVector = Icons.Filled.Sort,
                    contentDescription = "Sort and view options",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val configuration = LocalConfiguration.current
        val isTablet = configuration.smallestScreenWidthDp >= 600
        val isWide = isTablet && maxWidth >= 720.dp
        val showNavigationRail = isWide && navigationBarStyle == NavigationBarStyle.Standard
        val layoutDirection = LocalLayoutDirection.current
        val cutoutInsets = WindowInsets.displayCutout.asPaddingValues()
        val startCutoutPadding = cutoutInsets.calculateStartPadding(layoutDirection)
        val endCutoutPadding = cutoutInsets.calculateEndPadding(layoutDirection)
        val contentCutoutModifier = Modifier.padding(start = startCutoutPadding, end = endCutoutPadding)

        val topBarBackAction: (() -> Unit)? = when {
            destination == HomeDestination.Browse && browseDrilldown != null -> {
                { browseDrilldown = null }
            }
            destination == HomeDestination.Libraries && selectedLibrary != null && !isWide -> {
                { selectedLibrary = null }
            }
            destination == HomeDestination.Home && selectedShelf != null -> {
                { selectedShelf = null }
            }
            else -> null
        }

        val topBarTitle = when {
            destination == HomeDestination.Browse && browseDrilldown != null -> when (browseDrilldown) {
                BrowseDrilldown.Bookmarks -> "Bookmarks"
                BrowseDrilldown.Collections -> "Collections"
                BrowseDrilldown.ReadingLists -> "Reading Lists"
                BrowseDrilldown.Downloaded -> "Downloaded"
                null -> "Browse"
            }
            destination == HomeDestination.Libraries && selectedLibrary != null && !isWide -> selectedLibrary?.name.orEmpty()
            destination == HomeDestination.Home && selectedShelf != null -> selectedShelf?.title.orEmpty()
            else -> when (destination) {
                HomeDestination.Home -> "Bunko"
                HomeDestination.History -> "History"
                HomeDestination.Libraries -> "Libraries"
                HomeDestination.WantToRead -> "Want to Read"
                HomeDestination.Browse -> "Browse"
                HomeDestination.Search -> "Search"
            }
        }

        val handleOpenOfflineBook: (LocalBook) -> Unit = { book ->
            if (isWide) {
                selectedOfflineBook = book
                selectedKavitaSeries = null
            } else {
                onOpenOfflineBook(book)
            }
        }

        val handleSelectSeries: (SeriesDto, HomeDestination) -> Unit = { series, tab ->
            if (isWide) {
                selectedKavitaSeries = series
                selectedOfflineBook = null
            } else {
                onSelectSeries(series, tab)
            }
        }

        val mainListContent: @Composable () -> Unit = {
            HomeContent(
                destination = destination,
                scrollToTopSignal = reselectionCount,
                libraries = libraries,
                librarySeriesCounts = librarySeriesCounts,
                isAdmin = isAdmin,
                scanningLibraryIds = scanningLibraryIds,
                loading = loading,
                refreshing = refreshing,
                onRefresh = onRefresh,
                error = error,
                session = session,
                sessionStore = sessionStore,
                onDeck = onDeck,
                recentlyUpdated = recentlyUpdated,
                newlyAdded = newlyAdded,
                wantToRead = wantToRead,
                wantToReadError = wantToReadError,
                wantToReadHasMore = wantToReadHasMore,
                wantToReadLoadingMore = wantToReadLoadingMore,
                wantToReadLoadMoreError = wantToReadLoadMoreError,
                downloaded = downloaded,
                api = api,
                searchHistoryStore = searchHistoryStore,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                isSearching = isSearching,
                onOpenInlineSearch = ::openInlineSearch,
                selectedLibrary = selectedLibrary,
                onSelectLibraryChange = { selectedLibrary = it },
                selectedShelf = selectedShelf,
                onSelectShelfChange = { selectedShelf = it },
                browseDrilldown = browseDrilldown,
                onBrowseDrilldownChange = { browseDrilldown = it },
                onSelectLibrary = { lib ->
                    selectedLibrary = lib
                    destination = HomeDestination.Libraries
                },
                onScanLibrary = onScanLibrary,
                onSelectSeries = handleSelectSeries,
                onOpenShelf = { shelfKind ->
                    if (shelfKind == HomeShelfKind.OnDeck) {
                        selectDestination(HomeDestination.History)
                    } else {
                        selectedShelf = shelfKind
                        destination = HomeDestination.Home
                    }
                },
                onRemoveWantToRead = onRemoveWantToRead,
                onLoadMoreWantToRead = { onLoadMoreWantToRead() },
                onLoadAllWantToRead = onLoadAllWantToRead,
                onOpenBookmarks = {
                    selectDestination(HomeDestination.Browse)
                    browseDrilldown = BrowseDrilldown.Bookmarks
                },
                onOpenCollections = {
                    selectDestination(HomeDestination.Browse)
                    browseDrilldown = BrowseDrilldown.Collections
                },
                onOpenDownloaded = {
                    selectDestination(HomeDestination.Browse)
                    browseDrilldown = BrowseDrilldown.Downloaded
                },
                onOpenBookmark = onOpenBookmark,
                onOpenCollection = onOpenCollection,
                onPickIssue = onPickIssue,
                onOpenFilteredSeries = onOpenFilteredSeries,
                isOffline = isOffline,
                offlineBooks = offlineBooks,
                offlineFolders = offlineFolders,
                offlineFolderName = offlineFolderName,
                isOfflineScanning = isOfflineScanning,
                onOpenOfflineBook = handleOpenOfflineBook,
                onChangeOfflineFolder = onChangeOfflineFolder,
                onAddOfflineFolder = onAddOfflineFolder,
                onRescanOffline = onRescanOffline,
                selectedSort = selectedSort,
                kavitaSort = kavitaSort,
                isSortDescending = isSortDescending,
                isGridView = isGridView,
                onSelectDestination = ::selectDestination,
                modifier = Modifier.fillMaxSize()
            )
        }

        val topBarBlock: @Composable () -> Unit = {
            HomeTopBar(
                title = topBarTitle,
                onBack = topBarBackAction,
                showModeSwitch = topBarBackAction == null && (destination == HomeDestination.Home || isOffline),
                isOffline = isOffline,
                activeServerName = if (isOffline) "Local" else (activeKavitaProfile?.name?.ifBlank { "Kavita" } ?: "Kavita"),
                kavitaProfiles = kavitaProfiles,
                activeKavitaProfileId = activeKavitaProfile?.id,
                onSelectModeAndProfile = { mode, profileId ->
                    coroutineScope.launch {
                        if (mode == "kavita") {
                            if (profileId != null) {
                                sessionStore.selectProfile(profileId)
                                sessionStore.setDefaultProfile(profileId)
                            }
                            localRepository.setActiveMode("kavita")
                            if (isOffline) {
                                onToggleLibraryMode()
                            }
                        } else {
                            localRepository.setActiveMode("offline")
                            if (!isOffline) {
                                onToggleLibraryMode()
                            }
                        }
                        kavitaProfiles = sessionStore.profiles()
                        activeKavitaProfile = sessionStore.activeProfile()
                    }
                },
                onOpenSettings = onOpenSettings,
                onOpenManageServers = onOpenManageServers,
                onSearch = ::openInlineSearch,
                isSearchActive = isSearching,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onCloseSearch = ::closeInlineSearch,
                onSwitchMode = onToggleLibraryMode,
                onToggleTheme = onToggleTheme,
                actions = topBarActions
            )
        }

        if (showNavigationRail) {
            Row(Modifier.fillMaxSize().then(contentCutoutModifier)) {
                HomeNavigationRail(
                    selected = destination,
                    onSelect = ::selectDestination
                )
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    DualPaneOrSingleContent(
                        isWide = isWide,
                        selectedOfflineBook = selectedOfflineBook,
                        selectedKavitaSeries = selectedKavitaSeries,
                        onCloseDetail = {
                            selectedOfflineBook = null
                            selectedKavitaSeries = null
                        },
                        onSelectOfflineBook = { selectedOfflineBook = it },
                        offlineBooks = offlineBooks,
                        localRepository = localRepository,
                        sessionStore = sessionStore,
                        onOpenFilteredSeries = onOpenFilteredSeries,
                        onOpenSettings = onOpenSettings,
                        onPickIssue = onPickIssue,
                        onOpenOfflineBookReader = onOpenOfflineBook,
                        topBarContent = topBarBlock,
                        listContent = mainListContent,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Spacer(Modifier.navigationBarsPadding())
                    }
                }
            }
        } else if (navigationBarStyle == NavigationBarStyle.FloatingPill) {
            Box(Modifier.fillMaxSize().then(contentCutoutModifier)) {
                DualPaneOrSingleContent(
                    isWide = isWide,
                    selectedOfflineBook = selectedOfflineBook,
                    selectedKavitaSeries = selectedKavitaSeries,
                    onCloseDetail = {
                        selectedOfflineBook = null
                        selectedKavitaSeries = null
                    },
                    onSelectOfflineBook = { selectedOfflineBook = it },
                    offlineBooks = offlineBooks,
                    localRepository = localRepository,
                    sessionStore = sessionStore,
                    onOpenFilteredSeries = onOpenFilteredSeries,
                    onOpenSettings = onOpenSettings,
                    onPickIssue = onPickIssue,
                    onOpenOfflineBookReader = onOpenOfflineBook,
                    topBarContent = topBarBlock,
                    listContent = mainListContent,
                    modifier = Modifier.fillMaxSize()
                )
                HomeBottomNavigation(
                    selected = destination,
                    onSelect = ::selectDestination,
                    navigationBarStyle = navigationBarStyle,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        } else {
            Column(Modifier.fillMaxSize().then(contentCutoutModifier)) {
                DualPaneOrSingleContent(
                    isWide = isWide,
                    selectedOfflineBook = selectedOfflineBook,
                    selectedKavitaSeries = selectedKavitaSeries,
                    onCloseDetail = {
                        selectedOfflineBook = null
                        selectedKavitaSeries = null
                    },
                    onSelectOfflineBook = { selectedOfflineBook = it },
                    offlineBooks = offlineBooks,
                    localRepository = localRepository,
                    sessionStore = sessionStore,
                    onOpenFilteredSeries = onOpenFilteredSeries,
                    onOpenSettings = onOpenSettings,
                    onPickIssue = onPickIssue,
                    onOpenOfflineBookReader = onOpenOfflineBook,
                    topBarContent = topBarBlock,
                    listContent = mainListContent,
                    modifier = Modifier.weight(1f)
                )
                HomeBottomNavigation(
                    selected = destination,
                    onSelect = ::selectDestination,
                    navigationBarStyle = navigationBarStyle
                )
            }
        }
    }
}

@Composable
private fun DualPaneOrSingleContent(
    modifier: Modifier = Modifier,
    isWide: Boolean,
    selectedOfflineBook: LocalBook?,
    selectedKavitaSeries: SeriesDto?,
    onCloseDetail: () -> Unit,
    onSelectOfflineBook: (LocalBook) -> Unit = {},
    offlineBooks: List<LocalBook>,
    localRepository: LocalBookRepository,
    sessionStore: KavitaSessionStore,
    onOpenFilteredSeries: (SearchSeriesTarget, Int, String) -> Unit,
    onOpenSettings: () -> Unit,
    onPickIssue: (libraryId: Int, seriesId: Int, volumeId: Int, chapterId: Int, incognito: Boolean) -> Unit,
    onOpenOfflineBookReader: (LocalBook) -> Unit,
    topBarContent: @Composable () -> Unit,
    listContent: @Composable () -> Unit
) {
    val hasDetail = isWide && (selectedOfflineBook != null || selectedKavitaSeries != null)
    if (hasDetail) {
        Row(modifier = modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .weight(0.42f)
                    .fillMaxHeight()
            ) {
                topBarContent()
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    listContent()
                }
            }

            VerticalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )

            Box(
                modifier = Modifier
                    .weight(0.58f)
                    .fillMaxHeight()
            ) {
                if (selectedOfflineBook != null) {
                    LocalBookDetailContent(
                        book = selectedOfflineBook,
                        allBooks = offlineBooks,
                        localRepository = localRepository,
                        onBack = onCloseDetail,
                        onOpenReader = { targetBook, startPage -> onOpenOfflineBookReader(targetBook) },
                        isDualPane = true,
                        onSelectBook = onSelectOfflineBook,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (selectedKavitaSeries != null) {
                    ChapterPickScreen(
                        sessionStore = sessionStore,
                        libraryId = selectedKavitaSeries.libraryId ?: 0,
                        seriesId = selectedKavitaSeries.id,
                        seriesName = selectedKavitaSeries.name,
                        onOpenFilteredSeries = onOpenFilteredSeries,
                        onOpenSettings = onOpenSettings,
                        onBack = onCloseDetail,
                        isDualPane = true,
                        onPick = { chapterId, volumeId, incognito, initialPage ->
                            onPickIssue(selectedKavitaSeries.libraryId ?: 0, selectedKavitaSeries.id, volumeId, chapterId, incognito)
                        }
                    )
                }
            }
        }
    } else {
        Column(modifier = modifier.fillMaxSize()) {
            topBarContent()
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                listContent()
            }
        }
    }
}

/** Internal to library, not for external use. */
@Composable
internal fun HomeTopBar(
    title: String = "Bunko",
    onBack: (() -> Unit)? = null,
    showModeSwitch: Boolean = onBack == null,
    isOffline: Boolean = false,
    activeServerName: String = if (isOffline) "Local" else "Kavita",
    kavitaProfiles: List<KavitaServerProfile> = emptyList(),
    activeKavitaProfileId: String? = null,
    onSelectModeAndProfile: ((String, String?) -> Unit)? = null,
    onOpenSettings: () -> Unit,
    onOpenManageServers: () -> Unit = onOpenSettings,
    onSearch: (() -> Unit)? = null,
    isSearchActive: Boolean = false,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onCloseSearch: () -> Unit = {},
    onSwitchMode: (() -> Unit)? = null,
    onToggleTheme: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    var modeMenuExpanded by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        // mpvRx-style: searching swaps the whole top bar for an inline field;
        // the tab underneath stays mounted.
        if (isSearchActive && onSearch != null) {
            HomeSearchTopBar(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                onClose = onCloseSearch
            )
        } else {
        val statusInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout).asPaddingValues()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = if (onBack != null) 4.dp else 16.dp,
                    end = 4.dp,
                    top = statusInsets.calculateTopPadding() + 8.dp,
                    bottom = 8.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (onBack != null) 4.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = buildAnnotatedString {
                        append(title)
                        if (BuildConfig.IS_PREVIEW_BUILD && title.startsWith("Bunko", ignoreCase = true)) {
                            withStyle(
                                SpanStyle(
                                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                    fontWeight = FontWeight.SemiBold,
                                    baselineShift = BaselineShift.Superscript
                                )
                            ) {
                                append(" (Beta)")
                            }
                        }
                    },
                    color = MaterialTheme.colorScheme.primary,
                    style = if (onBack == null) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.then(themeToggleModifier(explicitToggle = onToggleTheme))
                )

                if (showModeSwitch) {
                    Box {
                        Surface(
                            onClick = { modeMenuExpanded = true },
                            shape = CircleShape,
                            color = Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                if (isOffline) {
                                    Icon(
                                        imageVector = Icons.Filled.Folder,
                                        contentDescription = "Active source: Local Storage",
                                        modifier = Modifier.size(24.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_kavita_logo),
                                        contentDescription = "Active source: Kavita",
                                        modifier = Modifier.size(24.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Filled.ArrowDropDown,
                                    contentDescription = "Switch library source",
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = modeMenuExpanded,
                            onDismissRequest = { modeMenuExpanded = false },
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            DropdownMenuItem(
                                text = { Text("Local Storage", color = MaterialTheme.colorScheme.onSurface, fontWeight = if (isOffline) FontWeight.Bold else FontWeight.Normal) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Folder,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = if (isOffline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                trailingIcon = {
                                    if (isOffline) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                },
                                onClick = {
                                    modeMenuExpanded = false
                                    onSelectModeAndProfile?.invoke("offline", null) ?: run {
                                        if (!isOffline) onSwitchMode?.invoke()
                                    }
                                }
                            )

                            if (kavitaProfiles.isNotEmpty()) {
                                kavitaProfiles.forEach { profile ->
                                    val isSelected = !isOffline && (activeKavitaProfileId == profile.id || (activeKavitaProfileId == null && profile.openByDefault))
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = profile.name.ifBlank { "Kavita Server" },
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_kavita_logo),
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp),
                                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        },
                                        trailingIcon = {
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Filled.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        },
                                        onClick = {
                                            modeMenuExpanded = false
                                            onSelectModeAndProfile?.invoke("kavita", profile.id)
                                        }
                                    )
                                }
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Kavita", color = MaterialTheme.colorScheme.onSurface, fontWeight = if (!isOffline) FontWeight.Bold else FontWeight.Normal) },
                                    leadingIcon = {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_kavita_logo),
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = if (!isOffline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    trailingIcon = {
                                        if (!isOffline) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    },
                                    onClick = {
                                        modeMenuExpanded = false
                                        onSelectModeAndProfile?.invoke("kavita", null) ?: run {
                                            if (isOffline) onSwitchMode?.invoke()
                                        }
                                    }
                                )
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )

                            DropdownMenuItem(
                                text = { Text("Manage Servers", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Settings,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                onClick = {
                                    modeMenuExpanded = false
                                    onOpenManageServers()
                                }
                            )
                        }
                    }
                }
            }

            if (onSearch != null) {
                IconButton(onClick = onSearch) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "Search",
                        tint = if (isSearchActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            actions()

            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        }
    }
}

/** Inline query field that replaces the top bar while searching (mpvRx-style). */
@Composable
internal fun HomeSearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val statusInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout).asPaddingValues()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = statusInsets.calculateTopPadding() + 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = {
                keyboardController?.hide()
                onClose()
            }
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Close search",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            placeholder = { Text("Search") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Clear search",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            },
            singleLine = true,
            shape = CircleShape,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = { keyboardController?.hide() }
            ),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        )
    }
}

/** Backward-compatible overload for existing Kavita Home calls. */
@Composable
internal fun HomeTopBar(
    serverName: String,
    onOpenSettings: () -> Unit,
    onSwitchToOffline: (() -> Unit)? = null
) = HomeTopBar(
    title = "Bunko",
    isOffline = false,
    onOpenSettings = onOpenSettings,
    onSwitchMode = onSwitchToOffline
)

private val MainNavDestinations = listOf(
    HomeDestination.Home,
    HomeDestination.History,
    HomeDestination.Libraries,
    HomeDestination.WantToRead,
    HomeDestination.Browse
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
/** Internal to library, not for external use. */
@Composable
internal fun HomeNavigationRail(
    selected: HomeDestination,
    onSelect: (HomeDestination) -> Unit
) {
    val railState = rememberWideNavigationRailState()
    val scope = rememberCoroutineScope()
    val expanded = railState.targetValue == WideNavigationRailValue.Expanded

    MaterialTheme(motionScheme = MotionScheme.expressive()) {
        ModalWideNavigationRail(
            modifier = Modifier
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceContainer),
            state = railState,
            arrangement = Arrangement.Center,
            hideOnCollapse = false,
            colors = WideNavigationRailDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modalContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                modalContentColor = MaterialTheme.colorScheme.onSurface
            ),
            header = null
        ) {
            MainNavDestinations.forEach { destination ->
                WideNavigationRailItem(
                    selected = selected == destination,
                    onClick = {
                        onSelect(destination)
                        if (expanded) scope.launch { railState.collapse() }
                    },
                    icon = { NavDestinationIcon(destination, selected == destination) },
                    label = {
                        Text(if (expanded) destination.expandedLabel else destination.label)
                    },
                    railExpanded = expanded
                )
            }
        }
    }
}

/** Internal to library, not for external use. */
@Composable
internal fun HomeBottomNavigation(
    selected: HomeDestination,
    onSelect: (HomeDestination) -> Unit,
    navigationBarStyle: NavigationBarStyle = NavigationBarStyle.Standard,
    modifier: Modifier = Modifier
) {
    when (navigationBarStyle) {
        NavigationBarStyle.FloatingPill -> {
            val haptics = LocalHapticFeedback.current
            var bottomDragOffset by remember { mutableFloatStateOf(0f) }
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 8.dp)
                    .pointerInput(selected) {
                        detectHorizontalDragGestures(
                            onDragStart = { bottomDragOffset = 0f },
                            onDragEnd = {
                                val currentIndex = MainNavDestinations.indexOf(selected)
                                if (bottomDragOffset < -40f && currentIndex < MainNavDestinations.lastIndex) {
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    onSelect(MainNavDestinations[currentIndex + 1])
                                } else if (bottomDragOffset > 40f && currentIndex > 0) {
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    onSelect(MainNavDestinations[currentIndex - 1])
                                }
                                bottomDragOffset = 0f
                            },
                            onDragCancel = { bottomDragOffset = 0f },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                bottomDragOffset += dragAmount
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                FloatingPillNavigationBar(
                    destinations = MainNavDestinations,
                    selected = selected,
                    onSelect = onSelect
                )
            }
        }
        NavigationBarStyle.Standard -> {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(Modifier.navigationBarsPadding()) {
                    ShortNavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        MainNavDestinations.forEach { destination ->
                            ShortNavigationBarItem(
                                selected = selected == destination,
                                onClick = { onSelect(destination) },
                                icon = { NavDestinationIcon(destination, selected == destination) },
                                label = { Text(destination.label) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Internal to library, not for external use. */
@Composable
internal fun NavDestinationIcon(destination: HomeDestination, selected: Boolean) {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected) {
            scale.snapTo(0.8f)
            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
    }
    Icon(
        imageVector = destination.icon,
        contentDescription = destination.label,
        modifier = Modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
    )
}

