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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
internal fun ChapterGridCard(item: ChapterCardItem, session: KavitaSession, onClick: () -> Unit) {
    val chapter = item.chapter
    val title = chapter.displayTitle()
    val label = listOfNotNull(
        title,
        item.volume.displayShortName(),
        chapter.releaseDateText()
    ).joinToString(" • ")
    val progress = chapter.readingProgress()
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
            }
            Box(Modifier.fillMaxWidth()) {
                ReadingProgressBar(
                    progress = progress,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .height(3.dp)
                )
                Column(Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
                    Text(
                        text = label,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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

