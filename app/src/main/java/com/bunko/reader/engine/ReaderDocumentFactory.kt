package com.bunko.reader.engine

import android.content.Context
import com.bunko.reader.engine.archive.UniversalArchiveReader
import com.bunko.reader.engine.epub.EpubPackageReader
import com.bunko.reader.engine.fb2.Fb2DocumentReader
import com.bunko.reader.engine.mobi.MobiDocumentReader
import com.bunko.reader.engine.model.ArchiveType
import com.bunko.reader.engine.model.ComicPageResource
import com.bunko.reader.engine.model.ReaderDocument
import com.bunko.reader.engine.pdf.PdfDocumentEngine
import com.bunko.reader.engine.txt.TxtDocumentReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ReaderDocumentFactory {

    suspend fun openLocalDocument(context: Context, file: File, customTitle: String? = null): ReaderDocument = withContext(Dispatchers.IO) {
        val ext = file.extension.lowercase().trim()
        val title = customTitle?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension.replace('_', ' ').trim()

        when (ext) {
            "cbz", "zip", "cbr", "rar", "cb7", "7z", "cbt", "tar" -> {
                val archiveType = UniversalArchiveReader.detectArchiveType(file)
                val entries = UniversalArchiveReader.listImageEntries(file)
                val comicInfo = UniversalArchiveReader.extractComicInfo(file)
                val dimensions = UniversalArchiveReader.extractPageDimensions(file, entries, sampleLimit = 15)
                val pages = entries.mapIndexed { index, entryName ->
                    ComicPageResource.ArchiveEntry(
                        archiveFile = file,
                        entryName = entryName,
                        format = archiveType,
                        index = index
                    )
                }
                ReaderDocument.Comic(
                    title = comicInfo?.series?.ifBlank { null } ?: comicInfo?.title?.ifBlank { null } ?: title,
                    pages = pages,
                    dimensions = dimensions,
                    genres = comicInfo?.genres.orEmpty(),
                    tags = comicInfo?.tags.orEmpty(),
                    publisher = comicInfo?.publisher,
                    summary = comicInfo?.summary,
                    isWebtoonHint = comicInfo?.isWebtoon == true
                )
            }
            "epub" -> {
                val parsed = EpubPackageReader.parseEpub(context, file)
                ReaderDocument.Reflow(
                    title = parsed.title.ifEmpty { title },
                    spines = parsed.spines,
                    tableOfContents = parsed.tableOfContents,
                    resourceDir = parsed.resourceDir
                )
            }
            "mobi", "azw", "azw3" -> {
                val parsed = MobiDocumentReader.parseMobi(context, file)
                ReaderDocument.Reflow(
                    title = parsed.title.ifEmpty { title },
                    spines = parsed.spines,
                    tableOfContents = parsed.tableOfContents,
                    resourceDir = parsed.resourceDir
                )
            }
            "fb2" -> {
                val parsed = Fb2DocumentReader.parseFb2(context, file)
                ReaderDocument.Reflow(
                    title = parsed.title.ifEmpty { title },
                    spines = parsed.spines,
                    tableOfContents = parsed.tableOfContents,
                    resourceDir = parsed.resourceDir
                )
            }
            "pdf" -> {
                val count = PdfDocumentEngine.getPageCount(file)
                val dimensions = PdfDocumentEngine.getPageDimensions(file)
                ReaderDocument.Pdf(
                    title = title,
                    pdfFile = file,
                    pageCount = count,
                    pageDimensions = dimensions
                )
            }
            "txt", "md" -> {
                val parsed = TxtDocumentReader.parseTxt(context, file)
                ReaderDocument.Reflow(
                    title = parsed.title.ifEmpty { title },
                    spines = parsed.spines,
                    tableOfContents = parsed.tableOfContents
                )
            }
            else -> {
                if (file.isDirectory) {
                    val entries = UniversalArchiveReader.listImageEntries(file)
                    val pages = entries.mapIndexed { index, relPath ->
                        ComicPageResource.DirectFile(
                            file = File(file, relPath),
                            index = index
                        )
                    }
                    ReaderDocument.Comic(
                        title = title,
                        pages = pages
                    )
                } else {
                    // Try parsing as archive fallback
                    val entries = UniversalArchiveReader.listImageEntries(file)
                    if (entries.isNotEmpty()) {
                        val pages = entries.mapIndexed { index, entryName ->
                            ComicPageResource.ArchiveEntry(
                                archiveFile = file,
                                entryName = entryName,
                                format = ArchiveType.ZIP,
                                index = index
                            )
                        }
                        ReaderDocument.Comic(title = title, pages = pages)
                    } else {
                        throw IllegalArgumentException("Unsupported book format: .$ext")
                    }
                }
            }
        }
    }
}
