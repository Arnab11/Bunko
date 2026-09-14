package com.bunko.reader.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import kotlinx.coroutines.flow.distinctUntilChanged
import com.bunko.reader.KavitaSession
import com.bunko.reader.SeriesDto
import com.bunko.reader.ui.KavitaCoverAspectRatio
import com.bunko.reader.ui.seriesCoverUrl
import com.bunko.reader.ui.seriesInitial
import com.bunko.reader.ui.theme.BunkoBackground
import com.bunko.reader.ui.theme.BunkoSurface
import com.bunko.reader.ui.theme.ReadingProgressInProgress
import com.bunko.reader.ui.theme.ReadingProgressRead
import com.bunko.reader.ui.theme.ReadingProgressTrack

@Composable
internal fun BrowsePageScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    statusBarPadding: Boolean = onBack != null,
    navigationIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack,
    navigationContentDescription: String = "Back",
    showTopBar: Boolean = statusBarPadding,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit
) {
    val scaffoldModifier = if (statusBarPadding) {
        modifier.statusBarsPadding()
    } else {
        modifier
    }

    if (!showTopBar) {
        Box(
            modifier = scaffoldModifier
                .fillMaxSize()
                .background(BunkoBackground),
            content = content
        )
    } else {
        Column(
            modifier = scaffoldModifier
                .fillMaxSize()
                .background(BunkoBackground)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = if (onBack == null) 16.dp else 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = navigationIcon,
                            contentDescription = navigationContentDescription,
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.primary,
                    style = if (onBack == null) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = if (onBack == null) 0.dp else 4.dp)
                )
                actions()
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                content = content
            )
        }
    }
}

@Composable
fun <T> PosterGrid(
    items: List<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    minSize: Dp = 130.dp,
    state: LazyGridState = rememberLazyGridState(),
    footer: (@Composable () -> Unit)? = null,
    itemContent: @Composable (T) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = minSize),
        state = state,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(items = items, key = key) { item -> itemContent(item) }
        if (footer != null) {
            item(span = { GridItemSpan(maxLineSpan) }) { footer() }
        }
    }
}

@Composable
internal fun LazyGridLoadMoreEffect(
    state: LazyGridState,
    itemCount: Int,
    hasMore: Boolean,
    loadingMore: Boolean,
    loadMoreError: String?,
    onLoadMore: () -> Unit
) {
    LaunchedEffect(state, itemCount, hasMore, loadingMore, loadMoreError) {
        if (!hasMore || loadingMore || loadMoreError != null || itemCount == 0) return@LaunchedEffect
        snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { lastVisibleIndex ->
                if (lastVisibleIndex >= itemCount - PagingPrefetchDistance) onLoadMore()
            }
    }
}

@Composable
internal fun PagingFooter(
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            loading -> CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
            error != null -> Button(onClick = onRetry) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Text("Retry", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

private const val PagingPrefetchDistance = 12

@Composable
fun UnifiedPosterCard(
    item: UnifiedMediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.small,
    coverFillsHeight: Boolean = false
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RectangleShape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column {
            val coverModifier = if (coverFillsHeight) {
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
            } else {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(KavitaCoverAspectRatio)
            }
            Box(
                modifier = coverModifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                contentAlignment = Alignment.Center
            ) {
                if (item.coverModel != null) {
                    val context = LocalContext.current
                    val request = remember(context, item.coverModel) {
                        ImageRequest.Builder(context)
                            .data(item.coverModel)
                            .crossfade(180)
                            .build()
                    }
                    AsyncImage(
                        model = request,
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    SeriesCoverPlaceholder(seriesName = item.title)
                }
                if (item.badgeText != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                    ) {
                        Text(
                            text = item.badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(seriesPosterLabelHeight())
            ) {
                SeriesReadingProgressBar(
                    progress = item.progress,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .height(3.dp)
                )
                Text(
                    text = item.title,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UnifiedListItem(
    item: UnifiedMediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onSelectionChange: (() -> Unit)? = null
) {
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .aspectRatio(KavitaCoverAspectRatio)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                contentAlignment = Alignment.Center
            ) {
                if (item.coverModel != null) {
                    val context = LocalContext.current
                    val request = remember(context, item.coverModel) {
                        ImageRequest.Builder(context)
                            .data(item.coverModel)
                            .crossfade(180)
                            .build()
                    }
                    AsyncImage(
                        model = request,
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    SeriesCoverPlaceholder(seriesName = item.title)
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val total = item.totalPages ?: 0
                val read = item.readPages ?: 0
                val statusText = when {
                    item.badgeText != null -> item.badgeText
                    total > 0 && read >= total -> "Completed"
                    read > 0 -> "In Progress"
                    item.subtitle != null -> item.subtitle
                    else -> "Unread"
                }
                val statusColor = when (statusText) {
                    "Completed" -> Color(0xFF66BB6A)
                    "In Progress" -> Color(0xFF42A5F5)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor,
                    fontWeight = FontWeight.Medium
                )
                SeriesReadingProgressBar(
                    progress = item.progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                )
            }
            if (selectionMode && onSelectionChange != null) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onSelectionChange() }
                )
            }
        }
    }
}

@Composable
internal fun SeriesPosterCard(
    series: SeriesDto,
    session: KavitaSession,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.small,
    coverFillsHeight: Boolean = false
) {
    Card(
        modifier = modifier,
        shape = RectangleShape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column {
            // In a fixed-height carousel the item width varies, so the cover fills the
            // height left over by the label and crops (never distorts) the artwork.
            // In grids the width is the driver, so the cover keeps the Kavita aspect.
            val coverModifier = if (coverFillsHeight) {
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
            } else {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(KavitaCoverAspectRatio)
            }
            Box(
                modifier = coverModifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                contentAlignment = Alignment.Center
            ) {
                if (session.baseUrl.isNotBlank() && session.apiKey.isNotBlank()) {
                    SeriesCoverImage(
                        seriesName = series.name,
                        coverUrl = seriesCoverUrl(session, series.id)
                    )
                } else {
                    SeriesCoverPlaceholder(seriesName = series.name)
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(seriesPosterLabelHeight())
            ) {
                SeriesReadingProgressBar(
                    progress = series.readingProgress(),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .height(3.dp)
                )
                Text(
                    text = series.name,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SeriesListItem(
    series: SeriesDto,
    session: KavitaSession,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onSelectionChange: (() -> Unit)? = null
) {
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .aspectRatio(KavitaCoverAspectRatio)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                contentAlignment = Alignment.Center
            ) {
                if (session.baseUrl.isNotBlank() && session.apiKey.isNotBlank()) {
                    SeriesCoverImage(
                        seriesName = series.name,
                        coverUrl = seriesCoverUrl(session, series.id)
                    )
                } else {
                    SeriesCoverPlaceholder(seriesName = series.name)
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = series.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val total = series.pages ?: 0
                val read = series.pagesRead ?: 0
                val statusText = when {
                    total > 0 && read >= total -> "Completed"
                    read > 0 -> "In Progress"
                    else -> "Unread"
                }
                val statusColor = when (statusText) {
                    "Completed" -> Color(0xFF66BB6A)
                    "In Progress" -> Color(0xFF42A5F5)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor,
                    fontWeight = FontWeight.Medium
                )
                SeriesReadingProgressBar(
                    progress = series.readingProgress(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                )
            }
            if (selectionMode && onSelectionChange != null) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onSelectionChange() }
                )
            }
        }
    }
}

@Composable
private fun SeriesCoverImage(seriesName: String, coverUrl: String) {
    val context = LocalContext.current
    var retryKey by remember(coverUrl) { mutableIntStateOf(0) }
    var loadState by remember(coverUrl) { mutableStateOf(SeriesCoverLoadState.Loading) }

    key(retryKey) {
        val request = remember(context, coverUrl) {
            ImageRequest.Builder(context)
                .data(coverUrl)
                .crossfade(180)
                .build()
        }
        AsyncImage(
            model = request,
            contentDescription = seriesName,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            onLoading = { loadState = SeriesCoverLoadState.Loading },
            onSuccess = { loadState = SeriesCoverLoadState.Success },
            onError = { loadState = SeriesCoverLoadState.Error }
        )
    }
    when (loadState) {
        SeriesCoverLoadState.Loading -> SeriesCoverPlaceholder(
            seriesName = seriesName,
            loading = true
        )
        SeriesCoverLoadState.Error -> SeriesCoverPlaceholder(
            seriesName = seriesName,
            onRetry = {
                loadState = SeriesCoverLoadState.Loading
                retryKey++
            }
        )
        SeriesCoverLoadState.Success -> Unit
    }
}

private enum class SeriesCoverLoadState {
    Loading,
    Success,
    Error
}

@Composable
private fun SeriesCoverPlaceholder(
    seriesName: String,
    loading: Boolean = false,
    onRetry: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF111111)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = seriesInitial(seriesName),
            color = Color(0xFFB9BDBD),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold
        )
        when {
            loading -> CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .size(22.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp
            )
            onRetry != null -> IconButton(
                onClick = onRetry,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.62f), MaterialTheme.shapes.small)
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Retry cover for $seriesName",
                    tint = Color.White
                )
            }
        }
    }
}

/**
 * Height of the two-line poster label derived from the actual text metrics (so a larger
 * device font scale grows the box instead of clipping the second line) plus its padding.
 * A little headroom on top: an exact 2x line height can lose the second line to pixel
 * rounding at some densities (the text then ellipsizes after one line). The text is
 * top-aligned, so the slack disappears into the label background.
 */
@Composable
internal fun seriesPosterLabelHeight(): Dp {
    val lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
    return with(LocalDensity.current) { (lineHeight * 2).toDp() } + 16.dp + 4.dp
}

/** Fixed width of a poster card in a horizontal shelf (Home / Search). */
internal val SeriesShelfItemWidth = 164.dp

/** Gap between poster cards in a horizontal shelf. */
internal val SeriesShelfItemSpacing = 14.dp

/**
 * Height of a poster shelf: the cover at [SeriesShelfItemWidth] (kept at the Kavita cover
 * aspect, so it never crops) plus the two-line label. Callers give each card this height and
 * [SeriesShelfItemWidth] width.
 */
@Composable
internal fun seriesShelfHeight(): Dp =
    SeriesShelfItemWidth / KavitaCoverAspectRatio + seriesPosterLabelHeight()

@Composable
private fun SeriesReadingProgressBar(progress: Float?, modifier: Modifier = Modifier) {
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

private fun SeriesDto.readingProgress(): Float? {
    val total = pages ?: return null
    if (total <= 0) return null
    val read = (pagesRead ?: 0).coerceIn(0, total)
    return read.toFloat() / total.toFloat()
}
