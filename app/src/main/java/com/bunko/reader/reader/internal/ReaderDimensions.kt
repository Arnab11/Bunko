package com.bunko.reader.reader.internal

import com.bunko.reader.FileDimensionDto

/** Internal to reader, not for external use. */
internal fun Map<Int, FileDimensionDto>.pageIsWide(page: Int): Boolean {
    return page >= 0 && this[page]?.isWidePage() == true
}

/** Internal to reader, not for external use. */
internal fun List<FileDimensionDto>.toPageDimensionMap(): Map<Int, FileDimensionDto> {
    return mapNotNull { dimension ->
        val page = dimension.pageNumber ?: return@mapNotNull null
        if (page < 0) null else page to dimension
    }.toMap()
}

/** Internal to reader, not for external use. */
internal fun FileDimensionDto.isWidePage(): Boolean {
    return isWide
}
