package com.bunko.reader.engine.archive

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Xml
import com.bunko.reader.BunkoLog
import com.bunko.reader.engine.model.ArchiveType
import com.bunko.reader.reader.internal.ReaderWebtoonDetector
import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.zip.ZipFile

data class ComicInfoMetadata(
    val title: String? = null,
    val series: String? = null,
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val publisher: String? = null,
    val summary: String? = null,
    val manga: String? = null,
    val format: String? = null,
    val isWebtoon: Boolean = false
)

object UniversalArchiveReader {

    private val ImageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "avif", "jxl")

    fun detectArchiveType(file: File): ArchiveType {
        if (file.isDirectory) return ArchiveType.FOLDER
        if (file.isFile && file.length() >= 4) {
            val magic = runCatching {
                file.inputStream().buffered().use { input ->
                    val bytes = ByteArray(8)
                    val read = input.read(bytes)
                    if (read >= 4) bytes else null
                }
            }.getOrNull()

            if (magic != null) {
                // ZIP signature: PK\x03\x04 or PK\x05\x06 or PK\x07\x08
                if (magic[0] == 0x50.toByte() && magic[1] == 0x4B.toByte()) {
                    return ArchiveType.ZIP
                }
                // RAR signature: Rar!\x1A\x07
                if (magic[0] == 0x52.toByte() && magic[1] == 0x61.toByte() && magic[2] == 0x72.toByte() && magic[3] == 0x21.toByte()) {
                    return ArchiveType.RAR
                }
                // 7Z signature: 7z\xBC\xAF\x27\x1C
                if (magic[0] == 0x37.toByte() && magic[1] == 0x7A.toByte() && magic[2] == 0xBC.toByte() && magic[3] == 0xAF.toByte()) {
                    return ArchiveType.SEVEN_ZIP
                }
            }
        }

        val name = file.name.lowercase().trim()
        return when {
            name.endsWith(".cbz") || name.endsWith(".zip") -> ArchiveType.ZIP
            name.endsWith(".cbr") || name.endsWith(".rar") -> ArchiveType.RAR
            name.endsWith(".cb7") || name.endsWith(".7z") -> ArchiveType.SEVEN_ZIP
            name.endsWith(".cbt") || name.endsWith(".tar") -> ArchiveType.TAR
            else -> ArchiveType.ZIP
        }
    }

    suspend fun listImageEntries(file: File): List<String> = withContext(Dispatchers.IO) {
        val primaryType = detectArchiveType(file)
        var entries = tryListEntries(file, primaryType)

        if (entries.isEmpty() && file.isFile) {
            // Fallback: try other archive formats if the file was misnamed or signature was non-standard
            val fallbacks = listOf(ArchiveType.ZIP, ArchiveType.RAR, ArchiveType.SEVEN_ZIP, ArchiveType.TAR)
                .filter { it != primaryType }
            for (type in fallbacks) {
                entries = tryListEntries(file, type)
                if (entries.isNotEmpty()) break
            }
        }

        entries.sortedWith { a, b -> compareNaturalFileNames(a, b) }
    }

    private fun tryListEntries(file: File, type: ArchiveType): List<String> {
        val entries = mutableListOf<String>()
        try {
            when (type) {
                ArchiveType.ZIP -> {
                    ZipFile(file).use { zip ->
                        val seq = zip.entries().asSequence()
                        for (entry in seq) {
                            if (!entry.isDirectory && isImageFile(entry.name)) {
                                entries.add(entry.name)
                            }
                        }
                    }
                }
                ArchiveType.RAR -> {
                    Archive(file).use { rar ->
                        for (header in rar.fileHeaders) {
                            if (!header.isDirectory && isImageFile(header.fileName)) {
                                entries.add(header.fileName)
                            }
                        }
                    }
                }
                ArchiveType.SEVEN_ZIP -> {
                    SevenZFile(file).use { sevenZ ->
                        var entry: SevenZArchiveEntry? = sevenZ.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory && isImageFile(entry.name)) {
                                entries.add(entry.name)
                            }
                            entry = sevenZ.nextEntry
                        }
                    }
                }
                ArchiveType.TAR -> {
                    TarArchiveInputStream(FileInputStream(file).buffered()).use { tar ->
                        var entry = tar.nextTarEntry
                        while (entry != null) {
                            if (!entry.isDirectory && isImageFile(entry.name)) {
                                entries.add(entry.name)
                            }
                            entry = tar.nextTarEntry
                        }
                    }
                }
                ArchiveType.FOLDER -> {
                    file.walkTopDown()
                        .filter { it.isFile && isImageFile(it.name) }
                        .map { it.relativeTo(file).path }
                        .forEach { entries.add(it) }
                }
            }
        } catch (_: Throwable) {
            // Silently allow fallback to try other formats
        }
        return entries
    }

    suspend fun extractEntryBytes(file: File, entryName: String): ByteArray? = withContext(Dispatchers.IO) {
        val primaryType = detectArchiveType(file)
        var bytes = tryExtractBytes(file, entryName, primaryType)

        if (bytes == null && file.isFile) {
            val fallbacks = listOf(ArchiveType.ZIP, ArchiveType.RAR, ArchiveType.SEVEN_ZIP, ArchiveType.TAR)
                .filter { it != primaryType }
            for (type in fallbacks) {
                bytes = tryExtractBytes(file, entryName, type)
                if (bytes != null) break
            }
        }
        bytes
    }

    private fun tryExtractBytes(file: File, entryName: String, type: ArchiveType): ByteArray? {
        val targetPath = entryName.replace('\\', '/')
        return try {
            when (type) {
                ArchiveType.ZIP -> {
                    ZipFile(file).use { zip ->
                        val entry = zip.getEntry(entryName)
                            ?: zip.getEntry(targetPath)
                            ?: zip.entries().asSequence().firstOrNull {
                                it.name.replace('\\', '/').equals(targetPath, ignoreCase = true)
                            }
                            ?: return@use null
                        zip.getInputStream(entry).use { it.readBytes() }
                    }
                }
                ArchiveType.RAR -> {
                    Archive(file).use { rar ->
                        val header = rar.fileHeaders.find {
                            it.fileName.replace('\\', '/').equals(targetPath, ignoreCase = true)
                        } ?: return@use null
                        val out = ByteArrayOutputStream()
                        rar.extractFile(header, out)
                        out.toByteArray()
                    }
                }
                ArchiveType.SEVEN_ZIP -> {
                    SevenZFile(file).use { sevenZ ->
                        var entry: SevenZArchiveEntry? = sevenZ.nextEntry
                        while (entry != null) {
                            if (entry.name.replace('\\', '/').equals(targetPath, ignoreCase = true)) {
                                val out = ByteArrayOutputStream()
                                val buffer = ByteArray(8192)
                                var read: Int
                                while (sevenZ.read(buffer).also { read = it } > 0) {
                                    out.write(buffer, 0, read)
                                }
                                return@use out.toByteArray()
                            }
                            entry = sevenZ.nextEntry
                        }
                        null
                    }
                }
                ArchiveType.TAR -> {
                    TarArchiveInputStream(FileInputStream(file).buffered()).use { tar ->
                        var entry = tar.nextTarEntry
                        while (entry != null) {
                            if (entry.name.replace('\\', '/').equals(targetPath, ignoreCase = true)) {
                                return@use tar.readBytes()
                            }
                            entry = tar.nextTarEntry
                        }
                        null
                    }
                }
                ArchiveType.FOLDER -> {
                    val direct = File(file, entryName)
                    if (direct.isFile) direct.readBytes()
                    else {
                        file.walkTopDown().firstOrNull {
                            it.isFile && it.relativeTo(file).path.replace('\\', '/').equals(targetPath, ignoreCase = true)
                        }?.readBytes()
                    }
                }
            }
        } catch (_: Throwable) {
            null
        }
    }

    suspend fun extractEntryStream(file: File, entryName: String): InputStream? {
        val bytes = extractEntryBytes(file, entryName) ?: return null
        return ByteArrayInputStream(bytes)
    }

    suspend fun extractCoverBitmap(
        file: File,
        maxWidth: Int = 400,
        maxHeight: Int = 600
    ): Bitmap? = withContext(Dispatchers.IO) {
        val entries = listImageEntries(file)
        if (entries.isEmpty()) return@withContext null

        val firstEntry = entries.first()
        val bytes = extractEntryBytes(file, firstEntry) ?: return@withContext null

        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)

        val sampleSize = calculateInSampleSize(boundsOptions.outWidth, boundsOptions.outHeight, maxWidth, maxHeight)
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }

        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
    }

    suspend fun extractComicInfo(file: File): ComicInfoMetadata? = withContext(Dispatchers.IO) {
        val xmlBytes = extractEntryBytes(file, "ComicInfo.xml")
            ?: if (file.isDirectory) {
                File(file, "ComicInfo.xml").takeIf { it.isFile }?.readBytes()
            } else null
            ?: return@withContext null

        runCatching {
            val parser = Xml.newPullParser()
            parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")

            var series: String? = null
            var title: String? = null
            var publisher: String? = null
            var summary: String? = null
            var manga: String? = null
            var format: String? = null
            val genres = mutableListOf<String>()
            val tags = mutableListOf<String>()

            var eventType = parser.eventType
            var currentTag = ""
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> currentTag = parser.name
                    XmlPullParser.TEXT -> {
                        val text = parser.text?.trim()
                        if (!text.isNullOrBlank()) {
                            when (currentTag.lowercase()) {
                                "series" -> series = text
                                "title" -> title = text
                                "publisher", "imprint" -> publisher = text
                                "summary", "notes", "scaninformation" -> summary = text
                                "manga" -> manga = text
                                "format" -> format = text
                                "genre" -> genres.addAll(text.split(',').map { it.trim() }.filter { it.isNotEmpty() })
                                "tags" -> tags.addAll(text.split(',').map { it.trim() }.filter { it.isNotEmpty() })
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> currentTag = ""
                }
                eventType = parser.next()
            }

            val isWebtoon = format?.contains("webtoon", ignoreCase = true) == true ||
                manga?.contains("webtoon", ignoreCase = true) == true ||
                ReaderWebtoonDetector.isWebtoonMetadata(
                    genres = genres,
                    tags = tags,
                    publishers = listOfNotNull(publisher),
                    summary = summary,
                    seriesName = series ?: title
                )

            ComicInfoMetadata(
                title = title,
                series = series,
                genres = genres,
                tags = tags,
                publisher = publisher,
                summary = summary,
                manga = manga,
                format = format,
                isWebtoon = isWebtoon
            )
        }.getOrNull()
    }

    suspend fun extractPageDimensions(
        file: File,
        entries: List<String>,
        sampleLimit: Int = 15
    ): Map<Int, Pair<Int, Int>> = withContext(Dispatchers.IO) {
        val dimensions = mutableMapOf<Int, Pair<Int, Int>>()
        val toSample = entries.take(sampleLimit)
        for ((index, entryName) in toSample.withIndex()) {
            val bytes = extractEntryBytes(file, entryName) ?: continue
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                dimensions[index] = Pair(bounds.outWidth, bounds.outHeight)
            }
        }
        dimensions
    }

    private fun isImageFile(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in ImageExtensions && !name.startsWith("__MACOSX") && !name.startsWith("._")
    }

    private fun calculateInSampleSize(
        rawWidth: Int,
        rawHeight: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        var inSampleSize = 1
        if (rawHeight > reqHeight || rawWidth > reqWidth) {
            val halfHeight = rawHeight / 2
            val halfWidth = rawWidth / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    fun compareNaturalFileNames(a: String, b: String): Int {
        var ia = 0
        var ib = 0
        val nza = a.replace('\\', '/')
        val nzb = b.replace('\\', '/')

        while (ia < nza.length && ib < nzb.length) {
            val ca = nza[ia]
            val cb = nzb[ib]

            if (ca.isDigit() && cb.isDigit()) {
                var startA = ia
                while (startA < nza.length && nza[startA] == '0') startA++
                var endA = startA
                while (endA < nza.length && nza[endA].isDigit()) endA++

                var startB = ib
                while (startB < nzb.length && nzb[startB] == '0') startB++
                var endB = startB
                while (endB < nzb.length && nzb[endB].isDigit()) endB++

                val numLenA = endA - startA
                val numLenB = endB - startB

                if (numLenA != numLenB) {
                    return numLenA - numLenB
                }

                for (k in 0 until numLenA) {
                    val da = nza[startA + k]
                    val db = nzb[startB + k]
                    if (da != db) return da - db
                }

                // If identical numbers, skip to ends
                var scanA = ia
                while (scanA < nza.length && nza[scanA].isDigit()) scanA++
                var scanB = ib
                while (scanB < nzb.length && nzb[scanB].isDigit()) scanB++
                ia = scanA
                ib = scanB
            } else {
                val diff = ca.lowercaseChar() - cb.lowercaseChar()
                if (diff != 0) return diff
                ia++
                ib++
            }
        }
        return nza.length - nzb.length
    }
}
