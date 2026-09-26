package com.bunko.reader.series

import com.bunko.reader.KavitaApi
import com.bunko.reader.SeriesDto
import com.bunko.reader.SeriesFilterStatementDto
import com.bunko.reader.SeriesFilterV2Dto

internal const val SeriesLibraryPageSize = 300

internal suspend fun KavitaApi.loadAllSeriesForLibrary(
    libraryId: Int,
    pageSize: Int = SeriesLibraryPageSize
): List<SeriesDto> {
    val body = librarySeriesFilter(libraryId)
    return loadPagedSeries(pageSize = pageSize) { pageNumber, requestedPageSize ->
        allSeriesV2(
            body = body,
            pageNumber = pageNumber,
            pageSize = requestedPageSize
        )
    }
        .filter { it.libraryId == null || it.libraryId == libraryId }
        .distinctBy { it.id }
        .sortedBy { it.name }
}

internal suspend fun loadPagedSeries(
    pageSize: Int = SeriesLibraryPageSize,
    loadPage: suspend (pageNumber: Int, pageSize: Int) -> List<SeriesDto>
): List<SeriesDto> {
    require(pageSize > 0) { "pageSize must be positive" }

    val result = mutableListOf<SeriesDto>()
    var pageNumber = 0
    while (true) {
        val page = loadPage(pageNumber, pageSize)
        result += page
        if (page.size < pageSize) break
        pageNumber += 1
    }
    return result
}

internal fun librarySeriesFilter(libraryId: Int): SeriesFilterV2Dto {
    return SeriesFilterV2Dto(
        statements = listOf(
            SeriesFilterStatementDto(
                comparison = 0,
                field = 19,
                value = libraryId.toString()
            )
        )
    )
}

internal fun normalizeSeriesSearchQuery(query: String): String {
    return query.replace('\u3000', ' ').trim()
}

internal fun SeriesDto.matchesSeriesTitle(query: String): Boolean {
    return listOfNotNull(name, localizedName, originalName)
        .any { it.contains(query, ignoreCase = true) }
}

internal enum class SeriesLibrarySort(val label: String) {
    Title("Title"),
    InProgressFirst("Recent"),
    ReadFirst("Date"),
    UnreadFirst("Unread")
}

internal fun List<SeriesDto>.sortedForLibrary(
    sort: SeriesLibrarySort,
    isDescending: Boolean = false
): List<SeriesDto> {
    val sorted = when (sort) {
        SeriesLibrarySort.Title -> sortedByTitle()
        SeriesLibrarySort.InProgressFirst -> sortedWith(
            compareByDescending<SeriesDto> { it.isInProgressSeries() }
                .thenBy { it.titleSortKey() }
        )
        SeriesLibrarySort.ReadFirst -> sortedWith(
            compareByDescending<SeriesDto> { it.isReadSeries() }
                .thenBy { it.titleSortKey() }
        )
        SeriesLibrarySort.UnreadFirst -> sortedWith(
            compareByDescending<SeriesDto> { it.isUnreadSeries() }
                .thenBy { it.titleSortKey() }
        )
    }
    return if (isDescending) sorted.reversed() else sorted
}

internal fun Int.seriesCountLabel(): String = "$this series"

private fun List<SeriesDto>.sortedByTitle(): List<SeriesDto> =
    sortedBy { it.titleSortKey() }

private fun SeriesDto.titleSortKey(): String =
    listOfNotNull(localizedName, name, originalName)
        .firstOrNull { it.isNotBlank() }
        .orEmpty()
        .lowercase()

private fun SeriesDto.isUnreadSeries(): Boolean {
    val total = pages ?: return (pagesRead ?: 0) <= 0
    return total > 0 && (pagesRead ?: 0) <= 0
}

private fun SeriesDto.isInProgressSeries(): Boolean {
    val total = pages ?: return false
    val read = pagesRead ?: 0
    return total > 0 && read in 1 until total
}

private fun SeriesDto.isReadSeries(): Boolean {
    val total = pages ?: return false
    return total > 0 && (pagesRead ?: 0) >= total
}
