package com.bunko.reader.series

import kotlinx.coroutines.runBlocking
import com.bunko.reader.SeriesDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesLibraryDataTest {
    @Test
    fun loadPagedSeriesContinuesUntilShortPage() = runBlocking {
        val pages = listOf(
            listOf(series(1), series(2)),
            listOf(series(3), series(4)),
            listOf(series(5))
        )
        val requests = mutableListOf<Pair<Int, Int>>()

        val result = loadPagedSeries(pageSize = 2) { pageNumber, pageSize ->
            requests += pageNumber to pageSize
            pages.getOrElse(pageNumber) { emptyList() }
        }

        assertEquals(listOf(1, 2, 3, 4, 5), result.map { it.id })
        assertEquals(listOf(0 to 2, 1 to 2, 2 to 2), requests)
    }

    @Test
    fun loadPagedSeriesStopsAfterFirstShortPage() = runBlocking {
        val requests = mutableListOf<Int>()

        val result = loadPagedSeries(pageSize = 3) { pageNumber, _ ->
            requests += pageNumber
            listOf(series(1), series(2))
        }

        assertEquals(listOf(1, 2), result.map { it.id })
        assertEquals(listOf(0), requests)
    }

    @Test
    fun normalizeSeriesSearchQueryTrimsAsciiAndFullWidthSpaces() {
        assertEquals("A  B", normalizeSeriesSearchQuery("  \u3000A  B\u3000 "))
    }

    @Test
    fun matchesSeriesTitleChecksAllKnownNamesIgnoringCase() {
        val item = SeriesDto(
            id = 1,
            name = "Display",
            localizedName = "駅で見た可愛い女の子",
            originalName = "Another Title"
        )

        assertTrue(item.matchesSeriesTitle("駅で見た"))
        assertTrue(item.matchesSeriesTitle("another"))
        assertFalse(item.matchesSeriesTitle("missing"))
    }

    @Test
    fun librarySeriesFilterUsesKavitaLibraryField() {
        val filter = librarySeriesFilter(35)

        assertEquals(1, filter.statements.size)
        assertEquals(0, filter.statements.single().comparison)
        assertEquals(19, filter.statements.single().field)
        assertEquals("35", filter.statements.single().value)
    }

    @Test
    fun sortedForLibraryUsesTitleAsDefaultOrder() {
        val items = listOf(
            series(1, "Beta"),
            series(2, "alpha"),
            series(3, "Gamma")
        )

        assertEquals(listOf(2, 1, 3), items.sortedForLibrary(SeriesLibrarySort.Title).map { it.id })
    }

    @Test
    fun sortedForLibraryCanPromoteUnreadSeries() {
        val items = listOf(
            series(1, "Read", pages = 100, pagesRead = 100),
            series(2, "Unread B", pages = 100, pagesRead = 0),
            series(3, "Progress", pages = 100, pagesRead = 40),
            series(4, "Unread A", pages = 100, pagesRead = null)
        )

        assertEquals(
            listOf(4, 2, 3, 1),
            items.sortedForLibrary(SeriesLibrarySort.UnreadFirst).map { it.id }
        )
    }

    @Test
    fun sortedForLibraryCanPromoteInProgressSeries() {
        val items = listOf(
            series(1, "Unread", pages = 100, pagesRead = 0),
            series(2, "Progress B", pages = 100, pagesRead = 40),
            series(3, "Read", pages = 100, pagesRead = 100),
            series(4, "Progress A", pages = 100, pagesRead = 1)
        )

        assertEquals(
            listOf(4, 2, 3, 1),
            items.sortedForLibrary(SeriesLibrarySort.InProgressFirst).map { it.id }
        )
    }

    @Test
    fun sortedForLibraryCanPromoteReadSeries() {
        val items = listOf(
            series(1, "Unread", pages = 100, pagesRead = 0),
            series(2, "Read B", pages = 100, pagesRead = 120),
            series(3, "Progress", pages = 100, pagesRead = 40),
            series(4, "Read A", pages = 100, pagesRead = 100)
        )

        assertEquals(
            listOf(4, 2, 3, 1),
            items.sortedForLibrary(SeriesLibrarySort.ReadFirst).map { it.id }
        )
    }

    private fun series(
        id: Int,
        name: String = "Series $id",
        pages: Int? = null,
        pagesRead: Int? = null
    ): SeriesDto {
        return SeriesDto(id = id, name = name, pages = pages, pagesRead = pagesRead)
    }
}
