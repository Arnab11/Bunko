package com.bunko.reader.engine.model

import android.graphics.Bitmap
import androidx.compose.ui.text.AnnotatedString
import java.io.File

/**
 * Universal abstraction for any document opened in Bunko.
 * Shared between Kavita (online/downloaded) and Local Storage.
 */
sealed interface ReaderDocument {

    val title: String
    val totalUnits: Int
    val coverBitmap: Bitmap?

    /**
     * Comic / Manga archives (CBZ, CBR, CB7, CBT, ZIP, RAR, 7Z, Folders, or Kavita image stream).
     */
    data class Comic(
        override val title: String,
        val pages: List<ComicPageResource>,
        val dimensions: Map<Int, Pair<Int, Int>> = emptyMap(),
        val genres: List<String> = emptyList(),
        val tags: List<String> = emptyList(),
        val publisher: String? = null,
        val summary: String? = null,
        val isWebtoonHint: Boolean = false,
        override val coverBitmap: Bitmap? = null
    ) : ReaderDocument {
        override val totalUnits: Int get() = pages.size
    }

    /**
     * Reflowable text documents (EPUB 2/3, MOBI, AZW, AZW3, FB2, TXT, MD).
     */
    data class Reflow(
        override val title: String,
        val spines: List<ReflowSpine>,
        val tableOfContents: List<TocItem> = emptyList(),
        val resourceDir: File? = null,
        override val coverBitmap: Bitmap? = null
    ) : ReaderDocument {
        override val totalUnits: Int get() = spines.size
    }

    /**
     * Fixed-layout PDF documents.
     */
    data class Pdf(
        override val title: String,
        val pdfFile: File,
        val pageCount: Int,
        val pageDimensions: Map<Int, Pair<Int, Int>> = emptyMap(),
        override val coverBitmap: Bitmap? = null
    ) : ReaderDocument {
        override val totalUnits: Int get() = pageCount
    }
}

/**
 * Uniform page descriptor for comic/archive pages.
 */
sealed interface ComicPageResource {
    val index: Int

    data class ArchiveEntry(
        val archiveFile: File,
        val entryName: String,
        val format: ArchiveType,
        override val index: Int
    ) : ComicPageResource

    data class DirectFile(
        val file: File,
        override val index: Int
    ) : ComicPageResource

    data class RemoteUrl(
        val url: String,
        val apiKey: String = "",
        override val index: Int
    ) : ComicPageResource
}

enum class ArchiveType {
    ZIP,
    RAR,
    SEVEN_ZIP,
    TAR,
    FOLDER
}

/**
 * A logical chapter/spine in a reflowable eBook.
 */
data class ReflowSpine(
    val id: String,
    val spineIndex: Int,
    val title: String? = null,
    val rawHtml: String,
    val href: String = ""
)

/**
 * Table of contents navigation point.
 */
data class TocItem(
    val title: String,
    val spineIndex: Int,
    val anchor: String? = null,
    val level: Int = 0
)

/**
 * Visual elements produced by parsing HTML in reflowable books.
 */
sealed interface ReflowBlock {
    data class Text(
        val text: AnnotatedString,
        val isHeading: Boolean = false,
        val headingLevel: Int = 0,
        val isQuote: Boolean = false,
        val isListItem: Boolean = false,
        val align: ReflowTextAlign = ReflowTextAlign.Start
    ) : ReflowBlock

    data class Image(
        val sourcePathOrUrl: String,
        val width: Int? = null,
        val height: Int? = null,
        val altText: String? = null
    ) : ReflowBlock

    data object HorizontalRule : ReflowBlock

    data class Space(val heightDp: Int) : ReflowBlock
}

enum class ReflowTextAlign {
    Start,
    Center,
    End,
    Justify
}
