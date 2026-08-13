package li.mof.kamigura.reader

import li.mof.kamigura.ReaderReadingDirection

internal const val KavitaReadingDirectionLtr = 0
internal const val KavitaReadingDirectionRtl = 1
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
        else -> globalDirection
    }
}
