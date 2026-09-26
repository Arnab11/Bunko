package com.bunko.reader.offline

import kotlinx.serialization.Serializable
import java.io.File

@Serializable
enum class LocalBookFormat {
    CBZ,
    CBR,
    CB7,
    CBT,
    ZIP,
    RAR,
    SEVEN_ZIP,
    EPUB,
    MOBI,
    AZW,
    AZW3,
    FB2,
    PDF,
    TXT,
    UNKNOWN;

    val displayName: String
        get() = when (this) {
            CBZ -> "CBZ"
            CBR -> "CBR"
            CB7 -> "CB7"
            CBT -> "CBT"
            ZIP -> "ZIP"
            RAR -> "RAR"
            SEVEN_ZIP -> "7Z"
            EPUB -> "EPUB"
            MOBI -> "MOBI"
            AZW, AZW3 -> "AZW"
            FB2 -> "FB2"
            PDF -> "PDF"
            TXT -> "TXT"
            UNKNOWN -> "OTHER"
        }

    val isComic: Boolean
        get() = this == CBZ || this == CBR || this == CB7 || this == CBT || this == ZIP || this == RAR || this == SEVEN_ZIP

    val isEpub: Boolean
        get() = this == EPUB

    val isReflowEbook: Boolean
        get() = this == EPUB || this == MOBI || this == AZW || this == AZW3 || this == FB2 || this == TXT

    val isPdf: Boolean
        get() = this == PDF

    companion object {
        fun fromExtension(ext: String): LocalBookFormat = when (ext.lowercase().trim()) {
            "cbz" -> CBZ
            "cbr" -> CBR
            "cb7" -> CB7
            "cbt" -> CBT
            "zip" -> ZIP
            "rar" -> RAR
            "7z" -> SEVEN_ZIP
            "epub" -> EPUB
            "mobi", "prc" -> MOBI
            "azw" -> AZW
            "azw3" -> AZW3
            "fb2" -> FB2
            "pdf" -> PDF
            "txt", "md" -> TXT
            else -> UNKNOWN
        }
    }
}

@Serializable
data class LocalBook(
    val id: String,
    val title: String,
    val uriString: String,
    val extension: String,
    val format: LocalBookFormat,
    val sizeBytes: Long = 0L,
    val lastModified: Long = 0L,
    val pageCount: Int = 0,
    val lastReadPage: Int = 0,
    val isCompleted: Boolean = false,
    val coverPath: String = "",
    val seriesName: String = "",
    val volumeOrIssue: String = "",
    val folderUriString: String = "",
    val folderName: String = "",
    val isWebtoon: Boolean = false,
    val isExternalFile: Boolean = false,
    val lastReadTime: Long = 0L,
    val author: String = "",
    val description: String = "",
    val tagsCsv: String = "",
    val rating: Float = 0f,
    val startedReadingAt: Long = 0L,
    val finishedReadingAt: Long = 0L,
    val totalReadingSeconds: Long = 0L,
    val isReadNext: Boolean = false,
    val isWantToRead: Boolean = false
) {
    val isWebtoonBook: Boolean
        get() = isWebtoon || com.bunko.reader.reader.internal.ReaderWebtoonDetector.isWebtoonMetadata(seriesName = title)

    val formattedSize: String
        get() {
            if (sizeBytes <= 0) return ""
            val mib = sizeBytes.toDouble() / (1024.0 * 1024.0)
            return if (mib >= 1024.0) {
                "%.1f GB".format(mib / 1024.0)
            } else {
                "%.1f MB".format(mib)
            }
        }

    val progressFraction: Float
        get() {
            if (isCompleted) return 1f
            if (pageCount <= 0) return 0f
            return (lastReadPage.toFloat() / (pageCount - 1).coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
        }

    val readingPercent: Float
        get() = progressFraction

    val hasCover: Boolean
        get() = coverPath.isNotBlank() && File(coverPath).let { it.isFile && it.length() > 0 }

    fun readingState(): LocalBookReadingState = when {
        isCompleted -> LocalBookReadingState.FINISHED
        lastReadPage > 0 || lastReadTime > 0L -> LocalBookReadingState.READING
        else -> LocalBookReadingState.NOT_STARTED
    }

    fun tags(): List<String> = if (tagsCsv.isBlank()) emptyList() else {
        tagsCsv.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun seriesDisplay(): String = buildString {
        if (seriesName.isNotBlank()) {
            append(seriesName)
            if (volumeOrIssue.isNotBlank()) {
                append(" #").append(volumeOrIssue)
            }
        }
    }
}

enum class LocalBookReadingState {
    NOT_STARTED,
    READING,
    FINISHED
}

