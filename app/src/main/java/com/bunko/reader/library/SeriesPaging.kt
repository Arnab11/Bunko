package com.bunko.reader.library

import com.bunko.reader.SeriesDto

internal data class SeriesPage(
    val items: List<SeriesDto>,
    val hasMore: Boolean
)

internal fun List<SeriesDto>.appendDistinct(page: List<SeriesDto>): List<SeriesDto> =
    (this + page).distinctBy { it.id }
