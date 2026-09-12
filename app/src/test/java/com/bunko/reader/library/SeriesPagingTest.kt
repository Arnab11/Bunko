package com.bunko.reader.library

import com.bunko.reader.SeriesDto
import org.junit.Assert.assertEquals
import org.junit.Test

class SeriesPagingTest {
    @Test
    fun appendDistinct_preservesExistingOrderAndDropsRepeatedIds() {
        val existing = listOf(series(1), series(2))
        val nextPage = listOf(series(2), series(3), series(3))

        val result = existing.appendDistinct(nextPage)

        assertEquals(listOf(1, 2, 3), result.map { it.id })
    }

    private fun series(id: Int) = SeriesDto(id = id, name = "Series $id")
}
