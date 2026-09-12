package li.mof.kamigura.reader

import android.os.SystemClock
import android.view.ViewConfiguration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import coil.ImageLoader
import coil.imageLoader
import eu.wewox.pagecurl.ExperimentalPageCurlApi
import eu.wewox.pagecurl.config.PageCurlConfig
import eu.wewox.pagecurl.config.rememberPageCurlConfig
import eu.wewox.pagecurl.page.PageCurl
import eu.wewox.pagecurl.page.PageCurlState
import eu.wewox.pagecurl.page.PageCurlTurnDirection
import li.mof.kamigura.AppSettings
import li.mof.kamigura.AppSettingsStore
import li.mof.kamigura.FileDimensionDto
import li.mof.kamigura.InvertMode
import li.mof.kamigura.PageBackground
import li.mof.kamigura.KavitaApi
import li.mof.kamigura.KavitaClient
import li.mof.kamigura.KamiguraLog
import li.mof.kamigura.KavitaSession
import li.mof.kamigura.KavitaSessionStore
import li.mof.kamigura.MarkChapterReadDto
import li.mof.kamigura.MarkVolumesReadDto
import li.mof.kamigura.PageTurnMode
import li.mof.kamigura.ProgressDto
import li.mof.kamigura.ReaderReadingDirection
import li.mof.kamigura.download.OfflineChapter
import li.mof.kamigura.download.OfflineIssueRepository
import li.mof.kamigura.reader.internal.ReaderInvertCacheKey
import li.mof.kamigura.reader.internal.ReaderPrefetchTarget
import li.mof.kamigura.reader.internal.ReaderFullscreenEffect
import li.mof.kamigura.reader.internal.ReaderChapterBoundary
import li.mof.kamigura.reader.internal.ReaderChapterBoundaryScreen
import li.mof.kamigura.reader.internal.ReaderChapterEntry
import li.mof.kamigura.reader.internal.ReaderMenuOverlay
import li.mof.kamigura.reader.internal.ReaderPageView
import li.mof.kamigura.reader.internal.ReaderTapLayer
import li.mof.kamigura.reader.internal.ReaderVerticalScroll
import li.mof.kamigura.reader.internal.ReaderZoomEpsilon
import li.mof.kamigura.reader.internal.ReaderZoomPanState
import li.mof.kamigura.reader.internal.lerpTo
import li.mof.kamigura.reader.internal.pageIsWide
import li.mof.kamigura.reader.internal.preAnalyzeReaderPages
import li.mof.kamigura.reader.internal.prefetchReaderPages
import li.mof.kamigura.reader.internal.readerPageLayout
import li.mof.kamigura.reader.internal.readerPanBoundsPx
import li.mof.kamigura.reader.internal.readerEstimatedDecodeBytes
import li.mof.kamigura.reader.internal.readerChapterEntry
import li.mof.kamigura.reader.internal.readerChapterNeighbors
import li.mof.kamigura.reader.internal.readerChapterSequence
import li.mof.kamigura.reader.internal.readerPrefetchMemoryPlan
import li.mof.kamigura.reader.internal.readerPrefetchPageIndicesAround
import li.mof.kamigura.reader.internal.readerPrefetchSlotWidthPx
import li.mof.kamigura.reader.internal.readerVerticalPrefetchPageIndicesAround
import li.mof.kamigura.reader.internal.readerVerticalPrefetchSize
import li.mof.kamigura.reader.internal.readerVisiblePageIndices
import li.mof.kamigura.reader.internal.spreadPagesFor
import li.mof.kamigura.reader.internal.toPageDimensionMap
import li.mof.kamigura.reader.internal.withDoubleTapZoom
import li.mof.kamigura.reader.internal.withTransform
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import li.mof.kamigura.reader.internal.EpubBlock
import li.mof.kamigura.reader.internal.EpubSubpage
import li.mof.kamigura.reader.internal.ReaderEpubPageView
import li.mof.kamigura.reader.internal.ReaderEpubPaginator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

// Process-lived scope for chapter-exit writes (mark read/unread, final progress) so they
// survive the reader being popped off the back stack the instant the user leaves.
private val ReaderExitWriteScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
private val ReaderProgressWriteMutex = Mutex()

private data class ReaderRemoteProgressTarget(
    val chapterId: Int,
    val volumeId: Int,
    val page: Int,
    val pageCount: Int,
    val offline: Boolean,
    val revision: Long,
    val revisionClock: AtomicLong
)

private data class PendingReaderRemoteProgress(
    val target: ReaderRemoteProgressTarget,
    val sinceMillis: Long
)

private const val ReaderProgressSyncDelayMillis = 3_000L
private const val ReaderSpreadCurlVisualPageCount = 3
private const val ReaderSpreadCurlVisualCurrent = 1
private const val ReaderSpreadCurlTurnEndFractionX = 0.5f
internal const val ReaderPortraitBackPageContentAlpha = 0.05f

internal fun readerPageBackgroundColor(
    darkPaper: Boolean,
    usePureColors: Boolean
): Color = when {
    darkPaper && usePureColors -> Color.Black
    darkPaper -> Color(0xFF101010)
    usePureColors -> Color.White
    else -> Color(0xFFFAF7F2)
}

internal fun readerPortraitBackPageContentAlpha(showContent: Boolean): Float {
    return if (showContent) ReaderPortraitBackPageContentAlpha else 0f
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPageCurlApi::class)
@Composable
fun ReaderScreen(
    sessionStore: KavitaSessionStore,
    settingsStore: AppSettingsStore,
    libraryId: Int,
    seriesId: Int,
    volumeId: Int,
    chapterId: Int,
    incognito: Boolean = false,
    initialPage: Int? = null,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val fallbackImageLoader = ctx.imageLoader
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val settings by settingsStore.flow.collectAsState(initial = AppSettings())

    var session by remember { mutableStateOf<KavitaSession?>(null) }
    var api by remember { mutableStateOf<KavitaApi?>(null) }
    var currentChapterId by remember { mutableIntStateOf(chapterId) }
    var currentVolumeId by remember { mutableIntStateOf(volumeId) }
    var chapterSequence by remember { mutableStateOf<List<ReaderChapterEntry>>(emptyList()) }
    var currentChapter by remember {
        mutableStateOf(
            ReaderChapterEntry(
                chapterId = chapterId,
                volumeId = volumeId,
                volumeName = null,
                chapterName = "Chapter"
            )
        )
    }
    var seriesName by remember { mutableStateOf("") }
    var chapterSwitching by remember { mutableStateOf(false) }
    var chapterBoundary by remember { mutableStateOf<ReaderChapterBoundary?>(null) }
    var boundaryDragDirection by remember { mutableStateOf<ReaderTurnDirection?>(null) }
    var boundaryDragProgress by remember { mutableFloatStateOf(0f) }
    var pages by remember { mutableIntStateOf(0) }
    var pageDimensions by remember { mutableStateOf<Map<Int, FileDimensionDto>>(emptyMap()) }
    var page by remember { mutableIntStateOf(0) }
    var readerReady by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showReaderMenu by remember { mutableStateOf(false) }
    ReaderFullscreenEffect(showStatusBar = showReaderMenu)
    var readingDirection by remember { mutableStateOf(settings.reader.readingDirection) }
    // Per-book overrides survive a short close/reopen cycle in process memory. The server
    // direction and global Reader setting remain authoritative after the cache expires.
    var invertMode by remember { mutableStateOf(settings.reader.invertMode) }
    var sessionPreferenceKey by remember { mutableStateOf<String?>(null) }
    var zoomPan by remember { mutableStateOf(ReaderZoomPanState()) }
    var completingRead by remember { mutableStateOf(false) }
    var offlineChapter by remember { mutableStateOf<OfflineChapter?>(null) }
    var readerImageLoader by remember { mutableStateOf<ImageLoader?>(null) }
    var activeTransition by remember { mutableStateOf<ReaderPageTransition?>(null) }
    var transitionProgress by remember { mutableFloatStateOf(0f) }
    var transitionSettling by remember { mutableStateOf(false) }
    var activeCurlDirection by remember { mutableStateOf<ReaderTurnDirection?>(null) }
    var activeCurlTargetPage by remember { mutableStateOf<Int?>(null) }
    var curlDragStartPointer by remember { mutableStateOf<Offset?>(null) }
    var curlDragProgress by remember { mutableFloatStateOf(0f) }
    var queuedTurn by remember { mutableStateOf<PendingReaderTurn?>(null) }
    var pendingZoomLandingDirection by remember { mutableStateOf<ReaderTurnDirection?>(null) }
    var dragBoundaryDirection by remember { mutableStateOf<ReaderTurnDirection?>(null) }
    var zoomAnimationJob by remember { mutableStateOf<Job?>(null) }
    var closeDragOffsetY by remember { mutableFloatStateOf(0f) }
    var closeAnimationJob by remember { mutableStateOf<Job?>(null) }
    var verticalRestoreNonce by remember { mutableIntStateOf(0) }
    val invertDecisionCache = remember { mutableStateMapOf<ReaderInvertCacheKey, Boolean>() }
    val offlineRepository = remember(ctx) { OfflineIssueRepository(ctx) }
    val minimumFlingVelocity = remember(ctx) {
        ViewConfiguration.get(ctx).scaledMinimumFlingVelocity.toFloat()
    }
    var pendingRemoteProgress by remember { mutableStateOf<PendingReaderRemoteProgress?>(null) }
    val lastRemoteProgressPages = remember { ConcurrentHashMap<Int, Int>() }
    val progressRevisionClocks = remember { mutableMapOf<Int, AtomicLong>() }

    var isEpub by remember { mutableStateOf(false) }
    var epubSpineBlocks by remember { mutableStateOf<List<List<EpubBlock>>>(emptyList()) }
    var epubSubpages by remember { mutableStateOf<List<EpubSubpage>>(emptyList()) }
    val epubFontSizeSp = settings.reader.epubFontSizeSp

    suspend fun loadEpubSpines(
        chapterId: Int,
        spineCount: Int,
        loadedApi: KavitaApi,
        loadedSession: KavitaSession,
        client: KavitaClient
    ): List<List<EpubBlock>> {
        val count = spineCount.coerceAtLeast(1)
        return (0 until count).map { spineIndex ->
            scope.async(Dispatchers.IO) {
                val html = runCatching {
                    loadedApi.bookPage(chapterId, spineIndex).string()
                }.getOrDefault("")
                ReaderEpubPaginator.parseHtmlToBlocks(
                    html = html,
                    chapterId = chapterId,
                    baseUrl = loadedSession.baseUrl,
                    apiKey = loadedSession.apiKey,
                    bookResourceUrlBuilder = client::bookResourceUrl
                )
            }
        }.awaitAll()
    }

    DisposableEffect(readerImageLoader) {
        val activeLoader = readerImageLoader
        onDispose { activeLoader?.shutdown() }
    }

    fun clampPage(value: Int): Int = value.coerceIn(0, (pages - 1).coerceAtLeast(0))
    fun newRemoteProgressTarget(
        targetChapterId: Int,
        targetVolumeId: Int,
        targetPage: Int,
        targetPageCount: Int,
        offline: Boolean
    ): ReaderRemoteProgressTarget {
        val revisionClock = progressRevisionClocks.getOrPut(targetChapterId) { AtomicLong(0L) }
        return ReaderRemoteProgressTarget(
            chapterId = targetChapterId,
            volumeId = targetVolumeId,
            page = targetPage,
            pageCount = targetPageCount,
            offline = offline,
            revision = revisionClock.incrementAndGet(),
            revisionClock = revisionClock
        )
    }
    suspend fun saveRemoteProgress(
        target: ReaderRemoteProgressTarget,
        clearPending: Boolean = true,
        targetApi: KavitaApi? = api,
        targetSession: KavitaSession? = session
    ): Boolean {
        if (incognito) return false
        if (target.pageCount <= 0 || target.page !in 0 until target.pageCount) return false
        if (lastRemoteProgressPages[target.chapterId] == target.page) {
            if (clearPending && pendingRemoteProgress?.target == target) {
                pendingRemoteProgress = null
            }
            return true
        }
        val loadedApi = targetApi ?: return false
        return ReaderProgressWriteMutex.withLock {
            if (target.revision != target.revisionClock.get()) return@withLock false
            try {
                loadedApi.saveProgress(
                    ProgressDto(
                        libraryId = libraryId,
                        seriesId = seriesId,
                        volumeId = target.volumeId,
                        chapterId = target.chapterId,
                        pageNum = target.page
                    )
                )
                lastRemoteProgressPages[target.chapterId] = target.page
                if (clearPending && pendingRemoteProgress?.target == target) {
                    pendingRemoteProgress = null
                }
                if (target.offline && targetSession != null) {
                    offlineRepository.markProgressSynced(
                        session = targetSession,
                        chapterId = target.chapterId,
                        expectedPage = target.page
                    )
                }
                true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                KamiguraLog.w(
                    "Could not save remote reader progress for chapter ${target.chapterId}.",
                    t
                )
                false
            }
        }
    }
    fun completeChapter(exitAfter: Boolean = true) {
        if (completingRead || pages <= 0) return
        completingRead = true
        showReaderMenu = false
        val completedChapterId = currentChapterId
        val completedVolumeId = currentVolumeId
        val finalPage = pages - 1
        val progressTarget = newRemoteProgressTarget(
            targetChapterId = completedChapterId,
            targetVolumeId = completedVolumeId,
            targetPage = finalPage,
            targetPageCount = pages,
            offline = offlineChapter != null
        )
        pendingRemoteProgress = null
        // Exit immediately when there is no neighbor; otherwise the same writes continue
        // behind the chapter-boundary screen.
        if (exitAfter) onBack()
        if (incognito) return
        val loadedApi = api
        val loadedSession = session
        val hasOffline = offlineChapter != null
        ReaderExitWriteScope.launch {
            runCatching {
                if (hasOffline && loadedSession != null) {
                    offlineRepository.saveLocalProgress(
                        session = loadedSession,
                        chapterId = completedChapterId,
                        page = finalPage,
                        markRead = true
                    )
                }
                val progressSaved = loadedApi != null && saveRemoteProgress(
                    target = progressTarget,
                    clearPending = false,
                    targetApi = loadedApi,
                    targetSession = loadedSession
                )
                val readMarked = loadedApi != null && runCatching {
                    loadedApi.markChapterRead(
                        MarkChapterReadDto(
                            seriesId = seriesId,
                            chapterId = completedChapterId,
                            generateReadingSession = false
                        )
                    )
                }.onFailure {
                    KamiguraLog.w("Could not mark chapter $completedChapterId as read.", it)
                }.isSuccess
                if (hasOffline && loadedSession != null && progressSaved) {
                    offlineRepository.markProgressSynced(
                        session = loadedSession,
                        chapterId = completedChapterId,
                        expectedPage = finalPage,
                        markedRead = readMarked
                    )
                }
            }
        }
    }
    fun resetChapterAndExit(exitAfter: Boolean = true) {
        if (completingRead) return
        completingRead = true
        showReaderMenu = false
        val resetChapterId = currentChapterId
        progressRevisionClocks.getOrPut(resetChapterId) { AtomicLong(0L) }.incrementAndGet()
        pendingRemoteProgress = null
        // Exit immediately when there is no neighbor; otherwise remain on the boundary.
        if (exitAfter) onBack()
        if (incognito) return
        val loadedApi = api
        val loadedSession = session
        val hasOffline = offlineChapter != null
        ReaderExitWriteScope.launch {
            runCatching {
                if (hasOffline && loadedSession != null) {
                    offlineRepository.markLocalUnread(loadedSession, resetChapterId)
                }
                val unreadMarked = loadedApi != null && runCatching {
                    ReaderProgressWriteMutex.withLock {
                        loadedApi.markChaptersUnread(
                            MarkVolumesReadDto(
                                seriesId = seriesId,
                                chapterIds = listOf(resetChapterId)
                            )
                        )
                    }
                }.onFailure {
                    KamiguraLog.w("Could not mark chapter $resetChapterId as unread.", it)
                }.isSuccess
                if (hasOffline && loadedSession != null && unreadMarked) {
                    offlineRepository.markProgressSynced(
                        session = loadedSession,
                        chapterId = resetChapterId,
                        expectedPage = 0,
                        markedUnread = true
                    )
                }
            }
        }
    }
    fun jumpToPage(targetPage: Int) {
        val nextPage = clampPage(targetPage)
        if (nextPage != page) {
            page = nextPage
        }
    }
    fun switchChapter(target: ReaderChapterEntry, openAtLastPage: Boolean) {
        if (chapterSwitching || target.chapterId == currentChapterId) return
        val loadedSession = session ?: return
        val loadedApi = api ?: return
        chapterSwitching = true
        showReaderMenu = false
        error = null
        scope.launch {
            try {
                val local = runCatching {
                    offlineRepository.localChapter(loadedSession, target.chapterId)
                }.onFailure {
                    KamiguraLog.w("Could not load local offline chapter ${target.chapterId}.", it)
                }.getOrNull()
                val loadedPageCount: Int
                val loadedDimensions: Map<Int, FileDimensionDto>
                if (local != null) {
                    loadedPageCount = local.pages.size
                    loadedDimensions = local.dimensions
                } else {
                    val info = loadedApi.chapterInfo(target.chapterId, includeDimensions = true)
                    loadedPageCount = info.pages ?: 0
                    loadedDimensions = info.pageDimensions.toPageDimensionMap()
                }
                if (loadedPageCount <= 0) {
                    error = "Chapter has no readable pages"
                    return@launch
                }
                val landingPage = if (openAtLastPage) loadedPageCount - 1 else 0

                var isEpubTarget = false
                val chDto = runCatching { loadedApi.seriesChapter(target.chapterId) }.getOrNull()
                if (chDto?.format == 3) {
                    isEpubTarget = true
                } else if (chDto?.format == null) {
                    val seriesDto = runCatching { loadedApi.series(seriesId) }.getOrNull()
                    isEpubTarget = seriesDto?.format == 3
                }
                isEpub = isEpubTarget
                if (isEpubTarget) {
                    val spineCount = loadedPageCount.coerceAtLeast(1)
                    val clientHelper = KavitaClient(ctx, sessionStore)
                    epubSpineBlocks = loadEpubSpines(
                        chapterId = target.chapterId,
                        spineCount = spineCount,
                        loadedApi = loadedApi,
                        loadedSession = loadedSession,
                        client = clientHelper
                    )
                } else {
                    epubSpineBlocks = emptyList()
                    epubSubpages = emptyList()
                }

                pendingRemoteProgress = null
                readerReady = false
                activeTransition = null
                transitionProgress = 0f
                transitionSettling = false
                activeCurlDirection = null
                activeCurlTargetPage = null
                curlDragStartPointer = null
                curlDragProgress = 0f
                queuedTurn = null
                dragBoundaryDirection = null
                currentChapterId = target.chapterId
                currentVolumeId = target.volumeId
                currentChapter = target
                offlineChapter = local
                pages = loadedPageCount
                pageDimensions = loadedDimensions
                page = landingPage
                verticalRestoreNonce++
                completingRead = false
                chapterBoundary = null
                boundaryDragDirection = null
                boundaryDragProgress = 0f
                error = null
                readerReady = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                KamiguraLog.w("Could not switch Reader to chapter ${target.chapterId}.", t)
                error = t.message ?: t.toString()
            } finally {
                chapterSwitching = false
            }
        }
    }

    LaunchedEffect(Unit) {
        // Seed the session invert override from the persisted global value. `.first()` on the
        // store flow yields the real DataStore value (not the collectAsState default), so this
        // is correct even before `settings` has emitted.
        val persistedReaderSettings = settingsStore.flow.first().reader
        invertMode = persistedReaderSettings.invertMode
        val loadedSession = sessionStore.load()
        session = loadedSession
        val activeProfileId = runCatching { sessionStore.activeProfile()?.id }.getOrNull()
        val preferenceKey = readerSessionPreferenceKey(loadedSession, activeProfileId, seriesId)
        sessionPreferenceKey = preferenceKey
        ReaderSessionPreferenceCache.load(ctx.cacheDir, System.currentTimeMillis())
        val cachedPreferences = ReaderSessionPreferenceCache.get(
            preferenceKey,
            System.currentTimeMillis()
        )
        if (cachedPreferences != null) {
            // Apply before network initialization so an offline reopen does not flash the
            // global direction or invert mode while the reading-profile call times out.
            readingDirection = cachedPreferences.readingDirection
            invertMode = cachedPreferences.invertMode
            ReaderExitWriteScope.launch {
                ReaderSessionPreferenceCache.persist(ctx.cacheDir)
            }
        }
        val local = runCatching {
            offlineRepository.localChapter(loadedSession, currentChapterId)
        }.onFailure {
            KamiguraLog.w("Could not load local offline chapter $currentChapterId.", it)
        }.getOrNull()
        offlineChapter = local
        if (local != null) {
            seriesName = local.record.seriesName
            currentVolumeId = local.record.volumeId
            currentChapter = ReaderChapterEntry(
                chapterId = currentChapterId,
                volumeId = currentVolumeId,
                volumeName = null,
                chapterName = local.record.issueName
            )
            pages = local.pages.size
            pageDimensions = local.dimensions
            page = (initialPage ?: local.record.localPage).coerceIn(0, (pages - 1).coerceAtLeast(0))
            if (!local.record.progressPending) {
                lastRemoteProgressPages[currentChapterId] = page
            }
            readerReady = pages > 0
            verticalRestoreNonce++
        }

        try {
            val client = KavitaClient(ctx, sessionStore)
            val (loadedApi, okHttp) = client.buildApi()
            api = loadedApi
            readerImageLoader = client.buildReaderImageLoader(okHttp, loadedSession)
            runCatching { offlineRepository.syncPending(loadedSession, loadedApi) }
                .onFailure { KamiguraLog.w("Could not sync pending offline progress from Reader.", it) }
            val chapterMetadataJob = launch {
                seriesName = runCatching { loadedApi.series(seriesId).name }
                    .onFailure { KamiguraLog.w("Could not load reader series name for $seriesId.", it) }
                    .getOrDefault(seriesName)
                val loadedVolumes = runCatching { loadedApi.volumes(seriesId) }
                    .onFailure { KamiguraLog.w("Could not load reader chapter sequence for $seriesId.", it) }
                    .getOrDefault(emptyList())
                chapterSequence = readerChapterSequence(loadedVolumes)
                readerChapterEntry(loadedVolumes, currentChapterId)?.let {
                    currentChapter = it
                    currentVolumeId = it.volumeId
                }
            }
            val resolvedDirection = try {
                // A series-specific direction on the server (User/Implicit profile)
                // wins. When only the global Default profile applies, the series has
                // no direction of its own, so fall back to the app setting.
                val profile = loadedApi.readingProfile(libraryId, seriesId)
                resolveReaderReadingDirection(
                    globalDirection = persistedReaderSettings.readingDirection,
                    cachedDirection = cachedPreferences?.readingDirection,
                    profileKind = profile.kind,
                    profileDirection = profile.readingDirection
                )
            } catch (t: Throwable) {
                KamiguraLog.w("Could not load reading direction for series $seriesId.", t)
                cachedPreferences?.readingDirection ?: persistedReaderSettings.readingDirection
            }
            readingDirection = resolvedDirection
            cachedPreferences?.let { invertMode = it.invertMode }
            var isEpubChapter = false
            val chDto = runCatching { loadedApi.seriesChapter(currentChapterId) }.getOrNull()
            if (chDto?.format == 3) {
                isEpubChapter = true
            } else if (chDto?.format == null) {
                val seriesDto = runCatching { loadedApi.series(seriesId) }.getOrNull()
                if (seriesDto?.format == 3) {
                    isEpubChapter = true
                }
            }
            isEpub = isEpubChapter

            if (local == null) {
                val info = loadedApi.chapterInfo(currentChapterId, includeDimensions = true)
                val pageCount = info.pages ?: 0
                pages = pageCount
                pageDimensions = info.pageDimensions.toPageDimensionMap()
                val savedPage = initialPage ?: loadedApi.getProgress(currentChapterId).pageNum
                page = if (pages > 0) savedPage.coerceIn(0, pages - 1) else 0
                lastRemoteProgressPages[currentChapterId] = page

                if (isEpubChapter) {
                    val spineCount = pageCount.coerceAtLeast(1)
                    epubSpineBlocks = loadEpubSpines(
                        chapterId = currentChapterId,
                        spineCount = spineCount,
                        loadedApi = loadedApi,
                        loadedSession = loadedSession,
                        client = client
                    )
                }
            } else if (!local.record.progressPending) {
                val savedPage = runCatching { loadedApi.getProgress(currentChapterId).pageNum }
                    .onFailure {
                        KamiguraLog.w(
                            "Could not load remote reader progress for chapter $currentChapterId.",
                            it
                        )
                    }
                    .getOrNull()
                val landingPage = initialPage ?: savedPage
                if (landingPage != null) {
                    page = landingPage.coerceIn(0, pages - 1)
                    lastRemoteProgressPages[currentChapterId] = page
                }
            }
            chapterMetadataJob.join()
            readerReady = true
            verticalRestoreNonce++
        } catch (t: Throwable) {
            KamiguraLog.w("Could not initialize Reader for chapter $currentChapterId.", t)
            if (local == null) {
                error = t.message ?: t.toString()
            }
        }
    }

    LaunchedEffect(
        currentChapterId,
        readerReady,
        pages,
        page,
        incognito,
        session,
        offlineChapter
    ) {
        if (!readerReady) return@LaunchedEffect
        if (incognito) return@LaunchedEffect
        if (pages <= 0 || page !in 0 until pages) return@LaunchedEffect
        val loadedSession = session ?: return@LaunchedEffect
        if (offlineChapter != null) {
            offlineRepository.saveLocalProgress(loadedSession, currentChapterId, page)
        }
    }

    LaunchedEffect(
        api,
        currentChapterId,
        currentVolumeId,
        readerReady,
        pages,
        page,
        incognito,
        chapterSwitching
    ) {
        if (!readerReady) return@LaunchedEffect
        if (incognito) return@LaunchedEffect
        if (chapterSwitching) return@LaunchedEffect
        if (pages <= 0 || page !in 0 until pages) return@LaunchedEffect
        if (api == null) return@LaunchedEffect
        if (lastRemoteProgressPages[currentChapterId] == page) return@LaunchedEffect
        val target = newRemoteProgressTarget(
            targetChapterId = currentChapterId,
            targetVolumeId = currentVolumeId,
            targetPage = page,
            targetPageCount = pages,
            offline = offlineChapter != null
        )
        pendingRemoteProgress = PendingReaderRemoteProgress(
            target = target,
            sinceMillis = SystemClock.elapsedRealtime()
        )
        delay(ReaderProgressSyncDelayMillis)
        saveRemoteProgress(target)
    }

    val latestFlushProgress by rememberUpdatedState<suspend () -> Unit>({
        val pending = pendingRemoteProgress
        if (pending != null) {
            val pendingAge = SystemClock.elapsedRealtime() - pending.sinceMillis
            if (pendingAge >= ReaderProgressSyncDelayMillis) {
                saveRemoteProgress(pending.target)
            }
        }
    })

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                lifecycleOwner.lifecycleScope.launch { latestFlushProgress() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            lifecycleOwner.lifecycleScope.launch { latestFlushProgress() }
        }
    }

    val s = session
    if (s == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Loading session...") }
        return
    }

    val client = remember { KavitaClient(ctx, sessionStore) }
    val activeImageLoader = readerImageLoader ?: fallbackImageLoader
    fun pageModel(index: Int): Any? = offlineChapter?.pages?.getOrNull(index)
        ?: if (index in 0 until pages) {
            client.pageImageUrl(s.baseUrl, s.apiKey, currentChapterId, index)
        } else {
            null
        }
    val rtl = readingDirection == ReaderReadingDirection.RightToLeft
    val vertical = readingDirection == ReaderReadingDirection.Vertical
    val spreadPages = spreadPagesFor(page, rtl)

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val portrait = maxHeight > maxWidth
        val viewportWidthPx = with(density) { maxWidth.toPx() }
        val viewportHeightPx = with(density) { maxHeight.toPx() }
        val viewportHeight = maxHeight

        LaunchedEffect(epubSpineBlocks, viewportWidthPx, viewportHeightPx, epubFontSizeSp, portrait) {
            if (isEpub && epubSpineBlocks.isNotEmpty() && viewportWidthPx > 0f && viewportHeightPx > 0f) {
                val horizontalPaddingPx = with(density) { 48.dp.roundToPx() }
                val verticalPaddingPx = with(density) { 40.dp.roundToPx() }
                val contentWidthPx = if (portrait) {
                    (viewportWidthPx.toInt() - horizontalPaddingPx).coerceAtLeast(100)
                } else {
                    ((viewportWidthPx / 2f).toInt() - horizontalPaddingPx).coerceAtLeast(100)
                }
                val contentHeightPx = (viewportHeightPx.toInt() - verticalPaddingPx).coerceAtLeast(100)
                val fontSizePx = with(density) { epubFontSizeSp.sp.toPx() }

                val oldProgressRatio = if (pages > 1) page.toFloat() / (pages - 1).toFloat() else 0f
                val allSubpages = withContext(Dispatchers.Default) {
                    epubSpineBlocks.indices.map { spineIndex ->
                        async {
                            ReaderEpubPaginator.paginateBlocks(
                                spineIndex = spineIndex,
                                blocks = epubSpineBlocks[spineIndex],
                                availableWidthPx = contentWidthPx,
                                availableHeightPx = contentHeightPx,
                                fontSizePx = fontSizePx,
                                density = density
                            )
                        }
                    }.awaitAll().flatten()
                }
                val total = allSubpages.size.coerceAtLeast(1)
                epubSubpages = allSubpages
                pages = total
                var targetPage = (oldProgressRatio * (total - 1)).roundToInt().coerceIn(0, total - 1)
                if (!portrait && targetPage % 2 != 0 && targetPage > 0) {
                    targetPage -= 1
                }
                page = targetPage
                lastRemoteProgressPages[currentChapterId] = targetPage
                readerReady = true
            }
        }

        @Composable
        fun RenderReaderPage(
            cursor: Int,
            pageCount: Int,
            portrait: Boolean,
            pageDimensions: Map<Int, FileDimensionDto>,
            rightToLeft: Boolean,
            pageModel: (Int) -> Any?,
            imageLoader: ImageLoader,
            invertMode: InvertMode,
            whiteThreshold: Float,
            invertDecisionCache: MutableMap<ReaderInvertCacheKey, Boolean>,
            pageBackground: Color,
            singlePageAlignmentOverride: Alignment? = null,
            modifier: Modifier = Modifier
        ) {
            if (isEpub && epubSubpages.isNotEmpty()) {
                if (portrait || singlePageAlignmentOverride != null) {
                    val safeIndex = cursor.coerceIn(0, epubSubpages.lastIndex)
                    ReaderEpubPageView(
                        subpage = epubSubpages[safeIndex],
                        fontSizeSp = epubFontSizeSp,
                        pageBackground = pageBackground,
                        invertMode = invertMode,
                        imageLoader = imageLoader,
                        modifier = modifier
                    )
                } else {
                    val spread = spreadPagesFor(cursor, rightToLeft)
                    Row(modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            if (spread.leftPage in epubSubpages.indices) {
                                ReaderEpubPageView(
                                    subpage = epubSubpages[spread.leftPage],
                                    fontSizeSp = epubFontSizeSp,
                                    pageBackground = pageBackground,
                                    invertMode = invertMode,
                                    imageLoader = imageLoader,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(pageBackground)
                                )
                            }
                        }
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            if (spread.rightPage in epubSubpages.indices) {
                                ReaderEpubPageView(
                                    subpage = epubSubpages[spread.rightPage],
                                    fontSizeSp = epubFontSizeSp,
                                    pageBackground = pageBackground,
                                    invertMode = invertMode,
                                    imageLoader = imageLoader,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(pageBackground)
                                )
                            }
                        }
                    }
                }
            } else {
                ReaderPageView(
                    cursor = cursor,
                    pageCount = pageCount,
                    portrait = portrait,
                    pageDimensions = pageDimensions,
                    rightToLeft = rightToLeft,
                    pageModel = pageModel,
                    imageLoader = imageLoader,
                    invertMode = invertMode,
                    whiteThreshold = whiteThreshold,
                    invertDecisionCache = invertDecisionCache,
                    pageBackground = pageBackground,
                    singlePageAlignmentOverride = singlePageAlignmentOverride,
                    modifier = modifier
                )
            }
        }

        val verticalListState = rememberLazyListState()
        var verticalBoundariesEnabled by remember(
            currentChapterId,
            readingDirection,
            verticalRestoreNonce
        ) { mutableStateOf(false) }
        LaunchedEffect(
            currentChapterId,
            readingDirection,
            verticalRestoreNonce,
            pages
        ) {
            if (vertical && pages > 0) {
                verticalBoundariesEnabled = false
                verticalListState.scrollToItem((page + 1).coerceIn(1, pages))
                withFrameNanos { }
                verticalBoundariesEnabled = true
            }
        }
        val turnVisualDistancePx = viewportWidthPx
        val layout = readerPageLayout(
            page = page,
            pageCount = pages,
            portrait = portrait,
            pageDimensions = pageDimensions,
            isEpub = isEpub
        )
        val portraitCurlState = remember(currentChapterId) { PageCurlState(initialCurrent = page) }
        val spreadCurlState = remember(currentChapterId) {
            PageCurlState(
                initialCurrent = ReaderSpreadCurlVisualCurrent,
                turnEndFractionX = ReaderSpreadCurlTurnEndFractionX
            )
        }
        // Inverting already forces the dark paper (a bright white flap next to an inverted
        // page is what the invert mode exists to avoid); the setting picks the colour the
        // rest of the time.
        val darkPaper = invertMode != InvertMode.Off ||
            settings.reader.pageBackground == PageBackground.Dark
        val curlBackPageColor = readerPageBackgroundColor(
            darkPaper = darkPaper,
            usePureColors = settings.reader.usePurePageBackgroundColors
        )
        // Every rendering branch letterboxes with the selected paper colour.
        val readerPageBackground = curlBackPageColor
        val portraitBackPageContentAlpha = readerPortraitBackPageContentAlpha(
            showContent = settings.reader.showPortraitPageBackContent
        )
        val portraitCurlConfig = rememberPageCurlConfig(
            backPageColor = curlBackPageColor,
            backPageContentAlpha = portraitBackPageContentAlpha
        )
        val spreadCurlConfig = rememberPageCurlConfig(
            backPageColor = curlBackPageColor,
            backPageContentAlpha = 0.96f,
            dragInteraction = PageCurlConfig.StartEndDragInteraction(
                pointerBehavior = PageCurlConfig.DragInteraction.PointerBehavior.PageEdge
            )
        )
        LaunchedEffect(curlBackPageColor, portraitBackPageContentAlpha) {
            portraitCurlConfig.backPageColor = curlBackPageColor
            portraitCurlConfig.backPageContentAlpha = portraitBackPageContentAlpha
            spreadCurlConfig.backPageColor = curlBackPageColor
        }
        fun prefetchTargetsFor(
            indices: List<Int>,
            forceMemory: Boolean = false
        ): List<ReaderPrefetchTarget> {
            val rawTargets = indices.mapNotNull { index ->
                val model = pageModel(index) as? String ?: return@mapNotNull null
                val targetWidth = readerPrefetchSlotWidthPx(
                    page = index,
                    pageCount = pages,
                    portrait = portrait,
                    pageDimensions = pageDimensions,
                    viewportWidthPx = viewportWidthPx
                )
                index to ReaderPrefetchTarget(
                    model = model,
                    targetWidth = targetWidth,
                    targetHeight = viewportHeightPx.roundToInt().coerceAtLeast(1)
                )
            }
            val memoryPlan = if (forceMemory) {
                rawTargets.map { true }
            } else {
                readerPrefetchMemoryPlan(
                    estimatedBytes = rawTargets.map { (targetPage, target) ->
                        readerEstimatedDecodeBytes(
                            page = targetPage,
                            pageDimensions = pageDimensions,
                            targetWidth = target.targetWidth,
                            targetHeight = target.targetHeight
                        )
                    },
                    memoryCacheMaxBytes = activeImageLoader.memoryCache?.maxSize?.toLong() ?: 0L
                )
            }
            return rawTargets.mapIndexedNotNull { index, (_, target) ->
                target.takeIf { memoryPlan[index] }
            }
        }
        fun verticalPrefetchTargetsFor(indices: List<Int>): List<ReaderPrefetchTarget> {
            val fallbackAspectRatio = viewportWidthPx / viewportHeightPx.coerceAtLeast(1f)
            val rawTargets = indices.mapNotNull { index ->
                val model = pageModel(index) as? String ?: return@mapNotNull null
                val size = readerVerticalPrefetchSize(
                    page = index,
                    pageDimensions = pageDimensions,
                    viewportWidthPx = viewportWidthPx,
                    fallbackAspectRatio = fallbackAspectRatio
                )
                index to ReaderPrefetchTarget(
                    model = model,
                    targetWidth = size.width,
                    targetHeight = size.height
                )
            }
            val memoryPlan = readerPrefetchMemoryPlan(
                estimatedBytes = rawTargets.map { (targetPage, target) ->
                    readerEstimatedDecodeBytes(
                        page = targetPage,
                        pageDimensions = pageDimensions,
                        targetWidth = target.targetWidth,
                        targetHeight = target.targetHeight
                    )
                },
                memoryCacheMaxBytes = activeImageLoader.memoryCache?.maxSize?.toLong() ?: 0L
            )
            return rawTargets.mapIndexedNotNull { index, (_, target) ->
                target.takeIf { memoryPlan[index] }
            }
        }
        LaunchedEffect(
            currentChapterId,
            page,
            pages,
            portrait,
            pageDimensions,
            offlineChapter,
            activeImageLoader,
            s.baseUrl,
            s.apiKey,
            settings.reader.prefetchTurns,
            vertical,
            invertMode,
            settings.reader.invertWhiteThreshold
        ) {
            if (offlineChapter != null || pages <= 0) return@LaunchedEffect
            val targets = if (vertical) {
                val indices = readerVerticalPrefetchPageIndicesAround(
                    page = page,
                    pageCount = pages,
                    pagesAhead = settings.reader.prefetchTurns
                )
                verticalPrefetchTargetsFor(indices)
            } else {
                val indices = readerPrefetchPageIndicesAround(
                    page = page,
                    pageCount = pages,
                    portrait = portrait,
                    pageDimensions = pageDimensions,
                    turns = settings.reader.prefetchTurns
                )
                prefetchTargetsFor(indices)
            }
            prefetchReaderPages(
                context = ctx,
                imageLoader = activeImageLoader,
                targets = targets
            )
            if (invertMode == InvertMode.Smart) {
                preAnalyzeReaderPages(
                    context = ctx,
                    imageLoader = activeImageLoader,
                    models = targets.map { it.model }.distinct(),
                    whiteThreshold = settings.reader.invertWhiteThreshold,
                    invertDecisionCache = invertDecisionCache
                )
            }
        }
        val showingFinalPage = if (layout.singlePage) {
            page >= pages - 1
        } else {
            spreadPages.leftPage >= pages - 1 || spreadPages.rightPage >= pages - 1
        }
        // Use the layout's own step sizes. layout.previousStep already accounts for a
        // wide/cover page just before the current view (step back 1, not a full 2), so
        // the page before a spread is not skipped when paging backwards.
        val nextPageTurnStep = layout.nextStep
        val previousPageTurnStep = layout.previousStep
        val baseZoomScale = 1f
        val totalZoomScale = baseZoomScale * zoomPan.userScale
        val panBounds = readerPanBoundsPx(viewportWidthPx, viewportHeightPx, totalZoomScale)
        val zoomPanEnabled = totalZoomScale > 1f + ReaderZoomEpsilon
        val usePortraitCurl =
            !vertical &&
            settings.reader.pageTurnMode == PageTurnMode.Curl &&
                settings.reader.pageTransitionAnimation &&
                portrait &&
                layout.singlePage &&
                !zoomPanEnabled
        val useSpreadCurl =
            !vertical &&
            settings.reader.pageTurnMode == PageTurnMode.Curl &&
                settings.reader.pageTransitionAnimation &&
                !portrait &&
                // A wide page is one sheet printed across the whole spread, so it turns as
                // a full-width leaf with the fold at the centre. Other landscape singles
                // (cover, chapter end, the page displayed alone next to a wide one) sit
                // centred with no spine under them, so they fall back to Slide.
                (!layout.singlePage || pageDimensions.pageIsWide(page)) &&
                !zoomPanEnabled
        val useCurl = usePortraitCurl || useSpreadCurl
        val initialZoomPanState = ReaderZoomPanState()

        LaunchedEffect(page, pages, usePortraitCurl, useSpreadCurl, rtl) {
            if (usePortraitCurl && pages > 0 && portraitCurlState.current != page) {
                portraitCurlState.snapTo(page)
            }
            if (useSpreadCurl && pages > 0 && spreadCurlState.current != ReaderSpreadCurlVisualCurrent) {
                spreadCurlState.snapTo(ReaderSpreadCurlVisualCurrent)
            }
        }

        LaunchedEffect(useCurl, rtl) {
            activeCurlDirection = null
            activeCurlTargetPage = null
            curlDragStartPointer = null
            curlDragProgress = 0f
            dragBoundaryDirection = null
            if (!useCurl) {
                return@LaunchedEffect
            }
            if (usePortraitCurl && pages > 0) {
                portraitCurlState.snapTo(page)
            }
            if (useSpreadCurl && pages > 0) {
                spreadCurlState.snapTo(ReaderSpreadCurlVisualCurrent)
            }
        }

        fun zoomTurnLandingOffsetX(direction: ReaderTurnDirection): Float {
            val physicalSign = readerTurnPhysicalSign(rtl, direction)
            return (-physicalSign * panBounds.maxX).coerceIn(-panBounds.maxX, panBounds.maxX)
        }

        fun rememberZoomTurnLanding(direction: ReaderTurnDirection) {
            if (zoomPan.userScale > 1f + ReaderZoomEpsilon) {
                pendingZoomLandingDirection = direction
            }
        }

        LaunchedEffect(page) {
            zoomAnimationJob?.cancel()
            closeAnimationJob?.cancel()
            val landingDirection = pendingZoomLandingDirection
            pendingZoomLandingDirection = null
            zoomPan = if (zoomPan.userScale > 1f + ReaderZoomEpsilon) {
                zoomPan.copy(
                    offsetX = landingDirection
                        ?.let(::zoomTurnLandingOffsetX)
                        ?: zoomPan.offsetX.coerceIn(-panBounds.maxX, panBounds.maxX),
                    offsetY = zoomPan.offsetY.coerceIn(-panBounds.maxY, panBounds.maxY)
                )
            } else {
                initialZoomPanState
            }
            closeDragOffsetY = 0f
        }

        fun turnTarget(
            direction: ReaderTurnDirection,
            step: Int,
            completeWhenPastEnd: Boolean
        ): Int? = readerTurnTargetPage(
            currentPage = page,
            pageCount = pages,
            direction = direction,
            step = step,
            completeWhenPastEnd = completeWhenPastEnd
        )

        fun runBoundaryAction(direction: ReaderTurnDirection, completeWhenPastEnd: Boolean) {
            val neighbors = readerChapterNeighbors(chapterSequence, currentChapterId)
            when (direction) {
                ReaderTurnDirection.Next -> if (completeWhenPastEnd) {
                    val next = neighbors.next
                    if (next == null) {
                        completeChapter()
                    } else {
                        completeChapter(exitAfter = false)
                        chapterBoundary = ReaderChapterBoundary(
                            direction = direction,
                            current = currentChapter,
                            neighbor = next
                        )
                    }
                }
                ReaderTurnDirection.Previous -> if (page == 0) {
                    val previous = neighbors.previous
                    if (previous == null) {
                        resetChapterAndExit()
                    } else {
                        resetChapterAndExit(exitAfter = false)
                        chapterBoundary = ReaderChapterBoundary(
                            direction = direction,
                            current = currentChapter,
                            neighbor = previous
                        )
                    }
                }
            }
        }

        fun continueFromChapterBoundary() {
            val boundary = chapterBoundary ?: return
            switchChapter(
                target = boundary.neighbor,
                openAtLastPage = boundary.direction == ReaderTurnDirection.Previous
            )
        }

        fun returnFromChapterBoundary() {
            if (chapterSwitching) return
            chapterBoundary = null
            boundaryDragDirection = null
            boundaryDragProgress = 0f
            completingRead = false
            error = null
        }

        fun turnChapterBoundary(direction: ReaderTurnDirection) {
            val boundary = chapterBoundary ?: return
            if (direction == boundary.direction) {
                continueFromChapterBoundary()
            } else {
                returnFromChapterBoundary()
            }
        }

        lateinit var requestTurn: (ReaderTurnDirection, Int, Boolean) -> Unit

        fun pageCurlDirection(direction: ReaderTurnDirection): PageCurlTurnDirection =
            when (direction) {
                ReaderTurnDirection.Next -> PageCurlTurnDirection.Forward
                ReaderTurnDirection.Previous -> PageCurlTurnDirection.Backward
            }

        fun curlPointer(position: Offset): Offset =
            if (rtl) Offset(viewportWidthPx - position.x, position.y) else position

        fun curlTurnStep(direction: ReaderTurnDirection): Int =
            if (useSpreadCurl) {
                if (direction == ReaderTurnDirection.Next) nextPageTurnStep else previousPageTurnStep
            } else {
                1
            }

        // A leaf can land on a spread or on a wide page (a full-width sheet), but not on a
        // centred landscape single (cover, chapter end, the lone page beside a wide one):
        // there is no spine under those, so such turns use the Slide path instead.
        fun curlTurnTargetEligible(target: Int): Boolean {
            if (!useSpreadCurl) return true
            val targetLayout = readerPageLayout(target, pages, portrait, pageDimensions, isEpub = isEpub)
            return !targetLayout.singlePage || pageDimensions.pageIsWide(target)
        }

        fun requestCurlTurn(
            direction: ReaderTurnDirection,
            step: Int,
            completeWhenPastEnd: Boolean,
            tapPosition: Offset? = null
        ) {
            // A slide transition (shift, boundary fallback) may be covering the curl;
            // queue the turn and replay it once the transition finishes.
            if (transitionSettling || activeTransition != null) {
                queuedTurn = PendingReaderTurn(direction, step, completeWhenPastEnd)
                return
            }
            val target = turnTarget(direction, step, completeWhenPastEnd)
            if (target == null) {
                runBoundaryAction(direction, completeWhenPastEnd)
                return
            }
            val curlState = if (useSpreadCurl) spreadCurlState else portraitCurlState
            val spreadCurl = useSpreadCurl
            activeCurlDirection = direction
            activeCurlTargetPage = target
            // The tap position picks which corner leads the fold (fork quietMiddle).
            val curlTapPosition = tapPosition?.let(::curlPointer)
            scope.launch {
                when (pageCurlDirection(direction)) {
                    PageCurlTurnDirection.Forward -> curlState.next(tapPosition = curlTapPosition)
                    PageCurlTurnDirection.Backward -> curlState.prev(tapPosition = curlTapPosition)
                }
                rememberZoomTurnLanding(direction)
                page = target
                if (spreadCurl) {
                    curlState.snapTo(ReaderSpreadCurlVisualCurrent)
                }
                activeCurlDirection = null
                activeCurlTargetPage = null
            }
        }

        fun settleTransition(
            commit: Boolean,
            completeWhenPastEnd: Boolean
        ) {
            val transition = activeTransition
            if (transition == null) {
                val boundaryDirection = dragBoundaryDirection
                dragBoundaryDirection = null
                if (commit && boundaryDirection != null) {
                    runBoundaryAction(boundaryDirection, completeWhenPastEnd)
                }
                transitionProgress = 0f
                return
            }

            if (!settings.reader.pageTransitionAnimation) {
                if (commit) {
                    rememberZoomTurnLanding(transition.direction)
                    page = transition.targetPage
                }
                activeTransition = null
                transitionProgress = 0f
                transitionSettling = false
                return
            }

            transitionSettling = true
            scope.launch {
                val targetProgress = if (commit) 1f else 0f
                Animatable(transitionProgress).animateTo(
                    targetValue = targetProgress,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ) {
                    transitionProgress = value
                }
                if (commit) {
                    rememberZoomTurnLanding(transition.direction)
                    page = transition.targetPage
                    withFrameNanos { }
                }
                activeTransition = null
                transitionProgress = 0f
                transitionSettling = false
            }
        }

        fun requestSlideTurn(direction: ReaderTurnDirection, step: Int, completeWhenPastEnd: Boolean) {
            if (transitionSettling || activeTransition != null) {
                queuedTurn = PendingReaderTurn(direction, step, completeWhenPastEnd, slideOnly = true)
                return
            }
            val target = turnTarget(direction, step, completeWhenPastEnd)
            if (target == null) {
                runBoundaryAction(direction, completeWhenPastEnd)
                return
            }
            if (!settings.reader.pageTransitionAnimation) {
                if (zoomPan.userScale > 1f + ReaderZoomEpsilon) {
                    pendingZoomLandingDirection = direction
                }
                page = target
                return
            }
            activeTransition = ReaderPageTransition(
                outgoingPage = page,
                targetPage = target,
                direction = direction,
                distanceFraction = readerSlideDistanceFraction(step, portrait, layout.singlePage)
            )
            transitionProgress = 0f
            settleTransition(commit = true, completeWhenPastEnd = completeWhenPastEnd)
        }

        fun curlRouteForTurn(direction: ReaderTurnDirection, step: Int, completeWhenPastEnd: Boolean): Boolean {
            if (!(useCurl && (useSpreadCurl || step == 1))) return false
            val target = turnTarget(direction, step, completeWhenPastEnd)
            // Boundary turns (null target) stay on the curl path for the edge resistance.
            return target == null || curlTurnTargetEligible(target)
        }

        requestTurn = requestTurnLambda@{ direction, step, completeWhenPastEnd ->
            if (curlRouteForTurn(direction, step, completeWhenPastEnd)) {
                requestCurlTurn(direction, step, completeWhenPastEnd)
                return@requestTurnLambda
            }
            requestSlideTurn(direction, step, completeWhenPastEnd)
        }

        fun requestTurnFromTap(
            direction: ReaderTurnDirection,
            step: Int,
            completeWhenPastEnd: Boolean,
            tapPosition: Offset
        ) {
            if (curlRouteForTurn(direction, step, completeWhenPastEnd)) {
                requestCurlTurn(direction, step, completeWhenPastEnd, tapPosition)
            } else {
                requestSlideTurn(direction, step, completeWhenPastEnd)
            }
        }

        // Spread shift (long-press / menu ±1) animates as a Slide transition in both
        // modes: in Curl mode it draws over the still-mounted PageCurl, so there is no
        // unmount/remount churn (the original cause of the image thrash).
        fun requestSingleStep(direction: ReaderTurnDirection, completeWhenPastEnd: Boolean) {
            requestSlideTurn(direction, 1, completeWhenPastEnd)
        }

        LaunchedEffect(activeTransition, transitionSettling, queuedTurn, page) {
            val pending = queuedTurn
            if (activeTransition == null && !transitionSettling && pending != null) {
                queuedTurn = null
                if (pending.slideOnly) {
                    requestSlideTurn(pending.direction, pending.step, pending.completeWhenPastEnd)
                } else {
                    requestTurn(pending.direction, pending.step, pending.completeWhenPastEnd)
                }
            }
        }

        fun beginTurnDrag(direction: ReaderTurnDirection, pointer: Offset) {
            if (useCurl) {
                curlDragStartPointer = pointer
                curlDragProgress = 0f
                return
            }
        }

        fun updateTurnDrag(direction: ReaderTurnDirection, progress: Float, pointer: Offset) {
            if (useCurl) {
                // Ignore new curl drags only while a SETTLING slide transition covers the
                // curl (shift / tap fallback animating, a few hundred ms). A drag-driven
                // fallback transition has transitionSettling == false and must keep
                // receiving its own drag frames through the fall-through below.
                if (activeCurlDirection == null && transitionSettling) return
                var fallThroughToSlide = false
                if (activeCurlDirection != direction) {
                    val step = curlTurnStep(direction)
                    val target = turnTarget(direction, step, direction == ReaderTurnDirection.Next && showingFinalPage)
                    if (target != null && !curlTurnTargetEligible(target)) {
                        // Turning towards a centred landscape single: this drag runs as a
                        // Slide transition below instead of a leaf curl.
                        fallThroughToSlide = true
                    } else {
                    dragBoundaryDirection = if (target == null) direction else null
                    activeCurlDirection = direction
                    activeCurlTargetPage = target
                    val start = curlDragStartPointer ?: pointer
                    if (target != null) {
                        val curlState = if (useSpreadCurl) spreadCurlState else portraitCurlState
                        val spreadCurl = useSpreadCurl
                        // The spike validated PageEdge for the spread leaf (paper edge anchored
                        // to the finger, crease at the midpoint, stops at the spine on its own)
                        // and the library default for portrait single pages (Stage 1 feel).
                        val pointerBehavior = if (spreadCurl) {
                            PageCurlConfig.DragInteraction.PointerBehavior.PageEdge
                        } else {
                            PageCurlConfig.DragInteraction.PointerBehavior.Default
                        }
                        scope.launch {
                            if (spreadCurl) {
                                curlState.snapTo(ReaderSpreadCurlVisualCurrent)
                            }
                            if (curlState.beginTurn(pageCurlDirection(direction), curlPointer(start), pointerBehavior)) {
                                curlState.dragTurnTo(curlPointer(pointer))
                            }
                        }
                    }
                    }
                } else {
                    val curlState = if (useSpreadCurl) spreadCurlState else portraitCurlState
                    scope.launch {
                        curlState.dragTurnTo(curlPointer(pointer))
                    }
                }
                if (!fallThroughToSlide) {
                    curlDragProgress = progress
                    return
                }
            }
            if (transitionSettling) return
            if (activeTransition?.direction != direction) {
                val step = if (direction == ReaderTurnDirection.Next) {
                    nextPageTurnStep
                } else {
                    previousPageTurnStep
                }
                val completeAtBoundary = direction == ReaderTurnDirection.Next && showingFinalPage
                val target = turnTarget(direction, step, completeAtBoundary)
                activeTransition = target?.let {
                    ReaderPageTransition(
                        page,
                        it,
                        direction,
                        distanceFraction = readerSlideDistanceFraction(step, portrait, layout.singlePage)
                    )
                }
                dragBoundaryDirection = if (target == null) direction else null
            }
            transitionProgress = progress
        }

        fun settleTurnDrag(velocityX: Float) {
            val curlDirection = activeCurlDirection
            if (useCurl && curlDirection != null) {
                val commit = shouldCommitReaderTurn(
                    progress = curlDragProgress,
                    velocityX = velocityX,
                    direction = curlDirection,
                    rightToLeft = rtl,
                    minimumFlingVelocity = minimumFlingVelocity
                )
                val boundaryDirection = dragBoundaryDirection
                val step = curlTurnStep(curlDirection)
                val target = turnTarget(
                    curlDirection,
                    step,
                    curlDirection == ReaderTurnDirection.Next && showingFinalPage
                )
                val curlState = if (useSpreadCurl) spreadCurlState else portraitCurlState
                val spreadCurl = useSpreadCurl
                activeCurlDirection = null
                curlDragStartPointer = null
                curlDragProgress = 0f
                dragBoundaryDirection = null
                // Keep the in-flight target through the settle animation (commit or cancel):
                // the revealed area under the flap must keep showing the incoming spread
                // while the paper returns, or the content pops at release.
                activeCurlTargetPage = target
                if (commit && boundaryDirection != null) {
                    activeCurlTargetPage = null
                    runBoundaryAction(
                        boundaryDirection,
                        completeWhenPastEnd = boundaryDirection == ReaderTurnDirection.Next && showingFinalPage
                    )
                    return
                }
                scope.launch {
                    curlState.settleTurn(commit)
                    if (commit) {
                        rememberZoomTurnLanding(curlDirection)
                        target?.let { page = it }
                    }
                    if (spreadCurl) {
                        curlState.snapTo(ReaderSpreadCurlVisualCurrent)
                    }
                    activeCurlTargetPage = null
                }
                return
            }
            settleTransition(
                commit = shouldCommitReaderTurn(
                    progress = transitionProgress,
                    velocityX = velocityX,
                    direction = activeTransition?.direction ?: dragBoundaryDirection,
                    rightToLeft = rtl,
                    minimumFlingVelocity = minimumFlingVelocity
                ),
                completeWhenPastEnd = dragBoundaryDirection == ReaderTurnDirection.Next &&
                    showingFinalPage
            )
        }

        fun cancelTurnDrag() {
            if (useCurl && activeCurlDirection != null) {
                val curlState = if (useSpreadCurl) spreadCurlState else portraitCurlState
                val spreadCurl = useSpreadCurl
                activeCurlDirection = null
                // activeCurlTargetPage stays as set by the drag so the revealed area keeps
                // showing the incoming spread while the paper settles back; cleared below.
                curlDragStartPointer = null
                curlDragProgress = 0f
                dragBoundaryDirection = null
                scope.launch {
                    curlState.settleTurn(commit = false)
                    if (spreadCurl) {
                        curlState.snapTo(ReaderSpreadCurlVisualCurrent)
                    }
                    activeCurlTargetPage = null
                }
                return
            }
            settleTransition(commit = false, completeWhenPastEnd = false)
        }

        LaunchedEffect(
            currentChapterId,
            activeTransition?.targetPage,
            pages,
            portrait,
            pageDimensions,
            offlineChapter,
            activeImageLoader,
            invertMode,
            settings.reader.invertWhiteThreshold
        ) {
            val targetPage = activeTransition?.targetPage ?: return@LaunchedEffect
            if (offlineChapter != null || pages <= 0) return@LaunchedEffect
            val targets = prefetchTargetsFor(
                readerVisiblePageIndices(
                    page = targetPage,
                    pageCount = pages,
                    portrait = portrait,
                    pageDimensions = pageDimensions
                ),
                forceMemory = true
            )
            prefetchReaderPages(
                context = ctx,
                imageLoader = activeImageLoader,
                targets = targets
            )
            if (invertMode == InvertMode.Smart) {
                preAnalyzeReaderPages(
                    context = ctx,
                    imageLoader = activeImageLoader,
                    models = targets.map { it.model }.distinct(),
                    whiteThreshold = settings.reader.invertWhiteThreshold,
                    invertDecisionCache = invertDecisionCache
                )
            }
        }

        if (error != null) {
            Text("Error: $error", color = Color.Red, modifier = Modifier.padding(12.dp))
        }

        val transition = activeTransition
        val transitionVisible =
            transition != null && settings.reader.pageTransitionAnimation
        Box(
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .graphicsLayer {
                    val safeViewportHeight = viewportHeightPx.coerceAtLeast(1f)
                    translationY = closeDragOffsetY
                    alpha = (1f - closeDragOffsetY / safeViewportHeight * 0.35f).coerceIn(0.65f, 1f)
                }
        ) {
            if (vertical) {
                val neighbors = readerChapterNeighbors(chapterSequence, currentChapterId)
                val previousBoundary = neighbors.previous?.let { previous ->
                    ReaderChapterBoundary(
                        direction = ReaderTurnDirection.Previous,
                        current = currentChapter,
                        neighbor = previous
                    )
                }
                val nextBoundary = neighbors.next?.let { next ->
                    ReaderChapterBoundary(
                        direction = ReaderTurnDirection.Next,
                        current = currentChapter,
                        neighbor = next
                    )
                }
                ReaderVerticalScroll(
                    pageCount = pages,
                    pageDimensions = pageDimensions,
                    pageModel = ::pageModel,
                    imageLoader = activeImageLoader,
                    invertMode = invertMode,
                    whiteThreshold = settings.reader.invertWhiteThreshold,
                    invertDecisionCache = invertDecisionCache,
                    pageBackground = readerPageBackground,
                    viewportHeight = viewportHeight,
                    fallbackPageAspectRatio = (viewportWidthPx / viewportHeightPx.coerceAtLeast(1f))
                        .coerceAtLeast(0.01f),
                    listState = verticalListState,
                    previousBoundary = previousBoundary,
                    nextBoundary = nextBoundary,
                    seriesName = seriesName,
                    chapterSwitching = chapterSwitching,
                    boundaryHasError = error != null,
                    onCurrentPageChanged = { currentPage ->
                        if (page != currentPage) page = currentPage
                        if (completingRead) completingRead = false
                    },
                    onBoundaryReached = { direction ->
                        if (verticalBoundariesEnabled && !completingRead) {
                            when (direction) {
                                ReaderTurnDirection.Next -> {
                                    if (neighbors.next == null) completeChapter()
                                    else completeChapter(exitAfter = false)
                                }
                                ReaderTurnDirection.Previous -> {
                                    if (neighbors.previous == null) resetChapterAndExit()
                                    else resetChapterAndExit(exitAfter = false)
                                }
                            }
                        }
                    },
                    onContinueBoundary = { direction ->
                        val neighbor = when (direction) {
                            ReaderTurnDirection.Next -> neighbors.next
                            ReaderTurnDirection.Previous -> neighbors.previous
                        }
                        neighbor?.let { target ->
                            switchChapter(
                                target = target,
                                openAtLastPage = direction == ReaderTurnDirection.Previous
                            )
                        }
                    },
                    onBackToSeries = onBack,
                    onMenuToggle = { showReaderMenu = !showReaderMenu },
                    epubSubpages = epubSubpages,
                    epubFontSizeSp = epubFontSizeSp
                )
            } else {
            // Keep PageCurl mounted whenever the curl mode is active (not just during a
            // turn): the neighbour pages it composes at rest are exactly the images the
            // next turn needs, so the gesture starts warm instead of kicking off image
            // loads on its first frame. Slide transitions (shift, boundary fallbacks) draw
            // on top of it rather than unmounting it — the remount recomposition is what
            // made the images thrash.
            if (usePortraitCurl) {
                val curlMirror = if (rtl) -1f else 1f
                PageCurl(
                    count = pages,
                    state = portraitCurlState,
                    config = portraitCurlConfig,
                    interactionsEnabled = false,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = curlMirror
                        }
                ) { cursor ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = curlMirror
                            }
                    ) {
                        RenderReaderPage(
                            cursor = cursor,
                            pageCount = pages,
                            portrait = portrait,
                            pageDimensions = pageDimensions,
                            rightToLeft = rtl,
                            pageModel = ::pageModel,
                            imageLoader = activeImageLoader,
                            invertMode = invertMode,
                            whiteThreshold = settings.reader.invertWhiteThreshold,
                            invertDecisionCache = invertDecisionCache,
                            pageBackground = curlBackPageColor,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            } else if (useSpreadCurl) {
                val curlMirror = if (rtl) -1f else 1f
                // During a turn the under/flap pages come from the in-flight target; at rest
                // they pre-render the forward target spread (the likely next turn), which
                // warms its images while the reader sits on the current spread.
                val forwardRestTargetPage = turnTarget(ReaderTurnDirection.Next, nextPageTurnStep, false) ?: page
                val underPage = activeCurlTargetPage ?: forwardRestTargetPage
                PageCurl(
                    count = ReaderSpreadCurlVisualPageCount,
                    state = spreadCurlState,
                    config = spreadCurlConfig,
                    interactionsEnabled = false,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = curlMirror
                        },
                    backContent = { _, forward ->
                        val targetIsWide = pageDimensions.pageIsWide(underPage)
                        val backPage = readerSpreadCurlBackPageIndex(
                            targetPage = underPage,
                            pageCount = pages,
                            direction = if (forward) ReaderTurnDirection.Next else ReaderTurnDirection.Previous,
                            targetIsWide = targetIsWide
                        )
                        if (targetIsWide) {
                            // A wide page lands spanning the whole spread, so its back-face
                            // content is laid out full width; the fold clip reveals the near
                            // half of the artwork as the leaf turns.
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = curlMirror
                                    }
                            ) {
                                RenderReaderPage(
                                    cursor = backPage,
                                    pageCount = pages,
                                    portrait = false,
                                    pageDimensions = pageDimensions,
                                    rightToLeft = rtl,
                                    pageModel = ::pageModel,
                                    imageLoader = activeImageLoader,
                                    invertMode = invertMode,
                                    whiteThreshold = settings.reader.invertWhiteThreshold,
                                    invertDecisionCache = invertDecisionCache,
                                    pageBackground = curlBackPageColor,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        } else {
                        val backPageAlignment = if (forward == rtl) {
                            Alignment.CenterStart
                        } else {
                            Alignment.CenterEnd
                        }
                        Box(Modifier.fillMaxSize()) {
                            Box(
                                Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(0.5f)
                                    .align(if (forward) Alignment.CenterStart else Alignment.CenterEnd)
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            scaleX = curlMirror
                                        }
                                ) {
                                    RenderReaderPage(
                                        cursor = backPage,
                                        pageCount = pages,
                                        portrait = true,
                                        pageDimensions = pageDimensions,
                                        rightToLeft = rtl,
                                        pageModel = ::pageModel,
                                        imageLoader = activeImageLoader,
                                        invertMode = invertMode,
                                        whiteThreshold = settings.reader.invertWhiteThreshold,
                                        invertDecisionCache = invertDecisionCache,
                                        pageBackground = curlBackPageColor,
                                        singlePageAlignmentOverride = backPageAlignment,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                        }
                    }
                ) { cursor ->
                    val renderPage = if (cursor != spreadCurlState.current) {
                        underPage
                    } else {
                        page
                    }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = curlMirror
                            }
                    ) {
                        RenderReaderPage(
                            cursor = renderPage,
                            pageCount = pages,
                            portrait = portrait,
                            pageDimensions = pageDimensions,
                            rightToLeft = rtl,
                            pageModel = ::pageModel,
                            imageLoader = activeImageLoader,
                            invertMode = invertMode,
                            whiteThreshold = settings.reader.invertWhiteThreshold,
                            invertDecisionCache = invertDecisionCache,
                            pageBackground = curlBackPageColor,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            } else if (!transitionVisible) {
                RenderReaderPage(
                    cursor = page,
                    pageCount = pages,
                    portrait = portrait,
                    pageDimensions = pageDimensions,
                    rightToLeft = rtl,
                    pageModel = ::pageModel,
                    imageLoader = activeImageLoader,
                    invertMode = invertMode,
                    whiteThreshold = settings.reader.invertWhiteThreshold,
                    invertDecisionCache = invertDecisionCache,
                    pageBackground = readerPageBackground,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = zoomPan.offsetX
                            translationY = zoomPan.offsetY
                            scaleX = totalZoomScale
                            scaleY = totalZoomScale
                        }
                )
            }
            // The slide transition draws on top of whichever base is composed (a mounted
            // curl or nothing), fully covering it with its two opaque pages.
            if (transitionVisible) {
                requireNotNull(transition)
                val progress = transitionProgress.coerceIn(0f, 1f)
                val physicalSign = readerTurnPhysicalSign(rtl, transition.direction)
                val slideDistancePx = viewportWidthPx * transition.distanceFraction
                val targetOffsetX = if (zoomPanEnabled) {
                    zoomTurnLandingOffsetX(transition.direction)
                } else {
                    zoomPan.offsetX
                }
                if (transition.distanceFraction < 1f && !zoomPanEnabled) {
                    // Spread shift: the two whole-spread layers cannot slide seamlessly
                    // because each hugs the pages to its own spine — at the layer seam the
                    // outer margins meet and open a gap at the spine. Instead draw one
                    // paper background and the three page images (enter / stay / exit)
                    // glued edge-to-edge, all travelling by the staying page's rendered
                    // width; the leaving page fades out.
                    fun halfPageWidthPx(pageIndex: Int): Float {
                        val dims = pageDimensions[pageIndex] ?: return viewportWidthPx / 2f
                        val w = dims.width?.toFloat() ?: return viewportWidthPx / 2f
                        val h = dims.height?.toFloat() ?: return viewportWidthPx / 2f
                        if (w <= 0f || h <= 0f) return viewportWidthPx / 2f
                        return minOf(viewportWidthPx / 2f, viewportHeightPx * (w / h))
                    }

                    val (enterPage, stayPage, exitPage) = readerShiftStripPages(
                        outgoingPage = transition.outgoingPage,
                        targetPage = transition.targetPage,
                        direction = transition.direction
                    )
                    val geometry = readerShiftStripGeometry(
                        physicalSign = physicalSign,
                        halfViewportPx = viewportWidthPx / 2f,
                        stayWidthPx = halfPageWidthPx(stayPage),
                        enterWidthPx = halfPageWidthPx(enterPage),
                        exitWidthPx = halfPageWidthPx(exitPage)
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(readerPageBackground)
                    ) {
                        listOf(
                            // The entering page starts glued to the staying page, which puts
                            // a sliver of it inside the resting view's outer margin; fade it
                            // in over the first quarter so it does not pop into existence.
                            Triple(enterPage, geometry.enterStartLeftPx, (progress * 4f).coerceAtMost(1f)),
                            Triple(stayPage, geometry.stayStartLeftPx, 1f),
                            Triple(exitPage, geometry.exitStartLeftPx, 1f - progress)
                        ).forEach { (stripPage, startLeftPx, pageAlpha) ->
                            if (stripPage in 0 until pages) {
                                val widthDp = with(density) { halfPageWidthPx(stripPage).toDp() }
                                key(stripPage) {
                                    Box(
                                        Modifier
                                            .fillMaxHeight()
                                            .width(widthDp)
                                            .graphicsLayer {
                                                translationX = startLeftPx + geometry.travelPx * progress
                                                alpha = pageAlpha
                                            }
                                    ) {
                                        RenderReaderPage(
                                            cursor = stripPage,
                                            pageCount = pages,
                                            portrait = true,
                                            pageDimensions = pageDimensions,
                                            rightToLeft = rtl,
                                            pageModel = ::pageModel,
                                            imageLoader = activeImageLoader,
                                            invertMode = invertMode,
                                            whiteThreshold = settings.reader.invertWhiteThreshold,
                                            invertDecisionCache = invertDecisionCache,
                                            pageBackground = readerPageBackground,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                RenderReaderPage(
                    cursor = transition.targetPage,
                    pageCount = pages,
                    portrait = portrait,
                    pageDimensions = pageDimensions,
                    rightToLeft = rtl,
                    pageModel = ::pageModel,
                    imageLoader = activeImageLoader,
                    invertMode = invertMode,
                    whiteThreshold = settings.reader.invertWhiteThreshold,
                    invertDecisionCache = invertDecisionCache,
                    pageBackground = readerPageBackground,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = -physicalSign * slideDistancePx * (1f - progress) + targetOffsetX
                            translationY = zoomPan.offsetY
                            scaleX = totalZoomScale
                            scaleY = totalZoomScale
                        }
                )
                RenderReaderPage(
                    cursor = transition.outgoingPage,
                    pageCount = pages,
                    portrait = portrait,
                    pageDimensions = pageDimensions,
                    rightToLeft = rtl,
                    pageModel = ::pageModel,
                    imageLoader = activeImageLoader,
                    invertMode = invertMode,
                    whiteThreshold = settings.reader.invertWhiteThreshold,
                    invertDecisionCache = invertDecisionCache,
                    pageBackground = readerPageBackground,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = physicalSign * slideDistancePx * progress + zoomPan.offsetX
                            translationY = zoomPan.offsetY
                            scaleX = totalZoomScale
                            scaleY = totalZoomScale
                        }
                )
                }
            }
            }
        }

        if (!vertical && chapterBoundary == null) {
            key(page, rtl, nextPageTurnStep, previousPageTurnStep, showingFinalPage) {
                ReaderTapLayer(
                rightToLeft = rtl,
                onNextSpread = { position ->
                    requestTurnFromTap(ReaderTurnDirection.Next, nextPageTurnStep, showingFinalPage, position)
                },
                onPreviousSpread = { position ->
                    requestTurnFromTap(ReaderTurnDirection.Previous, previousPageTurnStep, false, position)
                },
                onNextSingle = {
                    requestSingleStep(ReaderTurnDirection.Next, page >= pages - 1)
                },
                onPreviousSingle = {
                    requestSingleStep(ReaderTurnDirection.Previous, false)
                },
                onCenterTap = { showReaderMenu = !showReaderMenu },
                turnVisualDistancePx = turnVisualDistancePx,
                zoomPanEnabled = zoomPanEnabled,
                panOffsetX = zoomPan.offsetX,
                panOffsetY = zoomPan.offsetY,
                panMaxX = panBounds.maxX,
                panMaxY = panBounds.maxY,
                onPan = { x, y ->
                    zoomAnimationJob?.cancel()
                    zoomPan = zoomPan.copy(offsetX = x, offsetY = y)
                },
                onTurnDragStart = ::beginTurnDrag,
                onTurnDrag = ::updateTurnDrag,
                onTurnDragEnd = ::settleTurnDrag,
                onTurnDragCancel = ::cancelTurnDrag,
                directionLockEnabled = useCurl,
                closeSwipeEnabled = activeTransition == null &&
                    activeCurlDirection == null &&
                    !transitionSettling &&
                    !showReaderMenu,
                closeVisualDistancePx = viewportHeightPx,
                onCloseDrag = { offset ->
                    closeAnimationJob?.cancel()
                    closeDragOffsetY = offset
                },
                onCloseDragEnd = { commit ->
                    closeAnimationJob?.cancel()
                    closeAnimationJob = scope.launch {
                        val start = closeDragOffsetY
                        val target = if (commit) viewportHeightPx else 0f
                        Animatable(start).animateTo(
                            targetValue = target,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        ) {
                            closeDragOffsetY = value
                        }
                        if (commit) {
                            onBack()
                        } else {
                            closeDragOffsetY = 0f
                        }
                    }
                },
                onCloseDragCancel = {
                    closeAnimationJob?.cancel()
                    closeAnimationJob = scope.launch {
                        Animatable(closeDragOffsetY).animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        ) {
                            closeDragOffsetY = value
                        }
                        closeDragOffsetY = 0f
                    }
                },
                onDoubleTap = {
                    val start = zoomPan
                    val target = start.withDoubleTapZoom(
                        tapPosition = it,
                        baseZoomScale = baseZoomScale,
                        viewportWidthPx = viewportWidthPx,
                        viewportHeightPx = viewportHeightPx,
                        initialState = initialZoomPanState
                    )
                    zoomAnimationJob?.cancel()
                    zoomAnimationJob = scope.launch {
                        Animatable(0f).animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ) {
                            zoomPan = start.lerpTo(target, value)
                        }
                        zoomPan = target
                    }
                },
                onTransform = { zoomChange, panChange, focalPoint ->
                    zoomAnimationJob?.cancel()
                    zoomPan = zoomPan.withTransform(
                        zoomChange = zoomChange,
                        panChange = panChange,
                        focalPoint = focalPoint,
                        baseZoomScale = baseZoomScale,
                        viewportWidthPx = viewportWidthPx,
                        viewportHeightPx = viewportHeightPx
                    )
                }
                )
            }
        } else if (!vertical) {
            ReaderTapLayer(
                rightToLeft = rtl,
                onNextSpread = { turnChapterBoundary(ReaderTurnDirection.Next) },
                onPreviousSpread = { turnChapterBoundary(ReaderTurnDirection.Previous) },
                onNextSingle = { turnChapterBoundary(ReaderTurnDirection.Next) },
                onPreviousSingle = { turnChapterBoundary(ReaderTurnDirection.Previous) },
                onCenterTap = {},
                turnVisualDistancePx = turnVisualDistancePx,
                onTurnDragStart = { direction, _ ->
                    boundaryDragDirection = direction
                    boundaryDragProgress = 0f
                },
                onTurnDrag = { direction, progress, _ ->
                    boundaryDragDirection = direction
                    boundaryDragProgress = progress
                },
                onTurnDragEnd = { velocityX ->
                    val direction = boundaryDragDirection
                    if (direction != null && shouldCommitReaderTurn(
                            progress = boundaryDragProgress,
                            velocityX = velocityX,
                            direction = direction,
                            rightToLeft = rtl,
                            minimumFlingVelocity = minimumFlingVelocity
                        )) {
                        turnChapterBoundary(direction)
                    }
                    boundaryDragDirection = null
                    boundaryDragProgress = 0f
                },
                onTurnDragCancel = {
                    boundaryDragDirection = null
                    boundaryDragProgress = 0f
                }
            )
        }

        chapterBoundary?.let { boundary ->
            ReaderChapterBoundaryScreen(
                seriesName = seriesName,
                boundary = boundary,
                switching = chapterSwitching,
                hasError = error != null,
                onContinue = ::continueFromChapterBoundary,
                onBackToSeries = onBack
            )
        }

        if (showReaderMenu && chapterBoundary == null) {
            ReaderMenuOverlay(
                seriesName = seriesName,
                chapterName = currentChapter.displayName,
                page = page,
                pages = pages,
                readingDirection = readingDirection,
                showSpreadShift = !vertical && !portrait && settings.reader.showSpreadShiftButtons,
                onBack = onBack,
                onDismiss = { showReaderMenu = false },
                onSetReadingDirection = { direction ->
                    // Per-book session override only. The global fallback lives in Reader
                    // Settings; a per-series direction lives on the Kavita server. This
                    // toggle flips the current reading session without writing either, so it
                    // never silently changes other books or fights the server value.
                    readingDirection = direction
                    sessionPreferenceKey?.let { key ->
                        ReaderSessionPreferenceCache.put(
                            key = key,
                            preferences = ReaderSessionPreferences(direction, invertMode),
                            nowMillis = System.currentTimeMillis()
                        )
                        ReaderExitWriteScope.launch {
                            ReaderSessionPreferenceCache.persist(ctx.cacheDir)
                        }
                    }
                },
                invertMode = invertMode,
                onSetInvertMode = { mode ->
                    // Per-book session override only (see the invertMode declaration above);
                    // does not write global Reader Settings.
                    invertMode = mode
                    sessionPreferenceKey?.let { key ->
                        ReaderSessionPreferenceCache.put(
                            key = key,
                            preferences = ReaderSessionPreferences(readingDirection, mode),
                            nowMillis = System.currentTimeMillis()
                        )
                        ReaderExitWriteScope.launch {
                            ReaderSessionPreferenceCache.persist(ctx.cacheDir)
                        }
                    }
                },
                onNextSingle = {
                    requestSingleStep(ReaderTurnDirection.Next, page >= pages - 1)
                },
                onPreviousSingle = {
                    requestSingleStep(ReaderTurnDirection.Previous, false)
                },
                onJumpToPage = { targetPage ->
                    jumpToPage(targetPage)
                    if (vertical) {
                        scope.launch { verticalListState.scrollToItem(targetPage + 1) }
                    }
                },
                isEpub = isEpub,
                epubFontSizeSp = epubFontSizeSp,
                onSetEpubFontSizeSp = { newSize ->
                    scope.launch { settingsStore.setEpubFontSizeSp(newSize) }
                }
            )
        }

    }
}

