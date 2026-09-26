package com.bunko.reader.series

import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bunko.reader.ChapterDto
import com.bunko.reader.library.SearchSeriesTarget
import com.bunko.reader.KavitaApi
import com.bunko.reader.KavitaClient
import com.bunko.reader.BunkoLog
import com.bunko.reader.KavitaSession
import com.bunko.reader.KavitaSessionStore
import com.bunko.reader.MarkChapterReadDto
import com.bunko.reader.MarkVolumesReadDto
import com.bunko.reader.ReadingListDto
import com.bunko.reader.RefreshSeriesDto
import com.bunko.reader.normalizeKavitaBaseUrl
import com.bunko.reader.SeriesDto
import com.bunko.reader.SeriesFilterStatementDto
import com.bunko.reader.SeriesFilterV2Dto
import com.bunko.reader.SeriesMetadataDto
import com.bunko.reader.UpdateReadingListBySeriesDto
import com.bunko.reader.UpdateWantToReadDto
import com.bunko.reader.VolumeDto
import com.bunko.reader.download.OfflineDownloadStatus
import com.bunko.reader.download.OfflineIssueRepository
import com.bunko.reader.ui.DarkLoadingState
import com.bunko.reader.ui.DarkMessageState
import com.bunko.reader.ui.BunkoPullToRefreshIndicator
import com.bunko.reader.ui.KavitaCoverAspectRatio
import com.bunko.reader.ui.browse.BrowsePageScaffold
import com.bunko.reader.ui.browse.PosterGrid
import com.bunko.reader.ui.browse.SeriesPosterCard
import com.bunko.reader.ui.seriesCoverUrl
import com.bunko.reader.ui.seriesInitial
import com.bunko.reader.ui.theme.BunkoBackground
import com.bunko.reader.ui.theme.ReadingProgressInProgress
import com.bunko.reader.ui.theme.ReadingProgressRead
import com.bunko.reader.ui.theme.ReadingProgressTrack
import com.bunko.reader.series.internal.ChapterCardItem
import com.bunko.reader.series.internal.ChapterGridCard
import com.bunko.reader.series.internal.ChapterSectionHeader
import com.bunko.reader.series.internal.ChapterIssueGrid
import com.bunko.reader.series.internal.SeriesDetailSummary
import com.bunko.reader.series.internal.coverActionColor
import com.bunko.reader.series.internal.detailMetaLines
import com.bunko.reader.series.internal.displayName
import com.bunko.reader.series.internal.displayShortName
import com.bunko.reader.series.internal.displayTitle
import com.bunko.reader.series.internal.primaryReadActionText
import com.bunko.reader.series.internal.readableAccentOn
import com.bunko.reader.series.internal.readingProgress
import com.bunko.reader.series.internal.releaseDateText
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flowOf
import kotlin.math.roundToInt

import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.VerticalDivider
import com.bunko.reader.library.internal.HomeBottomNavigation
import com.bunko.reader.library.internal.HomeNavigationRail
import com.bunko.reader.library.internal.HomeDestination
import com.bunko.reader.NavigationBarStyle
import com.bunko.reader.ui.theme.themeToggleModifier

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChapterPickScreen(
    sessionStore: KavitaSessionStore,
    libraryId: Int,
    seriesId: Int,
    seriesName: String,
    onOpenFilteredSeries: (SearchSeriesTarget, Int, String) -> Unit,
    onOpenSettings: () -> Unit = {},
    currentDestination: HomeDestination = HomeDestination.Libraries,
    onSelectDestination: (HomeDestination) -> Unit = {},
    navigationBarStyle: NavigationBarStyle = NavigationBarStyle.Standard,
    onBack: () -> Unit = {},
    isDualPane: Boolean = false,
    onPick: (chapterId: Int, volumeId: Int, incognito: Boolean, initialPage: Int?) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var series by remember { mutableStateOf<SeriesDto?>(null) }
    var metadata by remember { mutableStateOf<SeriesMetadataDto?>(null) }
    var continueChapter by remember { mutableStateOf<ChapterDto?>(null) }
    var volumes by remember { mutableStateOf<List<VolumeDto>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var session by remember { mutableStateOf(KavitaSession()) }
    var api by remember { mutableStateOf<KavitaApi?>(null) }
    var isAdmin by remember { mutableStateOf(false) }
    var issueActionBusy by remember { mutableStateOf(false) }
    val offlineRepository = remember(ctx) { OfflineIssueRepository(ctx) }
    val pullRefreshState = rememberPullToRefreshState()
    val snackbarHostState = remember { SnackbarHostState() }

    fun showMessage(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    suspend fun loadSeriesDetails(initialLoad: Boolean) {
        if (!initialLoad && refreshing) return
        if (initialLoad) {
            loading = true
            error = null
            api = null
            isAdmin = false
        } else {
            refreshing = true
        }
        try {
            val loadedSession = sessionStore.load()
            session = loadedSession
            val client = KavitaClient(ctx, sessionStore)
            val (loadedApi, _) = client.buildApi()
            api = loadedApi
            runCatching { offlineRepository.syncPending(loadedSession, loadedApi) }
                .onFailure { BunkoLog.w("Could not sync pending offline progress from Series detail.", it) }
            isAdmin = runCatching {
                loadedApi.currentUser().roles.orEmpty().any { it.equals("Admin", ignoreCase = true) }
            }.onFailure {
                BunkoLog.w("Could not load current user roles on Series detail.", it)
            }.getOrDefault(false)
            series = loadedApi.series(seriesId)
            metadata = runCatching { loadedApi.seriesMetadata(seriesId) }
                .onFailure { BunkoLog.w("Could not load metadata for series $seriesId.", it) }
                .getOrNull()
            continueChapter = runCatching { loadedApi.continuePoint(seriesId) }
                .onFailure { BunkoLog.w("Could not load continue point for series $seriesId.", it) }
                .getOrNull()
            volumes = loadedApi.volumes(seriesId)
            error = null
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            BunkoLog.w("Could not load series details for series $seriesId.", t)
            val message = t.message ?: t.toString()
            if (initialLoad || series == null) {
                error = message
            } else {
                showMessage("Could not refresh series details")
            }
        } finally {
            if (initialLoad) {
                loading = false
            } else {
                refreshing = false
            }
        }
    }

    LaunchedEffect(seriesId) {
        loadSeriesDetails(initialLoad = true)
    }

    val chapterCards = volumes.flatMap { volume ->
        volume.chapters.map { chapter ->
            ChapterCardItem(volume = volume, chapter = chapter)
        }
    }.distinctBy { it.chapter.id }
    val displaySeries = series ?: SeriesDto(id = seriesId, name = seriesName, libraryId = libraryId)
    val loadedApi = api
    val downloadedList by offlineRepository.observeDownloaded(session).collectAsState(initial = emptyList())
    val downloadedChapterIds = remember(downloadedList) {
        downloadedList.filter { it.status == OfflineDownloadStatus.Ready }.mapTo(mutableSetOf()) { it.chapterId }
    }
    val downloadingChapterIds = remember(downloadedList) {
        downloadedList.filter { it.status in setOf(OfflineDownloadStatus.Queued, OfflineDownloadStatus.Downloading) }.mapTo(mutableSetOf()) { it.chapterId }
    }

    fun updateChapter(updated: ChapterDto) {
        volumes = volumes.map { volume ->
            if (volume.chapters.none { it.id == updated.id }) {
                volume
            } else {
                volume.copy(
                    chapters = volume.chapters.map { chapter ->
                        if (chapter.id == updated.id) updated else chapter
                    }
                )
            }
        }
    }

    fun markIssueRead(item: ChapterCardItem) {
        val currentApi = loadedApi ?: return
        if (issueActionBusy) return
        issueActionBusy = true
        scope.launch {
            try {
                currentApi.markChapterRead(
                    MarkChapterReadDto(
                        seriesId = seriesId,
                        chapterId = item.chapter.id,
                        generateReadingSession = false
                    )
                )
                runCatching {
                    val updatedSeries = currentApi.series(seriesId)
                    series = updatedSeries
                    val updatedVolumes = currentApi.volumes(seriesId)
                    volumes = updatedVolumes
                }
                showMessage("Marked as read")
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                BunkoLog.w("Could not mark issue ${item.chapter.id} as read.", t)
                showMessage("Could not mark issue as read")
            } finally {
                issueActionBusy = false
            }
        }
    }

    fun markIssueUnread(item: ChapterCardItem) {
        val currentApi = loadedApi ?: return
        if (issueActionBusy) return
        issueActionBusy = true
        scope.launch {
            try {
                currentApi.markChaptersUnread(
                    MarkVolumesReadDto(
                        seriesId = seriesId,
                        chapterIds = listOf(item.chapter.id)
                    )
                )
                runCatching {
                    val updatedSeries = currentApi.series(seriesId)
                    series = updatedSeries
                    val updatedVolumes = currentApi.volumes(seriesId)
                    volumes = updatedVolumes
                }
                showMessage("Marked as unread")
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                BunkoLog.w("Could not mark issue ${item.chapter.id} as unread.", t)
                showMessage("Could not mark issue as unread")
            } finally {
                issueActionBusy = false
            }
        }
    }

    fun downloadIssue(item: ChapterCardItem) {
        if (issueActionBusy) return
        issueActionBusy = true
        scope.launch {
            try {
                offlineRepository.enqueue(
                    session = session,
                    libraryId = libraryId,
                    seriesId = seriesId,
                    volumeId = item.volume.id,
                    chapterId = item.chapter.id,
                    seriesName = displaySeries.name,
                    issueName = item.volume.displayName() ?: item.chapter.displayTitle(),
                    expectedBytes = null,
                    expectedPageCount = item.chapter.pages
                )
                showMessage("Download queued")
            } catch (c: CancellationException) {
                throw c
            } catch (error: Throwable) {
                BunkoLog.w("Could not queue offline download for chapter ${item.chapter.id}.", error)
                showMessage(error.message ?: "Could not queue download")
            } finally {
                issueActionBusy = false
            }
        }
    }

    fun removeIssueDownload(item: ChapterCardItem) {
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Download removed",
                actionLabel = "Undo",
                withDismissAction = true,
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) return@launch
            try {
                offlineRepository.remove(session, item.chapter.id)
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                BunkoLog.w("Could not remove offline download for chapter ${item.chapter.id}.", t)
                snackbarHostState.showSnackbar("Could not remove download")
            }
        }
    }

    val topHeader: @Composable () -> Unit = {
        val statusInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout).asPaddingValues()
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, top = statusInsets.calculateTopPadding() + 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = displaySeries.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp)
                        .then(themeToggleModifier())
                )
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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(BunkoBackground)
    ) {
        val configuration = LocalConfiguration.current
        val isTablet = configuration.smallestScreenWidthDp >= 600 && maxWidth >= 720.dp
        val showRail = isTablet && navigationBarStyle == NavigationBarStyle.Standard
        val layoutDirection = LocalLayoutDirection.current
        val cutoutInsets = WindowInsets.displayCutout.asPaddingValues()
        val startCutoutPadding = cutoutInsets.calculateStartPadding(layoutDirection)
        val endCutoutPadding = cutoutInsets.calculateEndPadding(layoutDirection)
        val contentCutoutModifier = Modifier.padding(start = startCutoutPadding, end = endCutoutPadding)

        if (isDualPane) {
            Column(Modifier.fillMaxSize()) {
                val statusInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout).asPaddingValues()
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = 4.dp,
                                end = 4.dp,
                                top = statusInsets.calculateTopPadding() + 8.dp,
                                bottom = 8.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = displaySeries.name,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 4.dp)
                        )
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when {
                        loading -> DarkLoadingState()
                        error != null -> DarkMessageState(
                            title = "Could not load series details",
                            body = error ?: "Unknown error",
                            actionLabel = "Retry",
                            onAction = { scope.launch { loadSeriesDetails(initialLoad = true) } }
                        )
                        loadedApi == null -> DarkMessageState(
                            title = "Could not load series details",
                            body = "API unavailable",
                            actionLabel = "Retry",
                            onAction = { scope.launch { loadSeriesDetails(initialLoad = true) } }
                        )
                        else -> PullToRefreshBox(
                            isRefreshing = refreshing,
                            onRefresh = {
                                if (!refreshing) {
                                    scope.launch { loadSeriesDetails(initialLoad = false) }
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                            state = pullRefreshState,
                            indicator = { BunkoPullToRefreshIndicator(pullRefreshState, refreshing) }
                        ) {
                            SeriesDetailContent(
                                series = displaySeries,
                                metadata = metadata,
                                continueChapter = continueChapter,
                                chapterCards = chapterCards,
                                volumeCount = volumes.size,
                                session = session,
                                api = loadedApi,
                                isAdmin = isAdmin,
                                downloadedChapterIds = downloadedChapterIds,
                                downloadingChapterIds = downloadingChapterIds,
                                onOpenFilteredSeries = onOpenFilteredSeries,
                                onPick = { chapterId, volumeId, initialPage -> onPick(chapterId, volumeId, false, initialPage) },
                                onReadIncognito = { item -> onPick(item.chapter.id, item.volume.id, true, null) },
                                onMarkRead = ::markIssueRead,
                                onMarkUnread = ::markIssueUnread,
                                onDownload = ::downloadIssue,
                                onRemoveDownload = ::removeIssueDownload,
                                onRefreshSeries = { scope.launch { loadSeriesDetails(initialLoad = false) } },
                                onMessage = ::showMessage,
                                navigationBarStyle = navigationBarStyle
                            )
                        }
                    }

                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                    )
                }
            }
        } else if (showRail) {
            Row(Modifier.fillMaxSize().then(contentCutoutModifier)) {
                HomeNavigationRail(
                    selected = currentDestination,
                    onSelect = onSelectDestination
                )

                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    topHeader()

                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        when {
                            loading -> DarkLoadingState()
                            error != null -> DarkMessageState(
                                title = "Could not load series details",
                                body = error ?: "Unknown error",
                                actionLabel = "Retry",
                                onAction = { scope.launch { loadSeriesDetails(initialLoad = true) } }
                            )
                            loadedApi == null -> DarkMessageState(
                                title = "Could not load series details",
                                body = "API unavailable",
                                actionLabel = "Retry",
                                onAction = { scope.launch { loadSeriesDetails(initialLoad = true) } }
                            )
                            else -> PullToRefreshBox(
                                isRefreshing = refreshing,
                                onRefresh = {
                                    if (!refreshing) {
                                        scope.launch { loadSeriesDetails(initialLoad = false) }
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                                state = pullRefreshState,
                                indicator = { BunkoPullToRefreshIndicator(pullRefreshState, refreshing) }
                            ) {
                                SeriesDetailContent(
                                    series = displaySeries,
                                    metadata = metadata,
                                    continueChapter = continueChapter,
                                    chapterCards = chapterCards,
                                    volumeCount = volumes.size,
                                    session = session,
                                    api = loadedApi,
                                    isAdmin = isAdmin,
                                    downloadedChapterIds = downloadedChapterIds,
                                    downloadingChapterIds = downloadingChapterIds,
                                    onOpenFilteredSeries = onOpenFilteredSeries,
                                    onPick = { chapterId, volumeId, initialPage -> onPick(chapterId, volumeId, false, initialPage) },
                                    onReadIncognito = { item -> onPick(item.chapter.id, item.volume.id, true, null) },
                                    onMarkRead = ::markIssueRead,
                                    onMarkUnread = ::markIssueUnread,
                                    onDownload = ::downloadIssue,
                                    onRemoveDownload = ::removeIssueDownload,
                                    onRefreshSeries = { scope.launch { loadSeriesDetails(initialLoad = false) } },
                                    onMessage = ::showMessage,
                                    navigationBarStyle = navigationBarStyle
                                )
                            }
                        }

                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(16.dp)
                        )
                    }

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
                Column(Modifier.fillMaxSize()) {
                    topHeader()

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        when {
                            loading -> DarkLoadingState()
                            error != null -> DarkMessageState(
                                title = "Could not load series details",
                                body = error ?: "Unknown error",
                                actionLabel = "Retry",
                                onAction = { scope.launch { loadSeriesDetails(initialLoad = true) } }
                            )
                            loadedApi == null -> DarkMessageState(
                                title = "Could not load series details",
                                body = "API unavailable",
                                actionLabel = "Retry",
                                onAction = { scope.launch { loadSeriesDetails(initialLoad = true) } }
                            )
                            else -> PullToRefreshBox(
                                isRefreshing = refreshing,
                                onRefresh = {
                                    if (!refreshing) {
                                        scope.launch { loadSeriesDetails(initialLoad = false) }
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                                state = pullRefreshState,
                                indicator = { BunkoPullToRefreshIndicator(pullRefreshState, refreshing) }
                            ) {
                                SeriesDetailContent(
                                    series = displaySeries,
                                    metadata = metadata,
                                    continueChapter = continueChapter,
                                    chapterCards = chapterCards,
                                    volumeCount = volumes.size,
                                    session = session,
                                    api = loadedApi,
                                    isAdmin = isAdmin,
                                    downloadedChapterIds = downloadedChapterIds,
                                    downloadingChapterIds = downloadingChapterIds,
                                    onOpenFilteredSeries = onOpenFilteredSeries,
                                    onPick = { chapterId, volumeId, initialPage -> onPick(chapterId, volumeId, false, initialPage) },
                                    onReadIncognito = { item -> onPick(item.chapter.id, item.volume.id, true, null) },
                                    onMarkRead = ::markIssueRead,
                                    onMarkUnread = ::markIssueUnread,
                                    onDownload = ::downloadIssue,
                                    onRemoveDownload = ::removeIssueDownload,
                                    onRefreshSeries = { scope.launch { loadSeriesDetails(initialLoad = false) } },
                                    onMessage = ::showMessage,
                                    navigationBarStyle = navigationBarStyle
                                )
                            }
                        }

                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(bottom = 80.dp, start = 16.dp, end = 16.dp)
                        )
                    }
                }

                HomeBottomNavigation(
                    selected = currentDestination,
                    onSelect = onSelectDestination,
                    navigationBarStyle = navigationBarStyle,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        } else {
            Column(Modifier.fillMaxSize().then(contentCutoutModifier)) {
                topHeader()

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when {
                        loading -> DarkLoadingState()
                        error != null -> DarkMessageState(
                            title = "Could not load series details",
                            body = error ?: "Unknown error",
                            actionLabel = "Retry",
                            onAction = { scope.launch { loadSeriesDetails(initialLoad = true) } }
                        )
                        loadedApi == null -> DarkMessageState(
                            title = "Could not load series details",
                            body = "API unavailable",
                            actionLabel = "Retry",
                            onAction = { scope.launch { loadSeriesDetails(initialLoad = true) } }
                        )
                        else -> PullToRefreshBox(
                            isRefreshing = refreshing,
                            onRefresh = {
                                if (!refreshing) {
                                    scope.launch { loadSeriesDetails(initialLoad = false) }
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                            state = pullRefreshState,
                            indicator = { BunkoPullToRefreshIndicator(pullRefreshState, refreshing) }
                        ) {
                            SeriesDetailContent(
                                series = displaySeries,
                                metadata = metadata,
                                continueChapter = continueChapter,
                                chapterCards = chapterCards,
                                volumeCount = volumes.size,
                                session = session,
                                api = loadedApi,
                                isAdmin = isAdmin,
                                downloadedChapterIds = downloadedChapterIds,
                                downloadingChapterIds = downloadingChapterIds,
                                onOpenFilteredSeries = onOpenFilteredSeries,
                                onPick = { chapterId, volumeId, initialPage -> onPick(chapterId, volumeId, false, initialPage) },
                                onReadIncognito = { item -> onPick(item.chapter.id, item.volume.id, true, null) },
                                onMarkRead = ::markIssueRead,
                                onMarkUnread = ::markIssueUnread,
                                onDownload = ::downloadIssue,
                                onRemoveDownload = ::removeIssueDownload,
                                onRefreshSeries = { scope.launch { loadSeriesDetails(initialLoad = false) } },
                                onMessage = ::showMessage,
                                navigationBarStyle = navigationBarStyle
                            )
                        }
                    }

                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                    )
                }

                HomeBottomNavigation(
                    selected = currentDestination,
                    onSelect = onSelectDestination,
                    navigationBarStyle = navigationBarStyle
                )
            }
        }
    }
}

@Composable
private fun SeriesDetailContent(
    series: SeriesDto,
    metadata: SeriesMetadataDto?,
    continueChapter: ChapterDto?,
    chapterCards: List<ChapterCardItem>,
    volumeCount: Int,
    session: KavitaSession,
    api: KavitaApi,
    isAdmin: Boolean,
    downloadedChapterIds: Set<Int>,
    downloadingChapterIds: Set<Int>,
    onOpenFilteredSeries: (SearchSeriesTarget, Int, String) -> Unit,
    onPick: (chapterId: Int, volumeId: Int, initialPage: Int?) -> Unit,
    onReadIncognito: (ChapterCardItem) -> Unit,
    onMarkRead: (ChapterCardItem) -> Unit,
    onMarkUnread: (ChapterCardItem) -> Unit,
    onDownload: (ChapterCardItem) -> Unit,
    onRemoveDownload: (ChapterCardItem) -> Unit,
    onRefreshSeries: () -> Unit = {},
    onMessage: (String) -> Unit,
    navigationBarStyle: NavigationBarStyle = NavigationBarStyle.Standard
) {
    val bottomPadding = if (navigationBarStyle == NavigationBarStyle.FloatingPill) 96.dp else 24.dp
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 16.dp,
            end = 16.dp,
            bottom = bottomPadding
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SeriesDetailSummary(
                series = series,
                metadata = metadata,
                continueChapter = continueChapter,
                chapterCards = chapterCards,
                volumeCount = volumeCount,
                session = session,
                api = api,
                isAdmin = isAdmin,
                downloadedChapterIds = downloadedChapterIds,
                downloadingChapterIds = downloadingChapterIds,
                onOpenFilteredSeries = onOpenFilteredSeries,
                onPick = onPick,
                onReadIncognito = onReadIncognito,
                onMarkRead = onMarkRead,
                onMarkUnread = onMarkUnread,
                onDownload = onDownload,
                onRemoveDownload = onRemoveDownload,
                onRefreshSeries = onRefreshSeries,
                onMessage = onMessage
            )
        }
    }
}

