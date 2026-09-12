package com.bunko.reader.reader.internal

import com.bunko.reader.FileDimensionDto
import kotlin.math.roundToInt

/** Internal to reader, not for external use. */
internal fun readerPrefetchPageIndices(
    page: Int,
    pageCount: Int,
    portrait: Boolean,
    pageDimensions: Map<Int, FileDimensionDto>,
    turns: Int
): List<Int> {
    if (pageCount <= 0 || page !in 0 until pageCount || turns <= 0) return emptyList()
    val result = LinkedHashSet<Int>()
    var cursor = page
    var remainingTurns = turns
    while (remainingTurns > 0) {
        val currentLayout = readerPageLayout(cursor, pageCount, portrait, pageDimensions)
        val next = cursor + currentLayout.nextStep
        if (next !in 0 until pageCount) break
        result += readerVisiblePageIndices(next, pageCount, portrait, pageDimensions)
        cursor = next
        remainingTurns--
    }
    return result.toList()
}

/** Internal to reader, not for external use. */
internal fun readerPrefetchPageIndicesAround(
    page: Int,
    pageCount: Int,
    portrait: Boolean,
    pageDimensions: Map<Int, FileDimensionDto>,
    turns: Int
): List<Int> {
    if (pageCount <= 0 || page !in 0 until pageCount || turns <= 0) return emptyList()
    val result = LinkedHashSet<Int>()
    val layout = readerPageLayout(page, pageCount, portrait, pageDimensions)
    // Re-warm the spread we'd land on when turning back FIRST, ahead of the forward prefetch.
    // The loader processes these roughly in order under a concurrency limit, so putting the
    // previous spread last let the forward flood evict the just-left page (still only in the
    // memory cache from being displayed) before it was re-warmed — an immediate back-turn then
    // paid a full re-decode. Loading it first keeps it warm through the forward loads.
    if (page > 0) {
        val previous = (page - layout.previousStep).coerceAtLeast(0)
        result += readerVisiblePageIndices(previous, pageCount, portrait, pageDimensions)
    }
    result += readerPrefetchPageIndices(page, pageCount, portrait, pageDimensions, turns)
    return result.toList()
}

/** Internal to reader, not for external use. */
internal fun readerVerticalPrefetchPageIndicesAround(
    page: Int,
    pageCount: Int,
    pagesAhead: Int
): List<Int> {
    if (pageCount <= 0 || page !in 0 until pageCount || pagesAhead <= 0) return emptyList()
    val result = ArrayList<Int>(pagesAhead + 1)
    if (page > 0) result += page - 1
    val lastPage = minOf(page + pagesAhead, pageCount - 1)
    for (targetPage in page + 1..lastPage) result += targetPage
    return result
}

/** Matches the full-width, source-aspect-ratio constraints used by ReaderVerticalScroll. */
internal fun readerVerticalPrefetchSize(
    page: Int,
    pageDimensions: Map<Int, FileDimensionDto>,
    viewportWidthPx: Float,
    fallbackAspectRatio: Float
): ReaderPrefetchSize {
    val width = viewportWidthPx.roundToInt().coerceAtLeast(1)
    val aspectRatio = readerVerticalPageAspectRatio(
        dimension = pageDimensions[page],
        fallbackAspectRatio = fallbackAspectRatio
    )
    return ReaderPrefetchSize(
        width = width,
        height = (width / aspectRatio).roundToInt().coerceAtLeast(1)
    )
}

/** Internal to reader, not for external use. */
internal fun readerPrefetchSlotWidthPx(
    page: Int,
    pageCount: Int,
    portrait: Boolean,
    pageDimensions: Map<Int, FileDimensionDto>,
    viewportWidthPx: Float
): Int {
    val layout = readerPageLayout(page, pageCount, portrait, pageDimensions)
    val width = if (layout.singlePage) viewportWidthPx else viewportWidthPx / 2f
    return width.roundToInt().coerceAtLeast(1)
}

/** Internal to reader, not for external use. */
internal fun readerEstimatedDecodeBytes(
    page: Int,
    pageDimensions: Map<Int, FileDimensionDto>,
    targetWidth: Int,
    targetHeight: Int
): Long {
    val source = pageDimensions[page]
    val sourceWidth = source?.width?.takeIf { it > 0 }
    val sourceHeight = source?.height?.takeIf { it > 0 }
    val decodedPixels = if (sourceWidth != null && sourceHeight != null) {
        val scale = minOf(
            targetWidth.toDouble() / sourceWidth,
            targetHeight.toDouble() / sourceHeight,
            1.0
        )
        val width = (sourceWidth * scale).toLong().coerceAtLeast(1L)
        val height = (sourceHeight * scale).toLong().coerceAtLeast(1L)
        width * height
    } else {
        targetWidth.toLong().coerceAtLeast(1L) * targetHeight.toLong().coerceAtLeast(1L)
    }
    return decodedPixels.coerceAtMost(Long.MAX_VALUE / 4L) * 4L
}

/** Internal to reader, not for external use. */
internal fun readerPrefetchMemoryPlan(
    estimatedBytes: List<Long>,
    memoryCacheMaxBytes: Long
): List<Boolean> {
    if (memoryCacheMaxBytes <= 0L) return estimatedBytes.map { false }
    // Reserve the remaining 30% for the visible spread, curl surfaces, and short-lived
    // transition overlap. Targets after the first miss are not decoded merely to warm disk.
    val budget = memoryCacheMaxBytes * 7L / 10L
    var used = 0L
    var exhausted = false
    return estimatedBytes.map { estimate ->
        val safeEstimate = estimate.coerceAtLeast(0L)
        if (!exhausted && used <= budget && safeEstimate <= budget - used) {
            used += safeEstimate
            true
        } else {
            exhausted = true
            false
        }
    }
}
