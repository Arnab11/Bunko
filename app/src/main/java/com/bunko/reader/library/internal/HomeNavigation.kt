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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Folder
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
import com.bunko.reader.series.SeriesLibrarySort
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import com.bunko.reader.KavitaApi
import com.bunko.reader.KavitaSession
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
    offlineFolderName: String? = null,
    isOfflineScanning: Boolean = false,
    onOpenOfflineBook: (LocalBook) -> Unit = {},
    onChangeOfflineFolder: () -> Unit = {},
    onRescanOffline: () -> Unit = {},
    onToggleLibraryMode: () -> Unit = {}
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

    fun selectDestination(next: HomeDestination) {
        if (next == destination) {
            reselectionCount++
        } else {
            destination = next
        }
    }

    // The tabs are internal state on a single nav destination, so on a non-Home tab the
    // system back would pop past Home and exit. Send it to Home instead; Home lets back
    // through (exit).
    BackHandler(enabled = destination != HomeDestination.Home) {
        selectDestination(HomeDestination.Home)
    }

    val topBarSubtitle = if (isOffline) {
        val folderLabel = offlineFolderName ?: "No folder selected"
        val countLabel = if (offlineBooks.size == 1) "1 item" else "${offlineBooks.size} items"
        "$folderLabel • $countLabel"
    } else {
        serverName
    }

    val topBarActions: @Composable RowScope.() -> Unit = {
        IconButton(onClick = { selectDestination(HomeDestination.Search) }) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Search",
                tint = if (destination == HomeDestination.Search) MaterialTheme.colorScheme.primary else Color.White
            )
        }

        if (isOffline) {
            Box {
                IconButton(onClick = { sortMenuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Filled.Sort,
                        contentDescription = "Sort options",
                        tint = Color.White
                    )
                }
                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false },
                    containerColor = Color(0xFF282A2A)
                ) {
                    LocalBookSort.entries.forEach { sortOption ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = sortOption.label,
                                    color = Color.White,
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
                    tint = Color.White
                )
            }
        } else {
            Box {
                IconButton(onClick = { sortMenuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Filled.Sort,
                        contentDescription = "Sort and filter options",
                        tint = Color.White
                    )
                }
                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false },
                    containerColor = Color(0xFF282A2A)
                ) {
                    SeriesLibrarySort.entries.forEach { sortOption ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = sortOption.label,
                                    color = Color.White,
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
                    tint = Color.White
                )
            }
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .background(BunkoBackground)
    ) {
        val wide = maxWidth >= 720.dp
        if (wide) {
            Column(Modifier.fillMaxSize()) {
                HomeTopBar(
                    subtitle = topBarSubtitle,
                    isOffline = isOffline,
                    onOpenSettings = onOpenSettings,
                    onSwitchMode = onToggleLibraryMode,
                    actions = topBarActions
                )
                Row(Modifier.fillMaxSize()) {
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
                        onSelectLibrary = onSelectLibrary,
                        onScanLibrary = onScanLibrary,
                        onSelectSeries = onSelectSeries,
                        onOpenShelf = onOpenShelf,
                        onRemoveWantToRead = onRemoveWantToRead,
                        onLoadMoreWantToRead = onLoadMoreWantToRead,
                        onLoadAllWantToRead = onLoadAllWantToRead,
                        onOpenBookmarks = onOpenBookmarks,
                        onOpenCollections = onOpenCollections,
                        onOpenDownloaded = onOpenDownloaded,
                        onOpenFilteredSeries = onOpenFilteredSeries,
                        isOffline = isOffline,
                        offlineBooks = offlineBooks,
                        offlineFolderName = offlineFolderName,
                        isOfflineScanning = isOfflineScanning,
                        onOpenOfflineBook = onOpenOfflineBook,
                        onChangeOfflineFolder = onChangeOfflineFolder,
                        onRescanOffline = onRescanOffline,
                        selectedSort = selectedSort,
                        kavitaSort = kavitaSort,
                        isGridView = isGridView,
                        onSelectDestination = ::selectDestination,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                HomeTopBar(
                    subtitle = topBarSubtitle,
                    isOffline = isOffline,
                    onOpenSettings = onOpenSettings,
                    onSwitchMode = onToggleLibraryMode,
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
                    onSelectLibrary = onSelectLibrary,
                    onScanLibrary = onScanLibrary,
                    onSelectSeries = onSelectSeries,
                    onOpenShelf = onOpenShelf,
                    onRemoveWantToRead = onRemoveWantToRead,
                    onLoadMoreWantToRead = onLoadMoreWantToRead,
                    onLoadAllWantToRead = onLoadAllWantToRead,
                    onOpenBookmarks = onOpenBookmarks,
                    onOpenCollections = onOpenCollections,
                    onOpenDownloaded = onOpenDownloaded,
                    onOpenFilteredSeries = onOpenFilteredSeries,
                    isOffline = isOffline,
                    offlineBooks = offlineBooks,
                    offlineFolderName = offlineFolderName,
                    isOfflineScanning = isOfflineScanning,
                    onOpenOfflineBook = onOpenOfflineBook,
                    onChangeOfflineFolder = onChangeOfflineFolder,
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
    subtitle: String,
    isOffline: Boolean = false,
    onOpenSettings: () -> Unit,
    onSwitchMode: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    var modeMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF181A1A))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Bunko",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Box {
                    Surface(
                        onClick = { modeMenuExpanded = true },
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.12f),
                        contentColor = Color.White
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
                        containerColor = Color(0xFF282A2A)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Kavita Server", color = Color.White, fontWeight = FontWeight.Medium) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.CloudSync,
                                    contentDescription = null,
                                    tint = if (!isOffline) MaterialTheme.colorScheme.primary else Color(0xFFB9BDBD)
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
                            text = { Text("Offline Library", color = Color.White, fontWeight = FontWeight.Medium) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Folder,
                                    contentDescription = null,
                                    tint = if (isOffline) MaterialTheme.colorScheme.primary else Color(0xFFB9BDBD)
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
            Text(
                text = subtitle,
                color = Color(0xFFB9BDBD),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        actions()
        IconButton(onClick = onOpenSettings) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "Settings",
                tint = Color.White
            )
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
    subtitle = serverName,
    isOffline = false,
    onOpenSettings = onOpenSettings,
    onSwitchMode = onSwitchToOffline
)

private val MainNavDestinations = listOf(
    HomeDestination.Home,
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
                .background(Color(0xFF181A1A)),
            state = railState,
            hideOnCollapse = false,
            colors = WideNavigationRailDefaults.colors(
                containerColor = Color(0xFF181A1A),
                contentColor = Color.White,
                modalContainerColor = BunkoBackground,
                modalContentColor = Color.White
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
    ShortNavigationBar(
        containerColor = Color(0xFF181A1A),
        contentColor = Color.White
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

