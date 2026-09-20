package com.bunko.reader.engine.fb2

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.bunko.reader.BunkoLog
import com.bunko.reader.engine.model.ReflowSpine
import com.bunko.reader.engine.model.TocItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

data class ParsedFb2(
    val title: String,
    val author: String? = null,
    val spines: List<ReflowSpine>,
    val tableOfContents: List<TocItem>,
    val coverPath: String? = null,
    val resourceDir: File
)

object Fb2DocumentReader {

    suspend fun parseFb2(context: Context, file: File): ParsedFb2 = withContext(Dispatchers.IO) {
        val cacheKey = "fb2_${file.nameWithoutExtension.hashCode()}_${file.length()}"
        val resourceDir = File(context.cacheDir, cacheKey).apply { if (!exists()) mkdirs() }

        val xmlContent = if (file.extension.equals("zip", ignoreCase = true)) {
            ZipFile(file).use { zip ->
                val entry = zip.entries().asSequence().firstOrNull { it.name.endsWith(".fb2", ignoreCase = true) }
                    ?: throw IllegalArgumentException("No .fb2 file found inside zip")
                zip.getInputStream(entry).bufferedReader().use { it.readText() }
            }
        } else {
            file.bufferedReader().use { it.readText() }
        }

        val doc = Jsoup.parse(xmlContent, "", Parser.xmlParser())

        // 1. Metadata
        val title = doc.select("description > title-info > book-title").firstOrNull()?.text()
            ?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension
        val authorFirst = doc.select("description > title-info > author > first-name").firstOrNull()?.text() ?: ""
        val authorLast = doc.select("description > title-info > author > last-name").firstOrNull()?.text() ?: ""
        val author = "$authorFirst $authorLast".trim().ifEmpty { null }

        // 2. Extract embedded binary images
        var coverPath: String? = null
        val coverId = doc.select("description > title-info > coverpage > image").firstOrNull()?.attr("l:href")
            ?.removePrefix("#")

        doc.select("binary").forEach { binaryEl ->
            val id = binaryEl.attr("id")
            val base64Data = binaryEl.text().replace("\n", "").replace("\r", "").trim()
            if (id.isNotBlank() && base64Data.isNotBlank()) {
                try {
                    val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                    val imgFile = File(resourceDir, "$id.jpg")
                    if (!imgFile.exists() || imgFile.length() == 0L) {
                        imgFile.writeBytes(bytes)
                    }
                    if (id == coverId || coverPath == null) {
                        coverPath = imgFile.absolutePath
                    }
                } catch (t: Throwable) {
                    BunkoLog.w("Failed to decode base64 image $id in FB2", t)
                }
            }
        }

        // 3. Sections / Bodies
        val spines = mutableListOf<ReflowSpine>()
        val toc = mutableListOf<TocItem>()

        val mainBody = doc.select("body").firstOrNull()
        val sections = mainBody?.select("> section") ?: emptyList()

        if (sections.isNotEmpty()) {
            sections.forEachIndexed { idx, sec ->
                val sectionTitle = sec.select("> title").firstOrNull()?.text()
                    ?.takeIf { it.isNotBlank() } ?: "Section ${idx + 1}"
                
                // Rewrite image links
                sec.select("image").forEach { img ->
                    val href = img.attr("l:href").removePrefix("#")
                    val localImg = File(resourceDir, "$href.jpg")
                    if (localImg.exists()) {
                        img.tagName("img")
                        img.attr("src", localImg.absolutePath)
                    }
                }

                val html = "<html><head><title>$sectionTitle</title></head><body>${sec.html()}</body></html>"
                spines.add(
                    ReflowSpine(
                        id = "fb2_section_$idx",
                        spineIndex = idx,
                        title = sectionTitle,
                        rawHtml = html
                    )
                )
                toc.add(TocItem(sectionTitle, idx))
            }
        } else {
            // No sections, whole body as one spine
            mainBody?.select("image")?.forEach { img ->
                val href = img.attr("l:href").removePrefix("#")
                val localImg = File(resourceDir, "$href.jpg")
                if (localImg.exists()) {
                    img.tagName("img")
                    img.attr("src", localImg.absolutePath)
                }
            }
            val html = "<html><head><title>$title</title></head><body>${mainBody?.html() ?: ""}</body></html>"
            spines.add(
                ReflowSpine(
                    id = "fb2_section_0",
                    spineIndex = 0,
                    title = title,
                    rawHtml = html
                )
            )
            toc.add(TocItem(title, 0))
        }

        ParsedFb2(
            title = title,
            author = author,
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
            val xmlContent = if (file.extension.equals("zip", ignoreCase = true)) {
                ZipFile(file).use { zip ->
                    val entry = zip.entries().asSequence().firstOrNull { it.name.endsWith(".fb2", ignoreCase = true) } ?: return@use null
                    zip.getInputStream(entry).bufferedReader().use { it.readText() }
                }
            } else {
                file.bufferedReader().use { it.readText() }
            } ?: return@withContext null

            val doc = Jsoup.parse(xmlContent, "", Parser.xmlParser())
            val coverId = doc.select("description > title-info > coverpage > image").firstOrNull()?.attr("l:href")
                ?.removePrefix("#")

            val binaryEl = (coverId?.let { doc.select("binary[id=$it]").firstOrNull() })
                ?: doc.select("binary").firstOrNull()
                ?: return@withContext null

            val base64Data = binaryEl.text().replace("\n", "").replace("\r", "").trim()
            val bytes = Base64.decode(base64Data, Base64.DEFAULT)

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

            var sampleSize = 1
            if (bounds.outHeight > maxHeight || bounds.outWidth > maxWidth) {
                val halfH = bounds.outHeight / 2
                val halfW = bounds.outWidth / 2
                while ((halfH / sampleSize) >= maxHeight && (halfW / sampleSize) >= maxWidth) {
                    sampleSize *= 2
                }
            }

            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize.coerceAtLeast(1) }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        } catch (t: Throwable) {
            BunkoLog.w("Failed to extract FB2 cover from ${file.name}", t)
            null
        }
    }
}
