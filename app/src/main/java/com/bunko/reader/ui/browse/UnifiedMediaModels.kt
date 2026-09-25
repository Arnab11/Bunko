package com.bunko.reader.ui.browse

import com.bunko.reader.KavitaSession
import com.bunko.reader.SeriesDto
import com.bunko.reader.offline.LocalBook
import com.bunko.reader.series.internal.readingProgress
import com.bunko.reader.ui.seriesCoverUrl

enum class MediaStorageSource {
    Kavita,
    Local
}

/**
 * Unified representation of a book, comic, or series item for both Kavita and Local Storage.
 */
data class UnifiedMediaItem(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val coverModel: Any? = null,
    val progress: Float? = null,
    val totalPages: Int? = null,
    val readPages: Int? = null,
    val badgeText: String? = null,
    val source: MediaStorageSource = MediaStorageSource.Kavita,
    val isStack: Boolean = false,
    val itemCount: Int = 1,
    val rawPayload: Any? = null
)

/**
 * Extension to convert Kavita [SeriesDto] to [UnifiedMediaItem].
 */
fun SeriesDto.toUnifiedMediaItem(session: KavitaSession): UnifiedMediaItem {
    val coverUrl = if (session.baseUrl.isNotBlank() && session.apiKey.isNotBlank()) {
        seriesCoverUrl(session, id)
    } else {
        null
    }
    val totalChapters = volumes.sumOf { it.chapters.size }
    val isMulti = volumes.size > 1 || totalChapters > 1
    return UnifiedMediaItem(
        id = id.toString(),
        title = name,
        subtitle = libraryName,
        coverModel = coverUrl,
        progress = readingProgress(),
        totalPages = pages,
        readPages = pagesRead,
        source = MediaStorageSource.Kavita,
        isStack = isMulti,
        itemCount = if (totalChapters > 1) totalChapters else if (volumes.size > 1) volumes.size else 1,
        rawPayload = this
    )
}

/**
 * Extension to convert Local [LocalBook] to [UnifiedMediaItem].
 */
fun LocalBook.toUnifiedMediaItem(
    isStack: Boolean = false,
    itemCount: Int = 1,
    displayTitle: String? = null
): UnifiedMediaItem {
    val progressFraction = if (pageCount > 0 && lastReadPage > 0) {
        (lastReadPage.toFloat() / (pageCount - 1).coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    } else {
        null
    }
    return UnifiedMediaItem(
        id = id,
        title = displayTitle ?: title,
        subtitle = if (isStack && itemCount > 1) "$itemCount issues" else format.displayName,
        coverModel = coverPath.takeIf { it.isNotBlank() }?.let { java.io.File(it) },
        progress = progressFraction,
        totalPages = pageCount,
        readPages = lastReadPage,
        badgeText = if (isCompleted) "Completed" else if (isStack && itemCount > 1) "$itemCount issues" else null,
        source = MediaStorageSource.Local,
        isStack = isStack,
        itemCount = itemCount,
        rawPayload = this
    )
}
