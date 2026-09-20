package com.bunko.reader.download

import android.app.DownloadManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.StatFs
import android.util.Xml
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import com.bunko.reader.FileDimensionDto
import com.bunko.reader.BunkoLog
import com.bunko.reader.KavitaApi
import com.bunko.reader.KavitaSession
import com.bunko.reader.MarkChapterReadDto
import com.bunko.reader.MarkVolumesReadDto
import com.bunko.reader.ProgressDto
import com.bunko.reader.normalizeKavitaBaseUrl

private val Context.offlineIssueDataStore by preferencesDataStore("offline_issues")

private class UnsupportedOfflineFormatException(message: String) : Exception(message)

class InsufficientOfflineStorageException(message: String) : Exception(message)

@Serializable
enum class OfflineDownloadStatus {
    Queued,
    Downloading,
    Ready,
    Failed,
    Unsupported,
    Removed
}

@Serializable
data class OfflineIssueRecord(
    val serverKey: String,
    val chapterId: Int,
    val libraryId: Int,
    val seriesId: Int,
    val volumeId: Int,
    val seriesName: String,
    val issueName: String,
    val downloadId: Long,
    val archivePath: String,
    val expectedPageCount: Int = 0,
    val pageCount: Int = 0,
    val status: OfflineDownloadStatus = OfflineDownloadStatus.Queued,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val errorMessage: String? = null,
    val localPage: Int = 0,
    val progressPending: Boolean = false,
    val markReadPending: Boolean = false,
    val markUnreadPending: Boolean = false,
    val coverPath: String = ""
) {
    val progressFraction: Float?
        get() = totalBytes.takeIf { it > 0 }?.let {
            downloadedBytes.toFloat().div(it.toFloat()).coerceIn(0f, 1f)
        }
}

data class OfflineChapter(
    val record: OfflineIssueRecord,
    val pages: List<OfflinePage>,
    val dimensions: Map<Int, FileDimensionDto>,
    val coverPageIndex: Int = 0
)

fun OfflineIssueRecord.localCoverFile(): File? =
    coverPath.takeIf { it.isNotBlank() }
        ?.let(::File)
        ?.takeIf { it.isFile && it.length() > 0 }

sealed interface OfflinePage {
    val index: Int

    data class ArchiveEntry(
        val archivePath: String,
        val entryName: String,
        override val index: Int
    ) : OfflinePage

    data class PdfPage(
        val pdfPath: String,
        override val index: Int,
        val width: Int,
        val height: Int
    ) : OfflinePage
}

fun getCachedOfflinePage(page: OfflinePage, targetWidth: Int = 0, targetHeight: Int = 0): Bitmap? {
    return when (page) {
        is OfflinePage.PdfPage -> {
            val file = File(page.pdfPath)
            if (file.isFile) {
                com.bunko.reader.engine.pdf.PdfDocumentEngine.getCachedBitmap(file, page.index)
            } else null
        }
        is OfflinePage.ArchiveEntry -> {
            val key = "${page.archivePath}:${page.entryName}"
            archivePageCache.get(key)?.takeIf { !it.isRecycled }
        }
    }
}

suspend fun decodeOfflinePage(
    page: OfflinePage,
    targetWidth: Int,
    targetHeight: Int
): Bitmap? = withContext(Dispatchers.IO) {
    getCachedOfflinePage(page, targetWidth, targetHeight)?.let { return@withContext it }
    when (page) {
        is OfflinePage.ArchiveEntry -> decodeArchivePage(page, targetWidth, targetHeight)
        is OfflinePage.PdfPage -> decodePdfPage(page, targetWidth, targetHeight)
    }
}

class OfflineIssueRepository(context: Context) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val recordsKey = stringPreferencesKey("records")
    private val json = Json { ignoreUnknownKeys = true }

    fun observe(session: KavitaSession, chapterId: Int): Flow<OfflineIssueRecord?> {
        val key = sessionKey(session)
        return appContext.offlineIssueDataStore.data.map { preferences ->
            decode(preferences[recordsKey]).firstOrNull {
                it.serverKey == key && it.chapterId == chapterId
            }
        }
    }

    fun observeDownloaded(session: KavitaSession): Flow<List<OfflineIssueRecord>> {
        val key = sessionKey(session)
        return appContext.offlineIssueDataStore.data.map { preferences ->
            decode(preferences[recordsKey])
                .filter { it.serverKey == key && it.status == OfflineDownloadStatus.Ready }
                .sortedWith(compareBy<OfflineIssueRecord> { it.seriesName.lowercase() }
                    .thenBy { it.volumeId }
                    .thenBy { it.chapterId })
        }
    }

    suspend fun cleanupUnavailableDownload(session: KavitaSession, chapterId: Int): Boolean {
        val record = current(sessionKey(session), chapterId) ?: return false
        if (record.errorMessage != LegacyUnavailableDownloadMessage) return false
        remove(session, chapterId)
        return true
    }

    suspend fun enqueue(
        session: KavitaSession,
        libraryId: Int,
        seriesId: Int,
        volumeId: Int,
        chapterId: Int,
        seriesName: String,
        issueName: String,
        expectedBytes: Long? = null,
        expectedPageCount: Int? = null
    ): OfflineIssueRecord = withContext(Dispatchers.IO) {
        val key = sessionKey(session)
        val previous = current(key, chapterId)
        previous?.let { existing ->
            if (existing.status in setOf(
                    OfflineDownloadStatus.Queued,
                    OfflineDownloadStatus.Downloading,
                    OfflineDownloadStatus.Ready
                )) return@withContext existing
            if (existing.downloadId > 0) manager.remove(existing.downloadId)
            removeFiles(existing)
        }

        val root = normalizeKavitaBaseUrl(session.baseUrl)
        val url = "$root/api/Download/chapter?chapterId=$chapterId"
        val safeName = "$seriesName - $issueName"
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .take(120)
            .ifBlank { "issue-$chapterId" }
        val archive = archiveFile(key, chapterId)
        archive.delete()
        coverFile(key, chapterId).delete()
        expectedBytes?.takeIf { it > 0 }?.let { bytes ->
            val available = StatFs(archive.parentFile?.absolutePath ?: appContext.filesDir.absolutePath)
                .availableBytes
            if (!hasEnoughOfflineStorage(bytes, available)) {
                throw InsufficientOfflineStorageException(
                    "Not enough storage: ${formatBytes(bytes)} download, " +
                        "${formatBytes(available)} available"
                )
            }
        }

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(safeName)
            .setDescription("Bunko issue download")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(
                appContext,
                Environment.DIRECTORY_DOWNLOADS,
                archive.name
            )
        when {
            session.apiKey.isNotBlank() -> request.addRequestHeader("x-api-key", session.apiKey)
            session.jwt.isNotBlank() -> request.addRequestHeader("Authorization", "Bearer ${session.jwt}")
        }

        val downloadId = manager.enqueue(request)
        val record = OfflineIssueRecord(
            serverKey = key,
            chapterId = chapterId,
            libraryId = libraryId,
            seriesId = seriesId,
            volumeId = volumeId,
            seriesName = seriesName,
            issueName = issueName,
            downloadId = downloadId,
            archivePath = archive.absolutePath,
            expectedPageCount = expectedPageCount?.coerceAtLeast(0) ?: 0,
            localPage = previous?.localPage ?: 0,
            progressPending = previous?.progressPending ?: false,
            markReadPending = previous?.markReadPending ?: false,
            markUnreadPending = previous?.markUnreadPending ?: false
        )
        put(record)
        record
    }

    suspend fun reconcile(session: KavitaSession, chapterId: Int): OfflineIssueRecord? =
        withContext(Dispatchers.IO) {
            val key = sessionKey(session)
            val record = current(key, chapterId) ?: return@withContext null
            if (record.status == OfflineDownloadStatus.Ready) return@withContext record
            if (record.status == OfflineDownloadStatus.Unsupported) return@withContext record
            if (record.status == OfflineDownloadStatus.Removed) return@withContext record

            val cursor = manager.query(DownloadManager.Query().setFilterById(record.downloadId))
            cursor.use {
                if (!it.moveToFirst()) {
                    remove(session, chapterId)
                    return@withContext null
                }
                val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val downloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    .coerceAtLeast(0)
                val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    .coerceAtLeast(0)
                when (status) {
                    DownloadManager.STATUS_PENDING, DownloadManager.STATUS_PAUSED -> update(record.copy(
                        status = OfflineDownloadStatus.Queued,
                        downloadedBytes = downloaded,
                        totalBytes = total,
                        errorMessage = null
                    ))
                    DownloadManager.STATUS_RUNNING -> update(record.copy(
                        status = OfflineDownloadStatus.Downloading,
                        downloadedBytes = downloaded,
                        totalBytes = total,
                        errorMessage = null
                    ))
                    DownloadManager.STATUS_SUCCESSFUL -> prepareDownloadedFile(
                        record.copy(downloadedBytes = downloaded, totalBytes = total)
                    )
                    DownloadManager.STATUS_FAILED -> {
                        val reason = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                        manager.remove(record.downloadId)
                        removeFiles(record)
                        update(record.copy(
                            status = OfflineDownloadStatus.Failed,
                            downloadedBytes = downloaded,
                            totalBytes = total,
                            errorMessage = downloadFailureMessage(reason)
                        ))
                    }
                    else -> record
                }
            }
        }

    suspend fun localChapter(session: KavitaSession, chapterId: Int): OfflineChapter? =
        withContext(Dispatchers.IO) {
            val reconciled = reconcile(session, chapterId) ?: return@withContext null
            if (reconciled.status != OfflineDownloadStatus.Ready) return@withContext null
            val archive = File(reconciled.archivePath)
            val chapter = runCatching { inspectOfflineFile(archive, reconciled) }
                .onFailure { BunkoLog.w("Could not inspect local offline chapter $chapterId.", it) }
                .getOrNull()
            if (chapter == null || chapter.pages.isEmpty()) {
                update(reconciled.copy(
                    status = OfflineDownloadStatus.Failed,
                    errorMessage = "Offline file is missing or unsupported"
                ))
                return@withContext null
            }
            chapter
        }

    suspend fun ensureLocalCovers(session: KavitaSession) = withContext(Dispatchers.IO) {
        val key = sessionKey(session)
        records()
            .filter { it.serverKey == key && it.status == OfflineDownloadStatus.Ready }
            .forEach { record ->
                if (record.localCoverFile() != null) return@forEach
                runCatching {
                    val archive = File(record.archivePath)
                    val chapter = inspectOfflineFile(archive, record)
                    createLocalCover(record, chapter)
                }.onFailure {
                    BunkoLog.w("Could not create local cover for chapter ${record.chapterId}.", it)
                }.getOrNull()?.let { cover ->
                    update(record.copy(coverPath = cover.absolutePath))
                }
            }
    }

    suspend fun saveLocalProgress(
        session: KavitaSession,
        chapterId: Int,
        page: Int,
        markRead: Boolean = false
    ) {
        val key = sessionKey(session)
        mutateRecord(key, chapterId) { record ->
            record.copy(
                localPage = page.coerceAtLeast(0),
                progressPending = true,
                markReadPending = record.markReadPending || markRead,
                markUnreadPending = if (markRead) false else record.markUnreadPending
            )
        }
    }

    suspend fun markLocalUnread(session: KavitaSession, chapterId: Int) {
        val key = sessionKey(session)
        mutateRecord(key, chapterId) { record ->
            record.copy(
                localPage = 0,
                progressPending = true,
                markReadPending = false,
                markUnreadPending = true
            )
        }
    }

    suspend fun markProgressSynced(
        session: KavitaSession,
        chapterId: Int,
        expectedPage: Int,
        markedRead: Boolean = false,
        markedUnread: Boolean = false
    ) {
        val key = sessionKey(session)
        appContext.offlineIssueDataStore.edit { preferences ->
            val updated = decode(preferences[recordsKey]).mapNotNull { record ->
                if (record.serverKey != key || record.chapterId != chapterId) return@mapNotNull record
                val next = record.copy(
                    progressPending = if (record.localPage == expectedPage) false else record.progressPending,
                    markReadPending = if (markedRead) false else record.markReadPending,
                    markUnreadPending = if (markedUnread) false else record.markUnreadPending
                )
                next.takeUnless {
                    it.status == OfflineDownloadStatus.Removed &&
                        !it.progressPending &&
                        !it.markReadPending &&
                        !it.markUnreadPending
                }
            }
            preferences[recordsKey] = encode(updated)
        }
    }

    suspend fun syncPending(session: KavitaSession, api: KavitaApi) {
        val key = sessionKey(session)
        val pending = records().filter {
            it.serverKey == key &&
                (it.progressPending || it.markReadPending || it.markUnreadPending)
        }
        pending.forEach { record ->
            val progressSaved = runCatching {
                api.saveProgress(
                    ProgressDto(
                        libraryId = record.libraryId,
                        seriesId = record.seriesId,
                        volumeId = record.volumeId,
                        chapterId = record.chapterId,
                        pageNum = record.localPage
                    )
                )
            }.onFailure {
                BunkoLog.w("Could not sync pending progress for chapter ${record.chapterId}.", it)
            }.isSuccess
            val readMarked = !record.markReadPending || runCatching {
                api.markChapterRead(
                    MarkChapterReadDto(
                        seriesId = record.seriesId,
                        chapterId = record.chapterId,
                        generateReadingSession = false
                    )
                )
            }.onFailure {
                BunkoLog.w("Could not sync pending mark-read for chapter ${record.chapterId}.", it)
            }.isSuccess
            val unreadMarked = !record.markUnreadPending || runCatching {
                api.markChaptersUnread(
                    MarkVolumesReadDto(
                        seriesId = record.seriesId,
                        chapterIds = listOf(record.chapterId)
                    )
                )
            }.onFailure {
                BunkoLog.w("Could not sync pending mark-unread for chapter ${record.chapterId}.", it)
            }.isSuccess
            if (
                progressSaved ||
                (record.markReadPending && readMarked) ||
                (record.markUnreadPending && unreadMarked)
            ) {
                markProgressSynced(
                    session = session,
                    chapterId = record.chapterId,
                    expectedPage = record.localPage,
                    markedRead = record.markReadPending && readMarked,
                    markedUnread = record.markUnreadPending && unreadMarked
                )
            }
        }
    }

    suspend fun remove(session: KavitaSession, chapterId: Int) = withContext(Dispatchers.IO) {
        val key = sessionKey(session)
        val record = current(key, chapterId) ?: return@withContext
        if (record.downloadId > 0) manager.remove(record.downloadId)
        removeFiles(record)
        appContext.offlineIssueDataStore.edit { preferences ->
            preferences[recordsKey] = encode(decode(preferences[recordsKey]).mapNotNull {
                if (it.serverKey != key || it.chapterId != chapterId) return@mapNotNull it
                if (it.progressPending || it.markReadPending || it.markUnreadPending) {
                    // Keep a tombstone only while remote progress still needs syncing after the archive has been deleted.
                    it.copy(
                        downloadId = 0,
                        archivePath = "",
                        coverPath = "",
                        pageCount = 0,
                        status = OfflineDownloadStatus.Removed,
                        downloadedBytes = 0,
                        totalBytes = 0,
                        errorMessage = null
                    )
                } else {
                    null
                }
            })
        }
    }

    private suspend fun prepareDownloadedFile(record: OfflineIssueRecord): OfflineIssueRecord {
        return runCatching {
            val archive = File(record.archivePath)
            require(archive.isFile) { "Downloaded file was not found" }
            val chapter = inspectOfflineFile(archive, record)
            require(chapter.pages.isNotEmpty()) { "This downloaded format is not supported for offline reading" }
            require(record.expectedPageCount <= 0 || chapter.pages.size == record.expectedPageCount) {
                "Downloaded file is incomplete (${chapter.pages.size}/${record.expectedPageCount} pages)"
            }
            val coverPath = runCatching {
                // Persist a small local cover so the Downloaded shelf still has artwork when Coil's online cache is gone.
                createLocalCover(record, chapter)?.absolutePath
            }.onFailure {
                BunkoLog.w("Could not create local cover after download for chapter ${record.chapterId}.", it)
            }.getOrNull().orEmpty()
            update(record.copy(
                status = OfflineDownloadStatus.Ready,
                pageCount = chapter.pages.size,
                coverPath = coverPath,
                errorMessage = null
            ))
        }.getOrElse { error ->
            BunkoLog.w("Could not prepare offline issue for chapter ${record.chapterId}.", error)
            update(record.copy(
                status = if (error is UnsupportedOfflineFormatException) {
                    OfflineDownloadStatus.Unsupported
                } else {
                    OfflineDownloadStatus.Failed
                },
                errorMessage = error.message ?: "Could not prepare offline issue"
            ))
        }
    }

    private fun removeFiles(record: OfflineIssueRecord) {
        record.archivePath.takeIf { it.isNotBlank() }?.let(::File)?.delete()
        record.coverPath.takeIf { it.isNotBlank() }?.let(::File)?.delete()
        coverFile(record.serverKey, record.chapterId).delete()
    }

    private fun archiveFile(key: String, chapterId: Int): File {
        val downloads = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: appContext.filesDir
        return File(downloads, "bunko-$key-$chapterId.zip")
    }

    private fun coverFile(key: String, chapterId: Int): File {
        val downloads = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: appContext.filesDir
        return File(downloads, "bunko-$key-$chapterId-cover.jpg")
    }

    private suspend fun createLocalCover(
        record: OfflineIssueRecord,
        chapter: OfflineChapter
    ): File? {
        val page = chapter.pages.getOrNull(chapter.coverPageIndex) ?: chapter.pages.firstOrNull()
            ?: return null
        val source = decodeOfflineCoverPage(page, CoverMaxWidth, CoverMaxHeight) ?: return null
        var output: Bitmap? = null
        val target = coverFile(record.serverKey, record.chapterId)
        val temporary = File(target.parentFile, "${target.name}.tmp")
        return try {
            val scale = minOf(
                1f,
                CoverMaxWidth.toFloat() / source.width.coerceAtLeast(1),
                CoverMaxHeight.toFloat() / source.height.coerceAtLeast(1)
            )
            val width = (source.width * scale).toInt().coerceAtLeast(1)
            val height = (source.height * scale).toInt().coerceAtLeast(1)
            val coverBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            Canvas(coverBitmap).apply {
                drawColor(Color.WHITE)
                drawBitmap(
                    source,
                    null,
                    Rect(0, 0, width, height),
                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                )
            }
            output = coverBitmap
            temporary.parentFile?.mkdirs()
            temporary.outputStream().buffered().use { stream ->
                check(coverBitmap.compress(Bitmap.CompressFormat.JPEG, CoverJpegQuality, stream)) {
                    "Could not encode offline cover"
                }
            }
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
            target.takeIf { it.isFile && it.length() > 0 }
        } finally {
            temporary.delete()
            output?.recycle()
            source.recycle()
        }
    }

    private fun inspectOfflineFile(file: File, record: OfflineIssueRecord): OfflineChapter {
        require(file.isFile) { "Offline file is missing" }
        val ext = file.extension.lowercase().trim()
        val signature = file.inputStream().buffered().use { input ->
            ByteArray(4).also { input.read(it) }
        }
        return when {
            signature.contentEquals(pdfSignature) || ext == "pdf" -> inspectPdf(file, record)
            ext == "epub" -> inspectEpub(file, record)
            else -> inspectArchive(file, record)
        }
    }

    private fun inspectArchive(file: File, record: OfflineIssueRecord): OfflineChapter {
        val entries = kotlinx.coroutines.runBlocking {
            com.bunko.reader.engine.archive.UniversalArchiveReader.listImageEntries(file)
        }
        val rawDims = kotlinx.coroutines.runBlocking {
            com.bunko.reader.engine.archive.UniversalArchiveReader.extractPageDimensions(file, entries, sampleLimit = 15)
        }
        val dimensions = rawDims.mapValues { (index, dim) ->
            FileDimensionDto(
                width = dim.first,
                height = dim.second,
                pageNumber = index,
                fileName = entries.getOrNull(index) ?: "page-$index",
                isWide = dim.first > dim.second
            )
        }
        val pages = entries.mapIndexed { index, entryName ->
            OfflinePage.ArchiveEntry(file.absolutePath, entryName, index)
        }
        return OfflineChapter(record, pages, dimensions, 0)
    }

    private fun inspectEpub(file: File, record: OfflineIssueRecord): OfflineChapter {
        val parsed = kotlinx.coroutines.runBlocking {
            com.bunko.reader.engine.epub.EpubPackageReader.parseEpub(appContext, file)
        }
        val pages = parsed.spines.mapIndexed { index, spine ->
            OfflinePage.ArchiveEntry(file.absolutePath, spine.href.ifEmpty { "spine_$index" }, index)
        }
        return OfflineChapter(record, pages, emptyMap(), 0)
    }

    private fun comicInfoFrontCoverIndex(zip: ZipFile): Int? {
        val comicInfo = zip.entries().asSequence().firstOrNull { entry ->
            !entry.isDirectory && entry.name.substringAfterLast('/')
                .equals("ComicInfo.xml", ignoreCase = true)
        } ?: return null
        return runCatching {
            val parser = Xml.newPullParser()
            zip.getInputStream(comicInfo).use { input ->
                parser.setInput(input, "UTF-8")
                while (parser.eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    if (
                        parser.eventType == org.xmlpull.v1.XmlPullParser.START_TAG &&
                        parser.name.equals("Page", ignoreCase = true) &&
                        parser.getAttributeValue(null, "Type")
                            ?.equals("FrontCover", ignoreCase = true) == true
                    ) {
                        return@runCatching parser.getAttributeValue(null, "Image")?.toIntOrNull()
                    }
                    parser.next()
                }
                null
            }
        }.getOrNull()
    }

    private fun inspectPdf(file: File, record: OfflineIssueRecord): OfflineChapter {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                val pages = ArrayList<OfflinePage>(renderer.pageCount)
                val dimensions = HashMap<Int, FileDimensionDto>(renderer.pageCount)
                repeat(renderer.pageCount) { index ->
                    renderer.openPage(index).use { page ->
                        pages += OfflinePage.PdfPage(
                            pdfPath = file.absolutePath,
                            index = index,
                            width = page.width,
                            height = page.height
                        )
                        dimensions[index] = FileDimensionDto(
                            width = page.width,
                            height = page.height,
                            pageNumber = index,
                            fileName = "page-$index.pdf",
                            isWide = page.width > page.height
                        )
                    }
                }
                return OfflineChapter(record, pages, dimensions)
            }
        }
    }

    private suspend fun current(key: String, chapterId: Int): OfflineIssueRecord? =
        records().firstOrNull { it.serverKey == key && it.chapterId == chapterId }

    private suspend fun records(): List<OfflineIssueRecord> {
        val preferences = appContext.offlineIssueDataStore.data.first()
        return decode(preferences[recordsKey])
    }

    private suspend fun put(record: OfflineIssueRecord) {
        appContext.offlineIssueDataStore.edit { preferences ->
            val records = decode(preferences[recordsKey])
            val existing = records.firstOrNull {
                it.serverKey == record.serverKey && it.chapterId == record.chapterId
            }
            val latest = existing?.let {
                record.copy(
                    localPage = it.localPage,
                    progressPending = it.progressPending,
                    markReadPending = it.markReadPending,
                    markUnreadPending = it.markUnreadPending
                )
            } ?: record
            preferences[recordsKey] = encode(
                records.filterNot {
                    it.serverKey == record.serverKey && it.chapterId == record.chapterId
                } + latest
            )
        }
    }

    private suspend fun mutateRecord(
        key: String,
        chapterId: Int,
        transform: (OfflineIssueRecord) -> OfflineIssueRecord
    ) {
        appContext.offlineIssueDataStore.edit { preferences ->
            preferences[recordsKey] = encode(decode(preferences[recordsKey]).map { record ->
                if (record.serverKey == key && record.chapterId == chapterId) {
                    transform(record)
                } else {
                    record
                }
            })
        }
    }

    private suspend fun update(record: OfflineIssueRecord): OfflineIssueRecord {
        put(record)
        return record
    }

    private fun decode(value: String?): List<OfflineIssueRecord> {
        if (value.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(OfflineIssueRecord.serializer()), value)
        }.getOrDefault(emptyList())
    }

    private fun encode(records: List<OfflineIssueRecord>): String =
        json.encodeToString(ListSerializer(OfflineIssueRecord.serializer()), records)

    companion object {
        private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "avif")
        private val pdfSignature = byteArrayOf(0x25, 0x50, 0x44, 0x46)
        private const val DownloadStorageReserveBytes = 128L * 1024L * 1024L
        private const val CoverMaxWidth = 600
        private const val CoverMaxHeight = 900
        private const val CoverJpegQuality = 88
        private const val LegacyUnavailableDownloadMessage = "Download is no longer available"

        fun sessionKey(session: KavitaSession): String {
            val identity = session.username.trim().lowercase().ifBlank {
                MessageDigest.getInstance("SHA-256")
                    .digest(session.apiKey.toByteArray())
                    .take(8)
                    .joinToString("") { "%02x".format(it) }
            }
            val normalized = "${normalizeKavitaBaseUrl(session.baseUrl).lowercase()}|$identity"
            return MessageDigest.getInstance("SHA-256")
                .digest(normalized.toByteArray())
                .take(8)
                .joinToString("") { "%02x".format(it) }
        }

        internal fun hasEnoughOfflineStorage(
            expectedBytes: Long,
            availableBytes: Long,
            reserveBytes: Long = DownloadStorageReserveBytes
        ): Boolean = expectedBytes >= 0 &&
            reserveBytes >= 0 &&
            availableBytes >= reserveBytes &&
            expectedBytes <= availableBytes - reserveBytes

    }
}

private fun downloadFailureMessage(reason: Int): String = when (reason) {
    DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Not enough storage space"
    DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Download storage is unavailable"
    DownloadManager.ERROR_CANNOT_RESUME -> "Download could not be resumed"
    DownloadManager.ERROR_HTTP_DATA_ERROR -> "The server connection was interrupted"
    DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "The server rejected the download"
    else -> "Download failed ($reason)"
}

private fun formatBytes(bytes: Long): String {
    val gib = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
    return if (gib >= 1.0) {
        "%.1f GB".format(gib)
    } else {
        "%.0f MB".format(bytes.toDouble() / (1024.0 * 1024.0))
    }
}

internal fun compareNaturalFileNames(left: String, right: String): Int {
    val leftParts = naturalPart.findAll(left.lowercase()).map { it.value }.toList()
    val rightParts = naturalPart.findAll(right.lowercase()).map { it.value }.toList()
    for (index in 0 until minOf(leftParts.size, rightParts.size)) {
        val a = leftParts[index]
        val b = rightParts[index]
        val comparison = if (a.firstOrNull()?.isDigit() == true && b.firstOrNull()?.isDigit() == true) {
            val normalizedA = a.trimStart('0').ifEmpty { "0" }
            val normalizedB = b.trimStart('0').ifEmpty { "0" }
            normalizedA.length.compareTo(normalizedB.length)
                .takeIf { it != 0 }
                ?: normalizedA.compareTo(normalizedB)
        } else {
            a.compareTo(b)
        }
        if (comparison != 0) return comparison
    }
    return leftParts.size.compareTo(rightParts.size)
}

private val naturalPart = Regex("\\d+|\\D+")

private fun decodeOfflineCoverPage(
    page: OfflinePage,
    targetWidth: Int,
    targetHeight: Int
): Bitmap? = when (page) {
    is OfflinePage.ArchiveEntry -> decodeArchivePage(page, targetWidth, targetHeight)
    is OfflinePage.PdfPage -> decodePdfCoverPage(page, targetWidth, targetHeight)
}

private val archiveCacheMaxBytes = (Runtime.getRuntime().maxMemory() / 8).toInt().coerceIn(32 * 1024 * 1024, 128 * 1024 * 1024)
private val archivePageCache = object : android.util.LruCache<String, Bitmap>(archiveCacheMaxBytes) {
    override fun sizeOf(key: String, value: Bitmap): Int {
        val bytes = value.allocationByteCount
        return if (bytes > 0) bytes else (value.rowBytes * value.height).coerceAtLeast(1)
    }
}

private fun decodeArchivePage(
    page: OfflinePage.ArchiveEntry,
    targetWidth: Int,
    targetHeight: Int
): Bitmap? {
    val key = "${page.archivePath}:${page.entryName}"
    synchronized(archivePageCache) {
        val cached = archivePageCache.get(key)
        if (cached != null && !cached.isRecycled) return cached
    }

    val archive = File(page.archivePath)
    if (!archive.exists()) return null
    val bytes = kotlinx.coroutines.runBlocking {
        com.bunko.reader.engine.archive.UniversalArchiveReader.extractEntryBytes(archive, page.entryName)
    } ?: return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val options = BitmapFactory.Options().apply {
        inSampleSize = calculateInSampleSize(
            width = bounds.outWidth,
            height = bounds.outHeight,
            targetWidth = targetWidth,
            targetHeight = targetHeight
        )
        inPreferredConfig = Bitmap.Config.RGB_565
    }
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    if (decoded != null) {
        synchronized(archivePageCache) {
            archivePageCache.put(key, decoded)
        }
    }
    return decoded
}

private suspend fun decodePdfPage(
    page: OfflinePage.PdfPage,
    targetWidth: Int,
    targetHeight: Int
): Bitmap? {
    val file = File(page.pdfPath)
    if (!file.isFile) return null
    return com.bunko.reader.engine.pdf.PdfDocumentEngine.renderPageBitmap(file, page.index, targetWidth, targetHeight)
}

private fun decodePdfCoverPage(
    page: OfflinePage.PdfPage,
    targetWidth: Int,
    targetHeight: Int
): Bitmap? {
    val file = File(page.pdfPath)
    if (!file.isFile) return null
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            if (page.index !in 0 until renderer.pageCount) return null
            renderer.openPage(page.index).use { pdfPage ->
                val scale = minOf(
                    targetWidth.coerceAtLeast(1).toFloat() / pdfPage.width.coerceAtLeast(1),
                    targetHeight.coerceAtLeast(1).toFloat() / pdfPage.height.coerceAtLeast(1)
                ).coerceAtLeast(0.01f)
                val bitmap = Bitmap.createBitmap(
                    (pdfPage.width * scale).toInt().coerceAtLeast(1),
                    (pdfPage.height * scale).toInt().coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                bitmap.eraseColor(Color.WHITE)
                pdfPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return bitmap
            }
        }
    }
}

private fun calculateInSampleSize(
    width: Int,
    height: Int,
    targetWidth: Int,
    targetHeight: Int
): Int {
    if (targetWidth <= 0 || targetHeight <= 0) return 1
    val requestedWidth = (targetWidth.coerceAtLeast(1) * 2).coerceIn(1080, 4096)
    val requestedHeight = (targetHeight.coerceAtLeast(1) * 2).coerceIn(1920, 4096)
    var sample = 1
    while (width / (sample * 2) >= requestedWidth && height / (sample * 2) >= requestedHeight) {
        sample *= 2
    }
    return sample
}
