package com.bunko.reader.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.bunko.reader.ChapterDto
import com.bunko.reader.CollectionDto
import com.bunko.reader.GenreTagDto
import com.bunko.reader.BunkoLog
import com.bunko.reader.KavitaApi
import com.bunko.reader.KavitaClient
import com.bunko.reader.KavitaSession
import com.bunko.reader.KavitaSessionStore
import com.bunko.reader.ReadingListDto
import com.bunko.reader.SearchHistoryStore
import com.bunko.reader.SearchResultDto
import com.bunko.reader.SearchResultGroupDto
import com.bunko.reader.SeriesByIdsDto
import com.bunko.reader.SeriesDto
import com.bunko.reader.SeriesFilterStatementDto
import com.bunko.reader.SeriesFilterV2Dto
import com.bunko.reader.TagDto
import com.bunko.reader.ui.DarkLoadingState
import com.bunko.reader.ui.DarkMessageState
import com.bunko.reader.ui.browse.BrowsePageScaffold
import com.bunko.reader.ui.browse.LazyGridLoadMoreEffect
import com.bunko.reader.ui.browse.PagingFooter
import com.bunko.reader.ui.browse.PosterGrid
import com.bunko.reader.ui.browse.SeriesPosterCard
import com.bunko.reader.ui.browse.SeriesShelfItemSpacing
import com.bunko.reader.ui.browse.SeriesShelfItemWidth
import com.bunko.reader.ui.browse.seriesShelfHeight
import com.bunko.reader.ui.theme.BunkoBackground
import com.bunko.reader.ui.theme.BunkoSurface

enum class SearchSeriesTarget(val routeValue: String, val titlePrefix: String) {
    Person("person", "by"),
    Publisher("publisher", "Publisher"),
    Imprint("imprint", "Imprint"),
    Genre("genre", "Genre"),
    Tag("tag", "Tag"),
    Collection("collection", "Collection"),
    ReadingList("reading-list", "Reading List");

    companion object {
        fun fromRouteValue(value: String?): SearchSeriesTarget? {
            return entries.firstOrNull { it.routeValue == value }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeSearchScreen(
    api: KavitaApi?,
    session: KavitaSession,
    historyStore: SearchHistoryStore,
    query: String,
    onQueryChange: (String) -> Unit,
    listState: LazyListState,
    onSelectSeries: (SeriesDto) -> Unit,
    onOpenFilteredSeries: (SearchSeriesTarget, Int, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val recentQueries by historyStore.recentQueries.collectAsState(initial = emptyList())

    var result by remember { mutableStateOf(SearchResultGroupDto()) }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(api, query, retryKey) {
        val trimmed = query.trim()
        if (trimmed.isBlank() || api == null) {
            searching = false
            searchError = null
            result = SearchResultGroupDto()
            return@LaunchedEffect
        }
        searchError = null
        delay(SearchDebounceMs)
        searching = true
        try {
            result = api.search(trimmed, includeChapterAndFiles = true)
            if (!result.isEmpty()) historyStore.record(trimmed)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            BunkoLog.w("Could not search Kavita.", t)
            result = SearchResultGroupDto()
            searchError = t.message ?: t.toString()
        } finally {
            searching = false
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .background(BunkoBackground),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        item {
            SearchBar(
                inputField = {
                    SearchBarDefaults.InputField(
                        query = query,
                        onQueryChange = onQueryChange,
                        onSearch = {
                            onQueryChange(it)
                            scope.launch { historyStore.record(it) }
                        },
                        expanded = false,
                        onExpandedChange = {},
                        placeholder = { Text("Search") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotBlank()) {
                                IconButton(onClick = { onQueryChange("") }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                                }
                            }
                        }
                    )
                },
                expanded = false,
                onExpandedChange = {},
                modifier = Modifier.fillMaxWidth(),
            ) {}
        }

        if (query.isBlank()) {
            item {
                RecentSearches(
                    queries = recentQueries,
                    onPick = onQueryChange
                )
            }
            return@LazyColumn
        }

        if (searching) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        searchError?.let { message ->
            item {
                DarkMessageState(
                    title = "Could not search",
                    body = message,
                    actionLabel = "Retry",
                    onAction = { retryKey++ }
                )
            }
            return@LazyColumn
        }

        val series = result.series.map { it.toSeriesDto() }
        if (series.isNotEmpty()) {
            item {
                SearchSeriesSection(
                    title = "Series",
                    series = series,
                    session = session,
                    onSelectSeries = onSelectSeries
                )
            }
        }
        if (result.persons.isNotEmpty()) {
            item {
                SearchChipSection(
                    title = "Persons",
                    chips = result.persons.mapNotNull { person ->
                        val id = person.id ?: return@mapNotNull null
                        SearchChipItem(id, person.name.orEmpty())
                    },
                    onClick = { onOpenFilteredSeries(SearchSeriesTarget.Person, it.id, it.label) }
                )
            }
        }
        if (result.genres.isNotEmpty()) {
            item {
                SearchChipSection(
                    title = "Genres",
                    chips = result.genres.mapNotNull { it.toChipItem() },
                    onClick = { onOpenFilteredSeries(SearchSeriesTarget.Genre, it.id, it.label) }
                )
            }
        }
        if (result.tags.isNotEmpty()) {
            item {
                SearchChipSection(
                    title = "Tags",
                    chips = result.tags.mapNotNull { it.toChipItem() },
                    onClick = { onOpenFilteredSeries(SearchSeriesTarget.Tag, it.id, it.label) }
                )
            }
        }
        if (result.collections.isNotEmpty()) {
            item {
                SearchChipSection(
                    title = "Collections",
                    chips = result.collections.map { SearchChipItem(it.id, it.title) },
                    onClick = { onOpenFilteredSeries(SearchSeriesTarget.Collection, it.id, it.label) }
                )
            }
        }
        if (result.readingLists.isNotEmpty()) {
            item {
                SearchChipSection(
                    title = "Reading Lists",
                    chips = result.readingLists.map { SearchChipItem(it.id, it.title ?: "Reading List ${it.id}") },
                    onClick = { onOpenFilteredSeries(SearchSeriesTarget.ReadingList, it.id, it.label) }
                )
            }
        }
        if (result.chapters.isNotEmpty()) {
            item {
                SearchChapterSection(
                    chapters = result.chapters,
                    onClick = { chapter ->
                        val currentApi = api ?: return@SearchChapterSection
                        scope.launch {
                            runCatching { currentApi.seriesForChapter(chapter.id) }
                                .onSuccess { onSelectSeries(it) }
                                .onFailure {
                                    BunkoLog.w("Could not resolve series for chapter ${chapter.id}.", it)
                                }
                        }
                    }
                )
            }
        }

        if (!searching && result.isEmpty()) {
            item {
                DarkMessageState(
                    title = "No results",
                    body = "Try another title, creator, genre, or tag."
                )
            }
        }
    }
}

@Composable
private fun RecentSearches(
    queries: List<String>,
    onPick: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SearchSectionHeader(title = "Recent searches")
        if (queries.isEmpty()) {
            Text(
                text = "Your recent searches will appear here.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                queries.forEach { query ->
                    AssistChip(
                        onClick = { onPick(query) },
                        label = { Text(query, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(Icons.Filled.History, contentDescription = null) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchSeriesSection(
    title: String,
    series: List<SeriesDto>,
    session: KavitaSession,
    onSelectSeries: (SeriesDto) -> Unit
) {
    Column {
        SearchSectionHeader(title = title)
        Spacer(Modifier.height(10.dp))
        val cardShape = MaterialTheme.shapes.small
        // Same scheme as the Home shelf; shared dimensions live in BrowseComponents. A Carousel
        // is avoided here because its masked cut-off edge item trembled against the overscroll
        // spring at the right edge, and uniform cards don't need the masking.
        val shelfHeight = seriesShelfHeight()
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(SeriesShelfItemSpacing)
        ) {
            items(series, key = { it.id }) { item ->
                SeriesPosterCard(
                    series = item,
                    session = session,
                    shape = cardShape,
                    coverFillsHeight = true,
                    modifier = Modifier
                        .width(SeriesShelfItemWidth)
                        .height(shelfHeight)
                        .clickable { onSelectSeries(item) }
                )
            }
        }
    }
}

@Composable
private fun SearchChipSection(
    title: String,
    chips: List<SearchChipItem>,
    onClick: (SearchChipItem) -> Unit
) {
    if (chips.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SearchSectionHeader(title = title)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            chips.forEach { item ->
                AssistChip(
                    onClick = { onClick(item) },
                    label = { Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun SearchChapterSection(
    chapters: List<ChapterDto>,
    onClick: (ChapterDto) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SearchSectionHeader(title = "Chapters")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            chapters.forEach { chapter ->
                Surface(
                    color = BunkoSurface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onClick(chapter) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = chapter.titleName?.takeIf { it.isNotBlank() } ?: "Chapter ${chapter.id}",
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = chapter.searchSubtitle(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSectionHeader(title: String) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.onBackground,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
internal fun SearchSeriesScreen(
    sessionStore: KavitaSessionStore,
    target: SearchSeriesTarget,
    targetId: Int,
    label: String,
    onBack: () -> Unit,
    onSelectSeries: (SeriesDto) -> Unit
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    var series by remember { mutableStateOf<List<SeriesDto>>(emptyList()) }
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
        val currentApi = api ?: return
        if (!hasMore || loadingMore) return
        val requestRevision = pagingRevision
        loadingMore = true
        loadMoreError = null
        try {
            val page = currentApi.loadSearchSeriesPage(target, targetId, nextPage)
            if (requestRevision == pagingRevision) {
                series = series.appendDistinct(page.items).sortedBy { it.name }
                nextPage++
                hasMore = page.hasMore
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            if (requestRevision == pagingRevision) {
                BunkoLog.w("Could not load more filtered series for ${target.routeValue}.", t)
                loadMoreError = t.message ?: t.toString()
            }
        } finally {
            loadingMore = false
        }
    }

    LaunchedEffect(target, targetId, retryKey) {
        pagingRevision++
        loading = true
        error = null
        loadMoreError = null
        hasMore = false
        series = emptyList()
        try {
            session = sessionStore.load()
            val (loadedApi, _) = KavitaClient(ctx, sessionStore).buildApi()
            api = loadedApi
            val page = loadedApi.loadSearchSeriesPage(target, targetId, pageNumber = 0)
            series = page.items.sortedBy { it.name }
            nextPage = 1
            hasMore = page.hasMore
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            BunkoLog.w("Could not load filtered search series for ${target.routeValue}.", t)
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
            .statusBarsPadding()
            .navigationBarsPadding()
            .background(BunkoBackground)
    ) {
        BrowsePageScaffold(title = "${target.titlePrefix} $label", onBack = onBack) {
            when {
                loading -> DarkLoadingState()
                error != null -> DarkMessageState(
                    title = "Could not load results",
                    body = error ?: "Unknown error",
                    actionLabel = "Retry",
                    onAction = { retryKey++ }
                )
                series.isEmpty() -> DarkMessageState("No series", "No readable series matched this result.")
                else -> PosterGrid(
                    items = series,
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
                    SeriesPosterCard(
                        series = item,
                        session = session,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectSeries(item) }
                    )
                }
            }
        }
    }
}

private suspend fun KavitaApi.loadSearchSeriesPage(
    target: SearchSeriesTarget,
    targetId: Int,
    pageNumber: Int
): SeriesPage {
    return when (target) {
        SearchSeriesTarget.Person -> allSeriesV2(
            body = SeriesFilterV2Dto(
                statements = PersonFilterFields.map { field ->
                    SeriesFilterStatementDto(
                        comparison = FilterContains,
                        field = field,
                        value = targetId.toString()
                    )
                },
                combination = FilterOr
            ),
            pageNumber = pageNumber,
            pageSize = SearchFilteredSeriesPageSize
        ).let { SeriesPage(it, it.size == SearchFilteredSeriesPageSize) }
        SearchSeriesTarget.Publisher -> allSeriesV2(
            searchFilter(SeriesFilterFieldPublisher, targetId),
            pageNumber,
            SearchFilteredSeriesPageSize
        ).let { SeriesPage(it, it.size == SearchFilteredSeriesPageSize) }
        SearchSeriesTarget.Imprint -> allSeriesV2(
            searchFilter(SeriesFilterFieldImprint, targetId),
            pageNumber,
            SearchFilteredSeriesPageSize
        ).let { SeriesPage(it, it.size == SearchFilteredSeriesPageSize) }
        SearchSeriesTarget.Genre -> allSeriesV2(
            searchFilter(SeriesFilterFieldGenres, targetId),
            pageNumber,
            SearchFilteredSeriesPageSize
        ).let { SeriesPage(it, it.size == SearchFilteredSeriesPageSize) }
        SearchSeriesTarget.Tag -> allSeriesV2(
            searchFilter(SeriesFilterFieldTags, targetId),
            pageNumber,
            SearchFilteredSeriesPageSize
        ).let { SeriesPage(it, it.size == SearchFilteredSeriesPageSize) }
        SearchSeriesTarget.Collection -> allSeriesV2(
            searchFilter(SeriesFilterFieldCollections, targetId),
            pageNumber,
            SearchFilteredSeriesPageSize
        ).let { SeriesPage(it, it.size == SearchFilteredSeriesPageSize) }
        SearchSeriesTarget.ReadingList -> {
            if (pageNumber > 0) {
                SeriesPage(emptyList(), hasMore = false)
            } else {
                val ids = readingListItems(targetId).map { it.seriesId }.distinct()
                SeriesPage(
                    items = if (ids.isEmpty()) emptyList() else seriesByIds(SeriesByIdsDto(ids)),
                    hasMore = false
                )
            }
        }
    }
}

private fun searchFilter(field: Int, id: Int): SeriesFilterV2Dto {
    return SeriesFilterV2Dto(
        statements = listOf(
            SeriesFilterStatementDto(
                comparison = FilterContains,
                field = field,
                value = id.toString()
            )
        )
    )
}

private fun SearchResultGroupDto.isEmpty(): Boolean {
    return series.isEmpty() &&
        collections.isEmpty() &&
        readingLists.isEmpty() &&
        persons.isEmpty() &&
        genres.isEmpty() &&
        tags.isEmpty() &&
        chapters.isEmpty()
}

private fun SearchResultDto.toSeriesDto(): SeriesDto {
    return SeriesDto(
        id = seriesId,
        name = name,
        originalName = originalName,
        localizedName = localizedName,
        libraryId = libraryId,
        libraryName = libraryName
    )
}

private fun GenreTagDto.toChipItem(): SearchChipItem? {
    val genreId = id ?: return null
    return SearchChipItem(genreId, title.orEmpty())
}

private fun TagDto.toChipItem(): SearchChipItem? {
    val tagId = id ?: return null
    return SearchChipItem(tagId, title.orEmpty())
}

private fun ChapterDto.searchSubtitle(): String {
    val numberText = number?.toString()?.trim('"')?.takeIf { it.isNotBlank() }
        ?: title?.takeIf { it.isNotBlank() }
        ?: id.toString()
    val pageText = pages?.takeIf { it > 0 }?.let { "$it pages" }
    return listOfNotNull("Chapter $numberText", pageText).joinToString(" • ")
}

private data class SearchChipItem(
    val id: Int,
    val label: String
)

private const val SearchDebounceMs = 400L
private const val SearchFilteredSeriesPageSize = 300

private const val FilterOr = 0
private const val FilterContains = 5
private const val SeriesFilterFieldTags = 6
private const val SeriesFilterFieldCollections = 7
private const val SeriesFilterFieldTranslators = 8
private const val SeriesFilterFieldCharacters = 9
private const val SeriesFilterFieldPublisher = 10
private const val SeriesFilterFieldEditor = 11
private const val SeriesFilterFieldCoverArtist = 12
private const val SeriesFilterFieldLetterer = 13
private const val SeriesFilterFieldColorist = 14
private const val SeriesFilterFieldInker = 15
private const val SeriesFilterFieldPenciller = 16
private const val SeriesFilterFieldWriters = 17
private const val SeriesFilterFieldGenres = 18
private const val SeriesFilterFieldImprint = 29
private const val SeriesFilterFieldTeam = 30
private const val SeriesFilterFieldLocation = 31

private val PersonFilterFields = listOf(
    SeriesFilterFieldTranslators,
    SeriesFilterFieldCharacters,
    SeriesFilterFieldPublisher,
    SeriesFilterFieldEditor,
    SeriesFilterFieldCoverArtist,
    SeriesFilterFieldLetterer,
    SeriesFilterFieldColorist,
    SeriesFilterFieldInker,
    SeriesFilterFieldPenciller,
    SeriesFilterFieldWriters,
    SeriesFilterFieldImprint,
    SeriesFilterFieldTeam,
    SeriesFilterFieldLocation
)
