package com.bunko.reader.reader

import com.bunko.reader.ReaderReadingDirection

internal const val KavitaReadingDirectionLtr = 0
internal const val KavitaReadingDirectionRtl = 1
internal const val KavitaReadingDirectionVertical = 2
internal const val KavitaReadingProfileKindDefault = 0

internal fun resolveReaderReadingDirection(
    globalDirection: ReaderReadingDirection,
    cachedDirection: ReaderReadingDirection?,
    profileKind: Int?,
    profileDirection: Int?
): ReaderReadingDirection {
    cachedDirection?.let { return it }
    if (profileKind == KavitaReadingProfileKindDefault) return globalDirection
    return when (profileDirection) {
        KavitaReadingDirectionRtl -> ReaderReadingDirection.RightToLeft
        KavitaReadingDirectionLtr -> ReaderReadingDirection.LeftToRight
        KavitaReadingDirectionVertical -> ReaderReadingDirection.Webtoon
        else -> globalDirection
    }
}
