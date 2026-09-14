package com.bunko.reader.series.internal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownloadDone
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bunko.reader.ChapterDto
import com.bunko.reader.KavitaSession
import com.bunko.reader.VolumeDto
import com.bunko.reader.series.chapterCoverUrl
import com.bunko.reader.ui.KavitaCoverAspectRatio
import com.bunko.reader.ui.theme.ReadingProgressInProgress
import com.bunko.reader.ui.theme.ReadingProgressRead
import com.bunko.reader.ui.theme.ReadingProgressTrack

/** Internal to series, not for external use. */
@Composable
internal fun ChapterIssueGrid(
    issueCards: List<ChapterCardItem>,
    specialCards: List<ChapterCardItem>,
    session: KavitaSession,
    onIssueClick: (ChapterCardItem) -> Unit,
    onReadIncognito: ((ChapterCardItem) -> Unit)? = null,
    onMarkRead: ((ChapterCardItem) -> Unit)? = null,
    onMarkUnread: ((ChapterCardItem) -> Unit)? = null,
    onDownload: ((ChapterCardItem) -> Unit)? = null,
    onRemoveDownload: ((ChapterCardItem) -> Unit)? = null,
    downloadedChapterIds: Set<Int> = emptySet(),
    downloadingChapterIds: Set<Int> = emptySet(),
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        modifier = modifier.fillMaxHeight(),
        contentPadding = PaddingValues(bottom = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            ChapterSectionHeader("Issues", issueCards.size)
        }
        gridItems(issueCards, key = { "${it.volume.id}-${it.chapter.id}" }) { item ->
            ChapterGridCard(
                item = item,
                session = session,
                isDownloaded = item.chapter.id in downloadedChapterIds,
                isDownloading = item.chapter.id in downloadingChapterIds,
                onClick = { onIssueClick(item) },
                onReadIncognito = onReadIncognito?.let { { it(item) } },
                onMarkRead = onMarkRead?.let { { it(item) } },
                onMarkUnread = onMarkUnread?.let { { it(item) } },
                onDownload = onDownload?.let { { it(item) } },
                onRemoveDownload = onRemoveDownload?.let { { it(item) } }
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
                    isDownloaded = item.chapter.id in downloadedChapterIds,
                    isDownloading = item.chapter.id in downloadingChapterIds,
                    onClick = { onIssueClick(item) },
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

/** Internal to series, not for external use. */
@Composable
internal fun ChapterSectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                text = count.toString(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

internal data class ChapterCardItem(
    val volume: VolumeDto,
    val chapter: ChapterDto
)

/** Internal to series, not for external use. */
@Composable
internal fun ChapterGridCard(
    item: ChapterCardItem,
    session: KavitaSession,
    isDownloaded: Boolean = false,
    isDownloading: Boolean = false,
    onClick: () -> Unit,
    onReadIncognito: (() -> Unit)? = null,
    onMarkRead: (() -> Unit)? = null,
    onMarkUnread: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    onRemoveDownload: (() -> Unit)? = null
) {
    val chapter = item.chapter
    val title = chapter.displayTitle()
    val label = listOfNotNull(
        title,
        item.volume.displayShortName(),
        chapter.releaseDateText()
    ).joinToString(" • ")
    val progress = chapter.readingProgress()
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(KavitaCoverAspectRatio)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                contentAlignment = Alignment.Center
            ) {
                if (session.baseUrl.isNotBlank() && session.apiKey.isNotBlank()) {
                    AsyncImage(
                        model = chapterCoverUrl(session, chapter.id),
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text("CH", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }

                // Download badge
                if (isDownloading) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.7f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(4.dp)
                                .size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else if (isDownloaded) {
                    Surface(
                        color = Color(0xFF2E7D32).copy(alpha = 0.9f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FileDownloadDone,
                            contentDescription = "Downloaded",
                            tint = Color.White,
                            modifier = Modifier
                                .padding(4.dp)
                                .size(16.dp)
                        )
                    }
                }
            }
            Box(Modifier.fillMaxWidth()) {
                ReadingProgressBar(
                    progress = progress,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .height(3.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = label,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "Options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            DropdownMenuItem(
                                text = { Text("Read") },
                                leadingIcon = { Icon(Icons.Filled.AutoStories, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onClick()
                                }
                            )
                            if (onReadIncognito != null) {
                                DropdownMenuItem(
                                    text = { Text("Read Incognito") },
                                    leadingIcon = { Icon(Icons.Filled.VisibilityOff, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onReadIncognito()
                                    }
                                )
                            }
                            if ((progress ?: 0f) < 1f && onMarkRead != null) {
                                DropdownMenuItem(
                                    text = { Text("Mark as Read") },
                                    leadingIcon = { Icon(Icons.Filled.Visibility, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onMarkRead()
                                    }
                                )
                            }
                            if ((progress ?: 0f) > 0f && onMarkUnread != null) {
                                DropdownMenuItem(
                                    text = { Text("Mark as Unread") },
                                    leadingIcon = { Icon(Icons.Filled.VisibilityOff, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onMarkUnread()
                                    }
                                )
                            }
                            if (isDownloaded && onRemoveDownload != null) {
                                DropdownMenuItem(
                                    text = { Text("Remove Download") },
                                    leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onRemoveDownload()
                                    }
                                )
                            } else if (!isDownloaded && onDownload != null) {
                                DropdownMenuItem(
                                    text = { Text("Download") },
                                    leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onDownload()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Internal to series, not for external use. */
@Composable
internal fun ReadingProgressBar(progress: Float?, modifier: Modifier = Modifier) {
    val boundedProgress = (progress ?: 0f).coerceIn(0f, 1f)
    val fillColor = if (boundedProgress >= 1f) ReadingProgressRead else ReadingProgressInProgress
    Box(modifier = modifier.background(ReadingProgressTrack)) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(boundedProgress)
                .background(fillColor)
        )
    }
}

