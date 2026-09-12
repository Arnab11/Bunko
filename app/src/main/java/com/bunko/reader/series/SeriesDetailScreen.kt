package com.bunko.reader.series

import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChapterPickScreen(
    sessionStore: KavitaSessionStore,
    libraryId: Int,
    seriesId: Int,
    seriesName: String,
    onOpenFilteredSeries: (SearchSeriesTarget, Int, String) -> Unit,
    onBack: () -> Unit = {},
    onPick: (chapterId: Int, volumeId: Int, incognito: Boolean) -> Unit
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
    var selectedIssue by remember { mutableStateOf<ChapterCardItem?>(null) }
    var selectedIssueDetail by remember { mutableStateOf<ChapterDto?>(null) }
    var selectedIssueSize by remember { mutableStateOf<Long?>(null) }
    var issueLoading by remember { mutableStateOf(false) }
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
    }
    val displaySeries = series ?: SeriesDto(id = seriesId, name = seriesName, libraryId = libraryId)
    val loadedApi = api
    val selectedChapterId = selectedIssue?.chapter?.id
    val downloadFlow = remember(session.baseUrl, selectedChapterId) {
        selectedChapterId?.let { offlineRepository.observe(session, it) } ?: flowOf(null)
    }
    val downloadRecord by downloadFlow.collectAsState(initial = null)

    LaunchedEffect(session.baseUrl, selectedChapterId, downloadRecord?.status) {
        val chapterId = selectedChapterId ?: return@LaunchedEffect
        if (offlineRepository.cleanupUnavailableDownload(session, chapterId)) {
            return@LaunchedEffect
        }
        while (downloadRecord?.status in setOf(
                OfflineDownloadStatus.Queued,
                OfflineDownloadStatus.Downloading
            )) {
            offlineRepository.reconcile(session, chapterId)
            delay(750)
        }
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
        selectedIssue = selectedIssue?.let { item ->
            if (item.chapter.id == updated.id) item.copy(chapter = updated) else item
        }
        selectedIssueDetail = updated
    }

    fun openIssue(item: ChapterCardItem) {
        val currentApi = loadedApi ?: return
        selectedIssue = item
        selectedIssueDetail = item.chapter
        selectedIssueSize = null
        issueLoading = true
        scope.launch {
            val chapterId = item.chapter.id
            val detail = runCatching { currentApi.seriesChapter(chapterId) }
                .onFailure { BunkoLog.w("Could not load issue detail for chapter $chapterId.", it) }
                .getOrNull()
            val size = runCatching { currentApi.chapterSize(chapterId) }
                .onFailure { BunkoLog.w("Could not load issue size for chapter $chapterId.", it) }
                .getOrNull()
            if (selectedIssue?.chapter?.id == chapterId) {
                detail?.let(::updateChapter)
                selectedIssueSize = size
                issueLoading = false
            }
        }
    }

    fun markSelectedIssueRead() {
        val currentApi = loadedApi ?: return
        val item = selectedIssue ?: return
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
                val refreshed = try {
                    currentApi.seriesChapter(item.chapter.id)
                } catch (c: CancellationException) {
                    throw c
                } catch (t: Throwable) {
                    BunkoLog.w("Could not refresh issue detail after marking read.", t)
                    item.chapter.copy(pagesRead = item.chapter.pages)
                }
                updateChapter(refreshed)
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

    fun markSelectedIssueUnread() {
        val currentApi = loadedApi ?: return
        val item = selectedIssue ?: return
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
                val refreshed = try {
                    currentApi.seriesChapter(item.chapter.id)
                } catch (c: CancellationException) {
                    throw c
                } catch (t: Throwable) {
                    BunkoLog.w("Could not refresh issue detail after marking unread.", t)
                    item.chapter.copy(pagesRead = 0)
                }
                updateChapter(refreshed)
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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = BunkoBackground,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                title = {
                    Column(verticalArrangement = Arrangement.Center) {
                        Text(
                            text = displaySeries.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (chapterCards.isNotEmpty()) {
                            Text(
                                text = "${chapterCards.size} ${if (chapterCards.size == 1) "issue" else "issues"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { scope.launch { loadSeriesDetails(initialLoad = false) } },
                        enabled = !refreshing && !loading
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(16.dp)
            )
        }
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(BunkoBackground)
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
                        onOpenFilteredSeries = onOpenFilteredSeries,
                        onPick = { chapterId, volumeId -> onPick(chapterId, volumeId, false) },
                        onIssueClick = ::openIssue,
                        onMessage = ::showMessage
                    )
                }
            }

            val issue = selectedIssue
            val seriesActionColor = displaySeries.coverActionColor()
            val issueActionColor = (selectedIssueDetail ?: issue?.chapter)
                ?.coverActionColor(fallback = seriesActionColor)
                ?: seriesActionColor
            IssueDetailSideSheet(
                visible = issue != null,
                seriesName = displaySeries.name,
                volume = issue?.volume,
                chapter = selectedIssueDetail ?: issue?.chapter,
                fileSizeBytes = selectedIssueSize,
                downloadRecord = downloadRecord,
                loading = issueLoading,
                actionBusy = issueActionBusy,
                session = session,
                actionColor = issueActionColor,
                onDismissRequest = {
                    selectedIssue = null
                    selectedIssueDetail = null
                    selectedIssueSize = null
                },
                onRead = {
                    issue?.let { onPick(it.chapter.id, it.volume.id, false) }
                },
                onReadIncognito = {
                    issue?.let { onPick(it.chapter.id, it.volume.id, true) }
                },
                onMarkRead = ::markSelectedIssueRead,
                onMarkUnread = ::markSelectedIssueUnread,
                onDownload = {
                    issue?.let { item ->
                        if (issueActionBusy) return@IssueDetailSideSheet
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
                                    expectedBytes = selectedIssueSize,
                                    expectedPageCount = selectedIssueDetail?.pages ?: item.chapter.pages
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
                },
                onRemoveDownload = {
                    issue?.let { item ->
                        scope.launch {
                            selectedIssue = null
                            selectedIssueDetail = null
                            selectedIssueSize = null
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
                }
            )
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
    onOpenFilteredSeries: (SearchSeriesTarget, Int, String) -> Unit,
    onPick: (chapterId: Int, volumeId: Int) -> Unit,
    onIssueClick: (ChapterCardItem) -> Unit,
    onMessage: (String) -> Unit
) {
    val specialCards = chapterCards.filter { it.chapter.isSpecial }
    val issueCards = chapterCards.filterNot { it.chapter.isSpecial }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp && maxWidth > maxHeight
        if (wide) {
            val summaryWidth = if (maxWidth >= 1100.dp) 420.dp else 340.dp
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .width(summaryWidth)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
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
                            onOpenFilteredSeries = onOpenFilteredSeries,
                            onPick = onPick,
                            onMessage = onMessage
                        )
                    }
                }
                ChapterIssueGrid(
                    issueCards = issueCards,
                    specialCards = specialCards,
                    session = session,
                    onIssueClick = onIssueClick,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SeriesDetailSummary(
                        series = series,
                        metadata = metadata,
                        continueChapter = continueChapter,
                        chapterCards = chapterCards,
                        volumeCount = volumeCount,
                        session = session,
                        api = api,
                        isAdmin = isAdmin,
                        onOpenFilteredSeries = onOpenFilteredSeries,
                        onPick = onPick,
                        onMessage = onMessage
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ChapterSectionHeader("Issues", issueCards.size)
                }
                gridItems(issueCards, key = { "${it.volume.id}-${it.chapter.id}" }) { item ->
                    ChapterGridCard(
                        item = item,
                        session = session,
                        onClick = { onIssueClick(item) }
                    )
                }
                if (specialCards.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ChapterSectionHeader("Specials", specialCards.size)
                    }
                    gridItems(specialCards, key = { "${it.volume.id}-${it.chapter.id}" }) { item ->
                        ChapterGridCard(
                            item = item,
                            session = session,
                            onClick = { onIssueClick(item) }
                        )
                    }
                }
            }
        }
    }
}

