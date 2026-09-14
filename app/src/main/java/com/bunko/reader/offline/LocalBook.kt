package com.bunko.reader.offline

import kotlinx.serialization.Serializable
import java.io.File

@Serializable
enum class LocalBookFormat {
    CBZ,
    ZIP,
    EPUB,
    PDF,
    UNKNOWN;

    val displayName: String
        get() = when (this) {
            CBZ -> "CBZ"
            ZIP -> "ZIP"
            EPUB -> "EPUB"
            PDF -> "PDF"
            UNKNOWN -> "OTHER"
        }

    val isComic: Boolean
        get() = this == CBZ || this == ZIP

    val isEpub: Boolean
        get() = this == EPUB

    val isPdf: Boolean
        get() = this == PDF

    companion object {
        fun fromExtension(ext: String): LocalBookFormat = when (ext.lowercase().trim()) {
            "cbz" -> CBZ
            "zip" -> ZIP
            "epub" -> EPUB
            "pdf" -> PDF
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
    val folderName: String = ""
) {
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

    val hasCover: Boolean
        get() = coverPath.isNotBlank() && File(coverPath).let { it.isFile && it.length() > 0 }
}
