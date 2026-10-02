package com.bunko.reader.library

import android.net.Uri
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.bunko.reader.ChapterDto
import com.bunko.reader.GroupedSeriesDto
import com.bunko.reader.KavitaApi
import com.bunko.reader.KomgaSessionStore
import com.bunko.reader.KomgaKavitaAdapter
import com.bunko.reader.serverBackend
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
import com.bunko.reader.series.KomgaBookGrid
import com.bunko.reader.series.KomgaLibraryBook
import com.bunko.reader.series.chapterCoverUrl
import com.bunko.reader.series.isRead
import com.bunko.reader.series.internal.coverActionColor
import com.bunko.reader.ui.DarkLoadingState
import com.bunko.reader.ui.DarkMessageState
import com.bunko.reader.ui.KavitaCoverAspectRatio
import com.bunko.reader.ui.browse.BrowsePageScaffold
import com.bunko.reader.ui.browse.LazyGridLoadMoreEffect
import com.bunko.reader.ui.browse.PagingFooter
import com.bunko.reader.ui.browse.PosterGrid
import com.bunko.reader.ui.browse.SeriesPosterCard
import com.bunko.reader.ui.theme.BunkoBackground
import com.bunko.reader.series.KomgaBookList
import com.bunko.reader.ui.browse.SeriesListItem
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState

@Composable
internal fun SeriesShelfScreen(
    sessionStore: KavitaSessionStore,
    komgaSessionStore: KomgaSessionStore,
    shelfKind: HomeShelfKind,
    onBack: () -> Unit,
    statusBarPadding: Boolean = true,
    navigationBarPadding: Boolean = true,
    isGridView: Boolean = true,
    gridCoverSize: Int = 130,
    listCoverSize: Int = 80,
    onSelectSeries: (SeriesDto) -> Unit,
    onOpenBook: ((libraryId: Int, seriesId: Int, volumeId: Int, chapterId: Int) -> Unit)? = null
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    var series by remember { mutableStateOf<List<SeriesDto>>(emptyList()) }
    var komgaBooks by remember { mutableStateOf<List<KomgaLibraryBook>?>(null) }
    var komgaAdapter by remember { mutableStateOf<KomgaKavitaAdapter?>(null) }
    var session by remember { mutableStateOf(KavitaSession()) }
    var api by remember { mutableStateOf<KavitaApi?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableIntStateOf(0) }
    var nextPage by remember { mutableIntStateOf(0) }
    var hasMore by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var loadMoreError by remember { mutableStateOf<String?>(null) }
    var pagingRevision by remember { mutableIntStateOf(0) }

    suspend fun loadNextPage() {
        if (komgaBooks != null) return
        val currentApi = api ?: return
        if (!hasMore || loadingMore) return
        val requestRevision = pagingRevision
        loadingMore = true
        loadMoreError = null
        try {
            val page = currentApi.loadShelfSeriesPage(shelfKind, nextPage)
            if (requestRevision == pagingRevision) {
                series = series.appendDistinct(page.items)
                nextPage++
                hasMore = page.hasMore
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            if (requestRevision == pagingRevision) {
                BunkoLog.w("Could not load more shelf ${shelfKind.routeValue}.", t)
                loadMoreError = t.message ?: t.toString()
            }
        } finally {
            loadingMore = false
        }
    }

    LaunchedEffect(shelfKind, retryKey) {
        pagingRevision++
        loading = true
        error = null
        loadMoreError = null
        hasMore = false
        series = emptyList()
        try {
            session = sessionStore.load()
            val backend = ctx.serverBackend(sessionStore, komgaSessionStore)
            val loadedApi = backend.api
            api = loadedApi
            if (backend.isKomga && loadedApi is KomgaKavitaAdapter) {
                // Komga shelves list individual books (History = on-deck books).
                komgaAdapter = loadedApi
                val rawBooks = when (shelfKind) {
                    HomeShelfKind.OnDeck -> loadedApi.onDeckBooks(page = 0, size = 200)
                    else -> loadedApi.latestBooks(page = 0, size = 200)
                }
                komgaBooks = rawBooks.map { book ->
                    KomgaLibraryBook(
                        book = book,
                        chapter = loadedApi.bookToChapter(book),
                        route = loadedApi.routeForBook(book)
                    )
                }
                series = emptyList()
                hasMore = false
            } else {
                komgaAdapter = null
                komgaBooks = null
                val page = loadedApi.loadShelfSeriesPage(shelfKind, pageNumber = 0)
                series = page.items
                nextPage = 1
                hasMore = page.hasMore
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            BunkoLog.w("Could not load shelf ${shelfKind.routeValue}.", t)
            error = t.message ?: t.toString()
        } finally {
            loading = false
        }
    }

    LazyGridLoadMoreEffect(
        state = gridState,
        itemCount = series.size,
        hasMore = hasMore,
        loadingMore = loadingMore,
        loadMoreError = loadMoreError,
        onLoadMore = { scope.launch { loadNextPage() } }
    )

    Box(
        Modifier
            .fillMaxSize()
            .then(if (navigationBarPadding) Modifier.navigationBarsPadding() else Modifier)
            .background(BunkoBackground)
    ) {
        val booksSnapshot = komgaBooks
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
                onSelectSeries(
                    SeriesDto(
                        id = entry.route.seriesId,
                        name = entry.book.seriesTitle,
                        libraryId = entry.route.libraryId
                    )
                )
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
                        komgaBooks = komgaBooks?.map { existing ->
                            if (existing.route.chapterId == entry.route.chapterId) {
                                existing.copy(chapter = existing.chapter.copy(pagesRead = 0))
                            } else existing
                        }
                    } else {
                        adapter.markChapterRead(
                            MarkChapterReadDto(
                                seriesId = entry.route.seriesId,
                                chapterId = entry.route.chapterId,
                                generateReadingSession = false
                            )
                        )
                        komgaBooks = komgaBooks?.map { existing ->
                            if (existing.route.chapterId == entry.route.chapterId) {
                                existing.copy(chapter = existing.chapter.copy(pagesRead = existing.chapter.pages ?: 0))
                            } else existing
                        }
                    }
                } catch (c: CancellationException) {
                    throw c
                } catch (t: Throwable) {
                    BunkoLog.w("Could not toggle read state for Komga book ${entry.route.chapterId}.", t)
                }
            }
        }
        BrowsePageScaffold(title = shelfKind.title, onBack = onBack, statusBarPadding = statusBarPadding) {
            when {
                loading -> DarkLoadingState()
                error != null -> DarkMessageState(
                    title = "Could not load ${shelfKind.title}",
                    body = error ?: "Unknown error",
                    actionLabel = "Retry",
                    onAction = { retryKey++ }
                )
                booksSnapshot != null -> {
                    if (booksSnapshot.isEmpty()) {
                        DarkMessageState(shelfKind.title, shelfKind.emptyMessage)
                    } else if (isGridView) {
                        KomgaBookGrid(
                            books = booksSnapshot,
                            session = session,
                            gridState = gridState,
                            gridCoverSize = gridCoverSize,
                            onRead = ::openKomgaBook,
                            onToggleRead = ::toggleKomgaBookRead,
                            onViewSeries = { entry ->
                                onSelectSeries(
                                    SeriesDto(
                                        id = entry.route.seriesId,
                                        name = entry.book.seriesTitle,
                                        libraryId = entry.route.libraryId
                                    )
                                )
                            },
                            onSearchHome = {},
                            query = ""
                        )
                    } else {
                        KomgaBookList(
                            books = booksSnapshot,
                            session = session,
                            listState = listState,
                            listCoverSize = listCoverSize,
                            onRead = ::openKomgaBook,
                            onToggleRead = ::toggleKomgaBookRead,
                            onViewSeries = { entry ->
                                onSelectSeries(
                                    SeriesDto(
                                        id = entry.route.seriesId,
                                        name = entry.book.seriesTitle,
                                        libraryId = entry.route.libraryId
                                    )
                                )
                            },
                            onSearchHome = {},
                            query = ""
                        )
                    }
                }
                series.isEmpty() -> DarkMessageState(shelfKind.title, shelfKind.emptyMessage)
                isGridView -> PosterGrid(
                    items = series,
                    minSize = gridCoverSize.dp,
                    key = { it.id },
                    state = gridState,
                    footer = if (loadingMore || loadMoreError != null) {
                        {
                            PagingFooter(
                                loading = loadingMore,
                                error = loadMoreError,
                                onRetry = { scope.launch { loadNextPage() } }
                            )
                        }
                    } else null
                ) { item ->
                    com.bunko.reader.ui.browse.SeriesPosterCard(
                        series = item,
                        session = session,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectSeries(item) }
                    )
                }
                else -> androidx.compose.foundation.lazy.LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(items = series, key = { it.id }) { item ->
                        SeriesListItem(
                            series = item,
                            session = session,
                            coverWidth = listCoverSize.dp,
                            onClick = { onSelectSeries(item) }
                        )
                    }
                    if (loadingMore || loadMoreError != null) {
                        item {
                            PagingFooter(
                                loading = loadingMore,
                                error = loadMoreError,
                                onRetry = { scope.launch { loadNextPage() } }
                            )
                        }
                    }
                }
            }
        }
    }
}

private suspend fun KavitaApi.loadShelfSeriesPage(
    shelfKind: HomeShelfKind,
    pageNumber: Int
): SeriesPage {
    return when (shelfKind) {
        HomeShelfKind.OnDeck -> onDeck(pageNumber = pageNumber, pageSize = HomeShelfPageSize)
            .let { SeriesPage(it, it.size == HomeShelfPageSize) }
        HomeShelfKind.RecentlyUpdated -> recentlyUpdatedSeries(
            pageNumber = pageNumber,
            pageSize = HomeShelfPageSize
        ).let { raw ->
            SeriesPage(
                items = raw.map { it.toSeriesDto() }.distinctBy { it.id },
                hasMore = raw.size == HomeShelfPageSize
            )
        }
        HomeShelfKind.NewlyAdded -> recentlyAdded(pageNumber = pageNumber, pageSize = HomeShelfPageSize)
            .let { SeriesPage(it, it.size == HomeShelfPageSize) }
    }
}

private const val HomeShelfPageSize = 200
private fun GroupedSeriesDto.toSeriesDto(): SeriesDto {
    return SeriesDto(
        id = seriesId,
        name = seriesName ?: "Series $seriesId",
        libraryId = libraryId
    )
}
