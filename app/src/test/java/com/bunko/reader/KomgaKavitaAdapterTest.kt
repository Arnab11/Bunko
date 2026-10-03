package com.bunko.reader

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KomgaKavitaAdapterTest {

    private class FakeKomgaApi : KomgaApi {
        val updatedProgress = mutableMapOf<String, KomgaReadProgressUpdateDto>()
        val deletedProgress = mutableListOf<String>()
        var booksQueriedWithReadStatus: List<String>? = null
        var booksSortQueried: String? = null

        val sampleBook = KomgaBookDto(
            id = "book-1",
            seriesId = "series-1",
            libraryId = "lib-1",
            name = "Chapter 1",
            number = 1,
            media = KomgaMediaDto(pagesCount = 20),
            readProgress = KomgaReadProgressDto(page = 5, completed = false)
        )

        override suspend fun currentUser(): KomgaUserDto = KomgaUserDto()
        override suspend fun libraries(): List<KomgaLibraryDto> = listOf(KomgaLibraryDto(id = "lib-1", name = "Manga"))
        override suspend fun library(libraryId: String): KomgaLibraryDto = KomgaLibraryDto(id = libraryId, name = "Manga")
        override suspend fun seriesList(body: KomgaSeriesSearchDto, page: Int?, size: Int?, sort: String?, unpaged: Boolean?): KomgaPageDto<KomgaSeriesDto> = KomgaPageDto()
        override suspend fun allSeriesLegacy(libraryIds: List<String>?, page: Int?, size: Int?, unpaged: Boolean?): KomgaPageDto<KomgaSeriesDto> = KomgaPageDto()
        override suspend fun newSeries(libraryIds: List<String>?, page: Int?, size: Int?, unpaged: Boolean?): KomgaPageDto<KomgaSeriesDto> = KomgaPageDto()
        override suspend fun updatedSeries(libraryIds: List<String>?, page: Int?, size: Int?, unpaged: Boolean?): KomgaPageDto<KomgaSeriesDto> = KomgaPageDto()
        override suspend fun series(seriesId: String): KomgaSeriesDto = KomgaSeriesDto(id = seriesId, libraryId = "lib-1", name = "Test Series")
        override suspend fun seriesBooks(seriesId: String, unpaged: Boolean?, page: Int?, size: Int?, sort: String?): KomgaPageDto<KomgaBookDto> =
            KomgaPageDto(content = listOf(sampleBook))
        override suspend fun markSeriesRead(seriesId: String) {}
        override suspend fun markSeriesUnread(seriesId: String) {}
        override suspend fun analyzeSeries(seriesId: String) {}
        override suspend fun refreshSeriesMetadata(seriesId: String) {}
        override suspend fun scanLibrary(libraryId: String) {}
        override suspend fun booksList(body: KomgaBookSearchDto, page: Int?, size: Int?, sort: String?, unpaged: Boolean?): KomgaPageDto<KomgaBookDto> = KomgaPageDto()
        override suspend fun onDeck(libraryIds: List<String>?, page: Int?, size: Int?, unpaged: Boolean?): KomgaPageDto<KomgaBookDto> = KomgaPageDto()
        override suspend fun latestBooks(page: Int?, size: Int?, unpaged: Boolean?): KomgaPageDto<KomgaBookDto> = KomgaPageDto()
        override suspend fun books(libraryIds: List<String>?, readStatus: List<String>?, page: Int?, size: Int?, sort: String?, unpaged: Boolean?): KomgaPageDto<KomgaBookDto> {
            booksQueriedWithReadStatus = readStatus
            booksSortQueried = sort
            return KomgaPageDto(content = listOf(sampleBook))
        }
        override suspend fun book(bookId: String): KomgaBookDto = sampleBook
        override suspend fun nextBook(bookId: String): KomgaBookDto = sampleBook
        override suspend fun previousBook(bookId: String): KomgaBookDto = sampleBook
        override suspend fun updateReadProgress(bookId: String, body: KomgaReadProgressUpdateDto) {
            updatedProgress[bookId] = body
        }
        override suspend fun deleteReadProgress(bookId: String) {
            deletedProgress += bookId
        }
        override suspend fun collections(page: Int?, size: Int?, unpaged: Boolean?): KomgaPageDto<KomgaCollectionDto> = KomgaPageDto()
        override suspend fun collectionSeries(collectionId: String, unpaged: Boolean?): KomgaPageDto<KomgaSeriesDto> = KomgaPageDto()
        override suspend fun readLists(page: Int?, size: Int?, unpaged: Boolean?): KomgaPageDto<KomgaReadListDto> = KomgaPageDto()
        override suspend fun readListBooks(readListId: String, unpaged: Boolean?): KomgaPageDto<KomgaBookDto> = KomgaPageDto()
        override suspend fun updateReadList(readListId: String, body: KomgaReadListUpdateDto) {}
        override suspend fun bookPageProbe(bookId: String, pageNumber: Int): retrofit2.Response<okhttp3.ResponseBody> = error("Not needed")
        override suspend fun bookPages(bookId: String): List<KomgaBookPageDto> = emptyList()
        override suspend fun bookManifestEpub(bookId: String): KomgaEpubManifestDto = KomgaEpubManifestDto()
        override suspend fun bookManifestFallback(bookId: String): KomgaEpubManifestDto = KomgaEpubManifestDto()
        override suspend fun bookResourceRaw(bookId: String, resource: String): okhttp3.ResponseBody = error("Not needed")
    }

    @Test
    fun bookToChapter_maps1BasedKomgaPageProperly() {
        val fakeApi = FakeKomgaApi()
        val adapter = KomgaKavitaAdapter(fakeApi, OkHttpClient(), "http://localhost:25600")

        // In-progress book at page 5 (out of 20)
        val chapter = adapter.bookToChapter(fakeApi.sampleBook)
        assertEquals(20, chapter.pages)
        assertEquals(5, chapter.pagesRead)

        // Book on page 1 should have pagesRead = 1 (not 0)
        val page1Book = fakeApi.sampleBook.copy(readProgress = KomgaReadProgressDto(page = 1, completed = false))
        val chapter1 = adapter.bookToChapter(page1Book)
        assertEquals(1, chapter1.pagesRead)

        // Completed book
        val completedBook = fakeApi.sampleBook.copy(readProgress = KomgaReadProgressDto(page = 20, completed = true))
        val chapterCompleted = adapter.bookToChapter(completedBook)
        assertEquals(20, chapterCompleted.pagesRead)

        // Unread book
        val unreadBook = fakeApi.sampleBook.copy(readProgress = null)
        val chapterUnread = adapter.bookToChapter(unreadBook)
        assertEquals(0, chapterUnread.pagesRead)
    }

    @Test
    fun getProgress_returnsZeroBasedPageForReader() = runBlocking {
        val fakeApi = FakeKomgaApi()
        val adapter = KomgaKavitaAdapter(fakeApi, OkHttpClient(), "http://localhost:25600")
        val chapterId = KomgaIdMapper.bookId("book-1")

        val progress = adapter.getProgress(chapterId)
        // Komga page 5 is 0-based page 4 in the reader
        assertEquals(4, progress.pageNum)
    }

    @Test
    fun saveProgress_marksCompletedOnLastPage() = runBlocking {
        val fakeApi = FakeKomgaApi()
        val adapter = KomgaKavitaAdapter(fakeApi, OkHttpClient(), "http://localhost:25600")
        val chapterId = KomgaIdMapper.bookId("book-1")

        // Save page 10 (0-based page 9)
        adapter.saveProgress(ProgressDto(libraryId = 1, seriesId = 1, volumeId = 1, chapterId = chapterId, pageNum = 9))
        val updateMid = fakeApi.updatedProgress["book-1"]
        assertNotNull(updateMid)
        assertEquals(10, updateMid?.page)
        assertEquals(null, updateMid?.completed)

        // Save last page (0-based page 19 of 20 pages)
        adapter.saveProgress(ProgressDto(libraryId = 1, seriesId = 1, volumeId = 1, chapterId = chapterId, pageNum = 19))
        val updateEnd = fakeApi.updatedProgress["book-1"]
        assertNotNull(updateEnd)
        assertEquals(20, updateEnd?.page)
        assertEquals(true, updateEnd?.completed)
    }

    @Test
    fun markChapterRead_and_unread_sendsCorrectRequests() = runBlocking {
        val fakeApi = FakeKomgaApi()
        val adapter = KomgaKavitaAdapter(fakeApi, OkHttpClient(), "http://localhost:25600")
        val chapterId = KomgaIdMapper.bookId("book-1")

        adapter.markChapterRead(MarkChapterReadDto(seriesId = 1, chapterId = chapterId, generateReadingSession = false))
        val markReadUpdate = fakeApi.updatedProgress["book-1"]
        assertNotNull(markReadUpdate)
        assertEquals(20, markReadUpdate?.page)
        assertEquals(true, markReadUpdate?.completed)

        adapter.markChaptersUnread(MarkVolumesReadDto(seriesId = 1, chapterIds = listOf(chapterId)))
        assertTrue(fakeApi.deletedProgress.contains("book-1"))
    }

    @Test
    fun readHistoryBooksPage_queriesWithReadStatusAndSort() = runBlocking {
        val fakeApi = FakeKomgaApi()
        val adapter = KomgaKavitaAdapter(fakeApi, OkHttpClient(), "http://localhost:25600")

        val historyPage = adapter.readHistoryBooksPage(page = 0, size = 50)
        assertEquals(1, historyPage.content.size)
        assertEquals(listOf("IN_PROGRESS", "READ"), fakeApi.booksQueriedWithReadStatus)
        assertEquals("readProgress.readDate,desc", fakeApi.booksSortQueried)
    }
}
