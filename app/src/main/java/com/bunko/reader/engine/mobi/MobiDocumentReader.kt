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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class ParsedMobi(
    val title: String,
    val spines: List<ReflowSpine>,
    val tableOfContents: List<TocItem>,
    val coverPath: String? = null,
    val resourceDir: File
)

object MobiDocumentReader {

    suspend fun parseMobi(context: Context, file: File): ParsedMobi = withContext(Dispatchers.IO) {
        val cacheKey = "mobi_${file.nameWithoutExtension.hashCode()}_${file.length()}"
        val resourceDir = File(context.cacheDir, cacheKey).apply { if (!exists()) mkdirs() }

        var title = file.nameWithoutExtension
        val spines = mutableListOf<ReflowSpine>()
        val toc = mutableListOf<TocItem>()
        var coverPath: String? = null

        RandomAccessFile(file, "r").use { raf ->
            val numRecords = readHeaderAndRecordCount(raf)
            if (numRecords <= 1) throw IllegalArgumentException("Invalid MOBI / PalmDoc database")

            val recordOffsets = LongArray(numRecords)
            for (i in 0 until numRecords) {
                raf.seek(78L + i * 8L)
                recordOffsets[i] = raf.readInt().toLong() and 0xFFFFFFFFL
            }

            // Read Record 0
            val rec0Len = (recordOffsets[1] - recordOffsets[0]).toInt()
            val rec0Bytes = ByteArray(rec0Len)
            raf.seek(recordOffsets[0])
            raf.readFully(rec0Bytes)

            val rec0Buf = ByteBuffer.wrap(rec0Bytes).order(ByteOrder.BIG_ENDIAN)
            val compression = rec0Buf.getShort(0).toInt() and 0xFFFF
            val textRecordCount = rec0Buf.getShort(8).toInt() and 0xFFFF

            // Try to extract MOBI title
            if (rec0Bytes.size >= 88) {
                val mobiHeaderOffset = 16
                val magic = String(rec0Bytes, mobiHeaderOffset + 4, 4)
                if (magic == "MOBI") {
                    val fullTitleOffset = rec0Buf.getInt(mobiHeaderOffset + 68)
                    val fullTitleLength = rec0Buf.getInt(mobiHeaderOffset + 72)
                    if (fullTitleOffset in 0 until rec0Bytes.size && fullTitleLength > 0 && fullTitleOffset + fullTitleLength <= rec0Bytes.size) {
                        val extracted = String(rec0Bytes, fullTitleOffset, fullTitleLength).trim()
                        if (extracted.isNotBlank()) title = extracted
                    }
                }
            }

            // Decompress text records
            val textStream = ByteArrayOutputStream()
            val safeTextRecCount = textRecordCount.coerceIn(1, numRecords - 1)
            for (i in 1..safeTextRecCount) {
                val start = recordOffsets[i]
                val end = if (i + 1 < numRecords) recordOffsets[i + 1] else file.length()
                val len = (end - start).toInt()
                if (len <= 0) continue

                val bytes = ByteArray(len)
                raf.seek(start)
                raf.readFully(bytes)

                when (compression) {
                    1 -> textStream.write(bytes) // None
                    2 -> decompressPalmDoc(bytes, textStream) // PalmDOC LZ77
                    else -> textStream.write(bytes)
                }
            }

            // Extract images from subsequent records
            var imageIndex = 1
            for (i in (safeTextRecCount + 1) until numRecords) {
                val start = recordOffsets[i]
                val end = if (i + 1 < numRecords) recordOffsets[i + 1] else file.length()
                val len = (end - start).toInt()
                if (len < 16) continue

                val bytes = ByteArray(len)
                raf.seek(start)
                raf.readFully(bytes)

                val ext = detectImageFormat(bytes)
                if (ext != null) {
                    val imgFile = File(resourceDir, "image_${String.format("%04d", imageIndex)}.$ext")
                    if (!imgFile.exists() || imgFile.length() == 0L) {
                        imgFile.writeBytes(bytes)
                    }
                    if (coverPath == null) coverPath = imgFile.absolutePath
                    imageIndex++
                }
            }

            val rawHtml = textStream.toString("UTF-8").ifEmpty { textStream.toString("ISO-8859-1") }
            val doc = Jsoup.parse(rawHtml)

            // Rewrite <img> tags to local images
            var imgCount = 1
            doc.select("img, image, mbp\\:pagebreak").forEach { el ->
                if (el.tagName().equals("img", ignoreCase = true)) {
                    val imgFile = File(resourceDir, "image_${String.format("%04d", imgCount)}.jpg")
                    val pngFile = File(resourceDir, "image_${String.format("%04d", imgCount)}.png")
                    val actual = if (imgFile.exists()) imgFile else if (pngFile.exists()) pngFile else null
                    if (actual != null) {
                        el.attr("src", actual.absolutePath)
                    }
                    imgCount++
                }
            }

            // Split into chapters by <mbp:pagebreak>, <h1>, <h2> or chunk size
            val body = doc.body()
            val sections = doc.select("div.chapter, section, mbp\\:pagebreak")
            if (sections.isNotEmpty()) {
                var currentSpineIdx = 0
                val chapters = rawHtml.split(Regex("(?i)<mbp:pagebreak[^>]*>|(?i)<div class=[\"']chapter[\"']>"))
                chapters.forEachIndexed { idx, chunk ->
                    if (chunk.isNotBlank()) {
                        spines.add(
                            ReflowSpine(
                                id = "mobi_spine_$idx",
                                spineIndex = idx,
                                title = "Chapter ${idx + 1}",
                                rawHtml = chunk
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
        }

        ParsedMobi(
            title = title,
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
            RandomAccessFile(file, "r").use { raf ->
                val numRecords = readHeaderAndRecordCount(raf)
                if (numRecords <= 1) return@use null

                val recordOffsets = LongArray(numRecords)
                for (i in 0 until numRecords) {
                    raf.seek(78L + i * 8L)
                    recordOffsets[i] = raf.readInt().toLong() and 0xFFFFFFFFL
                }

                // Check first few image records
                for (i in 1 until numRecords.coerceAtMost(50)) {
                    val start = recordOffsets[i]
                    val end = if (i + 1 < numRecords) recordOffsets[i + 1] else file.length()
                    val len = (end - start).toInt()
                    if (len < 32) continue

                    val bytes = ByteArray(len)
                    raf.seek(start)
                    raf.readFully(bytes)

                    if (detectImageFormat(bytes) != null) {
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
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                        if (bmp != null) return@use bmp
                    }
                }
                null
            }
        } catch (t: Throwable) {
            BunkoLog.w("Failed to extract MOBI cover from ${file.name}", t)
            null
        }
    }

    private fun readHeaderAndRecordCount(raf: RandomAccessFile): Int {
        raf.seek(76)
        return raf.readShort().toInt() and 0xFFFF
    }

    private fun decompressPalmDoc(src: ByteArray, out: ByteArrayOutputStream) {
        var i = 0
        while (i < src.size) {
            val b = src[i].toInt() and 0xFF
            i++
            when {
                b == 0 -> {} // literal null
                b in 1..8 -> {
                    // copy literal b bytes
                    val count = b
                    if (i + count <= src.size) {
                        out.write(src, i, count)
                        i += count
                    }
                }
                b in 9..0x7F -> {
                    // literal character
                    out.write(b)
                }
                b in 0x80..0xBF -> {
                    // distance & length pair
                    if (i < src.size) {
                        val b2 = src[i].toInt() and 0xFF
                        i++
                        val distance = (((b and 0x3F) shl 3) or (b2 shr 5)) + 1
                        val length = (b2 and 0x07) + 3
                        val currentBytes = out.toByteArray()
                        val copyStart = currentBytes.size - distance
                        if (copyStart >= 0) {
                            for (k in 0 until length) {
                                out.write(currentBytes[(copyStart + (k % distance))].toInt() and 0xFF)
                            }
                        }
                    }
                }
                b >= 0xC0 -> {
                    // space followed by char
                    out.write(' '.code)
                    out.write(b xor 0x80)
                }
            }
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
