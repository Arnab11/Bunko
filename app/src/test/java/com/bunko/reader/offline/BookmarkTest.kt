package com.bunko.reader.offline

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class BookmarkTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    @Test
    fun testBookmarkSerializationRoundtrip() {
        val original = listOf(
            ReaderBookmark(
                id = "bm-1",
                bookId = "book-123",
                bookTitle = "War and Peace",
                chapterName = "Chapter 1",
                chapterId = 10,
                page = 5,
                pageCount = 100,
                progressPercent = 6.0f,
                previewText = "Well, Prince, so Genoa and Lucca are now just family estates...",
                timestamp = 1600000000000L
            ),
            ReaderBookmark(
                id = "bm-2",
                bookId = "book-456",
                bookTitle = "Comic Vol 1",
                chapterName = null,
                chapterId = 0,
                page = 12,
                pageCount = 30,
                progressPercent = 43.33f,
                previewText = "Page 13 of 30",
                timestamp = 1600000005000L
            )
        )

        val serialized = json.encodeToString(ListSerializer(ReaderBookmark.serializer()), original)
        val decoded = json.decodeFromString(ListSerializer(ReaderBookmark.serializer()), serialized)

        assertEquals(2, decoded.size)
        assertEquals("bm-1", decoded[0].id)
        assertEquals("War and Peace", decoded[0].bookTitle)
        assertEquals("Chapter 1", decoded[0].chapterName)
        assertEquals(5, decoded[0].page)
        assertEquals("Well, Prince, so Genoa and Lucca are now just family estates...", decoded[0].previewText)
        assertEquals(6.0f, decoded[0].progressPercent, 0.001f)

        assertEquals("bm-2", decoded[1].id)
        assertEquals("Comic Vol 1", decoded[1].bookTitle)
        assertEquals(12, decoded[1].page)
    }

    @Test
    fun testBookmarkFilteringAndSorting() {
        val bookmarks = listOf(
            ReaderBookmark(id = "1", bookId = "b1", bookTitle = "T", page = 10),
            ReaderBookmark(id = "2", bookId = "b2", bookTitle = "T", page = 5),
            ReaderBookmark(id = "3", bookId = "b1", bookTitle = "T", page = 2),
            ReaderBookmark(id = "4", bookId = "b1", bookTitle = "T", page = 7)
        )

        val forB1 = bookmarks.filter { it.bookId == "b1" }.sortedBy { it.page }
        assertEquals(3, forB1.size)
        assertEquals(listOf(2, 7, 10), forB1.map { it.page })
    }
}
