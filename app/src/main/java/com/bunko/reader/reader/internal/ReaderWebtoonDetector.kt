package com.bunko.reader.reader.internal

import com.bunko.reader.FileDimensionDto
import com.bunko.reader.ReaderReadingDirection

/**
 * Intelligent detector for Webtoon / Long Strip formats.
 * Inspects page aspect ratios and series metadata tags/genres.
 */
internal object ReaderWebtoonDetector {

    private val WEBTOON_KEYWORDS = listOf(
        "webtoon",
        "webtoons",
        "manhwa",
        "long strip",
        "longstrip",
        "vertical scroll",
        "scroll"
    )

    fun isWebtoonMetadata(genres: List<String>?, tags: List<String>?): Boolean {
        if (genres != null && genres.any { g -> WEBTOON_KEYWORDS.any { kw -> g.contains(kw, ignoreCase = true) } }) {
            return true
        }
        if (tags != null && tags.any { t -> WEBTOON_KEYWORDS.any { kw -> t.contains(kw, ignoreCase = true) } }) {
            return true
        }
        return false
    }

    fun isWebtoonDimensions(pageDimensions: Map<Int, FileDimensionDto>?): Boolean {
        if (pageDimensions.isNullOrEmpty()) return false
        val validDims = pageDimensions.values.filter { (it.width ?: 0) > 0 && (it.height ?: 0) > 0 }
        if (validDims.isEmpty()) return false

        var tallSliceCount = 0
        var extremeTallSliceCount = 0
        var totalAspect = 0f

        for (dim in validDims) {
            val aspect = dim.width!!.toFloat() / dim.height!!.toFloat()
            totalAspect += aspect
            if (aspect < 0.65f) {
                tallSliceCount++
            }
            if (aspect < 0.45f) {
                extremeTallSliceCount++
            }
        }

        val avgAspect = totalAspect / validDims.size
        // 1. If any extreme tall slice (< 0.45), webtoon strip format
        if (extremeTallSliceCount >= 1) return true
        // 2. If majority of pages have aspect < 0.65
        if (tallSliceCount >= (validDims.size + 1) / 2) return true
        // 3. If average aspect ratio is significantly tall
        if (avgAspect < 0.62f) return true

        return false
    }

    fun resolveEffectiveReadingDirection(
        preferredDirection: ReaderReadingDirection,
        autoWebtoonMode: Boolean,
        pageDimensions: Map<Int, FileDimensionDto>?,
        genres: List<String>? = null,
        tags: List<String>? = null
    ): ReaderReadingDirection {
        // If explicitly set to Webtoon or Vertical, respect user choice
        if (preferredDirection == ReaderReadingDirection.Webtoon) {
            return ReaderReadingDirection.Webtoon
        }
        if (preferredDirection == ReaderReadingDirection.Vertical) {
            return ReaderReadingDirection.Vertical
        }

        // If Auto Webtoon mode is on, check if content is webtoon
        if (autoWebtoonMode) {
            if (isWebtoonMetadata(genres, tags) || isWebtoonDimensions(pageDimensions)) {
                return ReaderReadingDirection.Webtoon
            }
        }

        return preferredDirection
    }
}
