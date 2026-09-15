package com.bunko.reader.library.internal

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.ViewList
import com.bunko.reader.offline.LocalBook
import com.bunko.reader.offline.LocalFolder
import com.bunko.reader.series.SeriesLibrarySort
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.bunko.reader.ui.theme.BunkoBackground
import com.bunko.reader.ui.theme.BunkoChrome
internal enum class HomeDestination(
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
    onSelectSeries: (SeriesDto) -> Unit,
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
    onToggleTheme: (() -> Unit)? = null
) {
    var destination by rememberSaveable(
        initialSearchQuery,
        stateSaver = Saver(
            save = { it.ordinal },
            restore = { HomeDestination.entries[it] }
        )
    ) {
        mutableStateOf(
            if (initialSearchQuery.isNotBlank()) HomeDestination.Search else HomeDestination.Home
        )
    }
    var reselectionCount by remember { mutableIntStateOf(0) }
    var isGridView by rememberSaveable { mutableStateOf(true) }
    var selectedSort by rememberSaveable { mutableStateOf(LocalBookSort.Title) }
    var kavitaSort by rememberSaveable { mutableStateOf(SeriesLibrarySort.Title) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    var browseDrilldown by rememberSaveable { mutableStateOf<BrowseDrilldown?>(null) }
    var selectedLibrary by remember { mutableStateOf<LibraryDto?>(null) }
    var selectedShelf by remember { mutableStateOf<HomeShelfKind?>(null) }

    fun selectDestination(next: HomeDestination) {
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

    BackHandler(enabled = browseDrilldown != null) {
        browseDrilldown = null
    }
    BackHandler(enabled = browseDrilldown == null && selectedLibrary != null) {
        selectedLibrary = null
    }
    BackHandler(enabled = browseDrilldown == null && selectedLibrary == null && selectedShelf != null) {
        selectedShelf = null
    }
    BackHandler(enabled = browseDrilldown == null && selectedLibrary == null && selectedShelf == null && destination != HomeDestination.Home) {
        selectDestination(HomeDestination.Home)
    }

    val topBarActions: @Composable RowScope.() -> Unit = {
        if (isOffline) {
            Box {
                IconButton(onClick = { sortMenuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Filled.Sort,
                        contentDescription = "Sort options",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    LocalBookSort.entries.forEach { sortOption ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = sortOption.label,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (sortOption == selectedSort) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            trailingIcon = {
                                if (sortOption == selectedSort) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            onClick = {
                                selectedSort = sortOption
                                sortMenuExpanded = false
                            }
                        )
                    }
                }
            }
            IconButton(onClick = { isGridView = !isGridView }) {
                Icon(
                    imageVector = if (isGridView) Icons.Filled.ViewList else Icons.Filled.GridView,
                    contentDescription = if (isGridView) "Switch to list view" else "Switch to grid view",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        } else if (destination == HomeDestination.Home || destination == HomeDestination.History || destination == HomeDestination.Libraries || destination == HomeDestination.WantToRead) {
            Box {
                IconButton(onClick = { sortMenuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Filled.Sort,
                        contentDescription = "Sort options",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    SeriesLibrarySort.entries.forEach { sortOption ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = sortOption.label,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (sortOption == kavitaSort) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            trailingIcon = {
                                if (sortOption == kavitaSort) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            onClick = {
                                kavitaSort = sortOption
                                sortMenuExpanded = false
                            }
                        )
                    }
                }
            }
            IconButton(onClick = { isGridView = !isGridView }) {
                Icon(
                    imageVector = if (isGridView) Icons.Filled.ViewList else Icons.Filled.GridView,
                    contentDescription = if (isGridView) "Switch to list view" else "Switch to grid view",
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
        val isWide = maxWidth >= 720.dp

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

        if (isWide) {
            Column(Modifier.fillMaxSize()) {
                HomeTopBar(
                    title = topBarTitle,
                    onBack = topBarBackAction,
                    showModeSwitch = topBarBackAction == null && (destination == HomeDestination.Home || isOffline),
                    isOffline = isOffline,
                    onOpenSettings = onOpenSettings,
                    onSearch = { selectDestination(HomeDestination.Search) },
                    isSearchActive = destination == HomeDestination.Search,
                    onSwitchMode = onToggleLibraryMode,
                    onToggleTheme = onToggleTheme,
                    actions = topBarActions
                )
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    HomeNavigationRail(
                        selected = destination,
                        onSelect = ::selectDestination
                    )
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
                        initialSearchQuery = initialSearchQuery,
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
                        onSelectSeries = onSelectSeries,
                        onOpenShelf = { shelfKind ->
                            if (shelfKind == HomeShelfKind.OnDeck) {
                                selectDestination(HomeDestination.History)
                            } else {
                                selectedShelf = shelfKind
                                destination = HomeDestination.Home
                            }
                        },
                        onRemoveWantToRead = onRemoveWantToRead,
                        onLoadMoreWantToRead = onLoadMoreWantToRead,
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
                        onOpenOfflineBook = onOpenOfflineBook,
                        onChangeOfflineFolder = onChangeOfflineFolder,
                        onAddOfflineFolder = onAddOfflineFolder,
                        onRescanOffline = onRescanOffline,
                        selectedSort = selectedSort,
                        kavitaSort = kavitaSort,
                        isGridView = isGridView,
                        onSelectDestination = ::selectDestination,
                        modifier = Modifier.weight(1f)
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Spacer(Modifier.navigationBarsPadding())
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                HomeTopBar(
                    title = topBarTitle,
                    onBack = topBarBackAction,
                    showModeSwitch = topBarBackAction == null && (destination == HomeDestination.Home || isOffline),
                    isOffline = isOffline,
                    onOpenSettings = onOpenSettings,
                    onSearch = { selectDestination(HomeDestination.Search) },
                    isSearchActive = destination == HomeDestination.Search,
                    onSwitchMode = onToggleLibraryMode,
                    onToggleTheme = onToggleTheme,
                    actions = topBarActions
                )
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
                    initialSearchQuery = initialSearchQuery,
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
                    onSelectSeries = onSelectSeries,
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
                    onOpenOfflineBook = onOpenOfflineBook,
                    onChangeOfflineFolder = onChangeOfflineFolder,
                    onAddOfflineFolder = onAddOfflineFolder,
                    onRescanOffline = onRescanOffline,
                    selectedSort = selectedSort,
                    kavitaSort = kavitaSort,
                    isGridView = isGridView,
                    onSelectDestination = ::selectDestination,
                    modifier = Modifier.weight(1f)
                )
                HomeBottomNavigation(
                    selected = destination,
                    onSelect = ::selectDestination
                )
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
    onOpenSettings: () -> Unit,
    onSearch: (() -> Unit)? = null,
    isSearchActive: Boolean = false,
    onSwitchMode: (() -> Unit)? = null,
    onToggleTheme: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    var modeMenuExpanded by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = if (onBack != null) 4.dp else 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
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
                    text = title,
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
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = if (isOffline) "Offline" else "Kavita",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium
                                )
                                Icon(
                                    imageVector = Icons.Filled.ArrowDropDown,
                                    contentDescription = "Switch library mode",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = modeMenuExpanded,
                            onDismissRequest = { modeMenuExpanded = false },
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            DropdownMenuItem(
                                text = { Text("Kavita Server", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.CloudSync,
                                        contentDescription = null,
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
                                    if (isOffline) onSwitchMode?.invoke()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Offline Library", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Folder,
                                        contentDescription = null,
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
                                    if (!isOffline) onSwitchMode?.invoke()
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
    onSelect: (HomeDestination) -> Unit
) {
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

