package com.bunko.reader.engine.mobi

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.bunko.reader.BunkoLog
import com.bunko.reader.engine.model.ReflowSpine
import com.bunko.reader.engine.model.TocItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.File

data class ParsedMobi(
    val title: String,
    val author: String? = null,
    val publisher: String? = null,
    val description: String? = null,
    val language: String? = null,
    val spines: List<ReflowSpine>,
    val tableOfContents: List<TocItem>,
    val coverPath: String? = null,
    val resourceDir: File
)

/**
 * MOBI / AZW / PalmDOC Document Reader powered by the Librera Reader parsing engine.
 */
object MobiDocumentReader {

    suspend fun parseMobi(context: Context, file: File): ParsedMobi = withContext(Dispatchers.IO) {
        val cacheKey = "mobi_${file.nameWithoutExtension.hashCode()}_${file.length()}"
        val resourceDir = File(context.cacheDir, cacheKey).apply { if (!exists()) mkdirs() }

        val parser = LibreraMobiParser(file)
        val title = parser.getTitle().ifBlank { file.nameWithoutExtension }
        val author = parser.getAuthor()
        val publisher = parser.getPublisher()
        val description = parser.getDescription()
        val language = parser.getLanguage()

        val spines = mutableListOf<ReflowSpine>()
        val toc = mutableListOf<TocItem>()
        var coverPath: String? = null

        // 1. Extract cover image
        val coverBytes = parser.getCoverOrThumb()
        if (coverBytes != null && coverBytes.isNotEmpty()) {
            val coverExt = detectImageFormat(coverBytes) ?: "jpg"
            val coverFile = File(resourceDir, "cover.$coverExt")
            if (!coverFile.exists() || coverFile.length() == 0L) {
                coverFile.writeBytes(coverBytes)
            }
            coverPath = coverFile.absolutePath
        }

        // 2. Extract embedded images
        val allImages = parser.getAllImageRecords()
        allImages.forEachIndexed { index, imgBytes ->
            val ext = detectImageFormat(imgBytes) ?: "jpg"
            val imgFile = File(resourceDir, "image_${String.format("%04d", index + 1)}.$ext")
            if (!imgFile.exists() || imgFile.length() == 0L) {
                imgFile.writeBytes(imgBytes)
            }
            if (coverPath == null) {
                coverPath = imgFile.absolutePath
            }
        }

        // 3. Extract text content via Librera parser
        val rawHtml = parser.getTextContent()
        val doc = Jsoup.parse(rawHtml)

        // Rewrite <img> and <image> tags to point to extracted resource files
        var imgTagCounter = 1
        doc.select("img, image").forEach { el ->
            val recindexAttr = el.attr("recindex").filter { it.isDigit() }.toIntOrNull()
            val imgIdx = recindexAttr ?: imgTagCounter
            val candidates = listOf("jpg", "png", "gif", "webp", "jpeg").mapNotNull { ext ->
                File(resourceDir, "image_${String.format("%04d", imgIdx)}.$ext").takeIf { it.exists() && it.length() > 0 }
            }
            val actual = candidates.firstOrNull() ?: run {
                listOf("jpg", "png", "gif", "webp", "jpeg").mapNotNull { ext ->
                    File(resourceDir, "image_${String.format("%04d", imgTagCounter)}.$ext").takeIf { it.exists() && it.length() > 0 }
                }.firstOrNull()
            }
            if (actual != null) {
                el.tagName("img")
                el.attr("src", actual.absolutePath)
            }
            imgTagCounter++
        }

        val processedHtml = doc.body()?.html() ?: doc.html()

        // 4. Split chapters by pagebreaks or headings
        val chapters = processedHtml.split(Regex("(?i)<mbp:pagebreak[^>]*>|(?i)<div class=[\"']chapter[\"']>"))
        if (chapters.size > 1) {
            chapters.forEachIndexed { idx, chunk ->
                if (chunk.isNotBlank()) {
                    val cleanChunk = "<html><body>$chunk</body></html>"
                    spines.add(
                        ReflowSpine(
                            id = "mobi_spine_$idx",
                            spineIndex = idx,
                            title = "Chapter ${idx + 1}",
                            rawHtml = cleanChunk
                        )
                    )
                    toc.add(TocItem("Chapter ${idx + 1}", idx))
                }
            }
        }

        if (spines.isEmpty()) {
            spines.add(
                ReflowSpine(
                    id = "mobi_spine_0",
                    spineIndex = 0,
                    title = title,
                    rawHtml = doc.outerHtml()
                )
            )
            toc.add(TocItem(title, 0))
        }

        ParsedMobi(
            title = title,
            author = author,
            publisher = publisher,
            description = description,
            language = language,
            spines = spines,
            tableOfContents = toc,
            coverPath = coverPath,
            resourceDir = resourceDir
        )
    }

    suspend fun extractCoverBitmap(
        context: Context,
        file: File,
        maxWidth: Int = 400,
        maxHeight: Int = 600
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val parser = LibreraMobiParser(file)
            val bytes = parser.getCoverOrThumb() ?: return@withContext null

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            var sampleSize = 1
            if (bounds.outHeight > maxHeight || bounds.outWidth > maxWidth) {
                val halfH = bounds.outHeight / 2
                val halfW = bounds.outWidth / 2
                while ((halfH / sampleSize) >= maxHeight && (halfW / sampleSize) >= maxWidth) {
                    sampleSize *= 2
                }
            }

            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize.coerceAtLeast(1)
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        } catch (t: Throwable) {
            BunkoLog.w("Failed to extract MOBI cover from ${file.name}", t)
            null
        }
    }

    private fun detectImageFormat(bytes: ByteArray): String? {
        if (bytes.size < 4) return null
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) return "jpg"
        if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte()) return "png"
        if (bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte()) return "gif"
        if (bytes.size >= 12 && bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte()) return "webp"
        return null
    }
}
