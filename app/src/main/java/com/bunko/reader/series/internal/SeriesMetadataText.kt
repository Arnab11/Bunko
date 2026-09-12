package com.bunko.reader.series.internal

import com.bunko.reader.SeriesDto
import com.bunko.reader.SeriesMetadataDto
import kotlin.math.roundToInt
/** Internal to series, not for external use. */
internal fun SeriesDto.detailMetaLines(
    metadata: SeriesMetadataDto?,
    creatorMaxChars: Int,
    issueCount: Int,
    volumeCount: Int,
    includePeople: Boolean = true,
    includePublisher: Boolean = true
): List<String> {
    val peopleLine = if (includePeople) metadata?.creatorMetaLine(creatorMaxChars) else null
    val publisherLine = if (includePublisher) {
        listOfNotNull(
            metadata?.imprints?.mapNotNull { it.name }.orEmpty().compactNameList(maxItems = 2),
            metadata?.publishers?.mapNotNull { it.name }.orEmpty().compactNameList(maxItems = 2)
        ).joinMetaLine()
    } else {
        null
    }
    val publicationLine = listOfNotNull(
        metadata?.releaseYear?.takeIf { it > 0 }?.toString(),
        metadata?.publicationStatus.publicationStatusText(),
        issueOrVolumeCountText(issueCount, volumeCount)
    ).joinMetaLine()
    val lengthLine = listOfNotNull(
        pages?.let { "${it.compactCount()} pages" },
        readingHoursText(),
        readingStateText()
    ).joinMetaLine()
    return listOfNotNull(
        peopleLine,
        publisherLine,
        publicationLine,
        lengthLine
    )
}

private fun SeriesDto.issueOrVolumeCountText(issueCount: Int, volumeCount: Int): String? {
    return when {
        issueCount > 0 -> "$issueCount ${if (issueCount == 1) "issue" else "issues"}"
        volumeCount > 0 -> "$volumeCount ${if (volumeCount == 1) "volume" else "volumes"}"
        else -> null
    }
}

private fun SeriesDto.readingStateText(): String? {
    val total = pages ?: return null
    if (total <= 0) return null
    val read = (pagesRead ?: 0).coerceAtLeast(0)
    return when {
        read <= 0 -> "Unread"
        read >= total -> "All read"
        else -> "In progress"
    }
}

private fun SeriesDto.readingHoursText(): String? {
    return readingHoursText(minHoursToRead, maxHoursToRead, avgHoursToRead)
}

/** Internal to series, not for external use. */
internal fun readingHoursText(min: Int?, max: Int?, average: Float?): String? {
    return when {
        min != null && max != null && min > 0 && max > min -> "$min-$max hours"
        min != null && min > 0 -> min.hourText()
        max != null && max > 0 -> max.hourText()
        average != null && average > 0f -> average.roundToInt().coerceAtLeast(1).hourText()
        else -> null
    }
}

private fun Int.hourText(): String = "$this ${if (this == 1) "hour" else "hours"}"

private fun Int.compactCount(): String {
    if (this < 1000) return toString()
    val tenths = (this / 100f).roundToInt()
    val whole = tenths / 10
    val decimal = tenths % 10
    return if (decimal == 0) "${whole}K" else "$whole.${decimal}K"
}

private fun SeriesMetadataDto.creatorMetaLine(maxChars: Int): String? {
    return listOf(
        writers,
        coverArtists,
        pencillers,
        inkers,
        colorists,
        letterers,
        editors,
        translators
    )
        .flatten()
        .mapNotNull { it.name?.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .compactNameLine(maxChars)
}

private fun List<String>.joinMetaLine(): String? {
    return joinToString("  •  ").takeIf { it.isNotBlank() }
}

private fun List<String>.compactNameList(maxItems: Int = 2): String? {
    val names = map { it.trim() }.filter { it.isNotBlank() }.distinct()
    if (names.isEmpty()) return null
    return if (names.size <= maxItems) {
        names.joinToString(", ")
    } else {
        names.take(maxItems).joinToString(", ") + " +${names.size - maxItems}"
    }
}

private fun List<String>.compactNameLine(maxChars: Int): String? {
    val names = map { it.trim() }.filter { it.isNotBlank() }.distinct()
    if (names.isEmpty()) return null
    val visible = mutableListOf<String>()
    for (name in names) {
        val candidate = (visible + name).joinToString("  •  ")
        if (visible.isNotEmpty() && candidate.length > maxChars) break
        visible += name
        if (candidate.length >= maxChars) break
    }
    if (visible.isEmpty()) visible += names.first()
    val hidden = names.size - visible.size
    return visible.joinToString("  •  ") + if (hidden > 0) " +$hidden" else ""
}

private fun Int?.publicationStatusText(): String? {
    return when (this) {
        0 -> "Ongoing"
        1 -> "Hiatus"
        2 -> "Completed"
        3 -> "Cancelled"
        4 -> "Ended"
        else -> null
    }
}

