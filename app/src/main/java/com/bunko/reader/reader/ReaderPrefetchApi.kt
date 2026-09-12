package com.bunko.reader.reader

import com.bunko.reader.FileDimensionDto

/** Internal to reader, not for external use. */
internal fun readerPrefetchPageIndices(
    page: Int,
    pageCount: Int,
    portrait: Boolean,
    pageDimensions: Map<Int, FileDimensionDto>,
    turns: Int
): List<Int> {
    return com.bunko.reader.reader.internal.readerPrefetchPageIndices(
        page = page,
        pageCount = pageCount,
        portrait = portrait,
        pageDimensions = pageDimensions,
        turns = turns
    )
}

/** Internal to reader, not for external use. */
internal fun readerPrefetchMemoryPlan(
    estimatedBytes: List<Long>,
    memoryCacheMaxBytes: Long
): List<Boolean> {
    return com.bunko.reader.reader.internal.readerPrefetchMemoryPlan(
        estimatedBytes = estimatedBytes,
        memoryCacheMaxBytes = memoryCacheMaxBytes
    )
}
