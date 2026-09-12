package com.bunko.reader.library

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.bunko.reader.offline.LocalBook
import com.bunko.reader.offline.LocalBookRepository

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalWideNavigationRail
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.WideNavigationRailDefaults
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.bunko.reader.ChapterDto
import com.bunko.reader.GroupedSeriesDto
import com.bunko.reader.KavitaApi
import com.bunko.reader.KavitaClient
import com.bunko.reader.BunkoLog
import com.bunko.reader.KavitaSession
import com.bunko.reader.KavitaSessionStore
import com.bunko.reader.LibraryDto
import com.bunko.reader.SeriesFilterStatementDto
import com.bunko.reader.SeriesFilterV2Dto
import com.bunko.reader.SeriesDto
import com.bunko.reader.UpdateWantToReadDto
import com.bunko.reader.VolumeDto
import com.bunko.reader.MarkChapterReadDto
import com.bunko.reader.MarkVolumesReadDto
import com.bunko.reader.SearchHistoryStore
import com.bunko.reader.download.OfflineIssueRecord
import com.bunko.reader.download.OfflineIssueRepository
import com.bunko.reader.download.localCoverFile
import com.bunko.reader.normalizeKavitaBaseUrl
import com.bunko.reader.series.IssueDetailSideSheet
import com.bunko.reader.series.chapterCoverUrl
import com.bunko.reader.series.internal.coverActionColor
import com.bunko.reader.ui.DarkLoadingState
import com.bunko.reader.ui.DarkMessageState
import com.bunko.reader.ui.KavitaCoverAspectRatio
import com.bunko.reader.ui.browse.BrowsePageScaffold
import com.bunko.reader.ui.browse.PosterGrid
import com.bunko.reader.ui.browse.SeriesPosterCard
import com.bunko.reader.update.AvailableUpdate
import com.bunko.reader.library.internal.HomeShell
import com.bunko.reader.library.internal.loadLibrarySeriesCounts

private val PaginationHeaderJson = Json { ignoreUnknownKeys = true }

enum class HomeShelfKind(
    val routeValue: String,
    val title: String,
    val emptyMessage: String
) {
    OnDeck("on-deck", "Continue Reading", "No series in progress"),
    RecentlyUpdated("recently-updated", "Recently Updated Series", "No recently updated series"),
    NewlyAdded("newly-added", "Newly Added Series", "No newly added series");

    companion object {
        fun fromRouteValue(value: String?): HomeShelfKind? {
            return entries.firstOrNull { it.routeValue == value }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    sessionStore: KavitaSessionStore,
    sessionRevision: Int,
    localRepository: LocalBookRepository,
    initialIsOffline: Boolean = false,
    initialSearchQuery: String = "",
    availableUpdate: AvailableUpdate? = null,
    onOpenUpdate: (String) -> Unit = {},
    onUpdateNoticeShown: () -> Unit = {},
    onOpenSettings: () -> Unit,
    onOpenShelf: (HomeShelfKind) -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenCollections: () -> Unit,
    onOpenDownloaded: () -> Unit,
    onOpenFilteredSeries: (SearchSeriesTarget, Int, String) -> Unit,
    onSelectLibrary: (LibraryDto) -> Unit,
    onSelectSeries: (SeriesDto) -> Unit,
    onOpenOfflineBook: (LocalBook) -> Unit = {},
    onRequireLogin: () -> Unit = {},
    onSwitchToOffline: (() -> Unit)? = null
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val offlineRepository = remember(ctx) { OfflineIssueRepository(ctx) }
    val searchHistoryStore = remember(ctx) { SearchHistoryStore(ctx) }
    val snackbarHostState = remember { SnackbarHostState() }

    var isOffline by rememberSaveable(initialIsOffline) { mutableStateOf(initialIsOffline) }
    val folderInfo by localRepository.folderFlow.collectAsState(initial = Pair(null, null))
    val offlineBooks by localRepository.booksFlow.collectAsState(initial = emptyList())
    val isOfflineScanning by localRepository.isScanning.collectAsState()

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                ctx.contentResolver.takePersistableUriPermission(uri, takeFlags)
            }
            val displayName = DocumentsContract.getTreeDocumentId(uri).substringAfterLast('/')
                .ifBlank { "eBooks & Comics" }
            scope.launch {
                localRepository.setDefaultFolder(uri, displayName)
            }
        }
    }

    fun rescanOffline() {
        localRepository.rescan()
    }

    fun toggleLibraryMode() {
        if (isOffline) {
            scope.launch {
                val session = sessionStore.load()
                if (session.baseUrl.isNotBlank() && (session.jwt.isNotBlank() || session.apiKey.isNotBlank())) {
                    localRepository.setActiveMode("kavita")
                    isOffline = false
                } else {
                    onRequireLogin()
                }
            }
        } else {
            scope.launch {
                localRepository.setActiveMode("offline")
                isOffline = true
            }
        }
    }

    fun showMessage(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }
    val wantToReadPagingMutex = remember { Mutex() }

    LaunchedEffect(availableUpdate) {
        val update = availableUpdate ?: return@LaunchedEffect
        onUpdateNoticeShown()
        val result = snackbarHostState.showSnackbar(
            message = "Bunko ${update.tagName} is available",
            actionLabel = "View release",
            withDismissAction = true,
            duration = SnackbarDuration.Indefinite
        )
        if (result == SnackbarResult.ActionPerformed) {
            onOpenUpdate(update.releaseUrl)
        }
    }

    var libs by remember { mutableStateOf<List<LibraryDto>>(emptyList()) }
    var librarySeriesCounts by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    var onDeck by remember { mutableStateOf<List<SeriesDto>>(emptyList()) }
    var recentlyUpdated by remember { mutableStateOf<List<SeriesDto>>(emptyList()) }
    var newlyAdded by remember { mutableStateOf<List<SeriesDto>>(emptyList()) }
    var wantToRead by remember { mutableStateOf<List<SeriesDto>>(emptyList()) }
    var wantToReadError by remember { mutableStateOf<String?>(null) }
    var wantToReadNextPage by remember { mutableIntStateOf(0) }
    var wantToReadHasMore by remember { mutableStateOf(false) }
    var wantToReadLoadingMore by remember { mutableStateOf(false) }
    var wantToReadLoadMoreError by remember { mutableStateOf<String?>(null) }
    var wantToReadPagingRevision by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var refreshJob by remember { mutableStateOf<Job?>(null) }
    var serverName by remember { mutableStateOf("No server selected") }
    var session by remember { mutableStateOf(KavitaSession()) }
    var api by remember { mutableStateOf<KavitaApi?>(null) }
    var isAdmin by remember { mutableStateOf(false) }
    var scanningLibraryIds by remember { mutableStateOf<Set<Int>>(emptySet()) }

    val downloadedFlow = remember(session.baseUrl, session.username, session.apiKey) {
        offlineRepository.observeDownloaded(session)
    }
    val downloaded by downloadedFlow.collectAsState(initial = emptyList())

    suspend fun loadHome(clearFirst: Boolean) = coroutineScope {
        wantToReadPagingRevision++
        if (clearFirst) {
            libs = emptyList()
            librarySeriesCounts = emptyMap()
            onDeck = emptyList()
            recentlyUpdated = emptyList()
            newlyAdded = emptyList()
            wantToRead = emptyList()
            wantToReadError = null
            wantToReadNextPage = 0
            wantToReadHasMore = false
            wantToReadLoadMoreError = null
            loading = true
            api = null
            isAdmin = false
            scanningLibraryIds = emptySet()
        }
        error = null
        try {
            serverName = sessionStore.activeProfile()?.name ?: "No server selected"
            session = sessionStore.load()
            runCatchingCancellable { offlineRepository.ensureLocalCovers(session) }
                .onFailure { BunkoLog.w("Could not ensure local covers on Home.", it) }
            val client = KavitaClient(ctx, sessionStore)
            val (loadedApi, _) = client.buildApi()
            api = loadedApi
            val loadedLibraries = loadedApi.userLibraries().sortedBy { it.id }
            libs = loadedLibraries
            // Structured children so a server switch (which restarts the loader) cancels
            // these instead of letting a stale result overwrite the new server's state.
            launch {
                isAdmin = runCatchingCancellable {
                    loadedApi.currentUser().roles.orEmpty()
                        .any { it.equals("Admin", ignoreCase = true) }
                }.onFailure {
                    BunkoLog.w("Could not load current user roles on Home.", it)
                }.getOrDefault(false)
            }
            launch {
                runCatchingCancellable { loadLibrarySeriesCounts(loadedApi, loadedLibraries) }
                    .onSuccess { librarySeriesCounts = it }
                    .onFailure { BunkoLog.w("Could not load library series counts on Home.", it) }
            }
            onDeck = loadedApi.onDeck(pageSize = HomePreviewShelfPageSize)
            recentlyUpdated = loadedApi.recentlyUpdatedSeries(pageSize = HomePreviewShelfPageSize)
                .map { it.toSeriesDto() }
                .distinctBy { it.id }
            newlyAdded = loadedApi.recentlyAdded(pageSize = HomePreviewShelfPageSize)
            runCatchingCancellable {
                loadedApi.wantToRead(pageNumber = 0, pageSize = WantToReadPageSize)
            }.onSuccess {
                wantToRead = it
                wantToReadNextPage = 1
                wantToReadHasMore = it.size == WantToReadPageSize
            }
                .onFailure {
                    BunkoLog.w("Could not load Want to Read list.", it)
                    wantToReadError = it.message ?: it.toString()
                }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            BunkoLog.w("Could not load Home.", t)
            // On a pull-to-refresh keep the current content instead of replacing it with
            // the full-screen error state; only the initial load surfaces the error.
            if (clearFirst) error = t.message ?: t.toString()
        } finally {
            loading = false
        }
    }

    LaunchedEffect(sessionRevision, isOffline) {
        if (!isOffline) {
            loadHome(clearFirst = true)
        }
    }

    // A refresh runs on the composition scope, so cancel any in-flight one when the active
    // server changes; otherwise a late refresh from the previous server could overwrite the
    // new server's Home state.
    DisposableEffect(sessionRevision) {
        onDispose { refreshJob?.cancel() }
    }

    fun refreshHome() {
        if (isOffline) {
            rescanOffline()
            return
        }
        if (refreshing) return
        val retryInitialLoad = error != null
        refreshJob = scope.launch {
            refreshing = true
            try {
                loadHome(clearFirst = retryInitialLoad)
            } finally {
                refreshing = false
            }
        }
    }

    suspend fun loadNextWantToReadPage(): Result<List<SeriesDto>> = wantToReadPagingMutex.withLock {
        val currentApi = api
            ?: return@withLock Result.failure(IllegalStateException("API unavailable"))
        if (!wantToReadHasMore) return@withLock Result.success(wantToRead)
        val requestRevision = wantToReadPagingRevision
        wantToReadLoadingMore = true
        wantToReadLoadMoreError = null
        try {
            val page = currentApi.wantToRead(
                pageNumber = wantToReadNextPage,
                pageSize = WantToReadPageSize
            )
            if (requestRevision == wantToReadPagingRevision) {
                wantToRead = wantToRead.appendDistinct(page)
                wantToReadNextPage++
                wantToReadHasMore = page.size == WantToReadPageSize
            }
            Result.success(wantToRead)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            if (requestRevision == wantToReadPagingRevision) {
                BunkoLog.w("Could not load more Want to Read items.", t)
                wantToReadLoadMoreError = t.message ?: t.toString()
            }
            Result.failure(t)
        } finally {
            wantToReadLoadingMore = false
        }
    }

    suspend fun loadAllWantToRead(): Result<List<SeriesDto>> {
        while (wantToReadHasMore) {
            val result = loadNextWantToReadPage()
            if (result.isFailure) return result
        }
        return Result.success(wantToRead)
    }

    fun removeFromWantToRead(series: List<SeriesDto>) {
        val currentApi = api ?: return
        val requestedIds = series.mapTo(mutableSetOf()) { it.id }
        val removedItems = wantToRead.withIndex().filter { it.value.id in requestedIds }
        if (removedItems.isEmpty()) return
        val removedIds = removedItems.map { it.value.id }
        scope.launch {
            runCatchingCancellable {
                currentApi.removeSeriesFromWantToRead(UpdateWantToReadDto(removedIds))
            }.onSuccess {
                wantToRead = wantToRead.filterNot { it.id in requestedIds }
                val result = snackbarHostState.showSnackbar(
                    message = if (removedItems.size == 1) {
                        "Removed from Want to Read"
                    } else {
                        "Removed ${removedItems.size} items from Want to Read"
                    },
                    actionLabel = "Undo",
                    withDismissAction = true,
                    duration = SnackbarDuration.Long
                )
                if (result == SnackbarResult.ActionPerformed) {
                    runCatchingCancellable {
                        currentApi.addSeriesToWantToRead(UpdateWantToReadDto(removedIds))
                    }.onSuccess {
                        val restored = wantToRead.toMutableList()
                        removedItems.forEach { indexed ->
                            if (restored.none { it.id == indexed.value.id }) {
                                restored.add(indexed.index.coerceIn(0, restored.size), indexed.value)
                            }
                        }
                        wantToRead = restored.distinctBy { it.id }
                    }.onFailure {
                        BunkoLog.w("Could not restore series to Want to Read.", it)
                        snackbarHostState.showSnackbar("Could not restore Want to Read items")
                    }
                }
            }.onFailure {
                BunkoLog.w("Could not remove series from Want to Read.", it)
                snackbarHostState.showSnackbar("Could not update Want to Read")
            }
        }
    }

    fun scanLibrary(library: LibraryDto) {
        val currentApi = api ?: return
        if (library.id in scanningLibraryIds) return
        scanningLibraryIds = scanningLibraryIds + library.id
        scope.launch {
            try {
                currentApi.scanLibrary(library.id)
                showMessage("${library.name} scan requested")
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                BunkoLog.w("Could not scan library ${library.id}.", t)
                showMessage("Could not scan ${library.name}")
            } finally {
                scanningLibraryIds = scanningLibraryIds - library.id
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        HomeShell(
            libraries = libs,
            librarySeriesCounts = librarySeriesCounts,
            isAdmin = isAdmin,
            scanningLibraryIds = scanningLibraryIds,
            serverName = serverName,
            loading = loading,
            refreshing = refreshing,
            onRefresh = ::refreshHome,
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
            onOpenSettings = onOpenSettings,
            onOpenShelf = onOpenShelf,
            onOpenBookmarks = onOpenBookmarks,
            onOpenCollections = onOpenCollections,
            onOpenDownloaded = onOpenDownloaded,
            onOpenFilteredSeries = onOpenFilteredSeries,
            onSelectLibrary = onSelectLibrary,
            onScanLibrary = ::scanLibrary,
            onSelectSeries = onSelectSeries,
            onRemoveWantToRead = ::removeFromWantToRead,
            onLoadMoreWantToRead = { scope.launch { loadNextWantToReadPage() } },
            onLoadAllWantToRead = ::loadAllWantToRead,
            onSwitchToOffline = onSwitchToOffline,
            isOffline = isOffline,
            offlineBooks = offlineBooks,
            offlineFolderName = folderInfo.second,
            isOfflineScanning = isOfflineScanning,
            onOpenOfflineBook = onOpenOfflineBook,
            onChangeOfflineFolder = { folderLauncher.launch(null) },
            onRescanOffline = ::rescanOffline,
            onToggleLibraryMode = ::toggleLibraryMode
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
        )
    }
}

/**
 * Like [runCatching] but never swallows a [CancellationException], so a cancelled coroutine
 * keeps unwinding instead of being logged and treated as a normal failure.
 */
private inline fun <T> runCatchingCancellable(block: () -> T): Result<T> {
    return try {
        Result.success(block())
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        Result.failure(t)
    }
}

private const val HomePreviewShelfPageSize = 20
private const val WantToReadPageSize = 200

private fun GroupedSeriesDto.toSeriesDto(): SeriesDto {
    return SeriesDto(
        id = seriesId,
        name = seriesName ?: "Series $seriesId",
        libraryId = libraryId
    )
}
