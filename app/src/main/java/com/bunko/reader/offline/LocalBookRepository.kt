package com.bunko.reader.offline

import android.content.Context
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
    private val json = Json { ignoreUnknownKeys = true }

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

    val folderFlow: Flow<Pair<String?, String?>> = appContext.localBooksDataStore.data.map { prefs ->
        Pair(prefs[KEY_FOLDER_URI], prefs[KEY_FOLDER_NAME])
    }

    val booksFlow: Flow<List<LocalBook>> = appContext.localBooksDataStore.data.map { prefs ->
        decodeBooks(prefs[KEY_BOOKS_JSON])
    }

    suspend fun getSavedBooks(): List<LocalBook> {
        val prefs = appContext.localBooksDataStore.data.first()
        return decodeBooks(prefs[KEY_BOOKS_JSON])
    }

    suspend fun getBook(bookId: String): LocalBook? {
        return getSavedBooks().firstOrNull { it.id == bookId }
    }

    suspend fun setDefaultFolder(uri: Uri, displayName: String) {
        appContext.localBooksDataStore.edit { prefs ->
            prefs[KEY_FOLDER_URI] = uri.toString()
            prefs[KEY_FOLDER_NAME] = displayName
        }
        rescan()
    }

    suspend fun clearDefaultFolder() {
        appContext.localBooksDataStore.edit { prefs ->
            prefs.remove(KEY_FOLDER_URI)
            prefs.remove(KEY_FOLDER_NAME)
            prefs.remove(KEY_BOOKS_JSON)
        }
    }

    fun rescan() {
        if (_isScanning.value) return
        scope.launch {
            _isScanning.value = true
            try {
                val prefs = appContext.localBooksDataStore.data.first()
                val uriString = prefs[KEY_FOLDER_URI]
                if (uriString.isNullOrBlank()) {
                    _isScanning.value = false
                    return@launch
                }

                val treeUri = Uri.parse(uriString)
                val scannedBooks = LocalBookScanner.scanTree(appContext, treeUri)
                val existingBooksMap = decodeBooks(prefs[KEY_BOOKS_JSON]).associateBy { it.id }

                val merged = scannedBooks.map { scanned ->
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

    /**
     * Prepares a local File in app cache directory for random-access reading if needed.
     * For large files, if already cached, returns existing file.
     */
    suspend fun prepareBookFile(book: LocalBook): File = withContext(Dispatchers.IO) {
        val cacheFolder = File(appContext.cacheDir, "active_books").apply { mkdirs() }
        val targetFile = File(cacheFolder, "${book.id}.${book.extension}")

        if (targetFile.isFile && targetFile.length() > 0 && targetFile.length() == book.sizeBytes) {
            return@withContext targetFile
        }

        val uri = Uri.parse(book.uriString)
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            val tempFile = File(cacheFolder, "${book.id}.${book.extension}.tmp")
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
            if (tempFile.renameTo(targetFile) || (targetFile.delete() && tempFile.renameTo(targetFile))) {
                targetFile
            } else {
                tempFile
            }
        } ?: targetFile
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
