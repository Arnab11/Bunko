package com.bunko.reader.engine.mobi

import com.bunko.reader.BunkoLog
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.Charset
import java.util.Arrays

/**
 * MOBI / AZW / PalmDOC Parser adapted directly from Librera Reader (com.foobnix.mobi.parser.MobiParser).
 * Uses on-demand slice reading via RandomAccessFile to prevent memory exhaustion on large files.
 */
class LibreraMobiParser(private val file: File) {

    companion object {
        const val COMPRESSION_NONE = 0
        const val COMPRESSION_PALMDOC = 2
        const val COMPRESSION_HUFF = 17480

        private val INDX = "INDX".toByteArray()

        fun byteArrayToInt(buffer: ByteArray): Int {
            var total = 0
            for (b in buffer) {
                total = (total shl 8) + (b.toInt() and 0xFF)
            }
            return total
        }

        fun asString(raw: ByteArray, offset: Int, len: Int, charset: Charset = Charsets.UTF_8): String {
            if (offset < 0 || offset + len > raw.size || len <= 0) return ""
            return String(raw.copyOfRange(offset, offset + len), charset)
        }

        fun asInt(raw: ByteArray, offset: Int, len: Int): Int {
            if (offset < 0 || offset + len > raw.size || len <= 0) return 0
            return byteArrayToInt(raw.copyOfRange(offset, offset + len))
        }

        /**
         * Librera Reader PalmDoc LZ77 decompression algorithm.
         */
        fun lz77(bytes: ByteArray): ByteArray {
            val outputStream = ByteArrayBuffer(bytes.size * 2)
            var i = 0
            while (i < bytes.size) {
                val b = bytes[i++].toInt() and 0xFF
                try {
                    when {
                        b == 0x00 -> {
                            outputStream.write(b)
                        }
                        b <= 0x08 -> {
                            for (j in 0 until b) {
                                if (i + j < bytes.size) {
                                    outputStream.write(bytes[i + j].toInt() and 0xFF)
                                }
                            }
                            i += b
                        }
                        b <= 0x7F -> {
                            outputStream.write(b)
                        }
                        b <= 0xBF -> {
                            if (i < bytes.size) {
                                val next = bytes[i++].toInt() and 0xFF
                                val combined = (b shl 8) or next
                                val length = (combined and 0x0007) + 3
                                val location = (combined shr 3) and 0x7FF

                                val rawBuf = outputStream.getRawData()
                                val outSize = outputStream.size()
                                for (j in 0 until length) {
                                    val copyPos = outSize - location + (j % maxOf(1, location))
                                    if (copyPos in 0 until outSize) {
                                        outputStream.write(rawBuf[copyPos].toInt() and 0xFF)
                                    }
                                }
                            }
                        }
                        else -> {
                            outputStream.write(' '.code)
                            outputStream.write(b xor 0x80)
                        }
                    }
                } catch (_: Exception) {
                }
            }
            return outputStream.toByteArray()
        }
    }

    class EXTH {
        var identifier: String = ""
        var len: Int = 0
        var count: Int = 0
        val headers = mutableMapOf<Int, ByteArray>()

        fun parse(raw: ByteArray): EXTH {
            val offset = indexOf(raw, "EXTH".toByteArray())
            if (offset < 0) return this
            identifier = asString(raw, offset, 4)
            len = asInt(raw, offset + 4, 4)
            count = asInt(raw, offset + 8, 4)

            var rOffset = offset + 12
            for (i in 0 until count) {
                if (rOffset + 8 > raw.size) break
                val rType = asInt(raw, rOffset, 4)
                val rLen = asInt(raw, rOffset + 4, 4)
                if (rLen < 8 || rOffset + rLen > raw.size) break
                val data = raw.copyOfRange(rOffset + 8, rOffset + rLen)
                rOffset += rLen
                headers[rType] = data
            }
            return this
        }

        private fun indexOf(input: ByteArray, search: ByteArray): Int {
            if (input.size < search.size) return -1
            for (i in 0..input.size - search.size) {
                var found = true
                for (j in search.indices) {
                    if (input[i + j] != search[j]) {
                        found = false
                        break
                    }
                }
                if (found) return i
            }
            return -1
        }
    }

    var name: String = ""
    var recordsCount: Int = 0
    val recordsOffset = mutableListOf<Long>()
    var mobiType: Int = 0
    var encoding: String = "UTF-8"
    var fullName: String = ""
    var locale: String = ""
    var firstImageIndex: Int = 0
    var firstContentIndex: Int = 1
    var lastContentIndex: Int = 0
    var compression: Int = COMPRESSION_NONE
    var bookSize: Int = 0
    var exth: EXTH = EXTH()

    init {
        loadHeader()
    }

    private fun loadHeader() {
        if (!file.exists() || file.length() < 100) return

        try {
            RandomAccessFile(file, "r").use { raf ->
                val headerBuf = ByteArray(78)
                raf.readFully(headerBuf)
                name = asString(headerBuf, 0, 32).trim { it <= ' ' || it == '\u0000' }

                raf.seek(76)
                recordsCount = raf.readShort().toInt() and 0xFFFF
                if (recordsCount <= 0) return

                for (i in 0 until recordsCount) {
                    raf.seek(78L + i * 8L)
                    val offset = raf.readInt().toLong() and 0xFFFFFFFFL
                    recordsOffset.add(offset)
                }
                if (recordsOffset.isEmpty()) return

                val mobiStart = recordsOffset[0]
                val mobiEnd = if (recordsOffset.size > 1) recordsOffset[1] else file.length()
                val mobiLen = (mobiEnd - mobiStart).toInt().coerceIn(16, 65536)
                val rec0 = ByteArray(mobiLen)
                raf.seek(mobiStart)
                raf.readFully(rec0)

                compression = asInt(rec0, 0, 2)
                bookSize = asInt(rec0, 4, 4)

                val encryption = asInt(rec0, 12, 2)
                mobiType = asInt(rec0, 24, 4)
                encoding = if (asInt(rec0, 28, 4) == 1252) "windows-1252" else "UTF-8"

                val fullNameOffset = asInt(rec0, 84, 4)
                val fullNameLen = asInt(rec0, 88, 4)
                if (fullNameOffset in 0..rec0.size && fullNameOffset + fullNameLen <= rec0.size && fullNameLen > 0) {
                    val titleCharset = runCatching { Charset.forName(encoding) }.getOrDefault(Charsets.UTF_8)
                    fullName = asString(rec0, fullNameOffset, fullNameLen, titleCharset).trim()
                }
                if (encryption != 0) {
                    fullName = "(DRM) $fullName"
                }

                locale = asString(rec0, 92, 4)
                firstImageIndex = asInt(rec0, 108, 4)
                val isEXTHFlag = (asInt(rec0, 128, 4) and 0x40) != 0
                firstContentIndex = asInt(rec0, 192, 2).coerceAtLeast(1)
                lastContentIndex = asInt(rec0, 194, 2)

                if (firstImageIndex <= 0) {
                    firstImageIndex = recordsCount
                }
                if (lastContentIndex <= 0 || lastContentIndex > recordsCount) {
                    lastContentIndex = firstImageIndex.coerceAtMost(recordsCount)
                }

                if (isEXTHFlag) {
                    exth = EXTH().parse(rec0)
                }
            }
        } catch (t: Throwable) {
            BunkoLog.w("Failed to read MOBI header from ${file.name}", t)
        }
    }

    fun getTitle(): String = fullName.ifBlank { name.ifBlank { file.nameWithoutExtension } }

    fun getAuthor(): String? = exth.headers[100]?.let { String(it).trim() }

    fun getPublisher(): String? = exth.headers[101]?.let { String(it).trim() }

    fun getDescription(): String? = exth.headers[103]?.let { String(it).trim() }

    fun getIsbn(): String? = exth.headers[104]?.let { String(it).trim() }

    fun getSubject(): String? = exth.headers[105]?.let { String(it).trim() }

    fun getPublishDate(): String? = exth.headers[106]?.let { String(it).trim() }

    fun getLanguage(): String? = exth.headers[524]?.let { String(it).trim() }

    fun getTextContent(): String {
        val outputStream = ByteArrayOutputStream()
        val endLimit = minOf(
            if (lastContentIndex > firstContentIndex) lastContentIndex else recordsCount,
            if (firstImageIndex > 0) firstImageIndex else recordsCount,
            recordsOffset.size
        )

        try {
            RandomAccessFile(file, "r").use { raf ->
                for (i in firstContentIndex until endLimit) {
                    val start = recordsOffset[i]
                    val end = if (i + 1 < recordsOffset.size) recordsOffset[i + 1] else file.length()
                    val len = (end - start).toInt()
                    if (len <= 0) continue

                    val coded = ByteArray(len)
                    raf.seek(start)
                    raf.readFully(coded)

                    val decoded: ByteArray = when (compression) {
                        COMPRESSION_PALMDOC -> lz77(coded)
                        COMPRESSION_NONE -> coded
                        else -> coded
                    }

                    if (decoded.size >= 4 && Arrays.equals(INDX, decoded.copyOfRange(0, 4))) {
                        continue
                    }

                    for (b in decoded) {
                        if (b != 0.toByte()) {
                            outputStream.write(b.toInt() and 0xFF)
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            BunkoLog.w("Failed to extract MOBI text content from ${file.name}", t)
        }

        val charset = runCatching { Charset.forName(encoding) }.getOrDefault(Charsets.UTF_8)
        return outputStream.toString(charset.name())
    }

    fun getRecordByIndex(index: Int): ByteArray? {
        if (index < 0 || index >= recordsOffset.size) return null
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val from = recordsOffset[index]
                val to = if (index + 1 < recordsOffset.size) recordsOffset[index + 1] else file.length()
                val len = (to - from).toInt()
                if (len <= 0) return null
                val data = ByteArray(len)
                raf.seek(from)
                raf.readFully(data)
                data
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun getAllImageRecords(): List<ByteArray> {
        val images = mutableListOf<ByteArray>()
        if (firstImageIndex <= 0 || firstImageIndex >= recordsOffset.size) return images

        try {
            RandomAccessFile(file, "r").use { raf ->
                for (i in firstImageIndex until recordsOffset.size) {
                    val from = recordsOffset[i]
                    val to = if (i + 1 < recordsOffset.size) recordsOffset[i + 1] else file.length()
                    val len = (to - from).toInt()
                    if (len < 16) continue
                    val data = ByteArray(len)
                    raf.seek(from)
                    raf.readFully(data)
                    if (isImageBytes(data)) {
                        images.add(data)
                    }
                }
            }
        } catch (t: Throwable) {
            BunkoLog.w("Failed to get image records from ${file.name}", t)
        }
        return images
    }

    fun getCoverOrThumb(): ByteArray? {
        val imgNumber = exth.headers[201] ?: exth.headers[202]
        if (imgNumber != null) {
            val index = byteArrayToInt(imgNumber)
            val coverRec = getRecordByIndex(index + firstImageIndex)
            if (coverRec != null && isImageBytes(coverRec)) {
                return coverRec
            }
        }

        // Fallback: search image records
        val end = minOf(recordsOffset.size, (firstImageIndex + 20).coerceAtLeast(recordsOffset.size))
        for (i in firstImageIndex until end) {
            val img = getRecordByIndex(i) ?: continue
            if (isImageBytes(img)) {
                return img
            }
        }
        return null
    }

    private fun isImageBytes(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        // JPEG
        if ((bytes[0].toInt() and 0xFF) == 0xFF && (bytes[1].toInt() and 0xFF) == 0xD8) return true
        // PNG
        if ((bytes[0].toInt() and 0xFF) == 0x89 && (bytes[1].toInt() and 0xFF) == 0x50 &&
            (bytes[2].toInt() and 0xFF) == 0x4E && (bytes[3].toInt() and 0xFF) == 0x47) return true
        // GIF
        if ((bytes[0].toInt() and 0xFF) == 0x47 && (bytes[1].toInt() and 0xFF) == 0x49 &&
            (bytes[2].toInt() and 0xFF) == 0x46) return true
        // WEBP
        if (bytes.size >= 12 && bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte()) return true
        return false
    }
}
