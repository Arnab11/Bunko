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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalWideNavigationRail
import androidx.compose.material3.MotionScheme
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
    onLoadAllWantToRead: suspend () -> Result<List<SeriesDto>>
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
                HomeTopBar(serverName = serverName, onOpenSettings = onOpenSettings)
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
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                HomeTopBar(serverName = serverName, onOpenSettings = onOpenSettings)
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
internal fun HomeTopBar(serverName: String, onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BunkoChrome)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Bunko", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(serverName, color = Color(0xFFB9BDBD), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onOpenSettings) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "Settings",
                tint = Color.White
            )
        }
    }
}

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
            header = {
                IconButton(
                    modifier = Modifier
                        .padding(start = 24.dp)
                        .semantics {
                            stateDescription = if (expanded) "Expanded" else "Collapsed"
                        },
                    onClick = { scope.launch { railState.toggle() } }
                ) {
                    Icon(
                        imageVector = if (expanded) {
                            Icons.AutoMirrored.Filled.MenuOpen
                        } else {
                            Icons.Filled.Menu
                        },
                        contentDescription = if (expanded) {
                            "Collapse navigation"
                        } else {
                            "Expand navigation"
                        }
                    )
                }
            }
        ) {
            HomeDestination.entries.forEach { destination ->
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
        HomeDestination.entries.forEach { destination ->
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

