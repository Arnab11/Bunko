package com.bunko.reader.series

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlin.math.roundToInt
import com.bunko.reader.ChapterDto
import com.bunko.reader.KavitaApi
import com.bunko.reader.ActiveServerRuntime
import com.bunko.reader.BunkoLog
import com.bunko.reader.KavitaSession
import com.bunko.reader.KavitaSessionStore
import com.bunko.reader.KomgaBookDto
import com.bunko.reader.KomgaBookRoute
import com.bunko.reader.KomgaIdMapper
import com.bunko.reader.KomgaKavitaAdapter
import com.bunko.reader.KomgaSessionStore
import com.bunko.reader.MarkChapterReadDto
import com.bunko.reader.MarkVolumesReadDto
import com.bunko.reader.normalizeKavitaBaseUrl
import com.bunko.reader.normalizeKomgaBaseUrl
import com.bunko.reader.serverBackend
import com.bunko.reader.series.internal.displayTitle
import com.bunko.reader.ui.KavitaCoverAspectRatio
import com.bunko.reader.ui.hasRemoteCovers
import com.bunko.reader.ui.seriesInitial
import com.bunko.reader.SeriesDto
import com.bunko.reader.ui.DarkLoadingState
import com.bunko.reader.ui.DarkMessageState
import com.bunko.reader.ui.BunkoPullToRefreshIndicator
import com.bunko.reader.ui.browse.SeriesListItem
import com.bunko.reader.ui.browse.SeriesPosterCard
import com.bunko.reader.ui.theme.BunkoBackground
import com.bunko.reader.ui.theme.BunkoChrome
import com.bunko.reader.ui.theme.BunkoSurface
import com.bunko.reader.ui.theme.themeToggleModifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal fun chapterCoverUrl(session: KavitaSession, chapterId: Int): String {
    // Komga mode: the shared UI passes Int ids mapped from Komga book ids.
    if (ActiveServerRuntime.mode == "komga" && ActiveServerRuntime.komgaBaseUrl.isNotBlank()) {
        val komgaId = KomgaIdMapper.komgaBookId(chapterId)
        if (komgaId != null) {
            return "${normalizeKomgaBaseUrl(ActiveServerRuntime.komgaBaseUrl)}/api/v1/books/$komgaId/thumbnail"
        }
    }
    val root = normalizeKavitaBaseUrl(session.baseUrl)
    val apiKey = session.apiKey.takeIf { it.isNotBlank() }?.let { "&apiKey=${Uri.encode(it)}" }.orEmpty()
    return "$root/api/Image/chapter-cover?chapterId=$chapterId$apiKey"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SeriesScreen(
    sessionStore: KavitaSessionStore,
    komgaSessionStore: KomgaSessionStore,
    libraryId: Int,
    libraryName: String,
    onBack: (() -> Unit)? = null,
    onSearchHome: (String) -> Unit = {},
    statusBarPadding: Boolean = true,
    navigationBarPadding: Boolean = true,
    showTopBar: Boolean = statusBarPadding,
    externalSort: SeriesLibrarySort? = null,
    externalSortDescending: Boolean = false,
    isGridView: Boolean = true,
    gridCoverSize: Int = 130,
    listCoverSize: Int = 80,
    onSelect: (SeriesDto) -> Unit,
    onOpenBook: ((libraryId: Int, seriesId: Int, volumeId: Int, chapterId: Int) -> Unit)? = null
) {
    val ctx = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val searchFocusRequester = remember { FocusRequester() }

    var series by remember { mutableStateOf<List<SeriesDto>>(emptyList()) }
    var komgaBooks by remember { mutableStateOf<List<KomgaLibraryBook>?>(null) }
    var komgaAdapter by remember { mutableStateOf<KomgaKavitaAdapter?>(null) }
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
            val backend = ctx.serverBackend(sessionStore, komgaSessionStore)
            val loadedApi = backend.api
            api = loadedApi
            if (backend.isKomga && loadedApi is KomgaKavitaAdapter) {
                // Komga libraries list individual books (tap = read).
                komgaAdapter = loadedApi
                komgaBooks = loadedApi.booksForLibrary(libraryId)
                    .sortedWith(compareBy({ it.seriesTitle.lowercase() }, { it.number }))
                    .map { book ->
                        KomgaLibraryBook(
                            book = book,
                            chapter = loadedApi.bookToChapter(book),
                            route = loadedApi.routeForBook(book)
                        )
                    }
                series = emptyList()
                isAdmin = false
            } else {
                komgaAdapter = null
                komgaBooks = null
                isAdmin = runCatching {
                    loadedApi.currentUser().roles.orEmpty().any { it.equals("Admin", ignoreCase = true) }
                }.onFailure {
                    BunkoLog.w("Could not load current user roles on Library series screen.", it)
                }.getOrDefault(false)
                series = loadedApi.loadAllSeriesForLibrary(libraryId)
            }
            error = null
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            BunkoLog.w("Could not load library $libraryId series.", t)
            val message = t.message ?: t.toString()
            if (initialLoad || (series.isEmpty() && komgaBooks.isNullOrEmpty())) {
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
    val visibleSeries = remember(series, normalizedQuery, activeSort, externalSortDescending) {
        val filtered = if (normalizedQuery.isBlank()) {
            series
        } else {
            series.filter { it.matchesSeriesTitle(normalizedQuery) }
        }
        filtered.sortedForLibrary(activeSort, externalSortDescending)
    }

    val booksSnapshot = komgaBooks
    val visibleBooks = remember(booksSnapshot, normalizedQuery, activeSort, externalSortDescending) {
        val list = booksSnapshot ?: emptyList()
        val filtered = if (normalizedQuery.isBlank()) {
            list
        } else {
            list.filter {
                it.chapter.displayTitle().contains(normalizedQuery, ignoreCase = true) ||
                    it.book.seriesTitle.contains(normalizedQuery, ignoreCase = true)
            }
        }
        val sorted = when (activeSort) {
            SeriesLibrarySort.Title -> filtered.sortedBy { it.chapter.displayTitle().lowercase() }
            SeriesLibrarySort.InProgressFirst -> filtered.sortedWith(
                compareByDescending<KomgaLibraryBook> { it.isInProgress() }
                    .thenBy { it.chapter.displayTitle().lowercase() }
            )
            SeriesLibrarySort.ReadFirst -> filtered.sortedWith(
                compareByDescending<KomgaLibraryBook> { it.isRead() }
                    .thenBy { it.chapter.displayTitle().lowercase() }
            )
            SeriesLibrarySort.UnreadFirst -> filtered.sortedWith(
                compareByDescending<KomgaLibraryBook> { it.isUnread() }
                    .thenBy { it.chapter.displayTitle().lowercase() }
            )
        }
        if (externalSortDescending) sorted.reversed() else sorted
    }

    fun openKomgaBook(entry: KomgaLibraryBook) {
        val openBook = onOpenBook
        if (openBook != null) {
            openBook(
                entry.route.libraryId,
                entry.route.seriesId,
                entry.route.volumeId,
                entry.route.chapterId
            )
        } else {
            onSelect(
                SeriesDto(
                    id = entry.route.seriesId,
                    name = entry.book.seriesTitle,
                    libraryId = entry.route.libraryId
                )
            )
        }
    }

    fun updateKomgaBookProgress(entry: KomgaLibraryBook, pagesRead: Int) {
        komgaBooks = komgaBooks?.map { existing ->
            if (existing.route.chapterId == entry.route.chapterId) {
                existing.copy(chapter = existing.chapter.copy(pagesRead = pagesRead))
            } else {
                existing
            }
        }
    }

    fun toggleKomgaBookRead(entry: KomgaLibraryBook) {
        scope.launch {
            try {
                val adapter = komgaAdapter ?: return@launch
                if (entry.isRead()) {
                    adapter.markChaptersUnread(
                        MarkVolumesReadDto(
                            seriesId = entry.route.seriesId,
                            chapterIds = listOf(entry.route.chapterId)
                        )
                    )
                    updateKomgaBookProgress(entry, pagesRead = 0)
                } else {
                    adapter.markChapterRead(
                        MarkChapterReadDto(
                            seriesId = entry.route.seriesId,
                            chapterId = entry.route.chapterId,
                            generateReadingSession = false
                        )
                    )
                    updateKomgaBookProgress(entry, pagesRead = entry.chapter.pages ?: 0)
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                BunkoLog.w("Could not toggle read state for Komga book ${entry.route.chapterId}.", t)
                showMessage("Could not update read state")
            }
        }
    }

    LaunchedEffect(searchActive) {
        if (searchActive) {
            searchFocusRequester.requestFocus()
            keyboard?.show()
        }
    }

    val layoutDirection = LocalLayoutDirection.current
    val cutoutInsets = WindowInsets.displayCutout.asPaddingValues()
    val startCutoutPadding = cutoutInsets.calculateStartPadding(layoutDirection)
    val endCutoutPadding = cutoutInsets.calculateEndPadding(layoutDirection)

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = startCutoutPadding, end = endCutoutPadding)
            .then(if (!showTopBar && statusBarPadding) Modifier.statusBarsPadding() else Modifier)
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
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.then(themeToggleModifier())
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
                            title = if (booksSnapshot != null) "Could not load books" else "Could not load series",
                            body = error ?: "Unknown error",
                            actionLabel = "Retry",
                            onAction = { scope.launch { loadLibrarySeries(initialLoad = true) } }
                        )
                        booksSnapshot != null -> {
                            if (visibleBooks.isEmpty()) {
                                DarkMessageState(
                                    "No books",
                                    if (normalizedQuery.isBlank()) {
                                        "This library did not return any visible books."
                                    } else {
                                        "This library does not contain a book matching \"$normalizedQuery\"."
                                    }
                                )
                            } else if (isGridView) {
                                KomgaBookGrid(
                                    books = visibleBooks,
                                    session = session,
                                    gridCoverSize = gridCoverSize,
                                    gridState = gridState,
                                    onRead = ::openKomgaBook,
                                    onToggleRead = ::toggleKomgaBookRead,
                                    onViewSeries = { entry ->
                                        onSelect(
                                            SeriesDto(
                                                id = entry.route.seriesId,
                                                name = entry.book.seriesTitle,
                                                libraryId = entry.route.libraryId
                                            )
                                        )
                                    },
                                    onSearchHome = onSearchHome,
                                    query = normalizedQuery
                                )
                            } else {
                                KomgaBookList(
                                    books = visibleBooks,
                                    session = session,
                                    listCoverSize = listCoverSize,
                                    listState = listState,
                                    onRead = ::openKomgaBook,
                                    onToggleRead = ::toggleKomgaBookRead,
                                    onViewSeries = { entry ->
                                        onSelect(
                                            SeriesDto(
                                                id = entry.route.seriesId,
                                                name = entry.book.seriesTitle,
                                                libraryId = entry.route.libraryId
                                            )
                                        )
                                    },
                                    onSearchHome = onSearchHome,
                                    query = normalizedQuery
                                )
                            }
                        }
                        series.isEmpty() -> DarkMessageState(
                            "No series",
                            "This library did not return any visible series."
                        )
                        isGridView -> SeriesLibraryGrid(
                            series = visibleSeries,
                            session = session,
                            query = normalizedQuery,
                            gridCoverSize = gridCoverSize,
                            gridState = gridState,
                            onSelect = onSelect,
                            onSearchHome = onSearchHome
                        )
                        else -> SeriesLibraryList(
                            series = visibleSeries,
                            session = session,
                            query = normalizedQuery,
                            listCoverSize = listCoverSize,
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
    gridCoverSize: Int = 130,
    gridState: LazyGridState,
    onSelect: (SeriesDto) -> Unit,
    onSearchHome: (String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = gridCoverSize.dp),
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
    listCoverSize: Int = 80,
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
                coverWidth = listCoverSize.dp,
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

/** A single Komga book in flat library-browsing mode (tap = read). */
internal data class KomgaLibraryBook(
    val book: KomgaBookDto,
    val chapter: ChapterDto,
    val route: KomgaBookRoute
)

internal fun KomgaLibraryBook.isUnread(): Boolean {
    val total = chapter.pages ?: 0
    return total <= 0 || (chapter.pagesRead ?: 0) <= 0
}

internal fun KomgaLibraryBook.isInProgress(): Boolean {
    val total = chapter.pages ?: 0
    val read = chapter.pagesRead ?: 0
    return total > 0 && read in 1 until total
}

internal fun KomgaLibraryBook.isRead(): Boolean {
    val total = chapter.pages ?: 0
    return total > 0 && (chapter.pagesRead ?: 0) >= total
}

@Composable
internal fun KomgaBookGrid(
    books: List<KomgaLibraryBook>,
    session: KavitaSession,
    gridState: LazyGridState,
    gridCoverSize: Int = 130,
    onRead: (KomgaLibraryBook) -> Unit,
    onToggleRead: (KomgaLibraryBook) -> Unit,
    onViewSeries: (KomgaLibraryBook) -> Unit,
    onSearchHome: (String) -> Unit,
    query: String
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = gridCoverSize.dp),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        gridItems(items = books, key = { it.route.chapterId }) { item ->
            KomgaBookGridCard(
                entry = item,
                session = session,
                onRead = { onRead(item) },
                onToggleRead = { onToggleRead(item) },
                onViewSeries = { onViewSeries(item) }
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
internal fun KomgaBookList(
    books: List<KomgaLibraryBook>,
    session: KavitaSession,
    listState: LazyListState,
    listCoverSize: Int = 80,
    onRead: (KomgaLibraryBook) -> Unit,
    onToggleRead: (KomgaLibraryBook) -> Unit,
    onViewSeries: (KomgaLibraryBook) -> Unit,
    onSearchHome: (String) -> Unit,
    query: String
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(items = books, key = { it.route.chapterId }) { item ->
            KomgaBookListRow(
                entry = item,
                session = session,
                coverWidth = listCoverSize.dp,
                onRead = { onRead(item) },
                onToggleRead = { onToggleRead(item) },
                onViewSeries = { onViewSeries(item) }
            )
        }
        if (query.isNotBlank()) {
            item {
                HomeSearchLink(query = query, onSearchHome = onSearchHome)
            }
        }
    }
}

@Composable
internal fun KomgaBookGridCard(
    entry: KomgaLibraryBook,
    session: KavitaSession,
    onRead: () -> Unit,
    onToggleRead: () -> Unit,
    onViewSeries: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        modifier = modifier,
        shape = RectangleShape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(Modifier.clickable(onClick = onViewSeries)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(KavitaCoverAspectRatio)
                    .clip(RectangleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                contentAlignment = Alignment.Center
            ) {
                if (hasRemoteCovers(session)) {
                    val context = LocalContext.current
                    val request = remember(context, entry.route.chapterId) {
                        ImageRequest.Builder(context)
                            .data(chapterCoverUrl(session, entry.chapter.id))
                            .crossfade(180)
                            .build()
                    }
                    AsyncImage(
                        model = request,
                        contentDescription = entry.chapter.displayTitle(),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        seriesInitial(entry.chapter.displayTitle()),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            KomgaBookProgressBar(entry = entry)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.chapter.displayTitle(),
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = entry.book.seriesTitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "Book actions",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    KomgaBookMenu(
                        expanded = menuExpanded,
                        isRead = entry.isRead(),
                        onDismiss = { menuExpanded = false },
                        onRead = { menuExpanded = false; onRead() },
                        onToggleRead = { menuExpanded = false; onToggleRead() },
                        onViewSeries = { menuExpanded = false; onViewSeries() }
                    )
                }
            }
        }
    }
}

@Composable
internal fun KomgaBookListRow(
    entry: KomgaLibraryBook,
    session: KavitaSession,
    coverWidth: androidx.compose.ui.unit.Dp = 80.dp,
    onRead: () -> Unit,
    onToggleRead: () -> Unit,
    onViewSeries: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onViewSeries)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(coverWidth)
                    .aspectRatio(KavitaCoverAspectRatio)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                contentAlignment = Alignment.Center
            ) {
                if (hasRemoteCovers(session)) {
                    val context = LocalContext.current
                    val request = remember(context, entry.route.chapterId) {
                        ImageRequest.Builder(context)
                            .data(chapterCoverUrl(session, entry.chapter.id))
                            .crossfade(180)
                            .build()
                    }
                    AsyncImage(
                        model = request,
                        contentDescription = entry.chapter.displayTitle(),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        seriesInitial(entry.chapter.displayTitle()),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.chapter.displayTitle(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = entry.book.seriesTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // Same status line as the Kavita list view.
                val total = entry.chapter.pages ?: 0
                val read = entry.chapter.pagesRead ?: 0
                val statusText = when {
                    total > 0 && read >= total -> "Completed"
                    read > 0 -> {
                        val pct = if (total > 0) ((read.toFloat() / total.toFloat()) * 100f).roundToInt().coerceIn(1, 99) else null
                        if (pct != null) "In Progress ($pct%)" else "In Progress"
                    }
                    else -> "Unread"
                }
                val statusColor = when {
                    statusText == "Completed" -> Color(0xFF66BB6A)
                    statusText.startsWith("In Progress") -> Color(0xFF42A5F5)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                KomgaBookProgressBar(entry = entry, modifier = Modifier.padding(top = 6.dp))
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "Book actions",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                KomgaBookMenu(
                    expanded = menuExpanded,
                    isRead = entry.isRead(),
                    onDismiss = { menuExpanded = false },
                    onRead = { menuExpanded = false; onRead() },
                    onToggleRead = { menuExpanded = false; onToggleRead() },
                    onViewSeries = { menuExpanded = false; onViewSeries() }
                )
            }
        }
    }
}

@Composable
internal fun KomgaBookProgressBar(entry: KomgaLibraryBook, modifier: Modifier = Modifier) {
    val total = entry.chapter.pages ?: 0
    val read = entry.chapter.pagesRead ?: 0
    if (total > 0) {
        LinearProgressIndicator(
            progress = { (read.toFloat() / total.toFloat()).coerceIn(0f, 1f) },
            modifier = modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    }
}

@Composable
internal fun KomgaBookMenu(
    expanded: Boolean,
    isRead: Boolean,
    onDismiss: () -> Unit,
    onRead: () -> Unit,
    onToggleRead: () -> Unit,
    onViewSeries: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        DropdownMenuItem(
            text = { Text("Read") },
            onClick = onRead
        )
        DropdownMenuItem(
            text = { Text(if (isRead) "Mark as unread" else "Mark as read") },
            onClick = onToggleRead
        )
        DropdownMenuItem(
            text = { Text("Book details") },
            onClick = onViewSeries
        )
    }
}
