package com.bunko.reader.offline

import com.bunko.reader.download.compareNaturalFileNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalBookTest {

    @Test
    fun testFormatDetection() {
        assertEquals(LocalBookFormat.CBZ, LocalBookFormat.fromExtension("cbz"))
        assertEquals(LocalBookFormat.CBZ, LocalBookFormat.fromExtension("CBZ"))
        assertEquals(LocalBookFormat.CBR, LocalBookFormat.fromExtension("cbr"))
        assertEquals(LocalBookFormat.ZIP, LocalBookFormat.fromExtension("zip"))
        assertEquals(LocalBookFormat.RAR, LocalBookFormat.fromExtension("rar"))
        assertEquals(LocalBookFormat.SEVEN_ZIP, LocalBookFormat.fromExtension("7z"))
        assertEquals(LocalBookFormat.EPUB, LocalBookFormat.fromExtension("epub"))
        assertEquals(LocalBookFormat.MOBI, LocalBookFormat.fromExtension("mobi"))
        assertEquals(LocalBookFormat.FB2, LocalBookFormat.fromExtension("fb2"))
        assertEquals(LocalBookFormat.PDF, LocalBookFormat.fromExtension("pdf"))
        assertEquals(LocalBookFormat.TXT, LocalBookFormat.fromExtension("txt"))
        assertEquals(LocalBookFormat.UNKNOWN, LocalBookFormat.fromExtension("xyz"))
    }

    @Test
    fun testProgressFraction() {
        val unread = LocalBook(
            id = "1",
            title = "Comic",
            uriString = "uri://1",
            extension = "cbz",
            format = LocalBookFormat.CBZ,
            pageCount = 100,
            lastReadPage = 0
        )
        assertEquals(0f, unread.progressFraction, 0.001f)

        val inProgress = unread.copy(lastReadPage = 49)
        assertEquals(0.4949f, inProgress.progressFraction, 0.01f)

        val completed = unread.copy(isCompleted = true)
        assertEquals(1f, completed.progressFraction, 0.001f)
    }

    @Test
    fun testFormattedSize() {
        val bookSmall = LocalBook(
            id = "1",
            title = "Test",
            uriString = "uri://1",
            extension = "cbz",
            format = LocalBookFormat.CBZ,
            sizeBytes = 15 * 1024 * 1024L
        )
        assertEquals("15.0 MB", bookSmall.formattedSize)

        val bookLarge = bookSmall.copy(sizeBytes = 2 * 1024 * 1024 * 1024L)
        assertEquals("2.0 GB", bookLarge.formattedSize)
    }

    @Test
    fun testHashUriStable() {
        val hash1 = LocalBookScanner.hashUri("content://com.android.documents/123")
        val hash2 = LocalBookScanner.hashUri("content://com.android.documents/123")
        val hash3 = LocalBookScanner.hashUri("content://com.android.documents/456")

        assertEquals(hash1, hash2)
        assertFalse(hash1 == hash3)
        assertEquals(16, hash1.length) // 8 bytes in hex = 16 chars
    }

    @Test
    fun testNaturalSortOrder() {
        val files = listOf("page_10.png", "page_1.png", "page_2.png", "page_20.png")
        val sorted = files.sortedWith(::compareNaturalFileNames)
        assertEquals(listOf("page_1.png", "page_2.png", "page_10.png", "page_20.png"), sorted)
    }

    @Test
    fun testContinueReadingSortedByLastReadDescending() {
        val bookEpub = LocalBook(
            id = "epub-1",
            title = "Epub Book",
            uriString = "uri://epub",
            extension = "epub",
            format = LocalBookFormat.EPUB,
            lastReadPage = 10,
            lastReadTime = 1000L
        )
        val bookPdf = LocalBook(
            id = "pdf-1",
            title = "PDF Book",
            uriString = "uri://pdf",
            extension = "pdf",
            format = LocalBookFormat.PDF,
            lastReadPage = 5,
            lastReadTime = 3000L
        )
        val bookCbz = LocalBook(
            id = "cbz-1",
            title = "Comic Book",
            uriString = "uri://cbz",
            extension = "cbz",
            format = LocalBookFormat.CBZ,
            lastReadPage = 25,
            lastReadTime = 2000L
        )
        val bookMobi = LocalBook(
            id = "mobi-1",
            title = "Mobi Book",
            uriString = "uri://mobi",
            extension = "mobi",
            format = LocalBookFormat.MOBI,
            lastReadPage = 1,
            lastReadTime = 4000L
        )
        val unreadBook = LocalBook(
            id = "unread-1",
            title = "Unread Book",
            uriString = "uri://unread",
            extension = "txt",
            format = LocalBookFormat.TXT,
            lastReadPage = 0,
            lastReadTime = 0L
        )
        val completedBook = LocalBook(
            id = "done-1",
            title = "Done Book",
            uriString = "uri://done",
            extension = "fb2",
            format = LocalBookFormat.FB2,
            lastReadPage = 100,
            lastReadTime = 5000L,
            isCompleted = true
        )

        val allBooks = listOf(bookEpub, unreadBook, bookPdf, completedBook, bookCbz, bookMobi)

        val continueReading = allBooks.filter { (it.lastReadPage > 0 || it.lastReadTime > 0L) && !it.isCompleted }
            .sortedWith { a, b ->
                val timeA = if (a.lastReadTime > 0L) a.lastReadTime else a.lastModified
                val timeB = if (b.lastReadTime > 0L) b.lastReadTime else b.lastModified
                timeB.compareTo(timeA)
            }

        assertEquals(4, continueReading.size)
        // Descending by last read book: mobi (4000) -> pdf (3000) -> cbz (2000) -> epub (1000)
        assertEquals(listOf("mobi-1", "pdf-1", "cbz-1", "epub-1"), continueReading.map { it.id })
        // Verify multiple formats are included
        val formats = continueReading.map { it.format }.toSet()
        assertTrue(formats.contains(LocalBookFormat.MOBI))
        assertTrue(formats.contains(LocalBookFormat.PDF))
        assertTrue(formats.contains(LocalBookFormat.CBZ))
        assertTrue(formats.contains(LocalBookFormat.EPUB))
    }
}
