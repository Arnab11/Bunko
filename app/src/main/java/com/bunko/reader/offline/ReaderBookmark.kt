package com.bunko.reader.offline

import kotlinx.serialization.Serializable

@Serializable
data class ReaderBookmark(
    val id: String,
    val bookId: String,
    val bookTitle: String,
    val chapterName: String? = null,
    val chapterId: Int = 0,
    val page: Int = 0,
    val pageCount: Int = 0,
    val progressPercent: Float = 0f,
    val previewText: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
