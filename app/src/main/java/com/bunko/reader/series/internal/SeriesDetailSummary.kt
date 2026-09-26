package com.bunko.reader.series.internal

import android.net.Uri
import androidx.core.text.HtmlCompat
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
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import kotlin.math.roundToInt
import com.bunko.reader.BunkoLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
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
import com.bunko.reader.UpdateSeriesRatingDto
import com.bunko.reader.MarkChapterReadDto
import com.bunko.reader.MarkVolumesReadDto
import com.bunko.reader.library.detail.BookDetailSection
import com.bunko.reader.library.detail.BookRatingRow
import com.bunko.reader.library.detail.CoverPreviewDialog
import com.bunko.reader.library.detail.KavitaEditCoverDialog
import com.bunko.reader.library.detail.KavitaQuickActions
import com.bunko.reader.library.detail.KavitaReadingStatsCard
import com.bunko.reader.library.detail.ScrollableDescription
import com.bunko.reader.library.SearchSeriesTarget
import com.bunko.reader.normalizeKavitaBaseUrl
import com.bunko.reader.ui.KavitaCoverAspectRatio
import com.bunko.reader.ui.seriesCoverUrl
import com.bunko.reader.ui.seriesInitial
import com.bunko.reader.ui.theme.BunkoBackground

import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults

internal fun String.cleanHtmlDescription(): String {
    if (isBlank()) return ""
    if (!contains('<') && !contains('&')) return trim()
    return try {
        HtmlCompat.fromHtml(this, HtmlCompat.FROM_HTML_MODE_COMPACT)
            .toString()
            .trim()
    } catch (_: Throwable) {
        replace(Regex("<[^>]*>"), "").trim()
    }
}

/** Internal to series, not for external use. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, coil.annotation.ExperimentalCoilApi::class)
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
    downloadedChapterIds: Set<Int> = emptySet(),
    downloadingChapterIds: Set<Int> = emptySet(),
    onOpenFilteredSeries: (SearchSeriesTarget, Int, String) -> Unit,
    onPick: (chapterId: Int, volumeId: Int, initialPage: Int?) -> Unit,
    onReadIncognito: ((ChapterCardItem) -> Unit)? = null,
    onMarkRead: ((ChapterCardItem) -> Unit)? = null,
    onMarkUnread: ((ChapterCardItem) -> Unit)? = null,
    onDownload: ((ChapterCardItem) -> Unit)? = null,
    onRemoveDownload: ((ChapterCardItem) -> Unit)? = null,
    onRefreshSeries: () -> Unit = {},
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isWantToRead by remember { mutableStateOf(false) }
    var userRating by remember { mutableStateOf(0f) }
    var showCoverPreview by remember { mutableStateOf(false) }
    var showActionsDialog by remember { mutableStateOf(false) }
    var showEditCoverDialog by remember { mutableStateOf(false) }
    var isUploadingCover by remember { mutableStateOf(false) }
    var coverUpdateKey by remember { mutableStateOf(0L) }

    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                isUploadingCover = true
                try {
                    val bytes = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }
                    if (bytes != null && bytes.isNotEmpty()) {
                        val requestFile = bytes.toRequestBody("image/*".toMediaTypeOrNull())
                        val body = MultipartBody.Part.createFormData("file", "cover.jpg", requestFile)
                        val response = api.uploadSeriesCover(body, series.id)
                        if (response.isSuccessful) {
                            coverUpdateKey = System.currentTimeMillis()
                            context.imageLoader.diskCache?.clear()
                            context.imageLoader.memoryCache?.clear()
                            showEditCoverDialog = false
                            onRefreshSeries()
                            onMessage("Series cover updated")
                        } else {
                            onMessage("Failed to update cover (${response.code()})")
                        }
                    }
                } catch (t: Throwable) {
                    BunkoLog.w("Failed to upload series cover", t)
                    onMessage("Failed to upload cover: ${t.message}")
                } finally {
                    isUploadingCover = false
                }
            }
        }
    }

    fun resetCover() {
        scope.launch {
            isUploadingCover = true
            try {
                val response = api.resetSeriesCover(series.id)
                if (response.isSuccessful) {
                    coverUpdateKey = System.currentTimeMillis()
                    context.imageLoader.diskCache?.clear()
                    context.imageLoader.memoryCache?.clear()
                    showEditCoverDialog = false
                    onRefreshSeries()
                    onMessage("Series cover reset to default")
                } else {
                    onMessage("Failed to reset cover (${response.code()})")
                }
            } catch (t: Throwable) {
                BunkoLog.w("Failed to reset series cover", t)
                onMessage("Failed to reset cover: ${t.message}")
            } finally {
                isUploadingCover = false
            }
        }
    }

    LaunchedEffect(series.id) {
        runCatching {
            val ratingDto = api.seriesRating(series.id)
            userRating = ratingDto.userRating
        }
        runCatching {
            val wantList = api.wantToRead(pageSize = 200)
            isWantToRead = wantList.any { it.id == series.id }
        }
    }

    val summary = remember(metadata?.summary) {
        metadata?.summary?.cleanHtmlDescription()?.takeIf { it.isNotBlank() }
    }
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
    val coverUrl = seriesCoverUrl(session, series.id)
    val isSeriesFinished = ((series.readingProgress() ?: 0f) >= 0.999f)

    if (showCoverPreview) {
        val previewUrl = if (coverUpdateKey > 0L) "$coverUrl&t=$coverUpdateKey" else coverUrl
        CoverPreviewDialog(
            coverPath = previewUrl,
            title = series.name,
            onDismiss = { showCoverPreview = false }
        )
    }

    if (showEditCoverDialog) {
        val currentCoverUrl = if (coverUpdateKey > 0L) "$coverUrl&t=$coverUpdateKey" else coverUrl
        KavitaEditCoverDialog(
            seriesName = series.name,
            coverUrl = currentCoverUrl,
            onChangeCover = {
                coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onResetCover = ::resetCover,
            onDismiss = { showEditCoverDialog = false },
            isProcessing = isUploadingCover
        )
    }

    if (showActionsDialog) {
        SeriesActionsDialog(
            series = series,
            api = api,
            isAdmin = isAdmin,
            onEditCover = { showEditCoverDialog = true },
            onDismiss = { showActionsDialog = false },
            onMessage = onMessage
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Hero section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .width(130.dp)
                        .aspectRatio(KavitaCoverAspectRatio)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { showCoverPreview = true }
                ) {
                    SeriesCover(series, session, Modifier.fillMaxSize(), coverKey = coverUpdateKey)
                }

                AssistChip(
                    onClick = { showEditCoverDialog = true },
                    label = { Text("Edit Cover", style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                    },
                    modifier = Modifier.padding(top = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        leadingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                    border = null,
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = series.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                val releaseYear = metadata?.releaseYear
                val metaText = listOfNotNull(
                    series.libraryName?.takeIf { it.isNotBlank() },
                    releaseYear?.toString(),
                    if (chapterCards.isNotEmpty()) "${chapterCards.size} issues" else null
                ).joinToString(" · ")
                if (metaText.isNotBlank()) {
                    Text(
                        text = metaText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                val progressPct = ((series.readingProgress() ?: 0f) * 100).roundToInt()
                val statusText = if (progressPct >= 100) "Completed" else if (progressPct > 0) "$progressPct% read" else "Not Started"
                Text(
                    text = "${series.pagesRead ?: 0} / ${series.pages ?: 0} pages · $statusText",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 2.dp)
                )

                // Credit and Publisher beside cover under pages and percentage read
                if (creditChips.isNotEmpty()) {
                    Text(
                        text = "Credits: " + creditChips.joinToString { it.second },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
                val publishersText = listOfNotNull(
                    publisherChips.takeIf { it.isNotEmpty() }?.joinToString { it.second },
                    imprintChips.takeIf { it.isNotEmpty() }?.joinToString { it.second }
                ).joinToString(" · ")
                if (publishersText.isNotBlank()) {
                    Text(
                        text = "Publisher: $publishersText",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }
            }
        }

        // Action Buttons Row (Continue / Want to Read / Mark Finished / Edit)
        KavitaQuickActions(
            primaryActionText = continueButtonText,
            onPrimaryAction = {
                continueItem?.let { onPick(it.chapter.id, it.volume.id, if (isReread) 0 else null) }
            },
            isWantToRead = isWantToRead,
            onToggleWantToRead = {
                val next = !isWantToRead
                isWantToRead = next
                scope.launch {
                    runCatching {
                        if (next) {
                            api.addSeriesToWantToRead(UpdateWantToReadDto(listOf(series.id)))
                            onMessage("Added to Want to Read")
                        } else {
                            api.removeSeriesFromWantToRead(UpdateWantToReadDto(listOf(series.id)))
                            onMessage("Removed from Want to Read")
                        }
                    }.onFailure {
                        isWantToRead = !next
                        BunkoLog.w("Could not update Want to Read", it)
                        onMessage("Failed to update Want to Read")
                    }
                }
            },
            isFinished = isSeriesFinished,
            onToggleFinished = {
                scope.launch {
                    runCatching {
                        if (!isSeriesFinished) {
                            val allChapterIds = chapterCards.map { it.chapter.id }
                            chapterCards.forEach { card ->
                                api.markChapterRead(
                                    MarkChapterReadDto(
                                        seriesId = series.id,
                                        chapterId = card.chapter.id,
                                        generateReadingSession = false
                                    )
                                )
                            }
                            onRefreshSeries()
                            onMessage("Marked series as finished")
                        } else {
                            val allChapterIds = chapterCards.map { it.chapter.id }
                            api.markChaptersUnread(
                                MarkVolumesReadDto(
                                    seriesId = series.id,
                                    chapterIds = allChapterIds
                                )
                            )
                            onRefreshSeries()
                            onMessage("Marked series as unread")
                        }
                    }.onFailure {
                        BunkoLog.w("Could not update reading status", it)
                        onMessage("Failed to update reading status")
                    }
                }
            },
            onEdit = { showActionsDialog = true },
            accentColor = series.coverActionColor()
        )

        // Issues & Specials Section (Placed above Rating Section)
        if (chapterCards.isNotEmpty()) {
            KavitaIssuesSection(
                chapterCards = chapterCards,
                session = session,
                downloadedChapterIds = downloadedChapterIds,
                downloadingChapterIds = downloadingChapterIds,
                onPick = onPick,
                onReadIncognito = onReadIncognito,
                onMarkRead = onMarkRead,
                onMarkUnread = onMarkUnread,
                onDownload = onDownload,
                onRemoveDownload = onRemoveDownload
            )
        }

        // Interactive 5-star Rating Row
        BookRatingRow(
            rating = userRating,
            onRatingChange = { newRating ->
                userRating = newRating
                scope.launch {
                    runCatching {
                        api.updateRating(UpdateSeriesRatingDto(seriesId = series.id, userRating = newRating))
                        onMessage(if (newRating > 0f) "Rating saved ($newRating ★)" else "Rating cleared")
                    }.onFailure {
                        BunkoLog.w("Could not update series rating", it)
                        onMessage("Failed to save rating")
                    }
                }
            },
            promptToRate = isSeriesFinished
        )

        // Genres & Tags
        if (genreChips.isNotEmpty() || tagChips.isNotEmpty()) {
            DetailChipBlock(
                title = "Genres & Tags",
                chips = genreChips.map { (id, title) ->
                    title to { onOpenFilteredSeries(SearchSeriesTarget.Genre, id, title) }
                } + tagChips.map { (id, title) ->
                    title to { onOpenFilteredSeries(SearchSeriesTarget.Tag, id, title) }
                }
            )
        }

        // Reading Stats Card
        val totalChapters = chapterCards.size
        val readChapters = chapterCards.count { (it.chapter.pagesRead ?: 0) >= (it.chapter.pages ?: 1) && (it.chapter.pages ?: 0) > 0 }
        val unreadChapters = (totalChapters - readChapters).coerceAtLeast(0)
        KavitaReadingStatsCard(
            totalChapters = totalChapters,
            readChapters = readChapters,
            unreadChapters = unreadChapters,
            totalPages = series.pages,
            readPages = series.pagesRead,
            avgHoursToRead = series.avgHoursToRead
        )

        // Synopsis Section
        if (summary != null) {
            BookDetailSection(
                icon = Icons.Outlined.Description,
                title = "About this series"
            ) {
                ScrollableDescription(text = summary)
            }
        }
    }
}

@Composable
private fun SeriesActionsDialog(
    series: SeriesDto,
    api: KavitaApi,
    isAdmin: Boolean,
    onEditCover: () -> Unit = {},
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var showingReadingLists by remember { mutableStateOf(false) }
    var readingLists by remember { mutableStateOf<List<ReadingListDto>>(emptyList()) }
    var createReadingListDialog by remember { mutableStateOf(false) }
    var newReadingListTitle by remember { mutableStateOf("") }
    var loadingReadingLists by remember { mutableStateOf(false) }

    if (createReadingListDialog) {
        AlertDialog(
            onDismissRequest = { createReadingListDialog = false },
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
                    enabled = newReadingListTitle.trim().isNotBlank(),
                    onClick = {
                        val title = newReadingListTitle.trim()
                        scope.launch {
                            runCatching {
                                val list = api.createReadingList(CreateReadingListDto(title))
                                api.addSeriesToReadingList(
                                    UpdateReadingListBySeriesDto(
                                        seriesId = series.id,
                                        readingListId = list.id
                                    )
                                )
                                createReadingListDialog = false
                                onDismiss()
                                onMessage("Added to $title")
                            }.onFailure {
                                onMessage("Could not create reading list")
                            }
                        }
                    }
                ) {
                    Text("Create & Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { createReadingListDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (showingReadingLists) "Add to Reading List" else "Series Actions")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (showingReadingLists) {
                    if (loadingReadingLists) {
                        Text("Loading reading lists...", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        readingLists.forEach { list ->
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        runCatching {
                                            api.addSeriesToReadingList(
                                                UpdateReadingListBySeriesDto(
                                                    seriesId = series.id,
                                                    readingListId = list.id
                                                )
                                            )
                                            onDismiss()
                                            onMessage("Added to ${list.title ?: "reading list"}")
                                        }.onFailure {
                                            onMessage("Could not update reading list")
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = list.title ?: "Reading List ${list.id}",
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Start
                                )
                            }
                        }
                        TextButton(
                            onClick = {
                                newReadingListTitle = ""
                                createReadingListDialog = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "+ Create New Reading List",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    TextButton(
                        onClick = {
                            onDismiss()
                            onEditCover()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.Edit, contentDescription = null)
                            Text("Edit Series Cover")
                        }
                    }

                    TextButton(
                        onClick = {
                            loadingReadingLists = true
                            showingReadingLists = true
                            scope.launch {
                                runCatching {
                                    readingLists = api.readingLists()
                                }.onFailure {
                                    onMessage("Could not load reading lists")
                                }
                                loadingReadingLists = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.PlaylistAdd, contentDescription = null)
                            Text("Add to Reading List")
                        }
                    }

                    if (isAdmin && (series.libraryId ?: 0) > 0) {
                        val request = RefreshSeriesDto(
                            libraryId = series.libraryId ?: 0,
                            seriesId = series.id,
                            forceUpdate = true
                        )
                        TextButton(
                            onClick = {
                                scope.launch {
                                    runCatching {
                                        api.refreshSeriesMetadata(request)
                                        onDismiss()
                                        onMessage("Refresh metadata requested")
                                    }.onFailure {
                                        onMessage("Could not refresh series metadata")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Outlined.Refresh, contentDescription = null)
                                Text("Refresh Metadata")
                            }
                        }

                        TextButton(
                            onClick = {
                                scope.launch {
                                    runCatching {
                                        api.scanSeries(request)
                                        onDismiss()
                                        onMessage("Scan series requested")
                                    }.onFailure {
                                        onMessage("Could not scan series")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Outlined.Folder, contentDescription = null)
                                Text("Scan Series Files")
                            }
                        }

                        TextButton(
                            onClick = {
                                scope.launch {
                                    runCatching {
                                        api.analyzeSeries(request)
                                        onDismiss()
                                        onMessage("Analyze series requested")
                                    }.onFailure {
                                        onMessage("Could not analyze series")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Outlined.Info, contentDescription = null)
                                Text("Analyze Series")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (showingReadingLists) {
                TextButton(onClick = { showingReadingLists = false }) {
                    Text("Back")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
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
private fun SeriesCover(
    series: SeriesDto,
    session: KavitaSession,
    modifier: Modifier = Modifier,
    coverKey: Long = 0L
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .aspectRatio(KavitaCoverAspectRatio)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest),
        contentAlignment = Alignment.Center
    ) {
        if (session.baseUrl.isNotBlank() && session.apiKey.isNotBlank()) {
            val url = seriesCoverUrl(session, series.id)
            val fullUrl = if (coverKey > 0L) "$url&t=$coverKey" else url
            val request = remember(context, fullUrl) {
                ImageRequest.Builder(context)
                    .data(fullUrl)
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

@Composable
internal fun KavitaIssuesSection(
    chapterCards: List<ChapterCardItem>,
    session: KavitaSession,
    downloadedChapterIds: Set<Int>,
    downloadingChapterIds: Set<Int>,
    onPick: (chapterId: Int, volumeId: Int, initialPage: Int?) -> Unit,
    onReadIncognito: ((ChapterCardItem) -> Unit)?,
    onMarkRead: ((ChapterCardItem) -> Unit)?,
    onMarkUnread: ((ChapterCardItem) -> Unit)?,
    onDownload: ((ChapterCardItem) -> Unit)?,
    onRemoveDownload: ((ChapterCardItem) -> Unit)?,
    modifier: Modifier = Modifier
) {
    if (chapterCards.isEmpty()) return

    val issueCards = remember(chapterCards) { chapterCards.filterNot { it.chapter.isSpecial }.distinctBy { it.chapter.id } }
    val specialCards = remember(chapterCards) { chapterCards.filter { it.chapter.isSpecial }.distinctBy { it.chapter.id } }

    var selectedTab by remember(issueCards.isEmpty(), specialCards.isEmpty()) {
        mutableStateOf(if (issueCards.isNotEmpty()) 0 else 1)
    }

    val activeList = when {
        selectedTab == 0 && issueCards.isNotEmpty() -> issueCards
        selectedTab == 1 && specialCards.isNotEmpty() -> specialCards
        issueCards.isNotEmpty() -> issueCards
        else -> specialCards
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (issueCards.isNotEmpty() && specialCards.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    label = {
                        Text(
                            "Issues (${issueCards.size})",
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
                FilterChip(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    label = {
                        Text(
                            "Specials (${specialCards.size})",
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        } else {
            val title = if (issueCards.isNotEmpty()) "Issues (${issueCards.size})" else "Specials (${specialCards.size})"
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
        ) {
            items(activeList.distinctBy { it.chapter.id }, key = { it.chapter.id }) { item ->
                Box(modifier = Modifier.width(130.dp)) {
                    ChapterGridCard(
                        item = item,
                        session = session,
                        isDownloaded = item.chapter.id in downloadedChapterIds,
                        isDownloading = item.chapter.id in downloadingChapterIds,
                        onClick = { onPick(item.chapter.id, item.volume.id, null) },
                        onReadIncognito = onReadIncognito?.let { { it(item) } },
                        onMarkRead = onMarkRead?.let { { it(item) } },
                        onMarkUnread = onMarkUnread?.let { { it(item) } },
                        onDownload = onDownload?.let { { it(item) } },
                        onRemoveDownload = onRemoveDownload?.let { { it(item) } }
                    )
                }
            }
        }
    }
}
