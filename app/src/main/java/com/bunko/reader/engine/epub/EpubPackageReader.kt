package com.bunko.reader.engine.epub

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.bunko.reader.BunkoLog
import com.bunko.reader.engine.model.ReflowSpine
import com.bunko.reader.engine.model.TocItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
import java.net.URI
import java.util.zip.ZipFile

data class ParsedEpub(
    val title: String,
    val author: String? = null,
    val spines: List<ReflowSpine>,
    val tableOfContents: List<TocItem>,
    val coverPath: String? = null,
    val resourceDir: File
)

object EpubPackageReader {

    private val ImageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "svg", "bmp", "avif")

    suspend fun parseEpub(context: Context, epubFile: File): ParsedEpub = withContext(Dispatchers.IO) {
        val cacheKey = "epub_${epubFile.nameWithoutExtension.hashCode()}_${epubFile.length()}"
        val resourceDir = File(context.cacheDir, cacheKey).apply { if (!exists()) mkdirs() }

        ZipFile(epubFile).use { zip ->
            val resourceFileIndex = mutableMapOf<String, File>()

            // 1. Extract all images and resources to cacheDir
            zip.entries().asSequence().forEach { entry ->
                val ext = entry.name.substringAfterLast('.', "").lowercase()
                if (!entry.isDirectory && (ext in ImageExtensions || ext == "css" || ext == "ttf" || ext == "otf")) {
                    val dest = File(resourceDir, entry.name.replace('/', '_'))
                    if (!dest.exists() || dest.length() == 0L) {
                        zip.getInputStream(entry).use { input ->
                            dest.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                    val rawName = entry.name
                    val decodedName = runCatching { java.net.URLDecoder.decode(rawName, "UTF-8") }.getOrDefault(rawName)
                    val baseName = rawName.substringAfterLast('/')
                    val decodedBaseName = decodedName.substringAfterLast('/')

                    resourceFileIndex[rawName] = dest
                    resourceFileIndex[rawName.lowercase()] = dest
                    resourceFileIndex[decodedName] = dest
                    resourceFileIndex[decodedName.lowercase()] = dest
                    resourceFileIndex[baseName] = dest
                    resourceFileIndex[baseName.lowercase()] = dest
                    resourceFileIndex[decodedBaseName] = dest
                    resourceFileIndex[decodedBaseName.lowercase()] = dest
                }
            }

            fun findExtractedFile(baseDir: String, rawHref: String): File? {
                val cleanHref = rawHref.substringBefore('#').substringBefore('?').trim()
                if (cleanHref.isEmpty()) return null
                val decodedHref = runCatching { java.net.URLDecoder.decode(cleanHref, "UTF-8") }.getOrDefault(cleanHref)

                val resolved = resolvePath(baseDir, decodedHref)
                val resolvedClean = resolvePath(baseDir, cleanHref)

                val candidateKeys = listOf(
                    resolved,
                    resolvedClean,
                    resolved.lowercase(),
                    resolvedClean.lowercase(),
                    decodedHref,
                    cleanHref,
                    decodedHref.substringAfterLast('/'),
                    cleanHref.substringAfterLast('/'),
                    decodedHref.substringAfterLast('/').lowercase(),
                    cleanHref.substringAfterLast('/').lowercase()
                )

                for (k in candidateKeys) {
                    resourceFileIndex[k]?.let { if (it.exists() && it.length() > 0) return it }
                }

                val directDest = File(resourceDir, resolved.replace('/', '_'))
                if (directDest.exists() && directDest.length() > 0) return directDest

                val fallbackFile = File(resourceDir, decodedHref.substringAfterLast('/'))
                if (fallbackFile.exists() && fallbackFile.length() > 0) return fallbackFile

                return null
            }

            // 2. Find container.xml and OPF path
            val opfPath = findOpfPath(zip) ?: "content.opf"
            val opfDir = opfPath.substringBeforeLast('/', "").let { if (it.isNotEmpty()) "$it/" else "" }

            val opfEntry = zip.getEntry(opfPath)
                ?: zip.entries().asSequence().firstOrNull { it.name.endsWith(".opf", ignoreCase = true) }
                ?: throw IllegalArgumentException("OPF package file not found in ${epubFile.name}")

            val opfContent = zip.getInputStream(opfEntry).bufferedReader().use { it.readText() }
            val opfDoc = Jsoup.parse(opfContent, "", Parser.xmlParser())

            // 3. Metadata
            val title = opfDoc.select("metadata > dc|title, metadata > title").firstOrNull()?.text()
                ?.takeIf { it.isNotBlank() } ?: epubFile.nameWithoutExtension
            val creator = opfDoc.select("metadata > dc|creator, metadata > creator").firstOrNull()?.text()

            // 4. Manifest
            val manifestMap = mutableMapOf<String, String>() // id -> normalized href
            val manifestMediaTypes = mutableMapOf<String, String>()
            opfDoc.select("manifest > item").forEach { item ->
                val id = item.attr("id")
                val href = item.attr("href")
                val mediaType = item.attr("media-type")
                if (id.isNotEmpty() && href.isNotEmpty()) {
                    manifestMap[id] = resolvePath(opfDir, href)
                    manifestMediaTypes[id] = mediaType
                }
            }

            // 5. Spines (Ordered reading sequence)
            val spineItems = mutableListOf<String>() // ordered hrefs
            opfDoc.select("spine > itemref").forEach { itemref ->
                val idref = itemref.attr("idref")
                val href = manifestMap[idref]
                if (href != null && href.isNotEmpty()) {
                    spineItems.add(href)
                }
            }

            // Fallback if spine was empty: look for all xhtml/html in manifest
            if (spineItems.isEmpty()) {
                manifestMap.values
                    .filter { it.endsWith(".xhtml") || it.endsWith(".html") || it.endsWith(".htm") }
                    .forEach { spineItems.add(it) }
            }

            // 6. Cover extraction
            var coverHref: String? = null
            val coverMeta = opfDoc.select("metadata > meta[name=cover]").firstOrNull()?.attr("content")
            if (coverMeta != null && manifestMap.containsKey(coverMeta)) {
                coverHref = manifestMap[coverMeta]
            } else {
                coverHref = manifestMap.values.firstOrNull { it.contains("cover", ignoreCase = true) && isImageExt(it) }
            }
            val coverFile = coverHref?.let { href ->
                findExtractedFile(opfDir, href)
            }

            // 7. Table of Contents
            val tocItems = mutableListOf<TocItem>()
            val ncxHref = manifestMap["ncx"]
                ?: manifestMap.values.firstOrNull { it.endsWith(".ncx", ignoreCase = true) }
            if (ncxHref != null) {
                zip.getEntry(ncxHref)?.let { ncxEntry ->
                    val ncxContent = zip.getInputStream(ncxEntry).bufferedReader().use { it.readText() }
                    parseNcxToc(ncxContent, ncxHref, spineItems, tocItems)
                }
            }

            // 8. Build Spines with relative image rewriting
            val spines = mutableListOf<ReflowSpine>()
            spineItems.forEachIndexed { index, href ->
                val entry = zip.getEntry(href)
                if (entry != null) {
                    val rawHtml = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                    val currentEntryDir = href.substringBeforeLast('/', "").let { if (it.isNotEmpty()) "$it/" else "" }

                    // Clean & rewrite relative image paths to local file paths
                    val doc = Jsoup.parse(rawHtml)
                    doc.select("img, image, svg image").forEach { img ->
                        val isImageTag = img.tagName().equals("image", ignoreCase = true)
                        val src = if (isImageTag) {
                            img.attr("xlink:href").ifEmpty { img.attr("href") }
                        } else {
                            img.attr("src")
                        }
                        if (src.isNotEmpty() && !src.startsWith("http://") && !src.startsWith("https://") && !src.startsWith("data:")) {
                            val matchedFile = findExtractedFile(currentEntryDir, src)
                            val finalPath = matchedFile?.absolutePath ?: run {
                                val resolved = resolvePath(currentEntryDir, src)
                                val cachedFile = File(resourceDir, resolved.replace('/', '_'))
                                if (cachedFile.exists()) cachedFile.absolutePath else File(resourceDir, src.substringAfterLast('/')).absolutePath
                            }
                            if (isImageTag) {
                                img.attr("xlink:href", finalPath)
                                img.attr("href", finalPath)
                            } else {
                                img.attr("src", finalPath)
                            }
                        }
                    }

                    spines.add(
                        ReflowSpine(
                            id = href,
                            spineIndex = index,
                            title = doc.title().takeIf { it.isNotBlank() },
                            rawHtml = doc.outerHtml(),
                            href = href
                        )
                    )
                }
            }

            ParsedEpub(
                title = title,
                author = creator,
                spines = spines,
                tableOfContents = tocItems,
                coverPath = coverFile?.absolutePath,
                resourceDir = resourceDir
            )
        }
    }

    suspend fun extractCoverBitmap(
        context: Context,
        epubFile: File,
        maxWidth: Int = 400,
        maxHeight: Int = 600
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            ZipFile(epubFile).use { zip ->
                val opfPath = findOpfPath(zip) ?: "content.opf"
                val opfDir = opfPath.substringBeforeLast('/', "").let { if (it.isNotEmpty()) "$it/" else "" }
                val opfEntry = zip.getEntry(opfPath)
                    ?: zip.entries().asSequence().firstOrNull { it.name.endsWith(".opf", ignoreCase = true) }
                    ?: return@use null

                val opfContent = zip.getInputStream(opfEntry).bufferedReader().use { it.readText() }
                val opfDoc = Jsoup.parse(opfContent, "", Parser.xmlParser())

                var coverHref: String? = null
                val coverMeta = opfDoc.select("metadata > meta[name=cover]").firstOrNull()?.attr("content")
                if (coverMeta != null) {
                    val item = opfDoc.select("manifest > item[id=$coverMeta]").firstOrNull()
                    coverHref = item?.attr("href")
                }

                if (coverHref == null) {
                    val items = opfDoc.select("manifest > item")
                    for (it in items) {
                        val href = it.attr("href")
                        if (href.contains("cover", ignoreCase = true) && isImageExt(href)) {
                            coverHref = href
                            break
                        }
                    }
                }

                val targetPath = if (coverHref != null) resolvePath(opfDir, coverHref) else null
                val imageEntry = (targetPath?.let { zip.getEntry(it) })
                    ?: zip.entries().asSequence().firstOrNull { isImageExt(it.name) && it.name.contains("cover", ignoreCase = true) }
                    ?: zip.entries().asSequence().firstOrNull { isImageExt(it.name) }
                    ?: return@use null

                val bytes = zip.getInputStream(imageEntry).use { it.readBytes() }
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
            }
        } catch (t: Throwable) {
            BunkoLog.w("Failed to extract EPUB cover for ${epubFile.name}", t)
            null
        }
    }

    private fun findOpfPath(zip: ZipFile): String? {
        val containerEntry = zip.getEntry("META-INF/container.xml") ?: return null
        val xml = zip.getInputStream(containerEntry).bufferedReader().use { it.readText() }
        val doc = Jsoup.parse(xml, "", Parser.xmlParser())
        return doc.select("rootfile").firstOrNull()?.attr("full-path")
    }

    private fun parseNcxToc(
        ncxXml: String,
        ncxHref: String,
        spines: List<String>,
        out: MutableList<TocItem>
    ) {
        val ncxDir = ncxHref.substringBeforeLast('/', "").let { if (it.isNotEmpty()) "$it/" else "" }
        val doc = Jsoup.parse(ncxXml, "", Parser.xmlParser())

        fun walkNavPoints(parent: org.jsoup.nodes.Element, level: Int) {
            parent.children().forEach { el ->
                if (el.tagName().equals("navPoint", ignoreCase = true)) {
                    val label = el.select("> navLabel > text").firstOrNull()?.text() ?: ""
                    val contentSrc = el.select("> content").firstOrNull()?.attr("src") ?: ""
                    if (label.isNotBlank() && contentSrc.isNotBlank()) {
                        val resolvedSrc = resolvePath(ncxDir, contentSrc)
                        val targetHref = resolvedSrc.substringBefore('#')
                        val anchor = resolvedSrc.substringAfter('#', "").ifEmpty { null }
                        val spineIdx = spines.indexOfFirst { it == targetHref || it.endsWith(targetHref) }
                        if (spineIdx >= 0) {
                            out.add(
                                TocItem(
                                    title = label.trim(),
                                    spineIndex = spineIdx,
                                    anchor = anchor,
                                    level = level
                                )
                            )
                        }
                    }
                    walkNavPoints(el, level + 1)
                }
            }
        }

        val navMap = doc.select("navMap").firstOrNull() ?: return
        walkNavPoints(navMap, 0)
    }

    private fun resolvePath(baseDir: String, relative: String): String {
        return try {
            val baseUri = URI("file:///$baseDir")
            val resolved = baseUri.resolve(relative.replace(" ", "%20")).path
            resolved.removePrefix("/").replace("%20", " ")
        } catch (_: Exception) {
            if (baseDir.isEmpty()) relative else "$baseDir$relative"
        }
    }

    private fun isImageExt(path: String): Boolean {
        val ext = path.substringAfterLast('.', "").lowercase()
        return ext in ImageExtensions
    }
}
