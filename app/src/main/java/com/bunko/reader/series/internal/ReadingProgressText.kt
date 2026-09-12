package com.bunko.reader.series.internal

import com.bunko.reader.ChapterDto
import com.bunko.reader.SeriesDto
/** Internal to series, not for external use. */
internal fun SeriesDto.readingProgress(): Float? {
    val total = pages ?: return null
    if (total <= 0) return null
    val read = (pagesRead ?: 0).coerceIn(0, total)
    return read.toFloat() / total.toFloat()
}

/** Internal to series, not for external use. */
internal fun ChapterDto.readingProgress(): Float? {
    val total = pages ?: return null
    if (total <= 0) return null
    val read = (pagesRead ?: 0).coerceIn(0, total)
    return read.toFloat() / total.toFloat()
}

/** Internal to series, not for external use. */
internal fun SeriesDto.primaryReadActionText(): String {
    return primaryReadActionText(pages = pages, pagesRead = pagesRead)
}

/** Internal to series, not for external use. */
internal fun primaryReadActionText(pages: Int?, pagesRead: Int?): String {
    val total = pages ?: return "Start Reading"
    if (total <= 0) return "Start Reading"
    val read = (pagesRead ?: 0).coerceIn(0, total)
    return when {
        read <= 0 -> "Start Reading"
        read >= total -> "Re-Read"
        else -> "Continue Reading"
    }
}

/** Internal to series, not for external use. */
internal fun SeriesDto.isRead(): Boolean {
    val total = pages ?: return false
    if (total <= 0) return false
    return (pagesRead ?: 0) >= total
}

