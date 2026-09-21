package com.bunko.reader.series

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import com.bunko.reader.series.internal.cleanHtmlDescription
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.bunko.reader.ui.theme.accessibleContentColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bunko.reader.ChapterDto
import com.bunko.reader.KavitaSession
import com.bunko.reader.VolumeDto
import com.bunko.reader.download.OfflineDownloadStatus
import com.bunko.reader.download.OfflineIssueRecord
import com.bunko.reader.download.localCoverFile
import com.bunko.reader.series.internal.displayName
import com.bunko.reader.series.internal.primaryReadActionText
import com.bunko.reader.series.internal.readingHoursText
import com.bunko.reader.ui.KavitaCoverAspectRatio
import com.bunko.reader.ui.ModalSideSheet
import com.bunko.reader.ui.theme.ReadingProgressInProgress
import com.bunko.reader.ui.theme.ReadingProgressRead
import com.bunko.reader.ui.theme.ReadingProgressTrack
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun IssueDetailSideSheet(
    visible: Boolean,
    seriesName: String,
    volume: VolumeDto?,
    chapter: ChapterDto?,
    fileSizeBytes: Long?,
    downloadRecord: OfflineIssueRecord?,
    loading: Boolean,
    actionBusy: Boolean,
    session: KavitaSession,
    actionColor: Color,
    onDismissRequest: () -> Unit,
    onRead: () -> Unit,
    onReadIncognito: () -> Unit,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
    onDownload: () -> Unit,
    onRemoveDownload: () -> Unit
) {
    ModalSideSheet(
        visible = visible,
        onDismissRequest = onDismissRequest,
        paneTitleText = "Issue details"
    ) {
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ContainedLoadingIndicator()
            }
            volume != null && chapter != null -> IssueDetailContent(
                seriesName = seriesName,
                volume = volume,
                chapter = chapter,
                fileSizeBytes = fileSizeBytes,
                downloadRecord = downloadRecord,
                actionBusy = actionBusy,
                session = session,
                actionColor = actionColor,
                onRead = onRead,
                onReadIncognito = onReadIncognito,
                onMarkRead = onMarkRead,
                onMarkUnread = onMarkUnread,
                onDownload = onDownload,
                onRemoveDownload = onRemoveDownload
            )
        }
    }
}

@Composable
private fun IssueDetailContent(
    seriesName: String,
    volume: VolumeDto,
    chapter: ChapterDto,
    fileSizeBytes: Long?,
    downloadRecord: OfflineIssueRecord?,
    actionBusy: Boolean,
    session: KavitaSession,
    actionColor: Color,
    onRead: () -> Unit,
    onReadIncognito: () -> Unit,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
    onDownload: () -> Unit,
    onRemoveDownload: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(Modifier.width(190.dp)) {
                        AsyncImage(
                            model = downloadRecord?.localCoverFile()
                                ?: chapterCoverUrl(session, chapter.id),
                            contentDescription = issueTitle(seriesName, chapter),
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(KavitaCoverAspectRatio)
                                .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                            contentScale = ContentScale.Crop
                        )
                        IssueProgressBar(
                            progress = chapter.issueReadingProgress(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                        )
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = issueTitle(seriesName, chapter),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    val metadata = listOfNotNull(
                        issueLabel(volume, chapter),
                        volume.displayName(),
                        chapter.issueReleaseDate(),
                        chapter.pages?.takeIf { it > 0 }?.let { "$it pages" },
                        chapter.issueReadingTime(),
                        fileSizeBytes?.takeIf { it > 0 }?.formatFileSize()
                    ).distinct()
                    Text(
                        text = metadata.joinToString(" • "),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    downloadRecord?.takeUnless { it.status == OfflineDownloadStatus.Removed }?.let { record ->
                        Text(
                            text = record.statusText(),
                            color = if (record.status in setOf(
                                    OfflineDownloadStatus.Failed,
                                    OfflineDownloadStatus.Unsupported
                                )) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
            chapter.summary?.cleanHtmlDescription()?.takeIf { it.isNotBlank() }?.let { summary ->
                item {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        IssueReadSplitButton(
            chapter = chapter,
            downloadRecord = downloadRecord,
            enabled = !actionBusy,
            containerColor = actionColor,
            onRead = onRead,
            onReadIncognito = onReadIncognito,
            onMarkRead = onMarkRead,
            onMarkUnread = onMarkUnread,
            onDownload = onDownload,
            onRemoveDownload = onRemoveDownload,
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun IssueReadSplitButton(
    chapter: ChapterDto,
    downloadRecord: OfflineIssueRecord?,
    enabled: Boolean,
    containerColor: Color,
    onRead: () -> Unit,
    onReadIncognito: () -> Unit,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
    onDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val actions = buildList {
        add(IssueMenuAction.ReadIncognito)
        if (!chapter.issueIsRead()) add(IssueMenuAction.MarkRead)
        if ((chapter.pagesRead ?: 0) > 0) add(IssueMenuAction.MarkUnread)
        add(
            when (downloadRecord?.status) {
                OfflineDownloadStatus.Ready -> IssueMenuAction.RemoveDownload
                OfflineDownloadStatus.Failed -> IssueMenuAction.RetryDownload
                OfflineDownloadStatus.Unsupported -> IssueMenuAction.RemoveDownload
                OfflineDownloadStatus.Queued,
                OfflineDownloadStatus.Downloading -> IssueMenuAction.Downloading
                OfflineDownloadStatus.Removed,
                null -> IssueMenuAction.Download
            }
        )
    }
    val colors = ButtonDefaults.buttonColors(
        containerColor = containerColor,
        contentColor = containerColor.accessibleContentColor()
    )

    BoxWithConstraints(modifier) {
        val trailingWidth = 48.dp
        val leadingWidth = (maxWidth - trailingWidth - SplitButtonDefaults.Spacing).coerceAtLeast(48.dp)

        SplitButtonLayout(
            leadingButton = {
                SplitButtonDefaults.LeadingButton(
                    onClick = onRead,
                    enabled = enabled,
                    modifier = Modifier.width(leadingWidth),
                    colors = colors
                ) {
                    Text(
                        text = chapter.issueReadActionText(),
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
                        enabled = enabled,
                        modifier = Modifier
                            .width(trailingWidth)
                            .semantics {
                                stateDescription = if (menuExpanded) "Expanded" else "Collapsed"
                                contentDescription = "More issue actions"
                            },
                        colors = colors
                    ) {
                        val rotation by animateFloatAsState(
                            targetValue = if (menuExpanded) 0f else 180f,
                            label = "Issue actions arrow rotation"
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
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.width(240.dp)
                    ) {
                        DropdownMenuGroup(
                            shapes = MenuDefaults.groupShape(index = 0, count = 1)
                        ) {
                            actions.forEachIndexed { index, action ->
                                DropdownMenuItem(
                                    text = { Text(action.label) },
                                    onClick = {
                                        menuExpanded = false
                                        when (action) {
                                            IssueMenuAction.ReadIncognito -> onReadIncognito()
                                            IssueMenuAction.MarkRead -> onMarkRead()
                                            IssueMenuAction.MarkUnread -> onMarkUnread()
                                            IssueMenuAction.Download -> onDownload()
                                            IssueMenuAction.RetryDownload -> onDownload()
                                            IssueMenuAction.RemoveDownload -> onRemoveDownload()
                                            IssueMenuAction.Downloading -> Unit
                                        }
                                    },
                                    shape = MenuDefaults.itemShape(index, actions.size).shape,
                                    enabled = enabled && action != IssueMenuAction.Downloading
                                )
                            }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun IssueProgressBar(progress: Float?, modifier: Modifier = Modifier) {
    if (progress == null || progress <= 0f) return
    val bounded = progress.coerceIn(0f, 1f)
    val fillColor = if (bounded >= 1f) ReadingProgressRead else ReadingProgressInProgress
    Box(modifier.background(ReadingProgressTrack)) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(bounded)
                .background(fillColor)
        )
    }
}

private enum class IssueMenuAction(val label: String) {
    ReadIncognito("Read Incognito"),
    MarkRead("Mark as Read"),
    MarkUnread("Mark as Unread"),
    Download("Download"),
    RetryDownload("Retry Download"),
    RemoveDownload("Remove Download"),
    Downloading("Downloading")
}

private fun OfflineIssueRecord.statusText(): String = when (status) {
    OfflineDownloadStatus.Queued -> "Download queued"
    OfflineDownloadStatus.Downloading -> progressFraction?.let {
        "Downloading ${(it * 100).roundToInt()}%"
    } ?: "Downloading"
    OfflineDownloadStatus.Ready -> "Available offline"
    OfflineDownloadStatus.Failed -> errorMessage ?: "Download failed"
    OfflineDownloadStatus.Unsupported -> errorMessage ?: "Unsupported offline format"
    OfflineDownloadStatus.Removed -> ""
}

private fun issueTitle(seriesName: String, chapter: ChapterDto): String {
    return listOf(chapter.volumeTitle, chapter.titleName, chapter.title)
        .firstOrNull { value ->
            !value.isNullOrBlank() &&
                !value.equals(seriesName, ignoreCase = true) &&
                value != "-100000" &&
                value.toFloatOrNull() == null
        }
        ?: seriesName
}

private fun issueLabel(volume: VolumeDto, chapter: ChapterDto): String {
    val number = chapter.numberText()
        ?: chapter.title?.trim()?.takeIf { it.toFloatOrNull() != null }
        ?: (volume.number as? JsonPrimitive)?.contentOrNull
        ?: volume.name?.trim()?.takeIf { it.toFloatOrNull() != null }
        ?: chapter.id.toString()
    return "Issue $number"
}

private fun ChapterDto.numberText(): String? {
    return (number as? JsonPrimitive)?.contentOrNull
        ?.takeIf { it.isNotBlank() && it != "-100000" }
}

private fun ChapterDto.issueReleaseDate(): String? {
    return releaseDate
        ?.trim()
        ?.takeIf { it.isNotBlank() && !it.startsWith("0001-01-01") }
        ?.substringBefore('T')
}

private fun ChapterDto.issueReadingTime(): String? {
    return readingHoursText(minHoursToRead, maxHoursToRead, avgHoursToRead)
}

private fun ChapterDto.issueReadingProgress(): Float? {
    val total = pages ?: return null
    if (total <= 0) return null
    return (pagesRead ?: 0).coerceIn(0, total).toFloat() / total.toFloat()
}

private fun ChapterDto.issueIsRead(): Boolean {
    val total = pages ?: return false
    return total > 0 && (pagesRead ?: 0) >= total
}

private fun ChapterDto.issueReadActionText(): String {
    return primaryReadActionText(pages = pages, pagesRead = pagesRead)
}

private fun Long.formatFileSize(): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    val formatted = if (value >= 10 || unitIndex == 0) {
        value.roundToInt().toString()
    } else {
        ((value * 10).roundToInt() / 10.0).toString()
    }
    return "$formatted ${units[unitIndex]}"
}
