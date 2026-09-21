package com.bunko.reader.offline

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.util.Xml
import com.bunko.reader.BunkoLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

object LocalBookScanner {

    private val SupportedExtensions = setOf(
        "cbz", "cbr", "cb7", "cbt", "zip", "rar", "7z",
        "epub", "mobi", "prc", "azw", "azw3", "fb2", "pdf", "txt", "md"
    )
    private val ImageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "avif")
    private const val CoverMaxWidth = 400
    private const val CoverMaxHeight = 600
    private const val CoverJpegQuality = 85

    suspend fun scanTree(
        context: Context,
        treeUri: Uri,
        folderName: String = "",
        maxDepth: Int = 3
    ): List<LocalBook> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val books = mutableListOf<LocalBook>()
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val folderUriStr = treeUri.toString()

        fun scanFolder(folderDocId: String, currentDepth: Int) {
            if (currentDepth > maxDepth) return
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, folderDocId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            )

            try {
                resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val sizeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                    val modifiedCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                    while (cursor.moveToNext()) {
                        val docId = cursor.getString(idCol)
                        val name = cursor.getString(nameCol) ?: continue
                        val mime = cursor.getString(mimeCol) ?: ""
                        val size = cursor.getLong(sizeCol)
                        val modified = cursor.getLong(modifiedCol)

                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            scanFolder(docId, currentDepth + 1)
                        } else {
                            val ext = name.substringAfterLast('.', "").lowercase().trim()
                            if (ext in SupportedExtensions) {
                                val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                                val bookId = hashUri(fileUri.toString())
                                val cleanTitle = name.substringBeforeLast('.')
                                    .replace('_', ' ')
                                    .replace(Regex("\\s+"), " ")
                                    .trim()
                                val isWebtoonCandidate = com.bunko.reader.reader.internal.ReaderWebtoonDetector.isWebtoonMetadata(seriesName = cleanTitle)
                                val localBook = LocalBook(
                                    id = bookId,
                                    title = cleanTitle,
                                    uriString = fileUri.toString(),
                                    extension = ext,
                                    format = LocalBookFormat.fromExtension(ext),
                                    sizeBytes = size,
                                    lastModified = modified,
                                    folderUriString = folderUriStr,
                                    folderName = folderName,
                                    isWebtoon = isWebtoonCandidate
                                )
                                books.add(localBook)
                                BunkoLog.i("Found local book: ${localBook.title} (${localBook.format}, ext=$ext, size=$size)")
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                BunkoLog.w("Failed to scan directory $folderDocId", t)
            }
        }

        scanFolder(rootDocId, 1)
        books.sortedWith(compareBy<LocalBook> { it.title.lowercase() })
    }

    suspend fun generateCover(
        context: Context,
        book: LocalBook
    ): String? = withContext(Dispatchers.IO) {
        val coversDir = File(context.cacheDir, "local_covers").apply { mkdirs() }
        val coverFile = File(coversDir, "${book.id}.jpg")
        if (coverFile.isFile && coverFile.length() > 0) {
            return@withContext coverFile.absolutePath
        }

        // Prepare local temp file to extract cover using universal engine
        val cacheFolder = File(context.cacheDir, "cover_staging").apply { mkdirs() }
        val tempBookFile = File(cacheFolder, "${book.id}.${book.extension}")
        val uri = Uri.parse(book.uriString)

        val fileReady = if (tempBookFile.isFile && tempBookFile.length() > 0) {
            tempBookFile
        } else {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempBookFile.outputStream().use { output -> input.copyTo(output) }
                }
                tempBookFile
            }.getOrNull()
        } ?: return@withContext null

        val bitmap: Bitmap? = when (book.format) {
            LocalBookFormat.PDF -> {
                com.bunko.reader.engine.pdf.PdfDocumentEngine.extractCoverBitmap(fileReady, CoverMaxWidth, CoverMaxHeight)
            }
            LocalBookFormat.EPUB -> {
                com.bunko.reader.engine.epub.EpubPackageReader.extractCoverBitmap(context, fileReady, CoverMaxWidth, CoverMaxHeight)
            }
            LocalBookFormat.MOBI, LocalBookFormat.AZW, LocalBookFormat.AZW3 -> {
                com.bunko.reader.engine.mobi.MobiDocumentReader.extractCoverBitmap(context, fileReady, CoverMaxWidth, CoverMaxHeight)
            }
            LocalBookFormat.FB2 -> {
                com.bunko.reader.engine.fb2.Fb2DocumentReader.extractCoverBitmap(context, fileReady, CoverMaxWidth, CoverMaxHeight)
            }
            LocalBookFormat.CBZ, LocalBookFormat.CBR, LocalBookFormat.CB7,
            LocalBookFormat.CBT, LocalBookFormat.ZIP, LocalBookFormat.RAR,
            LocalBookFormat.SEVEN_ZIP -> {
                com.bunko.reader.engine.archive.UniversalArchiveReader.extractCoverBitmap(fileReady, CoverMaxWidth, CoverMaxHeight)
            }
            else -> null
        }

        if (bitmap != null) {
            try {
                coverFile.outputStream().buffered().use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, CoverJpegQuality, stream)
                }
                bitmap.recycle()
                return@withContext coverFile.absolutePath
            } catch (t: Throwable) {
                BunkoLog.w("Failed to save cover for ${book.title}", t)
                bitmap.recycle()
            }
        }
        null
    }

    private fun extractPdfCover(resolver: ContentResolver, uri: Uri): Bitmap? {
        return runCatching {
            resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (renderer.pageCount <= 0) return@runCatching null
                    renderer.openPage(0).use { page ->
                        val scale = minOf(
                            CoverMaxWidth.toFloat() / page.width.coerceAtLeast(1),
                            CoverMaxHeight.toFloat() / page.height.coerceAtLeast(1)
                        ).coerceAtLeast(0.1f)
                        val width = (page.width * scale).toInt().coerceAtLeast(1)
                        val height = (page.height * scale).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap
                    }
                }
            }
        }.getOrNull()
    }

    private fun extractZipCover(resolver: ContentResolver, uri: Uri): Bitmap? {
        return runCatching {
            resolver.openInputStream(uri)?.use { stream ->
                ZipInputStream(stream.buffered()).use { zis ->
                    var entry: ZipEntry? = zis.nextEntry
                    var firstImageBytes: ByteArray? = null
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val ext = entry.name.substringAfterLast('.', "").lowercase()
                            if (ext in ImageExtensions) {
                                firstImageBytes = zis.readBytes()
                                break
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                    firstImageBytes?.let { bytes ->
                        decodeScaledBitmap(bytes, CoverMaxWidth, CoverMaxHeight)
                    }
                }
            }
        }.getOrNull()
    }

    private fun extractEpubCover(resolver: ContentResolver, uri: Uri): Bitmap? {
        return runCatching {
            resolver.openInputStream(uri)?.use { stream ->
                val entries = mutableMapOf<String, ByteArray>()
                ZipInputStream(stream.buffered()).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val name = entry.name.lowercase()
                        if (name.endsWith("container.xml") || name.endsWith(".opf") ||
                            name.contains("cover") && name.substringAfterLast('.', "") in ImageExtensions
                        ) {
                            entries[entry.name] = zis.readBytes()
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
                // Try finding cover image from entries directly
                val coverEntry = entries.keys.firstOrNull { key ->
                    val lower = key.lowercase()
                    (lower.contains("cover") || lower.contains("titlepage")) &&
                        lower.substringAfterLast('.', "") in ImageExtensions
                } ?: entries.keys.firstOrNull { key ->
                    key.substringAfterLast('.', "").lowercase() in ImageExtensions
                }

                coverEntry?.let { key ->
                    entries[key]?.let { bytes ->
                        decodeScaledBitmap(bytes, CoverMaxWidth, CoverMaxHeight)
                    }
                }
            }
        }.getOrNull()
    }

    private fun decodeScaledBitmap(bytes: ByteArray, maxWidth: Int, maxHeight: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var inSample = 1
        while (bounds.outWidth / (inSample * 2) >= maxWidth && bounds.outHeight / (inSample * 2) >= maxHeight) {
            inSample *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = inSample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions) ?: return null
        val scale = minOf(
            maxWidth.toFloat() / decoded.width.coerceAtLeast(1),
            maxHeight.toFloat() / decoded.height.coerceAtLeast(1),
            1f
        )
        val finalWidth = (decoded.width * scale).toInt().coerceAtLeast(1)
        val finalHeight = (decoded.height * scale).toInt().coerceAtLeast(1)
        val output = Bitmap.createBitmap(finalWidth, finalHeight, Bitmap.Config.RGB_565)
        Canvas(output).apply {
            drawColor(Color.WHITE)
            drawBitmap(decoded, null, Rect(0, 0, finalWidth, finalHeight), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }
        if (output !== decoded) {
            decoded.recycle()
        }
        return output
    }

    fun createBookFromSingleUri(context: Context, uri: Uri, intentMimeType: String? = null): LocalBook? {
        val resolver = context.contentResolver
        var displayName: String? = null
        var sizeBytes: Long = 0L
        var lastModified: Long = System.currentTimeMillis()

        if (uri.scheme == "file") {
            val file = uri.path?.let { File(it) }
            if (file != null && file.exists()) {
                displayName = file.name
                sizeBytes = file.length()
                lastModified = file.lastModified()
            }
        } else {
            try {
                resolver.query(
                    uri,
                    arrayOf(android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) displayName = cursor.getString(nameIndex)
                        val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                        if (sizeIndex != -1) sizeBytes = cursor.getLong(sizeIndex)
                    }
                }
            } catch (t: Throwable) {
                BunkoLog.w("Could not query openable columns for $uri: ${t.message}")
            }
        }

        if (displayName.isNullOrBlank()) {
            displayName = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')
        }
        if (displayName.isNullOrBlank()) {
            displayName = "Opened Document"
        }

        val rawExt = displayName!!.substringAfterLast('.', "").lowercase().trim()
        val format = LocalBookFormat.fromExtension(rawExt)
        var ext = if (format != LocalBookFormat.UNKNOWN && SupportedExtensions.contains(rawExt)) {
            rawExt
        } else {
            // The display name carries no usable extension (opaque content URIs from
            // file managers/shares often look like this). Fall back to the provider
            // MIME, then the sending intent's MIME (which file managers usually set
            // correctly even when the provider reports application/octet-stream).
            val providerMime = runCatching { resolver.getType(uri) }.getOrNull()
            extFromMime(providerMime) ?: extFromMime(intentMimeType)
        }
        if (ext == null) {
            // Last resort: sniff magic bytes so extensionless-but-valid books still open.
            ext = sniffExtension(context, uri)
        }

        val effectiveFormat = ext?.let { LocalBookFormat.fromExtension(it) } ?: LocalBookFormat.UNKNOWN
        if (effectiveFormat == LocalBookFormat.UNKNOWN || (ext != null && !SupportedExtensions.contains(ext))) {
            return null
        }
        val resolvedExt = ext!!

        val bookId = hashUri(uri.toString())
        val cleanTitle = displayName!!.substringBeforeLast('.')
            .replace('_', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { displayName!! }

        val isWebtoonCandidate = com.bunko.reader.reader.internal.ReaderWebtoonDetector.isWebtoonMetadata(seriesName = cleanTitle)
        return LocalBook(
            id = bookId,
            title = cleanTitle,
            uriString = uri.toString(),
            extension = resolvedExt,
            format = effectiveFormat,
            sizeBytes = sizeBytes,
            lastModified = lastModified,
            isWebtoon = isWebtoonCandidate,
            isExternalFile = true
        )
    }

    fun hashUri(uri: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(uri.toByteArray())
            .take(8)
            .joinToString("") { "%02x".format(it) }
    }

    private fun extFromMime(mime: String?): String? {
        if (mime.isNullOrBlank()) return null
        // Generic/unknown container types carry no signal; fall through to sniffing.
        if (mime.equals("application/octet-stream", ignoreCase = true) ||
            mime.equals("application/binary", ignoreCase = true) ||
            mime.equals("*/*", ignoreCase = true)
        ) return null
        return when {
            mime.contains("epub", ignoreCase = true) -> "epub"
            mime.contains("cbz", ignoreCase = true) ||
                mime.contains("comicbook+zip", ignoreCase = true) -> "cbz"
            mime.contains("cbr", ignoreCase = true) ||
                mime.contains("comicbook-rar", ignoreCase = true) -> "cbr"
            mime.contains("cb7", ignoreCase = true) -> "cb7"
            mime.contains("cbt", ignoreCase = true) -> "cbt"
            mime.contains("mobi", ignoreCase = true) ||
                mime.contains("mobipocket", ignoreCase = true) ||
                mime.contains("mobi8", ignoreCase = true) -> "mobi"
            mime.contains("azw3", ignoreCase = true) -> "azw3"
            mime.contains("azw", ignoreCase = true) -> "azw"
            mime.contains("fb2", ignoreCase = true) -> "fb2"
            mime.contains("pdf", ignoreCase = true) -> "pdf"
            mime.contains("7z", ignoreCase = true) ||
                mime.contains("7-zip", ignoreCase = true) -> "7z"
            mime.contains("rar", ignoreCase = true) -> "rar"
            mime.contains("tar", ignoreCase = true) -> "tar"
            mime.contains("zip", ignoreCase = true) -> "zip"
            mime.contains("markdown", ignoreCase = true) -> "md"
            mime.equals("text/plain", ignoreCase = true) ||
                mime.startsWith("text/", ignoreCase = true) -> "txt"
            else -> null
        }
    }

    /**
     * Reads a small header window and guesses the container from magic bytes, so
     * extensionless files shared by file managers/chat apps still open. Returns a
     * supported extension or null when nothing matches.
     */
    private fun sniffExtension(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { raw ->
                val window = ByteArray(4096)
                var read = 0
                while (read < window.size) {
                    val n = raw.read(window, read, window.size - read)
                    if (n <= 0) break
                    read += n
                }
                if (read < 4) return null
                fun byteAt(i: Int): Int = if (i < read) window[i].toInt() and 0xFF else -1
                // PDF: %PDF
                if (byteAt(0) == 0x25 && byteAt(1) == 0x50 && byteAt(2) == 0x44 && byteAt(3) == 0x46) {
                    return "pdf"
                }
                // ZIP family: PK\x03\x04 — EPUB has an uncompressed "mimetype" first entry
                if (byteAt(0) == 0x50 && byteAt(1) == 0x4B) {
                    return if (isEpubZip(window, read)) "epub" else "zip"
                }
                // RAR: Rar!
                if (byteAt(0) == 0x52 && byteAt(1) == 0x61 && byteAt(2) == 0x72 && byteAt(3) == 0x21) {
                    return "rar"
                }
                // 7z: 37 7A BC AF 27 1C
                if (read >= 6 && byteAt(0) == 0x37 && byteAt(1) == 0x7A && byteAt(2) == 0xBC &&
                    byteAt(3) == 0xAF && byteAt(4) == 0x27 && byteAt(5) == 0x1C
                ) {
                    return "7z"
                }
                // TAR: "ustar" at offset 257
                if (read >= 262 && byteAt(257) == 0x75 && byteAt(258) == 0x73 &&
                    byteAt(259) == 0x74 && byteAt(260) == 0x61 && byteAt(261) == 0x72
                ) {
                    return "tar"
                }
                if (read >= 68) {
                    // MOBI/AZW PalmDB header: BOOKMOBI at offset 60
                    var isMobi = true
                    val mobiMagic = "BOOKMOBI"
                    for (i in mobiMagic.indices) {
                        if (byteAt(60 + i) != mobiMagic[i].code) {
                            isMobi = false
                            break
                        }
                    }
                    if (isMobi) return "mobi"
                }
                val headText = String(window, 0, minOf(read, 1024), Charsets.UTF_8)
                if (headText.contains("FictionBook", ignoreCase = true)) return "fb2"
                null
            }
        } catch (t: Throwable) {
            BunkoLog.w("Could not sniff container for $uri: ${t.message}")
            null
        }
    }

    private fun isEpubZip(window: ByteArray, read: Int): Boolean {
        return try {
            // Parse the first local file header: name length @26 (LE u16), extra @28.
            if (read < 30) return false
            if (window[0] != 0x50.toByte() || window[1] != 0x4B.toByte() ||
                window[2] != 0x03.toByte() || window[3] != 0x04.toByte()
            ) return false
            val nameLen = ((window[26].toInt() and 0xFF) or ((window[27].toInt() and 0xFF) shl 8))
            val extraLen = ((window[28].toInt() and 0xFF) or ((window[29].toInt() and 0xFF) shl 8))
            if (nameLen <= 0 || nameLen > 256) return false
            val nameStart = 30
            val nameEnd = nameStart + nameLen
            if (nameEnd > read) return false
            val entryName = String(window, nameStart, nameLen, Charsets.US_ASCII)
            if (entryName != "mimetype") return false
            val contentStart = nameEnd + extraLen
            val probe = "application/epub+zip"
            if (contentStart + probe.length > read) return false
            String(window, contentStart, probe.length, Charsets.US_ASCII) == probe
        } catch (t: Throwable) {
            false
        }
    }
}
