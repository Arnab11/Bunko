package com.bunko.reader.offline

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bunko.reader.BunkoLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.bookmarksDataStore by preferencesDataStore("reader_bookmarks_prefs")

class BookmarkRepository(context: Context) {
    private val appContext = context.applicationContext
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    private val KEY_BOOKMARKS_JSON = stringPreferencesKey("reader_bookmarks_catalog")

    val bookmarksFlow: Flow<List<ReaderBookmark>> = appContext.bookmarksDataStore.data.map { prefs ->
        decodeBookmarks(prefs[KEY_BOOKMARKS_JSON])
    }

    fun getBookmarksForBook(bookId: String): Flow<List<ReaderBookmark>> {
        return bookmarksFlow.map { list ->
            list.filter { it.bookId == bookId }.sortedBy { it.page }
        }
    }

    suspend fun getAllBookmarks(): List<ReaderBookmark> {
        val prefs = appContext.bookmarksDataStore.data.first()
        return decodeBookmarks(prefs[KEY_BOOKMARKS_JSON])
    }

    suspend fun isPageBookmarked(bookId: String, chapterId: Int, page: Int): Boolean {
        val current = getAllBookmarks()
        return current.any { it.bookId == bookId && (chapterId == 0 || it.chapterId == chapterId) && it.page == page }
    }

    suspend fun addBookmark(bookmark: ReaderBookmark) {
        appContext.bookmarksDataStore.edit { prefs ->
            val current = decodeBookmarks(prefs[KEY_BOOKMARKS_JSON]).toMutableList()
            // Remove existing duplicate on same page if any
            current.removeAll { it.bookId == bookmark.bookId && it.chapterId == bookmark.chapterId && it.page == bookmark.page }
            current.add(0, bookmark)
            prefs[KEY_BOOKMARKS_JSON] = json.encodeToString(ListSerializer(ReaderBookmark.serializer()), current)
        }
    }

    suspend fun removeBookmark(id: String) {
        appContext.bookmarksDataStore.edit { prefs ->
            val current = decodeBookmarks(prefs[KEY_BOOKMARKS_JSON]).toMutableList()
            current.removeAll { it.id == id }
            prefs[KEY_BOOKMARKS_JSON] = json.encodeToString(ListSerializer(ReaderBookmark.serializer()), current)
        }
    }

    suspend fun toggleBookmark(
        bookId: String,
        bookTitle: String,
        chapterName: String?,
        chapterId: Int,
        page: Int,
        pageCount: Int,
        previewText: String?
    ): Boolean {
        var added = false
        appContext.bookmarksDataStore.edit { prefs ->
            val current = decodeBookmarks(prefs[KEY_BOOKMARKS_JSON]).toMutableList()
            val existing = current.indexOfFirst {
                it.bookId == bookId && (chapterId == 0 || it.chapterId == chapterId) && it.page == page
            }
            if (existing >= 0) {
                current.removeAt(existing)
                added = false
            } else {
                val progress = if (pageCount > 0) ((page + 1).toFloat() / pageCount.toFloat()) * 100f else 0f
                val newBookmark = ReaderBookmark(
                    id = UUID.randomUUID().toString(),
                    bookId = bookId,
                    bookTitle = bookTitle,
                    chapterName = chapterName,
                    chapterId = chapterId,
                    page = page,
                    pageCount = pageCount,
                    progressPercent = progress,
                    previewText = previewText?.take(200),
                    timestamp = System.currentTimeMillis()
                )
                current.add(0, newBookmark)
                added = true
            }
            prefs[KEY_BOOKMARKS_JSON] = json.encodeToString(ListSerializer(ReaderBookmark.serializer()), current)
        }
        return added
    }

    private fun decodeBookmarks(raw: String?): List<ReaderBookmark> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString(ListSerializer(ReaderBookmark.serializer()), raw)
        } catch (t: Throwable) {
            BunkoLog.w("Failed to decode bookmarks JSON", t)
            emptyList()
        }
    }
}
