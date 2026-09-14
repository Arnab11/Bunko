package com.bunko.reader.series

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bunko.reader.KavitaApi
import com.bunko.reader.KavitaClient
import com.bunko.reader.BunkoLog
import com.bunko.reader.KavitaSession
import com.bunko.reader.KavitaSessionStore
import com.bunko.reader.normalizeKavitaBaseUrl
import com.bunko.reader.SeriesDto
import com.bunko.reader.ui.DarkLoadingState
import com.bunko.reader.ui.DarkMessageState
import com.bunko.reader.ui.BunkoPullToRefreshIndicator
import com.bunko.reader.ui.browse.SeriesListItem
import com.bunko.reader.ui.browse.SeriesPosterCard
import com.bunko.reader.ui.theme.BunkoBackground
import com.bunko.reader.ui.theme.BunkoChrome
import com.bunko.reader.ui.theme.BunkoSurface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal fun chapterCoverUrl(session: KavitaSession, chapterId: Int): String {
    val root = normalizeKavitaBaseUrl(session.baseUrl)
    val apiKey = session.apiKey.takeIf { it.isNotBlank() }?.let { "&apiKey=${Uri.encode(it)}" }.orEmpty()
    return "$root/api/Image/chapter-cover?chapterId=$chapterId$apiKey"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SeriesScreen(
    sessionStore: KavitaSessionStore,
    libraryId: Int,
    libraryName: String,
    onBack: (() -> Unit)? = null,
    onSearchHome: (String) -> Unit = {},
    statusBarPadding: Boolean = true,
    navigationBarPadding: Boolean = true,
    showTopBar: Boolean = statusBarPadding,
    externalSort: SeriesLibrarySort? = null,
    isGridView: Boolean = true,
    onSelect: (SeriesDto) -> Unit
) {
    val ctx = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val searchFocusRequester = remember { FocusRequester() }

    var series by remember { mutableStateOf<List<SeriesDto>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var session by remember { mutableStateOf(KavitaSession()) }
    var api by remember { mutableStateOf<KavitaApi?>(null) }
    var isAdmin by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var scanRunning by remember { mutableStateOf(false) }
    var query by rememberSaveable(libraryId) { mutableStateOf("") }
    var searchActive by rememberSaveable(libraryId) { mutableStateOf(false) }
    var sort by rememberSaveable(libraryId) { mutableStateOf(SeriesLibrarySort.Title) }
    val pullRefreshState = rememberPullToRefreshState()
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    fun showMessage(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    suspend fun loadLibrarySeries(initialLoad: Boolean) {
        if (!initialLoad && refreshing) return
        if (initialLoad) {
            loading = true
            error = null
            api = null
            isAdmin = false
            menuExpanded = false
            sortMenuExpanded = false
            scanRunning = false
        } else {
            refreshing = true
        }
        try {
            session = sessionStore.load()
            val client = KavitaClient(ctx, sessionStore)
            val (loadedApi, _) = client.buildApi()
            api = loadedApi
            isAdmin = runCatching {
                loadedApi.currentUser().roles.orEmpty().any { it.equals("Admin", ignoreCase = true) }
            }.onFailure {
                BunkoLog.w("Could not load current user roles on Library series screen.", it)
            }.getOrDefault(false)
            series = loadedApi.loadAllSeriesForLibrary(libraryId)
            error = null
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            BunkoLog.w("Could not load library $libraryId series.", t)
            val message = t.message ?: t.toString()
            if (initialLoad || series.isEmpty()) {
                error = message
            } else {
                showMessage("Could not refresh library")
            }
        } finally {
            if (initialLoad) {
                loading = false
            } else {
                refreshing = false
            }
        }
    }

    LaunchedEffect(libraryId) {
        loadLibrarySeries(initialLoad = true)
    }

    val activeSort = externalSort ?: sort
    val normalizedQuery = remember(query) { normalizeSeriesSearchQuery(query) }
    val visibleSeries = remember(series, normalizedQuery, activeSort) {
        val filtered = if (normalizedQuery.isBlank()) {
            series
        } else {
            series.filter { it.matchesSeriesTitle(normalizedQuery) }
        }
        filtered.sortedForLibrary(activeSort)
    }

    LaunchedEffect(searchActive) {
        if (searchActive) {
            searchFocusRequester.requestFocus()
            keyboard?.show()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .then(if (statusBarPadding) Modifier.statusBarsPadding() else Modifier)
            .then(if (navigationBarPadding) Modifier.navigationBarsPadding() else Modifier),
        containerColor = BunkoBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    },
                title = {
                    if (searchActive) {
                        LibrarySearchField(
                            query = query,
                            onQueryChange = { query = it },
                            placeholder = "Search titles",
                            focusRequester = searchFocusRequester,
                            onClose = {
                                if (query.isBlank()) {
                                    searchActive = false
                                } else {
                                    query = ""
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(
                            text = libraryName,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    if (!searchActive) {
                        IconButton(onClick = { searchActive = true }) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = "Search titles",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Box {
                            IconButton(onClick = { sortMenuExpanded = true }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                                    contentDescription = "Sort series",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            DropdownMenuPopup(
                                expanded = sortMenuExpanded,
                                onDismissRequest = { sortMenuExpanded = false },
                                modifier = Modifier.width(220.dp)
                            ) {
                                DropdownMenuGroup(
                                    shapes = MenuDefaults.groupShape(index = 0, count = 1)
                                ) {
                                    val sortOptions = SeriesLibrarySort.entries
                                    sortOptions.forEachIndexed { index, option ->
                                        val selected = option == sort
                                        val itemShape = MenuDefaults.itemShape(index, sortOptions.size).shape
                                        DropdownMenuItem(
                                            text = { Text(option.label) },
                                            onClick = {
                                                sort = option
                                                sortMenuExpanded = false
                                            },
                                            trailingIcon = {
                                                if (selected) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Check,
                                                        contentDescription = null
                                                    )
                                                }
                                            },
                                            shape = itemShape,
                                            modifier = if (selected) {
                                                Modifier.background(
                                                    MaterialTheme.colorScheme.secondaryContainer,
                                                    itemShape
                                                )
                                            } else {
                                                Modifier
                                            },
                                            colors = MenuDefaults.itemColors(
                                                textColor = if (selected) {
                                                    MaterialTheme.colorScheme.onSecondaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                },
                                                trailingIconColor = if (selected) {
                                                    MaterialTheme.colorScheme.onSecondaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (isAdmin && api != null) {
                        Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                                enabled = !scanRunning
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = "Library actions",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            DropdownMenuPopup(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                                modifier = Modifier.width(220.dp)
                            ) {
                                DropdownMenuGroup(
                                    shapes = MenuDefaults.groupShape(index = 0, count = 1)
                                ) {
                                    DropdownMenuItem(
                                        shape = MenuDefaults.itemShape(index = 0, count = 1).shape,
                                        text = { Text("Scan Library") },
                                        onClick = {
                                            menuExpanded = false
                                            val loadedApi = api ?: return@DropdownMenuItem
                                            if (scanRunning) return@DropdownMenuItem
                                            scanRunning = true
                                            scope.launch {
                                                try {
                                                    loadedApi.scanLibrary(libraryId)
                                                    showMessage("Library scan requested")
                                                } catch (c: CancellationException) {
                                                    throw c
                                                } catch (t: Throwable) {
                                                    BunkoLog.w("Could not scan library $libraryId from Series screen.", t)
                                                    showMessage("Could not scan library")
                                                } finally {
                                                    scanRunning = false
                                                }
                                            }
                                        },
                                        enabled = !scanRunning
                                    )
                                }
                            }
                        }
                    }
                }
            )
        }
    }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (showTopBar) innerPadding else PaddingValues(0.dp))
                .background(BunkoBackground)
        ) {
            when {
                loading -> DarkLoadingState()
                else -> PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = {
                        if (!refreshing) {
                            scope.launch { loadLibrarySeries(initialLoad = false) }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    state = pullRefreshState,
                    indicator = { BunkoPullToRefreshIndicator(pullRefreshState, refreshing) }
                ) {
                    when {
                        error != null -> DarkMessageState(
                            title = "Could not load series",
                            body = error ?: "Unknown error",
                            actionLabel = "Retry",
                            onAction = { scope.launch { loadLibrarySeries(initialLoad = true) } }
                        )
                        series.isEmpty() -> DarkMessageState(
                            "No series",
                            "This library did not return any visible series."
                        )
                        isGridView -> SeriesLibraryGrid(
                            series = visibleSeries,
                            session = session,
                            query = normalizedQuery,
                            gridState = gridState,
                            onSelect = onSelect,
                            onSearchHome = onSearchHome
                        )
                        else -> SeriesLibraryList(
                            series = visibleSeries,
                            session = session,
                            query = normalizedQuery,
                            listState = listState,
                            onSelect = onSelect,
                            onSearchHome = onSearchHome
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibrarySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    focusRequester: FocusRequester,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = BunkoSurface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier.height(48.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Filled.Search, contentDescription = null)
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isBlank()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                }
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Clear search")
            }
        }
    }
}

@Composable
private fun SeriesLibraryGrid(
    series: List<SeriesDto>,
    session: KavitaSession,
    query: String,
    gridState: LazyGridState,
    onSelect: (SeriesDto) -> Unit,
    onSearchHome: (String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 130.dp),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (series.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                DarkMessageState(
                    title = "No local matches",
                    body = "This library does not contain a title matching \"$query\"."
                )
            }
        }
        gridItems(items = series, key = { it.id }) { item ->
            SeriesPosterCard(
                series = item,
                session = session,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(item) }
            )
        }
        if (query.isNotBlank()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                HomeSearchLink(query = query, onSearchHome = onSearchHome)
            }
        }
    }
}

@Composable
private fun HomeSearchLink(query: String, onSearchHome: (String) -> Unit) {
    Surface(
        color = BunkoSurface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSearchHome(query) }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Filled.Search, contentDescription = null)
            Text(
                text = "Global Search \"$query\"",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null
            )
        }
    }
}

@Composable
private fun SeriesLibraryList(
    series: List<SeriesDto>,
    session: KavitaSession,
    query: String,
    listState: LazyListState,
    onSelect: (SeriesDto) -> Unit,
    onSearchHome: (String) -> Unit
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (series.isEmpty()) {
            item {
                DarkMessageState(
                    title = "No local matches",
                    body = "This library does not contain a title matching \"$query\"."
                )
            }
        }
        items(items = series, key = { it.id }) { item ->
            SeriesListItem(
                series = item,
                session = session,
                onClick = { onSelect(item) }
            )
        }
        if (query.isNotBlank()) {
            item {
                HomeSearchLink(query = query, onSearchHome = onSearchHome)
            }
        }
    }
}
