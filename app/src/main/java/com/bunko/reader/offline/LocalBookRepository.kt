package com.bunko.reader.offline

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bunko.reader.BunkoLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

private val Context.localBooksDataStore by preferencesDataStore("local_books_prefs")

class LocalBookRepository(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    private val KEY_FOLDERS_JSON = stringPreferencesKey("local_library_folders_json")
    private val KEY_FOLDER_URI = stringPreferencesKey("default_folder_uri")
    private val KEY_FOLDER_NAME = stringPreferencesKey("default_folder_name")
    private val KEY_BOOKS_JSON = stringPreferencesKey("local_books_catalog")
    private val KEY_STARTUP_COMPLETED = booleanPreferencesKey("startup_completed")
    private val KEY_ACTIVE_MODE = stringPreferencesKey("active_library_mode")

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    val startupCompletedFlow: Flow<Boolean> = appContext.localBooksDataStore.data.map { prefs ->
        prefs[KEY_STARTUP_COMPLETED] ?: false
    }

    val activeModeFlow: Flow<String> = appContext.localBooksDataStore.data.map { prefs ->
        prefs[KEY_ACTIVE_MODE] ?: "offline"
    }

    suspend fun setStartupCompleted(completed: Boolean = true) {
        appContext.localBooksDataStore.edit { prefs ->
            prefs[KEY_STARTUP_COMPLETED] = completed
        }
    }

    suspend fun setActiveMode(mode: String) {
        appContext.localBooksDataStore.edit { prefs ->
            prefs[KEY_ACTIVE_MODE] = mode
        }
    }

    val foldersFlow: Flow<List<LocalFolder>> = appContext.localBooksDataStore.data.map { prefs ->
        decodeFolders(prefs[KEY_FOLDERS_JSON], prefs[KEY_FOLDER_URI], prefs[KEY_FOLDER_NAME])
    }

    val folderFlow: Flow<Pair<String?, String?>> = foldersFlow.map { folders ->
        if (folders.isEmpty()) {
            Pair(null, null)
        } else if (folders.size == 1) {
            Pair(folders.first().uriString, folders.first().name)
        } else {
            Pair(folders.first().uriString, "${folders.size} Folders")
        }
    }

    val booksFlow: Flow<List<LocalBook>> = appContext.localBooksDataStore.data.map { prefs ->
        decodeBooks(prefs[KEY_BOOKS_JSON])
    }

    suspend fun getSavedBooks(): List<LocalBook> {
        val prefs = appContext.localBooksDataStore.data.first()
        return decodeBooks(prefs[KEY_BOOKS_JSON])
    }

    suspend fun getSavedFolders(): List<LocalFolder> {
        val prefs = appContext.localBooksDataStore.data.first()
        return decodeFolders(prefs[KEY_FOLDERS_JSON], prefs[KEY_FOLDER_URI], prefs[KEY_FOLDER_NAME])
    }

    suspend fun getBook(bookId: String): LocalBook? {
        return getSavedBooks().firstOrNull { it.id == bookId }
    }

    suspend fun getOrCreateBookForUri(uri: Uri): LocalBook? = withContext(Dispatchers.IO) {
        val uriStr = uri.toString()
        val existing = getSavedBooks().firstOrNull { it.uriString == uriStr || it.id == LocalBookScanner.hashUri(uriStr) }
        if (existing != null) {
            if (!existing.hasCover) {
                scope.launch {
                    val coverPath = LocalBookScanner.generateCover(appContext, existing)
                    if (!coverPath.isNullOrBlank()) {
                        updateBookCover(existing.id, coverPath)
                    }
                }
            }
            return@withContext existing
        }

        val created = LocalBookScanner.createBookFromSingleUri(appContext, uri) ?: return@withContext null
        appContext.localBooksDataStore.edit { prefs ->
            val currentBooks = decodeBooks(prefs[KEY_BOOKS_JSON]).toMutableList()
            val idx = currentBooks.indexOfFirst { it.id == created.id || it.uriString == created.uriString }
            if (idx >= 0) {
                val existingItem = currentBooks[idx]
                currentBooks[idx] = created.copy(
                    pageCount = if (existingItem.pageCount > 0) existingItem.pageCount else created.pageCount,
                    lastReadPage = existingItem.lastReadPage,
                    isCompleted = existingItem.isCompleted,
                    coverPath = if (existingItem.hasCover) existingItem.coverPath else created.coverPath
                )
            } else {
                currentBooks.add(0, created)
            }
            prefs[KEY_BOOKS_JSON] = encodeBooks(currentBooks)
        }

        if (!created.hasCover) {
            scope.launch {
                val coverPath = LocalBookScanner.generateCover(appContext, created)
                if (!coverPath.isNullOrBlank()) {
                    updateBookCover(created.id, coverPath)
                }
            }
        }

        created
    }

    suspend fun addFolder(uri: Uri, displayName: String) {
        val uriStr = uri.toString()
        appContext.localBooksDataStore.edit { prefs ->
            val existingFolders = decodeFolders(prefs[KEY_FOLDERS_JSON], prefs[KEY_FOLDER_URI], prefs[KEY_FOLDER_NAME]).toMutableList()
            if (existingFolders.none { it.uriString == uriStr }) {
                existingFolders.add(LocalFolder(uriString = uriStr, name = displayName))
            }
            prefs[KEY_FOLDERS_JSON] = encodeFolders(existingFolders)
            // Update legacy keys for safety
            prefs[KEY_FOLDER_URI] = existingFolders.first().uriString
            prefs[KEY_FOLDER_NAME] = existingFolders.first().name
        }
        rescan()
    }

    suspend fun removeFolder(uriString: String) {
        appContext.localBooksDataStore.edit { prefs ->
            val existingFolders = decodeFolders(prefs[KEY_FOLDERS_JSON], prefs[KEY_FOLDER_URI], prefs[KEY_FOLDER_NAME]).filter { it.uriString != uriString }
            prefs[KEY_FOLDERS_JSON] = encodeFolders(existingFolders)
            if (existingFolders.isNotEmpty()) {
                prefs[KEY_FOLDER_URI] = existingFolders.first().uriString
                prefs[KEY_FOLDER_NAME] = existingFolders.first().name
            } else {
                prefs.remove(KEY_FOLDER_URI)
                prefs.remove(KEY_FOLDER_NAME)
            }

            // Remove books belonging to this folder, but keep external files intact
            val books = decodeBooks(prefs[KEY_BOOKS_JSON]).filter { it.isExternalFile || it.folderUriString != uriString }
            prefs[KEY_BOOKS_JSON] = encodeBooks(books)
        }

        runCatching {
            val uri = Uri.parse(uriString)
            appContext.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
    }

    suspend fun setDefaultFolder(uri: Uri, displayName: String) {
        addFolder(uri, displayName)
    }

    suspend fun clearDefaultFolder() {
        clearAllFolders()
    }

    suspend fun clearAllFolders() {
        appContext.localBooksDataStore.edit { prefs ->
            prefs.remove(KEY_FOLDERS_JSON)
            prefs.remove(KEY_FOLDER_URI)
            prefs.remove(KEY_FOLDER_NAME)
            // Keep external files intact
            val externalBooks = decodeBooks(prefs[KEY_BOOKS_JSON]).filter { it.isExternalFile }
            if (externalBooks.isNotEmpty()) {
                prefs[KEY_BOOKS_JSON] = encodeBooks(externalBooks)
            } else {
                prefs.remove(KEY_BOOKS_JSON)
            }
        }
    }

    fun rescan() {
        if (_isScanning.value) return
        scope.launch {
            _isScanning.value = true
            try {
                val prefs = appContext.localBooksDataStore.data.first()
                val folders = decodeFolders(prefs[KEY_FOLDERS_JSON], prefs[KEY_FOLDER_URI], prefs[KEY_FOLDER_NAME])
                val currentAllBooks = decodeBooks(prefs[KEY_BOOKS_JSON])
                val externalBooks = currentAllBooks.filter { it.isExternalFile }
                if (folders.isEmpty()) {
                    if (externalBooks.isNotEmpty()) {
                        appContext.localBooksDataStore.edit { editPrefs ->
                            editPrefs[KEY_BOOKS_JSON] = encodeBooks(externalBooks)
                        }
                    }
                    _isScanning.value = false
                    return@launch
                }

                val allScanned = mutableListOf<LocalBook>()
                for (folder in folders) {
                    try {
                        val treeUri = Uri.parse(folder.uriString)
                        val scanned = LocalBookScanner.scanTree(appContext, treeUri, folder.name)
                        allScanned.addAll(scanned)
                    } catch (t: Throwable) {
                        BunkoLog.w("Failed to scan folder ${folder.name}", t)
                    }
                }

                val existingBooksMap = currentAllBooks.associateBy { it.id }

                val mergedScanned = allScanned.map { scanned ->
                    val existing = existingBooksMap[scanned.id]
                    if (existing != null) {
                        scanned.copy(
                            pageCount = if (scanned.pageCount > 0) scanned.pageCount else existing.pageCount,
                            lastReadPage = existing.lastReadPage,
                            isCompleted = existing.isCompleted,
                            coverPath = if (existing.hasCover) existing.coverPath else ""
                        )
                    } else {
                        scanned
                    }
                }

                val merged = mergedScanned + externalBooks

                appContext.localBooksDataStore.edit { editPrefs ->
                    editPrefs[KEY_BOOKS_JSON] = encodeBooks(merged)
                }

                // Asynchronously generate covers for items that don't have one yet
                launch {
                    for (book in merged) {
                        if (!book.hasCover) {
                            val coverPath = LocalBookScanner.generateCover(appContext, book)
                            if (!coverPath.isNullOrBlank()) {
                                updateBookCover(book.id, coverPath)
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                BunkoLog.w("Failed to scan local books folder", t)
            } finally {
                _isScanning.value = false
            }
        }
    }

    suspend fun saveProgress(bookId: String, page: Int, totalPages: Int = 0, isCompleted: Boolean = false) {
        appContext.localBooksDataStore.edit { prefs ->
            val books = decodeBooks(prefs[KEY_BOOKS_JSON])
            val updated = books.map { book ->
                if (book.id == bookId) {
                    book.copy(
                        lastReadPage = page.coerceAtLeast(0),
                        pageCount = if (totalPages > 0) totalPages else book.pageCount,
                        isCompleted = isCompleted || (totalPages > 0 && page >= totalPages - 1)
                    )
                } else {
                    book
                }
            }
            prefs[KEY_BOOKS_JSON] = encodeBooks(updated)
        }
    }

    suspend fun markCompleted(bookId: String, isCompleted: Boolean) {
        appContext.localBooksDataStore.edit { prefs ->
            val books = decodeBooks(prefs[KEY_BOOKS_JSON])
            val updated = books.map { book ->
                if (book.id == bookId) {
                    book.copy(
                        isCompleted = isCompleted,
                        lastReadPage = if (isCompleted) (book.pageCount - 1).coerceAtLeast(0) else 0
                    )
                } else {
                    book
                }
            }
            prefs[KEY_BOOKS_JSON] = encodeBooks(updated)
        }
    }

    private suspend fun updateBookCover(bookId: String, coverPath: String) {
        appContext.localBooksDataStore.edit { prefs ->
            val books = decodeBooks(prefs[KEY_BOOKS_JSON])
            val updated = books.map { book ->
                if (book.id == bookId) book.copy(coverPath = coverPath) else book
            }
            prefs[KEY_BOOKS_JSON] = encodeBooks(updated)
        }
    }

    suspend fun prepareBookFile(book: LocalBook): File = withContext(Dispatchers.IO) {
        val cacheFolder = File(appContext.cacheDir, "active_books").apply { mkdirs() }
        val targetFile = File(cacheFolder, "${book.id}.${book.extension}")

        if (targetFile.isFile && targetFile.length() > 0 && (book.sizeBytes <= 0L || targetFile.length() == book.sizeBytes)) {
            return@withContext targetFile
        }

        val uri = Uri.parse(book.uriString)
        try {
            val input = if (uri.scheme == "file" && uri.path != null) {
                val directFile = File(uri.path!!)
                if (directFile.isFile && directFile.canRead()) {
                    directFile.inputStream()
                } else {
                    appContext.contentResolver.openInputStream(uri)
                }
            } else {
                appContext.contentResolver.openInputStream(uri)
            }

            input?.use { stream ->
                val tempFile = File(cacheFolder, "${book.id}.${book.extension}.tmp")
                FileOutputStream(tempFile).use { output ->
                    stream.copyTo(output)
                }
                if (tempFile.renameTo(targetFile) || (targetFile.delete() && tempFile.renameTo(targetFile))) {
                    targetFile
                } else {
                    tempFile
                }
            } ?: targetFile
        } catch (t: Throwable) {
            BunkoLog.w("Failed to prepare book file for ${book.uriString}", t)
            targetFile
        }
    }

    private fun decodeFolders(jsonStr: String?, legacyUri: String?, legacyName: String?): List<LocalFolder> {
        if (!jsonStr.isNullOrBlank()) {
            val parsed = runCatching {
                json.decodeFromString(ListSerializer(LocalFolder.serializer()), jsonStr)
            }.getOrDefault(emptyList())
            if (parsed.isNotEmpty()) return parsed
        }
        if (!legacyUri.isNullOrBlank()) {
            return listOf(LocalFolder(uriString = legacyUri, name = legacyName ?: "eBooks & Comics"))
        }
        return emptyList()
    }

    private fun encodeFolders(folders: List<LocalFolder>): String {
        return json.encodeToString(ListSerializer(LocalFolder.serializer()), folders)
    }

    private fun decodeBooks(jsonStr: String?): List<LocalBook> {
        if (jsonStr.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(LocalBook.serializer()), jsonStr)
        }.getOrDefault(emptyList())
    }

    private fun encodeBooks(books: List<LocalBook>): String {
        return json.encodeToString(ListSerializer(LocalBook.serializer()), books)
    }
}
