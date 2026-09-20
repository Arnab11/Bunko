package com.bunko.reader.reader

import android.os.SystemClock
import android.view.KeyEvent
import android.view.ViewConfiguration
import com.bunko.reader.MainActivity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
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
import com.bunko.reader.reader.internal.PlayCurlPage
import com.bunko.reader.reader.internal.rememberPlayCurlHostState
import com.bunko.reader.AppSettings
import com.bunko.reader.AppSettingsStore
import com.bunko.reader.EPaperMode
import com.bunko.reader.FileDimensionDto
import com.bunko.reader.InvertMode
import com.bunko.reader.PageBackground
import com.bunko.reader.PageLayoutMode
import com.bunko.reader.KavitaApi
import com.bunko.reader.KavitaClient
import com.bunko.reader.BunkoLog
import com.bunko.reader.KavitaSession
import com.bunko.reader.KavitaSessionStore
import com.bunko.reader.MarkChapterReadDto
import com.bunko.reader.MarkVolumesReadDto
import com.bunko.reader.PageTurnMode
import com.bunko.reader.ProgressDto
import com.bunko.reader.ReaderNavigationMode
import com.bunko.reader.ReaderReadingDirection
import com.bunko.reader.download.OfflineChapter
import com.bunko.reader.download.OfflineIssueRepository
import com.bunko.reader.MangaFormat
import com.bunko.reader.SeriesMetadataDto
import com.bunko.reader.reader.internal.ReaderLoadingScreen
import com.bunko.reader.reader.internal.readerLoadErrorMessage
import com.bunko.reader.reader.internal.ReaderInvertCacheKey
import com.bunko.reader.reader.internal.ReaderPrefetchTarget
import com.bunko.reader.reader.internal.ReaderFullscreenEffect
import com.bunko.reader.reader.internal.ReaderBottomStatusBar
import com.bunko.reader.reader.internal.ReaderChapterBoundary
import com.bunko.reader.reader.internal.ReaderChapterBoundaryScreen
import com.bunko.reader.reader.internal.ReaderChapterEntry
import com.bunko.reader.reader.internal.ReaderMenuOverlay
import com.bunko.reader.reader.internal.ReaderOverviewGallery
import com.bunko.reader.reader.internal.readerOverviewCursors
import com.bunko.reader.reader.internal.ReaderPageView
import com.bunko.reader.reader.internal.ReaderTapLayer
import com.bunko.reader.reader.internal.ReaderTapZoneOverlay
import com.bunko.reader.reader.internal.ReaderVerticalScroll
import com.bunko.reader.reader.internal.ReaderWebtoonDetector
import com.bunko.reader.reader.internal.ReaderZoomEpsilon
import com.bunko.reader.reader.internal.ReaderZoomPanState
import com.bunko.reader.reader.internal.lerpTo
import com.bunko.reader.reader.internal.pageIsWide
import com.bunko.reader.reader.internal.preAnalyzeReaderPages
import com.bunko.reader.reader.internal.prefetchReaderPages
import com.bunko.reader.reader.internal.readerPageLayout
import com.bunko.reader.reader.internal.readerPanBoundsPx
import com.bunko.reader.reader.internal.readerEstimatedDecodeBytes
import com.bunko.reader.reader.internal.readerChapterEntry
import com.bunko.reader.reader.internal.readerChapterNeighbors
import com.bunko.reader.reader.internal.readerChapterSequence
import com.bunko.reader.reader.internal.readerPrefetchMemoryPlan
import com.bunko.reader.reader.internal.readerPrefetchPageIndicesAround
import com.bunko.reader.reader.internal.readerPrefetchSlotWidthPx
import com.bunko.reader.reader.internal.readerVerticalPrefetchPageIndicesAround
import com.bunko.reader.reader.internal.readerVerticalPrefetchSize
import com.bunko.reader.reader.internal.readerVisiblePageIndices
import com.bunko.reader.reader.internal.spreadPagesFor
import com.bunko.reader.reader.internal.toPageDimensionMap
import com.bunko.reader.reader.internal.withDoubleTapZoom
import com.bunko.reader.reader.internal.withTransform
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import com.bunko.reader.reader.internal.EpubBlock
import com.bunko.reader.reader.internal.EpubSubpage
import com.bunko.reader.reader.internal.ReaderEpubPageView
import com.bunko.reader.reader.internal.ReaderEpubPaginator
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
import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import com.bunko.reader.download.OfflineIssueRecord
import com.bunko.reader.download.OfflinePage
import com.bunko.reader.download.compareNaturalFileNames
import com.bunko.reader.engine.ReaderDocumentFactory
import com.bunko.reader.engine.model.ComicPageResource
import com.bunko.reader.engine.model.ReaderDocument
import com.bunko.reader.offline.LocalBookFormat
import com.bunko.reader.offline.LocalBookRepository
import java.io.File

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
internal const val ReaderPortraitBackPageContentAlpha = 0.05f
private const val ReaderSpreadCurlVisualPageCount = 3
private const val ReaderSpreadCurlVisualCurrent = 1
private const val ReaderSpreadCurlTurnEndFractionX = 0.5f

internal fun readerPageBackgroundColor(
    darkPaper: Boolean,
    usePureColors: Boolean,
    themePaperColor: Color? = null
): Color = when {
    darkPaper && usePureColors -> Color.Black
    darkPaper -> Color(0xFF101010)
    usePureColors -> Color.White
    else -> themePaperColor ?: Color(0xFFFAF7F2)
}

private val LocalImageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "avif")

internal fun readerPortraitBackPageContentAlpha(showContent: Boolean): Float {
    return if (showContent) ReaderPortraitBackPageContentAlpha else 0f
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalPageCurlApi::class)
@Composable
fun ReaderScreen(
    sessionStore: KavitaSessionStore,
    settingsStore: AppSettingsStore,
    libraryId: Int = 0,
    seriesId: Int = 0,
    volumeId: Int = 0,
    chapterId: Int = 0,
    localBookId: String? = null,
    localRepository: LocalBookRepository? = null,
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
    // Mihon-style tap-zone preview, shown when the zones change in the dialog.
    var tapZoneOverlayVisible by remember { mutableStateOf(false) }
    var readingDirection by remember { mutableStateOf(settings.reader.readingDirection) }
    // Per-book overrides survive a short close/reopen cycle in process memory. The server
    // direction and global Reader setting remain authoritative after the cache expires.
    var invertMode by remember { mutableStateOf(settings.reader.invertMode) }
    var ePaperMode by remember { mutableStateOf(settings.reader.ePaperMode) }
    var readerBrightness by remember { mutableFloatStateOf(settings.reader.readerBrightness) }
    var nightModeEnabled by remember { mutableStateOf(settings.reader.nightModeEnabled) }
    var nightLightIntensity by remember { mutableFloatStateOf(settings.reader.nightLightIntensity) }
    ReaderFullscreenEffect(
        showStatusBar = showReaderMenu,
        brightness = readerBrightness
    )
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
    // Tracks a live pinch-to-enter/exit-overview drag.  When the user pinches
    // below 100% zoom the value goes 0→1, driving overviewProgressAnim directly.
    // On release we snap-animate it to 0 (cancel) or 1 (commit).
    var overviewDragProgress by remember { mutableFloatStateOf(0f) }
    var isDraggingOverview by remember { mutableStateOf(false) }
    var overviewAnimJob by remember { mutableStateOf<Job?>(null) }
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
    var isPdf by remember { mutableStateOf(false) }
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

    DisposableEffect(readerImageLoader, localBookId) {
        val activeLoader = readerImageLoader
        onDispose {
            activeLoader?.shutdown()
            com.bunko.reader.engine.pdf.PdfDocumentEngine.closeActiveSession()
            if (localBookId != null && localRepository != null && pages > 0) {
                ReaderExitWriteScope.launch {
                    localRepository.saveProgress(localBookId, page, pages, isCompleted = page >= pages - 1)
                }
            }
        }
    }

    val handleBack = {
        if (localBookId != null && localRepository != null && pages > 0) {
            ReaderExitWriteScope.launch {
                localRepository.saveProgress(localBookId, page, pages, isCompleted = page >= pages - 1)
            }
        }
        onBack()
    }

    BackHandler {
        if (showReaderMenu) {
            showReaderMenu = false
        } else {
            handleBack()
        }
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
                BunkoLog.w(
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
        if (localBookId != null && localRepository != null) {
            ReaderExitWriteScope.launch {
                localRepository.saveProgress(localBookId, finalPage, pages, isCompleted = true)
            }
        }
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
                    BunkoLog.w("Could not mark chapter $completedChapterId as read.", it)
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
        if (localBookId != null && localRepository != null) {
            ReaderExitWriteScope.launch {
                localRepository.saveProgress(localBookId, 0, pages, isCompleted = false)
            }
        }
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
                    BunkoLog.w("Could not mark chapter $resetChapterId as unread.", it)
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
                    BunkoLog.w("Could not load local offline chapter ${target.chapterId}.", it)
                }.getOrNull()
                var isEpubTarget = false
                var isPdfTarget = false
                val chDto = runCatching { loadedApi.seriesChapter(target.chapterId) }.getOrNull()
                if (chDto?.format == MangaFormat.Epub) {
                    isEpubTarget = true
                } else if (chDto?.format == MangaFormat.Pdf) {
                    isPdfTarget = true
                } else if (chDto?.format == null) {
                    val seriesDto = runCatching { loadedApi.series(seriesId) }.getOrNull()
                    isEpubTarget = seriesDto?.format == MangaFormat.Epub
                    isPdfTarget = seriesDto?.format == MangaFormat.Pdf
                }
                isEpub = isEpubTarget
                isPdf = isPdfTarget

                val loadedPageCount: Int
                val loadedDimensions: Map<Int, FileDimensionDto>
                if (local != null) {
                    loadedPageCount = local.pages.size
                    loadedDimensions = local.dimensions
                } else {
                    val info = loadedApi.chapterInfo(target.chapterId, includeDimensions = true, extractPdf = isPdfTarget)
                    loadedPageCount = info.pages ?: 0
                    loadedDimensions = info.pageDimensions.toPageDimensionMap()
                }
                if (loadedPageCount <= 0) {
                    error = "Chapter has no readable pages"
                    return@launch
                }
                val landingPage = if (openAtLastPage) loadedPageCount - 1 else 0

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
                if (settings.reader.autoWebtoonMode && !isEpubTarget && !isPdfTarget) {
                    val nextDirection = ReaderWebtoonDetector.resolveEffectiveReadingDirection(
                        preferredDirection = readingDirection,
                        autoWebtoonMode = true,
                        pageDimensions = loadedDimensions,
                        seriesName = seriesName
                    )
                    readingDirection = nextDirection
                }
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
                BunkoLog.w("Could not switch Reader to chapter ${target.chapterId}.", t)
                error = readerLoadErrorMessage(t)
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
        ePaperMode = persistedReaderSettings.ePaperMode
        readerBrightness = persistedReaderSettings.readerBrightness
        nightModeEnabled = persistedReaderSettings.nightModeEnabled
        nightLightIntensity = persistedReaderSettings.nightLightIntensity

        if (localBookId != null && localRepository != null) {
            val prefKey = "local:$localBookId"
            sessionPreferenceKey = prefKey
            ReaderSessionPreferenceCache.load(ctx.cacheDir, System.currentTimeMillis())
            val cachedPreferences = ReaderSessionPreferenceCache.get(
                prefKey,
                System.currentTimeMillis()
            )
            if (cachedPreferences != null) {
                readingDirection = cachedPreferences.readingDirection
                invertMode = cachedPreferences.invertMode
                ePaperMode = cachedPreferences.ePaperMode
            } else {
                readingDirection = persistedReaderSettings.readingDirection
            }
            try {
                val book = localRepository.getBook(localBookId)
                if (book == null) {
                    error = "Book not found in library."
                    return@LaunchedEffect
                }
                seriesName = book.title
                currentChapter = ReaderChapterEntry(
                    chapterId = 0,
                    volumeId = 0,
                    volumeName = null,
                    chapterName = book.title
                )
                chapterSequence = listOf(currentChapter)

                val file = localRepository.prepareBookFile(book)
                if (!file.isFile || file.length() == 0L) {
                    error = "Could not open book file from storage."
                    return@LaunchedEffect
                }

                val document = ReaderDocumentFactory.openLocalDocument(ctx, file, book.title)
                when (document) {
                    is ReaderDocument.Comic -> {
                        val resolvedPages = document.pages.map { resource ->
                            when (resource) {
                                is ComicPageResource.ArchiveEntry -> OfflinePage.ArchiveEntry(resource.archiveFile.absolutePath, resource.entryName, resource.index)
                                is ComicPageResource.DirectFile -> OfflinePage.ArchiveEntry(resource.file.parent ?: "", resource.file.name, resource.index)
                                is ComicPageResource.RemoteUrl -> OfflinePage.ArchiveEntry("", resource.url, resource.index)
                            }
                        }
                        val dimensionsDtoMap = document.dimensions.mapValues { (idx, dim) ->
                            FileDimensionDto(
                                width = dim.first,
                                height = dim.second,
                                pageNumber = idx,
                                fileName = "page-$idx",
                                isWide = dim.first > dim.second
                            )
                        }
                        pages = resolvedPages.size
                        pageDimensions = dimensionsDtoMap
                        offlineChapter = OfflineChapter(
                            record = OfflineIssueRecord(
                                serverKey = "local",
                                chapterId = 0,
                                libraryId = 0,
                                seriesId = 0,
                                volumeId = 0,
                                seriesName = book.title,
                                issueName = book.title,
                                downloadId = 0L,
                                archivePath = file.absolutePath,
                                localPage = initialPage ?: book.lastReadPage,
                                pageCount = resolvedPages.size
                            ),
                            pages = resolvedPages,
                            dimensions = dimensionsDtoMap
                        )
                        val effectiveDirection = if (cachedPreferences?.readingDirection != null) {
                            cachedPreferences.readingDirection
                        } else {
                            ReaderWebtoonDetector.resolveEffectiveReadingDirection(
                                preferredDirection = persistedReaderSettings.readingDirection,
                                autoWebtoonMode = persistedReaderSettings.autoWebtoonMode,
                                pageDimensions = dimensionsDtoMap,
                                genres = document.genres,
                                tags = document.tags,
                                publishers = listOfNotNull(document.publisher),
                                summary = document.summary,
                                seriesName = book.title
                            )
                        }
                        readingDirection = effectiveDirection
                        page = (initialPage ?: book.lastReadPage).coerceIn(0, (pages - 1).coerceAtLeast(0))
                        readerReady = pages > 0
                        verticalRestoreNonce++
                    }
                    is ReaderDocument.Pdf -> {
                        isPdf = true
                        val resolvedPages = (0 until document.pageCount).map { index ->
                            val dim = document.pageDimensions[index] ?: Pair(1200, 1600)
                            OfflinePage.PdfPage(document.pdfFile.absolutePath, index, dim.first, dim.second)
                        }
                        val dimensionsDtoMap = document.pageDimensions.mapValues { (idx, dim) ->
                            FileDimensionDto(
                                width = dim.first,
                                height = dim.second,
                                pageNumber = idx,
                                fileName = "page-$idx",
                                isWide = dim.first > dim.second
                            )
                        }
                        pages = resolvedPages.size
                        pageDimensions = dimensionsDtoMap
                        offlineChapter = OfflineChapter(
                            record = OfflineIssueRecord(
                                serverKey = "local",
                                chapterId = 0,
                                libraryId = 0,
                                seriesId = 0,
                                volumeId = 0,
                                seriesName = book.title,
                                issueName = book.title,
                                downloadId = 0L,
                                archivePath = file.absolutePath,
                                localPage = initialPage ?: book.lastReadPage,
                                pageCount = resolvedPages.size
                            ),
                            pages = resolvedPages,
                            dimensions = dimensionsDtoMap
                        )
                        page = (initialPage ?: book.lastReadPage).coerceIn(0, (pages - 1).coerceAtLeast(0))
                        readerReady = pages > 0
                        verticalRestoreNonce++
                    }
                    is ReaderDocument.Reflow -> {
                        isEpub = true
                        val spineBlocks = document.spines.map { spine ->
                            ReaderEpubPaginator.parseHtmlToBlocks(
                                html = spine.rawHtml,
                                chapterId = spine.spineIndex,
                                baseUrl = "",
                                apiKey = "",
                                bookResourceUrlBuilder = { _, _, _, resourcePath -> resourcePath }
                            )
                        }.filter { it.isNotEmpty() }

                        if (spineBlocks.isEmpty()) {
                            error = "Could not parse book content."
                            return@LaunchedEffect
                        }
                        epubSpineBlocks = spineBlocks
                        pages = spineBlocks.size
                        page = (initialPage ?: book.lastReadPage)
                        readerReady = true
                        verticalRestoreNonce++
                    }
                }
            } catch (t: Throwable) {
                BunkoLog.w("Could not load local book in Reader.", t)
                error = t.message ?: t.toString()
            }
            return@LaunchedEffect
        }

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
            ePaperMode = cachedPreferences.ePaperMode
            ReaderExitWriteScope.launch {
                ReaderSessionPreferenceCache.persist(ctx.cacheDir)
            }
        }
        val local = runCatching {
            offlineRepository.localChapter(loadedSession, currentChapterId)
        }.onFailure {
            BunkoLog.w("Could not load local offline chapter $currentChapterId.", it)
        }.getOrNull()
        offlineChapter = local
        if (local != null) {
            val archiveFile = File(local.record.archivePath)
            val isLocalEpub = local.record.archivePath.endsWith(".epub", ignoreCase = true)
            val isLocalPdf = local.record.archivePath.endsWith(".pdf", ignoreCase = true)
            if (isLocalEpub) isEpub = true
            if (isLocalPdf) isPdf = true

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
            if (cachedPreferences?.readingDirection == null && !isLocalEpub && !isLocalPdf) {
                readingDirection = ReaderWebtoonDetector.resolveEffectiveReadingDirection(
                    preferredDirection = persistedReaderSettings.readingDirection,
                    autoWebtoonMode = persistedReaderSettings.autoWebtoonMode,
                    pageDimensions = local.dimensions,
                    seriesName = local.record.seriesName
                )
            }
            page = (initialPage ?: local.record.localPage).coerceIn(0, (pages - 1).coerceAtLeast(0))
            if (!local.record.progressPending) {
                lastRemoteProgressPages[currentChapterId] = page
            }
            if (isLocalEpub && archiveFile.isFile) {
                val parsed = runCatching {
                    com.bunko.reader.engine.epub.EpubPackageReader.parseEpub(ctx, archiveFile)
                }.getOrNull()
                if (parsed != null) {
                    val spineBlocks = parsed.spines.map { spine ->
                        ReaderEpubPaginator.parseHtmlToBlocks(
                            html = spine.rawHtml,
                            chapterId = spine.spineIndex,
                            baseUrl = "",
                            apiKey = "",
                            bookResourceUrlBuilder = { _, _, _, path -> path }
                        )
                    }.filter { it.isNotEmpty() }
                    epubSpineBlocks = spineBlocks
                }
            }
            readerReady = if (isLocalEpub) epubSpineBlocks.isNotEmpty() else pages > 0
            verticalRestoreNonce++
        }

        try {
            val client = KavitaClient(ctx, sessionStore)
            val (loadedApi, okHttp) = client.buildApi()
            api = loadedApi
            readerImageLoader = client.buildReaderImageLoader(okHttp, loadedSession)
            runCatching { offlineRepository.syncPending(loadedSession, loadedApi) }
                .onFailure { BunkoLog.w("Could not sync pending offline progress from Reader.", it) }
            var seriesMetadata: SeriesMetadataDto? = null
            val chapterMetadataJob = launch {
                seriesName = runCatching { loadedApi.series(seriesId).name }
                    .onFailure { BunkoLog.w("Could not load reader series name for $seriesId.", it) }
                    .getOrDefault(seriesName)
                seriesMetadata = runCatching { loadedApi.seriesMetadata(seriesId) }
                    .onFailure { BunkoLog.w("Could not load reader series metadata for $seriesId.", it) }
                    .getOrNull()
                val loadedVolumes = runCatching { loadedApi.volumes(seriesId) }
                    .onFailure { BunkoLog.w("Could not load reader chapter sequence for $seriesId.", it) }
                    .getOrDefault(emptyList())
                chapterSequence = readerChapterSequence(loadedVolumes)
                readerChapterEntry(loadedVolumes, currentChapterId)?.let {
                    currentChapter = it
                    currentVolumeId = it.volumeId
                }
            }
            val (resolvedDirection, hasExplicitProfile) = try {
                // A series-specific direction on the server (User/Implicit profile)
                // wins. When only the global Default profile applies, the series has
                // no direction of its own, so fall back to the app setting.
                val profile = loadedApi.readingProfile(libraryId, seriesId)
                if (cachedPreferences?.readingDirection != null) {
                    Pair(cachedPreferences.readingDirection, true)
                } else if (profile.kind != KavitaReadingProfileKindDefault && profile.readingDirection != null) {
                    val dir = when (profile.readingDirection) {
                        KavitaReadingDirectionRtl -> ReaderReadingDirection.RightToLeft
                        KavitaReadingDirectionLtr -> ReaderReadingDirection.LeftToRight
                        KavitaReadingDirectionVertical -> ReaderReadingDirection.Webtoon
                        else -> persistedReaderSettings.readingDirection
                    }
                    Pair(dir, true)
                } else {
                    Pair(persistedReaderSettings.readingDirection, false)
                }
            } catch (t: Throwable) {
                BunkoLog.w("Could not load reading direction for series $seriesId.", t)
                if (cachedPreferences?.readingDirection != null) {
                    Pair(cachedPreferences.readingDirection, true)
                } else {
                    Pair(persistedReaderSettings.readingDirection, false)
                }
            }
            readingDirection = resolvedDirection
            cachedPreferences?.let {
                invertMode = it.invertMode
                ePaperMode = it.ePaperMode
            }
            var isEpubChapter = false
            var isPdfChapter = false
            val chDto = runCatching { loadedApi.seriesChapter(currentChapterId) }.getOrNull()
            if (chDto?.format == MangaFormat.Epub) {
                isEpubChapter = true
            } else if (chDto?.format == MangaFormat.Pdf) {
                isPdfChapter = true
            } else if (chDto?.format == null) {
                val seriesDto = runCatching { loadedApi.series(seriesId) }.getOrNull()
                if (seriesDto?.format == MangaFormat.Epub) {
                    isEpubChapter = true
                } else if (seriesDto?.format == MangaFormat.Pdf) {
                    isPdfChapter = true
                }
            }
            isEpub = isEpubChapter
            isPdf = isPdfChapter

            if (local == null) {
                val info = loadedApi.chapterInfo(currentChapterId, includeDimensions = true, extractPdf = isPdfChapter)
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
                        BunkoLog.w(
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
            val effectiveDirection = if (isEpubChapter) {
                ReaderReadingDirection.LeftToRight
            } else if (hasExplicitProfile) {
                resolvedDirection
            } else {
                ReaderWebtoonDetector.resolveEffectiveReadingDirection(
                    preferredDirection = resolvedDirection,
                    autoWebtoonMode = persistedReaderSettings.autoWebtoonMode,
                    pageDimensions = pageDimensions,
                    seriesName = seriesName,
                    seriesMetadata = seriesMetadata
                )
            }
            readingDirection = effectiveDirection
            readerReady = true
            verticalRestoreNonce++
        } catch (t: Throwable) {
            BunkoLog.w("Could not initialize Reader for chapter $currentChapterId.", t)
            if (local == null) {
                error = readerLoadErrorMessage(t)
            }
        }
    }

    LaunchedEffect(
        localBookId,
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
        if (localBookId != null && localRepository != null) {
            localRepository.saveProgress(localBookId, page, pages, isCompleted = page >= pages - 1)
            return@LaunchedEffect
        }
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
    if (s == null && localBookId == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Loading session...") }
        return
    }

    if (!readerReady && (!isEpub || epubSpineBlocks.isEmpty())) {
        ReaderLoadingScreen(
            preparingPdf = isPdf,
            error = error,
            onBack = handleBack
        )
        return
    }

    val client = remember { KavitaClient(ctx, sessionStore) }
    val activeImageLoader = readerImageLoader ?: fallbackImageLoader
    fun pageModel(index: Int): Any? = if (isEpub) {
        epubSubpages.getOrNull(index)
    } else {
        offlineChapter?.pages?.getOrNull(index)
            ?: if (s != null && index in 0 until pages) {
                client.pageImageUrl(
                    s.baseUrl,
                    s.apiKey,
                    currentChapterId,
                    index,
                    extractPdf = isPdf
                )
            } else {
                null
            }
    }
    val rtl = readingDirection == ReaderReadingDirection.RightToLeft
    val isWebtoon = readingDirection == ReaderReadingDirection.Webtoon
    val vertical = readingDirection == ReaderReadingDirection.Vertical || isWebtoon
    val spreadPages = spreadPagesFor(page, rtl)

    val isLightMode = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    val darkPaper = invertMode != InvertMode.Off ||
        settings.reader.pageBackground == PageBackground.Dark
    val defaultReaderBg = when {
        darkPaper && settings.reader.usePurePageBackgroundColors -> Color.Black
        darkPaper -> Color(0xFF101010)
        settings.reader.usePurePageBackgroundColors -> Color.White
        isLightMode -> MaterialTheme.colorScheme.background
        else -> Color.Black
    }
    val menuBg = if (isLightMode) {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        Color(0xFF141518)
    }

    val isOverviewDisabled = vertical || !settings.reader.overviewMode
    val isOverviewMenuOpen = showReaderMenu && !isOverviewDisabled && chapterBoundary == null
    // Use an Animatable so the overview transition can be driven both by a live
    // pinch gesture (snapTo during drag) and by a settling animation (animateTo
    // after release or a menu-button tap).
    val overviewProgressAnim = remember { Animatable(0f) }
    // Progress is read ONLY inside graphicsLayer lambdas (draw phase) via this
    // state, so the zoom animation invalidates drawing without recomposing the
    // reader tree every frame — that per-frame recomposition was the stutter.
    val overviewProgressState: State<Float> = remember {
        derivedStateOf { overviewProgressAnim.value }
    }
    // Threshold boolean for mount/unmount decisions; the guarded write means
    // recomposition happens only when crossing the threshold, not per frame.
    var overviewEngaged by remember { mutableStateOf(false) }
    LaunchedEffect(overviewProgressAnim) {
        snapshotFlow { overviewProgressAnim.value }
            .collect { value ->
                val engaged = value > 0.001f
                if (engaged != overviewEngaged) overviewEngaged = engaged
            }
    }
    val isOverviewActive = !isOverviewDisabled && (overviewEngaged || isOverviewMenuOpen)
    // When NOT in a live drag, let isOverviewMenuOpen settle the animation.
    LaunchedEffect(isOverviewMenuOpen, isDraggingOverview) {
        if (!isDraggingOverview) {
            overviewAnimJob?.cancel()
            overviewAnimJob = null
            if (!isOverviewMenuOpen) {
                zoomPan = ReaderZoomPanState()
            }
            overviewProgressAnim.animateTo(
                targetValue = if (isOverviewMenuOpen) 1f else 0f,
                animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
            )
        }
    }

    LaunchedEffect(isOverviewActive) {
        if (!isOverviewActive) {
            zoomPan = ReaderZoomPanState()
        }
    }

    // Auto-hide the tap-zone preview after a beat (a tap on it hides it sooner).
    LaunchedEffect(tapZoneOverlayVisible) {
        if (tapZoneOverlayVisible) {
            delay(1500L)
            tapZoneOverlayVisible = false
        }
    }

    // Separate alpha track for the menu overlay so the header/footer fade out
    // smoothly on a normal tap-to-dismiss (when overview is not active).  When
    // the overview IS active/exiting, effectiveMenuAlpha delegates to
    // overviewProgress so both layers animate in perfect lock-step.
    val menuContentVisible = showReaderMenu && chapterBoundary == null
    val menuAlphaState = animateFloatAsState(
        targetValue = if (menuContentVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 160, easing = LinearEasing),
        label = "menuAlpha"
    )
    // In horizontal mode, overview controls slide and fade out over the first 25% of the zoom-in
    // gesture (overviewProgress 1.0 → 0.75) so they retreat off-screen immediately as the user
    // zooms into the page, rather than lingering until the page has completely zoomed in.
    // Kept as State and read only in draw phase (see overviewProgressState).
    val overviewMenuFractionState: State<Float> = remember {
        derivedStateOf {
            if (isOverviewDisabled) {
                1f
            } else {
                ((overviewProgressAnim.value - 0.75f) / 0.25f).coerceIn(0f, 1f)
            }
        }
    }

    // Always use the reader's own background colour for the root container so the
    // status-bar and nav-bar inset strips (which the gallery card never covers) are
    // always the same shade as the reader — no animated colour transition means no
    // visible dark bar either during zoom-out or zoom-in.
    val screenBgColor = defaultReaderBg

    BoxWithConstraints(Modifier.fillMaxSize().background(screenBgColor)) {
        val pageLayoutMode = settings.reader.pageLayoutMode
        val portrait = when (pageLayoutMode) {
            PageLayoutMode.Auto -> maxHeight > maxWidth
            PageLayoutMode.TwoPages -> false
            PageLayoutMode.SinglePage -> true
        }
        val viewportWidthPx = with(density) { maxWidth.toPx() }
        val viewportHeightPx = with(density) { maxHeight.toPx() }
        val viewportHeight = maxHeight

        val stableInsets = WindowInsets.navigationBars.union(WindowInsets.displayCutout).asPaddingValues()
        val layoutDirection = LocalLayoutDirection.current
        val safeTopPadding = (stableInsets.calculateTopPadding() + 16.dp).coerceAtLeast(28.dp)
        val safeBottomPadding = (stableInsets.calculateBottomPadding() + 36.dp).coerceAtLeast(48.dp)
        val safeStartPadding = stableInsets.calculateStartPadding(layoutDirection).coerceAtLeast(20.dp)
        val safeEndPadding = stableInsets.calculateEndPadding(layoutDirection).coerceAtLeast(20.dp)

        // Compute the gallery card scale and center-offset using the SAME formula as
        // ReaderOverviewGallery, so the reader viewport can mirror the card's transform
        // exactly and eliminate both the "black bar" and the "ghost page" issues.
        val context = LocalContext.current
        val resStatusBarHeight = remember(context) {
            val resId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
            if (resId > 0) context.resources.getDimensionPixelSize(resId) else 0
        }
        val stableStatusBarHeight = maxOf(
            WindowInsets.statusBarsIgnoringVisibility
                .union(WindowInsets.displayCutout)
                .asPaddingValues()
                .calculateTopPadding(),
            with(density) { resStatusBarHeight.toDp() }
        )
        val liveStatusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val galleryTopInset = maxOf(stableStatusBarHeight, liveStatusBarHeight)
        val navBarBottomDp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val galleryTopInsetPx  = with(density) { (galleryTopInset + 56.dp).toPx() }
        val galleryBotInsetPx  = with(density) { (88.dp + navBarBottomDp).toPx() }
        val galleryPagerHeightPx = viewportHeightPx - galleryTopInsetPx - galleryBotInsetPx
        val maxCardWidthFraction = if (portrait) 0.78f else 0.74f
        val galleryScale = minOf(
            maxCardWidthFraction,
            (galleryPagerHeightPx * 0.88f / viewportHeightPx).coerceIn(0.1f, 1f)
        ).coerceIn(0.1f, 1f)
        // Vertical offset of the gallery pager centre relative to the screen centre (in px).
        // Positive = pager centre is BELOW screen centre.
        val galleryCenterShiftYPx = (galleryTopInsetPx - galleryBotInsetPx) / 2f

        val portraitPadding = PaddingValues(
            start = safeStartPadding,
            top = safeTopPadding,
            end = safeEndPadding,
            bottom = safeBottomPadding
        )
        val landscapeOuterMargin = maxOf(safeStartPadding, safeEndPadding).coerceIn(20.dp, 32.dp)
        val landscapeInnerMargin = 16.dp
        val landscapeLeftPadding = PaddingValues(
            start = landscapeOuterMargin,
            top = safeTopPadding,
            end = landscapeInnerMargin,
            bottom = safeBottomPadding
        )
        val landscapeRightPadding = PaddingValues(
            start = landscapeInnerMargin,
            top = safeTopPadding,
            end = landscapeOuterMargin,
            bottom = safeBottomPadding
        )

        LaunchedEffect(
            epubSpineBlocks,
            viewportWidthPx,
            viewportHeightPx,
            epubFontSizeSp,
            portrait,
            safeTopPadding,
            safeBottomPadding,
            safeStartPadding,
            safeEndPadding,
            settings.reader.epubFontFamily
        ) {
            if (isEpub && epubSpineBlocks.isNotEmpty() && viewportWidthPx > 0f && viewportHeightPx > 0f) {
                val horizontalPaddingPx = with(density) { (safeStartPadding + safeEndPadding).roundToPx() }
                val landscapeHorizontalPaddingPx = with(density) { (landscapeOuterMargin + landscapeInnerMargin).roundToPx() }
                val verticalPaddingPx = with(density) { (safeTopPadding + safeBottomPadding).roundToPx() }
                val contentWidthPx = if (portrait) {
                    (viewportWidthPx.toInt() - horizontalPaddingPx).coerceAtLeast(100)
                } else {
                    ((viewportWidthPx / 2f).toInt() - landscapeHorizontalPaddingPx).coerceAtLeast(100)
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
                                fontFamily = settings.reader.epubFontFamily,
                                density = density
                            )
                        }
                    }.awaitAll().flatten()
                }
                val total = allSubpages.size.coerceAtLeast(1)
                epubSubpages = allSubpages
                pages = total
                var targetPage = if (oldProgressRatio == 0f && page > 0) {
                    page.coerceIn(0, total - 1)
                } else {
                    (oldProgressRatio * (total - 1)).roundToInt().coerceIn(0, total - 1)
                }
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
            ePaperMode: EPaperMode,
            whiteThreshold: Float,
            invertDecisionCache: MutableMap<ReaderInvertCacheKey, Boolean>,
            pageBackground: Color,
            singlePageAlignmentOverride: Alignment? = null,
            modifier: Modifier = Modifier,
            epubFontSizeSpOverride: Float? = null,
            epubContentPaddingOverride: PaddingValues? = null,
            epubBlockSpacingOverride: Dp? = null
        ) {
            val effectiveEpubFontSizeSp = epubFontSizeSpOverride ?: epubFontSizeSp
            val effectiveBlockSpacing = epubBlockSpacingOverride ?: 10.dp
            Box(modifier = modifier) {
                if (isEpub && epubSubpages.isNotEmpty()) {
                    if (portrait || singlePageAlignmentOverride != null) {
                        val safeIndex = cursor.coerceIn(0, epubSubpages.lastIndex)
                        ReaderEpubPageView(
                            subpage = epubSubpages[safeIndex],
                            fontSizeSp = effectiveEpubFontSizeSp,
                            pageBackground = pageBackground,
                            invertMode = invertMode,
                            ePaperMode = ePaperMode,
                            epubFontFamily = settings.reader.epubFontFamily,
                            epubTextAlign = settings.reader.epubTextAlign,
                            imageLoader = imageLoader,
                            contentPadding = epubContentPaddingOverride ?: portraitPadding,
                            blockSpacingDp = effectiveBlockSpacing,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        val spread = spreadPagesFor(cursor, rightToLeft)
                        Row(modifier.fillMaxSize().background(pageBackground)) {
                            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                if (spread.leftPage in epubSubpages.indices) {
                                    ReaderEpubPageView(
                                        subpage = epubSubpages[spread.leftPage],
                                        fontSizeSp = effectiveEpubFontSizeSp,
                                        pageBackground = pageBackground,
                                        invertMode = invertMode,
                                        ePaperMode = ePaperMode,
                                        epubFontFamily = settings.reader.epubFontFamily,
                                        epubTextAlign = settings.reader.epubTextAlign,
                                        imageLoader = imageLoader,
                                        contentPadding = epubContentPaddingOverride ?: landscapeLeftPadding,
                                        blockSpacingDp = effectiveBlockSpacing,
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
                                        fontSizeSp = effectiveEpubFontSizeSp,
                                        pageBackground = pageBackground,
                                        invertMode = invertMode,
                                        ePaperMode = ePaperMode,
                                        epubFontFamily = settings.reader.epubFontFamily,
                                        epubTextAlign = settings.reader.epubTextAlign,
                                        imageLoader = imageLoader,
                                        contentPadding = epubContentPaddingOverride ?: landscapeRightPadding,
                                        blockSpacingDp = effectiveBlockSpacing,
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
                        ePaperMode = ePaperMode,
                        imageScaleType = settings.reader.imageScaleType,
                        cropBorders = settings.reader.cropBorders,
                        singlePageAlignmentOverride = singlePageAlignmentOverride,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (nightModeEnabled && nightLightIntensity > 0f) {
                    val amberAlpha = (nightLightIntensity * 0.38f).coerceIn(0f, 0.45f)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFFFF9E3D).copy(alpha = amberAlpha))
                    )
                }
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
        val playCurlHost = rememberPlayCurlHostState()
        val portraitCurlState = remember(currentChapterId) { PageCurlState(initialCurrent = page) }
        val spreadCurlState = remember(currentChapterId) {
            PageCurlState(
                initialCurrent = ReaderSpreadCurlVisualCurrent,
                turnEndFractionX = ReaderSpreadCurlTurnEndFractionX
            )
        }
        // Inverting already forces the dark paper (a bright fold next to an inverted
        // page is what the invert mode exists to avoid); the setting picks the colour the
        // rest of the time.
        val darkPaper = invertMode != InvertMode.Off ||
            settings.reader.pageBackground == PageBackground.Dark
        val themePaperColor = if (isLightMode) MaterialTheme.colorScheme.background else null
        val curlBackPageColor = readerPageBackgroundColor(
            darkPaper = darkPaper,
            usePureColors = settings.reader.usePurePageBackgroundColors,
            themePaperColor = themePaperColor
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
                val model = pageModel(index) ?: return@mapNotNull null
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
                val model = pageModel(index) ?: return@mapNotNull null
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
            s?.baseUrl,
            s?.apiKey,
            settings.reader.prefetchTurns,
            vertical,
            invertMode,
            settings.reader.invertWhiteThreshold
        ) {
            if (pages <= 0 || (offlineChapter == null && s == null)) return@LaunchedEffect
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
        val usePortraitPlayCurl =
            !vertical &&
            settings.reader.pageTurnMode == PageTurnMode.PlayCurl &&
                settings.reader.pageTransitionAnimation &&
                portrait &&
                layout.singlePage &&
                !zoomPanEnabled
        val useSpreadPlayCurl =
            !vertical &&
            settings.reader.pageTurnMode == PageTurnMode.PlayCurl &&
                settings.reader.pageTransitionAnimation &&
                !portrait &&
                // The PlayLikeCurl spread renderer addresses two half-width viewports,
                // so spreads only run on landscape viewports; a forced two-page layout
                // on a portrait screen falls back to Slide (same as centred singles).
                viewportWidthPx > viewportHeightPx &&
                // A wide page is one sheet printed across the whole spread, so it turns as
                // a full-width leaf with the fold at the centre. Other landscape singles
                // (cover, chapter end, the page displayed alone next to a wide one) sit
                // centred with no spine under them, so they fall back to Slide.
                (!layout.singlePage || pageDimensions.pageIsWide(page) || isEpub) &&
                !zoomPanEnabled
        val usePlayCurl = usePortraitPlayCurl || useSpreadPlayCurl

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
                (!layout.singlePage || pageDimensions.pageIsWide(page) || isEpub) &&
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

        LaunchedEffect(usePlayCurl, useCurl, rtl) {
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
                    if (next != null) {
                        completeChapter(exitAfter = false)
                        chapterBoundary = ReaderChapterBoundary(
                            direction = direction,
                            current = currentChapter,
                            neighbor = next
                        )
                    } else {
                        // At the end of the book: record completed progress but do not automatically close the reader
                        completeChapter(exitAfter = false)
                    }
                }
                ReaderTurnDirection.Previous -> if (page == 0) {
                    val previous = neighbors.previous
                    if (previous != null) {
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

        fun playCurlTurnStep(direction: ReaderTurnDirection): Int =
            if (useSpreadPlayCurl) {
                if (direction == ReaderTurnDirection.Next) nextPageTurnStep else previousPageTurnStep
            } else {
                1
            }

        // A leaf can land on a spread or on a wide page (a full-width sheet), but not on a
        // centred landscape single (cover, chapter end, the lone page beside a wide one):
        // there is no spine under those, so such turns use the Slide path instead.
        fun playCurlTurnTargetEligible(target: Int): Boolean {
            if (!useSpreadPlayCurl) return true
            val targetLayout = readerPageLayout(target, pages, portrait, pageDimensions, isEpub = isEpub)
            return !targetLayout.singlePage || pageDimensions.pageIsWide(target)
        }

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

        fun requestPlayCurlTurn(
            direction: ReaderTurnDirection,
            step: Int,
            completeWhenPastEnd: Boolean,
            tapPosition: Offset? = null
        ) {
            // A slide transition (shift, boundary fallback) may be covering the Play Curl;
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
            activeCurlDirection = direction
            activeCurlTargetPage = target
            // The PlayLikeCurl surface settles with the same reference animation as a
            // completed edge drag; page state commits in its onSettlementCompleted callback.
            // If the GL deck is not ready yet (first frames), fall back to Slide so taps
            // never drop.
            val accepted = playCurlHost.requestTurn(direction == ReaderTurnDirection.Next)
            if (!accepted) {
                activeCurlDirection = null
                activeCurlTargetPage = null
                requestSlideTurn(direction, step, completeWhenPastEnd)
            } else {
                rememberZoomTurnLanding(direction)
            }
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
            // The tap position picks which corner leads the fold.
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

        // Called by the PlayCurlPage settlement callback when the GL turn commits.
        fun onPlayCurlSettled(ordinal: Int) {
            val direction = activeCurlDirection
            val target = activeCurlTargetPage ?: ordinal
            page = target.coerceIn(0, (pages - 1).coerceAtLeast(0))
            activeCurlDirection = null
            activeCurlTargetPage = null
            if (direction != null) {
                rememberZoomTurnLanding(direction)
            }
        }

        fun playCurlRouteForTurn(direction: ReaderTurnDirection, step: Int, completeWhenPastEnd: Boolean): Boolean {
            if (!(usePlayCurl && (useSpreadPlayCurl || step == 1))) return false
            val target = turnTarget(direction, step, completeWhenPastEnd)
            // Boundary turns (null target) stay on the Play Curl path for the edge resistance.
            return target == null || playCurlTurnTargetEligible(target)
        }

        fun curlRouteForTurn(direction: ReaderTurnDirection, step: Int, completeWhenPastEnd: Boolean): Boolean {
            if (!(useCurl && (useSpreadCurl || step == 1))) return false
            val target = turnTarget(direction, step, completeWhenPastEnd)
            // Boundary turns (null target) stay on the curl path for the edge resistance.
            return target == null || curlTurnTargetEligible(target)
        }

        requestTurn = requestTurnLambda@{ direction, step, completeWhenPastEnd ->
            if (playCurlRouteForTurn(direction, step, completeWhenPastEnd)) {
                requestPlayCurlTurn(direction, step, completeWhenPastEnd)
                return@requestTurnLambda
            }
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
            if (playCurlRouteForTurn(direction, step, completeWhenPastEnd)) {
                requestPlayCurlTurn(direction, step, completeWhenPastEnd, tapPosition)
            } else if (curlRouteForTurn(direction, step, completeWhenPastEnd)) {
                requestCurlTurn(direction, step, completeWhenPastEnd, tapPosition)
            } else {
                requestSlideTurn(direction, step, completeWhenPastEnd)
            }
        }

        // Spread shift (long-press / menu ±1) animates as a Slide transition in both
        // modes: in Play Curl mode it draws over the still-mounted curl surface, so there
        // is no unmount/remount churn (the original cause of the image thrash).
        fun requestSingleStep(direction: ReaderTurnDirection, completeWhenPastEnd: Boolean) {
            requestSlideTurn(direction, 1, completeWhenPastEnd)
        }

        val activity = ctx as? MainActivity
        DisposableEffect(
            activity,
            settings.reader.volumeKeysNavigation,
            vertical,
            nextPageTurnStep,
            previousPageTurnStep,
            showingFinalPage,
            chapterBoundary,
            viewportHeightPx
        ) {
            if (settings.reader.volumeKeysNavigation && activity != null) {
                activity.volumeKeyHandler = { keyCode ->
                    when (keyCode) {
                        KeyEvent.KEYCODE_VOLUME_DOWN -> {
                            if (vertical) {
                                scope.launch { verticalListState.animateScrollBy(viewportHeightPx * 0.75f) }
                            } else if (chapterBoundary != null) {
                                turnChapterBoundary(ReaderTurnDirection.Next)
                            } else {
                                showReaderMenu = false
                                requestTurn(ReaderTurnDirection.Next, nextPageTurnStep, showingFinalPage)
                            }
                            true
                        }
                        KeyEvent.KEYCODE_VOLUME_UP -> {
                            if (vertical) {
                                scope.launch { verticalListState.animateScrollBy(-viewportHeightPx * 0.75f) }
                            } else if (chapterBoundary != null) {
                                turnChapterBoundary(ReaderTurnDirection.Previous)
                            } else {
                                showReaderMenu = false
                                requestTurn(ReaderTurnDirection.Previous, previousPageTurnStep, false)
                            }
                            true
                        }
                        else -> false
                    }
                }
            } else {
                activity?.volumeKeyHandler = null
            }
            onDispose {
                activity?.volumeKeyHandler = null
            }
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
            if (usePlayCurl || useCurl) {
                curlDragStartPointer = pointer
                curlDragProgress = 0f
                return
            }
        }

        fun updateTurnDrag(direction: ReaderTurnDirection, progress: Float, pointer: Offset) {
            if (usePlayCurl) {
                // Ignore new Play Curl drags only while a SETTLING slide transition covers
                // the surface (shift / tap fallback animating, a few hundred ms). A
                // drag-driven fallback transition has transitionSettling == false and must
                // keep receiving its own drag frames through the fall-through below.
                if (activeCurlDirection == null && transitionSettling) return
                var fallThroughToSlide = false
                if (activeCurlDirection != direction) {
                    val step = playCurlTurnStep(direction)
                    val target = turnTarget(direction, step, direction == ReaderTurnDirection.Next && showingFinalPage)
                    if (target != null && !playCurlTurnTargetEligible(target)) {
                        // Turning towards a centred landscape single: this drag runs as a
                        // Slide transition below instead of a Play Curl fold.
                        fallThroughToSlide = true
                    } else {
                        dragBoundaryDirection = if (target == null) direction else null
                        activeCurlDirection = direction
                        activeCurlTargetPage = target
                        val start = curlDragStartPointer ?: pointer
                        if (target != null) {
                            playCurlHost.beginDrag(start)
                            playCurlHost.moveDrag(pointer)
                        }
                    }
                } else {
                    playCurlHost.moveDrag(pointer)
                }
                if (!fallThroughToSlide) {
                    curlDragProgress = progress
                    return
                }
            } else if (useCurl) {
                if (activeCurlDirection == null && transitionSettling) return
                var fallThroughToSlide = false
                if (activeCurlDirection != direction) {
                    val step = curlTurnStep(direction)
                    val target = turnTarget(direction, step, direction == ReaderTurnDirection.Next && showingFinalPage)
                    if (target != null && !curlTurnTargetEligible(target)) {
                        fallThroughToSlide = true
                    } else {
                        dragBoundaryDirection = if (target == null) direction else null
                        activeCurlDirection = direction
                        activeCurlTargetPage = target
                        val start = curlDragStartPointer ?: pointer
                        if (target != null) {
                            val curlState = if (useSpreadCurl) spreadCurlState else portraitCurlState
                            val spreadCurl = useSpreadCurl
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
            val playCurlDirection = activeCurlDirection
            if (usePlayCurl && playCurlDirection != null) {
                // The PlayLikeCurl surface owns commit/cancel settlement from the live
                // pointer position (same path as a completed edge drag); the page state
                // commits in onPlayCurlSettled. Boundary drags still run chapter actions.
                val boundaryDirection = dragBoundaryDirection
                val step = playCurlTurnStep(playCurlDirection)
                val target = turnTarget(
                    playCurlDirection,
                    step,
                    playCurlDirection == ReaderTurnDirection.Next && showingFinalPage
                )
                activeCurlDirection = null
                curlDragStartPointer = null
                curlDragProgress = 0f
                dragBoundaryDirection = null
                activeCurlTargetPage = target
                if (boundaryDirection != null) {
                    playCurlHost.cancelDrag()
                    activeCurlTargetPage = null
                    runBoundaryAction(
                        boundaryDirection,
                        completeWhenPastEnd = boundaryDirection == ReaderTurnDirection.Next && showingFinalPage
                    )
                    return
                }
                if (target == null) {
                    playCurlHost.cancelDrag()
                    activeCurlTargetPage = null
                    return
                }
                playCurlHost.endDrag()
                return
            }
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
            if (usePlayCurl && activeCurlDirection != null) {
                activeCurlDirection = null
                curlDragStartPointer = null
                curlDragProgress = 0f
                dragBoundaryDirection = null
                playCurlHost.cancelDrag()
                activeCurlTargetPage = null
                return
            }
            if (useCurl && activeCurlDirection != null) {
                val curlState = if (useSpreadCurl) spreadCurlState else portraitCurlState
                val spreadCurl = useSpreadCurl
                activeCurlDirection = null
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
            if (pages <= 0 || (offlineChapter == null && s == null)) return@LaunchedEffect
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
                    ePaperMode = ePaperMode,
                    whiteThreshold = settings.reader.invertWhiteThreshold,
                    invertDecisionCache = invertDecisionCache,
                    pageBackground = readerPageBackground,
                    nightModeEnabled = nightModeEnabled,
                    nightLightIntensity = nightLightIntensity,
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
                                    completeChapter(exitAfter = false)
                                }
                                ReaderTurnDirection.Previous -> {
                                    if (neighbors.previous != null) {
                                        resetChapterAndExit(exitAfter = false)
                                    }
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
                    onBackToSeries = handleBack,
                    onMenuToggle = { showReaderMenu = !showReaderMenu },
                    epubSubpages = epubSubpages,
                    epubFontSizeSp = epubFontSizeSp,
                    epubFontFamily = settings.reader.epubFontFamily,
                    epubTextAlign = settings.reader.epubTextAlign,
                    isWebtoon = isWebtoon,
                    sidePaddingPercent = settings.reader.webtoonSidePadding,
                    navigationMode = settings.reader.navigationMode,
                    tappingInvertMode = settings.reader.tappingInvertMode,
                    imageScaleType = settings.reader.imageScaleType,
                    cropBorders = settings.reader.cropBorders
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    // Main reader viewport — kept permanently mounted to avoid page unmount/remount
                    // thrashing and bitmap reloads. When overview is active or animating, hidden
                    // so ONLY the overview center card renders, completely eliminating any halo/double-drawing.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                // Mirror the gallery center card's scale+translation exactly.
                                // At overview-open (progress=1): reader shrinks to galleryScale
                                // and shifts to the pager centre — perfectly hidden behind the card.
                                // At full-reader (progress=0): scale=1, shift=0.
                                // The bar strips above/below the card are filled by the reader page
                                // (same content as card) instead of the black background, eliminating
                                // the "black bar" flash without any alpha cross-fade ghost.
                                val progress = overviewProgressState.value
                                val readerScale = galleryScale + (1f - galleryScale) * (1f - progress)
                                scaleX = readerScale
                                scaleY = readerScale
                                // galleryCenterShiftYPx is the pager-centre offset from screen centre.
                                // At overview: shift reader to pager centre (same as card).
                                // At reader: no shift.
                                translationY = progress * galleryCenterShiftYPx
                                // When overview is fully open (progress >= 0.98f), hide the stationary
                                // reader viewport so it never shows through page gaps when scrolling
                                // the horizontal carousel. During zoom transitions (< 0.98f), alpha is 1f
                                // with exact transform mirroring, keeping the transition seamless with no black bars.
                                alpha = if (progress >= 0.98f) 0f else 1f
                            },
                        contentAlignment = Alignment.Center
                    ) {
            // Keep the Play Curl surface mounted whenever the mode is active (not just
            // during a turn): the neighbour pages in its current deck are exactly the
            // images the next turn needs, so the gesture starts warm instead of kicking
            // off image loads on its first frame. Slide transitions (shift, boundary
            // fallbacks) draw on top of it rather than unmounting it — the remount
            // recomposition is what made the images thrash.
            //
            // Exception: the overview zoom. The GL surface lives in a separate window
            // and ignores the Compose scale/alpha that shrinks the reader viewport
            // into the gallery card — it stays full-size on top and flashes black in
            // the card gaps. So while the overview is opening/open, unmount the
            // surface and show the identical Compose page instead; the deck rebuilds
            // warm from the Coil cache when the overview closes.
            if (usePortraitPlayCurl || useSpreadPlayCurl) {
                // Engaged for the whole zoom (mounts/unmounts at the ends only),
                // so the GL surface never churns mid-animation.
                val overviewTakingOver = overviewEngaged
                // Settle in-flight turn state before unmounting the surface for the
                // overview (dispose cancels the GL settlement without committing).
                // Tap-turns have no live drag pointer, so commit their target — the
                // GL animation would have landed there anyway. Live drag-turns are
                // cancelled instead: the page stays where it was.
                LaunchedEffect(overviewTakingOver) {
                    if (overviewTakingOver && activeCurlTargetPage != null) {
                        if (curlDragStartPointer == null) {
                            page = activeCurlTargetPage!!.coerceIn(0, (pages - 1).coerceAtLeast(0))
                        } else {
                            playCurlHost.cancelDrag()
                        }
                        activeCurlDirection = null
                        activeCurlTargetPage = null
                        curlDragStartPointer = null
                        curlDragProgress = 0f
                        dragBoundaryDirection = null
                    }
                }
                if (!overviewTakingOver) {
                    PlayCurlPage(
                        page = page,
                        pageCount = pages,
                        portrait = usePortraitPlayCurl,
                        rightToLeft = rtl,
                        viewportWidthPx = viewportWidthPx,
                        viewportHeightPx = viewportHeightPx,
                        paperColor = curlBackPageColor,
                        pageModel = ::pageModel,
                        imageLoader = activeImageLoader,
                        pageDimensions = pageDimensions,
                        invertMode = invertMode,
                        whiteThreshold = settings.reader.invertWhiteThreshold,
                        invertDecisionCache = invertDecisionCache,
                        ePaperMode = ePaperMode,
                        imageScaleType = settings.reader.imageScaleType,
                        cropBorders = settings.reader.cropBorders,
                        nightModeEnabled = nightModeEnabled,
                        nightLightIntensity = nightLightIntensity,
                        epubFontSizeSp = epubFontSizeSp,
                        epubFontFamily = settings.reader.epubFontFamily,
                        epubTextAlign = settings.reader.epubTextAlign,
                        epubContentPadding = portraitPadding,
                        density = density,
                        isEpub = isEpub,
                        host = playCurlHost,
                        onPageTurned = ::onPlayCurlSettled,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (overviewTakingOver || !playCurlHost.ready) {
                    RenderReaderPage(
                        cursor = page,
                        pageCount = pages,
                        portrait = portrait,
                        pageDimensions = pageDimensions,
                        rightToLeft = rtl,
                        pageModel = ::pageModel,
                        imageLoader = activeImageLoader,
                        invertMode = invertMode,
                        ePaperMode = ePaperMode,
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
            } else if (usePortraitCurl) {
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
                            ePaperMode = ePaperMode,
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
                                    ePaperMode = ePaperMode,
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
                                            ePaperMode = ePaperMode,
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
                            ePaperMode = ePaperMode,
                            whiteThreshold = settings.reader.invertWhiteThreshold,
                            invertDecisionCache = invertDecisionCache,
                            pageBackground = curlBackPageColor,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            } else if (!usePlayCurl && !useCurl) {
                RenderReaderPage(
                    cursor = page,
                    pageCount = pages,
                    portrait = portrait,
                    pageDimensions = pageDimensions,
                    rightToLeft = rtl,
                    pageModel = ::pageModel,
                    imageLoader = activeImageLoader,
                    invertMode = invertMode,
                    ePaperMode = ePaperMode,
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(readerPageBackground)
                ) {
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
                                            ePaperMode = ePaperMode,
                                            whiteThreshold = settings.reader.invertWhiteThreshold,
                                            invertDecisionCache = invertDecisionCache,
                                            pageBackground = readerPageBackground,
                                            modifier = Modifier.fillMaxSize()
                                        )
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
                            ePaperMode = ePaperMode,
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
                            ePaperMode = ePaperMode,
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

            if (readerBrightness in 0f..0.20f) {
                val dimAlpha = ((0.20f - readerBrightness) / 0.20f) * 0.65f
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = dimAlpha))
                )
            }

            // Keep the overview gallery mounted for the entire duration of the exit
            // animation (driven by overviewProgress → 0).
            if (isOverviewActive) {
                val overviewCursors = remember(pages, portrait, pageDimensions, isEpub) {
                    readerOverviewCursors(
                        pageCount = pages,
                        portrait = portrait,
                        pageDimensions = pageDimensions,
                        isEpub = isEpub
                    )
                }
                ReaderOverviewGallery(
                    cursors = overviewCursors,
                    currentCursor = page,
                    reverseLayout = rtl,
                    progressState = overviewProgressState,
                    tapZoneOverlayVisible = tapZoneOverlayVisible,
                    tapZoneNavigationMode = settings.reader.navigationMode,
                    tapZoneTappingInvertMode = settings.reader.tappingInvertMode,
                    tapZoneRightToLeft = rtl,
                    onTapZoneOverlayDismiss = { tapZoneOverlayVisible = false },
                    onSelect = { cursor ->
                        page = cursor
                        lastRemoteProgressPages[currentChapterId] = cursor
                    },
                    onCenterTap = {
                        zoomPan = initialZoomPanState
                        showReaderMenu = false
                    },
                    // Route pinch gestures from within the overview gallery to the same
                    // onTransform/onTransformEnd handlers used by ReaderTapLayer.  The
                    // gallery's Initial-pass interceptor captures 2-finger events before
                    // HorizontalPager can misinterpret them as horizontal scroll.
                    onTransform = { zoomChange, _, _ ->
                        if (isOverviewMenuOpen) {
                            if (zoomChange > 1f + ReaderZoomEpsilon || (isDraggingOverview && zoomChange < 1f - ReaderZoomEpsilon)) {
                                val startProgress = if (!isDraggingOverview) 1f else overviewDragProgress
                                val delta = (zoomChange - 1f) * 4f
                                val next = (startProgress - delta).coerceIn(0f, 1f)
                                isDraggingOverview = true
                                overviewDragProgress = next
                                zoomPan = initialZoomPanState
                                scope.launch { overviewProgressAnim.snapTo(next) }
                            }
                        }
                    },
                    onTransformEnd = { _ ->
                        if (isDraggingOverview) {
                            isDraggingOverview = false
                            val shouldDismiss = overviewDragProgress <= 0.65f
                            zoomPan = initialZoomPanState
                            if (shouldDismiss) {
                                showReaderMenu = false
                                overviewDragProgress = 0f
                            } else {
                                overviewDragProgress = 1f
                                scope.launch {
                                    overviewProgressAnim.animateTo(
                                        1f,
                                        tween(200, easing = FastOutSlowInEasing)
                                    )
                                }
                            }
                        }
                    }
                ) { cursor, cardModifier ->
                    RenderReaderPage(
                        cursor = cursor,
                        pageCount = pages,
                        portrait = portrait,
                        pageDimensions = pageDimensions,
                        rightToLeft = rtl,
                        pageModel = ::pageModel,
                        imageLoader = activeImageLoader,
                        invertMode = invertMode,
                        ePaperMode = ePaperMode,
                        whiteThreshold = settings.reader.invertWhiteThreshold,
                        invertDecisionCache = invertDecisionCache,
                        pageBackground = readerPageBackground,
                        modifier = cardModifier
                    )
                }
            }
        }
        }
        }



        if (!vertical && chapterBoundary == null && !showReaderMenu) {
            key(page, rtl, nextPageTurnStep, previousPageTurnStep, showingFinalPage) {
                ReaderTapLayer(
                rightToLeft = rtl,
                navigationMode = settings.reader.navigationMode,
                tappingInvertMode = settings.reader.tappingInvertMode,
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
                directionLockEnabled = usePlayCurl || useCurl,
                closeSwipeEnabled = false,
                closeVisualDistancePx = viewportHeightPx,
                onCloseDrag = {},
                onCloseDragEnd = {},
                onCloseDragCancel = {},
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
                    val candidate = zoomPan.withTransform(
                        zoomChange = zoomChange,
                        panChange = panChange,
                        focalPoint = focalPoint,
                        baseZoomScale = baseZoomScale,
                        viewportWidthPx = viewportWidthPx,
                        viewportHeightPx = viewportHeightPx
                    )
                    when {
                        // ── Overview already open: pinch-out (spread) → dismiss it ───
                        isOverviewMenuOpen && zoomChange > 1f + ReaderZoomEpsilon -> {
                            // On the FIRST frame of the dismiss gesture, overviewDragProgress
                            // is 0f (it stays 0 when overview was opened via button, not gesture).
                            // Seed it to 1f so the drag has the full 1→0 range to work with.
                            val startProgress = if (!isDraggingOverview) 1f else overviewDragProgress
                            val delta = (zoomChange - 1f) * 4f
                            val next = (startProgress - delta).coerceIn(0f, 1f)
                            isDraggingOverview = true
                            overviewDragProgress = next
                            zoomPan = initialZoomPanState
                            scope.launch { overviewProgressAnim.snapTo(next) }
                        }
                        // ── At 1× zoom: pinch-in → enter overview ──────────────────────
                        // Stay in drag mode if isDraggingOverview is already true, even
                        // when zoomChange oscillates near 1f on slow pinches — only exit
                        // on a clear pinch-OUT (zoomChange > 1+ε, handled above/in else).
                        !isOverviewDisabled &&
                        !isOverviewMenuOpen &&
                        candidate.userScale <= 1f + ReaderZoomEpsilon &&
                        (isDraggingOverview || zoomChange < 1f - ReaderZoomEpsilon) -> {
                            val delta = (1f - zoomChange).coerceAtLeast(0f) * 4f
                            val next = (overviewDragProgress + delta).coerceIn(0f, 1f)
                            isDraggingOverview = true
                            overviewDragProgress = next
                            zoomPan = initialZoomPanState
                            scope.launch { overviewProgressAnim.snapTo(next) }
                        }
                        // ── Normal zoom / clear reversal ────────────────────────────────
                        else -> {
                            if (isDraggingOverview) {
                                // Only cancel the overview drag when the user clearly
                                // spreads fingers (zooming IN).  Near-1 zoomChange values
                                // from a slow pinch must NOT cancel; just hold position.
                                if (zoomChange > 1f + ReaderZoomEpsilon) {
                                    isDraggingOverview = false
                                    overviewDragProgress = 0f
                                    scope.launch {
                                        overviewProgressAnim.animateTo(
                                            0f,
                                            tween(200, easing = FastOutSlowInEasing)
                                        )
                                    }
                                }
                                zoomPan = initialZoomPanState
                                // else: near-1 zoomChange while dragging → hold current progress
                            } else {
                                zoomPan = candidate
                            }
                        }
                    }
                },
                onTransformEnd = { _ ->
                    if (isDraggingOverview) {
                        isDraggingOverview = false
                        val shouldCommit = overviewDragProgress >= 0.35f
                        zoomPan = initialZoomPanState
                        if (isOverviewMenuOpen) {
                            // Was overview open, dragging to dismiss
                            val shouldDismiss = overviewDragProgress <= 0.65f
                            if (shouldDismiss) {
                                // Progress went below threshold → dismiss overview
                                showReaderMenu = false
                                overviewDragProgress = 0f
                                // isOverviewMenuOpen → false → LaunchedEffect animates to 0
                            } else {
                                // Didn't drag enough → snap back to fully open
                                overviewDragProgress = 1f
                                scope.launch {
                                    overviewProgressAnim.animateTo(
                                        1f,
                                        tween(200, easing = FastOutSlowInEasing)
                                    )
                                }
                            }
                        } else {
                            // Was reading, dragging to open overview
                            if (shouldCommit) {
                                // Open the overview: set showReaderMenu → triggers isOverviewMenuOpen
                                showReaderMenu = true
                                overviewDragProgress = 0f
                                // isOverviewMenuOpen → true → LaunchedEffect animates to 1
                            } else {
                                // Not enough → snap back to reading
                                overviewDragProgress = 0f
                                scope.launch {
                                    overviewProgressAnim.animateTo(
                                        0f,
                                        tween(200, easing = FastOutSlowInEasing)
                                    )
                                }
                            }
                        }
                    }
                }
                )
            }
        } else if (!vertical && !showReaderMenu) {
            ReaderTapLayer(
                rightToLeft = rtl,
                navigationMode = settings.reader.navigationMode,
                tappingInvertMode = settings.reader.tappingInvertMode,
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
                onBackToSeries = handleBack
            )
        }

        // Bottom reading status bar: page in chapter, percentage completed, battery percentage
        if (!isOverviewActive) {
            ReaderBottomStatusBar(
                currentPage = page,
                totalPages = pages,
                pageBackground = readerPageBackground,
                visible = !showReaderMenu && readerReady && error == null && chapterBoundary == null,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // Tap-zone preview under the menu layer, so the header/footer stay
        // visible and interactive above the zones. While the overview is open
        // the preview lives on the current gallery card instead, so this
        // fullscreen layer stays hidden out of the way.
        ReaderTapZoneOverlay(
            visible = tapZoneOverlayVisible && !isOverviewActive,
            navigationMode = settings.reader.navigationMode,
            tappingInvertMode = settings.reader.tappingInvertMode,
            rightToLeft = rtl,
            onDismiss = { tapZoneOverlayVisible = false }
        )

        // Mount the menu overlay layer whenever any portion of its alpha is still
        // positive — either during the overview exit or the normal menu fade.
        // Both the mount boolean and the alpha resolve off draw-phase reads, so
        // menu fading never recomposes the reader.
        var menuLayerMounted by remember { mutableStateOf(false) }
        LaunchedEffect(isOverviewDisabled) {
            snapshotFlow {
                if (isOverviewDisabled) {
                    menuAlphaState.value
                } else {
                    overviewMenuFractionState.value
                }
            }.collect { alpha ->
                val mounted = alpha > 0f
                if (mounted != menuLayerMounted) menuLayerMounted = mounted
            }
        }
        if (menuLayerMounted) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = if (isOverviewDisabled) {
                            menuAlphaState.value
                        } else {
                            overviewMenuFractionState.value
                        }
                    }
            ) {
            ReaderMenuOverlay(
                visible = menuContentVisible,
                menuFraction = overviewMenuFractionState,
                dismissOnBackgroundTap = isOverviewDisabled,
                seriesName = seriesName,
                chapterName = currentChapter.displayName,
                page = page,
                pages = pages,
                readingDirection = readingDirection,
                pageLayoutMode = settings.reader.pageLayoutMode,
                showSpreadShift = !vertical && !portrait && settings.reader.showSpreadShiftButtons,
                onBack = handleBack,
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
                            preferences = ReaderSessionPreferences(direction, invertMode, ePaperMode),
                            nowMillis = System.currentTimeMillis()
                        )
                        ReaderExitWriteScope.launch {
                            ReaderSessionPreferenceCache.persist(ctx.cacheDir)
                        }
                    }
                },
                onSetPageLayoutMode = { mode ->
                    scope.launch { settingsStore.setPageLayoutMode(mode) }
                },
                invertMode = invertMode,
                onSetInvertMode = { mode ->
                    // Per-book session override only (see the invertMode declaration above);
                    // does not write global Reader Settings.
                    invertMode = mode
                    sessionPreferenceKey?.let { key ->
                        ReaderSessionPreferenceCache.put(
                            key = key,
                            preferences = ReaderSessionPreferences(readingDirection, mode, ePaperMode),
                            nowMillis = System.currentTimeMillis()
                        )
                        ReaderExitWriteScope.launch {
                            ReaderSessionPreferenceCache.persist(ctx.cacheDir)
                        }
                    }
                },
                ePaperMode = ePaperMode,
                onSetEPaperMode = { mode ->
                    ePaperMode = mode
                    scope.launch { settingsStore.setEPaperMode(mode) }
                    sessionPreferenceKey?.let { key ->
                        ReaderSessionPreferenceCache.put(
                            key = key,
                            preferences = ReaderSessionPreferences(readingDirection, invertMode, mode),
                            nowMillis = System.currentTimeMillis()
                        )
                        ReaderExitWriteScope.launch {
                            ReaderSessionPreferenceCache.persist(ctx.cacheDir)
                        }
                    }
                },
                readerBrightness = readerBrightness,
                onSetReaderBrightness = { value ->
                    readerBrightness = value
                    scope.launch { settingsStore.setReaderBrightness(value) }
                },
                nightModeEnabled = nightModeEnabled,
                onSetNightModeEnabled = { enabled ->
                    nightModeEnabled = enabled
                    scope.launch { settingsStore.setNightModeEnabled(enabled) }
                },
                nightLightIntensity = nightLightIntensity,
                onSetNightLightIntensity = { intensity ->
                    nightLightIntensity = intensity
                    scope.launch { settingsStore.setNightLightIntensity(intensity) }
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
                },
                epubFontFamily = settings.reader.epubFontFamily,
                onSetEpubFontFamily = { newFamily ->
                    scope.launch { settingsStore.setEpubFontFamily(newFamily) }
                },
                epubTextAlign = settings.reader.epubTextAlign,
                onSetEpubTextAlign = { newAlign ->
                    scope.launch { settingsStore.setEpubTextAlign(newAlign) }
                },
                pageBackground = settings.reader.pageBackground,
                onSetPageBackground = { newBg ->
                    scope.launch { settingsStore.setPageBackground(newBg) }
                },
                usePureColors = settings.reader.usePurePageBackgroundColors,
                onSetUsePureColors = { newPure ->
                    scope.launch { settingsStore.setUsePurePageBackgroundColors(newPure) }
                },
                pageTransitionAnimation = settings.reader.pageTransitionAnimation,
                onSetPageTransitionAnimation = { enabled ->
                    scope.launch { settingsStore.setPageTransitionAnimation(enabled) }
                },
                pageTurnMode = settings.reader.pageTurnMode,
                onSetPageTurnMode = { newMode ->
                    scope.launch { settingsStore.setPageTurnMode(newMode) }
                },
                imageScaleType = settings.reader.imageScaleType,
                onSetImageScaleType = { newScale ->
                    scope.launch { settingsStore.setImageScaleType(newScale) }
                },
                cropBorders = settings.reader.cropBorders,
                onSetCropBorders = { newCrop ->
                    scope.launch { settingsStore.setCropBorders(newCrop) }
                },
                navigationMode = settings.reader.navigationMode,
                onSetNavigationMode = { newNav ->
                    if (newNav != settings.reader.navigationMode &&
                        newNav != ReaderNavigationMode.Disabled
                    ) {
                        tapZoneOverlayVisible = true
                    }
                    scope.launch { settingsStore.setNavigationMode(newNav) }
                },
                tappingInvertMode = settings.reader.tappingInvertMode,
                onSetTappingInvertMode = { newInvert ->
                    if (newInvert != settings.reader.tappingInvertMode &&
                        settings.reader.navigationMode != ReaderNavigationMode.Disabled
                    ) {
                        tapZoneOverlayVisible = true
                    }
                    scope.launch { settingsStore.setTappingInvertMode(newInvert) }
                },
                autoWebtoonMode = settings.reader.autoWebtoonMode,
                onSetAutoWebtoonMode = { newAuto ->
                    scope.launch { settingsStore.setAutoWebtoonMode(newAuto) }
                },
                webtoonSidePadding = settings.reader.webtoonSidePadding,
                onSetWebtoonSidePadding = { newPadding ->
                    scope.launch { settingsStore.setWebtoonSidePadding(newPadding) }
                },
                overviewMode = settings.reader.overviewMode,
                onSetOverviewMode = { enabled ->
                    scope.launch { settingsStore.setOverviewMode(enabled) }
                },
                // Fall back to the current chapter so the list is never empty
                // (e.g. offline opens where the volumes call failed).
                chapters = chapterSequence.ifEmpty { listOf(currentChapter) },
                currentChapterId = currentChapterId,
                onSelectChapter = { target ->
                    switchChapter(target, false)
                }
            )
            }
        }

    }
}

