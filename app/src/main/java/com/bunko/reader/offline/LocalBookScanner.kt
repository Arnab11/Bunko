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

    private val SupportedExtensions = setOf("cbz", "zip", "epub", "pdf")
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
                            val ext = name.substringAfterLast('.', "").lowercase()
                            if (ext in SupportedExtensions) {
                                val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                                val bookId = hashUri(fileUri.toString())
                                val cleanTitle = name.substringBeforeLast('.')
                                    .replace('_', ' ')
                                    .replace(Regex("\\s+"), " ")
                                    .trim()
                                books.add(
                                    LocalBook(
                                        id = bookId,
                                        title = cleanTitle,
                                        uriString = fileUri.toString(),
                                        extension = ext,
                                        format = LocalBookFormat.fromExtension(ext),
                                        sizeBytes = size,
                                        lastModified = modified,
                                        folderUriString = folderUriStr,
                                        folderName = folderName
                                    )
                                )
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

        val uri = Uri.parse(book.uriString)
        val resolver = context.contentResolver

        val bitmap: Bitmap? = when (book.format) {
            LocalBookFormat.PDF -> extractPdfCover(resolver, uri)
            LocalBookFormat.CBZ, LocalBookFormat.ZIP -> extractZipCover(resolver, uri)
            LocalBookFormat.EPUB -> extractEpubCover(resolver, uri)
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

    fun hashUri(uri: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(uri.toByteArray())
            .take(8)
            .joinToString("") { "%02x".format(it) }
    }
}
