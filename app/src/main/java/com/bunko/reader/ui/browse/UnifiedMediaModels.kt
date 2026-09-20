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
    return UnifiedMediaItem(
        id = id.toString(),
        title = name,
        subtitle = libraryName,
        coverModel = coverUrl,
        progress = readingProgress(),
        totalPages = pages,
        readPages = pagesRead,
        source = MediaStorageSource.Kavita,
        rawPayload = this
    )
}

/**
 * Extension to convert Local [LocalBook] to [UnifiedMediaItem].
 */
fun LocalBook.toUnifiedMediaItem(): UnifiedMediaItem {
    val progressFraction = if (pageCount > 0 && lastReadPage > 0) {
        (lastReadPage.toFloat() / (pageCount - 1).coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    } else {
        null
    }
    return UnifiedMediaItem(
        id = id,
        title = title,
        subtitle = format.displayName,
        coverModel = coverPath?.takeIf { it.isNotBlank() }?.let { java.io.File(it) },
        progress = progressFraction,
        totalPages = pageCount,
        readPages = lastReadPage,
        badgeText = if (isCompleted) "Completed" else null,
        source = MediaStorageSource.Local,
        rawPayload = this
    )
}
