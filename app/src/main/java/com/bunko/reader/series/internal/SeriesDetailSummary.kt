package com.bunko.reader.series.internal

import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.bunko.reader.ui.theme.accessibleContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.bunko.reader.ChapterDto
import com.bunko.reader.CreateReadingListDto
import com.bunko.reader.KavitaApi
import com.bunko.reader.KavitaSession
import com.bunko.reader.PersonDto
import com.bunko.reader.ReadingListDto
import com.bunko.reader.RefreshSeriesDto
import com.bunko.reader.SeriesDto
import com.bunko.reader.SeriesMetadataDto
import com.bunko.reader.UpdateReadingListBySeriesDto
import com.bunko.reader.UpdateWantToReadDto
import com.bunko.reader.library.SearchSeriesTarget
import com.bunko.reader.normalizeKavitaBaseUrl
import com.bunko.reader.ui.KavitaCoverAspectRatio
import com.bunko.reader.ui.seriesCoverUrl
import com.bunko.reader.ui.seriesInitial
import com.bunko.reader.ui.theme.BunkoBackground
/** Internal to series, not for external use. */
@Composable
internal fun SeriesDetailSummary(
    series: SeriesDto,
    metadata: SeriesMetadataDto?,
    continueChapter: ChapterDto?,
    chapterCards: List<ChapterCardItem>,
    volumeCount: Int,
    session: KavitaSession,
    api: KavitaApi,
    isAdmin: Boolean,
    onOpenFilteredSeries: (SearchSeriesTarget, Int, String) -> Unit,
    onPick: (chapterId: Int, volumeId: Int, initialPage: Int?) -> Unit,
    onMessage: (String) -> Unit
) {
    val summary = metadata?.summary?.takeIf { it.isNotBlank() }
    val creditChips = metadata.creditChips()
    val publisherChips = metadata?.publishers.orEmpty().personChips()
    val imprintChips = metadata?.imprints.orEmpty().personChips()
    val genreChips = metadata?.genres.orEmpty().mapNotNull { genre ->
        val id = genre.id ?: return@mapNotNull null
        val title = genre.title?.trim().orEmpty()
        if (title.isBlank()) null else id to title
    }
    val tagChips = metadata?.tags.orEmpty().mapNotNull { tag ->
        val id = tag.id ?: return@mapNotNull null
        val title = tag.title?.trim().orEmpty()
        if (title.isBlank()) null else id to title
    }
    val continueButtonText = series.primaryReadActionText()
    val isReread = continueButtonText == "Re-Read"
    val continueItem = if (isReread) {
        chapterCards.firstOrNull()
    } else {
        continueChapter?.let { chapter ->
            chapterCards.firstOrNull { it.chapter.id == chapter.id }
        } ?: chapterCards.firstOrNull()
    }
    val continueButtonColor = series.coverActionColor()
    val summaryActionColor = continueButtonColor.readableAccentOn(BunkoBackground)
    var summaryExpanded by remember(summary) { mutableStateOf(false) }
    var summaryCanExpand by remember(summary) { mutableStateOf(false) }

    val readButton: @Composable () -> Unit = {
        continueItem?.let { item ->
            SeriesReadSplitButton(
                text = continueButtonText,
                containerColor = continueButtonColor,
                series = series,
                api = api,
                isAdmin = isAdmin,
                onRead = { onPick(item.chapter.id, item.volume.id, if (isReread) 0 else null) },
                onMessage = onMessage,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    val summaryBlock: @Composable () -> Unit = {
        summary?.let { text ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = text,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (summaryExpanded) Int.MAX_VALUE else 8,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { layoutResult ->
                        if (!summaryExpanded && layoutResult.hasVisualOverflow) {
                            summaryCanExpand = true
                        }
                    }
                )
                if (summaryCanExpand || summaryExpanded) {
                    TextButton(
                        onClick = { summaryExpanded = !summaryExpanded },
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = summaryActionColor)
                    ) {
                        Text(if (summaryExpanded) "Show less" else "Show more")
                    }
                }
            }
        }
    }

    // Beside the cover the labels are redundant, so callers can hide them; stacked below
    // (narrow layout) they keep their titles to match the Genres/Tags sections.
    val creditsBlock: @Composable (showTitle: Boolean) -> Unit = { showTitle ->
        DetailChipBlock(
            title = "Credits",
            showTitle = showTitle,
            horizontalScroll = true,
            chips = creditChips.map { (id, name) ->
                name to { onOpenFilteredSeries(SearchSeriesTarget.Person, id, name) }
            }
        )
    }

    // Publisher and imprint share one "Publisher" row; each chip still filters its own field.
    val publisherBlock: @Composable (showTitle: Boolean) -> Unit = { showTitle ->
        DetailChipBlock(
            title = "Publisher",
            showTitle = showTitle,
            horizontalScroll = true,
            chips = publisherChips.map { (id, name) ->
                name to { onOpenFilteredSeries(SearchSeriesTarget.Publisher, id, name) }
            } + imprintChips.map { (id, name) ->
                name to { onOpenFilteredSeries(SearchSeriesTarget.Imprint, id, name) }
            }
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // Phone width and up: lay the cover beside the info column (with the short
        // Credits/Publisher blocks) instead of centering the cover with wasted space on
        // either side. The summary stays full width below where it is readable. Only very
        // narrow widths fall back to the stacked cover-on-top layout.
        val heroSideBySide = maxWidth >= 340.dp
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (heroSideBySide) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    SeriesCover(series, session, Modifier.width(150.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SeriesDetailHeroInfo(
                            series = series,
                            metadata = metadata,
                            issueCount = chapterCards.size,
                            volumeCount = volumeCount,
                            modifier = Modifier.fillMaxWidth()
                        )
                        // Keep the credits/publisher rows spaced like the wrapped chips
                        // within a single block (4dp) so they read as one chip list.
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            creditsBlock(false)
                            publisherBlock(false)
                        }
                    }
                }
            } else {
                SeriesDetailHero(
                    series = series,
                    metadata = metadata,
                    issueCount = chapterCards.size,
                    volumeCount = volumeCount,
                    session = session
                )
            }

            readButton()
            summaryBlock()
            if (!heroSideBySide) {
                creditsBlock(true)
                publisherBlock(true)
            }

            DetailChipBlock(
                title = "Genres",
                chips = genreChips.map { (id, title) ->
                    title to { onOpenFilteredSeries(SearchSeriesTarget.Genre, id, title) }
                }
            )
            DetailChipBlock(
                title = "Tags",
                chips = tagChips.map { (id, title) ->
                    title to { onOpenFilteredSeries(SearchSeriesTarget.Tag, id, title) }
                }
            )
        }
    }
}

/** Distinct credited people across every role, as (personId, name) pairs. */
private fun SeriesMetadataDto?.creditChips(): List<Pair<Int, String>> {
    if (this == null) return emptyList()
    return listOf(
        writers,
        coverArtists,
        pencillers,
        inkers,
        colorists,
        letterers,
        editors,
        translators
    )
        .flatMap { it.orEmpty() }
        .mapNotNull { person ->
            val id = person.id ?: return@mapNotNull null
            val name = person.name?.trim().orEmpty()
            if (name.isBlank()) null else id to name
        }
        .distinctBy { it.first }
}

/** Distinct people from one metadata role list, as (personId, name) pairs. */
private fun List<PersonDto>.personChips(): List<Pair<Int, String>> {
    return mapNotNull { person ->
        val id = person.id ?: return@mapNotNull null
        val name = person.name?.trim().orEmpty()
        if (name.isBlank()) null else id to name
    }.distinctBy { it.first }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailChipBlock(
    title: String,
    chips: List<Pair<String, () -> Unit>>,
    showTitle: Boolean = true,
    horizontalScroll: Boolean = false
) {
    if (chips.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (showTitle) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
        val chipContent: @Composable () -> Unit = {
            chips.forEach { (label, onClick) ->
                SuggestionChip(
                    onClick = onClick,
                    label = { Text(label) }
                )
            }
        }
        if (horizontalScroll) {
            // Keep the chips on a single scrollable row instead of wrapping, so a long
            // credit/publisher list stays compact next to the cover. Fade the scrollable
            // edge so a clipped chip reads as "there's more", not a rendering glitch.
            val scrollState = rememberScrollState()
            Row(
                modifier = Modifier
                    .horizontalFadingEdges(scrollState)
                    .horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                chipContent()
            }
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                chipContent()
            }
        }
    }
}

/**
 * Fades the leading/trailing edge of a horizontally scrollable row so a clipped chip reads as
 * "scroll for more" rather than a hard cut-off. A fade only appears on an edge that can still
 * scroll, so a row whose chips all fit shows no fade at all.
 */
private fun Modifier.horizontalFadingEdges(scrollState: ScrollState): Modifier =
    this
        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()
            val edge = 24.dp.toPx().coerceAtMost(size.width / 2f)
            if (edge <= 0f) return@drawWithContent
            if (scrollState.canScrollBackward) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        colorStops = arrayOf(0f to Color.Transparent, edge / size.width to Color.Black),
                        startX = 0f,
                        endX = size.width
                    ),
                    blendMode = BlendMode.DstIn
                )
            }
            if (scrollState.canScrollForward) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        colorStops = arrayOf((size.width - edge) / size.width to Color.Black, 1f to Color.Transparent),
                        startX = 0f,
                        endX = size.width
                    ),
                    blendMode = BlendMode.DstIn
                )
            }
        }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SeriesReadSplitButton(
    text: String,
    containerColor: Color,
    series: SeriesDto,
    api: KavitaApi,
    isAdmin: Boolean,
    onRead: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var menuExpanded by remember { mutableStateOf(false) }
    var showingReadingLists by remember { mutableStateOf(false) }
    var readingLists by remember { mutableStateOf<List<ReadingListDto>>(emptyList()) }
    var createReadingListDialog by remember { mutableStateOf(false) }
    var newReadingListTitle by remember { mutableStateOf("") }
    var actionRunning by remember { mutableStateOf(false) }

    fun launchAction(block: suspend () -> Unit) {
        if (actionRunning) return
        actionRunning = true
        scope.launch {
            try {
                block()
            } finally {
                actionRunning = false
            }
        }
    }
    val colors = ButtonDefaults.buttonColors(
        containerColor = containerColor,
        contentColor = containerColor.accessibleContentColor()
    )
    val mainMenuItems = buildList {
        add(SeriesMenuAction.WantToRead)
        add(SeriesMenuAction.AddToReadingList)
        if (isAdmin) add(SeriesMenuAction.Refresh)
    }

    BoxWithConstraints(modifier) {
        val trailingWidth = 48.dp
        val leadingWidth = (maxWidth - trailingWidth - SplitButtonDefaults.Spacing).coerceAtLeast(48.dp)

        SplitButtonLayout(
            leadingButton = {
                SplitButtonDefaults.LeadingButton(
                    onClick = onRead,
                    modifier = Modifier.width(leadingWidth),
                    colors = colors
                ) {
                    Text(
                        text = text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            trailingButton = {
                Box {
                    SplitButtonDefaults.TrailingButton(
                        checked = menuExpanded,
                        onCheckedChange = { menuExpanded = it },
                        modifier = Modifier
                            .width(trailingWidth)
                            .semantics {
                                stateDescription = if (menuExpanded) "Expanded" else "Collapsed"
                                contentDescription = "More reading actions"
                            },
                        colors = colors
                    ) {
                        val rotation by animateFloatAsState(
                            targetValue = if (menuExpanded) 180f else 0f,
                            label = "Reading actions arrow rotation"
                        )
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier
                                .size(SplitButtonDefaults.TrailingIconSize)
                                .graphicsLayer { rotationZ = rotation }
                        )
                    }

                    DropdownMenuPopup(
                        expanded = menuExpanded,
                        onDismissRequest = {
                            menuExpanded = false
                            showingReadingLists = false
                        },
                        modifier = Modifier.width(240.dp)
                    ) {
                        DropdownMenuGroup(
                            shapes = MenuDefaults.groupShape(index = 0, count = 1)
                        ) {
                            val labels = if (showingReadingLists) {
                                listOf("Back") + readingLists.map { list ->
                                    list.title?.takeIf { it.isNotBlank() } ?: "Reading List ${list.id}"
                                } + "New Reading List"
                            } else {
                                mainMenuItems.map { it.label }
                            }

                            labels.forEachIndexed { index, label ->
                                val itemShape = MenuDefaults.itemShape(
                                    index = index,
                                    count = labels.size
                                ).shape

                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        if (showingReadingLists) {
                                            if (index == 0) {
                                                showingReadingLists = false
                                            } else if (index == labels.lastIndex) {
                                                menuExpanded = false
                                                showingReadingLists = false
                                                newReadingListTitle = ""
                                                createReadingListDialog = true
                                            } else {
                                                val readingList = readingLists[index - 1]
                                                menuExpanded = false
                                                showingReadingLists = false
                                                launchAction {
                                                    try {
                                                        api.addSeriesToReadingList(
                                                            UpdateReadingListBySeriesDto(
                                                                seriesId = series.id,
                                                                readingListId = readingList.id
                                                            )
                                                        )
                                                        onMessage("Added to ${readingList.title ?: "reading list"}")
                                                    } catch (c: CancellationException) {
                                                        throw c
                                                    } catch (_: Throwable) {
                                                        onMessage("Could not update reading list")
                                                    }
                                                }
                                            }
                                        } else {
                                            when (mainMenuItems[index]) {
                                                SeriesMenuAction.WantToRead -> {
                                                    menuExpanded = false
                                                    launchAction {
                                                        try {
                                                            api.addSeriesToWantToRead(
                                                                UpdateWantToReadDto(listOf(series.id))
                                                            )
                                                            onMessage("Added to Want to Read")
                                                        } catch (c: CancellationException) {
                                                            throw c
                                                        } catch (_: Throwable) {
                                                            onMessage("Could not update Want to Read")
                                                        }
                                                    }
                                                }
                                                SeriesMenuAction.AddToReadingList -> launchAction {
                                                    try {
                                                        readingLists = api.readingLists()
                                                        showingReadingLists = true
                                                    } catch (c: CancellationException) {
                                                        throw c
                                                    } catch (_: Throwable) {
                                                        menuExpanded = false
                                                        onMessage("Could not load reading lists")
                                                    }
                                                }
                                                SeriesMenuAction.Refresh -> {
                                                    menuExpanded = false
                                                    val libraryId = series.libraryId
                                                    if (libraryId == null) {
                                                        onMessage("Library information is unavailable")
                                                    } else {
                                                        launchAction {
                                                            val request = RefreshSeriesDto(
                                                                libraryId = libraryId,
                                                                seriesId = series.id
                                                            )
                                                            try {
                                                                api.scanSeries(request)
                                                                api.analyzeSeries(request)
                                                                api.refreshSeriesMetadata(request)
                                                                onMessage("Series refresh requested")
                                                            } catch (c: CancellationException) {
                                                                throw c
                                                            } catch (_: Throwable) {
                                                                onMessage("Could not refresh series")
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    shape = itemShape,
                                    enabled = !actionRunning
                                )
                            }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (createReadingListDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!actionRunning) createReadingListDialog = false
            },
            title = { Text("New Reading List") },
            text = {
                OutlinedTextField(
                    value = newReadingListTitle,
                    onValueChange = { newReadingListTitle = it },
                    label = { Text("Title") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !actionRunning && newReadingListTitle.trim().isNotBlank(),
                    onClick = {
                        val title = newReadingListTitle.trim()
                        launchAction {
                            try {
                                val list = api.createReadingList(CreateReadingListDto(title))
                                api.addSeriesToReadingList(
                                    UpdateReadingListBySeriesDto(
                                        seriesId = series.id,
                                        readingListId = list.id
                                    )
                                )
                                readingLists = (readingLists + list)
                                    .distinctBy { it.id }
                                    .sortedBy { it.title ?: "Reading List ${it.id}" }
                                createReadingListDialog = false
                                onMessage("Added to ${list.title ?: "reading list"}")
                            } catch (c: CancellationException) {
                                throw c
                            } catch (_: Throwable) {
                                onMessage("Could not create reading list")
                            }
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !actionRunning,
                    onClick = { createReadingListDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

private enum class SeriesMenuAction(val label: String) {
    WantToRead("Add to Want to Read"),
    AddToReadingList("Add to Reading List"),
    Refresh("Refresh")
}

@Composable
private fun SeriesDetailHero(
    series: SeriesDto,
    metadata: SeriesMetadataDto?,
    issueCount: Int,
    volumeCount: Int,
    session: KavitaSession
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxWidth < 420.dp
        val coverWidth = if (compact) 180.dp else 150.dp
        if (compact) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SeriesCover(series, session, Modifier.width(coverWidth))
                SeriesDetailHeroInfo(
                    series = series,
                    metadata = metadata,
                    issueCount = issueCount,
                    volumeCount = volumeCount,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                SeriesCover(series, session, Modifier.width(coverWidth))
                SeriesDetailHeroInfo(
                    series = series,
                    metadata = metadata,
                    issueCount = issueCount,
                    volumeCount = volumeCount,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SeriesCover(series: SeriesDto, session: KavitaSession, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .aspectRatio(KavitaCoverAspectRatio)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest),
        contentAlignment = Alignment.Center
    ) {
        if (session.baseUrl.isNotBlank() && session.apiKey.isNotBlank()) {
            val request = remember(context, session.baseUrl, session.apiKey, series.id) {
                ImageRequest.Builder(context)
                    .data(seriesCoverUrl(session, series.id))
                    .crossfade(180)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = series.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                seriesInitial(series.name),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SeriesDetailHeroInfo(
    series: SeriesDto,
    metadata: SeriesMetadataDto?,
    issueCount: Int,
    volumeCount: Int,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier) {
        val creatorMaxChars = when {
            maxWidth < 220.dp -> 18
            maxWidth < 320.dp -> 28
            else -> 42
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = series.name,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            series.detailMetaLines(
                metadata,
                creatorMaxChars,
                issueCount,
                volumeCount,
                includePeople = false,
                includePublisher = false
            ).forEach { line ->
                Text(
                    text = line,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
