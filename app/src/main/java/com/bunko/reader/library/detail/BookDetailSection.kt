package com.bunko.reader.library.detail

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.StarHalf
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bunko.reader.ui.theme.accessibleContentColor
import com.bunko.reader.offline.LocalBook
import com.bunko.reader.offline.LocalBookReadingState
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import kotlin.math.roundToInt

val StarGoldColor = Color(0xFFFFB800)

/**
 * A titled card for one part of the book details (reading stats, about the book, volumes/chapters),
 * so each section reads as its own block instead of running into the next.
 */
@Composable
fun BookDetailSection(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                action?.invoke()
            }
            content()
        }
    }
}

/**
 * Reading statistics card with progress indicator and 4 equal tiles.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReadingStatsCard(
    book: LocalBook,
    finished: Boolean,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    onEditStarted: (() -> Unit)? = null,
    onEditFinished: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val startedAt = if (book.startedReadingAt > 0L) book.startedReadingAt else if (book.lastReadTime > 0L) book.lastReadTime else book.lastModified
    val daysTaken = remember(startedAt, book.finishedReadingAt) {
        calculateDaysTaken(startedAt = startedAt, finishedAt = if (book.finishedReadingAt > 0L) book.finishedReadingAt else null)
    }
    val timeTakenText = remember(book.totalReadingSeconds, book.pageCount, book.lastReadPage) {
        if (book.totalReadingSeconds > 0L) {
            formatReadingDuration(book.totalReadingSeconds, context)
        } else if (book.pageCount > 0) {
            "Page ${book.lastReadPage + 1} of ${book.pageCount}"
        } else {
            "—"
        }
    }
    val daysTakenText = remember(daysTaken, book.formattedSize) {
        if (book.formattedSize.isNotBlank()) {
            "$daysTaken ${if (daysTaken == 1) "day" else "days"} · ${book.formattedSize}"
        } else {
            "$daysTaken ${if (daysTaken == 1) "day" else "days"}"
        }
    }
    val startedStat = ReadingStat(
        label = "Started",
        value = if (startedAt > 0L) startedAt.formatDate() else "Not set",
        onClick = onEditStarted,
        clickLabel = "Edit started date"
    )
    val stats = if (finished) {
        listOf(
            startedStat,
            ReadingStat(
                label = "Finished",
                value = if (book.finishedReadingAt > 0L) book.finishedReadingAt.formatDate() else if (book.lastReadTime > 0L) book.lastReadTime.formatDate() else "Completed",
                onClick = onEditFinished,
                clickLabel = "Edit finished date"
            ),
            ReadingStat("Reading Progress", if (book.pageCount > 0) "${book.pageCount} pages read" else "100% complete"),
            ReadingStat("Time & Size", daysTakenText)
        )
    } else {
        listOf(
            startedStat,
            ReadingStat("Last Read", if (book.lastReadTime > 0L) book.lastReadTime.formatDate() else "Not read yet"),
            ReadingStat("Pages & Progress", timeTakenText),
            ReadingStat("Days & Size", daysTakenText)
        )
    }

    val progressPercent = (book.progressFraction * 100).roundToInt()
    BookDetailSection(
        icon = if (finished) Icons.Outlined.TaskAlt else Icons.Outlined.Timer,
        title = if (finished) "Finished Reading" else "Reading Progress: $progressPercent%",
        modifier = modifier,
        action = action,
    ) {
        if (!finished) {
            LinearProgressIndicator(
                progress = { book.progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                strokeCap = StrokeCap.Round
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            stats.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { stat ->
                        ReadingStatTile(
                            stat = stat,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                    repeat(2 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
}

/**
 * Reading statistics card for Kavita series.
 */
@Composable
fun KavitaReadingStatsCard(
    totalChapters: Int,
    readChapters: Int,
    unreadChapters: Int,
    totalPages: Int? = null,
    readPages: Int? = null,
    avgHoursToRead: Float? = null,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val progressFraction = if (totalPages != null && totalPages > 0) {
        ((readPages ?: 0).toFloat() / totalPages.toFloat()).coerceIn(0f, 1f)
    } else if (totalChapters > 0) {
        (readChapters.toFloat() / totalChapters.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val progressPercent = (progressFraction * 100).roundToInt()
    val finished = (totalPages != null && totalPages > 0 && (readPages ?: 0) >= totalPages) ||
            (totalChapters > 0 && readChapters >= totalChapters)

    val pagesReadText = if (totalPages != null && totalPages > 0) {
        "${readPages ?: 0} of $totalPages pages"
    } else {
        "$readChapters of $totalChapters chapters"
    }

    val unreadText = if (totalPages != null && totalPages > 0 && readPages != null) {
        val pagesLeft = (totalPages - readPages).coerceAtLeast(0)
        "$unreadChapters chapters ($pagesLeft pp left)"
    } else {
        "$unreadChapters chapters left"
    }

    val timeStat = if (avgHoursToRead != null && avgHoursToRead > 0f) {
        val totalMinutes = (avgHoursToRead * 60).roundToInt()
        val hours = totalMinutes / 60
        val mins = totalMinutes % 60
        when {
            hours > 0 && mins > 0 -> "${hours}h ${mins}m read time"
            hours > 0 -> "${hours}h read time"
            else -> "${mins}m read time"
        }
    } else {
        "$progressPercent% complete"
    }

    val stats = listOf(
        ReadingStat("Progress", pagesReadText),
        ReadingStat("Remaining", unreadText),
        ReadingStat("Time & Completion", timeStat),
        ReadingStat("Status", if (finished) "Completed" else if (readChapters > 0 || (readPages ?: 0) > 0) "In Progress" else "Unread")
    )

    BookDetailSection(
        icon = if (finished) Icons.Outlined.TaskAlt else Icons.Outlined.Timer,
        title = if (finished) "Series Completed" else "Series Progress: $progressPercent%",
        modifier = modifier,
        action = action,
    ) {
        if (!finished) {
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                strokeCap = StrokeCap.Round
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            stats.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { stat ->
                        ReadingStatTile(
                            stat = stat,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                    repeat(2 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
}

class ReadingStat(
    val label: String,
    val value: String,
    val onClick: (() -> Unit)? = null,
    val clickLabel: String? = null,
)

@Composable
fun ReadingStatTile(stat: ReadingStat, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    val onClick = stat.onClick
    Surface(
        modifier = modifier
            .clip(shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClickLabel = stat.clickLabel, role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            ),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stat.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stat.value,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onClick != null) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(16.dp),
                )
            }
        }
    }
}

/**
 * Primary reading & book action buttons row (Read / Continue / Read Again + Want to Read + Mark Finished + Edit).
 */
@Composable
fun BookQuickActions(
    primaryActionText: String = "Read",
    onPrimaryAction: () -> Unit,
    isWantToRead: Boolean,
    onToggleWantToRead: () -> Unit,
    isFinished: Boolean,
    onToggleFinished: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Prominent Primary Button
        val primaryContentColor = accentColor.accessibleContentColor()
        Button(
            onClick = onPrimaryAction,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = accentColor,
                contentColor = primaryContentColor
            )
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoStories,
                contentDescription = null,
                tint = primaryContentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = primaryActionText,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = primaryContentColor
            )
        }

        // Secondary Action Row: Want to Read, Mark Finished, Edit
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Want to Read Button
            OutlinedButton(
                onClick = onToggleWantToRead,
                modifier = Modifier
                    .weight(1.1f)
                    .height(42.dp),
                shape = RoundedCornerShape(14.dp),
                colors = if (isWantToRead) {
                    ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                } else {
                    ButtonDefaults.outlinedButtonColors()
                },
                contentPadding = PaddingValues(horizontal = 6.dp)
            ) {
                Icon(
                    imageVector = if (isWantToRead) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Want to Read",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isWantToRead) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Mark Finished / Unread Button
            OutlinedButton(
                onClick = onToggleFinished,
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp),
                shape = RoundedCornerShape(14.dp),
                colors = if (isFinished) {
                    ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else {
                    ButtonDefaults.outlinedButtonColors()
                },
                contentPadding = PaddingValues(horizontal = 6.dp)
            ) {
                Icon(
                    imageVector = if (isFinished) Icons.Outlined.Check else Icons.Outlined.TaskAlt,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (isFinished) "Finished" else "Mark Done",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isFinished) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Edit Info Button
            OutlinedButton(
                onClick = onEdit,
                modifier = Modifier
                    .weight(0.85f)
                    .height(42.dp),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Edit",
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Everyday actions row for Kavita series.
 */
@Composable
fun KavitaQuickActions(
    primaryActionText: String = "Read",
    onPrimaryAction: () -> Unit,
    isWantToRead: Boolean,
    onToggleWantToRead: () -> Unit,
    isFinished: Boolean,
    onToggleFinished: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
) {
    BookQuickActions(
        primaryActionText = primaryActionText,
        onPrimaryAction = onPrimaryAction,
        isWantToRead = isWantToRead,
        onToggleWantToRead = onToggleWantToRead,
        isFinished = isFinished,
        onToggleFinished = onToggleFinished,
        onEdit = onEdit,
        accentColor = accentColor,
        modifier = modifier
    )
}

@Composable
fun BookQuickAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = CircleShape,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .padding(12.dp)
                    .size(20.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/**
 * 5-star interactive rating row with half-step support and clear button.
 */
@Composable
fun BookRatingRow(
    rating: Float,
    onRatingChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    promptToRate: Boolean = false,
) {
    val normalizedRating = ((rating * 2f).roundToInt() / 2f).coerceIn(0f, 5f)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val asking = promptToRate && normalizedRating == 0f
            Text(
                text = if (asking) "Rate this book" else "Your Rating",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = if (asking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (normalizedRating > 0f) {
                Text(
                    text = "$normalizedRating ★",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = StarGoldColor,
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(5) { index ->
                val starNumber = index + 1
                HalfStepStar(
                    rating = normalizedRating,
                    starNumber = starNumber,
                    onRatingChange = onRatingChange,
                )
            }
            if (normalizedRating > 0f) {
                TextButton(onClick = { onRatingChange(0f) }) {
                    Text("Clear", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
fun HalfStepStar(
    rating: Float,
    starNumber: Int,
    onRatingChange: (Float) -> Unit,
) {
    val halfRating = starNumber - 0.5f
    val fullRating = starNumber.toFloat()
    Box(
        modifier = Modifier.size(36.dp),
        contentAlignment = Alignment.Center,
    ) {
        val icon = when {
            rating >= fullRating -> Icons.Filled.Star
            rating >= halfRating -> Icons.AutoMirrored.Filled.StarHalf
            else -> Icons.Outlined.StarBorder
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (rating >= halfRating) StarGoldColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            modifier = Modifier.size(28.dp),
        )
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clickable { onRatingChange(halfRating) },
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clickable { onRatingChange(fullRating) },
            )
        }
    }
}

/**
 * Tags row rendered as rounded chips.
 */
@Composable
fun BookTagsRow(
    tags: List<String>,
    modifier: Modifier = Modifier,
    onTagClick: ((String) -> Unit)? = null
) {
    if (tags.isEmpty()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tags.forEach { tag ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = if (onTagClick != null) Modifier.clickable { onTagClick(tag) } else Modifier
            ) {
                Text(
                    text = tag,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/**
 * Scrollable description with smooth fade on overflow edges.
 */
@Composable
fun ScrollableDescription(text: String, maxHeight: Int = 220) {
    val scrollState = rememberScrollState()
    val thumbColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val fadeColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val fadeHeight = 24.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight.dp)
            .drawWithContent {
                drawContent()
                val overflow = scrollState.maxValue
                if (overflow <= 0 || overflow == Int.MAX_VALUE) return@drawWithContent
                val fadePx = fadeHeight.toPx()
                if (scrollState.value > 0) {
                    drawRect(
                        brush = Brush.verticalGradient(listOf(fadeColor, Color.Transparent), endY = fadePx),
                        size = Size(size.width, fadePx),
                    )
                }
                if (scrollState.value < overflow) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            listOf(Color.Transparent, fadeColor),
                            startY = size.height - fadePx,
                            endY = size.height,
                        ),
                        topLeft = Offset(0f, size.height - fadePx),
                        size = Size(size.width, fadePx),
                    )
                }
                val barWidth = 4.dp.toPx()
                val radius = CornerRadius(barWidth / 2)
                val x = size.width - barWidth
                drawRoundRect(color = trackColor, topLeft = Offset(x, 0f), size = Size(barWidth, size.height), cornerRadius = radius)
                val viewport = size.height
                val thumbHeight = (viewport * viewport / (viewport + overflow)).coerceAtLeast(24.dp.toPx())
                val thumbTop = (viewport - thumbHeight) * scrollState.value / overflow
                drawRoundRect(
                    color = thumbColor,
                    topLeft = Offset(x, thumbTop),
                    size = Size(barWidth, thumbHeight),
                    cornerRadius = radius,
                )
            },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .verticalScroll(scrollState)
                .padding(end = 12.dp),
        )
    }
}

fun Long.formatDate(): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(this))

fun formatReadingDuration(totalSeconds: Long, context: Context): String {
    if (totalSeconds < 60L) {
        return "< 1 min"
    }
    val totalMinutes = totalSeconds / 60L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
        hours > 0L && minutes > 0L -> "${hours}h ${minutes}m"
        hours > 0L -> "${hours}h"
        else -> "${minutes}m"
    }
}

fun calculateDaysTaken(startedAt: Long, finishedAt: Long?): Int {
    val startCal = Calendar.getInstance().apply {
        timeInMillis = startedAt
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val endCal = Calendar.getInstance().apply {
        timeInMillis = finishedAt ?: System.currentTimeMillis()
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val diffMillis = endCal.timeInMillis - startCal.timeInMillis
    val days = (diffMillis / (24 * 60 * 60 * 1000L)).toInt() + 1
    return days.coerceAtLeast(1)
}
